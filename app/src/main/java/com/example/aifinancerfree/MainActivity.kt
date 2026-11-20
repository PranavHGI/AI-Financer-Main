package com.example.aifinancerfree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.aifinancerfree.data.ThemeMode
import com.example.aifinancerfree.data.local.ConsentPreferences
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.network.ApiClient
import com.example.aifinancerfree.data.repository.AuthRepository
import com.example.aifinancerfree.data.repository.TransactionRepository
import com.example.aifinancerfree.ui.ThemeViewModel
import com.example.aifinancerfree.ui.navigation.FinanceNavGraph
import com.example.aifinancerfree.ui.navigation.Routes
import com.example.aifinancerfree.ui.theme.AIFinancerFreeTheme
import com.example.aifinancerfree.ui.viewmodel.AuthViewModel
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import com.example.aifinancerfree.ui.viewmodel.ProfileViewModel
import com.example.aifinancerfree.ui.viewmodel.AdvisorViewModel
import com.example.aifinancerfree.ui.viewmodel.GoalsViewModel
import com.example.aifinancerfree.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as? AIFinancerApplication ?: AIFinancerApplication.instance

        enableEdgeToEdge()
        setContent {
            AIFinancerApp(
                tokenManager = app.tokenManager,
                authRepository = app.authRepository,
                consentPreferences = app.consentPreferences,
                transactionRepository = app.transactionRepository,
                databaseHelper = app.databaseHelper,
                voiceAssistantRepositoryProvider = { app.voiceAssistantRepository },
                speechManagerProvider = { app.speechRecognitionManager },
                ttsManagerProvider = { app.textToSpeechManager }
            )
        }
    }
}

@Composable
private fun AIFinancerApp(
    tokenManager: TokenManager,
    authRepository: AuthRepository,
    consentPreferences: ConsentPreferences,
    transactionRepository: TransactionRepository,
    databaseHelper: DatabaseHelper,
    voiceAssistantRepositoryProvider: (() -> com.example.aifinancerfree.data.repository.VoiceAssistantRepository)? = null,
    speechManagerProvider: (() -> com.example.aifinancerfree.data.voice.SpeechRecognitionManager)? = null,
    ttsManagerProvider: (() -> com.example.aifinancerfree.data.voice.TextToSpeechManager)? = null,
    themeViewModel: ThemeViewModel = viewModel()
) {
    val mode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    
    val factory = ViewModelFactory(
        authRepository = authRepository,
        tokenManager = tokenManager,
        transactionRepository = transactionRepository,
        databaseHelper = databaseHelper,
        voiceAssistantRepositoryProvider = voiceAssistantRepositoryProvider,
        speechManagerProvider = speechManagerProvider,
        ttsManagerProvider = ttsManagerProvider
    )
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)
    val financeViewModel: FinanceViewModel = viewModel(factory = factory)
    val advisorViewModel: AdvisorViewModel = viewModel(factory = factory)
    val goalsViewModel: GoalsViewModel = viewModel(factory = factory)
    val voiceViewModel: com.example.aifinancerfree.ui.viewmodel.VoiceAssistantViewModel = viewModel(factory = factory)

    val startDestination = if (tokenManager.getAccessToken() != null) {
        Routes.AuthGraph
    } else {
        Routes.UnauthGraph
    }

    AIFinancerFreeTheme(darkTheme = dark) {
        FinanceNavGraph(
            nav = rememberNavController(),
            authViewModel = authViewModel,
            profileViewModel = profileViewModel,
            financeViewModel = financeViewModel,
            advisorViewModel = advisorViewModel,
            goalsViewModel = goalsViewModel,
            voiceViewModel = voiceViewModel,
            consentPrefs = consentPreferences,
            mode = mode,
            selectTheme = { themeViewModel.selectTheme(it) },
            startDestination = startDestination
        )
    }
}
