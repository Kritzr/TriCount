package com.example.tricount

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.data.entity.ExpenseWithDetails
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Category metadata: emoji + colour (must match EXPENSE_CATEGORIES in AddExpense)
// ─────────────────────────────────────────────────────────────────────────────

private data class CategoryMeta(val emoji: String, val color: Color)

private val CATEGORY_META: Map<String, CategoryMeta> = mapOf(
    "Food & Drinks"  to CategoryMeta("🍔", Color(0xFFFF6B35)),
    "Transport"      to CategoryMeta("🚕", Color(0xFFFFB400)),
    "Accommodation"  to CategoryMeta("🏨", Color(0xFF4ECDC4)),
    "Entertainment"  to CategoryMeta("🎬", Color(0xFFFF6B9D)),
    "Shopping"       to CategoryMeta("🛍️", Color(0xFF9B59B6)),
    "Health"         to CategoryMeta("💊", Color(0xFF2ECC71)),
    "Groceries"      to CategoryMeta("🛒", Color(0xFF27AE60)),
    "Utilities"      to CategoryMeta("⚡", Color(0xFFF39C12)),
    "Travel"         to CategoryMeta("✈️", Color(0xFF3498DB)),
    "Education"      to CategoryMeta("📚", Color(0xFF1ABC9C)),
    "General"        to CategoryMeta("📌", Color(0xFF95A5A6)),
    "No Category"    to CategoryMeta("❓", Color(0xFF7F8C8D)),
)

// Fallback palette for any unexpected category strings
private val FALLBACK_COLORS = listOf(
    Color(0xFF6C5CE7), Color(0xFFE17055), Color(0xFF00B894),
    Color(0xFF74B9FF), Color(0xFFFD79A8), Color(0xFFA29BFE),
    Color(0xFF55EFC4), Color(0xFFFDCB6E), Color(0xFF81ECEC),
)

private fun metaFor(name: String, fallbackIndex: Int): CategoryMeta =
    CATEGORY_META[name] ?: CategoryMeta("❓", FALLBACK_COLORS[fallbackIndex % FALLBACK_COLORS.size])

// ─────────────────────────────────────────────────────────────────────────────
// Internal models
// ─────────────────────────────────────────────────────────────────────────────

enum class InsightPeriod { MONTH, YEAR, ALL }

private data class CategorySlice(
    val name     : String,
    val total    : Double,
    val count    : Int,
    val color    : Color,
    val emoji    : String,
    val fraction : Float   // 0..1
)

