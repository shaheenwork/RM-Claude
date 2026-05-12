package com.randomchat.shnapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randomchat.shnapp.firebase.FcmManager
import com.randomchat.shnapp.firebase.FirestoreManager
import com.randomchat.shnapp.realtime.RealtimeDbManager
import com.randomchat.shnapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionManager = SessionManager.getInstance(app)
    private val firestore = FirestoreManager.getInstance()
    private val rtdb = RealtimeDbManager.getInstance()

    val sessionId: String get() = sessionManager.sessionId

    val isPremium: StateFlow<Boolean> = sessionManager.isPremiumFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Remaining rewarded photo sends earned via ads. */
    val rewardedPhotoCredits: StateFlow<Int> = sessionManager.rewardedPhotoCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Remaining rewarded audio sends earned via ads. */
    val rewardedAudioCredits: StateFlow<Int> = sessionManager.rewardedAudioCreditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isBanned = MutableStateFlow(false)
    val isBanned: StateFlow<Boolean> = _isBanned

    /** True once at least one chat has been saved (premium OR rewarded-ad save). */
    val hasSavedFirstChat: StateFlow<Boolean> = sessionManager.hasSavedFirstChatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notifsEnabled: StateFlow<Boolean> = sessionManager.notifsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setNotifsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setNotifsEnabled(enabled)
            val ctx = getApplication<Application>()
            if (enabled) FcmManager.syncTzTopic(ctx)
            else         FcmManager.unsubscribeAll(ctx)
        }
    }

    init {
        viewModelScope.launch {
            _isBanned.value = firestore.isSessionBanned(sessionId)
        }
    }

    /** Called after user completes a rewarded ad on the home screen. */
    fun addRewardedCredits() {
        viewModelScope.launch { sessionManager.addRewardedMediaCredits() }
    }

    fun checkBanStatus() {
        viewModelScope.launch {
            _isBanned.value = firestore.isSessionBanned(sessionId)
        }
    }
}
