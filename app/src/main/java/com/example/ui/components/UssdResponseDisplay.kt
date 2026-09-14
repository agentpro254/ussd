package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
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
    val isSuccess: Boolean,
    val isBalance: Boolean,
    val isError: Boolean,
    val isPinPrompt: Boolean,
    val amount: String?,
    val recipient: String?,
    val balance: String?
)

fun parseSimpleUssd(raw: String): ParsedResponse {
    val trimmedRaw = raw.trim()
    if (trimmedRaw == "Waiting for carrier response..." || trimmedRaw.isBlank() || trimmedRaw.startsWith("Waiting for")) {
        return ParsedResponse(
            raw = raw,
            cleanText = "",
            title = null,
            options = emptyList(),
            isTransaction = false,
            isSuccess = false,
            isBalance = false,
            isError = false,
            isPinPrompt = false,
            amount = null,
            recipient = null,
            balance = null
        )
    }
    Log.d("PARSE_DEBUG", "Parsed text: " + raw)

    val clean = raw
        .replace(Regex("^(CON|END)\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\r\n|\r"""), "\n")
        .trim()

    val options = mutableListOf<ParsedOption>()
    val lines = clean.lines()
    val nonOptionLines = mutableListOf<String>()

    // Regex extracting menu items like: 0.Bizna Wallet, 98.CashBack, 1. Send Money, 2) Buy Airtime, 7- PIN, * next
    val optionRegex = Regex("""^[\s]*(\d+|\*+|#+|00)\s*(?:[\.\)\:\>\-]\s*|\s+)(.+)$""")

    for (line in lines) {
        val trimmed = line.trim()
        val match = optionRegex.find(trimmed)
        if (match != null) {
            val num = match.groupValues[1].trim()
            val txt = match.groupValues[2].trim()
            if (txt.isNotBlank()) {
                options.add(ParsedOption(number = num, text = txt))
            }
        } else if (trimmed.isNotBlank()) {
            nonOptionLines.add(trimmed)
        }
    }

    Log.d("PARSE_DEBUG", "Parsed options: " + options.size)

    val title = nonOptionLines.firstOrNull()
    val remainingBody = if (nonOptionLines.size > 1) nonOptionLines.drop(1).joinToString("\n") else (title ?: clean)

    val lower = raw.lowercase()
    val isError = (lower.contains("error") || lower.contains("failed") || lower.contains("invalid") || lower.contains("timed out") || lower.contains("connection problem")) && options.isEmpty()
    val isTransaction = (lower.contains("confirmed") || lower.contains("successful") || lower.contains("umefanikiwa")) &&
            (lower.contains("sent") || lower.contains("paid") || lower.contains("received") || lower.contains("transferred") || lower.contains("bought"))
    val isSuccess = options.isEmpty() && !isError && (
        isTransaction ||
        lower.contains("session complete") ||
        lower.contains("transaction successful") ||
        lower.contains("successful") ||
        lower.contains("completed successfully") ||
        lower.contains("thank you") ||
        lower.contains("umefanikiwa")
    )
    val isBalance = (lower.contains("balance") || lower.contains("salio") || lower.contains("solde")) && !isTransaction && options.isEmpty()
    val isPinPrompt = (lower.contains("pin") || lower.contains("secret code") || lower.contains("password")) && options.isEmpty()

    // Extract amount
    val amountRegex = Regex("""(?i)(?:KES|KShs?|Ksh)\s?([\d,]+\.?\d*)""")
    val amount = amountRegex.find(raw)?.groupValues?.get(1)?.let { "KES $it" }

    // Extract recipient
    val recipientRegex = Regex("""(?i)(?:sent to|paid to|kwa|to)\s+([A-Za-z0-9\s\-]+?)(?:(?:\s+07|\s+01|\s+\+254|\s+on|\.|$))""")
    val recipient = recipientRegex.find(raw)?.groupValues?.get(1)?.trim()

    // Extract balance
    val balanceRegex = Regex("""(?i)(?:balance\s+(?:is|ni)|salio\s+ni)\s*(?:Ksh|KES)?\s?([\d,]+\.?\d*)""")
    val balance = balanceRegex.find(raw)?.groupValues?.get(1)?.let { "KES $it" }

    return ParsedResponse(
        raw = raw,
        cleanText = if (remainingBody.isNotBlank()) remainingBody else clean,
        title = title,
        options = options,
        isTransaction = isTransaction,
        isSuccess = isSuccess,
        isBalance = isBalance,
        isError = isError,
        isPinPrompt = isPinPrompt,
        amount = amount,
        recipient = recipient,
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

    Box(modifier = modifier.fillMaxSize()) {
        val lower = response.lowercase()
        val isExplicitSuccess = parsed.isSuccess || isComplete ||
            lower.contains("session complete") ||
            lower.contains("transaction successful") ||
            lower.contains("umefanikiwa")

        when {
            // Case 1: Error card
            parsed.isError -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Session Failed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = parsed.cleanText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Case 2: Success card with checkmark
            isExplicitSuccess && parsed.options.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = EmeraldSuccessBg),
                        border = BorderStroke(1.5.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSuccess),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (parsed.isTransaction) "Transaction Successful" else "Session Complete",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = parsed.cleanText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Case 3: Menu Options -> Strict Card-Based List using LazyColumn
            parsed.options.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (parsed.title != null) {
                        item {
                            Text(
                                text = parsed.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Interactive Clickable and Selectable Option Cards
                    items(parsed.options, key = { it.number + it.text }) { option ->
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Log.d("PARSE_DEBUG", "Selected option: ${option.number} (${option.text})")
                                    onSendInput(option.number)
                                }
                                .testTag("ussd_option_${option.number}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Bold number badge on the left
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.number,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Main text on the right
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
            }

            // Case 4: PIN prompt
            parsed.isPinPrompt -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, TealPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Security PIN",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Security PIN Required",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = parsed.cleanText,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Case 5: Single Centered Message (Balance or General Carrier Info)
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (parsed.balance != null) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = "Balance",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Account Balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimaryDark
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = parsed.balance,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = TealPrimary,
                                        fontSize = 30.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            } else if (!parsed.title.isNullOrBlank() && parsed.title != parsed.cleanText) {
                                Text(
                                    text = parsed.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Text(
                                text = parsed.cleanText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
