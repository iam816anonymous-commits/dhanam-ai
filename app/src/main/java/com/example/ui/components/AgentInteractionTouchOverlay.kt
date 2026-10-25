package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class AgentTouchInteraction(
    val id: Long = System.currentTimeMillis(),
    val actionTitleTe: String,
    val actionTitleEn: String,
    val actionSubtitleTe: String,
    val actionSubtitleEn: String,
    val targetXFraction: Float = 0.5f,
    val targetYFraction: Float = 0.5f,
    val iconEmoji: String = "👆"
)

/**
 * AgentInteractionTouchOverlay:
 * Renders high-fidelity visible feedback showing the AI Agent (Jarvis) physically
 * touching, navigating, and manipulating elements on the user's screen in real time.
 * Includes:
 * - Animated capacitive touch pointer with glide physics
 * - Expanding dual-ring touch ripple waves (authentic finger tap simulation)
 * - Glassmorphic HUD badge explaining the autonomous action
 */
@Composable
fun AgentInteractionTouchOverlay(
    interaction: AgentTouchInteraction?,
    isTelugu: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = interaction != null,
        enter = fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.8f),
        exit = fadeOut(tween(300)) + scaleOut(tween(250), targetScale = 0.9f),
        modifier = modifier.fillMaxSize()
    ) {
        if (interaction == null) return@AnimatedVisibility

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()

            // Calculate screen coordinates from target fractions
            val targetXPx = screenWidthPx * interaction.targetXFraction.coerceIn(0.12f, 0.88f)
            val targetYPx = screenHeightPx * interaction.targetYFraction.coerceIn(0.15f, 0.85f)

            // Smooth pointer position glide animation
            val animX = remember(interaction.id) { Animatable(targetXPx) }
            val animY = remember(interaction.id) { Animatable(targetYPx + 30f) }

            LaunchedEffect(interaction.id) {
                animY.animateTo(targetYPx, tween(320, easing = FastOutSlowInEasing))
            }

            // Continuous Touch Ripple Wave Animations
            val infiniteTransition = rememberInfiniteTransition(label = "agent_tap_ripple")
            val rippleScale1 by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 2.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple_scale_1"
            )
            val rippleAlpha1 by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple_alpha_1"
            )

            val rippleScale2 by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = 350, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple_scale_2"
            )
            val rippleAlpha2 by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = 350, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple_alpha_2"
            )

            // Finger/pointer bounce animation
            val pointerBounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pointer_bounce"
            )

            val emeraldCyan = Brush.radialGradient(
                colors = listOf(Color(0xFF00E676), Color(0xFF00B0FF), Color.Transparent)
            )

            // 1. Dual-Ring Expanding Touch Ripple at the Target Contact Point
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (animX.value - 36).roundToInt(),
                            (animY.value - 36).roundToInt()
                        )
                    }
                    .size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer expanding ripple
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(rippleScale1)
                        .alpha(rippleAlpha1)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF00E676), CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                )

                // Inner expanding ripple
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .scale(rippleScale2)
                        .alpha(rippleAlpha2)
                        .clip(CircleShape)
                        .border(2.5.dp, Color(0xFF00E5FF), CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                )

                // High-Intensity Touch Contact Core
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676))
                        .shadow(10.dp, CircleShape)
                )
            }

            // 2. Animated Agent Finger Pointer & Holographic Action HUD Card
            val isNearBottom = interaction.targetYFraction > 0.65f
            val hudOffsetY = if (isNearBottom) (animY.value - 110f) else (animY.value + 40f)

            // Dynamic Hand / Touch Pointer Symbol
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (animX.value - 16).roundToInt(),
                            (animY.value - 36 + pointerBounce).roundToInt()
                        )
                    }
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = interaction.iconEmoji,
                            fontSize = 20.sp
                        )
                    }
                }
            }

            // Floating Holographic Action HUD Card
            Box(
                modifier = Modifier
                    .offset {
                        val cardHalfWidth = 140
                        val clampedX = (animX.value - cardHalfWidth)
                            .coerceIn(16f, (screenWidthPx - 296).coerceAtLeast(16f))
                        IntOffset(clampedX.roundToInt(), hudOffsetY.roundToInt())
                    }
                    .widthIn(max = 280.dp)
                    .testTag("agent_touch_hud")
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676).copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF00E676).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isTelugu) "జార్విస్ యాక్షన్" else "Jarvis Acting",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF00E676),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isTelugu) interaction.actionTitleTe else interaction.actionTitleEn,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (interaction.actionSubtitleEn.isNotBlank()) {
                                Text(
                                    text = if (isTelugu) interaction.actionSubtitleTe else interaction.actionSubtitleEn,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
