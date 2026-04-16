package com.example.tricount

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.tricount.data.entity.MemberWithDetails
import com.example.tricount.ui.theme.AppTheme
import com.example.tricount.ui.theme.TriCountTheme
import com.example.tricount.viewModel.AddExpenseResult
import com.example.tricount.viewModel.TricountViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Supported currencies
// ─────────────────────────────────────────────────────────────────────────────

data class Currency(val code: String, val symbol: String, val name: String, val flag: String)

val CURRENCIES = listOf(
    Currency("USD", "$",   "US Dollar",         "🇺🇸"),
    Currency("EUR", "€",   "Euro",              "🇪🇺"),
    Currency("GBP", "£",   "British Pound",     "🇬🇧"),
    Currency("INR", "₹",   "Indian Rupee",      "🇮🇳"),
    Currency("JPY", "¥",   "Japanese Yen",      "🇯🇵"),
    Currency("CAD", "C$",  "Canadian Dollar",   "🇨🇦"),
    Currency("AUD", "A$",  "Australian Dollar", "🇦🇺"),
    Currency("CHF", "Fr",  "Swiss Franc",       "🇨🇭"),
    Currency("SGD", "S$",  "Singapore Dollar",  "🇸🇬"),
    Currency("AED", "د.إ", "UAE Dirham",        "🇦🇪"),
)

// ─────────────────────────────────────────────────────────────────────────────
// Exchange rates → INR (approximate fixed rates; replace with live API if needed)
// ─────────────────────────────────────────────────────────────────────────────

val EXCHANGE_RATES_TO_INR = mapOf(
    "USD" to 83.50,
    "EUR" to 90.20,
    "GBP" to 105.60,
    "INR" to 1.0,
    "JPY" to 0.56,
    "CAD" to 61.80,
    "AUD" to 54.30,
    "CHF" to 94.10,
    "SGD" to 62.50,
    "AED" to 22.73,
)

fun convertToInr(amount: Double, fromCurrency: String): Double {
    val rate = EXCHANGE_RATES_TO_INR[fromCurrency] ?: 1.0
    return amount * rate
}

// ─────────────────────────────────────────────────────────────────────────────
// Split mode
// ─────────────────────────────────────────────────────────────────────────────

