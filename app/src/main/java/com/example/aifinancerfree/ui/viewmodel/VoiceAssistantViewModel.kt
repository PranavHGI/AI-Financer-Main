package com.example.aifinancerfree.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aifinancerfree.data.model.VoiceActionResult
import com.example.aifinancerfree.data.model.VoiceCommandResponse
import com.example.aifinancerfree.data.repository.VoiceAssistantRepository
import com.example.aifinancerfree.data.voice.SpeechRecognitionManager
import com.example.aifinancerfree.data.voice.SpeechState
import com.example.aifinancerfree.data.voice.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val rmsDb: Float = 0f,
    val liveTranscript: String = "",
    val assistantResponse: VoiceCommandResponse? = null,
    val errorMessage: String? = null,
    val undoStatus: String? = null,
    val canUndo: Boolean = false
)

class VoiceAssistantViewModel(
    private val voiceRepository: VoiceAssistantRepository,
    private val speechManager: SpeechRecognitionManager,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        ttsManager.initialize()

        // Observe speech recognition state
        viewModelScope.launch {
            speechManager.state.collect { state ->
                when (state) {
                    is SpeechState.Listening -> {
                        _uiState.update {
                            it.copy(
                                isListening = true,
                                isProcessing = false,
                                rmsDb = state.rmsDb,
                                errorMessage = null
                            )
                        }
                    }
                    is SpeechState.Processing -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                isProcessing = true
                            )
                        }
                    }
                    is SpeechState.Result -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                liveTranscript = state.text
                            )
                        }
                        if (state.text.isNotBlank()) {
                            processCommand(state.text)
                        }
                    }
                    is SpeechState.Error -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                isProcessing = false,
                                errorMessage = state.message
                            )
                        }
                    }
                    SpeechState.Idle, SpeechState.Ready, SpeechState.Initializing -> {
                        _uiState.update { it.copy(isListening = false) }
                    }
                }
            }
        }

        // Observe partial live transcription
        viewModelScope.launch {
            speechManager.partialTranscript.collect { partial ->
                if (partial.isNotBlank()) {
                    _uiState.update { it.copy(liveTranscript = partial) }
                }
            }
        }

        // Observe TTS playback state
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }

        // Observe TTS mute state
        viewModelScope.launch {
            ttsManager.isMuted.collect { muted ->
                _uiState.update { it.copy(isMuted = muted) }
            }
        }
    }

    fun startListening() {
        ttsManager.stop()
        _uiState.update {
            it.copy(
                errorMessage = null,
                undoStatus = null,
                liveTranscript = ""
            )
        }
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun processCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        speechManager.cancel()
        ttsManager.stop()

        _uiState.update {
            it.copy(
                isListening = false,
                isProcessing = true,
                liveTranscript = trimmed,
                errorMessage = null,
                undoStatus = null
            )
        }

        viewModelScope.launch {
            val result = voiceRepository.executeVoiceCommand(trimmed)
            when (result) {
                is VoiceActionResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            assistantResponse = result.response,
                            canUndo = result.canUndo,
                            errorMessage = null
                        )
                    }
                    ttsManager.speak(result.response.spokenText)
                }
                is VoiceActionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.message
                        )
                    }
                    ttsManager.speak(result.spokenFallback)
                }
            }
        }
    }

    fun undoLastAction() {
        viewModelScope.launch {
            val (success, message) = voiceRepository.undoLastAction()
            _uiState.update {
                it.copy(
                    undoStatus = message,
                    canUndo = false
                )
            }
            if (success) {
                ttsManager.speak(message)
            }
        }
    }

    fun toggleMute() {
        ttsManager.toggleMute()
    }

    fun onDismiss() {
        speechManager.cancel()
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.cancel()
        ttsManager.stop()
    }
}
