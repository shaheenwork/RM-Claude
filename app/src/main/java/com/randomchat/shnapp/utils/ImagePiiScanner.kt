package com.randomchat.shnapp.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Scans an image for personal info (phone, email, social handle) using on-device ML Kit OCR.
 * On-device only — image bytes never leave the phone.
 *
 * Returns the first detected PII kind, or null if clean / unreadable.
 * First-call latency: 1-2s while ML Kit warms up. Subsequent calls 50-300ms.
 */
object ImagePiiScanner {

    private const val TAG = "ImagePiiScanner"

    /** Max edge length — preserves readable text while avoiding OOM on 50MP cameras. */
    private const val MAX_EDGE_PX = 1920

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun scan(bytes: ByteArray): PiiDetector.Kind? {
        val bmp = decodeSampled(bytes) ?: run {
            Log.w(TAG, "decodeByteArray returned null (size=${bytes.size})")
            return null
        }
        return scan(bmp)
    }

    suspend fun scan(bitmap: Bitmap): PiiDetector.Kind? {
        // ML Kit requires ARGB_8888. Convert if needed.
        val safeBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
                        else bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val image = try {
            InputImage.fromBitmap(safeBitmap, 0)
        } catch (e: Exception) {
            Log.e(TAG, "fromBitmap failed: ${e.message}")
            return null
        }

        val text = try { recognizeText(image) } catch (e: Exception) {
            Log.e(TAG, "recognizeText threw: ${e.message}", e)
            ""
        }

        Log.d(TAG, "OCR result (${text.length} chars): ${text.take(200).replace("\n", " | ")}")

        if (text.isBlank()) return null
        val kind = PiiDetector.detect(text)
        if (kind != null) Log.i(TAG, "PII detected in image: $kind")
        return kind
    }

    /** Decode with downsampling — protects against OOM on multi-MP camera shots. */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        // Probe dimensions
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        val maxDim = maxOf(probe.outWidth, probe.outHeight)
        if (maxDim <= 0) return null

        // Use ceiling, not power-of-two — keeps more detail for OCR
        val sample = maxOf(1, (maxDim + MAX_EDGE_PX - 1) / MAX_EDGE_PX)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        Log.d(TAG, "decoding ${probe.outWidth}x${probe.outHeight}, inSampleSize=$sample")
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM decoding bitmap", e)
            null
        }
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit recognition failed: ${e.message}", e)
                    cont.resume("")
                }
        }
}
