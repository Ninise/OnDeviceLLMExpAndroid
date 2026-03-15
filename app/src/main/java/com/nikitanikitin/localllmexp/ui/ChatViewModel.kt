package com.example.ondevicellm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nikitanikitin.localllmexp.LlmManager
import com.nikitanikitin.localllmexp.tool.CreateEventTool
import com.nikitanikitin.localllmexp.tool.DescribeToolsTool
import com.nikitanikitin.localllmexp.tool.CreateNoteTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: Role,
    val content: String
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val llmReady: Boolean = false,
    val error: String? = null
)

/**
 * Mirrors the ViewModel pattern for the iOS chat view.
 * Wires LlmManager + tools together and exposes state to Compose UI.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val llmManager = LlmManager(application)

    // ─── Initialization ───────────────────────────────────────────────────────

    init {
        initializeLlm()
    }

    private fun initializeLlm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // AICore manages Gemini Nano on Pixel 9 — no model file needed
                llmManager.initialize()

                // Register tools — mirrors iOS `session.respond(to:including:)`
                // Add all real tools first, then DescribeToolsTool last so it can
                // reference the full list and accurately describe every tool.
                val createEventTool = CreateEventTool(getApplication())
                // CreateNoteTool gets llmManager (for summarization) and a lambda that
                // always returns the latest messages — evaluated lazily at call time.
                val createNoteTool = CreateNoteTool(
                    context = getApplication(),
                    llmManager = llmManager,
                    chatHistoryProvider = {
                        _uiState.value.messages
                            .filter { it.role != ChatMessage.Role.SYSTEM }
                            .map { it.role.name to it.content }
                    }
                )
                llmManager.addTool(createEventTool)
                llmManager.addTool(createNoteTool)
                llmManager.addTool(DescribeToolsTool(listOf(createEventTool, createNoteTool)))

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        llmReady = true,
                        messages = listOf(
                            ChatMessage(
                                ChatMessage.Role.SYSTEM,
                                "On-device LLM ready. Try: \"Schedule a team meeting tomorrow at 10am\""
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "❌ LLM init failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "LLM initialization failed: ${e.message}"
                    )
                }
            }
        }
    }

    // ─── Message Sending ──────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(ChatMessage.Role.USER, text)

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val response = llmManager.respond(text)

                val assistantMessage = ChatMessage(ChatMessage.Role.ASSISTANT, response)
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "❌ respond() failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmManager.close()
    }
}