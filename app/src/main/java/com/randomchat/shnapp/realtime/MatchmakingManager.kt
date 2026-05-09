package com.randomchat.shnapp.realtime

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.generateRoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class MatchmakingState {
    object Idle : MatchmakingState()
    object Searching : MatchmakingState()
    data class Matched(val roomId: String, val strangerId: String) : MatchmakingState()
    data class Error(val msg: String) : MatchmakingState()
}

class MatchmakingManager(
    private val sessionId: String,
    private val rtdb: RealtimeDbManager = RealtimeDbManager.getInstance()
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<MatchmakingState>(MatchmakingState.Idle)
    val state: StateFlow<MatchmakingState> = _state

    private var assignmentListenerJob: Job? = null
    private var matchPollingJob: Job? = null
    private var heartbeatJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun startSearch() {
        if (_state.value is MatchmakingState.Searching) return
        _state.value = MatchmakingState.Searching
        scope.launch {
            try {
                rtdb.setOnline(sessionId)
                writeQueueEntry()
                listenForAssignment()
                startMatchPolling()
                startHeartbeat()
            } catch (e: Exception) {
                Log.e("Matchmaking", "startSearch failed: ${e.message}")
                _state.value = MatchmakingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Write queue entry with all required fields ────────────────────────────

    private suspend fun writeQueueEntry() {
        val entry = mapOf(
            "sessionId" to sessionId,
            "joinedAt" to ServerValue.TIMESTAMP,
            "platform" to "android"
        )
        val ref = FirebaseDatabase.getInstance().reference
            .child(Constants.PATH_WAITING_QUEUE)
            .child(sessionId)
        ref.setValue(entry).await()
        ref.onDisconnect().removeValue()
        Log.d("Matchmaking", "Queue entry written OK for $sessionId")
    }

    // ── Listen for assignment written by the other device ─────────────────────

    private fun listenForAssignment() {
        assignmentListenerJob?.cancel()
        assignmentListenerJob = scope.launch {
            try {
                rtdb.observeAssignment(sessionId).collect { roomId ->
                    if (roomId != null && _state.value is MatchmakingState.Searching) {
                        Log.d("Matchmaking", "Assignment received roomId=$roomId")
                        resolveMatch(roomId)
                    }
                }
            } catch (e: Exception) {
                Log.e("Matchmaking", "Assignment listener error: ${e.message}")
            }
        }
    }

    private suspend fun resolveMatch(roomId: String) {
        try {
            val snap = FirebaseDatabase.getInstance().reference
                .child("rooms").child(roomId).get().await()
            val participants = snap.child("participants").children
                .mapNotNull { it.getValue(String::class.java) }
            val strangerId = participants.firstOrNull { it != sessionId } ?: "stranger"
            _state.value = MatchmakingState.Matched(roomId, strangerId)
            stopPollingAndHeartbeat()
        } catch (e: Exception) {
            _state.value = MatchmakingState.Matched(roomId, "stranger")
            stopPollingAndHeartbeat()
        }
    }

    // ── Polling loop — retries every 3s until matched ─────────────────────────
    //
    // Key design: whichever device has the lexicographically SMALLER sessionId
    // acts as the match initiator. The other device waits for the assignment
    // listener. This prevents double-room race conditions when both devices
    // poll simultaneously.

    private fun startMatchPolling() {
        matchPollingJob?.cancel()
        matchPollingJob = scope.launch {
            var attempt = 0
            while (_state.value is MatchmakingState.Searching) {
                attempt++
                delay(if (attempt == 1) 1000L else 3000L)
                if (_state.value !is MatchmakingState.Searching) break
                tryMatchOnce()
            }
        }
    }

    private suspend fun tryMatchOnce() {
        try {
            val queueSnap = FirebaseDatabase.getInstance().reference
                .child(Constants.PATH_WAITING_QUEUE).get().await()

            val candidates = queueSnap.children.mapNotNull { child ->
                child.child("sessionId").getValue(String::class.java)
                    ?.takeIf { it != sessionId }
            }

            Log.d("Matchmaking", "Poll: ${candidates.size} candidates. me=$sessionId")

            if (candidates.isEmpty()) return

            val partnerId = candidates.first()

            // Only the smaller sessionId initiates to prevent double-match
            if (sessionId > partnerId) {
                Log.d("Matchmaking", "Waiting (not initiator)")
                return
            }

            Log.d("Matchmaking", "Initiating match with $partnerId")
            val roomId = generateRoomId(sessionId, partnerId)

            rtdb.createRoom(roomId, sessionId, partnerId)
            rtdb.writeAssignment(sessionId, roomId)
            rtdb.writeAssignment(partnerId, roomId)
            rtdb.leaveWaitingQueue(sessionId)
            rtdb.leaveWaitingQueue(partnerId)

            if (_state.value is MatchmakingState.Searching) {
                _state.value = MatchmakingState.Matched(roomId, partnerId)
                stopPollingAndHeartbeat()
            }
        } catch (e: Exception) {
            Log.w("Matchmaking", "tryMatchOnce failed, will retry: ${e.message}")
        }
    }

    // ── Heartbeat — ONLY updates the heartbeat child, never overwrites parent ─

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _state.value is MatchmakingState.Searching) {
                delay(Constants.HEARTBEAT_INTERVAL_MS)
                if (_state.value !is MatchmakingState.Searching) break
                try {
                    // updateChildren is critical here — setValue on the parent node
                    // would erase sessionId and joinedAt, breaking matchmaking reads
                    FirebaseDatabase.getInstance().reference
                        .child(Constants.PATH_WAITING_QUEUE)
                        .child(sessionId)
                        .updateChildren(mapOf("heartbeat" to ServerValue.TIMESTAMP))
                } catch (_: Exception) {}
            }
        }
    }

    private fun stopPollingAndHeartbeat() {
        matchPollingJob?.cancel()
        matchPollingJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun cancelSearch() {
        _state.value = MatchmakingState.Idle
        stopPollingAndHeartbeat()
        assignmentListenerJob?.cancel()
        scope.launch {
            try {
                rtdb.leaveWaitingQueue(sessionId)
                rtdb.clearAssignment(sessionId)
                rtdb.setOffline(sessionId)
            } catch (_: Exception) {}
        }
    }

    fun reset() { cancelSearch() }

    fun destroy() {
        cancelSearch()
        scope.cancel()
    }

    companion object {
        @Volatile private var instance: MatchmakingManager? = null

        fun getInstance(sessionId: String): MatchmakingManager {
            return instance ?: synchronized(this) {
                instance ?: MatchmakingManager(sessionId).also { instance = it }
            }
        }

        fun resetInstance() {
            instance?.destroy()
            instance = null
        }
    }
}
