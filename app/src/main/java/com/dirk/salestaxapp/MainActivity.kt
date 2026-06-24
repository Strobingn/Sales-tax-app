package com.dirk.salestaxapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dirk.salestaxapp.ui.theme.SalesTaxAppTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class CalcResult(
    val amount: Double,
    val rate: Double,
    val tax: Double,
    val total: Double,
    val timestamp: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalesTaxAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SalesTaxCalculatorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesTaxCalculatorScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // State
    var amountStr by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("8.875") } // Default NYC
    var history by remember { mutableStateListOf<CalcResult>() }

    // Presets - common US state combined rates (approx, user can override)
    val presets = listOf(
        "NYC" to 8.875,
        "CA" to 7.25,
        "TX" to 6.25,
        "FL" to 6.0,
        "IL" to 6.25,
        "NJ" to 6.625,
        "PA" to 6.0,
        "Custom" to -1.0
    )

    // Computed values - live
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val rate = rateStr.toDoubleOrNull() ?: 0.0
    val tax = amount * (rate / 100.0)
    val total = amount + tax

    // Formatters
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val percentFormat = remember { NumberFormat.getPercentInstance(Locale.US).apply { maximumFractionDigits = 3 } }

    fun formatCurrency(value: Double): String = currencyFormat.format(value)
    fun formatPercent(value: Double): String = "%.3f%%".format(value)

    // Save current calc to history
    fun saveToHistory() {
        if (amount > 0 && rate > 0) {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val timestamp = sdf.format(Date())
            val result = CalcResult(
                amount = amount,
                rate = rate,
                tax = tax,
                total = total,
                timestamp = timestamp
            )
            history.add(0, result) // newest first
            if (history.size > 12) history.removeLast()
        }
    }

    // Copy results to clipboard
    fun copyResults() {
        val text = buildString {
            appendLine("Sales Tax Calculation")
            appendLine("Amount: ${formatCurrency(amount)}")
            appendLine("Tax Rate: ${formatPercent(rate)}")
            appendLine("Tax: ${formatCurrency(tax)}")
            appendLine("TOTAL: ${formatCurrency(total)}")
            appendLine("--- via Sales Tax App")
        }
        clipboardManager.setText(AnnotatedString(text))
    }

    // Share results
    fun shareResults() {
        val text = buildString {
            appendLine("Sales Tax Calc")
            appendLine("Amount: ${formatCurrency(amount)} @ ${formatPercent(rate)}")
            appendLine("Tax: ${formatCurrency(tax)}")
            appendLine("TOTAL DUE: ${formatCurrency(total)}")
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share calculation via")
        context.startActivity(shareIntent)
    }

    // Clear all inputs
    fun clearAll() {
        amountStr = ""
        rateStr = "8.875"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Tax Calculator", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Enter Purchase Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Amount Input
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                amountStr = newValue
                            }
                        },
                        label = { Text("Purchase Amount (USD)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0.00") }
                    )

                    // Rate Input
                    OutlinedTextField(
                        value = rateStr,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                                rateStr = newValue
                            }
                        },
                        label = { Text("Tax Rate") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("8.875") }
                    )

                    // Quick Presets
                    Text(
                        "Quick State Presets (tap to apply)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presets) { (name, rateValue) ->
                            FilterChip(
                                selected = rateStr.toDoubleOrNull() == rateValue && rateValue > 0,
                                onClick = {
                                    if (rateValue > 0) {
                                        rateStr = rateValue.toString()
                                    } else {
                                        rateStr = ""
                                    }
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            // Results Card - Live
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "LIVE RESULTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            formatCurrency(amount),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tax Rate", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            formatPercent(rate),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sales Tax", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatCurrency(tax),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "TOTAL TO PAY",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            formatCurrency(total),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { saveToHistory() },
                    modifier = Modifier.weight(1f),
                    enabled = amount > 0 && rate > 0
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save to History")
                }

                OutlinedButton(
                    onClick = { clearAll() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { copyResults() },
                    modifier = Modifier.weight(1f),
                    enabled = amount > 0
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }

                Button(
                    onClick = { shareResults() },
                    modifier = Modifier.weight(1f),
                    enabled = amount > 0
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }

            // History Section
            if (history.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "History (${history.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { history.clear() }) {
                                Text("Clear All")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        history.forEach { result ->
                            HistoryItem(result = result, currencyFormat = currencyFormat) {
                                amountStr = result.amount.toString()
                                rateStr = result.rate.toString()
                            }
                            if (result != history.last()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No history yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Calculate something and tap 'Save to History'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Native Android port • Built for speed • No bullshit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HistoryItem(
    result: CalcResult,
    currencyFormat: NumberFormat,
    onLoad: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${currencyFormat.format(result.amount)} @ ${"%.3f".format(result.rate)}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Tax: ${currencyFormat.format(result.tax)}  →  Total: ${currencyFormat.format(result.total)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            result.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        TextButton(onClick = onLoad) {
            Text("Load", fontSize = 12.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SalesTaxCalculatorScreenPreview() {
    SalesTaxAppTheme {
        SalesTaxCalculatorScreen()
    }
}
