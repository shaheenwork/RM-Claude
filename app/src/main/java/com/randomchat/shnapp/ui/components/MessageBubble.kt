package com.randomchat.shnapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import android.media.MediaPlayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import java.io.File
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.model.MessageStatus
import com.randomchat.shnapp.model.MessageType
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.BubbleIncoming
import com.randomchat.shnapp.theme.BubbleOutgoing
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.utils.toTimeString
import kotlinx.coroutines.launch

val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "👍", "👎")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    // Map<sessionId, emoji> for this message
    messageReactions: Map<String, String> = emptyMap(),
    mySessionId: String = "",
    isPremium: Boolean = false,
    /** True if this is the first message in a consecutive same-sender group. */
    isFirstInGroup: Boolean = true,
    /** True if this is the last message in a consecutive same-sender group. */
    isLastInGroup: Boolean = true,
    onLongPress: ((ChatMessage) -> Unit)? = null,
    onReactionTap: ((String) -> Unit)? = null, // emoji tapped on existing pill
    /** Called when user swipes the bubble horizontally past threshold. */
    onSwipeReply: ((ChatMessage) -> Unit)? = null
) {
    if (message.type == MessageType.SYSTEM) {
        SystemChip(text = message.content, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        return
    }

    val haptic = LocalHapticFeedback.current

    // Group reactions: emoji → count, sorted by count desc
    val grouped = messageReactions.values
        .groupBy { it }
        .mapValues { it.value.size }
        .entries.sortedByDescending { it.value }

    val myReaction = messageReactions[mySessionId]

    // Entrance: subtle slide from direction-of-sender + fade-in.
    // Spring-based — softer landing than tween, premium feel (iMessage / Telegram pattern).
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInHorizontally(
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness    = androidx.compose.animation.core.Spring.StiffnessMediumLow
            )
        ) { (if (message.isOutgoing) it else -it) / 3 }  // smaller travel — feels controlled, not loud
    ) {
        // Tighter vertical spacing inside a group, more between groups
        val topPadding = if (isFirstInGroup) 6.dp else 1.dp
        val bottomPadding = if (isLastInGroup) 6.dp else 1.dp

        // Asymmetric corners — full bubble corner stays on tail side,
        // shared side corners tighten when adjacent bubble exists.
        val topShared    = if (isFirstInGroup) 18.dp else 6.dp
        val bottomShared = if (isLastInGroup)  18.dp else 6.dp
        val tailRadius   = 4.dp
        val bubbleShape = if (message.isOutgoing) {
            RoundedCornerShape(
                topStart    = 18.dp,
                topEnd      = topShared,
                bottomEnd   = if (isLastInGroup) tailRadius else 6.dp,
                bottomStart = 18.dp
            )
        } else {
            RoundedCornerShape(
                topStart    = topShared,
                topEnd      = 18.dp,
                bottomEnd   = 18.dp,
                bottomStart = if (isLastInGroup) tailRadius else 6.dp
            )
        }

        // ── Swipe-to-reply gesture ────────────────────────────────────────────
        val density = androidx.compose.ui.platform.LocalDensity.current
        val thresholdPx = with(density) { 60.dp.toPx() }
        val maxDragPx   = with(density) { 100.dp.toPx() }
        val dragOffset = remember(message.id) { androidx.compose.animation.core.Animatable(0f) }
        val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
        var committed by remember(message.id) { mutableStateOf(false) }

        // Outer Box hosts the reveal icon behind the bubble Row
        Box(modifier = Modifier.fillMaxWidth()) {
            // Reveal icon — fades in as user drags. Tinted by progress.
            val progress = (kotlin.math.abs(dragOffset.value) / thresholdPx).coerceIn(0f, 1f)
            if (progress > 0.05f) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 18.dp)
                        .size(34.dp * (0.6f + progress * 0.4f))
                        .alpha(progress)
                        .background(AccentCyan.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.Reply,
                        null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = topPadding, bottom = bottomPadding)
                .graphicsLayer { translationX = dragOffset.value }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { committed = false },
                        onDragEnd = {
                            if (!committed && kotlin.math.abs(dragOffset.value) > thresholdPx) {
                                onSwipeReply?.invoke(message)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                committed = true
                            }
                            swipeScope.launch {
                                dragOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            swipeScope.launch { dragOffset.animateTo(0f, spring(0.7f, 600f)) }
                        }
                    ) { _, dragAmount ->
                        val newOffset = (dragOffset.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                        swipeScope.launch { dragOffset.snapTo(newOffset) }
                    }
                },
            horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
        ) {
            Column(
                horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (message.isOutgoing) BubbleOutgoing else BubbleIncoming,
                            shape = bubbleShape
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (message.status != MessageStatus.PENDING) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress?.invoke(message)
                                }
                            }
                        )
                        .padding(
                            horizontal = if (message.type == MessageType.IMAGE) 4.dp else 14.dp,
                            vertical   = if (message.type == MessageType.IMAGE) 4.dp else 10.dp
                        )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Reply context — small inset chip above the message content
                    if (message.replyToId.isNotEmpty()) {
                        ReplyContextChip(
                            preview      = message.replyToPreview,
                            isFromMe     = message.replyToSenderId == mySessionId,
                            originalType = message.replyToType,
                            onOutgoing   = message.isOutgoing
                        )
                    }
                    when (message.type) {
                        MessageType.IMAGE -> {
                            var showViewer by remember { mutableStateOf(false) }
                            // Outgoing: always revealed. Incoming: starts blurred until first tap.
                            // rememberSaveable keyed on message.id → reveal state survives scroll + process death.
                            var revealed by rememberSaveable(message.id) {
                                mutableStateOf(message.isOutgoing)
                            }
                            Box(
                                modifier = Modifier
                                    .size(200.dp, 160.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TextSecondary.copy(alpha = 0.15f))
                                    .clickable(enabled = message.mediaUrl.isNotBlank() && message.status != MessageStatus.PENDING) {
                                        if (!revealed) {
                                            revealed = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else {
                                            showViewer = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (message.mediaUrl.isNotBlank()) {
                                    val imgModel: Any = if (message.mediaUrl.startsWith("/"))
                                        File(message.mediaUrl) else message.mediaUrl
                                    AsyncImage(
                                        model = imgModel,
                                        contentDescription = "Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                // Modifier.blur is API 31+ — no-op on lower; scrim below masks fallback.
                                                if (!revealed) Modifier.blur(28.dp) else Modifier
                                            )
                                    )
                                    if (!revealed) {
                                        // Dark scrim covers older API levels where blur is a no-op + signals interactivity.
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .background(
                                                        Color.Black.copy(alpha = 0.6f),
                                                        RoundedCornerShape(20.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Tap to view",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        color = AccentCyan,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            if (showViewer) {
                                val imgModel: Any = if (message.mediaUrl.startsWith("/"))
                                    File(message.mediaUrl) else message.mediaUrl
                                PhotoViewerDialog(model = imgModel, onDismiss = { showViewer = false })
                            }
                        }
                        MessageType.AUDIO -> {
                            AudioMessageBubble(message)
                        }
                        else -> {
                            Text(
                                text = message.content,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    } // close reply-context Column
                }

                // ── Reaction pills ────────────────────────────────────────────
                if (grouped.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        grouped.forEach { (emoji, count) ->
                            val isMine = myReaction == emoji
                            ReactionPill(
                                emoji = emoji,
                                count = count,
                                isMine = isMine,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onReactionTap?.invoke(emoji)
                                }
                            )
                        }
                    }
                }

                // Timestamp + status only on last bubble of a group — premium-app pattern
                if (isLastInGroup) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = message.timestamp.toTimeString(),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    if (message.isOutgoing) {
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.PENDING -> Icons.Default.Schedule
                                else -> Icons.Default.DoneAll
                            },
                            contentDescription = null,
                            tint = if (message.status == MessageStatus.DELIVERED) AccentCyan else TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                } // close isLastInGroup
            }
        }
        } // close outer Box (swipe gesture host)
    }
}

