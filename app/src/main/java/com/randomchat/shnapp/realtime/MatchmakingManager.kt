package com.randomchat.shnapp.realtime

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.randomchat.shnapp.model.Gender
import com.randomchat.shnapp.utils.Constants
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
    private var heartbeatJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun startSearch(gender: Gender) {
        if (_state.value is MatchmakingState.Searching) return
        _state.value = MatchmakingState.Searching
        scope.launch {
            try {
                rtdb.clearAssignment(sessionId) // wipe any stale assignment before listening
                rtdb.setOnline(sessionId)
                writeQueueEntry(gender)
                listenForAssignment()
                startHeartbeat()
            } catch (e: Exception) {
                Log.e("Matchmaking", "startSearch failed: ${e.message}")
                _state.value = MatchmakingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Write queue entry with all required fields ────────────────────────────
    // Gender is written so the server matcher can apply a soft F-F bias.
    // Never read by the chat client → never shown to either user in-chat.

    private suspend fun writeQueueEntry(gender: Gender) {
        val entry = mapOf(
            "sessionId" to sessionId,
            "joinedAt" to ServerValue.TIMESTAMP,
            "platform" to "android",
            "gender" to gender.name        // "MALE" | "FEMALE"
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
            stopHeartbeat()
        } catch (e: Exception) {
            _state.value = MatchmakingState.Matched(roomId, "stranger")
            stopHeartbeat()
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

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun cancelSearch() {
        _state.value = MatchmakingState.Idle
        stopHeartbeat()
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
        synchronized(Companion) { instance = null } // clear before cancel so next getInstance() gets a fresh object
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
