package com.example.aifinancerfree.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.aifinancerfree.ui.navigation.Routes
import com.example.aifinancerfree.ui.navigation.go
import com.example.aifinancerfree.ui.theme.FinanceTeal
import com.example.aifinancerfree.ui.viewmodel.VoiceAssistantViewModel
import com.example.aifinancerfree.ui.voice.VoiceAssistantBottomSheet

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    nav: NavHostController,
    currentTab: String = Routes.Dashboard,
    onTabSelected: (String) -> Unit = {},
    voiceViewModel: VoiceAssistantViewModel? = null,
    onVoiceUpdate: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val effectiveRoute = currentTab
    var showVoiceSheet by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceSheet = true
        } else {
            Toast.makeText(context, "Microphone permission is required to use the Voice Assistant.", Toast.LENGTH_SHORT).show()
        }
    }

    val openVoiceAssistant = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            showVoiceSheet = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val items = listOf(
        NavItem(Routes.Dashboard, "Home", Icons.Default.Home),
        NavItem(Routes.Transactions, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
        NavItem(Routes.Goals, "Goals", Icons.Default.Flag),
        NavItem(Routes.Profile, "Profile", Icons.Default.Person)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Financer", fontWeight = FontWeight.Bold) },
                actions = {
                    if (voiceViewModel != null) {
                        IconButton(onClick = openVoiceAssistant) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Voice Assistant",
                                tint = FinanceTeal
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = effectiveRoute == item.route,
                        onClick = {
                            onTabSelected(item.route)
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (effectiveRoute == Routes.Dashboard || effectiveRoute == Routes.Transactions) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (voiceViewModel != null) {
                        FloatingActionButton(
                            onClick = openVoiceAssistant,
                            containerColor = FinanceTeal,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "AI Voice Assistant")
                        }
                    }
                    FloatingActionButton(
                        onClick = { nav.go(Routes.Add) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add transaction")
                    }
                }
            }
        }
    ) { pad ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            content()
        }
    }

    if (showVoiceSheet && voiceViewModel != null) {
        VoiceAssistantBottomSheet(
            viewModel = voiceViewModel,
            onDismiss = {
                showVoiceSheet = false
                onVoiceUpdate?.invoke()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopScreen(title: String, message: String, back: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = back) {
                        Text("Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, message: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
