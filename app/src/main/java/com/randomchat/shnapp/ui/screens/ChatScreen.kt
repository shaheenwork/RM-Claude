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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.randomchat.shnapp.theme.PremiumGold
import com.randomchat.shnapp.ui.dialogs.ReportDialog
import com.randomchat.shnapp.viewmodel.ChatViewModel
import com.randomchat.shnapp.viewmodel.SaveProgress
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val context = LocalContext.current
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
    val showMyBadge by viewModel.showMyBadge.collectAsState()
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

    // Auto-dismiss "Saved ✓" after 2s
    LaunchedEffect(saveProgress?.isDone) {
        if (saveProgress?.isDone == true) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveProgress()
        }
    }
    val listState = rememberLazyListState()

    // ── Photo source: gallery or camera ──────────────────────────────────────
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    // Camera capture — FileProvider URI created just before launch
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        viewModel.uploadAndSendImage(bytes)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) return@rememberLauncherForActivityResult
        val uri = cameraOutputUri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        // Delete temp file — bytes already in memory
        runCatching { File(uri.path ?: "").delete() }
        viewModel.uploadAndSendImage(bytes)
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
                            "Anonymous Stranger",
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
                        "👋  You're connected to a random stranger — say Hi!",
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
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        messageReactions = reactions[message.id] ?: emptyMap(),
                        mySessionId = viewModel.sessionId,
                        isPremium = isPremium,
                        onLongPress = { reactionTargetMessage = it },
                        onReactionTap = { emoji -> viewModel.reactToMessage(message.id, emoji) }
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

                    // Banner ad — shown to non-premium users during idle chat-ended moment
                    if (!isPremium) {
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
                        text = "New Stranger",
                        onClick = { viewModel.newChat(); onNavigateBack() },
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
            if (!chatEnded && !isPremium && !isRecording) {
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

            // ── Message Composer / Recording Bar ────────────────────────────
            if (!chatEnded) {
                if (isRecording) {
                    RecordingBar(
                        durationMs = recordingDurationMs,
                        onSend = { viewModel.stopAndSendAudio() },
                        onCancel = { viewModel.cancelAudioRecording() }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Image button — opens Camera / Gallery picker
                        ImageButton(
                            isPremium = isPremium,
                            onClick = {
                                if (!isPremium) { onNavigateToPremium(); return@ImageButton }
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) showPhotoSourceDialog = true
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )

                        // Mic button — tap to start recording
                        AudioButton(
                            isPremium = isPremium,
                            isRecording = false,
                            onClick = {
                                if (!isPremium) { onNavigateToPremium(); return@AudioButton }
                                cancelTriggered = false
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )

                        // Text input
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(ElevatedCard)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                        viewModel.broadcastDraftText(new) // real-time preview for premium stranger
                                    }
                                },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                                cursorBrush = SolidColor(AccentCyan),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                        }

                        // Send button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank())
                                        Brush.radialGradient(listOf(AccentCyan, com.randomchat.shnapp.theme.AccentCyanDim))
                                    else
                                        Brush.radialGradient(listOf(SubtleBorder, SubtleBorder))
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    val text = inputText.trim()
                                    if (text.isNotBlank()) {
                                        viewModel.sendMessage(text)
                                        inputText = ""
                                        viewModel.notifyTyping(false)
                                    }
                                },
                                enabled = inputText.isNotBlank()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    null,
                                    tint = if (inputText.isNotBlank()) Color(0xFF001A22) else TextMuted,
                                    modifier = Modifier.size(20.dp)
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
            showReportDialog = false
        }
    )

    if (showEndChatDialog) {
        AlertDialog(
            onDismissRequest = { showEndChatDialog = false },
            containerColor = CardSurface,
            title = { Text("End Chat?", color = TextPrimary) },
            text = { Text("Are you sure you want to end this conversation?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showEndChatDialog = false
                    viewModel.endChat()
                }) {
                    Text("End Chat", color = com.randomchat.shnapp.theme.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndChatDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
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

        // Send button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(AccentCyan, com.randomchat.shnapp.theme.AccentCyanDim)))
                .clickable(onClick = onSend)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color(0xFF001A22),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
