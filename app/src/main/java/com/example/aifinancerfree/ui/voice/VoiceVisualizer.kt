package com.example.aifinancerfree.ui.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.aifinancerfree.ui.theme.FinanceMint
import com.example.aifinancerfree.ui.theme.FinanceTeal
import com.example.aifinancerfree.ui.theme.FinanceTealLight
import com.example.aifinancerfree.ui.theme.WarmAccent

@Composable
fun VoiceVisualizer(
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    hasError: Boolean,
    rmsDb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine dynamic primary and accent colors based on state
    val targetPrimaryColor = when {
        hasError -> Color(0xFFFF5252)
        isProcessing -> Color(0xFF7C4DFF)
        isSpeaking -> Color(0xFF00C853)
        isListening -> FinanceTeal
        else -> FinanceTeal
    }

    val targetAccentColor = when {
        hasError -> WarmAccent
        isProcessing -> Color(0xFF536DFE)
        isSpeaking -> Color(0xFF69F0AE)
        isListening -> FinanceMint
        else -> FinanceTealLight
    }

    val primaryColor by animateColorAsState(
        targetValue = targetPrimaryColor,
        animationSpec = tween(durationMillis = 350),
        label = "primaryColor"
    )
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 350),
        label = "accentColor"
    )

    // Continuous breathing pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Wave ring expansion
    val ringPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPhase"
    )

    // Audio level reactivity based on rmsDb (0 to 10 dB typical range)
    val normalizedRms = (rmsDb / 10f).coerceIn(0f, 1f)
    val reactiveScale by animateFloatAsState(
        targetValue = if (isListening) 1f + (normalizedRms * 0.35f) else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reactiveScale"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Multi-layered animated acoustic ripple rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 4f

            if (isListening || isSpeaking || isProcessing) {
                val ringCount = 3
                for (i in 0 until ringCount) {
                    val progress = (ringPhase + (i.toFloat() / ringCount)) % 1f
                    val ringRadius = baseRadius + (progress * baseRadius * 1.5f * (if (isListening) (1f + normalizedRms) else 1f))
                    val ringAlpha = ((1f - progress) * 0.45f).coerceIn(0f, 1f)

                    drawCircle(
                        color = accentColor.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = (2.5f * (1f - progress)).dp.toPx())
                    )
                }
            }
        }

        // Central glowing orb
        val orbScale = (if (isListening) reactiveScale else pulseScale)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .scale(orbScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor, primaryColor),
                        radius = 120f
                    )
                )
        ) {
            val icon = when {
                hasError -> Icons.Default.MicOff
                isProcessing -> Icons.Default.HourglassTop
                isSpeaking -> Icons.AutoMirrored.Filled.VolumeUp
                isListening -> Icons.Default.GraphicEq
                else -> Icons.Default.Mic
            }

            Icon(
                imageVector = icon,
                contentDescription = "Voice Assistant Status",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
