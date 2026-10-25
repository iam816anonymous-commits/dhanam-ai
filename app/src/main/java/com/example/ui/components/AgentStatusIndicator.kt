package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visual metadata for an Agent State
 */
data class AgentStateStyle(
    val titleEn: String,
    val titleTe: String,
    val primaryColor: Color,
    val accentColor: Color,
    val glowColor: Color,
    val iconEmoji: String,
    val pulseDurationMs: Int
)

fun getAgentStateStyle(state: JarvisCoreState): AgentStateStyle {
    return when (state) {
        JarvisCoreState.LISTENING -> AgentStateStyle(
            titleEn = "Listening...",
            titleTe = "వింటోంది...",
            primaryColor = Color(0xFF00E5FF),
            accentColor = Color(0xFF00B0FF),
            glowColor = Color(0xFF80D8FF),
            iconEmoji = "🎙️",
            pulseDurationMs = 700
        )
        JarvisCoreState.THINKING -> AgentStateStyle(
            titleEn = "Thinking...",
            titleTe = "ఆలోచిస్తోంది...",
            primaryColor = Color(0xFF7C4DFF),
            accentColor = Color(0xFFE040FB),
            glowColor = Color(0xFFEA80FC),
            iconEmoji = "🧠",
            pulseDurationMs = 800
        )
        JarvisCoreState.PROCESSING -> AgentStateStyle(
            titleEn = "Processing...",
            titleTe = "లెక్కిస్తోంది...",
            primaryColor = Color(0xFFFF9100),
            accentColor = Color(0xFFFF3D00),
            glowColor = Color(0xFFFFD180),
            iconEmoji = "⚡",
            pulseDurationMs = 450
        )
        JarvisCoreState.NOTING -> AgentStateStyle(
            titleEn = "Noting...",
            titleTe = "నమోదవుతోంది...",
            primaryColor = Color(0xFF00E676),
            accentColor = Color(0xFF76FF03),
            glowColor = Color(0xFFB9F6CA),
            iconEmoji = "✍️",
            pulseDurationMs = 350
        )
        JarvisCoreState.SPEAKING -> AgentStateStyle(
            titleEn = "Speaking...",
            titleTe = "సమాధానం...",
            primaryColor = Color(0xFF00B0FF),
            accentColor = Color(0xFF00E5FF),
            glowColor = Color(0xFFE0F7FA),
            iconEmoji = "🔊",
            pulseDurationMs = 600
        )
        JarvisCoreState.IDLE -> AgentStateStyle(
            titleEn = "Jarvis Ready",
            titleTe = "జార్విస్ సిద్ధం",
            primaryColor = Color(0xFF00BFA5),
            accentColor = Color(0xFF00E5FF),
            glowColor = Color(0xFF1DE9B6),
            iconEmoji = "✨",
            pulseDurationMs = 1800
        )
    }
}

/**
 * AgentStatusPill:
 * A sleek, high-visibility status pill designed for top bars or sticky headers.
 * Shows an animated pulsing orb, real-time status label, and live equalizer bars.
 */
@Composable
fun AgentStatusPill(
    state: JarvisCoreState,
    rmsDb: Float = 0f,
    isTelugu: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val style = getAgentStateStyle(state)
    val animatedPrimaryColor by animateColorAsState(
        targetValue = style.primaryColor,
        animationSpec = tween(400),
        label = "pill_color"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = animatedPrimaryColor.copy(alpha = if (state == JarvisCoreState.IDLE) 0.12f else 0.22f),
        border = BorderStroke(
            width = if (state == JarvisCoreState.IDLE) 1.dp else 1.5.dp,
            color = animatedPrimaryColor.copy(alpha = if (state == JarvisCoreState.IDLE) 0.4f else 0.85f)
        ),
        modifier = modifier
            .testTag("agent_status_pill")
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pulse Radar Orb
            AgentPulseOrb(
                state = state,
                rmsDb = rmsDb,
                size = 14.dp
            )

            // Real-time localized status label
            Text(
                text = if (isTelugu) style.titleTe else style.titleEn,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = animatedPrimaryColor
            )

            // Equalizer Soundwave Bars (Active when listening, thinking, processing, or speaking)
            if (state != JarvisCoreState.IDLE) {
                AgentEqualizerBars(
                    color = animatedPrimaryColor,
                    isActive = true,
                    rmsDb = rmsDb
                )
            }
        }
    }
}

/**
 * AgentPulseOrb:
 * Pure Compose Canvas multi-layered radar pulse effect.
 * Features 2 concentric expanding wave rings with easing and alpha fade out,
 * plus a central radial-gradient breathing sphere.
 */
