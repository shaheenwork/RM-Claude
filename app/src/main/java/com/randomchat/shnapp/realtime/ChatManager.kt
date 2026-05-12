package com.randomchat.shnapp.realtime

import android.util.Log
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.model.MessageStatus
import com.randomchat.shnapp.model.MessageType
import com.randomchat.shnapp.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ChatManager(
    private val sessionId: String,
    private val rtdb: RealtimeDbManager = RealtimeDbManager.getInstance()
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Single source of truth: LinkedHashMap<id, ChatMessage> ───────────────
    // Using a Map instead of a List makes duplicate keys IMPOSSIBLE by design.
    // The LazyColumn key = { it.id } can never crash because the map enforces
    // uniqueness. A mutex serialises all writes so there are no race conditions
    // between the flush coroutine and the listener coroutine.
    private val messageMap = LinkedHashMap<String, ChatMessage>()
    private val mapMutex = Mutex()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _strangerActivity = MutableStateFlow<String?>(null)
    val strangerActivity: StateFlow<String?> = _strangerActivity

    private val _roomStatus = MutableStateFlow<String?>(null)
    val roomStatus: StateFlow<String?> = _roomStatus

    // Map<messageId, Map<sessionId, emoji>>
    private val _reactions = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val reactions: StateFlow<Map<String, Map<String, String>>> = _reactions

    // Real-time draft text from stranger (null = not typing / cleared)
    private val _strangerDraftText = MutableStateFlow<String?>(null)
    val strangerDraftText: StateFlow<String?> = _strangerDraftText

    // Pending messages queued before a stranger is assigned
    private val pendingMessages = mutableListOf<ChatMessage>()

    private var activeRoomId: String? = null
    private var messageListenerJob: Job? = null
    private var typingListenerJob: Job? = null
    private var roomStatusJob: Job? = null
    private var reactionsJob: Job? = null
    private var draftListenerJob: Job? = null
    private var typingDebounceJob: Job? = null

    // Draft text broadcast state
    private var draftBroadcastJob: Job? = null
    private var draftExpireJob: Job? = null
    private var lastBroadcastedDraft: String? = null

    // Rate limiting
    private var messageCountThisMinute = 0
    private var minuteWindowStart = System.currentTimeMillis()

    // ── Map helpers ───────────────────────────────────────────────────────────

    /** Add or replace a message by its ID, then publish the sorted list. */
    private suspend fun upsert(msg: ChatMessage) {
        mapMutex.withLock {
            messageMap[msg.id] = msg
            _messages.value = messageMap.values.sortedBy { it.timestamp }
        }
    }

    /** Rename a message key (local UUID → Firebase push ID) atomically. */
    private suspend fun renameKey(oldId: String, newMsg: ChatMessage) {
        mapMutex.withLock {
            messageMap.remove(oldId)
            messageMap[newMsg.id] = newMsg
            _messages.value = messageMap.values.sortedBy { it.timestamp }
        }
    }

    /** Remove a message by ID (used when replacing pending with confirmed). */
    private suspend fun remove(id: String) {
        mapMutex.withLock {
            messageMap.remove(id)
            _messages.value = messageMap.values.sortedBy { it.timestamp }
        }
    }

    fun clearMessages() {
        // Cancel jobs synchronously so attachRoom() can't race and start new listeners
        // that would then be cancelled by the async coroutine below.
        messageListenerJob?.cancel()
        typingListenerJob?.cancel()
        roomStatusJob?.cancel()
        reactionsJob?.cancel()
        draftListenerJob?.cancel()
        draftBroadcastJob?.cancel()
        draftExpireJob?.cancel()
        messageListenerJob = null
        typingListenerJob = null
        roomStatusJob = null
        reactionsJob = null
        draftListenerJob = null
        draftBroadcastJob = null
        draftExpireJob = null
        lastBroadcastedDraft = null
        activeRoomId = null
        pendingMessages.clear()
        _reactions.value = emptyMap()
        _strangerDraftText.value = null
        scope.launch {
            mapMutex.withLock {
                messageMap.clear()
                _messages.value = emptyList()
            }
        }
    }

    // ── Pending messages (before stranger assigned) ───────────────────────────

    fun queuePendingMessage(content: String): ChatMessage {
        val msg = ChatMessage(
            id = "pending_${UUID.randomUUID()}",
            senderId = sessionId,
            content = content,
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            isOutgoing = true,
            timestamp = System.currentTimeMillis()
        )
        pendingMessages.add(msg)
        scope.launch { upsert(msg) }
        return msg
    }

    // ── Room attachment ───────────────────────────────────────────────────────

    fun attachRoom(roomId: String) {
        activeRoomId = roomId
        startMessageListener(roomId)
        startTypingListener(roomId)
        startRoomStatusListener(roomId)
        startReactionsListener(roomId)
        startDraftTextListener(roomId)
        flushPendingMessages(roomId)
    }

    private fun startReactionsListener(roomId: String) {
        reactionsJob?.cancel()
        reactionsJob = scope.launch {
            rtdb.observeReactions(roomId).collect { map ->
                _reactions.value = map
            }
        }
    }

    fun setReaction(messageId: String, sessionId: String, emoji: String?) {
        val roomId = activeRoomId ?: return
        rtdb.setReaction(roomId, messageId, sessionId, emoji)
    }

    // ── Draft text (real-time typing preview) ─────────────────────────────────

    private fun startDraftTextListener(roomId: String) {
        draftListenerJob?.cancel()
        draftListenerJob = scope.launch {
            rtdb.observeDraftText(roomId, sessionId).collect { text ->
                // Reset auto-expire timer on every RTDB push
                draftExpireJob?.cancel()
                _strangerDraftText.value = text
                if (text != null) {
                    draftExpireJob = scope.launch {
                        delay(Constants.DRAFT_TEXT_EXPIRE_MS)
                        // Stranger stopped typing without clearing — hide ghost
                        _strangerDraftText.value = null
                    }
                }
            }
        }
    }

    /**
     * Broadcast the local user's current draft to RTDB with a short debounce.
     * Empty text → immediately removes the node so stranger's ghost disappears.
     */
    fun broadcastDraftText(text: String) {
        val roomId = activeRoomId ?: return
        // Skip redundant writes
        if (text == lastBroadcastedDraft) return
        draftBroadcastJob?.cancel()
        if (text.isEmpty()) {
            lastBroadcastedDraft = null
            rtdb.setDraftText(roomId, sessionId, null)
            return
        }
        draftBroadcastJob = scope.launch {
            delay(Constants.DRAFT_TEXT_DEBOUNCE_MS)
            lastBroadcastedDraft = text
            rtdb.setDraftText(roomId, sessionId, text)
        }
    }

    /** Remove our own draft from RTDB (called on send or chat end). */
    fun clearDraftText() {
        val roomId = activeRoomId ?: return
        draftBroadcastJob?.cancel()
        lastBroadcastedDraft = null
        rtdb.setDraftText(roomId, sessionId, null)
    }

    private fun flushPendingMessages(roomId: String) {
        scope.launch {
            val toFlush = pendingMessages.toList()
            pendingMessages.clear()
            toFlush.forEach { pending ->
                try {
                    val firebaseId = rtdb.sendMessage(roomId, pending)
                    // Atomically rename the pending entry to the Firebase push ID.
                    // If the listener already added the confirmed message (firebaseId),
                    // just remove the pending entry — the map already has the right copy.
                    mapMutex.withLock {
                        if (messageMap.containsKey(firebaseId)) {
                            // Listener beat us — drop the pending copy
                            messageMap.remove(pending.id)
                        } else {
                            // We beat the listener — rename pending → confirmed
                            val confirmed = pending.copy(
                                id = firebaseId,
                                status = MessageStatus.SENT
                            )
                            messageMap.remove(pending.id)
                            messageMap[firebaseId] = confirmed
                        }
                        _messages.value = messageMap.values.sortedBy { it.timestamp }
                    }
                } catch (e: Exception) {
                    Log.w("ChatManager", "flushPendingMessages failed: ${e.message}")
                }
            }
        }
    }

    // ── Message listener ──────────────────────────────────────────────────────

    private fun startMessageListener(roomId: String) {
        messageListenerJob?.cancel()
        messageListenerJob = scope.launch {
            rtdb.observeMessages(roomId).collect { incoming ->
                val isOutgoing = incoming.senderId == sessionId
                val confirmed = incoming.copy(isOutgoing = isOutgoing)

                mapMutex.withLock {
                    // If the map already has this ID (from optimistic insert or
                    // a previous onChildAdded burst), upsert just refreshes it —
                    // the key stays unique, no crash possible.
                    messageMap[confirmed.id] = confirmed
                    _messages.value = messageMap.values.sortedBy { it.timestamp }
                }
                Log.d("ChatManager", "onChildAdded id=${incoming.id} out=$isOutgoing total=${messageMap.size}")
            }
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    fun sendMessage(
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String = "",
        durationMs: Long = 0L
    ): ChatMessage? {
        if (!checkRateLimit()) return null

        val roomId = activeRoomId ?: run {
            val pending = ChatMessage(
                id = "pending_${UUID.randomUUID()}",
                senderId = sessionId,
                content = content,
                mediaUrl = mediaUrl,
                type = type,
                status = MessageStatus.PENDING,
                isOutgoing = true,
                timestamp = System.currentTimeMillis(),
                durationMs = durationMs
            )
            pendingMessages.add(pending)
            scope.launch { upsert(pending) }
            return pending
        }

        // Clear draft immediately so stranger's ghost disappears before our message arrives
        clearDraftText()

        // Show optimistically with a local ID
        val localId = "local_${UUID.randomUUID()}"
        val optimistic = ChatMessage(
            id = localId,
            roomId = roomId,
            senderId = sessionId,
            content = content,
            mediaUrl = mediaUrl,
            type = type,
            status = MessageStatus.SENT,
            isOutgoing = true,
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs
        )
        scope.launch {
            upsert(optimistic)

            try {
                val firebaseId = rtdb.sendMessage(roomId, optimistic)
                // Atomically swap local_ entry for the confirmed Firebase ID.
                // If the listener already inserted firebaseId, just drop the local copy.
                mapMutex.withLock {
                    if (messageMap.containsKey(firebaseId)) {
                        messageMap.remove(localId)
                    } else {
                        val confirmed = optimistic.copy(
                            id = firebaseId,
                            status = MessageStatus.SENT
                        )
                        messageMap.remove(localId)
                        messageMap[firebaseId] = confirmed
                    }
                    _messages.value = messageMap.values.sortedBy { it.timestamp }
                }
            } catch (e: Exception) {
                Log.w("ChatManager", "sendMessage write failed: ${e.message}")
                // Keep optimistic bubble — don't crash
            }
        }
        return optimistic
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun addPendingMediaMessage(localId: String, type: MessageType) {
        val msg = ChatMessage(
            id = localId,
            senderId = sessionId,
            content = "",
            mediaUrl = "",
            type = type,
            status = MessageStatus.PENDING,
            isOutgoing = true,
            timestamp = System.currentTimeMillis()
        )
        scope.launch { upsert(msg) }
    }

    fun removePendingMessage(id: String) {
        scope.launch { remove(id) }
    }

    fun addSystemMessage(text: String) {
        val msg = ChatMessage(
            id = "sys_${UUID.randomUUID()}",
            type = MessageType.SYSTEM,
            content = text,
            isOutgoing = false,
            timestamp = System.currentTimeMillis()
        )
        scope.launch { upsert(msg) }
    }

    fun notifyTyping(isTyping: Boolean) {
        val roomId = activeRoomId ?: return
        typingDebounceJob?.cancel()
        rtdb.setTyping(roomId, sessionId, isTyping)
        if (isTyping) {
            typingDebounceJob = scope.launch {
                delay(Constants.TYPING_INDICATOR_TIMEOUT_MS)
                rtdb.setTyping(roomId, sessionId, false)
            }
        }
    }

    suspend fun endChat() {
        val roomId = activeRoomId ?: return
        clearDraftText() // ensure ghost is removed on the other side immediately
        try {
            rtdb.endRoom(roomId)
            rtdb.setOffline(sessionId)
        } catch (e: Exception) {
            Log.w("ChatManager", "endChat failed: ${e.message}")
        }
    }

    fun setActivity(activity: String?) {
        val roomId = activeRoomId ?: return
        rtdb.setActivity(roomId, sessionId, activity)
    }

    private fun startTypingListener(roomId: String) {
        typingListenerJob?.cancel()
        typingListenerJob = scope.launch {
            rtdb.observeActivity(roomId, sessionId).collect { activity ->
                _strangerActivity.value = activity
            }
        }
    }

    private fun startRoomStatusListener(roomId: String) {
        roomStatusJob?.cancel()
        roomStatusJob = scope.launch {
            rtdb.observeRoomStatus(roomId).collect { status ->
                _roomStatus.value = status
            }
        }
    }

    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - minuteWindowStart > 60_000) {
            minuteWindowStart = now
            messageCountThisMinute = 0
        }
        messageCountThisMinute++
        return messageCountThisMinute <= Constants.MAX_MESSAGES_PER_MINUTE
    }

    fun destroy() {
        synchronized(Companion) { instance = null } // clear before cancel so next getInstance() gets a fresh object
        messageListenerJob?.cancel()
        typingListenerJob?.cancel()
        roomStatusJob?.cancel()
        reactionsJob?.cancel()
        draftListenerJob?.cancel()
        draftBroadcastJob?.cancel()
        draftExpireJob?.cancel()
        typingDebounceJob?.cancel()
        scope.cancel()
    }

    companion object {
        @Volatile private var instance: ChatManager? = null

        fun getInstance(sessionId: String): ChatManager {
            return instance ?: synchronized(this) {
                instance ?: ChatManager(sessionId).also { instance = it }
            }
        }

        fun resetInstance() {
            instance?.destroy()
            instance = null
        }
    }
}
