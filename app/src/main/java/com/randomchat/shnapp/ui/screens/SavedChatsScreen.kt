package com.randomchat.shnapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.DeepSpace
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.ErrorRed
import com.randomchat.shnapp.theme.GradientEnd
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import com.randomchat.shnapp.ui.components.MessageBubble
import com.randomchat.shnapp.utils.SavedChatMeta
import com.randomchat.shnapp.utils.toDateTimeString
import com.randomchat.shnapp.viewmodel.SavedChatsViewModel

@Composable
fun SavedChatsScreen(
    viewModel: SavedChatsViewModel,
    onNavigateBack: () -> Unit
) {
    val chats by viewModel.chats.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedChatMeta?>(null) }
    val listState = rememberLazyListState()

    // Reload every time this screen is opened — ViewModel may have stale data from app start
    LaunchedEffect(Unit) { viewModel.loadSavedChats() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, GradientEnd)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface.copy(0.95f))
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedChatId != null) {
                    selectedChatId = null
                    viewModel.clearMessages()
                } else {
                    onNavigateBack()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
            }
            Text(
                if (selectedChatId != null) "Conversation" else "Saved Chats",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            return@Column
        }

        // Detail view
        if (selectedChatId != null) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(AccentCyan.copy(0.10f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                null,
                                tint = AccentCyan,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "No messages in this chat",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The conversation looks empty.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                }
            }
            return@Column
        }

        // List view
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 40.dp)
                ) {
                    // Icon in colored circle — more designed than flat icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(AccentCyan.copy(0.10f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            null,
                            tint = AccentCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "No saved chats yet",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Save a chat from the menu to keep it forever — works with photos and voice notes too.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chats, key = { it.id }) { chat ->
                    SavedChatRow(
                        chat = chat,
                        onClick = {
                            selectedChatId = chat.id
                            viewModel.loadMessages(chat.id)
                        },
                        onDelete = { deleteTarget = chat }
                    )
                }
            }
        }
    }

    // Delete confirmation — custom branded dialog (matches End Chat style)
    deleteTarget?.let { target ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { deleteTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, SubtleBorder, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Delete this chat?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This permanently removes the conversation and any saved photos or voice notes.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                            .clickable { deleteTarget = null }
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ErrorRed)
                            .clickable {
                                viewModel.deleteChat(target.id)
                                if (selectedChatId == target.id) {
                                    selectedChatId = null
                                    viewModel.clearMessages()
                                }
                                deleteTarget = null
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Delete",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedChatRow(
    chat: SavedChatMeta,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElevatedCard, RoundedCornerShape(14.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .background(AccentCyan.copy(0.12f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.ChatBubbleOutline, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(chat.savedAt.toDateTimeString(), color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            if (chat.preview.isNotBlank()) {
                Text(
                    chat.preview,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text("${chat.messageCount} messages", color = TextMuted, fontSize = 11.sp)
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
