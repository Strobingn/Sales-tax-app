package com.dirk.salestaxapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dirk.salestaxapp.ui.theme.SalesTaxAppTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class LineItem(val id: Int, val amount: Double, val note: String = "")
data class CalcResult(
    val timestamp: String,
    val mode: String,
    val subtotal: Double,
    val rate: Double,
    val tax: Double,
    val tip: Double,
    val total: Double,
    val jurisdiction: String,
    val note: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalesTaxAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaxProApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxProApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Calculator", "History", "Settings")

    var mode by remember { mutableStateOf("Standard") }
    var jurisdiction by remember { mutableStateOf("NYC (8.875%)") }
    var rateStr by remember { mutableStateOf("8.875") }
    var amountStr by remember { mutableStateOf("") }
    var tipPercentStr by remember { mutableStateOf("15") }
    var items by remember { mutableStateListOf<LineItem>() }
    var history by remember { mutableStateListOf<CalcResult>() }
    var searchQuery by remember { mutableStateOf("") }

    val rate = rateStr.toDoubleOrNull() ?: 0.0
    val tipPercent = tipPercentStr.toDoubleOrNull() ?: 0.0

    val subtotal = if (mode == "Reverse") {
        val total = amountStr.toDoubleOrNull() ?: 0.0
        if (rate > 0) total / (1 + rate / 100) else total
    } else {
        (amountStr.toDoubleOrNull() ?: 0.0) + items.sumOf { it.amount }
    }

    val tax = subtotal * (rate / 100.0)
    val tip = if (mode == "TipTax") subtotal * (tipPercent / 100.0) else 0.0
    val total = if (mode == "Reverse") (amountStr.toDoubleOrNull() ?: 0.0) else subtotal + tax + tip

    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    fun fmt(d: Double) = currency.format(d)

    val jurisdictions = listOf(
        "NYC (8.875%)" to 8.875,
        "New York State (4%)" to 4.0,
        "California (7.25%)" to 7.25,
        "Texas (6.25%)" to 6.25,
        "Florida (6%)" to 6.0,
        "Illinois (6.25%)" to 6.25,
        "New Jersey (6.625%)" to 6.625,
        "Pennsylvania (6%)" to 6.0,
        "Massachusetts (6.25%)" to 6.25,
        "Washington (6.5%)" to 6.5,
        "Custom" to -1.0
    )

    fun saveToHistory() {
        if (subtotal > 0 || total > 0) {
            val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
            val entry = CalcResult(
                timestamp = sdf.format(Date()),
                mode = mode,
                subtotal = subtotal,
                rate = rate,
                tax = tax,
                tip = tip,
                total = total,
                jurisdiction = jurisdiction,
                note = if (items.isNotEmpty()) items.joinToString { it.note } else ""
            )
            history.add(0, entry)
            if (history.size > 20) history.removeLast()
        }
    }

    fun exportHistoryAsCsv() {
        val csv = buildString {
            appendLine("Timestamp,Mode,Jurisdiction,Subtotal,Rate%,Tax,Tip,Total,Note")
            history.forEach {
                appendLine("${it.timestamp},${it.mode},${it.jurisdiction},${it.subtotal},${it.rate},${it.tax},${it.tip},${it.total},\"${it.note}\"")
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, csv)
            putExtra(Intent.EXTRA_SUBJECT, "Sales Tax History CSV")
        }
        context.startActivity(Intent.createChooser(send, "Export History"))
    }

    fun copyResults() {
        val text = buildString {
            appendLine("Sales Tax Pro - $mode")
            appendLine("Jurisdiction: $jurisdiction @ $rate%")
            if (mode == "TipTax") appendLine("Tip $tipPercent% = ${fmt(tip)}")
            appendLine("Subtotal: ${fmt(subtotal)}")
            appendLine("Tax: ${fmt(tax)}")
            if (mode == "TipTax") appendLine("Tip: ${fmt(tip)}")
            appendLine("TOTAL: ${fmt(total)}")
        }
        clipboard.setText(AnnotatedString(text))
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(when (index) { 0 -> Icons.Default.Calculate; 1 -> Icons.Default.History; else -> Icons.Default.Settings }, contentDescription = null) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> CalculatorScreen(
                mode = mode,
                onModeChange = { mode = it },
                jurisdiction = jurisdiction,
                onJurisdictionChange = { j, r -> jurisdiction = j; if (r > 0) rateStr = r.toString() },
                jurisdictions = jurisdictions,
                rateStr = rateStr,
                onRateChange = { rateStr = it },
                amountStr = amountStr,
                onAmountChange = { amountStr = it },
                tipPercentStr = tipPercentStr,
                onTipChange = { tipPercentStr = it },
                items = items,
                onAddItem = { items.add(LineItem((items.maxOfOrNull { it.id } ?: 0) + 1, 0.0)) },
                onDeleteItem = { id -> items.removeAll { it.id == id } },
                onUpdateItemAmount = { id, amt -> items.replaceAll { if (it.id == id) it.copy(amount = amt) else it } },
                subtotal = subtotal,
                tax = tax,
                tip = tip,
                total = total,
                fmt = ::fmt,
                onSave = {
                    saveToHistory()
                    amountStr = ""
                    items.clear()
                },
                onCopy = ::copyResults
            )
            1 -> HistoryScreen(
                history = history.filter {
                    it.jurisdiction.contains(searchQuery, ignoreCase = true) || it.note.contains(searchQuery, ignoreCase = true)
                },
                searchQuery = searchQuery,
                onSearch = { searchQuery = it },
                onLoad = { entry ->
                    mode = entry.mode
                    jurisdiction = entry.jurisdiction
                    rateStr = entry.rate.toString()
                    amountStr = if (entry.mode == "Reverse") entry.total.toString() else entry.subtotal.toString()
                    selectedTab = 0
                },
                onExport = ::exportHistoryAsCsv,
                onClear = { history.clear() },
                fmt = ::fmt
            )
            2 -> SettingsScreen()
        }
    }
}

