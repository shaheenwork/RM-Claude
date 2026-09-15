package com.randomchat.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Compresses image bytes to a chat-friendly size.
 *
 * Strategy (applied in order until target size is met):
 *   1. Read EXIF orientation and auto-rotate so the receiver sees it correctly.
 *   2. Scale long-side to MAX_DIMENSION (1024 px) — eliminates most of the bulk.
 *   3. JPEG-encode at PRIMARY_QUALITY (72).
 *   4. If still > TARGET_BYTES, re-encode at FALLBACK_QUALITY (50).
 *   5. If still > TARGET_BYTES, scale down again (×0.75) and re-encode at 50.
 *
 * Chat photos look fine at 1024 px / 72 % quality (typically 60–180 KB).
 * Original bytes returned unchanged only if decoding fails.
 */
object ImageCompressor {

    private const val MAX_DIMENSION    = 512        // 512 px long-side keeps faces readable
    private const val PRIMARY_QUALITY  = 50
    private const val FALLBACK_QUALITY = 35
    private const val TARGET_BYTES     = 30_000    // 30 KB ceiling

    fun compress(rawBytes: ByteArray): ByteArray {
        // 1. Decode
        val bitmap = decode(rawBytes) ?: return rawBytes

        // 2. Auto-rotate via EXIF
        val rotated = applyExifRotation(bitmap, rawBytes)

        // 3. Scale
        val scaled = scaleTo(rotated, MAX_DIMENSION)

        // 4. First encode attempt
        var result = encode(scaled, PRIMARY_QUALITY)
        if (result.size <= TARGET_BYTES) return result

        // 5. Fallback quality
        result = encode(scaled, FALLBACK_QUALITY)
        if (result.size <= TARGET_BYTES) return result

        // 6. Scale down to 60 % + fallback quality — last resort
        val smaller = scaleTo(scaled, (MAX_DIMENSION * 0.60).toInt())
        return encode(smaller, FALLBACK_QUALITY)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        // Sample-decode to avoid OOM on huge camera shots
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val longSide = maxOf(opts.outWidth, opts.outHeight)
        opts.inSampleSize = sampleSize(longSide, MAX_DIMENSION * 2) // generous: full decode still scaled later
        opts.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()

    private fun applyExifRotation(bmp: Bitmap, raw: ByteArray): Bitmap = runCatching {
        val exif = ExifInterface(ByteArrayInputStream(raw))
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else                                 -> return@runCatching bmp
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }.getOrDefault(bmp)

    private fun scaleTo(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width; val h = bmp.height
        if (w <= maxDim && h <= maxDim) return bmp
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
    }

    private fun encode(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Returns the largest power-of-2 sample size such that decoded size ≥ target. */
    private fun sampleSize(actual: Int, target: Int): Int {
        var s = 1
        while (actual / (s * 2) >= target) s *= 2
        return s
    }
}
