package com.example.aifinancerfree.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.aifinancerfree.data.ThemeMode
import com.example.aifinancerfree.data.local.ConsentPreferences
import com.example.aifinancerfree.ui.screens.*
import com.example.aifinancerfree.ui.viewmodel.AuthViewModel
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import com.example.aifinancerfree.ui.viewmodel.ProfileViewModel
import com.example.aifinancerfree.ui.viewmodel.ProfileUiState
import com.example.aifinancerfree.ui.viewmodel.AdvisorViewModel
import com.example.aifinancerfree.ui.viewmodel.GoalsViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

@Composable
fun FinanceNavGraph(
    nav: NavHostController,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    financeViewModel: FinanceViewModel,
    advisorViewModel: AdvisorViewModel,
    goalsViewModel: GoalsViewModel,
    consentPrefs: ConsentPreferences,
    mode: ThemeMode,
    selectTheme: (ThemeMode) -> Unit,
    startDestination: String,
    voiceViewModel: com.example.aifinancerfree.ui.viewmodel.VoiceAssistantViewModel? = null
) {
    val isLoggedIn by profileViewModel.isLoggedIn.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val username = when (val state = profileState) {
        is ProfileUiState.Success -> state.user.username
        else -> "User"
    }

    // Global listener for logout
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            nav.navigate(Routes.UnauthGraph) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) }
    ) {
        // Unauthenticated Nested Graph
        navigation(
            startDestination = Routes.Welcome,
            route = Routes.UnauthGraph
        ) {
            composable(Routes.Welcome) {
                WelcomeScreen(
                    login = { nav.go(Routes.Login) },
                    register = { nav.go(Routes.Register) }
                )
            }
            composable(Routes.Login) {
                LoginScreen(
                    viewModel = authViewModel,
                    onSuccess = {
                        financeViewModel.resetState()
                        nav.openDemo()
                        profileViewModel.fetchProfile()
                        financeViewModel.loadData()
                        financeViewModel.syncRemote()
                    },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.Register) {
                RegisterScreen(
                    viewModel = authViewModel,
                    onSuccess = {
                        financeViewModel.resetState()
                        nav.openDemo()
                        profileViewModel.fetchProfile()
                        financeViewModel.loadData()
                        financeViewModel.syncRemote()
                    },
                    onBack = { nav.popBackStack() }
                )
            }
        }

        // Authenticated Nested Graph
        navigation(
            startDestination = Routes.Dashboard,
            route = Routes.AuthGraph
        ) {
            composable(Routes.Dashboard) {
                AuthenticatedMainScreen(
                    nav = nav,
                    financeViewModel = financeViewModel,
                    profileViewModel = profileViewModel,
                    goalsViewModel = goalsViewModel,
                    voiceViewModel = voiceViewModel,
                    mode = mode,
                    selectTheme = selectTheme,
                    username = username,
                    initialTab = Routes.Dashboard
                )
            }
            composable(Routes.Transactions) {
                AuthenticatedMainScreen(
                    nav = nav,
                    financeViewModel = financeViewModel,
                    profileViewModel = profileViewModel,
                    goalsViewModel = goalsViewModel,
                    voiceViewModel = voiceViewModel,
                    mode = mode,
                    selectTheme = selectTheme,
                    username = username,
                    initialTab = Routes.Transactions
                )
            }
            composable(Routes.Goals) {
                AuthenticatedMainScreen(
                    nav = nav,
                    financeViewModel = financeViewModel,
                    profileViewModel = profileViewModel,
                    goalsViewModel = goalsViewModel,
                    voiceViewModel = voiceViewModel,
                    mode = mode,
                    selectTheme = selectTheme,
                    username = username,
                    initialTab = Routes.Goals
                )
            }
            composable(Routes.Profile) {
                AuthenticatedMainScreen(
                    nav = nav,
                    financeViewModel = financeViewModel,
                    profileViewModel = profileViewModel,
                    goalsViewModel = goalsViewModel,
                    voiceViewModel = voiceViewModel,
                    mode = mode,
                    selectTheme = selectTheme,
                    username = username,
                    initialTab = Routes.Profile
                )
            }
            composable(Routes.Add) {
                AddTransactionScreen(
                    viewModel = financeViewModel,
                    back = { nav.popBackStack() }
                )
            }
            composable(Routes.Advisor) {
                AdvisorChatScreen(
                    viewModel = advisorViewModel,
                    back = { nav.popBackStack() }
                )
            }
            composable(Routes.Consent) {
                ConsentScreen(
                    prefs = consentPrefs,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun AuthenticatedMainScreen(
    nav: NavHostController,
    financeViewModel: FinanceViewModel,
    profileViewModel: ProfileViewModel,
    goalsViewModel: GoalsViewModel,
    voiceViewModel: com.example.aifinancerfree.ui.viewmodel.VoiceAssistantViewModel?,
    mode: ThemeMode,
    selectTheme: (ThemeMode) -> Unit,
    username: String,
    initialTab: String = Routes.Dashboard
) {
    var currentTab by rememberSaveable { mutableStateOf(initialTab) }
    var selectedGoalsTab by rememberSaveable { mutableStateOf(0) }

    // Intercept back press when not on Dashboard tab, smoothly returning to Dashboard first
    BackHandler(enabled = currentTab != Routes.Dashboard) {
        currentTab = Routes.Dashboard
    }

    MainScaffold(
        nav = nav,
        currentTab = currentTab,
        onTabSelected = { newTab -> currentTab = newTab },
        voiceViewModel = voiceViewModel,
        onVoiceUpdate = { financeViewModel.loadData() }
    ) {
        Crossfade(
            targetState = currentTab,
            label = "TabCrossfade",
            animationSpec = tween(durationMillis = 150)
        ) { tab ->
            when (tab) {
                Routes.Dashboard -> {
                    DashboardScreen(
                        viewModel = financeViewModel,
                        username = username,
                        advisor = { nav.go(Routes.Advisor) }
                    )
                }
                Routes.Transactions -> {
                    TransactionsScreen(
                        viewModel = financeViewModel,
                        addTx = { nav.go(Routes.Add) }
                    )
                }
                Routes.Goals -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedGoalsTab) {
                            Tab(
                                selected = selectedGoalsTab == 0,
                                onClick = { selectedGoalsTab = 0 },
                                text = { Text("Budgets", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedGoalsTab == 1,
                                onClick = { selectedGoalsTab = 1 },
                                text = { Text("Savings Goals", fontWeight = FontWeight.Bold) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (selectedGoalsTab == 0) {
                                BudgetsScreen(viewModel = financeViewModel)
                            } else {
                                GoalsScreen(viewModel = goalsViewModel)
                            }
                        }
                    }
                }
                Routes.Profile -> {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        mode = mode,
                        select = selectTheme,
                        onConsent = { nav.go(Routes.Consent) },
                        logout = {
                            profileViewModel.logout()
                            financeViewModel.resetState()
                        }
                    )
                }
            }
        }
    }
}

