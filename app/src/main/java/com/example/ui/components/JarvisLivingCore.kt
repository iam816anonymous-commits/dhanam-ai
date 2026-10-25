package com.example.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class JarvisCoreState {
    IDLE,
    LISTENING,
    THINKING,
    PROCESSING,
    NOTING,
    SPEAKING
}

/**
 * JarvisLivingCore:
 * An interactive, holographic Arc Reactor AI entity for Dhanam AI.
 * Renders animated rotating concentric rings, energy arcs, pulsing photon core,
 * and reactive soundwave rings responding directly to voice input and thinking states.
 */
@Composable
fun JarvisLivingCore(
    state: JarvisCoreState,
    rmsDb: Float = 0f,
    coreSize: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAppResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isAppResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activityManager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
    val isLowRamDevice = remember(activityManager) { activityManager?.isLowRamDevice == true }

    var isPowerSaveMode by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isPowerSaveMode == true)
    }

    DisposableEffect(context) {
        val filter = android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                val pm = c?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                isPowerSaveMode = pm?.isPowerSaveMode == true
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val shouldThrottle = isPowerSaveMode || isLowRamDevice

    val infiniteTransition = rememberInfiniteTransition(label = "JarvisInfinite")

    // Slow continuous rotation for outer holographic ring
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisCoreState.PROCESSING -> 2400
                    JarvisCoreState.THINKING -> 3600
                    else -> 10000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    // Counter rotation for middle segmented ring
    val middleRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisCoreState.PROCESSING -> 1800
                    JarvisCoreState.THINKING -> 2800
                    else -> 7000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "middleRotation"
    )

    // Core breathing scale
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisCoreState.PROCESSING -> 450
                    JarvisCoreState.THINKING -> 500
                    JarvisCoreState.NOTING -> 350
                    JarvisCoreState.SPEAKING -> 600
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingPulse"
    )

    // Radiant ripple alpha for listening / noting / thinking
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    val rippleRadiusFactor by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleRadiusFactor"
    )

    // Dynamic color palettes matching Jarvis living state
    val (primaryColor, accentColor, glowColor) = when (state) {
        JarvisCoreState.LISTENING -> Triple(
            Color(0xFF00E5FF), // Electric Cyan
            Color(0xFF00B0FF), // Deep Sky Blue
            Color(0xFF80D8FF)
        )
        JarvisCoreState.THINKING -> Triple(
            Color(0xFF7C4DFF), // Electric Violet / Synaptic Purple
            Color(0xFFE040FB), // Neon Magenta
            Color(0xFFEA80FC)
        )
        JarvisCoreState.PROCESSING -> Triple(
            Color(0xFFFF9100), // Energetic Amber
            Color(0xFFFF3D00), // Radiant Orange
            Color(0xFFFFD180)
        )
        JarvisCoreState.NOTING -> Triple(
            Color(0xFF00E676), // Vibrant Emerald
            Color(0xFF76FF03), // Bright Lime
            Color(0xFFB9F6CA)
        )
        JarvisCoreState.SPEAKING -> Triple(
            Color(0xFF00B0FF), // Azure Blue
            Color(0xFF00E5FF), // Cyan
            Color(0xFFE0F7FA)
        )
        JarvisCoreState.IDLE -> Triple(
            Color(0xFF00BFA5), // Teal Core
            Color(0xFF00E5FF), // Cyan Glow
            Color(0xFF1DE9B6)
        )
    }

    // Audio reactivity multiplier
    val reactiveAudioBoost = if (state == JarvisCoreState.LISTENING && !shouldThrottle && isAppResumed) {
        (rmsDb.coerceIn(0f, 15f) / 15f) * 0.25f
    } else 0f

    val effectivePulse = if (!isAppResumed || shouldThrottle) 1.0f else (breathingPulse + reactiveAudioBoost)

    val effectiveOuterRotation = when {
        !isAppResumed -> 0f
        shouldThrottle -> 45f
        else -> outerRotation
    }

    val effectiveMiddleRotation = when {
        !isAppResumed -> 0f
        shouldThrottle -> 30f
        else -> middleRotation
    }

    Box(
        modifier = modifier.size(coreSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(coreSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // 1. Expanding reactive soundwave ripples when listening, noting or thinking
            if (isAppResumed && !shouldThrottle && (state == JarvisCoreState.LISTENING || state == JarvisCoreState.NOTING || state == JarvisCoreState.THINKING)) {
                drawCircle(
                    color = primaryColor.copy(alpha = rippleAlpha * 0.45f),
                    radius = maxRadius * rippleRadiusFactor,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = accentColor.copy(alpha = (rippleAlpha * 0.3f).coerceAtLeast(0f)),
                    radius = maxRadius * (rippleRadiusFactor * 0.8f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2. Outer Holographic Ring with Arc Segments (Pauses or slows when backgrounded/power save)
            rotate(effectiveOuterRotation, center) {
                // Background subtle track
                drawCircle(
                    color = primaryColor.copy(alpha = 0.2f),
                    radius = maxRadius * 0.90f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 3 segmented energy arcs
                val arcSweep = when (state) {
                    JarvisCoreState.PROCESSING -> 85f
                    JarvisCoreState.THINKING -> 75f
                    else -> 60f
                }
                for (angle in listOf(0f, 120f, 240f)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(primaryColor.copy(alpha = 0.2f), primaryColor, glowColor),
                            center = center
                        ),
                        startAngle = angle,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - maxRadius * 0.90f, center.y - maxRadius * 0.90f),
                        size = androidx.compose.ui.geometry.Size(maxRadius * 1.80f, maxRadius * 1.80f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Small orbital tick dots (Throttled on Helio P23 during battery save)
                val tickCount = if (isPowerSaveMode) 4 else 12
                val tickStep = if (isPowerSaveMode) 90f else 30f
                for (i in 0 until tickCount) {
                    val tickAngle = (i * tickStep) * (PI / 180f).toFloat()
                    val tickDist = maxRadius * 0.90f
                    val dotCenter = Offset(
                        center.x + tickDist * cos(tickAngle),
                        center.y + tickDist * sin(tickAngle)
                    )
                    drawCircle(
                        color = if (i % 3 == 0) primaryColor else primaryColor.copy(alpha = 0.35f),
                        radius = if (i % 3 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        center = dotCenter
                    )
                }
            }

            // 3. Middle Counter-Rotating Segmented Reactor Ring
            rotate(effectiveMiddleRotation, center) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = maxRadius * 0.68f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // 4 energy blades
                for (angle in listOf(30f, 120f, 210f, 300f)) {
                    drawArc(
                        color = accentColor,
                        startAngle = angle,
                        sweepAngle = 45f,
                        useCenter = false,
                        topLeft = Offset(center.x - maxRadius * 0.68f, center.y - maxRadius * 0.68f),
                        size = androidx.compose.ui.geometry.Size(maxRadius * 1.36f, maxRadius * 1.36f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 4. Central Arc Reactor Glowing Core (Breathing and Pulsing)
            val coreRadius = maxRadius * 0.44f * effectivePulse

            // Soft radial ambient aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.4f
                ),
                radius = coreRadius * 1.4f,
                center = center
            )

            // Inner solid glowing sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        glowColor,
                        primaryColor
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Inner focus iris ring
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = coreRadius * 0.45f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
