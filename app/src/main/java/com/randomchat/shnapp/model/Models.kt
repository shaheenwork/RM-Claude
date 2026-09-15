package com.randomchat.shnapp.model

// ─── ChatMessage ─────────────────────────────────────────────────────────────

enum class MessageType {
    TEXT, IMAGE, AUDIO, SYSTEM
}

enum class MessageStatus {
    PENDING,   // locally queued, no stranger yet
    SENT,      // pushed to RTDB
    DELIVERED  // stranger received it
}

data class ChatMessage(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val content: String = "",           // text / system label
    val mediaUrl: String = "",          // image / audio URL
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,    // computed locally
    val durationMs: Long = 0L,          // audio duration (backward-compat default 0)
    // ── Reply context (Phase 3) ──────────────────────────────────────────────
    val replyToId: String = "",         // empty = not a reply
    val replyToPreview: String = "",    // short snippet of replied-to message (≤80 chars)
    val replyToSenderId: String = "",   // sender of the replied-to message
    val replyToType: MessageType = MessageType.TEXT  // type of the original (for icon hints)
)

// ─── ChatRoom ─────────────────────────────────────────────────────────────────

enum class RoomStatus {
    ACTIVE, ENDED, ABANDONED
}

data class ChatRoom(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val status: RoomStatus = RoomStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long = 0L
)

// ─── WaitingUser ──────────────────────────────────────────────────────────────

data class WaitingUser(
    val sessionId: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val platform: String = "android"
)

// ─── PremiumStatus ────────────────────────────────────────────────────────────

data class PremiumStatus(
    val isPremium: Boolean = false,
    val expiryMs: Long = 0L,
    val purchaseToken: String = ""
)
