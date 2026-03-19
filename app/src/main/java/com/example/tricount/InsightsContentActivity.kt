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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tricount.data.entity.ExpenseWithDetails
import java.text.SimpleDateFormat
import java.util.*

// =============================================================================
// Category metadata
// =============================================================================

private data class CategoryMeta(val icon: ImageVector, val color: Color)

private val FALLBACK_COLORS = listOf(
    Color(0xFF6C5CE7), Color(0xFFE17055), Color(0xFF00B894),
    Color(0xFF74B9FF), Color(0xFFFD79A8), Color(0xFFA29BFE),
    Color(0xFF55EFC4), Color(0xFFFDCB6E),
)

private fun catMeta(name: String): CategoryMeta = when (name) {
    "Food & Drinks"  -> CategoryMeta(Icons.Filled.Restaurant,     Color(0xFFFF6B35))
    "Transport"      -> CategoryMeta(Icons.Filled.DirectionsCar,  Color(0xFFFFB400))
    "Accommodation"  -> CategoryMeta(Icons.Filled.Hotel,          Color(0xFF4ECDC4))
    "Entertainment"  -> CategoryMeta(Icons.Filled.Movie,       Color(0xFFFF6B9D))
    "Shopping"       -> CategoryMeta(Icons.Filled.ShoppingBag,    Color(0xFF9B59B6))
    "Health"         -> CategoryMeta(Icons.Filled.LocalHospital,Color(0xFF2ECC71))
    "Groceries"      -> CategoryMeta(Icons.Filled.ShoppingCart,   Color(0xFF27AE60))
    "Utilities"      -> CategoryMeta(Icons.Filled.Bolt,   Color(0xFFF39C12))
    "Travel"         -> CategoryMeta(Icons.Filled.Flight,         Color(0xFF3498DB))
    "Education"      -> CategoryMeta(Icons.Filled.School,         Color(0xFF1ABC9C))
    "General"        -> CategoryMeta(Icons.Filled.PushPin,        Color(0xFF95A5A6))
    "No Category"    -> CategoryMeta(Icons.Filled.HelpOutline,    Color(0xFF7F8C8D))
    else             -> CategoryMeta(Icons.Filled.Category,       Color(0xFF7F8C8D))
}

private fun catColor(name: String, idx: Int): Color =
    catMeta(name).color.takeIf { it != Color(0xFF7F8C8D) }
        ?: FALLBACK_COLORS[idx % FALLBACK_COLORS.size]

// =============================================================================
// Models
// =============================================================================

private enum class InsightPeriod { MONTH, YEAR, ALL }

private data class CategorySlice(
    val name     : String,
    val total    : Double,
    val count    : Int,
    val color    : Color,
    val icon     : androidx.compose.ui.graphics.vector.ImageVector,
    val fraction : Float
)

// =============================================================================
// Entry point
// =============================================================================

