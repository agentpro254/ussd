package com.example.data.parser

import com.example.data.model.TransactionDetails
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionParser {

    fun parseTransaction(rawText: String): TransactionDetails? {
        val text = rawText.lowercase()

        // Check if it's a sent transaction
        if (text.contains("sent") || text.contains("send") || text.contains("tuma") ||
            text.contains("umelipa") || text.contains("umetuma") || text.contains("paid") ||
            text.contains("transferred")
        ) {
            return parseSentTransaction(rawText)
        }

        // Check if it's a received transaction
        if (text.contains("received") || text.contains("recieved") || text.contains("pokea") ||
            text.contains("umepokea")
        ) {
            return parseReceivedTransaction(rawText)
        }

        // Fallback for transactions with amount and recipient
        val amount = extractAmount(rawText)
        if (amount != "KES 0.00") {
            val recipient = extractRecipientName(rawText)
            val phone = extractPhoneNumber(rawText)
            val code = extractTransactionCode(rawText)
            return TransactionDetails(
                type = TransactionType.SENT,
                amount = amount,
                recipient = if (recipient != "Unknown") recipient else null,
                sender = null,
                phoneNumber = phone,
                transactionCode = code,
                timestamp = System.currentTimeMillis()
            )
        }

        return null
    }

    fun parseSentTransaction(text: String): TransactionDetails {
        val amount = extractAmount(text)
        val recipient = extractRecipientName(text)
        val phone = extractPhoneNumber(text)
        val code = extractTransactionCode(text)

        return TransactionDetails(
            type = TransactionType.SENT,
            amount = amount,
            recipient = if (recipient != "Unknown") recipient else null,
            sender = null,
            phoneNumber = phone,
            transactionCode = code,
            timestamp = System.currentTimeMillis()
        )
    }

    fun parseReceivedTransaction(text: String): TransactionDetails {
        val amount = extractAmount(text)
        val sender = extractSenderName(text)
        val phone = extractPhoneNumber(text)
        val code = extractTransactionCode(text)

        return TransactionDetails(
            type = TransactionType.RECEIVED,
            amount = amount,
            recipient = null,
            sender = if (sender != "Unknown") sender else null,
            phoneNumber = phone,
            transactionCode = code,
            timestamp = System.currentTimeMillis()
        )
    }

    fun extractRecipientName(text: String): String {
        // Pattern 1: "sent [AMOUNT] to NAME" or "paid to NAME"
        val pattern1 = Regex("""(?i)(?:sent|send|paid|transferred|umetuma)\s+(?:(?:KES|KShs?)\s*[\d,]+(?:\.\d{2})?\s+)?(?:to|kwa)\s+([A-Za-z0-9\s\-]+?)(?:\s+\d{10,12}|\s+07\d{8}|\s+01\d{8}|\s*\.|\n|,|on|balance|ref|txn|$)""")
        pattern1.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && !name.startsWith("KES", ignoreCase = true) && !name.startsWith("KSH", ignoreCase = true)) {
                return cleanPersonName(name)
            }
        }

        // Pattern 2: "to NAME"
        val pattern2 = Regex("""(?i)\bto\s+([A-Za-z\s\-]+?)(?:\s+\d{10,12}|\s+07\d{8}|\s+01\d{8}|\s*\.|\n|,|on|balance|ref|txn|$)""")
        pattern2.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && !name.startsWith("KES", ignoreCase = true) && !name.startsWith("KSH", ignoreCase = true)) {
                return cleanPersonName(name)
            }
        }

        // Pattern 3: "recipient: NAME"
        val pattern3 = Regex("""(?i)(?:recipient|jina)\s*[:\-]\s*([A-Za-z\s\-]+?)(?:\s*\.|\n|,|$)""")
        pattern3.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty()) {
                return cleanPersonName(name)
            }
        }

        // Pattern 4: "for NAME"
        val pattern4 = Regex("""(?i)\bfor\s+([A-Za-z\s\-]+?)(?:\s*\.|\n|,|$)""")
        pattern4.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && !name.startsWith("KES", ignoreCase = true) && !name.startsWith("KSH", ignoreCase = true)) {
                return cleanPersonName(name)
            }
        }

        return "Unknown"
    }

    fun extractSenderName(text: String): String {
        // Pattern 1: "received [AMOUNT] from NAME" or "received from NAME"
        val pattern1 = Regex("""(?i)(?:received|recieved|pokea|umepokea)\s+(?:(?:KES|KShs?)\s*[\d,]+(?:\.\d{2})?\s+)?(?:from|kutoka)\s+([A-Za-z0-9\s\-]+?)(?:\s+\d{10,12}|\s+07\d{8}|\s+01\d{8}|\s*\.|\n|,|on|balance|ref|txn|$)""")
        pattern1.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && !name.startsWith("KES", ignoreCase = true) && !name.startsWith("KSH", ignoreCase = true)) {
                return cleanPersonName(name)
            }
        }

        // Pattern 2: "from NAME"
        val pattern2 = Regex("""(?i)\bfrom\s+([A-Za-z\s\-]+?)(?:\s+\d{10,12}|\s+07\d{8}|\s+01\d{8}|\s*\.|\n|,|on|balance|ref|txn|$)""")
        pattern2.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && !name.startsWith("KES", ignoreCase = true) && !name.startsWith("KSH", ignoreCase = true)) {
                return cleanPersonName(name)
            }
        }

        // Pattern 3: "sender: NAME"
        val pattern3 = Regex("""(?i)(?:sender|kutoka kwa)\s*[:\-]\s*([A-Za-z\s\-]+?)(?:\s*\.|\n|,|$)""")
        pattern3.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty()) {
                return cleanPersonName(name)
            }
        }

        return "Unknown"
    }

    fun extractAmount(text: String): String {
        val patterns = listOf(
            Regex("""(?i)(?:KES|KShs?|Kenya Shillings?)\s*([0-9,]+(?:\.[0-9]{2})?)"""),
            Regex("""([0-9,]+\.[0-9]{2})\s*(?:KES|KShs?)""", RegexOption.IGNORE_CASE),
            Regex("""amount:\s*(?:KES|KShs?)?\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""amount:\s*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(text)?.let { match ->
                val value = match.groupValues.lastOrNull()?.trim() ?: return@let
                if (value.isNotBlank() && !value.equals("KES", ignoreCase = true)) {
                    return if (value.startsWith("KES", ignoreCase = true)) value else "KES $value"
                }
            }
        }

        return "KES 0.00"
    }

    fun extractPhoneNumber(text: String): String? {
        val patterns = listOf(
            Regex("""\+254\d{9}"""),
            Regex("""254\d{9}"""),
            Regex("""\b07\d{8}\b"""),
            Regex("""\b01\d{8}\b"""),
            Regex("""\b\d{10,12}\b""")
        )

        for (pattern in patterns) {
            pattern.find(text)?.let {
                val match = it.value.trim()
                if (match.length >= 10) return match
            }
        }

        return null
    }

    fun extractTransactionCode(text: String): String? {
        val patterns = listOf(
            Regex("""\b([A-Z0-9]{10,12})\b"""),
            Regex("""\b([A-Z0-9]{8,14})\b"""),
            Regex("""(?i)(?:XYZ|ABC|MPESA|TXN)[A-Za-z0-9]{4,}"""),
            Regex("""(?i)(?:code|txn id|ref|transaction id)\s*[:\-]\s*([A-Za-z0-9]+)"""),
            Regex("""(?i)reference:\s*([A-Za-z0-9]+)""")
        )

        for (pattern in patterns) {
            pattern.find(text)?.let { match ->
                val code = if (match.groupValues.size > 1 && match.groupValues[1].isNotBlank()) match.groupValues[1].trim() else match.value.trim()
                if (code.length >= 6 && !code.equals("Unknown", ignoreCase = true) && !code.equals("Confirmed", ignoreCase = true)) {
                    return code
                }
            }
        }

        return null
    }

    private fun cleanPersonName(rawName: String): String {
        return rawName
            .replace(Regex("""(?i)\b(on|at|balance|is|new|account|ref|txn|phone)\b.*"""), "")
            .replace(Regex("""[^\w\s\-]"""), "")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }

    fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = (diff / 1000).coerceAtLeast(0)
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }

    fun formatFullTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
