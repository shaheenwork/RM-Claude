package com.randomchat.shnapp.realtime

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.model.WaitingUser
import com.randomchat.shnapp.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

class RealtimeDbManager {

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance().apply {
        setPersistenceEnabled(false) // ephemeral - no disk cache
    }

    private val root: DatabaseReference = db.reference

    // ─── Presence ────────────────────────────────────────────────────────────

    fun setOnline(sessionId: String) {
        val ref = root.child(Constants.PATH_PRESENCE).child(sessionId)
        // updateChildren merges — preserves existing fields like `ip`
        ref.updateChildren(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
        ref.onDisconnect().updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    suspend fun fetchAndStoreIp(sessionId: String) {
        try {
            val ip = withContext(Dispatchers.IO) {
                URL("https://api.ipify.org").readText().trim()
            }
            root.child(Constants.PATH_PRESENCE).child(sessionId).child("ip").setValue(ip)
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "fetchAndStoreIp failed: ${e.message}")
        }
    }

    suspend fun readIp(sessionId: String): String? {
        return try {
            root.child(Constants.PATH_PRESENCE).child(sessionId).child("ip")
                .get().await().getValue(String::class.java)
        } catch (e: Exception) { null }
    }

    fun setOffline(sessionId: String) {
        root.child(Constants.PATH_PRESENCE).child(sessionId)
            .updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    // ─── Waiting Queue ────────────────────────────────────────────────────────

    suspend fun joinWaitingQueue(sessionId: String) {
        try {
            val entry = mapOf(
                "sessionId" to sessionId,
                "joinedAt" to ServerValue.TIMESTAMP,
                "platform" to "android"
            )
            root.child(Constants.PATH_WAITING_QUEUE).child(sessionId).setValue(entry).await()
            // Auto-remove on disconnect
            root.child(Constants.PATH_WAITING_QUEUE).child(sessionId)
                .onDisconnect().removeValue()
        } catch (e: Exception) {
            // Permission denied or network — log and continue; app still works
            android.util.Log.w("RTDB", "joinWaitingQueue failed: ${e.message}")
        }
    }

    suspend fun leaveWaitingQueue(sessionId: String) {
        try {
            root.child(Constants.PATH_WAITING_QUEUE).child(sessionId).removeValue().await()
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "leaveWaitingQueue failed: ${e.message}")
        }
    }

    // Observe waiting queue - used by server-side logic simulation in MatchmakingManager
    fun observeWaitingQueue(): Flow<List<WaitingUser>> = callbackFlow {
        val ref = root.child(Constants.PATH_WAITING_QUEUE)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { child ->
                    val sessionId = child.child("sessionId").getValue(String::class.java) ?: return@mapNotNull null
                    val joinedAt = child.child("joinedAt").getValue(Long::class.java) ?: 0L
                    WaitingUser(sessionId = sessionId, joinedAt = joinedAt)
                }
                trySend(users)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Session Assignments ─────────────────────────────────────────────────

    fun observeAssignment(sessionId: String): Flow<String?> = callbackFlow {
        val ref = root.child(Constants.PATH_SESSION_ASSIGNMENTS).child(sessionId)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val roomId = snapshot.child("roomId").getValue(String::class.java)
                trySend(roomId)
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.w("RTDB", "observeAssignment cancelled: ${error.message}")
                // Don't close the flow — let the caller handle silence
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun writeAssignment(sessionId: String, roomId: String) {
        try {
            root.child(Constants.PATH_SESSION_ASSIGNMENTS).child(sessionId)
                .setValue(mapOf("roomId" to roomId, "assignedAt" to ServerValue.TIMESTAMP)).await()
            root.child(Constants.PATH_SESSION_ASSIGNMENTS).child(sessionId)
                .onDisconnect().removeValue()
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "writeAssignment failed: ${e.message}")
        }
    }

    suspend fun clearAssignment(sessionId: String) {
        try {
            root.child(Constants.PATH_SESSION_ASSIGNMENTS).child(sessionId).removeValue().await()
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "clearAssignment failed: ${e.message}")
        }
    }

    // ─── Rooms ────────────────────────────────────────────────────────────────

    suspend fun createRoom(roomId: String, participantA: String, participantB: String) {
        try {
            val room = mapOf(
                "id" to roomId,
                "participants" to listOf(participantA, participantB),
                "status" to "ACTIVE",
                "createdAt" to ServerValue.TIMESTAMP
            )
            root.child(Constants.PATH_ROOMS).child(roomId).setValue(room).await()
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "createRoom failed: ${e.message}")
            throw e // rethrow so MatchmakingManager can handle it
        }
    }

