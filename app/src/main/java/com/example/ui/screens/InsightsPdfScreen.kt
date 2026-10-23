package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DailySafeToSpendCard
import com.example.ui.components.PaymentModeBreakdownCard
import com.example.ui.components.WeeklySpendBarChart
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberSecondaryLight
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InsightsPdfScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categoryTotals by viewModel.categoryTotals.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val savingsTips by viewModel.savingsTips.collectAsState()
    val financialHealth by viewModel.financialHealth.collectAsState()
    val preferredLang by viewModel.preferredLanguage.collectAsState()
    val isTelugu = preferredLang == "te"

    val selectedMonthTimestamp by viewModel.selectedMonthTimestamp.collectAsState()
    val isAllMonthsSelected by viewModel.isAllMonthsSelected.collectAsState()

    var isExportingPdf by remember { mutableStateOf(false) }
    var isExportingCsv by remember { mutableStateOf(false) }

    val totalSpent = categoryTotals.sumOf { it.total }
    val dateStr = if (isAllMonthsSelected) {
        if (isTelugu) "అన్ని నెలల చరిత్ర" else "All Months History"
    } else {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedMonthTimestamp }
        val mIdx = cal.get(java.util.Calendar.MONTH)
        val yr = cal.get(java.util.Calendar.YEAR)
        val teMonths = arrayOf(
            "జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్",
            "జూలై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్"
        )
        if (isTelugu && mIdx in 0..11) "${teMonths[mIdx]} $yr"
        else SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(selectedMonthTimestamp))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("insights_pdf_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Month Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isTelugu) "విజువల్ విశ్లేషణ & సేవింగ్స్" else "Visual Analytics & Insights",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$dateStr • ${if (isTelugu) "స్థానిక గణనలు" else "100% Local Calculations"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { viewModel.refreshSavingsTips() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 1. Daily Safe-to-Spend & Burn Rate Metric Card
        item {
            DailySafeToSpendCard(
                health = financialHealth,
                isTelugu = isTelugu
            )
        }

        // 2. Interactive Category Donut Chart
        item {
            CategoryDonutChart(
                categoryTotals = categoryTotals,
                totalSpent = totalSpent,
                isTelugu = isTelugu
            )
        }

        // 3. Weekly 7-Day Spend Pattern Bar Chart
        item {
            WeeklySpendBarChart(
                expenses = allExpenses,
                isTelugu = isTelugu
            )
        }

        // 4. Payment Methods Share (UPI vs Card vs Cash)
        item {
            PaymentModeBreakdownCard(
                expenses = allExpenses,
                isTelugu = isTelugu
            )
        }

        // 5. PDF Export Banner & Action Button
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(AlertRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF Report",
                                tint = AlertRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isTelugu) "నెలవారీ PDF సారాంశ నివేదిక" else "Monthly PDF Summary Report",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Text(
                                text = if (isTelugu) "ఖర్చులు, అప్పులు మరియు బడ్జెట్ వివరాలతో PDF"
                                else "Category tables, khata balances & tips in PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isExportingPdf = true
                            viewModel.exportAndShareMonthlyPdf(context) { success, msg ->
                                isExportingPdf = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isExportingPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_pdf_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(if (isTelugu) "PDF ని రూపొందిస్తున్నాము..." else "Generating PDF Report...")
                        } else {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTelugu) "PDF ని డౌన్‌లోడ్ & షేర్ చేయండి" else "Generate & Share Monthly PDF",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5b. CSV / Excel Export Button
                    Button(
                        onClick = {
                            isExportingCsv = true
                            viewModel.exportAndShareCsv(context) { success, msg ->
                                isExportingCsv = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isExportingCsv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimaryLight,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        if (isExportingCsv) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = androidx.compose.ui.graphics.Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(if (isTelugu) "CSV ఫైల్ సిద్ధం చేస్తున్నాము..." else "Generating CSV File...")
                        } else {
                            Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTelugu) "Excel & Sheets కోసం CSV ఎగుమతి" else "Export CSV (Excel / Google Sheets)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // 6. AI Savings Tips Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AmberSecondaryLight,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTelugu) "వ్యక్తిగత పొదుపు సలహాలు (AI Savings Tips)" else "Personalized Savings Tips",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Savings Tips Cards
        items(savingsTips) { tip ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldPrimaryLight,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}
