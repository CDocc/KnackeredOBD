package com.knackered.obd.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.knackered.obd.StandInSession
import com.knackered.obd.session.ObdClient
import com.knackered.obd.session.TerminalReply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TerminalOutcome {
    REPLY,
    BLOCKED,
    FAILED,
}

data class TerminalLine(
    val command: String,
    val body: String,
    val outcome: TerminalOutcome,
)

data class TerminalUiState(
    val draft: String = "",
    val history: List<TerminalLine> = emptyList(),
    val sending: Boolean = false,
    val connected: Boolean = false,
    val status: String = "Connecting stand-in adapter…",
)

class TerminalViewModel(
    private val client: ObdClient,
    private val awaitConnected: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                awaitConnected()
                _state.update {
                    it.copy(
                        connected = true,
                        status = "Stand-in adapter ready. Nothing has been sent to a car.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(connected = false, status = error.message ?: "Could not open the adapter.")
                }
            }
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun send() {
        val draft = _state.value.draft
        if (draft.isBlank() || _state.value.sending || !_state.value.connected) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            val line = try {
                client.submitTerminal(draft).toLine(draft)
            } catch (error: Exception) {
                TerminalLine(
                    command = draft.trim(),
                    body = error.message ?: "Send failed.",
                    outcome = TerminalOutcome.FAILED,
                )
            }
            _state.update {
                it.copy(
                    sending = false,
                    draft = if (line.outcome == TerminalOutcome.REPLY) "" else it.draft,
                    history = it.history + line,
                )
            }
        }
    }

    private fun TerminalReply.toLine(draft: String): TerminalLine {
        val shown = command.ifBlank { draft.trim() }
        return when {
            sent -> TerminalLine(shown, raw.ifBlank { "(empty reply)" }, TerminalOutcome.REPLY)
            else -> TerminalLine(shown, message ?: "Not sent.", TerminalOutcome.BLOCKED)
        }
    }

    companion object {
        fun factory(session: StandInSession): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TerminalViewModel(session.client, session::awaitConnected) as T
            }
    }
}
