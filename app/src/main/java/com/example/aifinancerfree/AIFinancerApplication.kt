package com.example.aifinancerfree

import android.app.Application
import com.example.aifinancerfree.data.local.ConsentPreferences
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.network.ApiClient
import com.example.aifinancerfree.data.repository.AuthRepository
import com.example.aifinancerfree.data.repository.TransactionRepository
import com.example.aifinancerfree.data.sms.SmsAccessManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AIFinancerApplication : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var consentPreferences: ConsentPreferences
        private set

    lateinit var databaseHelper: DatabaseHelper
        private set

    lateinit var transactionRepository: TransactionRepository
        private set

    lateinit var smsAccessManager: SmsAccessManager
        private set

    val speechRecognitionManager: com.example.aifinancerfree.data.voice.SpeechRecognitionManager by lazy {
        com.example.aifinancerfree.data.voice.SpeechRecognitionManager(this)
    }

    val textToSpeechManager: com.example.aifinancerfree.data.voice.TextToSpeechManager by lazy {
        com.example.aifinancerfree.data.voice.TextToSpeechManager(this)
    }

    val voiceAssistantRepository: com.example.aifinancerfree.data.repository.VoiceAssistantRepository by lazy {
        com.example.aifinancerfree.data.repository.VoiceAssistantRepository(
            apiService = apiClient.apiService,
            dbHelper = databaseHelper,
            tokenManager = tokenManager,
            transactionRepository = transactionRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenManager = TokenManager(this)
        apiClient = ApiClient(tokenManager)
        authRepository = AuthRepository(apiClient.apiService, tokenManager)
        consentPreferences = ConsentPreferences(this)
        databaseHelper = DatabaseHelper(this)
        transactionRepository = TransactionRepository(apiClient.apiService, databaseHelper, tokenManager)
        smsAccessManager = SmsAccessManager(this, consentPreferences)

        // Asynchronously clean up any existing duplicate records on IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            databaseHelper.cleanupDuplicateTransactions()
        }
    }

    companion object {
        lateinit var instance: AIFinancerApplication
            private set
    }
}
