package com.randomchat.shnapp.firebase

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

class StorageManager {

    private val root = FirebaseStorage.getInstance().reference

    suspend fun uploadImage(bytes: ByteArray, sessionId: String): String {
        val ref = root.child("images/$sessionId/${UUID.randomUUID()}.jpg")
        withTimeout(60_000L) { ref.putBytes(bytes).await() }
        return withTimeout(15_000L) { ref.downloadUrl.await().toString() }
    }

    suspend fun uploadAudio(bytes: ByteArray, sessionId: String): String {
        val ref = root.child("audio/$sessionId/${UUID.randomUUID()}.aac")
        withTimeout(60_000L) { ref.putBytes(bytes).await() }
        return withTimeout(15_000L) { ref.downloadUrl.await().toString() }
    }

    companion object {
        @Volatile private var instance: StorageManager? = null
        fun getInstance(): StorageManager = instance ?: synchronized(this) {
            instance ?: StorageManager().also { instance = it }
        }
    }
}
