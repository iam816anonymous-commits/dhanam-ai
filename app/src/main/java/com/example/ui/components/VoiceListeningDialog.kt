package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldPrimaryLight

@Composable
fun VoiceListeningDialog(
    isTelugu: Boolean,
    isListening: Boolean,
    partialTranscript: String,
    rmsDb: Float,
    errorMessage: String?,
    isTeluguTtsAvailable: Boolean,
    isEmulator: Boolean = false,
    isOnline: Boolean = true,
    currentLanguage: String = if (isTelugu) "te" else "en",
    onSelectLanguage: ((String) -> Unit)? = null,
    isSystemVoiceAvailable: Boolean = true,
    onDismiss: () -> Unit,
    onRetryListening: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onDoneListening: (() -> Unit)? = null,
    onLaunchSystemVoice: (() -> Unit)? = null,
    onOpenVoiceSettings: (() -> Unit)? = null,
    onSimulateVoice: ((String) -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_pulse"
    )

    // Dynamic mic scale based on real voice sound level (rmsDb: 0f to 10f)
    val reactiveScale = if (isListening) {
        val voiceBoost = (rmsDb / 10f).coerceIn(0f, 0.5f)
        ambientPulse + voiceBoost
    } else {
        1f
    }

    val activeLang = when {
        currentLanguage.startsWith("hi", ignoreCase = true) -> "hi"
        currentLanguage.startsWith("en", ignoreCase = true) -> "en"
        else -> if (isTelugu) "te" else "en"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("voice_listening_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar with Language Selector and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeLang == "te") "వాయిస్ ఇన్‌పుట్" else if (activeLang == "hi") "वॉइस इनपुट" else "Voice Input",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isEmulator) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "🖥️ Virtual Mic",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("voice_dialog_close")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multilingual Selector Chips: Telugu, English, Hindi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val languages = listOf(
                        Triple("te", "తెలుగు", ""),
                        Triple("en", "English", ""),
                        Triple("hi", "हिन्दी", "")
                    )

                    languages.forEach { (code, name, _) ->
                        val isSelected = activeLang == code
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (onSelectLanguage != null) {
                                        onSelectLanguage(code)
                                    } else if ((code == "te") != isTelugu) {
                                        onSwitchLanguage()
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Display live RecognitionEngineMode status (Online Cloud vs Offline On-Device)
                if (!isOnline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (activeLang) {
                                    "te" -> "ఆఫ్‌లైన్ వాయిస్ మోడ్ యాక్టివ్ (ఆన్-డివైస్ స్పీచ్)"
                                    "hi" -> "ऑफलाइन वॉइस एक्टिव (ऑन-डिवाइस स्पीच)"
                                    else -> "Offline Voice Active (On-device speech)"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (activeLang) {
                                    "te" -> "గూగుల్ క్లౌడ్ స్పీచ్ యాక్టివ్ (అధిక ఖచ్చితత్వం)"
                                    "hi" -> "Google क्लाउड स्पीच एक्टिव (उच्च सटीकता)"
                                    else -> "Google Cloud Speech Active (High Accuracy)"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Pulsing Mic Visualizer reacting to real audio
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Reactive Voice Ripple
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .scale(reactiveScale)
                                .clip(CircleShape)
                                .background(EmeraldPrimaryLight.copy(alpha = 0.22f))
                        )
                    }

                    // Inner Core Mic Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                if (errorMessage != null && isOnline) MaterialTheme.colorScheme.error
                                else EmeraldPrimaryLight
                            )
                            .clickable {
                                if ((errorMessage != null && isOnline) || !isListening) {
                                    onRetryListening()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (errorMessage != null && isOnline) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Listening" else "Mic",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Quick Voice Test Chips and Virtual Mic Simulation tray visible right below the microphone
                if (onSimulateVoice != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (!isOnline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (activeLang == "te") "✨ త్వరిత వాయిస్ ఎంట్రీ (ఆఫ్‌లైన్ ఇన్‌స్టంట్):"
                                    else if (activeLang == "hi") "✨ त्वरित वॉइस एंट्री (ऑफ़लाइन तुरंत):"
                                    else "✨ Quick Voice Simulation (Instant Offline):",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!isOnline) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "ఆఫ్‌లైన్ ✓",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val testChips = when (activeLang) {
                                    "te" -> listOf(
                                        "🍵 టీ 20 మరియు సమోసా 30" to "టీ 20 మరియు సమోసా 30",
                                        "⛽ పెట్రోల్ 350 రూపాయలు" to "పెట్రోల్ 350 రూపాయలు",
                                        "🛒 కూరగాయలు 250" to "కూరగాయలు 250 రూపాయలు",
                                        "🤝 రమేష్ కి 2000 అప్పు ఇచ్చాను" to "రమేష్ కి 2000 అప్పు ఇచ్చాను",
                                        "💵 రమేష్ 1000 తిరిగి ఇచ్చాడు" to "రమేష్ 1000 తిరిగి ఇచ్చాడు",
                                        "📅 నెల బడ్జెట్ 45000 గా పెట్టు" to "నెల బడ్జెట్ 45000 గా పెట్టు",
                                        "📊 ఈ రోజు ఎంత ఖర్చు చేశాను?" to "ఈ రోజు ఎంత ఖర్చు చేశాను?",
                                        "🌾 కిరాణా సామాను 1200" to "కిరాణా సామాను 1200 రూపాయలు"
                                    )
                                    "hi" -> listOf(
                                        "🍵 चाय 20 और समोसा 30" to "चाय 20 और समोसा 30",
                                        "⛽ पेट्रोल 350 रुपये" to "पेट्रोल 350 रुपये",
                                        "🛒 राशन 250 रुपये" to "राशन 250 रुपये",
                                        "🤝 रमेश को 2000 उधार दिए" to "रमेश को 2000 उधार दिए",
                                        "💵 रमेश ने 1000 वापस किए" to "रमेश ने 1000 वापस किए",
                                        "📅 महीने का बजट 45000 सेट करो" to "महीने का बजट 45000 सेट करो",
                                        "📊 आज का खर्च कितना है?" to "आज का खर्च कितना है?"
                                    )
                                    else -> listOf(
                                        "🍵 Tea 20 and Samosa 30" to "Tea 20 and samosa 30",
                                        "⛽ Petrol 350 rupees" to "Petrol 350 rupees",
                                        "🛒 Groceries 250" to "Groceries 250",
                                        "🤝 Lent 2000 to Ramesh" to "Lent 2000 to Ramesh",
                                        "💵 Ramesh returned 1000" to "Ramesh returned 1000",
                                        "📅 Set monthly budget 45000" to "Set monthly budget to 45000",
                                        "📊 How much spent today?" to "How much did I spend today?"
                                    )
                                }

                                items(testChips) { (label, phrase) ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        ),
                                        modifier = Modifier.clickable {
                                            onSimulateVoice(phrase)
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status text
                Text(
                    text = when {
                        !isOnline -> if (activeLang == "te") "⚡ ఆఫ్‌లైన్ వాయిస్ మోడ్: మాట్లాడండి లేదా చిప్స్ నొక్కండి" else if (activeLang == "hi") "⚡ ऑफ़लाइन मोड: बोलें या चिप्स चुनें" else "⚡ Offline Voice Mode: Speak or tap chips"
                        errorMessage != null -> if (activeLang == "te") "వాయిస్ సమస్య" else if (activeLang == "hi") "वॉइस समस्या" else "Voice Input Issue"
                        isListening -> if (activeLang == "te") "వింటున్నాను... మాట్లాడండి" else if (activeLang == "hi") "सुन रहा हूँ... बोलिए" else "Listening... speak now"
                        else -> if (activeLang == "te") "సిద్ధంగా ఉన్నాను" else if (activeLang == "hi") "तैयार हूँ" else "Ready"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = if (errorMessage != null && isOnline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Error message banner if any (suppressed offline to prevent harsh error toasts/banners)
                AnimatedVisibility(visible = errorMessage != null && isOnline) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onRetryListening,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (activeLang == "te") "మళ్లీ ప్రయత్నించండి" else if (activeLang == "hi") "पुनः प्रयास" else "Retry",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            if (isSystemVoiceAvailable && onLaunchSystemVoice != null) {
                                FilledTonalButton(
                                    onClick = onLaunchSystemVoice,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (activeLang == "te") "గూగుల్ వాయిస్" else if (activeLang == "hi") "Google वॉइस" else "System Voice",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-time live transcript
                AnimatedVisibility(visible = partialTranscript.isNotBlank() && errorMessage == null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "“$partialTranscript”",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (errorMessage == null && partialTranscript.isBlank()) {
                    Text(
                        text = if (activeLang == "te")
                            "మైక్రోఫోన్ ద్వారా మీ ఖర్చు లేదా అప్పు వివరాలను మాట్లాడండి"
                        else if (activeLang == "hi")
                            "माइक के माध्यम से अपने खर्च या उधारी का विवरण बोलें"
                        else
                            "Speak your expense or loan details into the microphone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Informative note if Telugu TTS is not installed on device
                if (isTelugu && !isTeluguTtsAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ℹ️ వాయిస్ ఫీడ్‌బ్యాక్ ఇంగ్లీష్‌లో వినిపిస్తుంది (ఫోన్‌లో తెలుగు వాయిస్ లేదు)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }

                if (isListening && onDoneListening != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onDoneListening,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_done_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTelugu) "పూర్తయింది (సమర్పించు)" else "Done Speaking (Submit)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1-Tap Language Switch Button
                OutlinedButton(
                    onClick = onSwitchLanguage,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("switch_voice_lang_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTelugu) "Switch to English Mode" else "తెలుగులోకి మార్చండి",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
