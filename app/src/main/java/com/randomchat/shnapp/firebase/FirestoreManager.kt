package com.randomchat.shnapp.firebase

import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.model.MessageStatus
import com.randomchat.shnapp.utils.Constants
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreManager {

    private val db = Firebase.firestore("main")

    // ─── Premium / Subscription ───────────────────────────────────────────────

    suspend fun savePremiumStatus(sessionId: String, isPremium: Boolean, expiryMs: Long, purchaseToken: String) {
        db.collection(Constants.COL_SUBSCRIPTIONS).document(sessionId).set(
            mapOf(
                "sessionId" to sessionId,
                "isPremium" to isPremium,
                "expiryMs" to expiryMs,
                "purchaseToken" to purchaseToken,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun verifyPremiumStatus(sessionId: String): Boolean {
        return try {
            val doc = db.collection(Constants.COL_SUBSCRIPTIONS).document(sessionId).get().await()
            val isPremium = doc.getBoolean("isPremium") ?: false
            val expiryMs = doc.getLong("expiryMs") ?: 0L
            isPremium && (expiryMs == 0L || expiryMs > System.currentTimeMillis())
        } catch (e: Exception) { false }
    }

    // ─── Reports ─────────────────────────────────────────────────────────────

    suspend fun reportSession(
        reporterSessionId: String,
        reportedSessionId: String,
        roomId: String,
        reason: String,
        reporterIp: String? = null,
        reportedIp: String? = null
    ) {
        val data = mutableMapOf<String, Any>(
            "reporter" to reporterSessionId,
            "reported" to reportedSessionId,
            "roomId" to roomId,
            "reason" to reason,
            "status" to "pending",
            "timestamp" to System.currentTimeMillis()
        )
        reporterIp?.let { data["reporterIp"] = it }
        reportedIp?.let { data["reportedIp"] = it }
        db.collection(Constants.COL_REPORTS).add(data).await()
    }

    // ─── Bans ─────────────────────────────────────────────────────────────────

    suspend fun isSessionBanned(sessionId: String): Boolean {
        return try {
            val doc = db.collection(Constants.COL_BANNED_SESSIONS).document(sessionId).get().await()
            if (!doc.exists()) return false
            val bannedUntil = doc.getLong("bannedUntil") ?: 0L
            System.currentTimeMillis() < bannedUntil
        } catch (e: Exception) { false }
    }

    suspend fun banSession(sessionId: String, durationMinutes: Long) {
        val bannedUntil = System.currentTimeMillis() + (durationMinutes * 60_000)
        db.collection(Constants.COL_BANNED_SESSIONS).document(sessionId).set(
            mapOf("bannedUntil" to bannedUntil, "bannedAt" to System.currentTimeMillis())
        ).await()
    }

    // ─── Saved Chats ──────────────────────────────────────────────────────────

    suspend fun saveChat(sessionId: String, roomId: String, messages: List<ChatMessage>) {
        val docId = "${sessionId}_${roomId}"
        val data = mapOf(
            "sessionId" to sessionId,
            "roomId" to roomId,
            "savedAt" to System.currentTimeMillis(),
            "messageCount" to messages.size,
            "preview" to (messages.lastOrNull { it.type.name == "TEXT" }?.content?.take(80) ?: "")
        )
        val docRef = db.collection(Constants.COL_SAVED_CHATS).document(docId)
        docRef.set(data).await()

        // Persist messages in a subcollection (capped at 500)
        val batch = db.batch()
        messages.takeLast(500).forEach { msg ->
            val msgRef = docRef.collection("messages")
                .document(msg.id.ifBlank { UUID.randomUUID().toString() })
            batch.set(msgRef, mapOf(
                "senderId" to msg.senderId,
                "content" to msg.content,
                "mediaUrl" to msg.mediaUrl,
                "type" to msg.type.name,
                "timestamp" to msg.timestamp,
                "isOutgoing" to msg.isOutgoing
            ))
        }
        batch.commit().await()
    }

    suspend fun getSavedChats(sessionId: String): List<Map<String, Any>> {
        return try {
            db.collection(Constants.COL_SAVED_CHATS)
                .whereEqualTo("sessionId", sessionId)
                .orderBy("savedAt", Query.Direction.DESCENDING)
                .get().await()
                .documents.mapNotNull { doc -> doc.data?.plus("docId" to doc.id) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getSavedChatMessages(docId: String): List<ChatMessage> {
        return try {
            db.collection(Constants.COL_SAVED_CHATS).document(docId)
                .collection("messages")
                .orderBy("timestamp")
                .get().await()
                .documents.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            content = doc.getString("content") ?: "",
                            mediaUrl = doc.getString("mediaUrl") ?: "",
                            type = enumValueOf(doc.getString("type") ?: "TEXT"),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            isOutgoing = doc.getBoolean("isOutgoing") ?: false,
                            status = MessageStatus.DELIVERED
                        )
                    } catch (_: Exception) { null }
                }
        } catch (e: Exception) { emptyList() }
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    fun logEvent(sessionId: String, event: String, extras: Map<String, Any> = emptyMap()) {
        db.collection(Constants.COL_ANALYTICS).add(
            mapOf(
                "sessionId" to sessionId,
                "event" to event,
                "timestamp" to System.currentTimeMillis()
            ) + extras
        )
    }

    companion object {
        @Volatile private var instance: FirestoreManager? = null
        fun getInstance(): FirestoreManager = instance ?: synchronized(this) {
            instance ?: FirestoreManager().also { instance = it }
        }
    }
}
