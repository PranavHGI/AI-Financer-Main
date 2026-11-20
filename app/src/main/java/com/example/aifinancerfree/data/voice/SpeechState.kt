package com.example.aifinancerfree.data.voice

/**
 * Represents the real-time state of the speech recognition pipeline.
 */
sealed interface SpeechState {
    data object Idle : SpeechState
    data object Initializing : SpeechState
    data object Ready : SpeechState
    data class Listening(val rmsDb: Float = 0f) : SpeechState
    data object Processing : SpeechState
    data class Result(val text: String) : SpeechState
    data class Error(val message: String, val errorCode: Int = -1) : SpeechState
}
