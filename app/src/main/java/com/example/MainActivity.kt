package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.notifications.NotificationReminderManager
import com.example.security.BiometricAuthManager
import com.example.ui.components.BiometricLockOverlay
import com.example.ui.components.JarvisFloatingOverlay
import com.example.ui.components.VoiceListeningDialog
import com.example.ui.navigation.Screen
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.InsightsPdfScreen
import com.example.ui.screens.LendingKhataScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var audioPermissionCallback: ((Boolean) -> Unit)? = null
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null

    val systemSpeechLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setShowVoiceListeningDialog(false)
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenTextList = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenTextList?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.processAgentInput(spokenText)
            }
        }
    }

    fun launchSystemSpeechFallback() {
        val isTe = viewModel.preferredLanguage.value == "te"
        val fallbackIntent = viewModel.voiceAgentManager.createFallbackRecognizerIntent(isTe)

        val canResolve = viewModel.voiceAgentManager.isSystemSpeechActivityAvailable(this)
        if (canResolve) {
            try {
                systemSpeechLauncher.launch(fallbackIntent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val errMsg = if (isTe) {
            "వాయిస్ రికగ్నిషన్ సేవ ప్రారంభం కాలేదు. దయచేసి మైక్ నొక్కి మళ్లీ మాట్లాడండి."
        } else {
            "Voice recognition service unavailable. Please tap mic to try again."
        }
        viewModel.setVoiceErrorMessage(errMsg)
        Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
    }

    fun requestAudioPermission(callback: (Boolean) -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            callback(true)
            return
        }
        audioPermissionCallback = callback
        androidx.core.app.ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            1001
        )
    }

    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                callback(true)
                return
            }
            notificationPermissionCallback = callback
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1002
            )
        } else {
            callback(true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val isGranted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            1001 -> {
                audioPermissionCallback?.invoke(isGranted)
                audioPermissionCallback = null
            }
            1002 -> {
                notificationPermissionCallback?.invoke(isGranted)
                notificationPermissionCallback = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationReminderManager.initNotificationChannel(this)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isAppLocked by viewModel.isAppLocked.collectAsState()
            val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
            val appPin by viewModel.appPin.collectAsState()
            val preferredLang by viewModel.preferredLanguage.collectAsState()
            val isTelugu = preferredLang == "te"

            // Check if device actually has registered biometric credentials (fingerprint/face)
            val hasEnrolledBiometrics = BiometricAuthManager.isBiometricEnrolled(this@MainActivity)

            // Auto-remedy: If biometrics are not registered on this device, disable biometric lock
            // so the app NEVER locks without having registered biometrics
            LaunchedEffect(hasEnrolledBiometrics) {
                if (!hasEnrolledBiometrics && isBiometricEnabled) {
                    viewModel.setBiometricEnabled(false)
                    viewModel.unlockApp()
                }
            }

            // Lock screen is ONLY valid if:
            // 1) App PIN is set by the user, OR
            // 2) Biometric lock is enabled AND device actually has registered biometrics
            val shouldLock = isAppLocked && ((isBiometricEnabled && hasEnrolledBiometrics) || appPin != null)

            MyApplicationTheme(themeMode = themeMode) {
                if (shouldLock) {
                    // Only auto-trigger biometric prompt if device actually has biometrics enrolled!
                    if (hasEnrolledBiometrics && isBiometricEnabled) {
                        LaunchedEffect(Unit) {
                            attemptBiometricUnlock()
                        }
                    }

                    BiometricLockOverlay(
                        isTelugu = isTelugu,
                        hasBiometricsEnrolled = hasEnrolledBiometrics,
                        appPin = appPin,
                        onUnlockClick = { attemptBiometricUnlock() },
                        onVerifyPin = { pin -> viewModel.verifyAppPin(pin) },
                        onBypassIfNoCredentials = {
                            viewModel.setBiometricEnabled(false)
                            viewModel.unlockApp()
                            Toast.makeText(
                                this@MainActivity,
                                if (isTelugu) "పరికరంలో బయోమెట్రిక్స్ లేనందున లాక్ తీసివేయబడింది"
                                else "Lock disabled as no biometrics are registered on this device",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onOpenSettings = {
                            BiometricAuthManager.openSecuritySettings(this@MainActivity)
                        }
                    )
                } else {
                    MainAppScaffold(
                        viewModel = viewModel,
                        onGlobalMicClick = { launchGlobalVoiceRecognition() },
                        onLockClick = {
                            val canLockBiometric = isBiometricEnabled && hasEnrolledBiometrics
                            val canLockPin = appPin != null

                            if (canLockBiometric || canLockPin) {
                                viewModel.lockApp()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    if (isTelugu) "ముందుగా సెట్టింగ్స్‌లో బయోమెట్రిక్ లేదా 4-అంకెల పిన్ సెట్ చేసుకోండి"
                                    else "Set up an App PIN or register device biometrics in Settings first",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun launchGlobalVoiceRecognition() {
        if (viewModel.isListening.value) {
            viewModel.submitVoiceListeningImmediately()
            return
        }

        requestAudioPermission { granted ->
            if (granted) {
                val isTe = viewModel.preferredLanguage.value == "te"
                viewModel.startVoiceListening(
                    isTelugu = isTe,
                    onResult = { spokenText ->
                        if (spokenText.isNotBlank()) {
                            viewModel.processAgentInput(spokenText)
                        }
                    }
                )
            } else {
                Toast.makeText(
                    this,
                    if (viewModel.preferredLanguage.value == "te") "మైక్రోఫోన్ అనుమతి అవసరం" else "Microphone permission required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun attemptBiometricUnlock() {
        if (!BiometricAuthManager.isBiometricEnrolled(this)) return

        val isTe = viewModel.preferredLanguage.value == "te"
        BiometricAuthManager.showBiometricPrompt(
            activity = this,
            title = if (isTe) "Dhanam AI అన్‌లాక్" else "Unlock Dhanam AI",
            subtitle = if (isTe) "మీ ఆర్థిక వివరాలను చూడటానికి వేలిముద్ర వేయండి" else "Authenticate with fingerprint or face to view records",
            negativeButtonText = if (isTe) "రద్దు" else "Cancel",
            onSuccess = {
                viewModel.unlockApp()
            },
            onError = { errMsg ->
                // Prompt cancelled or failed; user can re-tap or enter App PIN
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: MainViewModel,
    onGlobalMicClick: () -> Unit,
    onLockClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val isTelugu = preferredLang == "te"

    val showVoiceListeningDialog by viewModel.showVoiceListeningDialog.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val voiceErrorMessage by viewModel.voiceErrorMessage.collectAsState()
    val isTeluguTtsAvailable by viewModel.isTeluguTtsAvailable.collectAsState()

    // BackHandler to return to Agent if on other screen
    BackHandler(enabled = currentScreen != Screen.AGENT) {
        viewModel.setCurrentScreen(Screen.AGENT)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isTelugu) currentScreen.titleTe else currentScreen.titleEn,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    // Quick theme toggle icon
                    IconButton(
                        onClick = {
                            val nextMode = when (themeMode) {
                                "LIGHT" -> "DARK"
                                "DARK" -> "LIGHT"
                                else -> "DARK"
                            }
                            viewModel.setThemeMode(nextMode)
                        }
                    ) {
                        Icon(
                            imageVector = if (themeMode == "DARK") Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Quick lock button
                    IconButton(onClick = onLockClick) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock App",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Screen.entries.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.setCurrentScreen(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = if (isTelugu) screen.titleTe else screen.titleEn
                            )
                        },
                        label = {
                            Text(
                                text = if (isTelugu) screen.titleTe else screen.titleEn,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.AGENT -> AgentScreen(viewModel = viewModel)
                Screen.EXPENSES -> ExpensesScreen(viewModel = viewModel)
                Screen.KHATA -> LendingKhataScreen(viewModel = viewModel)
                Screen.INSIGHTS -> InsightsPdfScreen(viewModel = viewModel)
                Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }

            // Jarvis Living AI Entity Overlay across all screens
            JarvisFloatingOverlay(
                viewModel = viewModel,
                onMicClick = onGlobalMicClick
            )

            // Universal Voice Listening Dialog across all screens
            if (showVoiceListeningDialog) {
                val currentVoiceLang by viewModel.voiceLanguage.collectAsState()
                VoiceListeningDialog(
                    isTelugu = isTelugu,
                    isListening = isListening,
                    partialTranscript = partialTranscript,
                    rmsDb = rmsDb,
                    errorMessage = voiceErrorMessage,
                    isTeluguTtsAvailable = isTeluguTtsAvailable,
                    isEmulator = viewModel.voiceAgentManager.isEmulator(),
                    isOnline = viewModel.voiceAgentManager.isOnline(),
                    currentLanguage = currentVoiceLang,
                    onSelectLanguage = { lang ->
                        viewModel.setVoiceLanguage(lang)
                        if (lang == "te") viewModel.setPreferredLanguage("te")
                        else if (lang == "en") viewModel.setPreferredLanguage("en")
                        onGlobalMicClick()
                    },
                    onDismiss = {
                        viewModel.cancelVoiceListening()
                    },
                    onRetryListening = {
                        onGlobalMicClick()
                    },
                    onSwitchLanguage = {
                        val nextLang = if (isTelugu) "en" else "te"
                        viewModel.setPreferredLanguage(nextLang)
                        viewModel.setVoiceLanguage(nextLang)
                        onGlobalMicClick()
                    },
                    isSystemVoiceAvailable = viewModel.voiceAgentManager.isSystemSpeechActivityAvailable(context),
                    onDoneListening = {
                        viewModel.submitVoiceListeningImmediately()
                    },
                    onLaunchSystemVoice = {
                        (context as? MainActivity)?.launchSystemSpeechFallback()
                    },
                    onOpenVoiceSettings = {
                        viewModel.voiceAgentManager.openOfflineSpeechSettings(context)
                    },
                    onSimulateVoice = { phrase ->
                        viewModel.voiceAgentManager.simulateVoiceInput(phrase)
                    }
                )
            }
        }
    }
}
