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
                    Text("No messages", color = TextMuted, fontSize = 14.sp)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Bookmark, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No saved chats yet", color = TextMuted, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "End a chat and tap ⋮ → Save Chat",
                        color = TextMuted.copy(0.6f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
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

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = CardSurface,
            title = { Text("Delete chat?", color = TextPrimary) },
            text = { Text("This will permanently delete the conversation and all saved media.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(target.id)
                    if (selectedChatId == target.id) {
                        selectedChatId = null
                        viewModel.clearMessages()
                    }
                    deleteTarget = null
                }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
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
