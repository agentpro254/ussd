package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.IndigoInfo
import com.example.ui.theme.IndigoInfoBg
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

data class ParsedOption(
    val number: String,
    val text: String
)

data class ParsedResponse(
    val raw: String,
    val cleanText: String,
    val title: String?,
    val options: List<ParsedOption>,
    val isTransaction: Boolean,
    val isBalance: Boolean,
    val isError: Boolean,
    val amount: String?,
    val recipient: String?,
    val phone: String?,
    val transactionCode: String?,
    val dateTime: String?,
    val balance: String?
)

fun parseSimpleUssd(raw: String): ParsedResponse {
    val clean = raw
        .replace(Regex("^(CON|END)\\s*", RegexOption.IGNORE_CASE), "")
        .trim()

    val options = mutableListOf<ParsedOption>()
    val lines = clean.lines()
    val nonOptionLines = mutableListOf<String>()

    val optionRegex = Regex("^(\\d+|\\*|#)\\s*[\\.)\\:\\-]\\s*(.+)$")

    for (line in lines) {
        val trimmed = line.trim()
        val match = optionRegex.find(trimmed)
        if (match != null) {
            options.add(
                ParsedOption(
                    number = match.groupValues[1].trim(),
                    text = match.groupValues[2].trim()
                )
            )
        } else if (trimmed.isNotBlank()) {
            nonOptionLines.add(trimmed)
        }
    }

    val title = nonOptionLines.firstOrNull()
    val remainingBody = if (nonOptionLines.size > 1) nonOptionLines.drop(1).joinToString("\n") else ""

    val lower = raw.lowercase()
    val isTransaction = (raw.contains("Confirmed", ignoreCase = true) || raw.contains("successful", ignoreCase = true)) &&
            (lower.contains("sent to") || lower.contains("paid") || lower.contains("transferred"))
    val isBalance = (lower.contains("balance") || lower.contains("salio") || lower.contains("solde")) && !isTransaction
    val isError = lower.contains("error") || lower.contains("failed") || lower.contains("invalid") || lower.contains("timeout")

    // Extract amount
    val amountRegex = Regex("(?:KES|KShs?|Ksh)\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
    val amount = amountRegex.find(raw)?.groupValues?.get(1)?.let { "KES $it" }

    // Extract recipient
    val recipientRegex = Regex("(?:sent to|paid to|kwa)\\s+([A-Z0-9\\s]+?)(?:\\s+07|\\s+01|\\s+\\+254|\\s+on|\\.|$)", RegexOption.IGNORE_CASE)
    val recipient = recipientRegex.find(raw)?.groupValues?.get(1)?.trim()

    // Extract phone
    val phoneRegex = Regex("(07\\d{8}|01\\d{8}|\\+254\\d{9})")
    val phone = phoneRegex.find(raw)?.value

    // Extract transaction code
    val codeRegex = Regex("([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE)
    val transactionCode = codeRegex.find(raw)?.groupValues?.get(1)

    // Extract date/time
    val dateTimeRegex = Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?)", RegexOption.IGNORE_CASE)
    val dateTime = dateTimeRegex.find(raw)?.value

    // Extract balance
    val balanceRegex = Regex("(?:balance\\s+is|salio\\s+ni)\\s*(?:Ksh|KES)?\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
    val balance = balanceRegex.find(raw)?.groupValues?.get(1)?.let { "KES $it" }

    return ParsedResponse(
        raw = raw,
        cleanText = if (remainingBody.isNotBlank()) remainingBody else clean,
        title = title,
        options = options,
        isTransaction = isTransaction,
        isBalance = isBalance,
        isError = isError,
        amount = amount,
        recipient = recipient,
        phone = phone,
        transactionCode = transactionCode,
        dateTime = dateTime,
        balance = balance
    )
}

@Composable
fun UssdResponseDisplay(
    response: String,
    isComplete: Boolean,
    onSendInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsed = remember(response) { parseSimpleUssd(response) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. If it's a Transaction Confirmation
        if (parsed.isTransaction) {
            TransactionSuccessCard(
                amount = parsed.amount,
                recipient = parsed.recipient,
                phone = parsed.phone,
                code = parsed.transactionCode,
                dateTime = parsed.dateTime,
                balance = parsed.balance
            )
        }
        // 2. If it's a Balance Summary
        else if (parsed.isBalance) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoInfoBg),
                border = BorderStroke(1.dp, IndigoInfo.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(IndigoInfo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Balance",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Current Account Balance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndigoInfo
                    )

                    if (!parsed.balance.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = parsed.balance,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = IndigoInfo,
                                fontSize = 30.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = parsed.cleanText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // 3. If it's an Error / Warning
        else if (parsed.isError) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Carrier Response Notice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = parsed.cleanText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 4. If Menu Options are present -> Render interactive cards
        if (parsed.options.isNotEmpty()) {
            if (parsed.title != null && !parsed.isTransaction && !parsed.isBalance) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = parsed.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            parsed.options.forEach { option ->
                androidx.compose.material3.OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSendInput(option.number) }
                        .testTag("ussd_option_${option.number}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Option number pill
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(TealPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.number,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Option Label Text
                        Text(
                            text = option.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Select",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        // 5. Fallback: Clean formatted response text if no options
        else if (!parsed.isTransaction && !parsed.isBalance && !parsed.isError) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (parsed.title != null) {
                        Text(
                            text = parsed.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = parsed.cleanText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // Completion Banner
        if (isComplete) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccessBg,
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "USSD Session Complete",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }
            }
        }
    }
}
