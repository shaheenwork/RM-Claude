package com.randomchat.shnapp.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImageCompressor {

    private const val MAX_DIMENSION = 640
    private const val TARGET_BYTES = 30 * 1024
    private const val QUALITY_START = 72
    private const val QUALITY_MIN = 20
    private const val QUALITY_STEP = 10

    fun compress(input: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, options)

        val scale = maxOf(options.outWidth, options.outHeight).toFloat() / MAX_DIMENSION
        val sampleSize = if (scale > 1f) scale.toInt() else 1

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(input, 0, input.size, decodeOptions)
            ?: return input

        val scaled = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
            val ratio = bitmap.width.toFloat() / bitmap.height
            val (w, h) = if (bitmap.width >= bitmap.height)
                MAX_DIMENSION to (MAX_DIMENSION / ratio).toInt()
            else
                (MAX_DIMENSION * ratio).toInt() to MAX_DIMENSION
            Bitmap.createScaledBitmap(bitmap, w, h, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else bitmap

        val out = ByteArrayOutputStream()
        var quality = QUALITY_START
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= QUALITY_STEP
        } while (out.size() > TARGET_BYTES && quality >= QUALITY_MIN)

        scaled.recycle()
        return out.toByteArray()
    }
}
