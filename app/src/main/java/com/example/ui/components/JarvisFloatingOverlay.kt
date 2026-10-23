package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt

/**
 * JarvisFloatingOverlay:
 * Floating, draggable living entity overlay that hovers across all screens of Dhanam AI.
 * Allows operating the entire app via voice (noting expenses, lending, navigation) without leaving the current screen.
 */
@Composable
fun JarvisFloatingOverlay(
    viewModel: MainViewModel,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening by viewModel.isListening.collectAsState()
    val isProcessing by viewModel.isProcessingAgent.collectAsState()
    val isNoting by viewModel.isNoting.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val lastNote by viewModel.lastJarvisNote.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val isTelugu = preferredLang == "te"

    var isExpanded by remember { mutableStateOf(false) }

    // Draggable position offsets
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val coreState = when {
        isNoting -> JarvisCoreState.NOTING
        isProcessing -> JarvisCoreState.PROCESSING
        isListening -> JarvisCoreState.LISTENING
        isSpeaking -> JarvisCoreState.SPEAKING
        else -> JarvisCoreState.IDLE
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Draggable floating capsule located near bottom-end by default
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(end = 16.dp, bottom = 12.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expanded Jarvis Command HUD
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .width(260.dp)
                            .shadow(12.dp, RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    JarvisLivingCore(
                                        state = coreState,
                                        rmsDb = rmsDb,
                                        coreSize = 32.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Jarvis AI Core",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { isExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close HUD",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isTelugu) "ఆప్ అంతటా వాయిస్ ఆపరేషన్:"
                                else "Operate anywhere inside app:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick screen navigation shortcuts inside Jarvis HUD
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickNavPill(
                                    label = if (isTelugu) "ఖాతా" else "Khata",
                                    icon = Icons.Default.Handshake,
                                    onClick = {
                                        viewModel.setCurrentScreen(Screen.KHATA)
                                        isExpanded = false
                                    }
                                )
                                QuickNavPill(
                                    label = if (isTelugu) "ఖర్చులు" else "Expenses",
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    onClick = {
                                        viewModel.setCurrentScreen(Screen.EXPENSES)
                                        isExpanded = false
                                    }
                                )
                                QuickNavPill(
                                    label = if (isTelugu) "రిపోర్ట్" else "Report",
                                    icon = Icons.Default.Insights,
                                    onClick = {
                                        viewModel.setCurrentScreen(Screen.INSIGHTS)
                                        isExpanded = false
                                    }
                                )
                                QuickNavPill(
                                    label = if (isTelugu) "సెట్టింగ్స్" else "Config",
                                    icon = Icons.Default.Settings,
                                    onClick = {
                                        viewModel.setCurrentScreen(Screen.SETTINGS)
                                        isExpanded = false
                                    }
                                )
                            }

                            if (partialTranscript.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "\"$partialTranscript\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(6.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating Pill (Core + Status / Action)
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .shadow(10.dp, RoundedCornerShape(32.dp))
                        .testTag("jarvis_floating_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Interactive living arc reactor orb
                        Box(
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            JarvisLivingCore(
                                state = coreState,
                                rmsDb = rmsDb,
                                coreSize = 44.dp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Dynamic status text (Noting, Listening, Speaking, or Ready)
                        Column(
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = when {
                                    isNoting -> if (isTelugu) "జార్విస్ రాస్తోంది..." else "Jarvis Noting..."
                                    isProcessing -> if (isTelugu) "లెక్కిస్తోంది..." else "Processing..."
                                    isListening -> if (isTelugu) "వినబడుతోంది..." else "Listening..."
                                    isSpeaking -> if (isTelugu) "సమాధానం..." else "Speaking..."
                                    else -> "Jarvis AI"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isNoting -> Color(0xFF00E676)
                                    isListening -> Color(0xFF00B0FF)
                                    isProcessing -> Color(0xFFFF9100)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )

                            Text(
                                text = when {
                                    isNoting -> lastNote ?: "Writing records..."
                                    isListening && partialTranscript.isNotBlank() -> partialTranscript.take(18) + "..."
                                    isListening -> if (isTelugu) "మాట్లాడండి..." else "Speak multi-items..."
                                    else -> if (isTelugu) "నొక్కండి / మాట్లాడండి" else "Tap or speak"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Quick 1-tap Mic button
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) Color(0xFFFF5252)
                                    else MaterialTheme.colorScheme.primary
                                )
                                .testTag("jarvis_quick_mic")
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Jarvis Voice",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNavPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
