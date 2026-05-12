package com.randomchat.shnapp.utils

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stranger_chat_prefs")

class SessionManager(private val context: Context) {

    private val termsAcceptedKey = booleanPreferencesKey(Constants.PREF_TERMS_ACCEPTED)
    private val sessionIdKey = stringPreferencesKey(Constants.PREF_SESSION_ID)
    private val isPremiumKey = booleanPreferencesKey(Constants.PREF_IS_PREMIUM)
    private val premiumExpiryKey = longPreferencesKey(Constants.PREF_PREMIUM_EXPIRY)
    private val chatsSinceAdKey = longPreferencesKey(Constants.PREF_CHATS_SINCE_AD)
    private val hasSavedFirstChatKey = booleanPreferencesKey("has_saved_first_chat")
    private val notifPermAskedKey   = booleanPreferencesKey("notif_perm_asked")
    private val notifsEnabledKey    = booleanPreferencesKey("notifs_enabled")
    private val firstChatEndedKey    = booleanPreferencesKey("first_chat_ended")
    private val showPremiumBadgeKey   = booleanPreferencesKey("show_premium_badge")
    private val rewardedPhotoKey      = intPreferencesKey("rewarded_photo_credits")
    private val rewardedAudioKey      = intPreferencesKey("rewarded_audio_credits")
    private val appLockEnabledKey     = booleanPreferencesKey("app_lock_enabled")

    /**
     * Stable per-install identity = FirebaseAuth anonymous UID.
     *
     * If signed in (typical after first launch), returns instantly.
     * If not, performs a blocking anonymous sign-in. First launch only — UID then
     * persists in FirebaseAuth's local cache for offline reuse.
     *
     * Fallback to a locally-generated UUID only if auth fails (offline first-launch).
     * In that fallback case, RTDB writes will be rejected by rules — that's intentional;
     * the app surfaces "no connection" rather than running on a forgeable identity.
     */
    val sessionId: String by lazy {
        runBlocking {
            val auth = FirebaseAuth.getInstance()
            auth.currentUser?.uid ?: try {
                withTimeout(10_000) {
                    auth.signInAnonymously().await().user!!.uid
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Anonymous sign-in failed: ${e.message}", e)
                // Fallback to legacy/local UUID — RTDB writes will fail until auth recovers
                val prefs = context.dataStore.data.first()
                prefs[sessionIdKey] ?: run {
                    val newId = UUID.randomUUID().toString().replace("-", "").take(20)
                    context.dataStore.edit { it[sessionIdKey] = newId }
                    newId
                }
            }
        }
    }

    /**
     * Eagerly trigger anonymous sign-in. Call early in Application.onCreate so
     * the first access to sessionId is non-blocking.
     */
    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
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

    // ── App Lock ──────────────────────────────────────────────────────────────
    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[appLockEnabledKey] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[appLockEnabledKey] = enabled }
    }

    suspend fun markFirstChatEnded() {
        context.dataStore.edit { it[firstChatEndedKey] = true }
    }

    // ── Terms acceptance ──────────────────────────────────────────────────────
    /** True once user has explicitly agreed to Privacy Policy + Terms of Service. */
    val termsAcceptedFlow: Flow<Boolean> = context.dataStore.data
        .map { it[termsAcceptedKey] ?: false }

    suspend fun markTermsAccepted() {
        context.dataStore.edit { it[termsAcceptedKey] = true }
    }

    // ── Premium Badge ──────────────────────────────────────────────────────────
    /** Whether the user has opted to show their premium badge to strangers (default: on). */
    val showPremiumBadgeFlow: Flow<Boolean> = context.dataStore.data
        .map { it[showPremiumBadgeKey] ?: true }

    suspend fun setShowPremiumBadge(show: Boolean) {
        context.dataStore.edit { it[showPremiumBadgeKey] = show }
    }

    // ── Rewarded-ad credits ───────────────────────────────────────────────────
    /** Remaining rewarded photo sends (0 when exhausted). */
    val rewardedPhotoCreditsFlow: Flow<Int> = context.dataStore.data
        .map { it[rewardedPhotoKey] ?: 0 }

    /** Remaining rewarded audio sends (0 when exhausted). */
    val rewardedAudioCreditsFlow: Flow<Int> = context.dataStore.data
        .map { it[rewardedAudioKey] ?: 0 }

    /** Called when user completes a rewarded ad. Grants +1 photo and +1 audio send. */
    suspend fun addRewardedMediaCredits() {
        context.dataStore.edit { prefs ->
            prefs[rewardedPhotoKey] = (prefs[rewardedPhotoKey] ?: 0) + 1
            prefs[rewardedAudioKey] = (prefs[rewardedAudioKey] ?: 0) + 1
        }
    }

    /** Decrements photo credit by 1. No-op if already zero. */
    suspend fun consumePhotoCredit() {
        context.dataStore.edit { prefs ->
            val c = prefs[rewardedPhotoKey] ?: 0
            if (c > 0) prefs[rewardedPhotoKey] = c - 1
        }
    }

    /** Decrements audio credit by 1. No-op if already zero. */
    suspend fun consumeAudioCredit() {
        context.dataStore.edit { prefs ->
            val c = prefs[rewardedAudioKey] ?: 0
            if (c > 0) prefs[rewardedAudioKey] = c - 1
        }
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