    suspend fun endRoom(roomId: String) {
        try {
            root.child(Constants.PATH_ROOMS).child(roomId).updateChildren(
                mapOf("status" to "ENDED", "endedAt" to ServerValue.TIMESTAMP)
            ).await()
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "endRoom failed: ${e.message}")
        }
    }

    fun observeRoomStatus(roomId: String): Flow<String?> = callbackFlow {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId).child("status")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(String::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.w("RTDB", "observeRoomStatus cancelled: ${error.message}")
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    suspend fun sendMessage(roomId: String, message: ChatMessage): String {
        val msgRef = root.child(Constants.PATH_ROOMS).child(roomId)
            .child(Constants.PATH_MESSAGES).push()
        val msgId = msgRef.key ?: System.currentTimeMillis().toString()
        val data = mapOf(
            "id" to msgId,
            "senderId" to message.senderId,
            "content" to message.content,
            "mediaUrl" to message.mediaUrl,
            "type" to message.type.name,
            "timestamp" to ServerValue.TIMESTAMP
        )
        try {
            msgRef.setValue(data).await()
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "sendMessage failed: ${e.message}")
        }
        return msgId
    }

    fun observeMessages(roomId: String): Flow<ChatMessage> = callbackFlow {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId)
            .child(Constants.PATH_MESSAGES)
            .orderByChild("timestamp")
            .limitToLast(Constants.MESSAGES_PAGE_SIZE)

        // IMPORTANT: ChildEventListener fires only for NEW children (onChildAdded).
        // ValueEventListener fires the ENTIRE list on every change, so adding
        // message #4 re-emits 1,2,3,4 — causing duplicate key crashes in LazyColumn.
        val listener = ref.addChildEventListener(object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val msg = parseMessage(snapshot)
                if (msg != null) trySend(msg)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.w("RTDB", "observeMessages cancelled: ${error.message}")
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun parseMessage(snapshot: DataSnapshot): ChatMessage? {
        return try {
            val id = snapshot.child("id").getValue(String::class.java) ?: return null
            val senderId = snapshot.child("senderId").getValue(String::class.java) ?: return null
            val content = snapshot.child("content").getValue(String::class.java) ?: ""
            val mediaUrl = snapshot.child("mediaUrl").getValue(String::class.java) ?: ""
            val typeStr = snapshot.child("type").getValue(String::class.java) ?: "TEXT"
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            ChatMessage(
                id = id,
                senderId = senderId,
                content = content,
                mediaUrl = mediaUrl,
                type = enumValueOf(typeStr),
                timestamp = timestamp
            )
        } catch (e: Exception) { null }
    }

    // ─── Typing ───────────────────────────────────────────────────────────────

    fun setActivity(roomId: String, sessionId: String, activity: String?) {
        root.child(Constants.PATH_ROOMS).child(roomId)
            .child(Constants.PATH_TYPING).child(sessionId)
            .setValue(activity)
    }

    fun setTyping(roomId: String, sessionId: String, isTyping: Boolean) {
        setActivity(roomId, sessionId, if (isTyping) "typing" else null)
    }

    fun observeActivity(roomId: String, mySessionId: String): Flow<String?> = callbackFlow {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId).child(Constants.PATH_TYPING)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activity = snapshot.children
                    .firstOrNull { it.key != mySessionId }
                    ?.getValue(String::class.java)
                trySend(activity)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Draft text (real-time typing preview for premium) ────────────────────

    /**
     * Write or remove the caller's current draft text.
     * text=null removes the node so RTDB doesn't retain stale data.
     */
    fun setDraftText(roomId: String, sessionId: String, text: String?) {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId)
            .child(Constants.PATH_DRAFT_TEXT).child(sessionId)
        if (text == null) ref.removeValue() else ref.setValue(text)
    }

    /**
     * Emits the stranger's current draft string (or null when they clear it).
     * Filters out mySessionId so we always see only the other participant.
     */
    fun observeDraftText(roomId: String, mySessionId: String): Flow<String?> = callbackFlow {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId).child(Constants.PATH_DRAFT_TEXT)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val text = snapshot.children
                    .firstOrNull { it.key != mySessionId }
                    ?.getValue(String::class.java)
                trySend(text)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Transaction-safe matchmaking ─────────────────────────────────────────

    fun tryMatchFromQueue(mySessionId: String, onMatched: (String, String) -> Unit) {
        val queueRef = root.child(Constants.PATH_WAITING_QUEUE)
        queueRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val entries = currentData.children.toList()
                val candidate = entries.firstOrNull { it.key != mySessionId } ?: return Transaction.success(currentData)
                val candidateId = candidate.child("sessionId").getValue(String::class.java) ?: return Transaction.success(currentData)
                currentData.child(mySessionId).value = null
                currentData.child(candidateId).value = null
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (!committed || error != null) return
                // Find who we matched with by looking at what was removed
                val removedKeys = currentData?.children?.map { it.key }?.toSet() ?: return
                // Re-read queue to find candidate - simplified approach
                queueRef.get().addOnSuccessListener { snapshot ->
                    // The match already happened in transaction; we need to re-derive it
                    // In production you'd use Cloud Functions for this
                }
            }
        })
    }

    // ─── Reactions ────────────────────────────────────────────────────────────

    /**
     * Set or remove a reaction.
     * emoji=null → remove the caller's reaction from this message.
     */
    fun setReaction(roomId: String, messageId: String, sessionId: String, emoji: String?) {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId)
            .child(Constants.PATH_REACTIONS).child(messageId).child(sessionId)
        if (emoji == null) ref.removeValue() else ref.setValue(emoji)
    }

    /**
     * Emits the full reactions map whenever anything changes.
     * Map structure: Map<messageId, Map<sessionId, emoji>>
     */
    fun observeReactions(roomId: String): Flow<Map<String, Map<String, String>>> = callbackFlow {
        val ref = root.child(Constants.PATH_ROOMS).child(roomId).child(Constants.PATH_REACTIONS)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = mutableMapOf<String, Map<String, String>>()
                snapshot.children.forEach { msgSnap ->
                    val msgId = msgSnap.key ?: return@forEach
                    val perSession = mutableMapOf<String, String>()
                    msgSnap.children.forEach { sessionSnap ->
                        val sid = sessionSnap.key ?: return@forEach
                        val emoji = sessionSnap.getValue(String::class.java) ?: return@forEach
                        perSession[sid] = emoji
                    }
                    if (perSession.isNotEmpty()) result[msgId] = perSession
                }
                trySend(result)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── Cleanup / Archive ───────────────────────────────────────────────────

    suspend fun archiveReportedRoom(roomId: String) {
        try {
            val snapshot = root.child(Constants.PATH_ROOMS).child(roomId).get().await()
            if (snapshot.exists()) {
                root.child(Constants.PATH_REPORTED_ROOMS).child(roomId)
                    .setValue(snapshot.value).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("RTDB", "archiveReportedRoom failed: ${e.message}")
        }
    }

    fun cleanupRoom(roomId: String) {
        root.child(Constants.PATH_ROOMS).child(roomId).removeValue()
    }

    companion object {
        @Volatile private var instance: RealtimeDbManager? = null
        fun getInstance(): RealtimeDbManager = instance ?: synchronized(this) {
            instance ?: RealtimeDbManager().also { instance = it }
        }
    }
}