@Composable
fun InsightsContent(
    modifier      : Modifier = Modifier,
    expenses      : List<ExpenseWithDetails>,
    currentUserId : Int
) {
    var period        by remember { mutableStateOf(InsightPeriod.MONTH) }
    var calYear       by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var calMonth      by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var viewMode      by remember { mutableStateOf("group") }
    var selectedSlice by remember { mutableStateOf<String?>(null) }

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

    val slices: List<CategorySlice> = remember(filtered) {
        val total = filtered.sumOf { it.amount }.coerceAtLeast(0.01)
        filtered
            .groupBy { it.category.ifBlank { "No Category" } }
            .entries
            .sortedByDescending { (_, l) -> l.sumOf { it.amount } }
            .mapIndexed { idx, (cat, list) ->
                val sum = list.sumOf { it.amount }
                CategorySlice(
                    name     = cat,
                    total    = sum,
                    count    = list.size,
                    color    = catColor(cat, idx),
                    icon     = catMeta(cat).icon,
                    fraction = (sum / total).toFloat()
                )
            }
    }

    val grandTotal = filtered.sumOf { it.amount }
    val myTotal    = filtered.filter { it.paidBy == currentUserId }.sumOf { it.amount }

    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        // ── Period tabs ───────────────────────────────────────────────────────
        item {
            InsightPeriodTabs(period) { period = it; selectedSlice = null }
        }

        // ── Date navigator ────────────────────────────────────────────────────
        if (period != InsightPeriod.ALL) {
            item {
                InsightDateNav(
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

        // ── Chart card ────────────────────────────────────────────────────────
        item {
            InsightChartCard(
                slices        = slices,
                grandTotal    = grandTotal,
                selectedSlice = selectedSlice,
                onSliceClick  = { selectedSlice = if (selectedSlice == it) null else it },
                viewMode      = viewMode,
                onViewToggle  = { viewMode = if (viewMode == "group") "mine" else "group"; selectedSlice = null }
            )
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (slices.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.SearchOff, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No expenses this period",
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add expenses to see spending insights here",
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // ── Summary stat chips ────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightStatChip(Modifier.weight(1f), Icons.Filled.Payments,   "Total",    fmtAmt(grandTotal))
                InsightStatChip(Modifier.weight(1f), Icons.Filled.Person,      "My share", fmtAmt(myTotal))
                InsightStatChip(Modifier.weight(1f), Icons.Filled.Receipt,     "Expenses", "${filtered.size}")
            }
        }

        // ── Category section header + rows in one grouped card ─────────────
        item {
            Text(
                "Category Breakdown",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column {
                    slices.forEachIndexed { idx, slice ->
                        InsightCategoryRow(
                            slice      = slice,
                            isSelected = selectedSlice == null || selectedSlice == slice.name,
                            onClick    = { selectedSlice = if (selectedSlice == slice.name) null else slice.name }
                        )
                        if (idx < slices.lastIndex) {
                            HorizontalDivider(
                                modifier  = Modifier.padding(start = 68.dp, end = 16.dp),
                                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }

        // ── Top payer card ────────────────────────────────────────────────────
        if (filtered.isNotEmpty()) {
            item { InsightTopPayerCard(filtered, currentUserId) }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// =============================================================================
// Period tabs
// =============================================================================

@Composable
private fun InsightPeriodTabs(selected: InsightPeriod, onChange: (InsightPeriod) -> Unit) {
    val opts = listOf(InsightPeriod.MONTH to "Month", InsightPeriod.YEAR to "Year", InsightPeriod.ALL to "All")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        shape    = RoundedCornerShape(14.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(Modifier.padding(4.dp)) {
            opts.forEach { (p, label) ->
                val sel = selected == p
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChange(p) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    shadowElevation = if (sel) 3.dp else 0.dp
                ) {
                    Text(
                        label,
                        modifier   = Modifier.padding(vertical = 10.dp),
                        fontSize   = 14.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color      = if (sel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =============================================================================
// Date navigator
// =============================================================================

@Composable
private fun InsightDateNav(
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
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        FilledTonalIconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ChevronLeft, null, modifier = Modifier.size(18.dp))
        }
        Text(
            label,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier
                .widthIn(min = 170.dp)
                .padding(horizontal = 8.dp),
            textAlign  = TextAlign.Center
        )
        FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp))
        }
    }
}

// =============================================================================
// Chart card
// =============================================================================

@Composable
private fun InsightChartCard(
    slices        : List<CategorySlice>,
    grandTotal    : Double,
    selectedSlice : String?,
    onSliceClick  : (String) -> Unit,
    viewMode      : String,
    onViewToggle  : () -> Unit
) {
    val animProg by animateFloatAsState(
        targetValue   = if (slices.isEmpty()) 0f else 1f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "pie_anim"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {

            // Total
            if (slices.isNotEmpty()) {
                Text(
                    "Total: ${fmtAmt(grandTotal)}",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign  = TextAlign.Center
                )
            }

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut chart
                Box(Modifier.size(168.dp), Alignment.Center) {
                    Canvas(Modifier.size(168.dp)) {
                        if (slices.isEmpty()) {
                            drawArc(
                                color      = Color.LightGray.copy(alpha = 0.2f),
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                style      = Stroke(38.dp.toPx(), cap = StrokeCap.Butt),
                                size       = Size(size.width * .84f, size.height * .84f),
                                topLeft    = Offset(size.width * .08f, size.height * .08f)
                            )
                        } else {
                            drawInsightDonut(slices, animProg, selectedSlice)
                        }
                    }
                    // Centre label
                    if (slices.isNotEmpty()) {
                        val sel = slices.find { it.name == selectedSlice }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (sel != null) {
                                Icon(sel.icon, null,
                                    tint     = sel.color,
                                    modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    fmtAmt(sel.total),
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = sel.color
                                )
                                Text(
                                    "${(sel.fraction * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "${slices.size}",
                                    fontSize   = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "categories",
                                    fontSize = 11.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            "No data",
                            fontSize  = 12.sp,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Legend
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slices.take(6).forEach { slice ->
                        val dimmed = selectedSlice != null && selectedSlice != slice.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedSlice == slice.name) slice.color.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { onSliceClick(slice.name) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (dimmed) slice.color.copy(alpha = 0.25f)
                                        else slice.color
                                    )
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                slice.name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                color    = if (dimmed)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (slices.size > 6) {
                        Text(
                            "+${slices.size - 6} more",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // For the Group / For Me toggle
            Surface(
                shape  = RoundedCornerShape(50),
                color  = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.wrapContentWidth()
            ) {
                Row(
                    modifier          = Modifier
                        .clickable(onClick = onViewToggle)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (viewMode == "group") Icons.Filled.Group else Icons.Filled.Person,
                        null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (viewMode == "group") "For the Group" else "For Me",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.ArrowDropDown, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Donut renderer
// =============================================================================

private fun DrawScope.drawInsightDonut(
    slices        : List<CategorySlice>,
    progress      : Float,
    selectedSlice : String?
) {
    val stroke  = 38.dp.toPx()
    val pad     = stroke / 2 + 6.dp.toPx()
    val diam    = minOf(size.width, size.height) - pad * 2
    val tl      = Offset((size.width - diam) / 2, (size.height - diam) / 2)
    val arcSize = Size(diam, diam)
    var start   = -90f

    slices.forEach { slice ->
        val sweep = 360f * slice.fraction * progress
        val isSel = selectedSlice == slice.name
        val bump  = if (isSel) 8.dp.toPx() else 0f
        val mid   = Math.toRadians((start + sweep / 2.0))
        val ox    = (Math.cos(mid) * bump).toFloat()
        val oy    = (Math.sin(mid) * bump).toFloat()

        drawArc(
            color      = if (isSel || selectedSlice == null) slice.color
            else slice.color.copy(alpha = 0.25f),
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

// =============================================================================
// Category row
// =============================================================================

@Composable
private fun InsightCategoryRow(
    slice      : CategorySlice,
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    val barAnim by animateFloatAsState(
        targetValue   = if (isSelected) slice.fraction else slice.fraction * 0.35f,
        animationSpec = tween(500),
        label         = "bar_${slice.name}"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) slice.color.copy(alpha = 0.06f)
                else            Color.Transparent
            )
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon badge
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape    = RoundedCornerShape(12.dp),
                    color    = slice.color.copy(alpha = 0.15f)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(
                            slice.icon, null,
                            tint     = slice.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        slice.name,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${slice.count} expense${if (slice.count != 1) "s" else ""}",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        fmtAmt(slice.total),
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = slice.color
                    )
                    Text(
                        "${(slice.fraction * 100).toInt()}%",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(slice.color.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barAnim)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(slice.color, slice.color.copy(alpha = 0.6f))
                            )
                        )
                )
            }
        }
    }
}

// =============================================================================
// Top payer card
// =============================================================================

@Composable
private fun InsightTopPayerCard(
    expenses      : List<ExpenseWithDetails>,
    currentUserId : Int
) {
    val topEntry = expenses
        .groupBy { it.paidBy to it.paidByName }
        .maxByOrNull { it.value.sumOf { e -> e.amount } } ?: return

    val (pair, payerExpenses) = topEntry
    val (payerId, payerName)  = pair
    val total = payerExpenses.sumOf { it.amount }
    val isMe  = payerId == currentUserId

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trophy icon circle
            Surface(
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(
                        Icons.Filled.Star, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "Top Payer",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (isMe) "You ($payerName)" else payerName,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${payerExpenses.size} expense${if (payerExpenses.size != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                )
            }

            Text(
                fmtAmt(total),
                fontSize   = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// =============================================================================
// Stat chip
// =============================================================================

@Composable
private fun InsightStatChip(
    modifier : Modifier,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    value    : String
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize   = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1,
                textAlign  = TextAlign.Center
            )
            Text(
                label,
                fontSize  = 10.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private fun fmtAmt(v: Double): String =
    if (v >= 1_000) "\u20B9${String.format("%.0f", v)}"
    else "\u20B9${String.format("%.2f", v)}"