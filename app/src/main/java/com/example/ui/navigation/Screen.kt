package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val titleEn: String,
    val titleTe: String,
    val icon: ImageVector
) {
    AGENT("agent", "AI Agent", "AI ఏజెంట్", Icons.Default.AutoAwesome),
    EXPENSES("expenses", "Expenses", "ఖర్చులు", Icons.AutoMirrored.Filled.ReceiptLong),
    KHATA("khata", "Lending Khata", "ఇచ్చిన అప్పు", Icons.Default.Handshake),
    INSIGHTS("insights", "Insights & PDF", "విశ్లేషణ & PDF", Icons.Default.Insights),
    SETTINGS("settings", "Settings", "సెట్టింగ్స్", Icons.Default.Settings)
}
