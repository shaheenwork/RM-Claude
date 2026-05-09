package com.randomchat.shnapp.utils

import java.text.SimpleDateFormat
import java.util.*

fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun String.containsProfanity(): Boolean {
    val lower = this.lowercase()
    return Constants.PROFANITY_LIST.any { lower.contains(it) }
}

fun String.truncate(maxLength: Int): String {
    return if (length > maxLength) substring(0, maxLength) else this
}

fun generateRoomId(sessionA: String, sessionB: String): String {
    val sorted = listOf(sessionA, sessionB).sorted()
    return "${sorted[0]}_${sorted[1]}_${System.currentTimeMillis()}"
}
