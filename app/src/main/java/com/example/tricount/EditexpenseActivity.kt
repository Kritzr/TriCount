package com.example.tricount

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tricount.data.SessionManager
import com.example.tricount.data.entity.ExpenseSplitWithUser
import com.example.tricount.data.entity.ExpenseWithDetails
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddExpenseResult
import com.example.tricount.viewModel.TricountViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class EditExpenseActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expenseId      = intent.getIntExtra(EXTRA_EXPENSE_ID,  -1)
        val tricountId     = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName   = intent.getStringExtra(EXTRA_TRICOUNT_NAME) ?: "Tricount"
        val splitModeExtra = intent.getStringExtra(EXTRA_SPLIT_MODE)
            ?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() }

        if (expenseId == -1 || tricountId == -1) { finish(); return }

        val sessionManager = SessionManager(this)
        AppTheme.isDark.value = sessionManager.getDarkMode()

        setContent {
            TriCountTheme {
                LaunchedEffect(tricountId) {
                    viewModel.loadTricountDetails(tricountId)
                    viewModel.loadExpenses(tricountId)
                }

                val expenses      by viewModel.expenses.collectAsStateWithLifecycle()
                val members       by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val expenseSplits by viewModel.expenseSplits.collectAsStateWithLifecycle()
                val currentUserId = sessionManager.getUserId() ?: -1

                val expense = remember(expenses, expenseId) {
                    expenses.find { it.id == expenseId }
                }

                // splits for this specific expense (list of ExpenseSplitWithUser)
                val splits = remember(expenseSplits, expenseId) {
                    expenseSplits[expenseId] ?: emptyList()
                }

                if (expense != null && members.isNotEmpty()) {
                    EditExpenseScreen(
                        expense           = expense,
                        splits            = splits,
                        tricountId        = tricountId,
                        tricountName      = tricountName,
                        members           = members,
                        currentUserId     = currentUserId,
                        viewModel         = viewModel,
                        initialSplitMode  = splitModeExtra,
                        onBackClick       = { finish() },
                        onSaved           = {
                            setResult(android.app.Activity.RESULT_OK)
                            finish()
                        }
                    )
                } else {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    companion object {
        const val EXTRA_EXPENSE_ID    = "extra_edit_expense_id"
        const val EXTRA_TRICOUNT_ID   = "extra_edit_tricount_id"
        const val EXTRA_TRICOUNT_NAME = "extra_edit_tricount_name"
        const val EXTRA_SPLIT_MODE    = "extra_edit_split_mode"   // "EQUALLY" | "PERCENTAGE" | "PARTS"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detect the original split mode from the saved split amounts.
//
// ExpenseSplitWithUser.amount = the actual INR amount each member owes.
// We can detect the original mode by checking if all amounts are equal
// (EQUALLY), or if they differ (PERCENTAGE — we'll express the current
// split as percentages so the user can see and adjust them).
//
// PARTS is indistinguishable from PERCENTAGE at display-time using only
// amounts, so we always restore as PERCENTAGE (most informative).
// The user can freely switch to PARTS if they want.
// ─────────────────────────────────────────────────────────────────────────────

fun detectSplitModeFromAmounts(splits: List<ExpenseSplitWithUser>): SplitMode {
    if (splits.isEmpty()) return SplitMode.EQUALLY
    // Use raw shares (integer) — more reliable than computed INR amounts
    val firstShares = splits.first().shares
    return if (splits.all { it.shares == firstShares }) SplitMode.EQUALLY
    else SplitMode.PARTS  // unequal integer shares → restore as PARTS
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    expense          : ExpenseWithDetails,
    splits           : List<ExpenseSplitWithUser>,
    tricountId       : Int,
    tricountName     : String,
    members          : List<MemberWithDetails>,
    currentUserId    : Int,
    viewModel        : TricountViewModel,
    initialSplitMode : SplitMode? = null,   // passed from intent; null = auto-detect
    onBackClick      : () -> Unit,
    onSaved          : () -> Unit
) {
    val context = LocalContext.current

    // Map userId → raw shares (integer from DB) — source of truth for PARTS/EQUALLY
    val splitSharesMap = remember(splits) {
        splits.associate { it.userId to it.shares }
    }
    // Map userId → computed INR amount — used for PERCENTAGE restoration
    val splitAmountMap = remember(splits) {
        splits.associate { it.userId to it.amount }
    }

    // Prefer the mode explicitly passed from intent; fall back to
    // detecting from shares (all equal = EQUALLY, else PERCENTAGE).
    val detectedMode = remember(splits, initialSplitMode) {
        initialSplitMode ?: run {
            if (splits.isEmpty()) SplitMode.EQUALLY
            else {
                val firstShares = splits.first().shares
                if (splits.all { it.shares == firstShares }) SplitMode.EQUALLY
                else SplitMode.PARTS   // raw shares are integers — restore as PARTS, not %
            }
        }
    }

    // ── Form state ────────────────────────────────────────────────────────────
    var expenseName      by remember { mutableStateOf(expense.name) }
    var amountText       by remember { mutableStateOf("%.2f".format(expense.amount)) }
    var description      by remember { mutableStateOf(expense.description) }
    val selectedCurrency = CURRENCIES.first { it.code == "INR" }
    var selectedPayerId  by remember { mutableStateOf(expense.paidBy) }
    var splitMode        by remember { mutableStateOf(detectedMode) }
    var isLoading        by remember { mutableStateOf(false) }
    var payerExpanded    by remember { mutableStateOf(false) }

    // ── Split inputs pre-populated from existing DB data ────────────────────
    // EQUALLY    → "1" for everyone (raw shares are all equal, display is uniform).
    // PARTS      → the raw integer shares straight from the DB (e.g. 2, 3, 1).
    // PERCENTAGE → recompute each member's % from the total shares in the DB.
    //              e.g. shares [2, 3] on a 5-share split → 40% and 60%.
    val splitInputs = remember(members, detectedMode, splitSharesMap, splitAmountMap, expense.amount) {
        val totalShares = splitSharesMap.values.sum().coerceAtLeast(1)
        mutableStateMapOf<Int, String>().also { map ->
            members.forEach { member ->
                val memberShares = splitSharesMap[member.userId]
                val memberAmount = splitAmountMap[member.userId]
                map[member.userId] = when (detectedMode) {
                    SplitMode.EQUALLY -> "1"
                    SplitMode.PARTS -> {
                        // Use the exact integer shares saved in the DB
                        memberShares?.coerceAtLeast(1)?.toString() ?: "1"
                    }
                    SplitMode.PERCENTAGE -> {
                        // Derive % from shares ratio (most accurate — avoids
                        // floating-point drift from the stored INR amounts)
                        if (memberShares != null && memberShares > 0) {
                            val pct = memberShares.toDouble() / totalShares * 100.0
                            "%.1f".format(pct)
                        } else if (memberAmount != null && expense.amount > 0) {
                            // Fallback: derive from stored INR amount
                            val pct = (memberAmount / expense.amount) * 100.0
                            "%.1f".format(pct)
                        } else ""
                    }
                }
            }
        }
    }

    // Guard: prevent the reset LaunchedEffect from firing on first composition
    // and overwriting the pre-populated values restored above.
    var isFirstComposition by remember { mutableStateOf(true) }

    // When the user manually switches split mode AFTER opening the screen,
    // reset all inputs to blank/default so they start fresh.
    LaunchedEffect(splitMode) {
        if (isFirstComposition) { isFirstComposition = false; return@LaunchedEffect }
        members.forEach { member ->
            splitInputs[member.userId] = when (splitMode) {
                SplitMode.EQUALLY    -> "1"
                SplitMode.PERCENTAGE -> ""
                SplitMode.PARTS      -> "1"
            }
        }
    }

    // ── Autofill: tracks which members the user has manually edited ──────────
    // Members NOT in this set are "auto" slots whose value is recalculated
    // every time any manual member's percentage/parts value changes.
    val manuallyEditedIds = remember { mutableStateMapOf<Int, Boolean>() }
    // Clear manual tracking whenever the split mode changes
    LaunchedEffect(splitMode) { manuallyEditedIds.clear() }


    // ── Validation ────────────────────────────────────────────────────────────
    val amountValue       = amountText.toDoubleOrNull()
    val isAmountValid     = amountValue != null && amountValue > 0
    val isNameValid       = expenseName.isNotBlank()
    val percentageTotal   = if (splitMode == SplitMode.PERCENTAGE)
        members.sumOf { splitInputs[it.userId]?.toDoubleOrNull() ?: 0.0 } else 0.0
    val isPercentageValid = splitMode != SplitMode.PERCENTAGE ||
            (percentageTotal >= 99.9 && percentageTotal <= 100.1)
    val canSave           = isNameValid && isAmountValid && isPercentageValid && !isLoading

    // ── Save ──────────────────────────────────────────────────────────────────
    fun save() {
        if (!canSave) return
        val rawAmount   = amountValue!!
        // Edit screen always locks currency to INR, so no conversion needed.
        val amountInInr = rawAmount

        val sharesMap: Map<Int, Int> = when (splitMode) {
            SplitMode.EQUALLY    -> members.associate { it.userId to 1 }
            SplitMode.PERCENTAGE -> members.associate { m ->
                m.userId to ((splitInputs[m.userId]?.toDoubleOrNull() ?: 0.0) * 100).toInt()
            }.filter { it.value > 0 }
            SplitMode.PARTS      -> members.associate { m ->
                m.userId to (splitInputs[m.userId]?.toIntOrNull() ?: 0)
            }.filter { it.value > 0 }
        }

        if (sharesMap.isEmpty()) {
            Toast.makeText(context, "At least one member must have a share", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        viewModel.deleteExpense(expense.id, tricountId)
        viewModel.addExpense(
            tricountId  = tricountId,
            name        = expenseName.trim(),
            description = description.trim(),
            amount      = amountInInr,
            paidBy      = selectedPayerId,
            sharesMap   = sharesMap
        ) { result ->
            isLoading = false
            when (result) {
                is AddExpenseResult.Success -> {
                    Toast.makeText(context, "Expense updated!", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
                is AddExpenseResult.Error ->
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit Expense", fontWeight = FontWeight.Bold)
                        Text(
                            tricountName, fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = canSave) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Expense Details ──────────────────────────────────────────────
            item {
                EditSectionCard(title = "Expense Details") {

                    OutlinedTextField(
                        value           = expenseName,
                        onValueChange   = { expenseName = it },
                        label           = { Text("Expense Name *") },
                        placeholder     = { Text("e.g. Dinner, Hotel, Taxi") },
                        leadingIcon     = { Icon(Icons.Filled.ShoppingCart, null) },
                        modifier        = Modifier.fillMaxWidth(),
                        singleLine      = true,
                        isError         = expenseName.isEmpty(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        enabled         = !isLoading
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Currency locked to INR for edits
                        OutlinedTextField(
                            value         = "${selectedCurrency.flag} ${selectedCurrency.code}",
                            onValueChange = {},
                            readOnly      = true,
                            modifier      = Modifier.width(108.dp),
                            singleLine    = true,
                            enabled       = false
                        )
                        OutlinedTextField(
                            value           = amountText,
                            onValueChange   = { v ->
                                if (v.isEmpty() || v.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?\$")))
                                    amountText = v
                            },
                            label           = { Text("Amount *") },
                            placeholder     = { Text("0.00") },
                            modifier        = Modifier.weight(1f),
                            singleLine      = true,
                            isError         = amountText.isNotEmpty() && !isAmountValid,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction    = ImeAction.Next
                            ),
                            enabled         = !isLoading
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value           = description,
                        onValueChange   = { description = it },
                        label           = { Text("Description (Optional)") },
                        leadingIcon     = { Icon(Icons.Filled.Notes, null) },
                        modifier        = Modifier.fillMaxWidth(),
                        minLines        = 2,
                        maxLines        = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        enabled         = !isLoading
                    )
                }
            }

            // ── Paid By ──────────────────────────────────────────────────────
            item {
                EditSectionCard(title = "Paid By") {
                    ExposedDropdownMenuBox(
                        expanded         = payerExpanded,
                        onExpandedChange = { payerExpanded = it && !isLoading }
                    ) {
                        val payer = members.find { it.userId == selectedPayerId }
                        OutlinedTextField(
                            value         = when {
                                payer == null                 -> "Select payer"
                                payer.userId == currentUserId -> "You (${payer.name})"
                                else                          -> payer.name
                            },
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Who paid?") },
                            leadingIcon   = {
                                payer?.let {
                                    Surface(
                                        modifier = Modifier.size(28.dp),
                                        shape    = CircleShape,
                                        color    = if (it.userId == currentUserId)
                                            colorScheme.primaryContainer
                                        else colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                it.name.first().uppercase(),
                                                fontSize   = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color      = if (it.userId == currentUserId)
                                                    colorScheme.onPrimaryContainer
                                                else colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            },
                            trailingIcon  = {
                                ExposedDropdownMenuDefaults.TrailingIcon(payerExpanded)
                            },
                            modifier      = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            singleLine    = true,
                            enabled       = !isLoading
                        )
                        ExposedDropdownMenu(
                            expanded         = payerExpanded,
                            onDismissRequest = { payerExpanded = false }
                        ) {
                            members.forEach { member ->
                                val isCurrentUser = member.userId == currentUserId
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.size(32.dp),
                                                shape    = CircleShape,
                                                color    = if (isCurrentUser)
                                                    colorScheme.primaryContainer
                                                else colorScheme.secondaryContainer
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        member.name.first().uppercase(),
                                                        fontSize   = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    if (isCurrentUser) "You (${member.name})"
                                                    else member.name,
                                                    fontWeight = if (isCurrentUser)
                                                        FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    member.email,
                                                    fontSize = 11.sp,
                                                    color    = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick      = {
                                        selectedPayerId = member.userId
                                        payerExpanded   = false
                                    },
                                    trailingIcon = {
                                        if (selectedPayerId == member.userId)
                                            Icon(
                                                Icons.Filled.Check, null,
                                                tint = colorScheme.primary
                                            )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Split ────────────────────────────────────────────────────────
            item {
                EditSectionCard(title = "Split") {

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SplitMode.values().forEach { mode ->
                            val label = when (mode) {
                                SplitMode.EQUALLY    -> "Equally"
                                SplitMode.PERCENTAGE -> "Percentage"
                                SplitMode.PARTS      -> "By Parts"
                            }
                            val icon = when (mode) {
                                SplitMode.EQUALLY    -> Icons.Filled.Balance
                                SplitMode.PERCENTAGE -> Icons.Filled.Percent
                                SplitMode.PARTS      -> Icons.Filled.PieChart
                            }
                            FilterChip(
                                selected    = splitMode == mode,
                                onClick     = { splitMode = mode },
                                label       = { Text(label, fontSize = 12.sp) },
                                leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
                                modifier    = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        when (splitMode) {
                            SplitMode.EQUALLY    -> "The expense will be split equally among all members."
                            SplitMode.PERCENTAGE -> "Enter a percentage for each member. Must total 100%."
                            SplitMode.PARTS      -> "Assign parts (e.g. 1 & 2 → one owes ⅓, other ⅔)."
                        },
                        fontSize = 12.sp,
                        color    = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    members.forEach { member ->
                        val isCurrentUser = member.userId == currentUserId
                        val amount        = amountText.toDoubleOrNull() ?: 0.0
                        val preview: Double? = when (splitMode) {
                            SplitMode.EQUALLY    ->
                                if (amount > 0) amount / members.size else null
                            SplitMode.PERCENTAGE -> {
                                val pct = splitInputs[member.userId]?.toDoubleOrNull()
                                if (pct != null && amount > 0) amount * pct / 100.0 else null
                            }
                            SplitMode.PARTS      -> {
                                val parts      = splitInputs[member.userId]?.toIntOrNull() ?: 0
                                val totalParts = members
                                    .sumOf { splitInputs[it.userId]?.toIntOrNull() ?: 0 }
                                    .coerceAtLeast(1)
                                if (amount > 0 && parts > 0)
                                    amount * parts.toDouble() / totalParts else null
                            }
                        }
                        EditSplitMemberRow(
                            member         = member,
                            isCurrentUser  = isCurrentUser,
                            splitMode      = splitMode,
                            inputValue     = splitInputs[member.userId] ?: "",
                            previewAmount  = preview,
                            currencySymbol = selectedCurrency.symbol,
                            isLoading      = isLoading,
                            onInputChange  = { v -> splitInputs[member.userId] = v }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Percentage validation banner
                    if (splitMode == SplitMode.PERCENTAGE && percentageTotal > 0.0) {
                        val color = if (isPercentageValid) Color(0xFF2E7D32)
                        else Color(0xFFC62828)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(8.dp),
                            color    = color.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier          = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isPercentageValid) Icons.Filled.CheckCircle
                                    else Icons.Filled.Warning,
                                    null,
                                    tint     = color,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isPercentageValid) "Total: 100% ✓"
                                    else "Total: ${"%.1f".format(percentageTotal)}% — must equal 100%",
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = color
                                )
                            }
                        }
                    }
                }
            }

            // ── Save button ──────────────────────────────────────────────────
            item {
                Button(
                    onClick  = ::save,
                    enabled  = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Saving…")
                    } else {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-member split row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditSplitMemberRow(
    member         : MemberWithDetails,
    isCurrentUser  : Boolean,
    splitMode      : SplitMode,
    inputValue     : String,
    previewAmount  : Double?,
    currencySymbol : String,
    isLoading      : Boolean,
    onInputChange  : (String) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape    = CircleShape,
            color    = if (isCurrentUser) colorScheme.primaryContainer
            else colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    member.name.first().uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = if (isCurrentUser) colorScheme.onPrimaryContainer
                    else colorScheme.onSecondaryContainer
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isCurrentUser) "You" else member.name,
                fontSize   = 14.sp,
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
            )
            if (previewAmount != null) {
                Text(
                    "≈ $currencySymbol${"%.2f".format(previewAmount)}",
                    fontSize = 11.sp,
                    color    = colorScheme.primary
                )
            }
        }

        when (splitMode) {
            SplitMode.EQUALLY -> {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        "Equal share", fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color    = colorScheme.primary
                    )
                }
            }
            SplitMode.PERCENTAGE -> {
                OutlinedTextField(
                    value           = inputValue,
                    onValueChange   = { v ->
                        if (v.isEmpty() || v.matches(Regex("^\\d{0,3}(\\.\\d{0,1})?\$")))
                            onInputChange(v)
                    },
                    modifier        = Modifier.width(90.dp),
                    singleLine      = true,
                    suffix          = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled         = !isLoading
                )
            }
            SplitMode.PARTS -> {
                OutlinedTextField(
                    value           = inputValue,
                    onValueChange   = { v ->
                        if (v.isEmpty() || v.all { it.isDigit() }) onInputChange(v)
                    },
                    modifier        = Modifier.width(90.dp),
                    singleLine      = true,
                    suffix          = { Text("pt") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled         = !isLoading
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditSectionCard(
    title   : String,
    content : @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                title,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = colorScheme.primary,
                modifier   = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}