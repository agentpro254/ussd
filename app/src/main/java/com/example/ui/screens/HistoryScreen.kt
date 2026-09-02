package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.ParsedSms
import com.example.data.model.SmsMessage
import com.example.data.model.SmsType
import com.example.engine.SmsParser
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onDialCode: (code: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val smsParser = remember { SmsParser() }
    val smsMessages = remember { mutableStateListOf<ParsedSms>() }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedMessageForModal by remember { mutableStateOf<ParsedSms?>(null) }

    val filterOptions = listOf("All", "M-PESA", "Airtime", "Bank Alerts", "Bills & Utilities")

    fun reloadMessages() {
        isLoading = true
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                fetchSmsMessages(context, smsParser)
            }
            smsMessages.clear()
            smsMessages.addAll(loaded)
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            reloadMessages()
        } else {
            isLoading = false
            Toast.makeText(context, "SMS permission required to read transaction receipts", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            reloadMessages()
        } else {
            isLoading = false
        }
    }

    val filteredMessages = remember(smsMessages.toList(), searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        smsMessages.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "M-PESA" -> item.type == SmsType.MPESA_SENT || item.type == SmsType.MPESA_RECEIVED || item.type == SmsType.MPESA_WITHDRAWAL
                "Airtime" -> item.type == SmsType.MPESA_AIRTIME
                "Bank Alerts" -> item.type == SmsType.BANK_ALERT
                "Bills & Utilities" -> item.type == SmsType.MPESA_BILL_PAYMENT || item.type == SmsType.GOVERNMENT
                else -> true
            }

            val matchesSearch = if (q.isBlank()) true else {
                (item.amount?.lowercase()?.contains(q) == true) ||
                (item.recipient?.lowercase()?.contains(q) == true) ||
                (item.sender?.lowercase()?.contains(q) == true) ||
                (item.phoneNumber?.lowercase()?.contains(q) == true) ||
                (item.transactionCode?.lowercase()?.contains(q) == true) ||
                (item.raw.body.lowercase().contains(q))
            }
            matchesFilter && matchesSearch
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "SMS Messages",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SMS History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${filteredMessages.size} confirmed transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            if (hasPermission) {
                                reloadMessages()
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        },
                        modifier = Modifier.testTag("refresh_sms_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TealPrimary
                        )
                    }

                    if (!hasPermission) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.READ_SMS)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6D00)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("grant_sms_permission_button")
                        ) {
                            Text("Grant Access", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions, codes, names...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_sms_history_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content Area
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = TealPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reading and deduplicating SMS inbox...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            } else if (!hasPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SMS Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Grant SMS permission to view real M-PESA & Bank transaction history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enable SMS Access", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (filteredMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No transactions found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Complete a transaction or clear your search filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredMessages, key = { it.transactionCode ?: (it.raw.id + "_" + it.raw.timestamp) }) { parsedSms ->
                        SmsMessageCard(
                            parsedSms = parsedSms,
                            onClick = {
                                selectedMessageForModal = parsedSms
                            },
                            onCopy = {
                                val code = parsedSms.transactionCode ?: parsedSms.amount ?: parsedSms.raw.body
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SMS Details", code))
                                Toast.makeText(context, "Copied $code", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Full Screen Transaction Detail Modal
        if (selectedMessageForModal != null) {
            TransactionDetailModal(
                parsedSms = selectedMessageForModal!!,
                onDismiss = { selectedMessageForModal = null },
                onCopy = { textToCopy ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Transaction Info", textToCopy))
                    Toast.makeText(context, "Copied: $textToCopy", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun SmsMessageCard(
    parsedSms: ParsedSms,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSent = parsedSms.type == SmsType.MPESA_SENT || parsedSms.type == SmsType.MPESA_BILL_PAYMENT || parsedSms.type == SmsType.MPESA_WITHDRAWAL
    val isReceived = parsedSms.type == SmsType.MPESA_RECEIVED

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(
            1.dp,
            if (parsedSms.isConfirmed) EmeraldSuccess.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("sms_card_${parsedSms.transactionCode ?: parsedSms.raw.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when (parsedSms.type) {
                        SmsType.MPESA_SENT -> "📤"
                        SmsType.MPESA_RECEIVED -> "📥"
                        SmsType.MPESA_AIRTIME -> "📱"
                        SmsType.MPESA_BILL_PAYMENT -> "🏢"
                        SmsType.MPESA_WITHDRAWAL -> "🏧"
                        SmsType.BANK_ALERT -> "🏦"
                        SmsType.GOVERNMENT -> "🏛️"
                        SmsType.OTHER -> "📩"
                    }
                    Text(icon, fontSize = 20.sp)

                    Text(
                        text = when (parsedSms.type) {
                            SmsType.MPESA_SENT -> "Money Sent"
                            SmsType.MPESA_RECEIVED -> "Money Received"
                            SmsType.MPESA_AIRTIME -> "Airtime Purchase"
                            SmsType.MPESA_BILL_PAYMENT -> "Bill / Paybill Payment"
                            SmsType.MPESA_WITHDRAWAL -> "Cash Withdrawal"
                            SmsType.BANK_ALERT -> "Bank Alert"
                            SmsType.GOVERNMENT -> "Government Notice"
                            SmsType.OTHER -> parsedSms.raw.sender.ifBlank { "SMS Transaction" }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (parsedSms.isConfirmed) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldSuccessBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount Display
            if (parsedSms.amount != null) {
                Text(
                    text = parsedSms.amount,
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSent) Color(0xFF00A859) else Color(0xFF1E88E5)
                    )
                )
            }

            // Recipient / Sender Details
            if (parsedSms.recipient != null) {
                Text(
                    text = "Sent to: ${parsedSms.recipient}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (parsedSms.sender != null) {
                Text(
                    text = "From: ${parsedSms.sender}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Phone number
            if (!parsedSms.phoneNumber.isNullOrBlank()) {
                Text(
                    text = "📱 ${parsedSms.phoneNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Transaction Reference Code
            if (!parsedSms.transactionCode.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🔑", fontSize = 13.sp)
                    Text(
                        text = parsedSms.transactionCode,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TealPrimaryDark
                        )
                    )
                }
            }

            // Fee and Balance Breakdown
            if (parsedSms.fee != null || parsedSms.balance != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (parsedSms.fee != null) {
                        Text(
                            text = "Fee: ${parsedSms.fee}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (parsedSms.balance != null) {
                        Text(
                            text = "Balance: ${parsedSms.balance}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Timestamp & Modal Hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSmsDate(parsedSms.raw.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tap for modal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealPrimary
                    )
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDetailModal(
    parsedSms: ParsedSms,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    val isSent = parsedSms.type == SmsType.MPESA_SENT || parsedSms.type == SmsType.MPESA_BILL_PAYMENT || parsedSms.type == SmsType.MPESA_WITHDRAWAL
    val isReceived = parsedSms.type == SmsType.MPESA_RECEIVED

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Dimmed modal backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* prevent click-through */ }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Large Type Icon
                    val icon = when (parsedSms.type) {
                        SmsType.MPESA_SENT -> "📤"
                        SmsType.MPESA_RECEIVED -> "📥"
                        SmsType.MPESA_AIRTIME -> "📱"
                        SmsType.MPESA_BILL_PAYMENT -> "🏢"
                        SmsType.MPESA_WITHDRAWAL -> "🏧"
                        SmsType.BANK_ALERT -> "🏦"
                        SmsType.GOVERNMENT -> "🏛️"
                        SmsType.OTHER -> "📩"
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSent) Color(0xFF00A859).copy(alpha = 0.12f)
                                else Color(0xFF1E88E5).copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 36.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title
                    Text(
                        text = when (parsedSms.type) {
                            SmsType.MPESA_SENT -> "Money Sent"
                            SmsType.MPESA_RECEIVED -> "Money Received"
                            SmsType.MPESA_AIRTIME -> "Airtime Purchase"
                            SmsType.MPESA_BILL_PAYMENT -> "Bill Payment"
                            SmsType.MPESA_WITHDRAWAL -> "Cash Withdrawal"
                            SmsType.BANK_ALERT -> "Bank Alert"
                            SmsType.GOVERNMENT -> "Government Notice"
                            SmsType.OTHER -> parsedSms.raw.sender.ifBlank { "Transaction" }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSent) Color(0xFF00A859) else Color(0xFF1E88E5)
                    )

                    if (parsedSms.isConfirmed) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldSuccessBg,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(
                                text = "✅ Confirmed & Verified",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Large Amount
                    if (parsedSms.amount != null) {
                        Text(
                            text = parsedSms.amount,
                            style = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSent) Color(0xFF00A859) else Color(0xFF1E88E5)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Detailed Key-Value Rows (Simplified: Amount, Sender/Receiver, Phone, Transaction Code, Date/Time)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (parsedSms.recipient != null) {
                            ModalDetailRow(
                                label = if (isSent) "Sent To" else "Recipient",
                                value = parsedSms.recipient,
                                onCopy = { onCopy(parsedSms.recipient) }
                            )
                        }
                        if (parsedSms.sender != null) {
                            ModalDetailRow(
                                label = "Sender / Source",
                                value = parsedSms.sender,
                                onCopy = { onCopy(parsedSms.sender) }
                            )
                        }
                        if (!parsedSms.phoneNumber.isNullOrBlank()) {
                            ModalDetailRow(
                                label = "Phone Number",
                                value = "📱 ${parsedSms.phoneNumber}",
                                onCopy = { onCopy(parsedSms.phoneNumber) }
                            )
                        }
                        if (!parsedSms.transactionCode.isNullOrBlank()) {
                            ModalDetailRow(
                                label = "Transaction Code",
                                value = parsedSms.transactionCode,
                                isCode = true,
                                onCopy = { onCopy(parsedSms.transactionCode) }
                            )
                        }
                        ModalDetailRow(
                            label = "Date & Time",
                            value = formatSmsDate(parsedSms.raw.timestamp),
                            onCopy = { onCopy(formatSmsDate(parsedSms.raw.timestamp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Close Button
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ModalDetailRow(
    label: String,
    value: String,
    isCode: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = onCopy != null) { onCopy?.invoke() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (isCode) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
                    color = if (isCode) TealPrimaryDark else MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.End
            )
            if (onCopy != null) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

private fun fetchSmsMessages(
    context: Context,
    parser: SmsParser
): List<ParsedSms> {
    val list = mutableListOf<ParsedSms>()
    val seenTransactionKeys = mutableSetOf<String>()

    try {
        val uri: Uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date", "read")
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "date DESC LIMIT 150"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            val senderIndex = it.getColumnIndex("address")
            val dateIndex = it.getColumnIndex("date")
            val idIndex = it.getColumnIndex("_id")

            while (it.moveToNext()) {
                val body = if (bodyIndex >= 0) it.getString(bodyIndex) else null
                if (body.isNullOrBlank()) continue
                val sender = if (senderIndex >= 0) it.getString(senderIndex) ?: "Unknown" else "Unknown"
                val timestamp = if (dateIndex >= 0) it.getLong(dateIndex) else System.currentTimeMillis()
                val id = if (idIndex >= 0) it.getString(idIndex) ?: "" else ""

                val lowerBody = body.lowercase()
                val lowerSender = sender.lowercase()

                if (lowerSender.contains("mpesa") ||
                    lowerSender.contains("m-pesa") ||
                    lowerSender.contains("safaricom") ||
                    lowerSender.contains("equity") ||
                    lowerSender.contains("kcb") ||
                    lowerSender.contains("co-op") ||
                    lowerSender.contains("coop") ||
                    lowerSender.contains("family") ||
                    lowerSender.contains("kplc") ||
                    lowerSender.contains("kra") ||
                    lowerSender.contains("nhif") ||
                    lowerSender.contains("ecitizen") ||
                    lowerBody.contains("confirmed") ||
                    lowerBody.contains("ksh") ||
                    lowerBody.contains("balance is")) {

                    val sms = SmsMessage(
                        id = id,
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        isRead = true
                    )

                    val parsed = parser.parseSms(sms)
                    if (parsed.isConfirmed || parsed.amount != null || parsed.transactionCode != null) {
                        // Deduplicate: key on transaction code or body hash
                        val dedupeKey = parsed.transactionCode ?: (body.trim() + "_" + (timestamp / 60000))
                        if (!seenTransactionKeys.contains(dedupeKey)) {
                            seenTransactionKeys.add(dedupeKey)
                            list.add(parsed)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Fallback gracefully on permission or querying exceptions
    }
    return list
}

private fun formatSmsDate(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
    return sdf.format(date)
}
