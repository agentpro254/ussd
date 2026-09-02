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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Quick presets
    val quickPresets = listOf(
        Triple("*334#", "M-PESA", "💰"),
        Triple("*144#", "Airtime Balance", "📱"),
        Triple("*106#", "SIM Reg", "🆔"),
        Triple("*544#", "Data Bundles", "📦"),
        Triple("*456#", "Bonga Points", "⭐"),
        Triple("*100#", "Customer Care", "📞"),
        Triple("*247#", "Equity Bank", "🏦"),
        Triple("*522#", "KCB Bank", "🏦")
    )

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
            Toast.makeText(context, "SMS permission required to read inbox history", Toast.LENGTH_SHORT).show()
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

    Column(
        modifier = modifier
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
                        text = "${filteredMessages.size} verified transactions",
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

        // Quick Presets Row at the Top
        Text(
            text = "Quick Presets",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPresets.forEach { (code, label, icon) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TealPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable {
                        onDialCode(code, label)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(icon, fontSize = 14.sp)
                        Text(
                            text = code,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimaryDark
                            )
                        )
                        Text(
                            text = "• $label",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search and Filter Bar
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

        // Filter chips
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
                Text("Reading SMS inbox...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    "No confirmation messages found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Complete a transaction or check your SMS filters.",
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
                items(filteredMessages, key = { it.raw.id + "_" + it.raw.timestamp }) { parsedSms ->
                    SmsMessageCard(
                        parsedSms = parsedSms,
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
}

@Composable
fun SmsMessageCard(
    parsedSms: ParsedSms,
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

            // Footer: Timestamp & Copy Action
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

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun fetchSmsMessages(
    context: Context,
    parser: SmsParser
): List<ParsedSms> {
    val list = mutableListOf<ParsedSms>()
    try {
        val uri: Uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date", "read")
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "date DESC LIMIT 100"
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
                        list.add(parsed)
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
