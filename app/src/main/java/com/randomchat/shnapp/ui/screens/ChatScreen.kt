package com.randomchat.shnapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.randomchat.shnapp.ads.AdMobManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.AudioButton
import com.randomchat.shnapp.ui.components.ImageButton
import com.randomchat.shnapp.ui.components.MessageBubble
import com.randomchat.shnapp.ui.components.OnlineStatusChip
import com.randomchat.shnapp.ui.components.SystemChip
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.ui.dialogs.ReportDialog
import com.randomchat.shnapp.viewmodel.ChatViewModel
import com.randomchat.shnapp.viewmodel.SaveProgress
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val context  = LocalContext.current
    val activity = context as android.app.Activity
    val scope    = rememberCoroutineScope()
    val haptics  = com.randomchat.shnapp.utils.LocalHaptics.current

    val messages by viewModel.messages.collectAsState()
    val strangerActivity by viewModel.strangerActivity.collectAsState()
    val isStrangerConnected by viewModel.isStrangerConnected.collectAsState()
    val chatEnded by viewModel.chatEnded.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsState()
    val hasSavedFirstChat by viewModel.hasSavedFirstChat.collectAsState()
    val saveProgress by viewModel.saveProgress.collectAsState()
    val reportStatus by viewModel.reportStatus.collectAsState()
    val reactions by viewModel.reactions.collectAsState()
    val currentRoomId by viewModel.currentRoomId.collectAsState()
    val strangerDraftText by viewModel.strangerDraftText.collectAsState()
    val strangerHasBadge by viewModel.strangerHasBadge.collectAsState()
    val showMyBadge         by viewModel.showMyBadge.collectAsState()
    val rewardedPhotoCredits by viewModel.rewardedPhotoCredits.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val rewardedAudioCredits by viewModel.rewardedAudioCredits.collectAsState()
    val rewardedGifCredits by viewModel.rewardedGifCredits.collectAsState()
    val chatSaved            by viewModel.chatSaved.collectAsState()
    // Ghost bubble visible when premium + stranger is actively drafting
    val showGhostBubble = isPremium && strangerDraftText != null && !chatEnded

    var reactionTargetMessage by remember { mutableStateOf<com.randomchat.shnapp.model.ChatMessage?>(null) }

    // ── Flying emoji tracking ─────────────────────────────────────────────────
    var prevReactions by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var reactionsReady by remember { mutableStateOf(false) }
    var flyingEmoji by remember { mutableStateOf<String?>(null) }

    // Reset baseline whenever we enter a new room
    LaunchedEffect(currentRoomId) {
        prevReactions = emptyMap()
        reactionsReady = false
    }

    // Haptic on stranger-connected transition (match found)
    LaunchedEffect(isStrangerConnected) {
        if (isStrangerConnected && !chatEnded) {
            haptics.match()
            com.randomchat.shnapp.utils.Telemetry.chatStarted()
        }
    }

    // Haptic on chat ended (sharp warning)
    LaunchedEffect(chatEnded) {
        if (chatEnded) {
            haptics.warning()
            com.randomchat.shnapp.utils.Telemetry.chatEnded(messages.size)
        }
    }

    // Haptic on save success
    LaunchedEffect(saveProgress?.isDone) {
        if (saveProgress?.isDone == true) haptics.success()
    }

    // Detect new reactions added by the stranger
    LaunchedEffect(reactions) {
        if (!reactionsReady) {
            // First snapshot establishes the baseline — no animation
            prevReactions = reactions
            reactionsReady = true
            return@LaunchedEffect
        }
        val myId = viewModel.sessionId
        outer@ for ((msgId, perMsg) in reactions) {
            val prev = prevReactions[msgId] ?: emptyMap()
            for ((sid, emoji) in perMsg) {
                if (sid != myId && prev[sid] != emoji) {
                    flyingEmoji = emoji
                    haptics.tick() // stranger reacted to my message
                    break@outer
                }
            }
        }
        prevReactions = reactions
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reportStatus) {
        when (reportStatus) {
            "success" -> {
                snackbarHostState.showSnackbar("Report submitted. Thank you.")
                viewModel.clearReportStatus()
            }
            "error" -> {
                snackbarHostState.showSnackbar("Failed to submit report. Please try again.")
                viewModel.clearReportStatus()
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var cancelTriggered by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showEndChatDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSaveChatDialog by remember { mutableStateOf(false) }
    var piiBlockedKind by remember { mutableStateOf<com.randomchat.shnapp.utils.PiiDetector.Kind?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }

    // Auto-dismiss "Saved ✓" after 2s
    LaunchedEffect(saveProgress?.isDone) {
        if (saveProgress?.isDone == true) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveProgress()
        }
    }
    val listState = rememberLazyListState()

    // Derived: show scroll-to-bottom FAB when user scrolled up from latest
    val showScrollFab by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            messages.isNotEmpty() &&
            listState.firstVisibleItemIndex < (messages.size - 4)
        }
    }

    // ── Photo source: gallery or camera ──────────────────────────────────────
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    // Camera capture — FileProvider URI created just before launch
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    // Helper: scan bytes with on-device OCR, block upload if PII found
    val handleImageBytes: (ByteArray) -> Unit = { bytes ->
        android.util.Log.d("ChatScreen", "handleImageBytes called, ${bytes.size} bytes — starting OCR scan")
        scope.launch {
            val kind = withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.randomchat.shnapp.utils.ImagePiiScanner.scan(bytes)
            }
            android.util.Log.d("ChatScreen", "OCR scan result: $kind")
            if (kind != null) {
                haptics.warning()
                piiBlockedKind = kind
                com.randomchat.shnapp.utils.Telemetry.imagePiiBlocked(kind.name)
            } else {
                viewModel.uploadAndSendImage(bytes)
                com.randomchat.shnapp.utils.Telemetry.messageSent("photo")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        handleImageBytes(bytes)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) return@rememberLauncherForActivityResult
        val uri = cameraOutputUri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        // Delete temp file — bytes already in memory
        runCatching { File(uri.path ?: "").delete() }
        handleImageBytes(bytes)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showPhotoSourceDialog = true
    }

    // Audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startAudioRecording()
    }

    // Auto-scroll to bottom on new messages, typing indicator, or ghost bubble appearing
    LaunchedEffect(messages.size, strangerActivity, showGhostBubble) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    BackHandler {
        when {
            reactionTargetMessage != null -> reactionTargetMessage = null
            !chatEnded -> showEndChatDialog = true
            else -> onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, GradientEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(CardSurface.copy(0.95f))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (!chatEnded) showEndChatDialog = true else onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "A Random Malayali",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        // Gold badge pill shown when stranger has premium badge enabled
                        AnimatedVisibility(
                            visible = strangerHasBadge && isStrangerConnected && !chatEnded,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -8 },
                            exit = fadeOut(tween(200))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        PremiumGold.copy(0.15f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PremiumGold,
                                    modifier = Modifier.size(9.dp)
                                )
                                Text(
                                    " Premium",
                                    color = PremiumGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                    // When premium ghost bubble is showing, suppress the redundant "typing…"
                    // label — still show recording/sending activities since those aren't in the bubble
                    val suppressTyping = showGhostBubble && strangerActivity == "typing"
                    if (strangerActivity != null && !suppressTyping) {
                        StrangerActivityText(activity = strangerActivity!!)
                    } else {
                        // Always show Online while chat is active — stranger is always "present"
                        OnlineStatusChip(isOnline = !chatEnded)
                    }
                }

                // More options menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CardSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report", color = com.randomchat.shnapp.theme.ErrorRed, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Flag, null, tint = com.randomchat.shnapp.theme.ErrorRed) },
                            onClick = { showMenu = false; showReportDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("End Chat", color = TextSecondary, fontSize = 14.sp) },
                            onClick = { showMenu = false; showEndChatDialog = true }
                        )
                        if (isPremium && chatEnded) {
                            DropdownMenuItem(
                                text = { Text("Save Chat", color = AccentCyan, fontSize = 14.sp) },
                                onClick = { showMenu = false; showSaveChatDialog = true }
                            )
                        }
                        if (isPremium) {
                            HorizontalDivider(color = SubtleBorder.copy(0.5f))
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Show Premium Badge",
                                            color = PremiumGold,
                                            fontSize = 14.sp
                                        )
                                        Switch(
                                            checked = showMyBadge,
                                            onCheckedChange = null,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = PremiumGold,
                                                checkedTrackColor = PremiumGold.copy(0.35f),
                                                uncheckedThumbColor = TextMuted,
                                                uncheckedTrackColor = SubtleBorder
                                            )
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = PremiumGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { viewModel.toggleMyBadge() }
                            )
                        }
                    }
                }
            }

            // ── Save progress banner ─────────────────────────────────────────
            AnimatedVisibility(visible = saveProgress != null, enter = fadeIn(), exit = fadeOut()) {
                saveProgress?.let { prog ->
                    SaveProgressBanner(progress = prog)
                }
            }

            // ── "Connected" hint strip ────────────────────────────────────────
            // Shown immediately when ChatScreen opens; gives the illusion of an
            // established connection while matchmaking completes in the background.
            AnimatedVisibility(
                visible = !chatEnded,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentCyan.copy(alpha = 0.07f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "👋  Connected to a fellow Malayali — say hi!",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            // ── Messages ─────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Messages — key is the stable Firebase push ID
                // contentType added so Compose recycles bubble types efficiently
                itemsIndexed(messages, key = { _, m -> m.id }, contentType = { _, m -> m.type }) { idx, message ->
                    // Group consecutive same-sender messages within 60s — Telegram/iMessage pattern
                    val prev = messages.getOrNull(idx - 1)
                    val next = messages.getOrNull(idx + 1)
                    val isFirst = prev == null ||
                        prev.senderId != message.senderId ||
                        prev.type == com.randomchat.shnapp.model.MessageType.SYSTEM ||
                        (message.timestamp - prev.timestamp) > 60_000L
                    val isLast = next == null ||
                        next.senderId != message.senderId ||
                        next.type == com.randomchat.shnapp.model.MessageType.SYSTEM ||
                        (next.timestamp - message.timestamp) > 60_000L

                    MessageBubble(
                        message = message,
                        messageReactions = reactions[message.id] ?: emptyMap(),
                        mySessionId = viewModel.sessionId,
                        isPremium = isPremium,
                        isFirstInGroup = isFirst,
                        isLastInGroup = isLast,
                        onLongPress = { haptics.click(); reactionTargetMessage = it },
                        onReactionTap = { emoji -> haptics.tick(); viewModel.reactToMessage(message.id, emoji) },
                        onSwipeReply = { msg ->
                            haptics.click()
                            viewModel.startReply(msg)
                        }
                    )
                }

            }

            // ── Chat ended state ─────────────────────────────────────────────
            AnimatedVisibility(visible = chatEnded, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SystemChip("Chat has ended")
                    Spacer(Modifier.height(12.dp))

                    // Banner ad — non-premium + ADS_ENABLED only
                    if (!isPremium && com.randomchat.shnapp.utils.Constants.ADS_ENABLED) {
                        AndroidView(
                            factory = { ctx ->
                                AdView(ctx).apply {
                                    setAdSize(AdSize.BANNER)
                                    adUnitId = com.randomchat.shnapp.utils.Constants.ADMOB_BANNER_ID
                                    loadAd(AdRequest.Builder().build())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // "Watch ad to save" strip — needs rewarded ads enabled
                    if (!isPremium && messages.isNotEmpty() && !chatSaved && com.randomchat.shnapp.utils.Constants.ADS_ENABLED) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(AccentCyan.copy(alpha = 0.07f))
                                .border(
                                    1.dp,
                                    AccentCyan.copy(alpha = 0.25f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    com.randomchat.shnapp.utils.Telemetry.rewardedAdTap("save_chat")
                                    if (AdMobManager.getInstance(context).isRewardedReady()) {
                                        AdMobManager.getInstance(context).showRewardedIfReady(
                                            activity       = activity,
                                            onRewarded     = { viewModel.saveChat() },
                                            onNotAvailable = {
                                                com.randomchat.shnapp.utils.Telemetry.rewardedAdUnavailable("save_chat")
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Ad not ready — try again in a moment."
                                                    )
                                                }
                                            },
                                            onDismissed    = {
                                                // onRewarded fires before onDismissed,
                                                // so chatSaved is already true if user earned reward
                                                if (viewModel.chatSaved.value) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Chat saved ✓")
                                                    }
                                                } else {
                                                    com.randomchat.shnapp.utils.Telemetry.rewardedAdDismissed("save_chat")
                                                }
                                            }
                                        )
                                    } else {
                                        com.randomchat.shnapp.utils.Telemetry.rewardedAdUnavailable("save_chat")
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Ad not ready — try again in a moment."
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                "Watch ad to save this chat",
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = AccentCyan.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // First-time save hint for premium users
                    if (isPremium && !hasSavedFirstChat) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AccentCyan.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Text(
                                "You can save this chat! Tap ⋮ → Save Chat to read it later.",
                                color = AccentCyan,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    com.randomchat.shnapp.ui.components.CyanButton(
                        text = "Meet another Malayali",
                        onClick = { haptics.click(); viewModel.newChat(); onNavigateBack() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Ghost typing bubble (premium: see stranger's live draft) ─────
            AnimatedVisibility(
                visible = showGhostBubble,
                enter = slideInVertically { it } + fadeIn(tween(180)),
                exit = slideOutVertically { it } + fadeOut(tween(150))
            ) {
                strangerDraftText?.let { draft ->
                    com.randomchat.shnapp.ui.components.GhostTypingBubble(text = draft)
                }
            }

            // ── Active-chat banner — between messages and composer ────────────
            // Hidden during recording (recording bar replaces composer),
            // when chat ends (ended section has its own banner), and for premium users.
            if (!chatEnded && !isPremium && !isRecording && com.randomchat.shnapp.utils.Constants.ADS_ENABLED) {
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = com.randomchat.shnapp.utils.Constants.ADMOB_BANNER_ID
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Reply chip — shown above composer when replying ──────────────
            AnimatedVisibility(
                visible = !chatEnded && !isRecording && replyingTo != null,
                enter = androidx.compose.animation.expandVertically(tween(180)) + fadeIn(tween(160)),
                exit  = androidx.compose.animation.shrinkVertically(tween(140)) + fadeOut(tween(120))
            ) {
                replyingTo?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(AccentCyan, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Replying to ${if (target.senderId == viewModel.sessionId) "yourself" else "Malayali"}",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when (target.type) {
                                    com.randomchat.shnapp.model.MessageType.IMAGE -> "📷 Photo"
                                    com.randomchat.shnapp.model.MessageType.AUDIO -> "🎤 Voice note"
                                    else -> target.content.take(80)
                                },
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            haptics.tick()
                            viewModel.cancelReply()
                        }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.Close,
                                null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Message Composer / Recording Bar ────────────────────────────
            if (!chatEnded) {
                if (isRecording) {
                    RecordingBar(
                        durationMs = recordingDurationMs,
                        onSend = {
                            haptics.click()
                            viewModel.stopAndSendAudio()
                            com.randomchat.shnapp.utils.Telemetry.messageSent("audio")
                        },
                        onCancel = { haptics.tick(); viewModel.cancelAudioRecording() }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)  // tighter — reads as one bar
                    ) {
                        // Image button — opens Camera / Gallery picker
                        // Non-premium: allowed if they have a rewarded credit, else → premium screen
                        // ── Single attach button (Telegram pattern) ─────────────
                        // Tap → opens AttachSheet with Camera / Gallery / GIF tiles.
                        // Mic stays separate (trailing slot, toggles with send).
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ElevatedCard)
                                .border(1.dp, SubtleBorder, CircleShape)
                                .clickable {
                                    haptics.tick()
                                    showAttachSheet = true
                                }
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                contentDescription = "Attach",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Text input — pill, 22dp radius (modern), tighter v-padding
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(ElevatedCard)
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            if (inputText.isEmpty()) {
                                Text("Message...", color = TextMuted, fontSize = 15.sp)
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { new ->
                                    if (new.length <= com.randomchat.shnapp.utils.Constants.MAX_MESSAGE_LENGTH) {
                                        inputText = new
                                        viewModel.notifyTyping(new.isNotEmpty())
                                        // Don't leak PII via live preview — only broadcast clean drafts
                                        if (!com.randomchat.shnapp.utils.PiiDetector.containsPii(new)) {
                                            viewModel.broadcastDraftText(new)
                                        } else {
                                            viewModel.broadcastDraftText("") // hide preview when PII typed
                                        }
                                    }
                                },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                                cursorBrush = SolidColor(AccentCyan),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                        }

                        // ── Trailing slot: mic (empty) ↔ send (typing) ──────────
                        // Smooth scale-cross-fade between the two states. One slot,
                        // less visual weight than the old 3-button layout.
                        androidx.compose.animation.AnimatedContent(
                            targetState = inputText.isNotBlank(),
                            transitionSpec = {
                                (androidx.compose.animation.scaleIn(
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.65f,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    )
                                ) + fadeIn(tween(150))) togetherWith
                                    (androidx.compose.animation.scaleOut(tween(140)) + fadeOut(tween(140)))
                            },
                            label = "send_mic"
                        ) { hasText ->
                            if (hasText) {
                                // SEND button — brand gradient pink → violet
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(com.randomchat.shnapp.theme.BrandGradients.primary)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val text = inputText.trim()
                                            if (text.isBlank()) return@IconButton
                                            val piiKind = com.randomchat.shnapp.utils.PiiDetector.detect(text)
                                            if (piiKind != null) {
                                                haptics.warning()
                                                piiBlockedKind = piiKind
                                                com.randomchat.shnapp.utils.Telemetry.piiBlocked(piiKind.name)
                                                return@IconButton
                                            }
                                            haptics.click()
                                            viewModel.sendMessage(text)
                                            com.randomchat.shnapp.utils.Telemetry.messageSent("text")
                                            inputText = ""
                                            viewModel.notifyTyping(false)
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                // MIC button — tap to start recording
                                AudioButton(
                                    isPremium   = isPremium,
                                    creditCount = rewardedAudioCredits,
                                    isRecording = false,
                                    onClick = {
                                        haptics.tick()
                                        if (!isPremium && rewardedAudioCredits == 0) {
                                            onNavigateToPremium()
                                            return@AudioButton
                                        }
                                        cancelTriggered = false
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Reaction Picker — full-screen overlay (tapping outside dismisses) ──
        com.randomchat.shnapp.ui.components.ReactionPicker(
            targetMessage = reactionTargetMessage,
            myReaction = reactionTargetMessage?.let { reactions[it.id]?.get(viewModel.sessionId) },
            isPremium = isPremium,
            onReact = { emoji ->
                reactionTargetMessage?.let { viewModel.reactToMessage(it.id, emoji) }
                reactionTargetMessage = null
            },
            onDismiss = { reactionTargetMessage = null },
            onNavigateToPremium = { reactionTargetMessage = null; onNavigateToPremium() }
        )

        // ── Flying emoji — plays when stranger reacts ──────────────────────────
        flyingEmoji?.let { emoji ->
            FlyingEmojiOverlay(emoji = emoji, onFinished = { flyingEmoji = null })
        }

        // ── Scroll-to-bottom FAB ───────────────────────────────────────────────
        // Appears when user scrolled up. Tap → smooth scroll to latest.
        AnimatedVisibility(
            visible = showScrollFab,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.7f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            ) + fadeIn(tween(160)),
            exit = androidx.compose.animation.scaleOut(tween(140)) + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 84.dp)  // sits above composer
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ElevatedCard)
                    .border(1.dp, SubtleBorder, CircleShape)
                    .clickable {
                        haptics.tick()
                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to latest",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

    }

    // Snackbar host (overlays outside the Column)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(hostState = snackbarHostState)
    }

    // Dialogs
    ReportDialog(
        visible = showReportDialog,
        onDismiss = { showReportDialog = false },
        onReport = { reason ->
            viewModel.reportStranger(reason)
            com.randomchat.shnapp.utils.Telemetry.reportSubmitted(reason)
            showReportDialog = false
        }
    )

    if (showEndChatDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showEndChatDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, SubtleBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Icon badge ───────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            com.randomchat.shnapp.theme.ErrorRed.copy(alpha = 0.12f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = com.randomchat.shnapp.theme.ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "End this chat?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You'll be disconnected. Start a fresh chat anytime.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                // ── Action buttons — Cancel (outlined) + End chat (filled) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel — secondary outlined
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                            .clickable { showEndChatDialog = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Cancel",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // End chat — primary destructive filled
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(com.randomchat.shnapp.theme.ErrorRed)
                            .clickable {
                                showEndChatDialog = false
                                viewModel.endChat()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "End chat",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ── Photo source bottom sheet (Telegram / WhatsApp style) ────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    if (showPhotoSourceDialog) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showPhotoSourceDialog = false },
            sheetState       = sheetState,
            containerColor   = com.randomchat.shnapp.theme.ElevatedCard,
            tonalElevation   = 0.dp,
            dragHandle       = {
                // Pill drag handle
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(com.randomchat.shnapp.theme.SubtleBorder, CircleShape)
                )
            }
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Sheet title
                Text(
                    text     = "Send Photo",
                    color    = com.randomchat.shnapp.theme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )

                HorizontalDivider(color = com.randomchat.shnapp.theme.SubtleBorder, thickness = 0.5.dp)

                // Camera row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPhotoSourceDialog = false
                            val dir  = File(context.cacheDir, "camera").also { it.mkdirs() }
                            val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                            val uri  = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            cameraOutputUri = uri
                            cameraLauncher.launch(uri)
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(AccentCyan.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint     = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text("Camera", color = com.randomchat.shnapp.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Take a new photo", color = com.randomchat.shnapp.theme.TextMuted, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(
                    color     = com.randomchat.shnapp.theme.SubtleBorder.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(start = 80.dp)
                )

                // Gallery row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(PremiumGold.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = null,
                            tint     = PremiumGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text("Gallery", color = com.randomchat.shnapp.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Choose from your photos", color = com.randomchat.shnapp.theme.TextMuted, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showSaveChatDialog) {
        SaveChatDialog(
            hasMedia = viewModel.hasMediaMessages(),
            onSave = {
                showSaveChatDialog = false
                viewModel.saveChat()
            },
            onDismiss = { showSaveChatDialog = false }
        )
    }

    // ── PII blocked dialog ───────────────────────────────────────────────────
    piiBlockedKind?.let { kind ->
        PiiBlockedDialog(
            kind = kind,
            onDismiss = { piiBlockedKind = null }
        )
    }

    // ── Attach sheet — Camera / Gallery / GIF tiles ──────────────────────────
    if (showAttachSheet) {
        AttachSheet(
            isPremium = isPremium,
            photoCredits = rewardedPhotoCredits,
            audioCredits = rewardedAudioCredits,
            gifCredits = rewardedGifCredits,
            onDismiss = { showAttachSheet = false },
            onCamera = {
                showAttachSheet = false
                if (!isPremium && rewardedPhotoCredits == 0) {
                    onNavigateToPremium()
                    return@AttachSheet
                }
                val hasCam = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCam) {
                    val dir  = File(context.cacheDir, "camera").also { it.mkdirs() }
                    val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                    val uri  = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraOutputUri = uri
                    cameraLauncher.launch(uri)
                } else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGallery = {
                showAttachSheet = false
                if (!isPremium && rewardedPhotoCredits == 0) {
                    onNavigateToPremium()
                    return@AttachSheet
                }
                galleryLauncher.launch("image/*")
            },
            onGif = {
                showAttachSheet = false
                // GIF is premium — non-premium needs a rewarded GIF credit
                if (!isPremium && rewardedGifCredits == 0) {
                    onNavigateToPremium()
                    return@AttachSheet
                }
                showGifPicker = true
            },
            onWatchAd = {
                // Inline rewarded ad flow — earn +1 photo, +1 voice, +1 GIF credit
                haptics.tick()
                com.randomchat.shnapp.utils.Telemetry.rewardedAdTap("attach_sheet")
                if (AdMobManager.getInstance(context).isRewardedReady()) {
                    AdMobManager.getInstance(context).showRewardedIfReady(
                        activity = activity,
                        onRewarded = {
                            haptics.success()
                            // Same path home screen uses
                            scope.launch {
                                com.randomchat.shnapp.utils.SessionManager.getInstance(context)
                                    .addRewardedMediaCredits()
                            }
                            com.randomchat.shnapp.utils.Telemetry.rewardedAdEarned("attach_sheet")
                            // Keep sheet open so user sees the credit update + can attach
                        },
                        onNotAvailable = {
                            com.randomchat.shnapp.utils.Telemetry.rewardedAdUnavailable("attach_sheet")
                            scope.launch {
                                snackbarHostState.showSnackbar("Ad not ready — try again in a moment.")
                            }
                        }
                    )
                } else {
                    com.randomchat.shnapp.utils.Telemetry.rewardedAdUnavailable("attach_sheet")
                    scope.launch {
                        snackbarHostState.showSnackbar("Ad not ready — try again in a moment.")
                    }
                }
            }
        )
    }

    // ── GIF picker sheet — premium OR has rewarded GIF credit ────────────────
    if (showGifPicker && (isPremium || rewardedGifCredits > 0)) {
        com.randomchat.shnapp.ui.components.GifPickerSheet(
            onPick = { gif ->
                haptics.click()
                viewModel.sendMediaMessage(gif.fullUrl, com.randomchat.shnapp.model.MessageType.IMAGE)
                // Non-premium: consume one GIF credit per send
                if (!isPremium) viewModel.consumeGifCredit()
                com.randomchat.shnapp.utils.Telemetry.messageSent("gif")
                showGifPicker = false
            },
            onDismiss = { showGifPicker = false }
        )
    }
}

@Composable
private fun PiiBlockedDialog(
    kind: com.randomchat.shnapp.utils.PiiDetector.Kind,
    onDismiss: () -> Unit
) {
    val (label, hint) = when (kind) {
        com.randomchat.shnapp.utils.PiiDetector.Kind.PHONE         ->
            "phone number" to "Edit your message to remove the number."
        com.randomchat.shnapp.utils.PiiDetector.Kind.EMAIL         ->
            "email address" to "Edit your message to remove the email."
        com.randomchat.shnapp.utils.PiiDetector.Kind.SOCIAL_HANDLE ->
            "social media contact" to "Edit your message to remove the handle."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.PrivacyTip,
                    null,
                    tint = com.randomchat.shnapp.theme.ErrorRed,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "Personal info blocked",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "To keep everyone safe, sharing a $label isn't allowed in anonymous chat.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    hint,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = AccentCyan, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun SaveChatDialog(
    hasMedia: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text("Save Chat", color = TextPrimary) },
        text = {
            Text(
                if (hasMedia)
                    "This chat contains photos and audio. They'll be downloaded to your device."
                else
                    "Save this conversation to read later?",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save", color = AccentCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun SaveProgressBanner(progress: SaveProgress) {
    val fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else 1f
    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(300),
        label = "save_progress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (progress.isDone) {
            Icon(Icons.Default.Bookmark, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
            Text("Chat saved!", color = AccentCyan, fontSize = 13.sp, modifier = Modifier.weight(1f))
        } else {
            Icon(Icons.Default.Bookmark, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (progress.total > 0) "Saving media… ${progress.done}/${progress.total}"
                    else "Saving…",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                if (progress.total > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(CardSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animFraction)
                                .background(AccentCyan, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrangerActivityText(activity: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "activity_dots")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dots_phase"
    )
    val dots = when ((phase * 3).toInt()) {
        0 -> "."
        1 -> ".."
        else -> "..."
    }
    val label = when (activity) {
        "typing"        -> "typing"
        "recording"     -> "recording audio"
        "sending_audio" -> "sending audio"
        "sending_photo" -> "sending photo"
        else            -> activity
    }
    Text(
        text = "$label$dots",
        color = AccentCyan,
        fontSize = 12.sp
    )
}

/**
 * A large emoji pops in at the centre of the chat area, then flies downward and
 * shrinks to nothing — giving the impression it's settling onto the reacted message.
 * Pointer events are NOT consumed so the user can still interact underneath.
 */
@Composable
private fun FlyingEmojiOverlay(emoji: String, onFinished: () -> Unit) {
    val offsetY = remember { Animatable(0f) }
    val scale  = remember { Animatable(0f) }
    val alpha  = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // ── Phase 1: pop in (spring) ──────────────────────────────────────────
        launch { alpha.animateTo(1f, tween(120)) }
        scale.animateTo(
            1.25f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
        )
        scale.animateTo(1f, tween(80))

        kotlinx.coroutines.delay(480) // hold so the user notices it

        // ── Phase 2: fly down & shrink ────────────────────────────────────────
        launch {
            offsetY.animateTo(520f, tween(650, easing = FastOutSlowInEasing))
        }
        launch { scale.animateTo(0.05f, tween(650, easing = FastOutSlowInEasing)) }
        alpha.animateTo(0f, tween(500, delayMillis = 120))

        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = emoji,
            fontSize = 72.sp,
            modifier = Modifier.graphicsLayer {
                translationY = offsetY.value
                scaleX      = scale.value
                scaleY      = scale.value
                this.alpha  = alpha.value
            }
        )
    }
}

private fun formatAudioDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun RecordingBar(
    durationMs: Long,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    var dragOffsetX by remember { mutableStateOf(0f) }
    val cancelThreshold = -200f
    val cancelProgress = (dragOffsetX / cancelThreshold).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragOffsetX < cancelThreshold) onCancel() else dragOffsetX = 0f },
                    onDragCancel = { dragOffsetX = 0f }
                ) { _, dragAmount ->
                    dragOffsetX = (dragOffsetX + dragAmount).coerceAtMost(0f)
                    if (dragOffsetX < cancelThreshold) onCancel()
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pulsing red dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(com.randomchat.shnapp.theme.ErrorRed.copy(alpha = pulseAlpha), CircleShape)
        )

        // Duration counter
        Text(
            text = formatAudioDuration(durationMs),
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.width(44.dp)
        )

        // Slide-to-cancel hint (fades as you drag)
        Text(
            text = "< Slide to cancel",
            color = TextMuted.copy(alpha = 1f - cancelProgress),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        // Recording bar send button — brand gradient
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(com.randomchat.shnapp.theme.BrandGradients.primary)
                .clickable(onClick = onSend)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Attach sheet — Telegram-style consolidated media picker.
 * Three tiles: Camera / Gallery / GIF. Reduces composer button count from 3 to 1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    isPremium: Boolean,
    photoCredits: Int,
    audioCredits: Int,
    gifCredits: Int,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onGif: () -> Unit,
    onWatchAd: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    // Show "Watch Ad" only when: non-premium, ads enabled, any credit type is 0
    val showWatchAd = !isPremium &&
        com.randomchat.shnapp.utils.Constants.ADS_ENABLED &&
        (photoCredits == 0 || audioCredits == 0 || gifCredits == 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = com.randomchat.shnapp.theme.ElevatedCard,
        tonalElevation   = 0.dp,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(com.randomchat.shnapp.theme.SubtleBorder, CircleShape)
            )
        }
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "Share",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 14.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Camera + Gallery: same neutral violet — both are standard media,
                // no special status. Consistent = calm = premium.
                AttachTile(
                    icon  = Icons.Default.PhotoCamera,
                    label = "Camera",
                    bg    = com.randomchat.shnapp.theme.BrandViolet.copy(alpha = 0.12f),
                    tint  = com.randomchat.shnapp.theme.BrandViolet,
                    onClick = onCamera,
                    creditBadge = if (!isPremium) photoCredits else null,
                    modifier = Modifier.weight(1f)
                )
                AttachTile(
                    icon  = Icons.Default.Collections,
                    label = "Gallery",
                    bg    = com.randomchat.shnapp.theme.BrandViolet.copy(alpha = 0.12f),
                    tint  = com.randomchat.shnapp.theme.BrandViolet,
                    onClick = onGallery,
                    creditBadge = if (!isPremium) photoCredits else null,
                    modifier = Modifier.weight(1f)
                )
                AttachGifTile(
                    isPremium = isPremium,
                    gifCredits = gifCredits,
                    onClick   = onGif,
                    modifier  = Modifier.weight(1f)
                )
            }

            // ── Watch ad row — only when ads enabled, non-premium, credits depleted ──
            if (showWatchAd) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentCyan.copy(alpha = 0.08f))
                        .border(1.dp, AccentCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onWatchAd)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentCyan.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Watch a short ad",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Earn 1 Photo + 1 Voice + 1 GIF",
                            color = AccentCyan.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "Free",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(AccentCyan.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AttachTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
    /** null = no badge (premium). >0 = ×N credit badge. 0 = lock badge (need premium or ad). */
    creditBadge: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Wrapper Box for icon + overhang badge
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(bg, CircleShape)
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            // Credit badge — overhang at bottom-right
            if (creditBadge != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(if (creditBadge > 0) 18.dp else 16.dp)
                        .clip(CircleShape)
                        .background(if (creditBadge > 0) AccentCyan else PremiumGold)
                ) {
                    if (creditBadge > 0) {
                        Text(
                            "×$creditBadge",
                            color = Color(0xFF001A22),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Lock,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
        Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AttachGifTile(
    isPremium: Boolean,
    gifCredits: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Same neutral violet as Camera/Gallery — standard media, consistent.
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(com.randomchat.shnapp.theme.BrandViolet.copy(alpha = 0.12f), CircleShape)
            ) {
                Text(
                    "GIF",
                    color = com.randomchat.shnapp.theme.BrandViolet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
            }
            // Non-premium: credit badge (×N) or lock (0 = needs ad/premium)
            if (!isPremium) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(if (gifCredits > 0) 18.dp else 16.dp)
                        .clip(CircleShape)
                        .background(if (gifCredits > 0) AccentCyan else PremiumGold)
                ) {
                    if (gifCredits > 0) {
                        Text(
                            "×$gifCredits",
                            color = Color(0xFF001A22),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Lock,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
        Text("GIFs", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
