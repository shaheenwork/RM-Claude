package com.randomchat.shnapp.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randomchat.shnapp.firebase.FirestoreManager
import com.randomchat.shnapp.utils.LocalChatStore
import com.randomchat.shnapp.firebase.ModerationManager
import com.randomchat.shnapp.firebase.StorageManager
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.model.MessageStatus
import com.randomchat.shnapp.model.MessageType
import com.randomchat.shnapp.realtime.ChatManager
import com.randomchat.shnapp.realtime.MatchmakingManager
import com.randomchat.shnapp.realtime.MatchmakingState
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.ImageCompressor
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class SaveProgress(val done: Int, val total: Int, val isDone: Boolean = false)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager.getInstance(app)
    val sessionId: String get() = sessionManager.sessionId

    private val matchmakingManager = MatchmakingManager.getInstance(sessionId)
    private val chatManager = ChatManager.getInstance(sessionId)
    private val firestore = FirestoreManager.getInstance()
    private val moderation = ModerationManager.getInstance()
    private val storageManager = StorageManager.getInstance()
    private val rtdb = com.randomchat.shnapp.realtime.RealtimeDbManager.getInstance()

    // ── Exposed state ──────────────────────────────────────────────────────────
    val messages: StateFlow<List<ChatMessage>> = chatManager.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val strangerActivity: StateFlow<String?> = chatManager.strangerActivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Map<messageId, Map<sessionId, emoji>>
    val reactions: StateFlow<Map<String, Map<String, String>>> = chatManager.reactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val roomStatus: StateFlow<String?> = chatManager.roomStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val strangerDraftText: StateFlow<String?> = chatManager.strangerDraftText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPremium: StateFlow<Boolean> = sessionManager.isPremiumFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasSavedFirstChat: StateFlow<Boolean> = sessionManager.hasSavedFirstChatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Remaining rewarded photo sends for non-premium users. */
    val rewardedPhotoCredits: StateFlow<Int> = sessionManager.rewardedPhotoCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Remaining rewarded audio sends for non-premium users. */
    val rewardedAudioCredits: StateFlow<Int> = sessionManager.rewardedAudioCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Remaining rewarded GIF sends earned via ads. */
    val rewardedGifCredits: StateFlow<Int> = sessionManager.rewardedGifCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Whether this premium user has opted to broadcast their badge (default: true). */
    val showMyBadge: StateFlow<Boolean> = sessionManager.showPremiumBadgeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Whether the connected stranger is showing a premium badge. */
    private val _strangerHasBadge = MutableStateFlow(false)
    val strangerHasBadge: StateFlow<Boolean> = _strangerHasBadge

    private val _isStrangerConnected = MutableStateFlow(false)
    val isStrangerConnected: StateFlow<Boolean> = _isStrangerConnected

    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId: StateFlow<String?> = _currentRoomId

    private val _currentStrangerId = MutableStateFlow<String?>(null)
    val currentStrangerId: StateFlow<String?> = _currentStrangerId

    private val _chatEnded = MutableStateFlow(false)
    val chatEnded: StateFlow<Boolean> = _chatEnded

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs

    private val _triggerInterstitial = MutableStateFlow(false)
    val triggerInterstitial: StateFlow<Boolean> = _triggerInterstitial

    private val _saveProgress = MutableStateFlow<SaveProgress?>(null)
    val saveProgress: StateFlow<SaveProgress?> = _saveProgress

    /** True once the current chat session has been saved (via premium or rewarded ad). */
    private val _chatSaved = MutableStateFlow(false)
    val chatSaved: StateFlow<Boolean> = _chatSaved

    private val _reportStatus = MutableStateFlow<String?>(null)
    val reportStatus: StateFlow<String?> = _reportStatus

    // Ensures at most one interstitial fires per chat session, regardless of how
    // many times endChat() is called (user ends + then taps "New Stranger").
    private var adFiredForCurrentChat = false

    private var mediaRecorder: MediaRecorder? = null
    private var audioTempFile: File? = null
    private var recordingTimerJob: kotlinx.coroutines.Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        observeMatchmaking()
        observeRoomStatus()
        viewModelScope.launch { rtdb.fetchAndStoreIp(sessionId) }
    }

    // ── Matchmaking observation ───────────────────────────────────────────────

    private fun observeMatchmaking() {
        viewModelScope.launch {
            matchmakingManager.state.collect { state ->
                when (state) {
                    is MatchmakingState.Matched -> {
                        val roomId = state.roomId
                        val strangerId = state.strangerId
                        _currentRoomId.value = roomId
                        _currentStrangerId.value = strangerId
                        _isStrangerConnected.value = true
                        _strangerHasBadge.value = false

                        // Broadcast own premium badge immediately on match
                        if (isPremium.value && showMyBadge.value) {
                            rtdb.setPremiumBadge(roomId, sessionId, true)
                        }

                        // Observe stranger's premium badge for this room
                        launch {
                            rtdb.observeStrangerBadge(roomId, sessionId).collect { hasBadge ->
                                _strangerHasBadge.value = hasBadge
                            }
                        }

                        chatManager.attachRoom(roomId)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeRoomStatus() {
        viewModelScope.launch {
            roomStatus.collect { status ->
                if (status == "ENDED" && _isStrangerConnected.value) {
                    _chatEnded.value = true
                    // Clean up assignment so the next startSearch() doesn't re-attach to this room
                    matchmakingManager.cancelSearch()
                }
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun startSearch() {
        chatManager.clearMessages()
        _isStrangerConnected.value = false
        _chatEnded.value = false
        _strangerHasBadge.value = false
        _currentRoomId.value = null
        _currentStrangerId.value = null
        adFiredForCurrentChat = false
        _chatSaved.value = false
        _replyingTo.value = null  // clear any pending reply from previous chat
        matchmakingManager.startSearch()
    }

    /**
     * Toggle the user's own premium badge visibility.
     * Persists preference and immediately updates RTDB if in an active room.
     */
    fun toggleMyBadge() {
        viewModelScope.launch {
            val newValue = !showMyBadge.value
            sessionManager.setShowPremiumBadge(newValue)
            _currentRoomId.value?.let { roomId ->
                if (isPremium.value) {
                    rtdb.setPremiumBadge(roomId, sessionId, newValue)
                }
            }
        }
    }

    // ── Reply state (swipe-to-reply) ──────────────────────────────────────────
    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo

    fun startReply(message: ChatMessage) {
        // Only allow reply to confirmed (non-pending) text/image/audio
        if (message.status == MessageStatus.PENDING) return
        if (message.type == MessageType.SYSTEM) return
        _replyingTo.value = message
    }

    fun cancelReply() { _replyingTo.value = null }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (content.length > Constants.MAX_MESSAGE_LENGTH) return
        if (moderation.isFloodSpam(sessionId)) return

        val filtered = moderation.filterMessage(content)
        val replyContext = _replyingTo.value
        chatManager.sendMessage(filtered, MessageType.TEXT, replyTo = replyContext)
        _replyingTo.value = null  // clear after send
    }

    fun sendMediaMessage(mediaUrl: String, type: MessageType) {
        chatManager.sendMessage("", type, mediaUrl)
    }

    fun notifyTyping(isTyping: Boolean) {
        chatManager.notifyTyping(isTyping)
    }

    /**
     * Called on every keystroke. Broadcasts the live draft to RTDB (debounced).
     * Sending/clearing clears draft automatically via ChatManager.
     */
    fun broadcastDraftText(text: String) {
        chatManager.broadcastDraftText(text)
    }

    /**
     * Toggle reaction on a message.
     * If the user already reacted with the same emoji → remove it.
     * If different emoji → replace.
     */
    fun reactToMessage(messageId: String, emoji: String) {
        val current = reactions.value[messageId]?.get(sessionId)
        val newEmoji = if (current == emoji) null else emoji
        chatManager.setReaction(messageId, sessionId, newEmoji)
    }

    fun setActivity(activity: String?) {
        chatManager.setActivity(activity)
    }

    fun endChat() {
        viewModelScope.launch {
            chatManager.endChat()
            _chatEnded.value = true
            matchmakingManager.cancelSearch()
            sessionManager.markFirstChatEnded()  // signals MainActivity to ask notif permission
            if (!isPremium.value && !adFiredForCurrentChat) {
                adFiredForCurrentChat = true
                _triggerInterstitial.value = true
            }
        }
    }

    fun interstitialShown() {
        _triggerInterstitial.value = false
    }

    fun reportStranger(reason: String) {
        val roomId = _currentRoomId.value
        val strangerId = _currentStrangerId.value
        Log.d("ChatViewModel", "reportStranger: roomId=$roomId strangerId=$strangerId reason=$reason")
        if (roomId == null || strangerId == null) {
            Log.w("ChatViewModel", "reportStranger: null IDs, cannot submit")
            _reportStatus.value = "error"
            return
        }
        viewModelScope.launch {
            try {
                // Fetch both IPs and archive room concurrently
                val reporterIpDeferred = async { rtdb.readIp(sessionId) }
                val reportedIpDeferred = async { rtdb.readIp(strangerId) }
                val archiveJob = launch { rtdb.archiveReportedRoom(roomId) }

                val reporterIp = reporterIpDeferred.await()
                val reportedIp = reportedIpDeferred.await()
                archiveJob.join()

                firestore.reportSession(sessionId, strangerId, roomId, reason, reporterIp, reportedIp)
                Log.d("ChatViewModel", "reportStranger: success reporterIp=$reporterIp reportedIp=$reportedIp")
                _reportStatus.value = "success"
            } catch (e: Exception) {
                Log.e("ChatViewModel", "reportStranger failed: ${e.message}", e)
                _reportStatus.value = "error"
            }
        }
    }

    fun clearReportStatus() {
        _reportStatus.value = null
    }

    fun hasMediaMessages(): Boolean = messages.value.any {
        (it.type == MessageType.IMAGE || it.type == MessageType.AUDIO) &&
                it.mediaUrl.isNotBlank() && it.status != MessageStatus.PENDING
    }

    fun saveChat() {
        // Fall back to a stable UUID so a null room ID never silently drops the save.
        val roomId = _currentRoomId.value ?: UUID.randomUUID().toString().replace("-", "").take(20)
        val msgs = messages.value
        val mediaCount = msgs.count {
            it.mediaUrl.isNotBlank() && !it.mediaUrl.startsWith("/") &&
                    (it.type == MessageType.IMAGE || it.type == MessageType.AUDIO) &&
                    it.status != MessageStatus.PENDING
        }
        if (mediaCount > 0) _saveProgress.value = SaveProgress(0, mediaCount)

        viewModelScope.launch {
            try {
                LocalChatStore.saveChat(
                    getApplication(), roomId, msgs,
                    includeMedia = true,
                    onProgress = if (mediaCount > 0) { done, total ->
                        _saveProgress.value = SaveProgress(done, total, done == total)
                    } else null
                )
                sessionManager.markSavedFirstChat()
                _chatSaved.value = true
                if (mediaCount == 0) _saveProgress.value = SaveProgress(0, 0, isDone = true)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "saveChat failed: ${e.message}")
                _saveProgress.value = null
            }
        }
    }

    fun clearSaveProgress() {
        _saveProgress.value = null
    }

    /** Deduct one photo credit. Fire-and-forget; called just before upload starts. */
    fun consumePhotoCredit() {
        viewModelScope.launch { sessionManager.consumePhotoCredit() }
    }

    /** Deduct one audio credit. Fire-and-forget; called just before recording starts. */
    fun consumeAudioCredit() {
        viewModelScope.launch { sessionManager.consumeAudioCredit() }
    }

    /** Deduct one GIF credit. Fire-and-forget; called just after a GIF is sent. */
    fun consumeGifCredit() {
        viewModelScope.launch { sessionManager.consumeGifCredit() }
    }

    fun uploadAndSendImage(rawBytes: ByteArray) {
        val pendingId = "pending_img_${UUID.randomUUID()}"
        chatManager.addPendingMediaMessage(pendingId, MessageType.IMAGE)
        chatManager.setActivity("sending_photo")
        viewModelScope.launch {
            // Non-premium users spent a rewarded credit to reach here — deduct it now.
            if (!isPremium.value) sessionManager.consumePhotoCredit()
            try {
                // Compress on IO: scale to 1024 px long-side, JPEG 72 % → typically 60–180 KB.
                val compressed = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ImageCompressor.compress(rawBytes)
                }
                Log.d("ChatViewModel", "Image: ${rawBytes.size / 1024}KB → ${compressed.size / 1024}KB")
                val url = storageManager.uploadImage(compressed, sessionId)
                chatManager.setActivity(null)
                chatManager.removePendingMessage(pendingId)
                sendMediaMessage(url, MessageType.IMAGE)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Image upload failed: ${e.message}")
                chatManager.setActivity(null)
                chatManager.removePendingMessage(pendingId)
            }
        }
    }

    fun startAudioRecording() {
        if (_isRecording.value) return
        val ctx = getApplication<Application>()
        val file = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.aac")
        audioTempFile = file
        try {
            @Suppress("DEPRECATION")
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(ctx) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            _isRecording.value = true
            chatManager.setActivity("recording")
        val startTime = System.currentTimeMillis()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTime
                _recordingDurationMs.value = elapsed
                if (elapsed >= Constants.MAX_AUDIO_DURATION_MS) {
                    stopAndSendAudio()
                    break
                }
            }
        }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "startRecording failed: ${e.message}")
            mediaRecorder?.release()
            mediaRecorder = null
            audioTempFile?.delete()
            audioTempFile = null
        }
    }

    fun stopAndSendAudio() {
        if (!_isRecording.value) return
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        val durationMs = _recordingDurationMs.value
        _recordingDurationMs.value = 0L
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            _isRecording.value = false
            if (durationMs < 500L) { // discard very short recordings
                audioTempFile?.delete()
                audioTempFile = null
                return
            }
            val file = audioTempFile ?: return
            audioTempFile = null
            val pendingId = "pending_audio_${UUID.randomUUID()}"
            chatManager.addPendingMediaMessage(pendingId, MessageType.AUDIO)
            chatManager.setActivity("sending_audio")
            // Past all cancel/discard guards — audio is definitely uploading. Consume credit now.
            if (!isPremium.value) viewModelScope.launch { sessionManager.consumeAudioCredit() }
            viewModelScope.launch {
                try {
                    val bytes = file.readBytes()
                    val url = storageManager.uploadAudio(bytes, sessionId)
                    chatManager.setActivity(null)
                    chatManager.removePendingMessage(pendingId)
                    sendMediaMessage(url, MessageType.AUDIO)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Audio upload failed: ${e.message}")
                    chatManager.setActivity(null)
                    chatManager.removePendingMessage(pendingId)
                } finally {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "stopAudio failed: ${e.message}")
            mediaRecorder?.release()
            mediaRecorder = null
            _isRecording.value = false
        }
    }

    fun cancelAudioRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _recordingDurationMs.value = 0L
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        _isRecording.value = false
        chatManager.setActivity(null)
        audioTempFile?.delete()
        audioTempFile = null
    }

    fun newChat() {
        endChat()
        chatManager.clearMessages()
        matchmakingManager.reset()
    }

    override fun onCleared() {
        super.onCleared()
        matchmakingManager.destroy()
        chatManager.destroy()
        cancelAudioRecording()
    }
}
