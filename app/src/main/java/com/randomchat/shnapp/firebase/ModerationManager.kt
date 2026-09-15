package com.randomchat.shnapp.firebase

import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.containsProfanity

class ModerationManager(
    private val firestore: FirestoreManager = FirestoreManager.getInstance()
) {

    private val floodTracker = mutableMapOf<String, MutableList<Long>>()

    fun filterMessage(content: String): String {
        if (!content.containsProfanity()) return content
        return content.replace(Regex("[a-zA-Z]".toRegex(RegexOption.IGNORE_CASE).pattern)) { match ->
            if (Constants.PROFANITY_LIST.any { content.lowercase().contains(it) }) "*"
            else match.value
        }
    }

    fun isFloodSpam(sessionId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = floodTracker.getOrPut(sessionId) { mutableListOf() }
        timestamps.removeAll { now - it > 60_000 }
        timestamps.add(now)
        return timestamps.size > Constants.MAX_MESSAGES_PER_MINUTE
    }

    suspend fun handleSpam(sessionId: String) {
        firestore.banSession(sessionId, Constants.SPAM_BAN_DURATION_MINUTES)
    }

    suspend fun isUserBanned(sessionId: String): Boolean {
        return firestore.isSessionBanned(sessionId)
    }

    companion object {
        @Volatile private var instance: ModerationManager? = null
        fun getInstance(): ModerationManager = instance ?: synchronized(this) {
            instance ?: ModerationManager().also { instance = it }
        }
    }
}