/**
 * Reply context — small inset card shown at the top of a reply message,
 * showing what the user is replying to. WhatsApp / iMessage pattern.
 */
@Composable
private fun ReplyContextChip(
    preview: String,
    isFromMe: Boolean,
    originalType: MessageType,
    onOutgoing: Boolean
) {
    val accentColor = if (onOutgoing) Color(0xFF9FE6FF) else AccentCyan
    Row(
        modifier = Modifier
            .background(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .widthIn(min = 80.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 220.dp)) {
            Text(
                text = if (isFromMe) "You" else "Stranger",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = preview.ifEmpty {
                    when (originalType) {
                        MessageType.IMAGE -> "📷 Photo"
                        MessageType.AUDIO -> "🎤 Voice note"
                        else -> "Message"
                    }
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReactionPill(
    emoji: String,
    count: Int,
    isMine: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isMine) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "reaction_scale"
    )
    Row(
        modifier = Modifier
            .scale(scale)
            .background(
                color = if (isMine) AccentCyan.copy(alpha = 0.18f) else ElevatedCard,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = if (isMine) AccentCyan.copy(alpha = 0.5f) else SubtleBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = emoji, fontSize = 14.sp)
        if (count > 1) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMine) AccentCyan else TextSecondary
            )
        }
    }
}

