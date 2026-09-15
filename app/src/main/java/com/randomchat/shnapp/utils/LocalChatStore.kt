package com.randomchat.shnapp.utils

import android.content.Context
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.model.MessageStatus
import com.randomchat.shnapp.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

data class SavedChatMeta(
    val id: String,
    val savedAt: Long,
    val preview: String,
    val messageCount: Int
)

object LocalChatStore {

    private fun chatsDir(context: Context) =
        File(context.filesDir, "saved_chats").also { it.mkdirs() }

    private fun chatDir(context: Context, chatId: String) =
        File(chatsDir(context), chatId).also { it.mkdirs() }

    private fun mediaDir(context: Context, chatId: String) =
        File(chatDir(context, chatId), "media").also { it.mkdirs() }

    private fun indexFile(context: Context) = File(chatsDir(context), "index.json")

    private fun chatFile(context: Context, chatId: String) =
        File(chatDir(context, chatId), "chat.json")

    /**
     * @param includeMedia if true, downloads images/audio to local files and replaces URLs
     * @param onProgress called after each media file download: (done, total)
     */
    suspend fun saveChat(
        context: Context,
        chatId: String,
        messages: List<ChatMessage>,
        includeMedia: Boolean = false,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): SavedChatMeta = withContext(Dispatchers.IO) {
        val saveable = messages.filter { it.status != MessageStatus.PENDING }

        val resolved: List<ChatMessage> = if (includeMedia) {
            val mediaItems = saveable.filter {
                it.mediaUrl.isNotBlank() && !it.mediaUrl.startsWith("/") &&
                        (it.type == MessageType.IMAGE || it.type == MessageType.AUDIO)
            }
            val total = mediaItems.size
            var done = 0
            saveable.map { msg ->
                if (msg.mediaUrl.isNotBlank() && !msg.mediaUrl.startsWith("/") &&
                    (msg.type == MessageType.IMAGE || msg.type == MessageType.AUDIO)
                ) {
                    val result = try {
                        val ext = if (msg.type == MessageType.AUDIO) "aac" else "jpg"
                        val localFile = File(mediaDir(context, chatId), "${msg.id.takeLast(12)}.$ext")
                        URL(msg.mediaUrl).openStream().use { input ->
                            localFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        msg.copy(mediaUrl = localFile.absolutePath)
                    } catch (_: Exception) { msg }
                    done++
                    onProgress?.invoke(done, total)
                    result
                } else msg
            }
        } else {
            saveable
        }

        writeMessagesJson(context, chatId, resolved)

        val preview = saveable.lastOrNull { it.type == MessageType.TEXT }?.content?.take(80) ?: ""
        val meta = SavedChatMeta(chatId, System.currentTimeMillis(), preview, saveable.size)

        val index = readIndex(context).toMutableList()
        index.removeAll { it.id == chatId }
        index.add(0, meta)
        writeIndex(context, index)

        meta
    }

    private fun writeMessagesJson(context: Context, chatId: String, messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(JSONObject().apply {
                put("id", msg.id)
                put("senderId", msg.senderId)
                put("content", msg.content)
                put("mediaUrl", msg.mediaUrl)
                put("type", msg.type.name)
                put("timestamp", msg.timestamp)
                put("isOutgoing", msg.isOutgoing)
            })
        }
        chatFile(context, chatId).writeText(arr.toString())
    }

    fun getSavedChats(context: Context): List<SavedChatMeta> = readIndex(context)

    fun getChatMessages(context: Context, chatId: String): List<ChatMessage> {
        val file = chatFile(context, chatId)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    ChatMessage(
                        id = o.getString("id"),
                        senderId = o.getString("senderId"),
                        content = o.getString("content"),
                        mediaUrl = o.getString("mediaUrl"),
                        type = MessageType.valueOf(o.getString("type")),
                        timestamp = o.getLong("timestamp"),
                        isOutgoing = o.getBoolean("isOutgoing"),
                        status = MessageStatus.DELIVERED
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun deleteChat(context: Context, chatId: String) {
        chatDir(context, chatId).deleteRecursively()
        val index = readIndex(context).toMutableList()
        index.removeAll { it.id == chatId }
        writeIndex(context, index)
    }

    /** Wipes every saved chat + the index. For account deletion. */
    fun clearAll(context: Context) {
        runCatching {
            readIndex(context).forEach { meta -> chatDir(context, meta.id).deleteRecursively() }
            indexFile(context).delete()
        }
    }

    private fun readIndex(context: Context): List<SavedChatMeta> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    SavedChatMeta(
                        id = o.getString("id"),
                        savedAt = o.getLong("savedAt"),
                        preview = o.getString("preview"),
                        messageCount = o.getInt("messageCount")
                    )
                } catch (_: Exception) { null }
            }.sortedByDescending { it.savedAt }
        } catch (_: Exception) { emptyList() }
    }

    private fun writeIndex(context: Context, metas: List<SavedChatMeta>) {
        val arr = JSONArray()
        metas.forEach { meta ->
            arr.put(JSONObject().apply {
                put("id", meta.id)
                put("savedAt", meta.savedAt)
                put("preview", meta.preview)
                put("messageCount", meta.messageCount)
            })
        }
        indexFile(context).writeText(arr.toString())
    }
}
