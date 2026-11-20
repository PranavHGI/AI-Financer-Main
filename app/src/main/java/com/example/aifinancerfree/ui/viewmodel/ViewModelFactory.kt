package com.example.aifinancerfree.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.repository.AuthRepository
import com.example.aifinancerfree.data.repository.TransactionRepository

class ViewModelFactory(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val transactionRepository: TransactionRepository,
    private val databaseHelper: DatabaseHelper,
    private val voiceAssistantRepositoryProvider: (() -> com.example.aifinancerfree.data.repository.VoiceAssistantRepository)? = null,
    private val speechManagerProvider: (() -> com.example.aifinancerfree.data.voice.SpeechRecognitionManager)? = null,
    private val ttsManagerProvider: (() -> com.example.aifinancerfree.data.voice.TextToSpeechManager)? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(authRepository) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(authRepository, tokenManager) as T
            }
            modelClass.isAssignableFrom(FinanceViewModel::class.java) -> {
                FinanceViewModel(transactionRepository, databaseHelper) as T
            }
            modelClass.isAssignableFrom(AdvisorViewModel::class.java) -> {
                AdvisorViewModel(transactionRepository) as T
            }
            modelClass.isAssignableFrom(GoalsViewModel::class.java) -> {
                GoalsViewModel(transactionRepository) as T
            }
            modelClass.isAssignableFrom(VoiceAssistantViewModel::class.java) -> {
                val voiceRepo = voiceAssistantRepositoryProvider?.invoke() ?: throw IllegalStateException("VoiceAssistantRepository not provided")
                val speech = speechManagerProvider?.invoke() ?: throw IllegalStateException("SpeechRecognitionManager not provided")
                val tts = ttsManagerProvider?.invoke() ?: throw IllegalStateException("TextToSpeechManager not provided")
                VoiceAssistantViewModel(voiceRepo, speech, tts) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
