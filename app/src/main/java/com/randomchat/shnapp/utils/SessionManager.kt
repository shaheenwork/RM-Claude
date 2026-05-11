package com.randomchat.shnapp.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stranger_chat_prefs")

class SessionManager(private val context: Context) {

    private val sessionIdKey = stringPreferencesKey(Constants.PREF_SESSION_ID)
    private val isPremiumKey = booleanPreferencesKey(Constants.PREF_IS_PREMIUM)
    private val premiumExpiryKey = longPreferencesKey(Constants.PREF_PREMIUM_EXPIRY)
    private val chatsSinceAdKey = longPreferencesKey(Constants.PREF_CHATS_SINCE_AD)
    private val hasSavedFirstChatKey = booleanPreferencesKey("has_saved_first_chat")
    private val notifPermAskedKey   = booleanPreferencesKey("notif_perm_asked")
    private val notifsEnabledKey    = booleanPreferencesKey("notifs_enabled")
    private val firstChatEndedKey    = booleanPreferencesKey("first_chat_ended")
    private val showPremiumBadgeKey  = booleanPreferencesKey("show_premium_badge")

    val sessionId: String by lazy {
        runBlocking {
            val prefs = context.dataStore.data.first()
            prefs[sessionIdKey] ?: run {
                val newId = UUID.randomUUID().toString().replace("-", "").take(20)
                context.dataStore.edit { it[sessionIdKey] = newId }
                newId
            }
        }
    }

    val isPremiumFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val isPremium = prefs[isPremiumKey] ?: false
        val expiry = prefs[premiumExpiryKey] ?: 0L
        isPremium && (expiry == 0L || expiry > System.currentTimeMillis())
    }

    suspend fun setPremium(isPremium: Boolean, expiryMs: Long = 0L) {
        context.dataStore.edit { prefs ->
            prefs[isPremiumKey] = isPremium
            prefs[premiumExpiryKey] = expiryMs
        }
    }

    suspend fun incrementChatsSinceAd(): Long {
        var count = 0L
        context.dataStore.edit { prefs ->
            count = (prefs[chatsSinceAdKey] ?: 0L) + 1
            prefs[chatsSinceAdKey] = count
        }
        return count
    }

    suspend fun resetChatsSinceAd() {
        context.dataStore.edit { it[chatsSinceAdKey] = 0L }
    }

    val hasSavedFirstChatFlow: Flow<Boolean> = context.dataStore.data
        .map { it[hasSavedFirstChatKey] ?: false }

    suspend fun markSavedFirstChat() {
        context.dataStore.edit { it[hasSavedFirstChatKey] = true }
    }

    // ── Notifications ──────────────────────────────────────────────────────────
    val notifPermAskedFlow: Flow<Boolean> = context.dataStore.data.map { it[notifPermAskedKey] ?: false }
    val notifsEnabledFlow:  Flow<Boolean> = context.dataStore.data.map { it[notifsEnabledKey] ?: true }
    val firstChatEndedFlow: Flow<Boolean> = context.dataStore.data.map { it[firstChatEndedKey] ?: false }

    suspend fun markNotifPermAsked() {
        context.dataStore.edit { it[notifPermAskedKey] = true }
    }

    suspend fun setNotifsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[notifsEnabledKey] = enabled }
    }

    suspend fun markFirstChatEnded() {
        context.dataStore.edit { it[firstChatEndedKey] = true }
    }

    // ── Premium Badge ──────────────────────────────────────────────────────────
    /** Whether the user has opted to show their premium badge to strangers (default: on). */
    val showPremiumBadgeFlow: Flow<Boolean> = context.dataStore.data
        .map { it[showPremiumBadgeKey] ?: true }

    suspend fun setShowPremiumBadge(show: Boolean) {
        context.dataStore.edit { it[showPremiumBadgeKey] = show }
    }

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
