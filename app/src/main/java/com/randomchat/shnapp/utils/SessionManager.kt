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
import com.randomchat.shnapp.model.Gender
import com.randomchat.shnapp.model.RewardGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val rewardedGifKey        = intPreferencesKey("rewarded_gif_credits")
    private val appLockEnabledKey     = booleanPreferencesKey("app_lock_enabled")
    private val tutorialSeenKey       = booleanPreferencesKey("tutorial_seen")
    private val genderKey             = stringPreferencesKey("user_gender")
    // One-shot analytics gates — fire each funnel event once per install
    private val analyticsOnboardingLoggedKey   = booleanPreferencesKey("an_onboarding_logged")
    private val analyticsFirstMatchLoggedKey   = booleanPreferencesKey("an_first_match_logged")
    private val analyticsFirstChatMsgLoggedKey = booleanPreferencesKey("an_first_chat_msg_logged")
    // Rewarded-ad daily-cap tracking
    private val rewardEarnsTodayKey     = intPreferencesKey("reward_earns_today")
    private val lastRewardEarnMsKey     = longPreferencesKey("last_reward_earn_ms")
    private val rewardEarnResetDateKey  = stringPreferencesKey("reward_earn_reset_date")

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

    // ── Account deletion (GDPR) ───────────────────────────────────────────────
    /** Wipes every preference. Next launch behaves like first-ever install. */
    suspend fun clearAllPrefs() {
        context.dataStore.edit { it.clear() }
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

    // ── Onboarding tutorial ───────────────────────────────────────────────────
    /** True once user has finished (or skipped) the 3-page feature tutorial. */
    val tutorialSeenFlow: Flow<Boolean> = context.dataStore.data
        .map { it[tutorialSeenKey] ?: false }

    suspend fun markTutorialSeen() {
        context.dataStore.edit { it[tutorialSeenKey] = true }
    }

    // ── Gender (per-user, persisted; user can change anytime from Home) ──────
    /** null = never set (first launch). MALE / FEMALE = persisted choice. */
    val genderFlow: Flow<Gender?> = context.dataStore.data.map { prefs ->
        prefs[genderKey]?.let { name -> runCatching { Gender.valueOf(name) }.getOrNull() }
    }

    suspend fun setGender(gender: Gender) {
        context.dataStore.edit { it[genderKey] = gender.name }
        // Analytics: per-pick event + persistent user property so every other
        // event (retention, premium conv, etc.) can be sliced by gender.
        Telemetry.genderSelected(gender.name)
        Telemetry.setUserGender(gender.name)
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

    /** Remaining rewarded GIF sends (0 when exhausted). */
    val rewardedGifCreditsFlow: Flow<Int> = context.dataStore.data
        .map { it[rewardedGifKey] ?: 0 }

    /** Called when user completes a rewarded ad. Grants +1 photo, +1 audio, +1 GIF. */
    suspend fun addRewardedMediaCredits() {
        context.dataStore.edit { prefs ->
            prefs[rewardedPhotoKey] = (prefs[rewardedPhotoKey] ?: 0) + 1
            prefs[rewardedAudioKey] = (prefs[rewardedAudioKey] ?: 0) + 1
            prefs[rewardedGifKey]   = (prefs[rewardedGifKey] ?: 0) + 1
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

    /** Decrements GIF credit by 1. No-op if already zero. */
    suspend fun consumeGifCredit() {
        context.dataStore.edit { prefs ->
            val c = prefs[rewardedGifKey] ?: 0
            if (c > 0) prefs[rewardedGifKey] = c - 1
        }
    }

    // ── One-shot analytics gates ──────────────────────────────────────────────
    val analyticsOnboardingLoggedFlow: Flow<Boolean> = context.dataStore.data
        .map { it[analyticsOnboardingLoggedKey] ?: false }
    val analyticsFirstMatchLoggedFlow: Flow<Boolean> = context.dataStore.data
        .map { it[analyticsFirstMatchLoggedKey] ?: false }
    val analyticsFirstChatMsgLoggedFlow: Flow<Boolean> = context.dataStore.data
        .map { it[analyticsFirstChatMsgLoggedKey] ?: false }

    suspend fun markAnalyticsOnboardingLogged() {
        context.dataStore.edit { it[analyticsOnboardingLoggedKey] = true }
    }
    suspend fun markAnalyticsFirstMatchLogged() {
        context.dataStore.edit { it[analyticsFirstMatchLoggedKey] = true }
    }
    suspend fun markAnalyticsFirstChatMsgLogged() {
        context.dataStore.edit { it[analyticsFirstChatMsgLoggedKey] = true }
    }

    // ── Rewarded-ad daily cap ─────────────────────────────────────────────────
    // Max free rewarded-ad earns per local day, and cooldown between earns.
    // Cap protects premium conversion (grinders hit the wall → upsell), and
    // cooldown protects AdMob eCPM (too-frequent fills tank quality).

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Current rewarded-ad state. Read-only — never mutates prefs.
     * UI subscribes via [rewardGateFlow].
     */
    suspend fun getRewardGate(): RewardGate {
        val prefs = context.dataStore.data.first()
        val today = todayDateString()
        val storedDate = prefs[rewardEarnResetDateKey]
        val earnsToday = if (storedDate != today) 0 else (prefs[rewardEarnsTodayKey] ?: 0)
        val left = DAILY_REWARD_CAP - earnsToday
        if (left <= 0) return RewardGate.CapReached
        val lastMs = prefs[lastRewardEarnMsKey] ?: 0L
        val sinceLast = System.currentTimeMillis() - lastMs
        if (lastMs > 0 && sinceLast < REWARD_COOLDOWN_MS) {
            return RewardGate.Cooldown((REWARD_COOLDOWN_MS - sinceLast) / 1000L)
        }
        return RewardGate.Ready(left)
    }

    /**
     * Atomic: bump today's earn counter, record timestamp, grant media credits.
     * Handles the daily rollover (if stored date != today, counter resets to 0
     * before bumping).
     *
     * Single entry point — callers must use this instead of [addRewardedMediaCredits]
     * so the cap stays consistent.
     */
    suspend fun recordRewardEarn() {
        context.dataStore.edit { prefs ->
            val today = todayDateString()
            val storedDate = prefs[rewardEarnResetDateKey]
            val current = if (storedDate != today) 0 else (prefs[rewardEarnsTodayKey] ?: 0)
            prefs[rewardEarnsTodayKey] = current + 1
            prefs[lastRewardEarnMsKey] = System.currentTimeMillis()
            prefs[rewardEarnResetDateKey] = today
            // Bundled credit grant — same as legacy addRewardedMediaCredits
            prefs[rewardedPhotoKey] = (prefs[rewardedPhotoKey] ?: 0) + 1
            prefs[rewardedAudioKey] = (prefs[rewardedAudioKey] ?: 0) + 1
            prefs[rewardedGifKey]   = (prefs[rewardedGifKey] ?: 0) + 1
        }
    }

    /**
     * Cold flow that emits the current [RewardGate] every 30 seconds so UIs
     * can show a live-ticking cooldown countdown without recomposing manually.
     */
    fun rewardGateFlow(): Flow<RewardGate> = flow {
        while (true) {
            emit(getRewardGate())
            delay(30_000L)
        }
    }

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        /** Max free rewarded-ad earns per local day. */
        const val DAILY_REWARD_CAP = 3
        /** Cooldown between rewarded-ad earns (ms). */
        const val REWARD_COOLDOWN_MS = 30L * 60_000L  // 30 minutes

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