@Composable
fun AgentPulseOrb(
    state: JarvisCoreState,
    rmsDb: Float = 0f,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val style = getAgentStateStyle(state)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_orb")

    // Expanding outer wave ring 1
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(style.pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_scale_1"
    )
    val waveAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(style.pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha_1"
    )

    // Expanding inner wave ring 2 with phase delay
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(style.pulseDurationMs, delayMillis = style.pulseDurationMs / 3, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_scale_2"
    )
    val waveAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(style.pulseDurationMs, delayMillis = style.pulseDurationMs / 3, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha_2"
    )

    // Core breathing scale
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(style.pulseDurationMs / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Rotation angle for thinking satellites or processing arcs
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisCoreState.PROCESSING) 1200 else 2200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rotation"
    )

    val audioBoost = if (state == JarvisCoreState.LISTENING) {
        (rmsDb.coerceIn(0f, 12f) / 12f) * 0.4f
    } else 0f

    val effectiveCoreScale = (breathingPulse + audioBoost).coerceAtMost(1.8f)

    Box(
        modifier = modifier
            .size(size)
            .testTag("agent_pulse_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f

            // 1. Expanding Pulse Wave Rings (Active when state != IDLE)
            if (state != JarvisCoreState.IDLE) {
                drawCircle(
                    color = style.primaryColor.copy(alpha = waveAlpha1 * 0.5f),
                    radius = baseRadius * waveScale1,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawCircle(
                    color = style.accentColor.copy(alpha = waveAlpha2 * 0.4f),
                    radius = baseRadius * waveScale2,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 2. Synaptic Orbiting Satellites during THINKING state
            if (state == JarvisCoreState.THINKING) {
                rotate(orbitalRotation, center) {
                    val satRadius = baseRadius * 1.35f
                    for (i in 0 until 3) {
                        val angle = (i * 120f) * (PI / 180f).toFloat()
                        val satOffset = Offset(
                            center.x + satRadius * cos(angle),
                            center.y + satRadius * sin(angle)
                        )
                        drawCircle(
                            color = style.accentColor,
                            radius = 1.5.dp.toPx(),
                            center = satOffset
                        )
                    }
                }
            }

            // 3. Fast Energy Arcs during PROCESSING state
            if (state == JarvisCoreState.PROCESSING) {
                rotate(orbitalRotation, center) {
                    drawArc(
                        color = style.accentColor,
                        startAngle = 0f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 1.3f, center.y - baseRadius * 1.3f),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 2.6f, baseRadius * 2.6f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 4. Glowing Ambient Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        style.glowColor.copy(alpha = 0.8f),
                        style.primaryColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.4f * effectiveCoreScale
                ),
                radius = baseRadius * 1.4f * effectiveCoreScale,
                center = center
            )

            // 5. Central Solid Photon Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        style.glowColor,
                        style.primaryColor
                    ),
                    center = center,
                    radius = baseRadius * 0.75f * effectiveCoreScale
                ),
                radius = baseRadius * 0.75f * effectiveCoreScale,
                center = center
            )
        }
    }
}

/**
 * AgentEqualizerBars:
 * Real-time bouncing soundwave bars giving tactile feedback during voice or cognitive activity.
 */
@Composable
fun AgentEqualizerBars(
    color: Color,
    isActive: Boolean,
    rmsDb: Float = 0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_3"
    )

    val audioFactor = (rmsDb / 10f).coerceIn(0f, 0.5f)

    Row(
        modifier = modifier
            .height(12.dp)
            .testTag("agent_equalizer_bars"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val heights = if (isActive) listOf(h1 + audioFactor, h2 + audioFactor, h3 + audioFactor)
        else listOf(0.3f, 0.3f, 0.3f)

        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((h.coerceIn(0.2f, 1f) * 12).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

/**
 * AgentStatusHeroCard:
 * Full showcase banner featuring the Arc Reactor living core, status badges,
 * dynamic waves, and localized prompt/activity updates.
 */
@Composable
fun AgentStatusHeroCard(
    state: JarvisCoreState,
    rmsDb: Float = 0f,
    isTelugu: Boolean = false,
    subtitleText: String? = null,
    onCardClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val style = getAgentStateStyle(state)
    val animatedPrimaryColor by animateColorAsState(
        targetValue = style.primaryColor,
        animationSpec = tween(400),
        label = "hero_color"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (state == JarvisCoreState.IDLE) 1.dp else 1.5.dp,
            color = if (state == JarvisCoreState.IDLE) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else animatedPrimaryColor.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_hero_card")
            .then(
                if (onCardClick != null) {
                    Modifier.clickable(onClick = onCardClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Arc Reactor Living Core
            JarvisLivingCore(
                state = state,
                rmsDb = rmsDb,
                coreSize = 64.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = animatedPrimaryColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${style.iconEmoji} ${if (isTelugu) style.titleTe else style.titleEn}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = animatedPrimaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (state != JarvisCoreState.IDLE) {
                            AgentEqualizerBars(
                                color = animatedPrimaryColor,
                                isActive = true,
                                rmsDb = rmsDb
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitleText ?: when (state) {
                        JarvisCoreState.LISTENING -> if (isTelugu) "మాట్లాడండి..." else "Speak now..."
                        JarvisCoreState.THINKING -> if (isTelugu) "ఆర్థిక విశ్లేషణ చేస్తోంది..." else "Synthesizing financial intent..."
                        JarvisCoreState.PROCESSING -> if (isTelugu) "ఖాతా లెక్కలను అప్‌డేట్ చేస్తోంది..." else "Computing ledger & budget limits..."
                        JarvisCoreState.NOTING -> if (isTelugu) "డేటాబేస్‌లో రాసింది!" else "Saved transactions to ledger!"
                        JarvisCoreState.SPEAKING -> if (isTelugu) "సమాధానం వినిపిస్తోంది..." else "Speaking confirmation..."
                        JarvisCoreState.IDLE -> if (isTelugu) "నొక్కండి (ఖర్చులు, అప్పులు మాట్లాడవచ్చు)" else "Tap to speak (multi-item expenses, khata loans)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
