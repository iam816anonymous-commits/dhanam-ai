package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.model.CategoryBudget
import com.example.notifications.NotificationReminderManager
import com.example.security.BiometricAuthManager
import com.example.security.BiometricStatus
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberSecondaryLight
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val themeMode by viewModel.themeMode.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val categoryBudgets by viewModel.categoryBudgets.collectAsState()
    val isVoiceEnabled by viewModel.voiceFeedbackEnabled.collectAsState()
    val appPin by viewModel.appPin.collectAsState()
    val isCloudAiOptIn by viewModel.isCloudAiOptIn.collectAsState()
    val adaptivePlan by viewModel.adaptivePlan.collectAsState()
    val isTelugu = preferredLang == "te"

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showBiometricUnenrolledDialog by remember { mutableStateOf(false) }
    var showAppPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryBudget?>(null) }

    // Dynamic Permission States
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationReminderManager.hasNotificationPermission(context))
    }

    val biometricStatus = BiometricAuthManager.getBiometricStatus(context)
    val hasEnrolledBiometrics = biometricStatus == BiometricStatus.AVAILABLE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isTelugu) "సెట్టింగ్స్ & భద్రత (Settings & Security)" else "Settings & Security",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 1. Accessibility: Dark Mode Toggle
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isTelugu) "యాక్సెసిబిలిటీ: డార్క్ మోడ్ (Dark Mode)" else "Accessibility: Appearance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isTelugu) "కంటి చూపు సౌలభ్యం కోసం లైట్ లేదా డార్క్ మోడ్ ఎంచుకోండి."
                        else "Choose dark theme for better eye comfort and accessibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionTile(
                            title = if (isTelugu) "సిస్టమ్" else "System",
                            icon = Icons.Default.SettingsBrightness,
                            isSelected = themeMode == "SYSTEM",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setThemeMode("SYSTEM") }
                        )

                        ThemeOptionTile(
                            title = if (isTelugu) "లైట్" else "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == "LIGHT",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setThemeMode("LIGHT") }
                        )

                        ThemeOptionTile(
                            title = if (isTelugu) "డార్క్" else "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == "DARK",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setThemeMode("DARK") }
                        )
                    }
                }
            }
        }

        // 2. Permissions & Privacy Center (Addresses runtime permissions cleanly)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTelugu) "అనుమతులు & గోప్యత (App Permissions)" else "Permissions & Privacy Center",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isTelugu)
                            "వాయిస్ ఇన్‌పుట్, నోటిఫికేషన్లు మరియు బయోమెట్రిక్ స్థితిని ఇక్కడ తనిఖీ చేయండి."
                        else
                            "Real-time status of microphone voice input, reminders, and biometric sensors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Microphone Permission Row
                    PermissionStatusRow(
                        title = if (isTelugu) "మైక్రోఫోన్ ఆడియో (RECORD_AUDIO)" else "Microphone Audio (Voice Input)",
                        subtitle = if (isTelugu) "తెలుగు & ఇంగ్లీష్ లో ఖర్చులు చెప్పడానికి" else "Used to dictate expenses in Telugu & English",
                        isGranted = hasMicPermission,
                        onGrantClick = {
                            val mainActivity = activity as? com.example.MainActivity
                            if (mainActivity != null) {
                                mainActivity.requestAudioPermission { isGranted ->
                                    hasMicPermission = isGranted
                                    Toast.makeText(
                                        context,
                                        if (isGranted) (if (isTelugu) "మైక్రోఫోన్ అనుమతి మంజూరు చేయబడింది" else "Microphone permission granted")
                                        else (if (isTelugu) "మైక్రోఫోన్ అనుమతి నిరాకరించబడింది" else "Microphone permission denied"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onOpenSettings = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                com.example.util.AppLogger.e("SettingsScreen", "Failed opening details settings", e)
                                try {
                                    val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(generalIntent)
                                } catch (_: Exception) {}
                            }
                        },
                        isTelugu = isTelugu
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Notifications Permission Row
                    PermissionStatusRow(
                        title = if (isTelugu) "నోటిఫికేషన్లు (POST_NOTIFICATIONS)" else "System Notifications",
                        subtitle = if (isTelugu) "ఖాతా రీపేమెంట్ & బడ్జెట్ హెచ్చరికల కోసం" else "For loan due reminders & overspending alerts",
                        isGranted = hasNotificationPermission,
                        onGrantClick = {
                            val mainActivity = activity as? com.example.MainActivity
                            if (mainActivity != null) {
                                mainActivity.requestNotificationPermission { isGranted ->
                                    hasNotificationPermission = isGranted
                                    Toast.makeText(
                                        context,
                                        if (isGranted) (if (isTelugu) "నోటిఫికేషన్ అనుమతి మంజూరు చేయబడింది" else "Notification permission granted")
                                        else (if (isTelugu) "నోటిఫికేషన్ అనుమతి నిరాకరించబడింది" else "Notification permission denied"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onOpenSettings = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                com.example.util.AppLogger.e("SettingsScreen", "Failed opening details settings", e)
                                try {
                                    val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(generalIntent)
                                } catch (_: Exception) {}
                            }
                        },
                        isTelugu = isTelugu
                    )

                    if (hasNotificationPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                NotificationReminderManager.showTestNotification(context, isTelugu)
                                Toast.makeText(
                                    context,
                                    if (isTelugu) "టెస్ట్ నోటిఫికేషన్ పంపబడింది!" else "Test notification sent!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTelugu) "నోటిఫికేషన్ పరీక్షించండి (Test Notification)" else "Send Test Notification")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showBatteryOptimizationDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTelugu) "రిమైండర్లు అందట్లేదా? (బ్యాటరీ సెట్టింగ్స్)" else "Reminders not arriving? (OEM Battery Fix)")
                    }
                }
            }
        }

        // 3. Security: Biometric Authentication
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasEnrolledBiometrics) MaterialTheme.colorScheme.primaryContainer
                                        else AmberSecondaryLight.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (hasEnrolledBiometrics) MaterialTheme.colorScheme.primary else AmberSecondaryLight
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isTelugu) "బయోమెట్రిక్ లాక్ (Biometric Lock)" else "Biometric Security",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (biometricStatus) {
                                        BiometricStatus.AVAILABLE -> if (isTelugu) "ఫింగర్‌ప్రింట్ నమోదు చేయబడింది (సిద్ధం)" else "Biometrics enrolled & ready"
                                        BiometricStatus.NOT_ENROLLED -> if (isTelugu) "పరికరంలో బయోమెట్రిక్స్ నమోదు కాలేదు" else "No biometrics registered on device"
                                        BiometricStatus.NO_HARDWARE -> if (isTelugu) "బయోమెట్రిక్ హార్డ్‌వేర్ లేదు" else "No biometric hardware on device"
                                        BiometricStatus.UNAVAILABLE -> if (isTelugu) "సెన్సార్ అందుబాటులో లేదు" else "Sensor currently unavailable"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasEnrolledBiometrics) SuccessGreen else AmberSecondaryLight
                                )
                            }
                        }

                        Switch(
                            checked = isBiometricEnabled && hasEnrolledBiometrics,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (!hasEnrolledBiometrics) {
                                        // User does NOT have biometrics registered!
                                        // Refuse to enable and show clear explanatory dialog
                                        showBiometricUnenrolledDialog = true
                                    } else {
                                        // Verify biometrics right now before enabling!
                                        if (activity != null) {
                                            BiometricAuthManager.showBiometricPrompt(
                                                activity = activity,
                                                title = if (isTelugu) "బయోమెట్రిక్ ధృవీకరణ" else "Verify Biometrics",
                                                subtitle = if (isTelugu) "లాక్ ఆన్ చేయడానికి వేలిముద్ర వేయండి" else "Scan fingerprint to enable biometric lock",
                                                onSuccess = {
                                                    viewModel.setBiometricEnabled(true)
                                                    Toast.makeText(
                                                        context,
                                                        if (isTelugu) "బయోమెట్రిక్ లాక్ ఆన్ చేయబడింది!" else "Biometric lock enabled!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            viewModel.setBiometricEnabled(true)
                                        }
                                    }
                                } else {
                                    viewModel.setBiometricEnabled(false)
                                    Toast.makeText(
                                        context,
                                        if (isTelugu) "బయోమెట్రిక్ లాక్ ఆఫ్ చేయబడింది" else "Biometric lock disabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("biometric_switch")
                        )
                    }

                    if (!hasEnrolledBiometrics) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { BiometricAuthManager.openSecuritySettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isTelugu) "Android సెట్టింగ్స్‌లో ఫింగర్‌ప్రింట్ నమోదు చేయండి"
                                else "Register Fingerprint in Device Settings"
                            )
                        }
                    } else if (isBiometricEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.lockApp() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTelugu) "ఇప్పుడే లాక్ చేసి పరీక్షించండి" else "Lock App Now to Test")
                        }
                    }
                }
            }
        }

        // App PIN Security Card (Reliable fallback for devices without registered biometrics)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AmberSecondaryLight.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = AmberSecondaryLight
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isTelugu) "యాప్ పిన్ లాక్ (App PIN Lock)" else "App PIN Protection",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (appPin != null)
                                        (if (isTelugu) "పిన్ సెట్ చేయబడింది (••••)" else "PIN is active (••••)")
                                    else
                                        (if (isTelugu) "ఫింగర్‌ప్రింట్ లేని పరికరాలకు ఆదర్శం" else "Ideal backup if no biometrics registered"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { showAppPinDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (appPin != null) (if (isTelugu) "మార్చు" else "Change")
                                else (if (isTelugu) "సెట్ చేయి" else "Set PIN")
                            )
                        }
                    }

                    if (appPin != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showRemovePinDialog = true }) {
                                Text(if (isTelugu) "పిన్ తొలగించండి (Remove PIN)" else "Remove PIN", color = AlertRed)
                            }
                        }
                    }
                }
            }
        }

        // Voice Response Toggle Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isTelugu) "వాయిస్ ప్రతిస్పందన (Voice Response)" else "Voice Audio Response",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTelugu) "ఏజెంట్ ప్రతిస్పందనలను తెలుగు/ఇంగ్లీష్ లో వినిపించు"
                                else "Hear agent speak replies & overspending alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isVoiceEnabled,
                        onCheckedChange = { viewModel.setVoiceFeedbackEnabled(it) },
                        modifier = Modifier.testTag("voice_response_switch")
                    )
                }
            }
        }

        // Cloud AI Opt-In Toggle Card (Privacy & Transparency)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isTelugu) "క్లౌడ్ AI సహాయం (ఐచ్ఛికం)" else "Cloud AI Assistance (Optional)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTelugu) "స్థానిక పార్సింగ్ విఫలమైనప్పుడు క్లౌడ్ AI (Gemini) ని ఉపయోగించండి. ఆఫ్‌లో ఉన్నప్పుడు మీ ఆర్థిక డేటా కేవలం మీ ఫోన్‌లోనే ఉంటుంది."
                                else "Allow Gemini AI fallback when on-device parsing is uncertain. When off, data stays 100% on-device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isCloudAiOptIn,
                        onCheckedChange = { viewModel.setCloudAiOptIn(it) },
                        modifier = Modifier.testTag("cloud_ai_opt_in_switch")
                    )
                }
            }
        }

        // 4. Language Preference
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTelugu) "ప్రాధాన్య భాష (Language Preference)" else "Language Preference",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (preferredLang == "te") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPreferredLanguage("te") }
                        ) {
                            Text(
                                text = "తెలుగు (Telugu)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (preferredLang == "te") FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (preferredLang == "te") MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (preferredLang == "en") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPreferredLanguage("en") }
                        ) {
                            Text(
                                text = "English",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (preferredLang == "en") FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (preferredLang == "en") MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 5. Overall & Category Budgets
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isTelugu) "నెలవారీ మొత్తం బడ్జెట్" else "Monthly Overall Budget",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "₹${overallBudget.toInt()}",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldPrimaryLight
                                )
                            )
                        }

                        Button(
                            onClick = { showBudgetDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isTelugu) "మార్చు" else "Edit")
                        }
                    }
                }
            }
        }

        // Adaptive Habit Learning Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTelugu) "ఆటో-అలొకేషన్ & అలవాట్ల అడాప్టేషన్" else "Adaptive Habit Allocation",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val insightText = adaptivePlan?.let {
                        if (isTelugu) it.proactiveInsightTe else it.proactiveInsightEn
                    } ?: if (isTelugu) {
                        "మొత్తం బడ్జెట్ ప్రకారం విభాగాల పరిమితులు ఆటోమేటిక్‌గా కేటాయించబడ్డాయి. మీరు ఖర్చు చేసే కొద్దీ మీ అలవాట్లకు అనుగుణంగా సర్దుబాటు అవుతాయి."
                    } else {
                        "Category limits are auto-allocated from your total budget and dynamically adapt as your spending habits develop."
                    }

                    Text(
                        text = insightText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.reallocateBudgetsAdaptively(overallBudget)
                            Toast.makeText(
                                context,
                                if (isTelugu) "ఖర్చుల అలవాట్ల ప్రకారం బడ్జెట్ రీ-బ్యాలెన్స్ చేయబడింది!"
                                else "Category budgets re-balanced to your spending habits!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTelugu) "అలవాట్ల ప్రకారం రీ-బ్యాలెన్స్ చేయండి"
                            else "Re-balance with Learned Habits",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Category Budgets Header
        item {
            Text(
                text = if (isTelugu) "విభాగాల బడ్జెట్ పరిమితులు (Category Limits)" else "Category Budget Limits",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(categoryBudgets) { budget ->
            val catEnum = com.example.data.model.ExpenseCategory.fromString(budget.category)
            val pct = if (overallBudget > 0) ((budget.monthlyLimit / overallBudget) * 100).toInt() else 0
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { categoryToEdit = budget }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isTelugu) catEnum.displayNameTe else catEnum.displayNameEn,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (isTelugu) "${catEnum.displayNameEn} • మొత్తం బడ్జెట్‌లో $pct%" else "$pct% of total monthly budget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "₹${budget.monthlyLimit.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // App Info
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Dhanam AI • v1.0",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Private on-device room persistence with AI expense tracking, lending khata reminders, and PDF export.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Data Management (Purge all data)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isTelugu) "డేటా నిర్వహణ (Data Management)" else "Data Management",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isTelugu) "మీ ఫోన్‌లోని ఖర్చులు మరియు అప్పుల రికార్డులను క్లియర్ చేయండి."
                        else "Clear all local expense and loan records to start fresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showClearDataDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isTelugu) "అన్ని రికార్డులను తొలగించండి (Clear All Data)" else "Clear All Data (Reset)")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isTelugu) "డేటాను తొలగించాలా?" else "Clear All Data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTelugu)
                        "ఈ చర్య మీ ఫోన్‌లోని ఖర్చులు మరియు అప్పుల (ఖాటా) రికార్డులన్నింటినీ తొలగిస్తుంది. ఈ చర్యను రద్దు చేయలేము."
                    else
                        "This will permanently delete all recorded expenses and lending khata records from your device. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                        Toast.makeText(
                            context,
                            if (isTelugu) "అన్ని రికార్డులు క్లియర్ చేయబడ్డాయి" else "All records cleared successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isTelugu) "అవును, తొలగించు" else "Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text(if (isTelugu) "రద్దు" else "Cancel")
                }
            }
        )
    }

    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isTelugu) "బ్యాటరీ ఆప్టిమైజేషన్ & రిమైండర్లు" else "Battery Saver & Reminders",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isTelugu)
                            "కొన్ని ఫోన్లలో (Samsung, Xiaomi, Vivo, Oppo, OnePlus, Tecno) కఠినమైన బ్యాటరీ ఆదా నియమాల వల్ల రిమైండర్లు సమయానికి రాకపోవచ్చు. దయచేసి ధనం AI కి 'Unrestricted / బ్యాక్‌గ్రౌండ్ అనుమతి' ఇవ్వండి:"
                        else
                            "Aggressive OEM battery optimizations (Samsung, Xiaomi/Redmi, Vivo, Oppo/Realme, OnePlus, Infinix/Tecno) can delay or block background reminders. Please set Dhanam AI battery to Unrestricted:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isTelugu)
                            "• Samsung: సెట్టింగ్స్ -> యాప్స్ -> Dhanam AI -> బ్యాటరీ -> అపరిమితం (Unrestricted)\n• Xiaomi/Redmi: సెక్యూరిటీ -> బ్యాటరీ సేవర్ -> Dhanam AI -> పరిమితులు లేవు (No restrictions) & ఆటో-స్టార్ట్ ఆన్ చేయండి\n• Vivo/Oppo/Realme: సెట్టింగ్స్ -> బ్యాటరీ -> బ్యాక్‌గ్రౌండ్ పవర్ వినియోగాన్ని అనుమతించు\n• OnePlus: సెట్టింగ్స్ -> యాప్స్ -> Dhanam AI -> బ్యాటరీ వినియోగం -> Allow background activity"
                        else
                            "• Samsung: Settings -> Apps -> Dhanam AI -> Battery -> Unrestricted\n• Xiaomi/Redmi: Security -> Battery Saver -> Dhanam AI -> No restrictions & Autostart ON\n• Vivo/Oppo/Realme: Settings -> Battery -> Allow background power consumption\n• OnePlus: Settings -> Apps -> Dhanam AI -> Battery usage -> Allow background activity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text(if (isTelugu) "సెట్టింగ్స్ తెరవండి" else "Open Battery Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                    Text(if (isTelugu) "సరే" else "Dismiss")
                }
            }
        )
    }

    if (showBudgetDialog) {
        var newBudgetStr by remember { mutableStateOf("${overallBudget.toInt()}") }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text(if (isTelugu) "మొత్తం బడ్జెట్ మార్చు" else "Edit Overall Monthly Budget") },
            text = {
                OutlinedTextField(
                    value = newBudgetStr,
                    onValueChange = { newBudgetStr = it },
                    label = { Text("Budget (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val amt = newBudgetStr.toDoubleOrNull() ?: 35000.0
                    viewModel.setOverallMonthlyBudget(amt)
                    showBudgetDialog = false
                }) {
                    Text(if (isTelugu) "సేవ్" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text(if (isTelugu) "రద్దు" else "Cancel")
                }
            }
        )
    }

    categoryToEdit?.let { budget ->
        var limitStr by remember { mutableStateOf("${budget.monthlyLimit.toInt()}") }
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text(if (isTelugu) "${budget.category} బడ్జెట్ పరిమితి" else "Edit ${budget.category} Limit") },
            text = {
                OutlinedTextField(
                    value = limitStr,
                    onValueChange = { limitStr = it },
                    label = { Text("Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val limit = limitStr.toDoubleOrNull() ?: 5000.0
                    viewModel.updateCategoryBudget(budget.category, limit)
                    categoryToEdit = null
                }) {
                    Text(if (isTelugu) "సేవ్" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) {
                    Text(if (isTelugu) "రద్దు" else "Cancel")
                }
            }
        )
    }

    if (showBiometricUnenrolledDialog) {
        AlertDialog(
            onDismissRequest = { showBiometricUnenrolledDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AmberSecondaryLight) },
            title = {
                Text(
                    text = if (isTelugu) "బయోమెట్రిక్స్ నమోదు కాలేదు" else "Biometrics Not Enrolled",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTelugu)
                        "ఈ పరికరంలో ఫింగర్‌ప్రింట్ లేదా ఫేస్ లాక్ ఇంకా సెట్ చేయలేదు. దయచేసి Android సెట్టింగ్స్‌లో ఫింగర్‌ప్రింట్ సెట్ చేసిన తర్వాత బయోమెట్రిక్ లాక్ ఆన్ చేయండి, లేదా క్రింద ఉన్న 4-అంకెల యాప్ పిన్ (App PIN) వాడండి."
                    else
                        "No fingerprint or face unlock credentials are registered on this device yet. You must first register your fingerprint in Android Settings > Security before enabling Biometric Lock, or you can use the 4-digit App PIN below."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBiometricUnenrolledDialog = false
                    BiometricAuthManager.openSecuritySettings(context)
                }) {
                    Text(if (isTelugu) "డివైజ్ సెట్టింగ్స్ ఓపెన్ చేయండి" else "Open Device Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBiometricUnenrolledDialog = false
                    showAppPinDialog = true
                }) {
                    Text(if (isTelugu) "యాప్ పిన్ సెట్ చేయి" else "Set App PIN")
                }
            }
        )
    }

    if (showAppPinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        val isChanging = appPin != null

        AlertDialog(
            onDismissRequest = { showAppPinDialog = false },
            title = {
                Text(
                    text = if (isChanging) {
                        if (isTelugu) "యాప్ పిన్ మార్చండి" else "Change App PIN"
                    } else {
                        if (isTelugu) "4-అంకెల యాప్ పిన్ సెట్ చేయండి" else "Set 4-Digit App PIN"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isChanging) {
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = {
                                if (it.length <= 6) currentPin = it
                                errorMsg = null
                            },
                            label = { Text(if (isTelugu) "ప్రస్తుత పిన్" else "Current PIN") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            if (it.length <= 6) newPin = it
                            errorMsg = null
                        },
                        label = { Text(if (isTelugu) "క్రొత్త పిన్ (4-6 అంకెలు)" else "New PIN (4-6 digits)") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            if (it.length <= 6) confirmPin = it
                            errorMsg = null
                        },
                        label = { Text(if (isTelugu) "పిన్ ధృవీకరించండి" else "Confirm PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMsg != null) {
                        Text(text = errorMsg!!, color = AlertRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (isChanging && currentPin.isBlank()) {
                        errorMsg = if (isTelugu) "దయచేసి ప్రస్తుత పిన్ నమోదు చేయండి" else "Please enter your current PIN"
                    } else if (newPin.length !in 4..6) {
                        errorMsg = if (isTelugu) "పిన్ 4 నుండి 6 అంకెలు ఉండాలి" else "PIN must be between 4 and 6 digits"
                    } else if (newPin != confirmPin) {
                        errorMsg = if (isTelugu) "రెండు పిన్‌లు సరిపోలడం లేదు" else "PINs do not match"
                    } else if (isChanging) {
                        val success = viewModel.changeAppPin(currentPin, newPin)
                        if (success) {
                            showAppPinDialog = false
                            Toast.makeText(
                                context,
                                if (isTelugu) "యాప్ పిన్ విజయవంతంగా మార్చబడింది!" else "App PIN changed successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            errorMsg = if (isTelugu) "ప్రస్తుత పిన్ తప్పుగా ఉంది" else "Incorrect current PIN"
                        }
                    } else {
                        viewModel.setAppPin(newPin)
                        showAppPinDialog = false
                        Toast.makeText(
                            context,
                            if (isTelugu) "యాప్ పిన్ సెట్ చేయబడింది!" else "App PIN set successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text(if (isTelugu) "సేవ్" else "Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppPinDialog = false }) {
                    Text(if (isTelugu) "రద్దు" else "Cancel")
                }
            }
        )
    }

    if (showRemovePinDialog) {
        var verifyCurrentPin by remember { mutableStateOf("") }
        var removeErrorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            title = {
                Text(
                    text = if (isTelugu) "యాప్ పిన్ తొలగించండి" else "Remove App PIN",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isTelugu)
                            "పిన్ తొలగించడానికి మీ ప్రస్తుత పిన్ నమోదు చేయండి."
                        else
                            "Please enter your current PIN to confirm removal.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = verifyCurrentPin,
                        onValueChange = {
                            if (it.length <= 6) verifyCurrentPin = it
                            removeErrorMsg = null
                        },
                        label = { Text(if (isTelugu) "ప్రస్తుత పిన్" else "Current PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (removeErrorMsg != null) {
                        Text(text = removeErrorMsg!!, color = AlertRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val removed = viewModel.removeAppPin(verifyCurrentPin)
                        if (removed) {
                            showRemovePinDialog = false
                            Toast.makeText(
                                context,
                                if (isTelugu) "యాప్ పిన్ తొలగించబడింది" else "App PIN removed",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            removeErrorMsg = if (isTelugu) "తప్పుడు పిన్. మళ్ళీ ప్రయత్నించండి." else "Incorrect PIN. Try again."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text(if (isTelugu) "తొలగించు" else "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePinDialog = false }) {
                    Text(if (isTelugu) "రద్దు" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
    isTelugu: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) SuccessGreen.copy(alpha = 0.15f) else AmberSecondaryLight.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isGranted) (if (isTelugu) "మంజూరైంది ✓" else "Granted ✓")
                        else (if (isTelugu) "కావాలి ✕" else "Not Granted ✕"),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isGranted) SuccessGreen else AmberSecondaryLight,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (!isGranted) {
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(if (isTelugu) "అనుమతించు" else "Grant", fontSize = 12.sp)
            }
        } else {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (isTelugu) "మార్చు" else "Manage", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ThemeOptionTile(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
