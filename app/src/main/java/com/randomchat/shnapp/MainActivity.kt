package com.randomchat.shnapp

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.randomchat.shnapp.ads.AdMobManager
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.StrangerChatTheme
import com.randomchat.shnapp.ui.dialogs.MatchmakingDialog
import com.randomchat.shnapp.ui.screens.ChatScreen
import com.randomchat.shnapp.ui.screens.HomeScreen
import com.randomchat.shnapp.ui.screens.LockScreen
import com.randomchat.shnapp.ui.screens.PremiumScreen
import com.randomchat.shnapp.ui.screens.SavedChatsScreen
import com.randomchat.shnapp.ui.screens.OnboardingScreen
import com.randomchat.shnapp.ui.screens.SettingsScreen
import com.randomchat.shnapp.ui.components.GenderPickSheet
import com.randomchat.shnapp.ui.screens.SplashScreen
import com.randomchat.shnapp.ui.screens.TutorialScreen
import com.randomchat.shnapp.utils.Constants
import com.randomchat.shnapp.utils.SessionManager
import com.randomchat.shnapp.viewmodel.ChatViewModel
import com.randomchat.shnapp.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.first
import com.randomchat.shnapp.viewmodel.PremiumViewModel
import com.randomchat.shnapp.viewmodel.SavedChatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val TUTORIAL = "tutorial"
    const val HOME = "home"
    const val CHAT = "chat"
    const val PREMIUM = "premium"
    const val SETTINGS = "settings"
    const val SAVED_CHATS = "saved_chats"
}

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private val premiumViewModel: PremiumViewModel by viewModels()
    private val savedChatsViewModel: SavedChatsViewModel by viewModels()

    private var isFirstStart = true   // skip lock check on cold start

    // Handles result from system credential screen
    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) homeViewModel.setLocked(false)
        // cancelled/failed: stay on LockScreen — user taps Unlock to retry
    }

    override fun onStop() {
        super.onStop()
        if (homeViewModel.appLockEnabled.value && homeViewModel.isPremium.value) {
            homeViewModel.setLocked(true)
        }
    }

    override fun onStart() {
        super.onStart()
        if (isFirstStart) { isFirstStart = false; return }
        if (homeViewModel.isLocked.value) showLockPrompt()
    }

    fun showLockPrompt() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) {
            homeViewModel.setAppLockEnabled(false)
            homeViewModel.setLocked(false)
            Toast.makeText(
                this,
                "No device screen lock found. App Lock has been disabled.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent(
            "Random Malayali",
            "Verify your identity to continue"
        )
        if (intent != null) unlockLauncher.launch(intent)
        else { homeViewModel.setAppLockEnabled(false); homeViewModel.setLocked(false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // No system splash — go straight to the branded Compose SplashScreen.
        // Activity theme windowBackground is splash_bg (dark green) so the
        // ~50ms window before first Compose frame matches the brand bg seamlessly.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Detect launch-from-push so AppNavHost can auto-start matchmaking after splash.
        val launchedFromPush = intent?.getStringExtra("from") == "push"

        setContent {
            StrangerChatTheme {
                val haptics = com.randomchat.shnapp.utils.rememberHaptics()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.randomchat.shnapp.utils.LocalHaptics provides haptics
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = DeepSpace) {
                        AppNavHost(
                            homeViewModel = homeViewModel,
                            chatViewModel = chatViewModel,
                            premiumViewModel = premiumViewModel,
                            savedChatsViewModel = savedChatsViewModel,
                            launchedFromPush = launchedFromPush,
                            onTryUnlock = { showLockPrompt() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavHost(
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    premiumViewModel: PremiumViewModel,
    savedChatsViewModel: SavedChatsViewModel,
    launchedFromPush: Boolean = false,
    onTryUnlock: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    // ── App Lock gate ──────────────────────────────────────────────────────────
    val isLocked by homeViewModel.isLocked.collectAsState()
    if (isLocked) {
        LockScreen(onUnlock = onTryUnlock)
        return
    }

    // Matchmaking overlay state
    var showMatchmakingDialog by remember { mutableStateOf(false) }
    // Gender prompt — shown before every matchmaking start (per-chat selection)
    var pendingGenderPrompt by remember { mutableStateOf(false) }

    // Interstitial ad trigger
    val triggerInterstitial by chatViewModel.triggerInterstitial.collectAsState()
    LaunchedEffect(triggerInterstitial) {
        if (triggerInterstitial) {
            AdMobManager.getInstance(context).showInterstitialIfReady(
                context as android.app.Activity
            ) { chatViewModel.interstitialShown() }
        }
    }

    // ── Notification permission ask (Android 13+) ─────────────────────────────
    // Trigger once after first chat ends — never on first launch (avoids "pushy" feel).
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored: user choice respected, no retry */ }

    val chatEnded by chatViewModel.chatEnded.collectAsState()
    LaunchedEffect(chatEnded) {
        if (!chatEnded) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val sm = SessionManager.getInstance(context)
        if (sm.notifPermAskedFlow.first()) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        sm.markNotifPermAsked()
        if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) }
    ) {
        composable(Routes.SPLASH) {
            // State flip triggers an async LaunchedEffect that reads DataStore
            // before deciding which destination to open.
            var splashDone by remember { mutableStateOf(false) }

            LaunchedEffect(splashDone) {
                if (!splashDone) return@LaunchedEffect
                val sm = SessionManager.getInstance(context)
                val accepted = sm.termsAcceptedFlow.first()
                if (accepted) {
                    // TUTORIAL DISABLED — skip regardless of tutorialSeen flag.
                    // Re-enable: restore tutorialSeen check and navigate to Routes.TUTORIAL.
                    if (false) {
                        navController.navigate(Routes.TUTORIAL) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                        // Launched from notification → use saved gender if any, else prompt.
                        if (launchedFromPush) {
                            val savedGender = sm.genderFlow.first()
                            if (savedGender != null) {
                                chatViewModel.startSearch(savedGender)
                                showMatchmakingDialog = true
                            } else {
                                pendingGenderPrompt = true
                            }
                        }
                    }
                } else {
                    // First launch — must accept policies before entering the app.
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }

            SplashScreen(onReady = { splashDone = true })
        }

        composable(
            Routes.ONBOARDING,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(250)) }
        ) {
            OnboardingScreen(
                onAccepted = {
                    if (launchedFromPush) {
                        // Edge case: launched from push notification on first-ever open.
                        // Skip tutorial — user wants to chat now.
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                        pendingGenderPrompt = true
                    } else {
                        // TUTORIAL DISABLED — go straight to HOME.
                        // Re-enable: navigate to Routes.TUTORIAL instead.
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            Routes.TUTORIAL,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(250)) }
        ) {
            val scope = rememberCoroutineScope()
            TutorialScreen(
                onComplete = {
                    scope.launch {
                        SessionManager.getInstance(context).markTutorialSeen()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.TUTORIAL) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            Routes.HOME,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            HomeScreen(
                viewModel = homeViewModel,
                onStartChat = { gender ->
                    chatViewModel.startSearch(gender)
                    showMatchmakingDialog = true
                },
                onOpenPremium = { navController.navigate(Routes.PREMIUM) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            Routes.CHAT,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { it } }
        ) {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack(Routes.HOME, false)
                },
                onNavigateToPremium = { navController.navigate(Routes.PREMIUM) }
            )
        }

        composable(
            Routes.PREMIUM,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { it } }
        ) {
            PremiumScreen(
                viewModel = premiumViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.SETTINGS,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { it } }
        ) {
            SettingsScreen(
                viewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenPremium = { navController.navigate(Routes.PREMIUM) },
                onOpenSavedChats = { navController.navigate(Routes.SAVED_CHATS) }
            )
        }

        composable(
            Routes.SAVED_CHATS,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { it } }
        ) {
            SavedChatsScreen(
                viewModel = savedChatsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

    // Matchmaking dialog — full-screen overlay on HomeScreen.
    // Observes matchmakingState so it can flip to an error UI on network failure.
    val mmState by chatViewModel.matchmakingState.collectAsState()
    val mmError = (mmState as? com.randomchat.shnapp.realtime.MatchmakingState.Error)?.msg
    MatchmakingDialog(
        visible = showMatchmakingDialog,
        errorMessage = mmError,
        onCancel = {
            showMatchmakingDialog = false
            chatViewModel.newChat()
        }
    )

    // Gender pick — gates every startSearch entry (home tap + push auto-start).
    // Selection is sent to the matchmaker (soft F-F bias). Never shown in chat.
    GenderPickSheet(
        visible = pendingGenderPrompt,
        onSelect = { gender ->
            pendingGenderPrompt = false
            // Persist so Home's inline selector reflects this choice next time.
            homeViewModel.setGender(gender)
            chatViewModel.startSearch(gender)
            showMatchmakingDialog = true
        },
        onDismiss = { pendingGenderPrompt = false }
    )

    // 2-4 s random delay for believability, then navigate to chat.
    // Matchmaking runs in parallel; if not yet matched, pending messages queue
    // in ChatManager and flush automatically when stranger connects.
    LaunchedEffect(showMatchmakingDialog) {
        if (!showMatchmakingDialog) return@LaunchedEffect
        val waitMs = Constants.MIN_MATCH_DELAY_MS +
            (Math.random() * (Constants.MAX_MATCH_DELAY_MS - Constants.MIN_MATCH_DELAY_MS)).toLong()
        delay(waitMs)
        if (showMatchmakingDialog) {
            showMatchmakingDialog = false
            navController.navigate(Routes.CHAT)
        }
    }
}
