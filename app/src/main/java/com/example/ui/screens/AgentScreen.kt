package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AgentResult
import com.example.ai.ResultType
import com.example.ui.components.JarvisCoreState
import com.example.ui.components.JarvisLivingCore
import com.example.ui.components.OverspendingBanner
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberSecondaryLight
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.viewmodel.AgentChatMessage
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.agentMessages.collectAsState()
    val isProcessing by viewModel.isProcessingAgent.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val isVoiceEnabled by viewModel.voiceFeedbackEnabled.collectAsState()
    val isTelugu = preferredLang == "te"

    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isNoting by viewModel.isNoting.collectAsState()
    val lastNote by viewModel.lastJarvisNote.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val speechError by viewModel.speechError.collectAsState()
    val isTeluguTtsAvailable by viewModel.isTeluguTtsAvailable.collectAsState()

    val coreState = when {
        isNoting -> JarvisCoreState.NOTING
        isProcessing -> JarvisCoreState.PROCESSING
        isListening -> JarvisCoreState.LISTENING
        isSpeaking -> JarvisCoreState.SPEAKING
        else -> JarvisCoreState.IDLE
    }

    var inputText by remember { mutableStateOf("") }
    var showAudioPermissionRationale by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelVoiceListening()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setShowVoiceListeningDialog(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenTextList?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.processAgentInput(spokenText)
            }
        }
    }

    fun launchVoiceRecognition() {
        val currentIsTelugu = viewModel.preferredLanguage.value == "te"

        viewModel.startVoiceListening(
            isTelugu = currentIsTelugu,
            onResult = { spokenText ->
                if (spokenText.isNotBlank()) {
                    viewModel.processAgentInput(spokenText)
                }
            },
            onError = { _ ->
                // If device is online, attempt fallback dialog.
                // If offline, do NOT launch Google online popup to avoid immediate "no connection" failure.
                if (viewModel.voiceAgentManager.isOnline()) {
                    try {
                        val fallbackIntent = viewModel.voiceAgentManager.createFallbackRecognizerIntent(currentIsTelugu)
                        speechLauncher.launch(fallbackIntent)
                    } catch (_: Exception) {}
                }
            }
        )
    }

    fun onMicClicked() {
        if (viewModel.isListening.value) {
            viewModel.submitVoiceListeningImmediately()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchVoiceRecognition()
        } else {
            val mainActivity = context as? com.example.MainActivity
            if (mainActivity != null) {
                mainActivity.requestAudioPermission { isGranted ->
                    if (isGranted) {
                        launchVoiceRecognition()
                    } else {
                        showAudioPermissionRationale = true
                    }
                }
            } else {
                showAudioPermissionRationale = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("agent_screen")
    ) {
        // Top Language & Voice Control Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisLivingCore(state = coreState, rmsDb = rmsDb, coreSize = 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTelugu) "జార్విస్ AI కోర్ (Jarvis Core)" else "Jarvis AI Living Core",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Clear chat button (frees memory on low-RAM devices)
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAgentChat() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = if (isTelugu) "చరిత్ర తుడిచివేయి" else "Clear Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // Voice response toggle
                    IconButton(
                        onClick = { viewModel.setVoiceFeedbackEnabled(!isVoiceEnabled) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isVoiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Voice Feedback",
                            tint = if (isVoiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Language toggle pills
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                    ) {
                        LanguagePill(
                            label = "English",
                            isSelected = !isTelugu,
                            onClick = { viewModel.setPreferredLanguage("en") }
                        )
                        LanguagePill(
                            label = "తెలుగు",
                            isSelected = isTelugu,
                            onClick = { viewModel.setPreferredLanguage("te") }
                        )
                    }
                }
            }
        }

        // Interactive Jarvis Living Entity Showcase Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isListening) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            ),
            border = if (isListening) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clickable { onMicClicked() }
                .testTag("jarvis_hero_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JarvisLivingCore(
                        state = coreState,
                        rmsDb = rmsDb,
                        coreSize = 64.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (coreState) {
                                    JarvisCoreState.NOTING -> if (isTelugu) "⚡ జార్విస్ ఖర్చులు నమోదు చేస్తోంది..." else "⚡ JARVIS: NOTING TRANSACTIONS..."
                                    JarvisCoreState.LISTENING -> if (isTelugu) "🎙️ వింటోంది..." else "🎙️ LISTENING..."
                                    JarvisCoreState.PROCESSING -> if (isTelugu) "🧠 లెక్కిస్తోంది & వర్గీకరిస్తోంది..." else "🧠 ANALYZING & PARSING..."
                                    JarvisCoreState.SPEAKING -> if (isTelugu) "🔊 సమాధానం వినిపిస్తోంది..." else "🔊 SPEAKING CONFIRMATION..."
                                    JarvisCoreState.IDLE -> if (isTelugu) "✨ జార్విస్ సిద్ధంగా ఉంది (నొక్కండి)" else "✨ JARVIS CORE ACTIVE (TAP TO SPEAK)"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = when (coreState) {
                                    JarvisCoreState.NOTING -> Color(0xFF00E676)
                                    JarvisCoreState.LISTENING -> Color(0xFF00B0FF)
                                    JarvisCoreState.PROCESSING -> Color(0xFFFF9100)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )

                            if (viewModel.voiceAgentManager.isEmulator()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = "🖥️ Virtual Mic",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when {
                                isNoting -> lastNote ?: "Writing items to database..."
                                isListening && partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                                isListening -> if (isTelugu) "మాట్లాడండి లేదా కింద ఉన్న టెస్ట్ చిప్స్ నొక్కండి..." else "Speak now or tap test voice chips below..."
                                else -> if (isTelugu) "నొక్కండి (ఖర్చులు, అప్పులు, బడ్జెట్ వివరాలు మాట్లాడవచ్చు)." else "Tap to speak (multi-item expenses, khata loans, budget)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                // If Listening or Error or Virtual Device: Provide Done & Cancel buttons + Quick Voice Test Chips
                AnimatedVisibility(visible = isListening || speechError != null || viewModel.voiceAgentManager.isEmulator()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        if (isListening) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.submitVoiceListeningImmediately() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isTelugu) "పూర్తయింది (Done)" else "Finish & Process", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cancelVoiceListening() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isTelugu) "రద్దు (Cancel)" else "Cancel", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = if (isTelugu) "💡 వర్చువల్ / త్వరిత వాయిస్ టెస్ట్ (ఏదైనా చిప్ నొక్కండి):"
                            else "💡 Virtual / Quick Voice Test (Tap to simulate speech):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val testChips = if (isTelugu) listOf(
                                "🍵 టీ 20 మరియు సమోసా 30" to "టీ 20 మరియు సమోసా 30",
                                "⛽ పెట్రోల్ 350 రూపాయలు" to "పెట్రోల్ 350 రూపాయలు",
                                "🤝 రమేష్ కి 2000 అప్పు ఇచ్చాను" to "రమేష్ కి 2000 అప్పు ఇచ్చాను",
                                "💵 రమేష్ 1000 తిరిగి ఇచ్చాడు" to "రమేష్ 1000 తిరిగి ఇచ్చాడు",
                                "📅 నెల బడ్జెట్ 45000 గా పెట్టు" to "నెల బడ్జెట్ 45000 గా పెట్టు",
                                "📊 ఈ రోజు ఎంత ఖర్చు చేశాను?" to "ఈ రోజు ఎంత ఖర్చు చేశాను?",
                                "🛒 కూరగాయలు 250" to "కూరగాయలు 250 రూపాయలు"
                            ) else listOf(
                                "🍵 Tea 20 and Samosa 30" to "Tea 20 and samosa 30",
                                "⛽ Petrol 350 rupees" to "Petrol 350 rupees",
                                "🤝 Lent 2000 to Ramesh" to "Lent 2000 to Ramesh",
                                "💵 Ramesh returned 1000" to "Ramesh returned 1000",
                                "📅 Set monthly budget 45000" to "Set monthly budget to 45000",
                                "📊 How much spent today?" to "How much did I spend today?",
                                "🛒 Groceries 250" to "Groceries 250"
                            )

                            items(testChips) { (label, phrase) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        viewModel.voiceAgentManager.simulateVoiceInput(phrase)
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Actionable Error Banner (e.g. Offline Speech Pack download guidance)
        if (speechError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = speechError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (speechError?.contains("ఆఫ్‌లైన్") == true || speechError?.contains("Offline") == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = { viewModel.voiceAgentManager.openOfflineSpeechSettings(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isTelugu) "సెట్టింగ్స్" else "Settings",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Chat & Cards List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubbleItem(
                    message = msg,
                    isTelugu = isTelugu,
                    onUndoClick = { result -> viewModel.undoLastAgentAction(result) },
                    onSendReminderClick = { loan -> viewModel.sendRepaymentReminder(context, loan) },
                    onSuggestionClick = { suggestion -> viewModel.processAgentInput(suggestion) }
                )
            }

            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isTelugu) "స్థానికంగా లెక్కిస్తున్నాను..." else "Processing with local finance brain...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Smart Prompt Suggestions (Instant one-tap inquiries & actions)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timePrompt = when {
                hour in 6..11 -> if (isTelugu) "🍳 టిఫిన్ 60" to "టిఫిన్ 60 రూపాయలు" else "🍳 Breakfast ₹60" to "Breakfast 60"
                hour in 12..16 -> if (isTelugu) "🍛 భోజనం 120" to "భోజనం 120 రూపాయలు" else "🍛 Lunch ₹120" to "Lunch 120"
                hour in 17..21 -> if (isTelugu) "🛒 కిరాణా 300" to "కిరాణా 300 రూపాయలు" else "🛒 Groceries ₹300" to "Groceries 300"
                else -> if (isTelugu) "🥛 పాలు 40" to "పాలు 40 రూపాయలు" else "🥛 Milk ₹40" to "Milk 40"
            }

            val suggestions = if (isTelugu) listOf(
                timePrompt,
                "📊 ఈ రోజు ఖర్చు ఎంత?" to "ఈ రోజు ఖర్చు ఎంత?",
                "🤝 రమేష్ కి 500 ఇచ్చాను" to "రమేష్ కి 500 ఇచ్చాను",
                "💰 బడ్జెట్ ఎంత మిగిలింది?" to "బడ్జెట్ ఎంత మిగిలింది?",
                "🤝 బాకీలు ఎంత ఉన్నాయి?" to "బాకీలు ఎంత ఉన్నాయి?",
                "💡 పొదుపు చిట్కా" to "పొదుపు చిట్కా చెప్పు",
                "↩️ చివరి ఖర్చు రద్దు" to "చివరి ఖర్చు రద్దు చేయి"
            ) else listOf(
                timePrompt,
                "📊 Today's Total" to "How much did I spend today?",
                "🤝 Lent 500 to Ramesh" to "Gave 500 to Ramesh",
                "💰 Remaining Budget" to "What is my remaining budget?",
                "🤝 Pending Loans" to "Who owes me money?",
                "💡 Savings Advice" to "Give me financial advice",
                "↩️ Undo Last" to "Undo last transaction"
            )

            items(suggestions) { (label, command) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        viewModel.processAgentInput(command)
                    }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Input Controls (Text + Mic + Send)
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Mic Button
                IconButton(
                    onClick = { onMicClicked() },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("agent_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Speak Expense",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (isTelugu) "ఉదా: కూరగాయలకి 450 రూపాయలు..." else "e.g., Spent 500 on lunch...",
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.processAgentInput(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isProcessing,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("agent_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }


    if (showAudioPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showAudioPermissionRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isTelugu) "మైక్రోఫోన్ అనుమతి అవసరం" else "Microphone Permission Required",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTelugu)
                        "మీరు టైప్ చేయకుండా తెలుగు లేదా ఇంగ్లీష్ లో ఖర్చులు మరియు అప్పుల వివరాలు మాట్లాడి నమోదు చేయడానికి మైక్రోఫోన్ అనుమతి అవసరం. దయచేసి అనుమతించండి."
                    else
                        "Dhanam AI uses microphone access to capture and categorize your spoken expenses and loan records in Telugu or English. Please grant microphone permission to enable voice input."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAudioPermissionRationale = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            (context as? com.example.MainActivity)?.requestAudioPermission { isGranted ->
                                if (isGranted) launchVoiceRecognition()
                            }
                        }
                    }
                ) {
                    Text(if (isTelugu) "సెట్టింగ్స్ లో అనుమతించండి" else "Open App Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAudioPermissionRationale = false }) {
                    Text(if (isTelugu) "రద్దు" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun LanguagePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubbleItem(
    message: AgentChatMessage,
    isTelugu: Boolean,
    onUndoClick: (AgentResult) -> Unit,
    onSendReminderClick: (com.example.data.model.Loan) -> Unit,
    onSuggestionClick: (String) -> Unit = {}
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = bgColor,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        message.agentResult?.let { result ->
            Spacer(modifier = Modifier.height(6.dp))

            if (result.isOverspent && result.overspentWarning != null) {
                OverspendingBanner(
                    title = if (isTelugu) "బడ్జెట్ హెచ్చరిక!" else "Budget Overspending Alert!",
                    message = result.overspentWarning,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            if (result.type != ResultType.OUT_OF_SCOPE && result.type != ResultType.ERROR) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val isMulti = result.type == ResultType.MULTI_EXPENSE_RECORDED || result.recordedExpenses.size > 1
                        if (isMulti) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = EmeraldPrimaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isTelugu) "ఒకేసారి నమోదైన ఖర్చులు (${result.recordedExpenses.size})"
                                        else "Multi-Item Records (${result.recordedExpenses.size})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "₹${result.totalRecordedAmount.toInt()}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldPrimaryLight
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            result.recordedExpenses.forEach { exp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = exp.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = exp.category,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "₹${exp.amount.toInt()}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            val isLoan = result.type == ResultType.LOAN_RECORDED
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isLoan) Icons.Default.Handshake else Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = if (isLoan) AmberSecondaryLight else EmeraldPrimaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isLoan) (if (isTelugu) "ఇచ్చిన అప్పు (Khata)" else "Money Lent (Khata)")
                                        else (if (isTelugu) "నమోదైన ఖర్చు" else "Recorded Expense"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val amt = result.recordedExpense?.amount ?: result.recordedLoan?.amount ?: 0.0
                                Text(
                                    text = "₹${amt.toInt()}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isLoan) AmberSecondaryLight else EmeraldPrimaryLight
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val itemTitle = result.recordedExpense?.title ?: result.recordedLoan?.personName ?: ""
                            Text(
                                text = itemTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        result.recordedLoan?.let { loan ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val dueStr = loan.dueDateMillis?.let {
                                SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(it))
                            } ?: "7 days"

                            Text(
                                text = if (isTelugu) "తిరిగి అడగాల్సిన గడువు: $dueStr" else "Repayment Due Date: $dueStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onSendReminderClick(loan) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberSecondaryLight
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTelugu) "${loan.personName} కి వాట్సాప్ రిమైండర్ పంపండి"
                                    else "Send WhatsApp Reminder to ${loan.personName}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val undoLabel = if (isMulti) {
                            if (isTelugu) "అన్నీ రద్దు చేయి (Undo All)" else "Undo All"
                        } else {
                            if (isTelugu) "రద్దు చేయి (Undo)" else "Undo"
                        }
                        OutlinedButton(
                            onClick = { onUndoClick(result) },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = undoLabel, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (!message.isUser && message.agentResult?.suggestions?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                message.agentResult.suggestions.forEach { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { onSuggestionClick(suggestion) }
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
