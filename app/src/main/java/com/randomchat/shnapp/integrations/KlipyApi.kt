package com.randomchat.shnapp.integrations

import android.util.Log
import com.randomchat.shnapp.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Klipy GIF/sticker API client.
 *
 * Docs: https://docs.klipy.com
 * Auth: API key embedded in URL path (no Authorization header).
 *
 * Logs HTTP status + first 600 chars of response body on error — check
 * Logcat with tag `KlipyApi` if returns are empty to diagnose URL/response mismatch.
 */
data class Gif(
    val id: String,
    val previewUrl: String,
    val fullUrl: String,
    val title: String = ""
)

object KlipyApi {
    private const val TAG  = "KlipyApi"
    private const val BASE = "https://api.klipy.com/api/v1"

    suspend fun search(query: String, limit: Int = 24): List<Gif> = withContext(Dispatchers.IO) {
        if (!hasKey()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        fetch("$BASE/${Constants.KLIPY_CUSTOMER_ID}/gifs/search?q=$q&per_page=$limit&content_filter=high")
    }

    suspend fun trending(limit: Int = 24): List<Gif> = withContext(Dispatchers.IO) {
        if (!hasKey()) return@withContext emptyList()
        fetch("$BASE/${Constants.KLIPY_CUSTOMER_ID}/gifs/trending?per_page=$limit&content_filter=high")
    }

    private fun hasKey(): Boolean {
        if (Constants.KLIPY_CUSTOMER_ID == "REPLACE_WITH_KLIPY_CUSTOMER_ID") {
            Log.w(TAG, "KLIPY_CUSTOMER_ID not configured — see Constants.kt")
            return false
        }
        return true
    }

    private fun fetch(urlStr: String): List<Gif> {
        Log.d(TAG, "GET $urlStr")
        val conn = try {
            (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout    = 8000
                requestMethod  = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "StrangerChat-Android/1.0")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connection setup failed: ${e.message}")
            return emptyList()
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.d(TAG, "HTTP $code, body[0..${minOf(600, body.length)}]: ${body.take(600).replace('\n', ' ')}")
            if (code !in 200..299) {
                Log.w(TAG, "Non-2xx response: HTTP $code")
                return emptyList()
            }
            parse(body)
        } catch (e: Exception) {
            Log.w(TAG, "Fetch threw: ${e.message}", e)
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Defensive parser. Klipy response shape varies — we try multiple paths:
     *   1. result wrapper:  { "result": true, "data": { "data": [...] } }
     *   2. flat data:       { "data": [...] }
     *   3. items:           { "items": [...] }
     *   4. direct array:    [...]
     * Within each item, look for image URLs at common locations.
     */
    private fun parse(json: String): List<Gif> {
        return try {
            val arr = locateArray(json) ?: run {
                Log.w(TAG, "Couldn't locate result array in response")
                return emptyList()
            }
            Log.d(TAG, "Parsed array with ${arr.length()} items")
            (0 until arr.length()).mapNotNull { i ->
                runCatching { extractGif(arr.getJSONObject(i)) }.getOrNull()
            }.also {
                if (it.isEmpty()) Log.w(TAG, "Array had items but none parseable")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun locateArray(json: String): JSONArray? {
        // Try as array directly
        runCatching { return JSONArray(json) }
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        // Klipy nested: data.data
        root.optJSONObject("data")?.optJSONArray("data")?.let { return it }
        // data flat
        root.optJSONArray("data")?.let { return it }
        // items
        root.optJSONArray("items")?.let { return it }
        // results (Tenor/Giphy convention, just in case)
        root.optJSONArray("results")?.let { return it }
        return null
    }

    private fun extractGif(r: JSONObject): Gif? {
        // Klipy shape: file.{size}.{format}.url
        // size:   xs | sm | md | hd
        // format: gif | webp | mp4 | jpg
        val file = r.optJSONObject("file") ?: r.optJSONObject("media") ?: r.optJSONObject("images")
            ?: return null

        // Preview: prefer small sizes, gif/webp formats
        val preview = pickUrl(file, sizeOrder = listOf("sm", "xs", "md", "hd"), formatOrder = listOf("gif", "webp"))

        // Full: prefer large sizes, gif format (Coil handles best). webp as fallback.
        val full = pickUrl(file, sizeOrder = listOf("hd", "md", "sm", "xs"), formatOrder = listOf("gif", "webp"))

        if (preview.isNullOrBlank() || full.isNullOrBlank()) return null
        return Gif(
            id         = r.optString("id").ifBlank { preview.hashCode().toString() },
            previewUrl = preview,
            fullUrl    = full,
            title      = r.optString("title").ifBlank { r.optString("name") }
        )
    }

    /** Walks file.{size}.{format}.url in priority order until a non-blank URL is found. */
    private fun pickUrl(file: JSONObject, sizeOrder: List<String>, formatOrder: List<String>): String? {
        for (size in sizeOrder) {
            val sizeObj = file.optJSONObject(size) ?: continue
            for (format in formatOrder) {
                val formatObj = sizeObj.optJSONObject(format) ?: continue
                val url = formatObj.optString("url")
                if (url.isNotBlank()) return url
            }
            // Some endpoints may still flatten — try direct `url` on the size object
            val flatUrl = sizeObj.optString("url")
            if (flatUrl.isNotBlank()) return flatUrl
        }
        return null
    }
}