enum class SplitMode { EQUALLY, PERCENTAGE, PARTS }

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class AddExpenseActivity : ComponentActivity() {

    private val viewModel: TricountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tricountId     = intent.getIntExtra(EXTRA_TRICOUNT_ID, -1)
        val tricountName   = intent.getStringExtra(EXTRA_TRICOUNT_NAME) ?: "Tricount"
        val sessionManager = SessionManager(this)

        if (tricountId == -1) { finish(); return }

        AppTheme.isDark.value = sessionManager.getDarkMode()
        setContent {
            TriCountTheme {
                LaunchedEffect(tricountId) { viewModel.loadTricountDetails(tricountId) }

                val members       by viewModel.tricountMembers.collectAsStateWithLifecycle()
                val currentUserId = sessionManager.getUserId() ?: -1

                AddExpenseScreen(
                    tricountId    = tricountId,
                    tricountName  = tricountName,
                    members       = members,
                    currentUserId = currentUserId,
                    viewModel     = viewModel,
                    onBackClick   = { finish() },
                    onSaved       = { addedAt ->
                        val data = android.content.Intent().apply {
                            putExtra(EXTRA_NEW_EXPENSE_ID, addedAt)
                        }
                        setResult(android.app.Activity.RESULT_OK, data)
                        finish()
                    }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    companion object {
        const val EXTRA_TRICOUNT_ID    = "extra_tricount_id"
        const val EXTRA_TRICOUNT_NAME  = "extra_tricount_name"
        const val EXTRA_NEW_EXPENSE_ID = "extra_new_expense_added_at"  // Long: System.currentTimeMillis() when expense was saved
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    tricountId    : Int,
    tricountName  : String,
    members       : List<MemberWithDetails>,
    currentUserId : Int,
    viewModel     : TricountViewModel,
    onBackClick   : () -> Unit,
    onSaved       : (addedAt: Long) -> Unit
) {
    val context = LocalContext.current

    // ── Form state ────────────────────────────────────────────────────────────
    var expenseName      by remember { mutableStateOf("") }
    var amountText       by remember { mutableStateOf("") }
    var description      by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf(CURRENCIES.first { it.code == "INR" }) }
    var selectedPayerId  by remember { mutableStateOf(currentUserId) }
    var splitMode        by remember { mutableStateOf(SplitMode.EQUALLY) }
    var isLoading        by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var payerExpanded    by remember { mutableStateOf(false) }

    val splitInputs = remember(members) {
        mutableStateMapOf<Int, String>().also { map ->
            members.forEach { map[it.userId] = "1" }
        }
    }
    LaunchedEffect(splitMode) {
        members.forEach {
            splitInputs[it.userId] = if (splitMode == SplitMode.PERCENTAGE) "" else "1"
        }
    }

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
        val rawAmount = amountValue!!
        // Convert to INR if a different currency is selected
        val amountInInr = if (selectedCurrency.code == "INR") rawAmount
        else convertToInr(rawAmount, selectedCurrency.code)
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
                    val msg = if (selectedCurrency.code != "INR")
                        "Expense added! (${selectedCurrency.symbol}${"%.2f".format(rawAmount)} → ₹${"%.2f".format(amountInInr)})"
                    else "Expense added!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onSaved(System.currentTimeMillis())
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
                        Text("Add Expense", fontWeight = FontWeight.Bold)
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
                SectionCard(title = "Expense Details") {

                    // Expense name
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

                    // ── [Currency button]  [Amount field] ────────────────────
                    // Box + plain DropdownMenu so the menu is NOT constrained
                    // to the 108 dp button width — it renders at its own 260 dp.
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Currency trigger
                        Box {
                            OutlinedTextField(
                                value         = "${selectedCurrency.flag} ${selectedCurrency.code}",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Currency", fontSize = 10.sp) },
                                trailingIcon  = {
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = "Select currency",
                                        modifier           = Modifier.size(20.dp)
                                    )
                                },
                                // disabled so it never steals keyboard focus;
                                // taps are handled by the Box's clickable modifier
                                enabled       = false,
                                modifier      = Modifier
                                    .width(108.dp)
                                    .clickable { if (!isLoading) currencyExpanded = true },
                                singleLine    = true,
                                textStyle     = LocalTextStyle.current.copy(fontSize = 13.sp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor          = colorScheme.onSurface,
                                    disabledBorderColor        = colorScheme.outline,
                                    disabledLabelColor         = colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor  = colorScheme.onSurfaceVariant
                                )
                            )

                            // Plain DropdownMenu — width is independent of the anchor
                            DropdownMenu(
                                expanded         = currencyExpanded,
                                onDismissRequest = { currencyExpanded = false },
                                modifier         = Modifier.width(260.dp)
                            ) {
                                CURRENCIES.forEach { currency ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment     = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier              = Modifier.fillMaxWidth()
                                            ) {
                                                Text(currency.flag, fontSize = 20.sp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        currency.code,
                                                        fontSize   = 14.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        currency.name,
                                                        fontSize = 11.sp,
                                                        color    = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    currency.symbol,
                                                    fontSize   = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedCurrency = currency
                                            currencyExpanded = false
                                        },
                                        trailingIcon = {
                                            if (selectedCurrency.code == currency.code)
                                                Icon(
                                                    Icons.Filled.Check, null,
                                                    tint = colorScheme.primary
                                                )
                                        }
                                    )
                                }
                            }
                        }

                        // Amount field — fills remaining space
                        OutlinedTextField(
                            value           = amountText,
                            onValueChange   = {
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}\$")))
                                    amountText = it
                            },
                            label           = { Text("Amount *") },
                            placeholder     = { Text("0.00") },
                            leadingIcon     = {
                                Text(
                                    selectedCurrency.symbol,
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(start = 4.dp),
                                    color      = colorScheme.onSurfaceVariant
                                )
                            },
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

                    // ── INR conversion info banner ────────────────────────────
                    if (selectedCurrency.code != "INR" && amountValue != null && amountValue > 0) {
                        val converted = convertToInr(amountValue, selectedCurrency.code)
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(8.dp),
                            color    = colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔄", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${selectedCurrency.symbol}${"%.2f".format(amountValue)} " +
                                            "≈ ₹${"%.2f".format(converted)} (saved in INR)",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Description
                    OutlinedTextField(
                        value         = description,
                        onValueChange = { description = it },
                        label         = { Text("Description (optional)") },
                        placeholder   = { Text("Add a note…") },
                        leadingIcon   = { Icon(Icons.Filled.Notes, null) },
                        modifier      = Modifier.fillMaxWidth(),
                        minLines      = 2,
                        maxLines      = 4,
                        enabled       = !isLoading
                    )
                }
            }

            // ── Paid By ──────────────────────────────────────────────────────
            item {
                SectionCard(title = "Paid By") {
                    if (members.isEmpty()) {
                        Text(
                            "No members loaded yet.", fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded         = payerExpanded,
                            onExpandedChange = { if (!isLoading) payerExpanded = it }
                        ) {
                            val selectedMember = members.find { it.userId == selectedPayerId }
                            OutlinedTextField(
                                value         = selectedMember?.name ?: "Select member",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Who paid?") },
                                leadingIcon   = {
                                    Surface(
                                        modifier = Modifier.size(28.dp),
                                        shape    = CircleShape,
                                        color    = colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                selectedMember?.name?.first()?.uppercase() ?: "?",
                                                fontSize   = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color      = colorScheme.onPrimaryContainer
                                            )
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
                                                        member.email, fontSize = 11.sp,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
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
            }

            // ── Split ────────────────────────────────────────────────────────
            item {
                SectionCard(title = "Split") {
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

                    if (members.isEmpty()) {
                        Text(
                            "No members loaded yet.", fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
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
                            SplitMemberRow(
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
                        Text("Save Expense", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
private fun SplitMemberRow(
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
private fun SectionCard(
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