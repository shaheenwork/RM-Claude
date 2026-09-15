package com.randomchat.shnapp.model

/**
 * Per-chat gender selection — user picks this each time before matchmaking starts.
 * Not persisted across sessions. Used server-side for the soft F-F match bias only;
 * never displayed to the other user inside chat.
 */
enum class Gender { MALE, FEMALE }
