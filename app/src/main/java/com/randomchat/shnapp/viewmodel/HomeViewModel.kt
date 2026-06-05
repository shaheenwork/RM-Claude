package com.randomchat.shnapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randomchat.shnapp.firebase.FcmManager
import com.randomchat.shnapp.firebase.FirestoreManager
import com.randomchat.shnapp.model.Gender
import com.randomchat.shnapp.model.RewardGate
import com.randomchat.shnapp.realtime.RealtimeDbManager
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager.getInstance(app)
    private val firestore = FirestoreManager.getInstance()
    private val rtdb = RealtimeDbManager.getInstance()

    val sessionId: String get() = sessionManager.sessionId

    val onlineCount: StateFlow<Int> = rtdb.observeOnlineCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isPremium: StateFlow<Boolean> = sessionManager.isPremiumFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Remaining rewarded photo sends earned via ads. */
    val rewardedPhotoCredits: StateFlow<Int> = sessionManager.rewardedPhotoCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Remaining rewarded audio sends earned via ads. */
    val rewardedAudioCredits: StateFlow<Int> = sessionManager.rewardedAudioCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Remaining rewarded GIF sends earned via ads. */
    val rewardedGifCredits: StateFlow<Int> = sessionManager.rewardedGifCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Daily rewarded-ad gate. Drives the RewardsCard render between
     * Ready / Cooldown / CapReached states. Re-evaluates every 30s.
     */
    val rewardGate: StateFlow<RewardGate> = sessionManager.rewardGateFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            RewardGate.Ready(SessionManager.DAILY_REWARD_CAP)
        )

    private val _isBanned = MutableStateFlow(false)
    val isBanned: StateFlow<Boolean> = _isBanned

    /** True once at least one chat has been saved (premium OR rewarded-ad save). */
    val hasSavedFirstChat: StateFlow<Boolean> = sessionManager.hasSavedFirstChatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notifsEnabled: StateFlow<Boolean> = sessionManager.notifsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val appLockEnabled: StateFlow<Boolean> = sessionManager.appLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Persisted gender selection. null = never picked; user changes via Home pills. */
    val selectedGender: StateFlow<Gender?> = sessionManager.genderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setGender(gender: Gender) {
        viewModelScope.launch { sessionManager.setGender(gender) }
    }

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setAppLockEnabled(enabled) }
    }

    /** Called by MainActivity when app goes to background with lock enabled. */
    fun setLocked(locked: Boolean) { _isLocked.value = locked }

    fun setNotifsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setNotifsEnabled(enabled)
            val ctx = getApplication<Application>()
            if (enabled) FcmManager.subscribeAll()
            else         FcmManager.unsubscribeAll(ctx)
        }
    }

    init {
        viewModelScope.launch {
            _isBanned.value = firestore.isSessionBanned(sessionId)
        }
    }

    /**
     * Called after the user completes a rewarded ad on the home screen.
     * Routes through [SessionManager.recordRewardEarn] so the daily cap +
     * cooldown stay in sync with the credit grant.
     */
    fun addRewardedCredits() {
        viewModelScope.launch { sessionManager.recordRewardEarn() }
    }

    fun checkBanStatus() {
        viewModelScope.launch {
            _isBanned.value = firestore.isSessionBanned(sessionId)
        }
    }

    // ── Account deletion (GDPR) ───────────────────────────────────────────────
    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState

    /**
     * Permanently deletes the user's account.
     * Wipes Firestore, RTDB, local saved chats, DataStore prefs, FCM topics,
     * and the FirebaseAuth anonymous user. On next launch the app behaves
     * like first-ever install.
     */
    fun deleteAccount() {
        if (_deleteAccountState.value is DeleteAccountState.InProgress) return
        _deleteAccountState.value = DeleteAccountState.InProgress
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val sid = sessionId
            try {
                // 1. Firestore — subscription, ban, saved chats
                firestore.deleteUserData(sid)
                // 2. Realtime DB — presence, queue, assignments
                rtdb.deleteUserData(sid)
                // 3. Local saved chats
                com.randomchat.shnapp.utils.LocalChatStore.clearAll(ctx)
                // 4. FCM unsubscribe
                com.randomchat.shnapp.firebase.FcmManager.unsubscribeAll(ctx)
                // 5. DataStore — wipe everything
                sessionManager.clearAllPrefs()
                // 6. FirebaseAuth — delete anonymous user (best effort)
                runCatching {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.delete()?.await()
                }
                runCatching {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                }
                _deleteAccountState.value = DeleteAccountState.Done
            } catch (e: Exception) {
                _deleteAccountState.value = DeleteAccountState.Error(e.message ?: "Deletion failed")
            }
        }
    }
}

sealed class DeleteAccountState {
    data object Idle : DeleteAccountState()
    data object InProgress : DeleteAccountState()
    data object Done : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}
