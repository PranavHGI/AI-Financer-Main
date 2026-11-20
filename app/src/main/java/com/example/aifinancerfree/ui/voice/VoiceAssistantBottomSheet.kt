package com.example.aifinancerfree.ui.voice

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aifinancerfree.data.model.VoiceActionEntity
import com.example.aifinancerfree.ui.theme.FinanceMint
import com.example.aifinancerfree.ui.theme.FinanceTeal
import com.example.aifinancerfree.ui.theme.FinanceTealLight
import com.example.aifinancerfree.ui.theme.WarmAccent
import com.example.aifinancerfree.ui.viewmodel.VoiceAssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantBottomSheet(
    viewModel: VoiceAssistantViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    var textInput by remember { mutableStateOf("") }

    // Automatically start listening when bottom sheet appears
    LaunchedEffect(Unit) {
        viewModel.startListening()
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onDismiss()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FinanceTeal.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = FinanceTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Voice Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isListening) "Listening..." else if (uiState.isProcessing) "Analyzing with Gemini..." else "Ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    // Mute/Unmute audio button
                    IconButton(onClick = { viewModel.toggleMute() }) {
                        Icon(
                            imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                            tint = if (uiState.isMuted) MaterialTheme.colorScheme.error else FinanceTeal
                        )
                    }

                    // Close button
                    IconButton(onClick = {
                        viewModel.onDismiss()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Visualizer
            VoiceVisualizer(
                isListening = uiState.isListening,
                isProcessing = uiState.isProcessing,
                isSpeaking = uiState.isSpeaking,
                hasError = uiState.errorMessage != null,
                rmsDb = uiState.rmsDb,
                onClick = {
                    if (uiState.isListening) {
                        viewModel.stopListening()
                    } else {
                        viewModel.startListening()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // State Status Label
            val statusText = when {
                uiState.errorMessage != null -> uiState.errorMessage ?: "An error occurred"
                uiState.isProcessing -> "Analyzing financial intent..."
                uiState.isSpeaking -> "Speaking response..."
                uiState.isListening -> "Listening... Speak your command"
                else -> "Tap orb to speak"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    uiState.errorMessage != null -> MaterialTheme.colorScheme.error
                    uiState.isListening -> FinanceTeal
                    uiState.isProcessing -> Color(0xFF7C4DFF)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (uiState.isListening || uiState.isProcessing) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Transcript Display
            AnimatedVisibility(
                visible = uiState.liveTranscript.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = FinanceTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "\"${uiState.liveTranscript}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Undo Notice Card
            AnimatedVisibility(
                visible = uiState.undoStatus != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarmAccent.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            tint = WarmAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.undoStatus ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Action Confirmation Card (when transaction is logged or advice provided)
            val response = uiState.assistantResponse
            if (response != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (response.entity != null) FinanceMint else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (response.entity != null) Icons.Default.CheckCircle else Icons.Default.ChatBubble,
                                    contentDescription = null,
                                    tint = if (response.entity != null) FinanceTeal else Color(0xFF7C4DFF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (response.entity != null) "Transaction Executed" else "Advisor Insight",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (response.entity != null) FinanceTeal else Color(0xFF7C4DFF)
                                )
                            }

                            // Undo button
                            if (uiState.canUndo) {
                                Button(
                                    onClick = { viewModel.undoLastAction() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WarmAccent,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Undo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = response.displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Render transaction entity chips if present
                        val entity = response.entity
                        if (entity != null && entity.amount != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            TransactionEntityChips(entity = entity)
                        }
                    }
                }
            }

            // Quick Suggestion Chips (Horizontal Scroll)
            val suggestions = listOf(
                "How much did I spend this month?",
                "Spent ₹120 for coffee",
                "Food spending",
                "Check budget status",
                "What is my balance?",
                "Paid ₹850 for groceries via UPI",
                "Received ₹20,000 freelance"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            viewModel.processCommand(suggestion)
                        },
                        label = {
                            Text(suggestion, fontSize = 12.sp)
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Silent Text Input Bar (for quiet environments)
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Or type command here...", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.processCommand(textInput)
                            textInput = ""
                            keyboardController?.hide()
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.processCommand(textInput)
                                textInput = ""
                                keyboardController?.hide()
                            }
                        },
                        enabled = textInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = if (textInput.isNotBlank()) FinanceTeal else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TransactionEntityChips(entity: VoiceActionEntity) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        // Amount chip
        val isExpense = entity.type != "income"
        val sign = if (isExpense) "-" else "+"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isExpense) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
        ) {
            Text(
                text = "$sign₹${entity.amount}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isExpense) Color(0xFFC62828) else Color(0xFF2E7D32),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Category chip
        entity.category?.let { cat ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = FinanceTeal.copy(alpha = 0.12f)
            ) {
                Text(
                    text = cat,
                    fontSize = 12.sp,
                    color = FinanceTeal,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Merchant chip
        entity.merchant?.let { merch ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = merch,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Account chip
        entity.account?.let { acct ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = acct,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
