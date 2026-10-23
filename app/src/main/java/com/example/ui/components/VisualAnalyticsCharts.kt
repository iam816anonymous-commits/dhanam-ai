package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AgentFinancialBrain
import com.example.data.local.CategoryTotal
import com.example.data.model.Expense
import com.example.data.model.Loan
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberSecondaryLight
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.IndigoTertiaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

val ChartPalette = listOf(
    Color(0xFF0F766E), // Teal
    Color(0xFFD97706), // Amber
    Color(0xFF4F46E5), // Indigo
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4), // Cyan
    Color(0xFF8B5CF6), // Purple
    Color(0xFF10B981), // Emerald
    Color(0xFFF97316), // Orange
    Color(0xFF64748B)  // Slate
)

@Composable
fun CategoryDonutChart(
    categoryTotals: List<CategoryTotal>,
    totalSpent: Double,
    isTelugu: Boolean,
    modifier: Modifier = Modifier
) {
    if (categoryTotals.isEmpty() || totalSpent <= 0) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTelugu) "విశ్లేషించడానికి ఖర్చులు లేవు" else "No expenses yet to visualize",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(categoryTotals) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val sorted = categoryTotals.sortedByDescending { it.total }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_donut_chart")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (isTelugu) "ఖర్చుల విభజన (Category Distribution)" else "Category Spending Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Donut Chart Canvas with Center Text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(190.dp)
                        .pointerInput(sorted) {
                            detectTapGestures { tapOffset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = tapOffset.x - center.x
                                val dy = tapOffset.y - center.y
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f

                                var currentStart = 0f
                                for (i in sorted.indices) {
                                    val sweep = ((sorted[i].total / totalSpent) * 360f).toFloat()
                                    if (angle >= currentStart && angle <= currentStart + sweep) {
                                        selectedIndex = if (selectedIndex == i) null else i
                                        break
                                    }
                                    currentStart += sweep
                                }
                            }
                        }
                ) {
                    val strokeWidth = 32.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    var startAngle = -90f
                    for (i in sorted.indices) {
                        val sweep = ((sorted[i].total / totalSpent) * 360f).toFloat() * animatedProgress.value
                        val color = ChartPalette[i % ChartPalette.size]
                        val isSelected = selectedIndex == i

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = if (isSelected) strokeWidth + 8.dp.toPx() else strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                        startAngle += sweep
                    }
                }

                // Center Label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedIndex != null && selectedIndex!! in sorted.indices) {
                        val item = sorted[selectedIndex!!]
                        val pct = ((item.total / totalSpent) * 100).toInt()
                        Text(
                            text = item.category.split("&")[0].trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = "₹${item.total.toInt()}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = ChartPalette[selectedIndex!! % ChartPalette.size]
                            )
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = if (isTelugu) "మొత్తం ఖర్చు" else "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${totalSpent.toInt()}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend FlowRow
            OptInFlowRowLegend(
                sorted = sorted,
                totalSpent = totalSpent,
                selectedIndex = selectedIndex,
                onSelect = { i -> selectedIndex = if (selectedIndex == i) null else i }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptInFlowRowLegend(
    sorted: List<CategoryTotal>,
    totalSpent: Double,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sorted.take(6).forEachIndexed { index, cat ->
            val color = ChartPalette[index % ChartPalette.size]
            val pct = ((cat.total / totalSpent) * 100).toInt()
            val isSelected = selectedIndex == index

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onSelect(index) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${cat.category.split("&")[0].trim()} ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklySpendBarChart(
    expenses: List<Expense>,
    isTelugu: Boolean,
    modifier: Modifier = Modifier
) {
    // Group expenses into the last 7 calendar days
    val cal = Calendar.getInstance()
    val sdfDay = SimpleDateFormat("EEE", Locale.getDefault())

    val daysList = (6 downTo 0).map { offset ->
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -offset)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        val start = c.timeInMillis
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        val end = c.timeInMillis
        val label = sdfDay.format(c.time)
        val daySpent = expenses.filter { it.timestamp in start..end }.sumOf { it.amount }
        Pair(label, daySpent)
    }

    val maxSpend = (daysList.maxOfOrNull { it.second } ?: 1000.0).coerceAtLeast(100.0)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_spend_chart")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = EmeraldPrimaryLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTelugu) "వారం రోజువారీ వ్యయం (7-Day Trend)" else "7-Day Spending Pattern",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Peak: ₹${maxSpend.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                daysList.forEach { (label, amt) ->
                    val ratio = (amt / maxSpend).toFloat().coerceIn(0.04f, 1f)
                    val isPeak = amt == maxSpend && amt > 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (amt > 0) {
                            Text(
                                text = "₹${amt.toInt()}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isPeak) AmberSecondaryLight else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Bar
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((ratio * 85).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (isPeak) AmberSecondaryLight else EmeraldPrimaryLight)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailySafeToSpendCard(
    health: AgentFinancialBrain.LocalFinancialHealth,
    isTelugu: Boolean,
    modifier: Modifier = Modifier
) {
    val isPaceExceeded = health.dailyBurnRate > health.dailySafeToSpend && health.totalSpentThisMonth > 0

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaceExceeded) AlertRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (isPaceExceeded) AlertRed else EmeraldPrimaryLight,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTelugu) "స్థానిక గణన: రోజువారీ సురక్షిత వ్యయం" else "Local Metric: Daily Safe-to-Spend",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPaceExceeded) AlertRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isPaceExceeded) (if (isTelugu) "అధిక వేగం!" else "Fast Pace")
                        else (if (isTelugu) "ఆదర్శం" else "Balanced"),
                        color = if (isPaceExceeded) AlertRed else SuccessGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isTelugu) "సురక్షిత రోజువారీ పరిమితి" else "Safe-to-Spend Daily",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${health.dailySafeToSpend.toInt()}/day",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPaceExceeded) AmberSecondaryLight else EmeraldPrimaryLight
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isTelugu) "ప్రస్తుత సగటు వేగం (Burn Rate)" else "Current Burn Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${health.dailyBurnRate.toInt()}/day",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isPaceExceeded) AlertRed else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isTelugu)
                    "ఈ నెలలో ఇంకా ${health.daysRemainingInMonth} రోజులు మిగిలి ఉన్నాయి. నెలాఖరుకు అంచనా వ్యయం: ₹${health.projectedMonthEndSpend.toInt()}."
                else
                    "${health.daysRemainingInMonth} days remaining this month. Projected month-end spend at this rate: ₹${health.projectedMonthEndSpend.toInt()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PaymentModeBreakdownCard(
    expenses: List<Expense>,
    isTelugu: Boolean,
    modifier: Modifier = Modifier
) {
    val total = expenses.sumOf { it.amount }
    if (total <= 0) return

    val upiTotal = expenses.filter { it.paymentMode.equals("UPI", ignoreCase = true) }.sumOf { it.amount }
    val cashTotal = expenses.filter { it.paymentMode.equals("Cash", ignoreCase = true) }.sumOf { it.amount }
    val cardTotal = expenses.filter { it.paymentMode.equals("Card", ignoreCase = true) || it.paymentMode.contains("Bank", ignoreCase = true) }.sumOf { it.amount }

    val upiPct = ((upiTotal / total) * 100).toInt()
    val cashPct = ((cashTotal / total) * 100).toInt()
    val cardPct = ((cardTotal / total) * 100).toInt()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = EmeraldPrimaryLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTelugu) "చెల్లింపు విధానాలు (Payment Mode Share)" else "Payment Methods Distribution",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                if (upiPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(upiPct.toFloat())
                            .height(10.dp)
                            .background(EmeraldPrimaryLight)
                    )
                }
                if (cardPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(cardPct.toFloat())
                            .height(10.dp)
                            .background(IndigoTertiaryLight)
                    )
                }
                if (cashPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(cashPct.toFloat())
                            .height(10.dp)
                            .background(AmberSecondaryLight)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UPI: ₹${upiTotal.toInt()} ($upiPct%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = EmeraldPrimaryLight
                )
                Text(
                    text = "Card/Net: ₹${cardTotal.toInt()} ($cardPct%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = IndigoTertiaryLight
                )
                Text(
                    text = "Cash: ₹${cashTotal.toInt()} ($cashPct%)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AmberSecondaryLight
                )
            }
        }
    }
}