// ─────────────────────────────────────────────────────────────────────────────
// Public entry-point composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InsightsContent(
    modifier      : Modifier = Modifier,
    expenses      : List<ExpenseWithDetails>,
    currentUserId : Int
) {
    var period        by remember { mutableStateOf(InsightPeriod.MONTH) }
    var calYear       by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var calMonth      by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var viewMode      by remember { mutableStateOf("group") }  // "group" | "mine"
    var selectedSlice by remember { mutableStateOf<String?>(null) }

    // ── Filter by period + view mode ─────────────────────────────────────────
    val filtered = remember(expenses, period, calYear, calMonth, viewMode) {
        val base = if (viewMode == "mine") expenses.filter { it.paidBy == currentUserId }
        else expenses
        when (period) {
            InsightPeriod.ALL   -> base
            InsightPeriod.YEAR  -> base.filter {
                Calendar.getInstance().also { c -> c.timeInMillis = it.createdAt }
                    .get(Calendar.YEAR) == calYear
            }
            InsightPeriod.MONTH -> base.filter {
                val c = Calendar.getInstance().also { c -> c.timeInMillis = it.createdAt }
                c.get(Calendar.YEAR) == calYear && c.get(Calendar.MONTH) == calMonth
            }
        }
    }

    // ── Build category slices ─────────────────────────────────────────────────
    val slices: List<CategorySlice> = remember(filtered) {
        val grandTotal = filtered.sumOf { it.amount }.coerceAtLeast(0.01)
        filtered
            .groupBy { it.category.ifBlank { "No Category" } }
            .entries
            .sortedByDescending { (_, list) -> list.sumOf { it.amount } }
            .mapIndexed { idx, (cat, list) ->
                val sum  = list.sumOf { it.amount }
                val meta = metaFor(cat, idx)
                CategorySlice(
                    name     = cat,
                    total    = sum,
                    count    = list.size,
                    color    = meta.color,
                    emoji    = meta.emoji,
                    fraction = (sum / grandTotal).toFloat()
                )
            }
    }

    val grandTotal = filtered.sumOf { it.amount }
    val myTotal    = filtered.filter { it.paidBy == currentUserId }.sumOf { it.amount }

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Period tabs
        item { PeriodSelector(period) { period = it; selectedSlice = null } }

        // Date navigator (Month / Year only)
        if (period != InsightPeriod.ALL) {
            item {
                DateNavigator(
                    period  = period,
                    year    = calYear,
                    month   = calMonth,
                    onPrev  = {
                        selectedSlice = null
                        if (period == InsightPeriod.MONTH) {
                            if (calMonth == 0) { calMonth = 11; calYear-- } else calMonth--
                        } else calYear--
                    },
                    onNext  = {
                        selectedSlice = null
                        if (period == InsightPeriod.MONTH) {
                            if (calMonth == 11) { calMonth = 0; calYear++ } else calMonth++
                        } else calYear++
                    }
                )
            }
        }

        // Pie chart card (always shown — shows empty state inside when no data)
        item {
            ChartCard(
                slices        = slices,
                grandTotal    = grandTotal,
                selectedSlice = selectedSlice,
                onSliceClick  = { selectedSlice = if (selectedSlice == it) null else it },
                viewMode      = viewMode,
                onViewToggle  = { viewMode = if (viewMode == "group") "mine" else "group"; selectedSlice = null }
            )
        }

        if (slices.isEmpty()) return@LazyColumn

        // Summary stat chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(modifier = Modifier.weight(1f), icon = "💰", label = "Total",    value = fmt(grandTotal))
                StatChip(modifier = Modifier.weight(1f), icon = "👤", label = "My share", value = fmt(myTotal))
                StatChip(modifier = Modifier.weight(1f), icon = "🧾", label = "Expenses", value = "${filtered.size}")
            }
        }

        // Section header
        item {
            Text(
                "Breakdown by Category",
                fontSize      = 13.sp,
                fontWeight    = FontWeight.SemiBold,
                color         = MaterialTheme.colorScheme.primary,
                modifier      = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
            )
        }

        // Category rows
        items(slices) { slice ->
            CategoryRow(
                slice      = slice,
                isSelected = selectedSlice == null || selectedSlice == slice.name,
                onClick    = { selectedSlice = if (selectedSlice == slice.name) null else slice.name }
            )
        }

        // Top payer card
        if (filtered.isNotEmpty()) {
            item { TopPayerCard(expenses = filtered, currentUserId = currentUserId) }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Period selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodSelector(selected: InsightPeriod, onChange: (InsightPeriod) -> Unit) {
    val opts = listOf(InsightPeriod.MONTH to "Month", InsightPeriod.YEAR to "Year", InsightPeriod.ALL to "All")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        opts.forEach { (p, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected == p) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onChange(p) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize   = 14.sp,
                    fontWeight = if (selected == p) FontWeight.Bold else FontWeight.Normal,
                    color      = if (selected == p) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date navigator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateNavigator(
    period : InsightPeriod,
    year   : Int,
    month  : Int,
    onPrev : () -> Unit,
    onNext : () -> Unit
) {
    val label = if (period == InsightPeriod.MONTH) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
            Calendar.getInstance().also { it.set(year, month, 1) }.time
        )
    } else "$year"

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.widthIn(min = 160.dp),
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.width(16.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Donut / pie chart card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChartCard(
    slices        : List<CategorySlice>,
    grandTotal    : Double,
    selectedSlice : String?,
    onSliceClick  : (String) -> Unit,
    viewMode      : String,
    onViewToggle  : () -> Unit
) {
    val animProgress by animateFloatAsState(
        targetValue   = if (slices.isEmpty()) 0f else 1f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "pie"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Total label
            if (slices.isNotEmpty()) {
                Text(
                    "Total: ${fmt(grandTotal)}",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.fillMaxWidth(),
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Donut chart ──────────────────────────────────────────────
                Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        if (slices.isEmpty()) {
                            // Ghost ring
                            drawArc(
                                color      = Color.LightGray.copy(alpha = 0.25f),
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                style   = Stroke(36.dp.toPx(), cap = StrokeCap.Butt),
                                size    = Size(size.width * .82f, size.height * .82f),
                                topLeft = Offset(size.width * .09f, size.height * .09f)
                            )
                        } else {
                            drawDonut(slices, animProgress, selectedSlice)
                        }
                    }

                    // Centre label
                    if (slices.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "No transactions\nfor this period yet",
                                fontSize  = 12.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Spend to see a breakdown\nof this period's expenses",
                                fontSize  = 10.sp,
                                textAlign = TextAlign.Center,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                    } else {
                        val sel = slices.find { it.name == selectedSlice }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(sel?.emoji ?: "${slices.size}", fontSize = if (sel != null) 22.sp else 24.sp,
                                fontWeight = FontWeight.Bold)
                            if (sel != null) {
                                Text(fmt(sel.total), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${(sel.fraction * 100).toInt()}%", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("categories", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                // ── Legend ───────────────────────────────────────────────────
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slices.take(6).forEach { slice ->
                        val dimmed = selectedSlice != null && selectedSlice != slice.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selectedSlice == slice.name) slice.color.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { onSliceClick(slice.name) }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (dimmed) slice.color.copy(alpha = 0.3f) else slice.color)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                slice.name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                color    = if (dimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (slices.size > 6) {
                        Text("+${slices.size - 6} more", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // "For the Group" / "For Me" toggle
            OutlinedButton(
                onClick  = onViewToggle,
                shape    = RoundedCornerShape(20.dp),
                modifier = Modifier.wrapContentWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (viewMode == "group") "For the Group" else "For Me", fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas donut renderer
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawDonut(
    slices        : List<CategorySlice>,
    progress      : Float,
    selectedSlice : String?
) {
    val stroke  = 36.dp.toPx()
    val padding = stroke / 2 + 6.dp.toPx()
    val diam    = minOf(size.width, size.height) - padding * 2
    val tl      = Offset((size.width - diam) / 2, (size.height - diam) / 2)
    val arcSize = Size(diam, diam)

    var start = -90f
    slices.forEach { slice ->
        val sweep   = 360f * slice.fraction * progress
        val isSelected = selectedSlice == slice.name
        val bump    = if (isSelected) 7.dp.toPx() else 0f

        val mid = Math.toRadians((start + sweep / 2.0))
        val ox  = (Math.cos(mid) * bump).toFloat()
        val oy  = (Math.sin(mid) * bump).toFloat()

        drawArc(
            color      = if (isSelected || selectedSlice == null) slice.color else slice.color.copy(alpha = 0.3f),
            startAngle = start,
            sweepAngle = sweep.coerceAtLeast(0.5f),
            useCenter  = false,
            style      = Stroke(stroke, cap = StrokeCap.Butt),
            size       = arcSize,
            topLeft    = Offset(tl.x + ox, tl.y + oy)
        )
        start += sweep
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category breakdown row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryRow(
    slice      : CategorySlice,
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    val barFraction by animateFloatAsState(
        targetValue   = if (isSelected) slice.fraction else slice.fraction * 0.35f,
        animationSpec = tween(450),
        label         = "bar_${slice.name}"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) slice.color.copy(alpha = 0.07f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape    = RoundedCornerShape(10.dp),
                    color    = slice.color.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(slice.emoji, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(slice.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${slice.count} expense${if (slice.count != 1) "s" else ""}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt(slice.total), fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = slice.color)
                    Text("${(slice.fraction * 100).toInt()}%",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(4.dp)
                    .clip(CircleShape)
                    .background(slice.color.copy(alpha = 0.13f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction).height(4.dp)
                        .clip(CircleShape)
                        .background(slice.color)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top payer card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopPayerCard(expenses: List<ExpenseWithDetails>, currentUserId: Int) {
    val (payerPair, payerExpenses) = expenses
        .groupBy { it.paidBy to it.paidByName }
        .maxByOrNull { it.value.sumOf { e -> e.amount } } ?: return
    val (payerId, payerName) = payerPair
    val total  = payerExpenses.sumOf { it.amount }
    val isMe   = payerId == currentUserId

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏆", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Top Payer", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
                Text(if (isMe) "You ($payerName)" else payerName,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${payerExpenses.size} expense${if (payerExpenses.size != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
            }
            Text(fmt(total), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(modifier: Modifier, icon: String, label: String, value: String) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier            = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, fontSize = 10.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Amount formatter  (currency-agnostic — shows raw number)
// ─────────────────────────────────────────────────────────────────────────────

private fun fmt(amount: Double): String =
    if (amount >= 1_000) String.format("%.0f", amount)
    else String.format("%.2f", amount)