@Composable
fun CalculatorScreen(
    mode: String,
    onModeChange: (String) -> Unit,
    jurisdiction: String,
    onJurisdictionChange: (String, Double) -> Unit,
    jurisdictions: List<Pair<String, Double>>,
    rateStr: String,
    onRateChange: (String) -> Unit,
    amountStr: String,
    onAmountChange: (String) -> Unit,
    tipPercentStr: String,
    onTipChange: (String) -> Unit,
    items: List<LineItem>,
    onAddItem: () -> Unit,
    onDeleteItem: (Int) -> Unit,
    onUpdateItemAmount: (Int, Double) -> Unit,
    subtotal: Double,
    tax: Double,
    tip: Double,
    total: Double,
    fmt: (Double) -> String,
    onSave: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sales Tax Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Standard", "Reverse", "TipTax").forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(m) }
                )
            }
        }

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = jurisdiction,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jurisdiction") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                jurisdictions.forEach { (name, r) ->
                    DropdownMenuItem(text = { Text("$name") }, onClick = {
                        onJurisdictionChange(name, r)
                        expanded = false
                    })
                }
            }
        }

        if (jurisdiction.contains("NYC", ignoreCase = true)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text("NYC Tax Breakdown", fontWeight = FontWeight.Bold)
                    Text("NY State 4% + NYC 4.5% + MTA 0.375% = 8.875%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        OutlinedTextField(
            value = amountStr,
            onValueChange = onAmountChange,
            label = { Text(if (mode == "Reverse") "Total Amount" else "Amount") },
            prefix = { Text("$") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == "TipTax") {
            OutlinedTextField(
                value = tipPercentStr,
                onValueChange = onTipChange,
                label = { Text("Tip %") },
                suffix = { Text("%") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (mode != "Reverse") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Line Items (${items.size})", fontWeight = FontWeight.SemiBold)
                Button(onClick = onAddItem) { Text("Add Item") }
            }
            items.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.amount.toString(),
                        onValueChange = { newVal ->
                            onUpdateItemAmount(item.id, newVal.toDoubleOrNull() ?: 0.0)
                        },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDeleteItem(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LIVE RESULTS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (mode == "TipTax") Text("Tip (${tipPercent}%): ${fmt(tip)}")
                Text("Subtotal: ${fmt(subtotal)}")
                Text("Tax (${rate}%): ${fmt(tax)}")
                if (mode == "TipTax") Text("Tip: ${fmt(tip)}")
                HorizontalDivider()
                Text("TOTAL TO PAY", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(fmt(total), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = total > 0) {
                Text("Save & Clear")
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text("Copy Results")
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<CalcResult>,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onLoad: (CalcResult) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    fmt: (Double) -> String
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearch,
            label = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching history. Save some calculations from the Calculator tab.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { entry ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("${entry.timestamp} • ${entry.mode} • ${entry.jurisdiction}", fontWeight = FontWeight.Bold)
                            Text("${fmt(entry.subtotal)} + tax ${fmt(entry.tax)} = ${fmt(entry.total)}")
                            if (entry.tip > 0) Text("Tip: ${fmt(entry.tip)}")
                            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { onLoad(entry) }) { Text("Load into Calculator") }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export All History as CSV") }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Clear History") }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & Future Rock Star Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("This version is already very powerful. Next pushes can add:\n• Persistent history with Room DB\n• Camera receipt scanner + photo attach\n• Home screen widget\n• Rounding options & default tip %\n• PDF export\n
Tell me exactly what you want next and I will push it.")
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTaxPro() {
    SalesTaxAppTheme { TaxProApp() }
}
