package com.example.localhand_new.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localhand_new.data.model.ChatMessage
import com.example.localhand_new.data.remote.LocalHandApi
import com.example.localhand_new.data.remote.dto.ChatRequest
import com.example.localhand_new.data.remote.dto.ChatTurnDto
import com.example.localhand_new.data.remote.toUserMessage
import com.example.localhand_new.data.repo.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** The prompts offered above the input, so the field is never blank-faced. */
val SuggestedPrompts = listOf(
    "How do I post a listing?",
    "Find me an electrician",
    "Find me a tutor nearby",
    "How do favourites work?",
)

private const val GREETING_ID = "greeting"
private const val TYPING_ID = "typing-indicator"

/** Only the last N turns are replayed as context — enough to feel continuous without an unbounded request body. */
private const val HISTORY_LIMIT = 20

private fun greetingFor(name: String) =
    "Hi $name — I'm the LocalHand assistant. Ask me how the app works, or " +
        "tell me what you need and I'll find a neighbour who can help."

/**
 * Backed by the LocalHand_Backend's /assistant/chat route, which calls Gemini
 * with function-calling access to real listing search — so replies are
 * grounded in actual data rather than guessed.
 */
class AssistantViewModel(
    private val api: LocalHandApi,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    // Placeholder greeting until the real signed-in name loads, replaced
    // in-place below rather than delaying the first frame on it.
    private val _messages = MutableStateFlow(
        listOf(ChatMessage(id = GREETING_ID, fromUser = false, text = greetingFor("there"))),
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        viewModelScope.launch {
            val name = prefs.userProfile.first()?.name?.substringBefore(" ")?.ifBlank { "there" } ?: "there"
            _messages.update { messages ->
                messages.map { if (it.id == GREETING_ID) it.copy(text = greetingFor(name)) else it }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isSending.value) return

        // The greeting is UI scaffolding, not a real turn — excluded from
        // what gets replayed as conversation history to the backend.
        val historyForRequest = _messages.value
            .filterNot { it.id == GREETING_ID }
            .takeLast(HISTORY_LIMIT)
            .map { ChatTurnDto(fromUser = it.fromUser, text = it.text) }

        _messages.update {
            it + ChatMessage(UUID.randomUUID().toString(), fromUser = true, text = trimmed) +
                ChatMessage(TYPING_ID, fromUser = false, text = "", isTyping = true)
        }
        _isSending.value = true

        viewModelScope.launch {
            runCatching { api.assistantChat(ChatRequest(message = trimmed, history = historyForRequest)) }
                .onSuccess { response ->
                    _messages.update { messages ->
                        messages.filterNot { it.id == TYPING_ID } + ChatMessage(
                            id = UUID.randomUUID().toString(),
                            fromUser = false,
                            text = response.reply,
                            listingId = response.listingId,
                        )
                    }
                }
                .onFailure { e ->
                    _messages.update { messages ->
                        messages.filterNot { it.id == TYPING_ID } +
                            ChatMessage(UUID.randomUUID().toString(), fromUser = false, text = e.toUserMessage())
                    }
                }
            _isSending.value = false
        }
    }
}