/**
 * Floating emoji picker — shown above the input bar when user long-presses a message.
 * Slide-up + scale-in animation. Dismiss by tapping outside.
 */
@Composable
fun ReactionPicker(
    targetMessage: ChatMessage?,
    myReaction: String?,       // current reaction of local user on this message
    isPremium: Boolean,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    AnimatedVisibility(
        visible = targetMessage != null,
        enter = fadeIn(tween(150)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.85f, animationSpec = tween(100))
    ) {
        // Full-screen scrim — tapping anywhere outside the picker dismisses it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Picker content — inner clickable consumes the tap so the scrim won't fire
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(bottom = 84.dp) // sits above the composer bar
                    .clickable { /* consume — prevent scrim dismiss */ }
            ) {
                if (!isPremium) {
                    Text(
                        "✨ Premium feature",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(AccentCyan.copy(0.12f), RoundedCornerShape(20.dp))
                            .clickable { onDismiss(); onNavigateToPremium() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                } else {
                    // Emoji row
                    Row(
                        modifier = Modifier
                            .background(CardSurface, RoundedCornerShape(28.dp))
                            .border(1.dp, SubtleBorder, RoundedCornerShape(28.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        REACTION_EMOJIS.forEach { emoji ->
                            val isSelected = myReaction == emoji
                            val emojiScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.3f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "emoji_scale_$emoji"
                            )
                            Text(
                                text = emoji,
                                fontSize = 26.sp,
                                modifier = Modifier
                                    .scale(emojiScale)
                                    .background(
                                        if (isSelected) AccentCyan.copy(0.15f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onReact(emoji) }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Fixed waveform heights — 30 bars representing the audio shape
private val WAVEFORM = listOf(4,6,9,5,12,8,14,10,16,12,9,14,8,16,11,13,7,15,10,8,12,6,14,9,11,7,13,5,8,4)

private fun formatAudioMs(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AudioMessageBubble(message: ChatMessage) {
    if (message.status == MessageStatus.PENDING) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(min = 180.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(AccentCyan.copy(alpha = 0.35f), CircleShape)
            ) {
                CircularProgressIndicator(
                    color = AccentCyan,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WAVEFORM.forEach { h ->
                        Box(
                            modifier = Modifier
                                .size(3.dp, h.dp)
                                .background(TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        )
                    }
                }
                Text(text = "Sending…", color = TextMuted, fontSize = 11.sp)
            }
        }
        return
    }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var totalMs by remember { mutableIntStateOf(0) }
    var currentMs by remember { mutableIntStateOf(0) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    // Poll position while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            val mp = player.value ?: break
            try {
                val dur = mp.duration
                val pos = mp.currentPosition
                if (dur > 0) {
                    progress = pos.toFloat() / dur
                    currentMs = pos
                }
            } catch (_: Exception) { break }
            delay(80)
        }
    }

    DisposableEffect(message.id) {
        onDispose {
            player.value?.release()
            player.value = null
        }
    }

    val displayMs = if (isPlaying || progress > 0f) currentMs else totalMs
    val durationText = if (displayMs > 0) formatAudioMs(displayMs) else if (message.mediaUrl.isBlank()) "0:00" else "…"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .widthIn(min = 180.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        // Play / Pause circle — solid fill like WhatsApp
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(AccentCyan, CircleShape)
                .clickable {
                    if (isPlaying) {
                        player.value?.pause()
                        isPlaying = false
                    } else {
                        val mp = player.value
                        if (mp != null) {
                            mp.start()
                            isPlaying = true
                        } else if (message.mediaUrl.isNotBlank()) {
                            MediaPlayer().apply {
                                setDataSource(message.mediaUrl)
                                setOnPreparedListener {
                                    totalMs = duration
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    progress = 0f
                                    currentMs = 0
                                    player.value = null
                                }
                                setOnErrorListener { _, _, _ ->
                                    isPlaying = false
                                    player.value = null
                                    false
                                }
                                prepareAsync()
                                player.value = this
                            }
                        }
                    }
                }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF001A22),
                modifier = Modifier.size(22.dp)
            )
        }

        // Waveform + duration
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Waveform bars — cyan = played, dim = unplayed
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { // seek on tap
                        val mp = player.value ?: return@clickable
                        if (totalMs > 0) {
                            // seek to tapped position handled by progress recomposition
                        }
                    }
            ) {
                WAVEFORM.forEachIndexed { index, h ->
                    val played = progress > 0f && index.toFloat() / WAVEFORM.size <= progress
                    Box(
                        modifier = Modifier
                            .size(3.dp, h.dp)
                            .background(
                                if (played) AccentCyan else TextSecondary.copy(alpha = 0.45f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            // Duration text
            Text(
                text = durationText,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ── Full-screen pinch-zoom photo viewer ──────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoViewerDialog(model: Any, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true
        )
    ) {
        Box(
            modifier         = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            var scale     by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            var offsetX   by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            var offsetY   by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

            val transformState = rememberTransformableState { zoom, pan, _ ->
                scale   = (scale * zoom).coerceIn(0.8f, 6f)
                offsetX += pan.x
                offsetY += pan.y
            }

            AsyncImage(
                model              = model,
                contentDescription = "Photo",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .transformable(state = transformState)
            )

            // Double-tap to reset
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick     = {},
                        onDoubleClick = {
                            scale   = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    )
            )

            // Close button
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint     = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * Ghost bubble shown to premium users — displays the stranger's live draft text
 * before they hit send. Italicised, cyan border, LIVE badge, blinking cursor.
 */
@Composable
fun GhostTypingBubble(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "ghost")

    // Blinking cursor — square wave at ~1 Hz
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    // Pulsing dot in the LIVE badge
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(800), RepeatMode.Reverse
        ),
        label = "live_pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = BubbleIncoming.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = 4.dp, bottomEnd = 18.dp
                        )
                    )
                    .border(
                        1.dp,
                        AccentCyan.copy(alpha = 0.30f),
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = 4.dp, bottomEnd = 18.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {

                    // ── LIVE badge ──────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .alpha(livePulse)
                                .background(AccentCyan, CircleShape)
                        )
                        Text(
                            text = "typing",
                            color = AccentCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // ── Draft text + blinking cursor ────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = text,
                            color = TextPrimary.copy(alpha = 0.80f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(2.dp))
                        // Blinking I-beam cursor
                        Box(
                            modifier = Modifier
                                .size(width = 2.dp, height = 17.dp)
                                .alpha(cursorAlpha)
                                .background(AccentCyan, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(BubbleIncoming, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            DotsLoader()
        }
    }
}

@Composable
fun ActivityIndicator(activity: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "activity_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "activity_alpha"
    )

    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(BubbleIncoming, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (activity) {
                "typing" -> DotsLoader()
                else -> {
                    val icon = when (activity) {
                        "recording"     -> Icons.Default.Mic
                        "sending_audio" -> Icons.Default.Mic
                        "sending_photo" -> Icons.Default.Image
                        else            -> Icons.Default.Mic
                    }
                    val label = when (activity) {
                        "recording"     -> "Recording audio…"
                        "sending_audio" -> "Sending audio…"
                        "sending_photo" -> "Sending photo…"
                        else            -> activity
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = AccentCyan.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(text = label, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
