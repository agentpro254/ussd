package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionDetails
import com.example.data.model.TransactionType
import com.example.data.parser.TransactionParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Codee Brand Colors
val CodeeBrandGreen = Color(0xFF00B341)
val CodeeBrandGreenDark = Color(0xFF009933)
val CodeeBrandBlue = Color(0xFF1A73E8)
val CodeeBrandBlueDark = Color(0xFF1557B0)

/**
 * Codee's Signature Branded Success Full-Screen / View.
 * Displays vibrant brand gradient, checkmark pulse animation, clean elevated card,
 * bold amounts, recipient/sender, M-PESA Code, timestamp, and branded watermark.
 */
@Composable
fun CodeeSuccessScreen(
    amount: String,
    recipient: String? = null,
    sender: String? = null,
    phoneNumber: String? = null,
    mpesaCode: String? = null,
    timestamp: Long = System.currentTimeMillis(),
    type: TransactionType = TransactionType.SENT,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSent = type != TransactionType.RECEIVED
    val title = if (isSent) "Money Sent" else "Money Received"
    val label = if (isSent) "Sent to" else "Received from"
    val person = if (isSent) recipient else sender
    val accentColor = if (isSent) CodeeBrandGreen else CodeeBrandBlue
    val accentColorDark = if (isSent) CodeeBrandGreenDark else CodeeBrandBlueDark

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.88f),
                        accentColorDark
                    )
                )
            )
            .testTag("codee_signature_success_screen")
    ) {
        // Decorative background circles
        Box(
            modifier = Modifier
                .offset(x = 120.dp, y = (-40).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .size(250.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top branding header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "⚡ Codee",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                Text(
                    text = "Secure",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Signature White Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Codee Distinctive Animated Checkmark
                    CodeeCheckmarkBadge(color = accentColor)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accentColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "✅ Completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Codee Brand Gradient Divider
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(accentColor, accentColor.copy(alpha = 0.25f))
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Amount (Large with Codee signature typography)
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.headlineLarge,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )

                    // Recipient or Sender Section
                    if (!person.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF888888),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSent) Icons.Default.PersonOutline else Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = person,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                        }
                    }

                    // Phone number
                    if (!phoneNumber.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "📱 $phoneNumber",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Transaction Detail Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!mpesaCode.isNullOrBlank()) {
                            CodeeDetailChip(
                                icon = Icons.Default.Pin,
                                label = "Code",
                                value = mpesaCode,
                                isMonospace = true,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", mpesaCode))
                                    Toast.makeText(context, "Code copied: $mpesaCode", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        CodeeDetailChip(
                            icon = Icons.Default.CalendarToday,
                            label = "Time",
                            value = timeFormatted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done Button - Branded
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = accentColor
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(50.dp)
                    .testTag("codee_success_done_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accentColor
                    )
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer branding
            Text(
                text = "⚡ Secured by Codee",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * Animated Glowing Checkmark Badge for Codee Signature look.
 */
@Composable
fun CodeeCheckmarkBadge(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = color.copy(alpha = 0.2f),
                spotColor = color.copy(alpha = 0.4f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(2.dp, color.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxSize()
        ) {}

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = color,
            modifier = Modifier.size(42.dp)
        )
    }
}

/**
 * Codee Branded Message Card (Inline) for chat/session timeline.
 */
@Composable
fun CodeeInlineSuccessCard(
    amount: String,
    recipient: String? = null,
    sender: String? = null,
    phoneNumber: String? = null,
    mpesaCode: String? = null,
    timestamp: Long = System.currentTimeMillis(),
    type: TransactionType = TransactionType.SENT,
    onDoneClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSent = type != TransactionType.RECEIVED
    val label = if (isSent) "Sent to" else "Received from"
    val person = if (isSent) recipient else sender
    val accentColor = if (isSent) CodeeBrandGreen else CodeeBrandBlue

    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("codee_inline_success_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White, accentColor.copy(alpha = 0.04f))
                    )
                )
                .padding(16.dp)
        ) {
            // Brand header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "⚡", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        Text(
                            text = "Codee",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                Text(
                    text = "✅ Complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                letterSpacing = (-0.5).sp
            )

            // Recipient / Sender
            if (!person.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF888888)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isSent) Icons.Default.PersonOutline else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = person,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A)
                    )
                }
            }

            if (!phoneNumber.isNullOrBlank()) {
                Text(
                    text = "📱 $phoneNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Footer row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!mpesaCode.isNullOrBlank()) {
                    Text(
                        text = "🔑 $mpesaCode",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF555555),
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Code", mpesaCode))
                            Toast.makeText(context, "Copied: $mpesaCode", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Text(
                        text = "⚡ Secured by Codee",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF999999)
                    )
                }

                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

@Composable
private fun CodeeDetailChip(
    icon: ImageVector,
    label: String,
    value: String,
    isMonospace: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF6F8FA),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF888888)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                color = Color(0xFF1A1A1A)
            )
        }
    }
}

/**
 * Overload for CodeeSuccessScreen accepting [TransactionDetails].
 */
@Composable
fun CodeeSuccessScreen(
    details: TransactionDetails,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    CodeeSuccessScreen(
        amount = details.amount,
        recipient = details.recipient,
        sender = details.sender,
        phoneNumber = details.phoneNumber,
        mpesaCode = details.transactionCode,
        timestamp = details.timestamp,
        type = details.type,
        onDone = onDone,
        modifier = modifier
    )
}

/**
 * Signature TransactionItem for recent lists / history / feeds.
 * Displays who sent or who received clearly with amount, phone, code, and timestamp.
 */
@Composable
fun TransactionItem(
    details: TransactionDetails,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSent = details.type == TransactionType.SENT
    val person = if (isSent) details.recipient ?: "Unknown Recipient" else details.sender ?: "Unknown Sender"
    val accentColor = if (isSent) CodeeBrandGreen else CodeeBrandBlue
    val iconEmoji = if (isSent) "📤" else "📥"
    val actionVerb = if (isSent) "Sent" else "Received"
    val preposition = if (isSent) "to" else "from"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("transaction_item_${details.transactionCode ?: "item"}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: [Emoji] Action + Amount + Person
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = iconEmoji, fontSize = 20.sp)
                    Column {
                        Text(
                            text = "$actionVerb ${details.amount} $preposition $person",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (isSent) "SENT" else "RECEIVED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-details: Phone Number, Transaction Code, Timestamp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!details.phoneNumber.isNullOrBlank()) {
                    Text(
                        text = "📱 ${details.phoneNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF555555),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!details.transactionCode.isNullOrBlank()) {
                    Text(
                        text = "🔑 ${details.transactionCode}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF555555),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Transaction Code", details.transactionCode))
                            Toast.makeText(context, "Code copied: ${details.transactionCode}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Text(
                    text = TransactionParser.formatFullTime(details.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}
