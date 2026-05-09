package com.randomchat.shnapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randomchat.shnapp.model.ChatMessage
import com.randomchat.shnapp.utils.LocalChatStore
import com.randomchat.shnapp.utils.SavedChatMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SavedChatsViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    private val _chats = MutableStateFlow<List<SavedChatMeta>>(emptyList())
    val chats: StateFlow<List<SavedChatMeta>> = _chats

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadSavedChats()
    }

    fun loadSavedChats() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _chats.value = LocalChatStore.getSavedChats(context)
            _isLoading.value = false
        }
    }

    fun loadMessages(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _messages.value = LocalChatStore.getChatMessages(context, chatId)
            _isLoading.value = false
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalChatStore.deleteChat(context, chatId)
            _chats.value = LocalChatStore.getSavedChats(context)
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
