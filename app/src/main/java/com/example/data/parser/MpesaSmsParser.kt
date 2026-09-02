package com.example.data.parser

import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.util.regex.Pattern

/**
 * Structured parsed data model for an incoming or stored M-PESA SMS message.
 */
data class ParsedMpesaSms(
    val transactionCode: String,
    val type: TransactionType,
    val amount: String,
    val rawAmount: Double = 0.0,
    val senderName: String? = null,
    val receiverName: String? = null,
    val partyName: String, // Best human-readable name of the other party (sender, receiver, merchant, agent)
    val phoneNumber: String? = null,
    val accountNumber: String? = null,
    val newBalance: String? = null,
    val transactionCost: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val rawBody: String
)

/**
 * High-performance, inside-the-app parser utility for M-PESA SMS notifications.
 * Accurately extracts transaction code, type, amount, sender/receiver names,
 * phone numbers, account references, fees, and updated balances.
 */
object MpesaSmsParser {

    private val CODE_REGEX = Pattern.compile("([A-Z0-9]{8,12})\\s+(?:Confirmed|confirmed|CONFIRMED)", Pattern.CASE_INSENSITIVE)
    private val AMOUNT_REGEX = Pattern.compile("(?:Ksh\\.?|KES)\\s*([0-9,]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE)
    private val BALANCE_REGEX = Pattern.compile("(?:new\\s+(?:M-PESA|m-pesa)?\\s*balance(?:\\s+is)?|balance\\s+is)\\s*(?:Ksh\\.?|KES)?\\s*([0-9,]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE)
    private val COST_REGEX = Pattern.compile("(?:transaction\\s+cost|cost)[,:]?\\s*(?:Ksh\\.?|KES)\\s*([0-9,]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE)
    private val PHONE_REGEX = Pattern.compile("""(?:\+?254|0)[17]\d{8}""")

    /**
     * Parses an M-PESA SMS message body into a structured [ParsedMpesaSms] object.
     */
    fun parse(body: String, timestamp: Long = System.currentTimeMillis()): ParsedMpesaSms? {
        if (body.isBlank()) return null

        val clean = body.trim()
        val lower = clean.lowercase()

        // 1. Transaction Code
        val codeMatcher = CODE_REGEX.matcher(clean)
        val code = if (codeMatcher.find()) {
            codeMatcher.group(1)?.uppercase() ?: "MPESA"
        } else {
            // Fallback code detection
            val fallbackMatch = Regex("""\b([A-Z0-9]{8,12})\b""").find(clean)
            fallbackMatch?.groupValues?.get(1) ?: "MPESA-${timestamp.toString().takeLast(6)}"
        }

        // 2. Amount
        val amountMatcher = AMOUNT_REGEX.matcher(clean)
        val (amountStr, amountVal) = if (amountMatcher.find()) {
            val rawNum = amountMatcher.group(1) ?: "0.00"
            val cleanNum = rawNum.replace(",", "")
            val parsedDouble = cleanNum.toDoubleOrNull() ?: 0.0
            "KES $rawNum" to parsedDouble
        } else {
            "KES 0.00" to 0.0
        }

        // 3. New Balance
        val balanceMatcher = BALANCE_REGEX.matcher(clean)
        val newBalance = if (balanceMatcher.find()) {
            "KES ${balanceMatcher.group(1)}"
        } else null

        // 4. Transaction Cost
        val costMatcher = COST_REGEX.matcher(clean)
        val transactionCost = if (costMatcher.find()) {
            "KES ${costMatcher.group(1)}"
        } else null

        // 5. Phone Number
        val phoneMatcher = PHONE_REGEX.matcher(clean)
        val extractedPhone = if (phoneMatcher.find()) phoneMatcher.group() else null

        // 6. Categorize & Extract Parties (Sender / Receiver / Merchant / Agent)
        var senderName: String? = null
        var receiverName: String? = null
        var accountNumber: String? = null
        val type: TransactionType
        val partyName: String

        when {
            // Case A: Sent Money ("...sent to Alice Wanjiku 0712345678 on...")
            lower.contains("sent to") -> {
                type = TransactionType.SENT
                val sentRegex = Pattern.compile("sent to\\s+([^0-9\\.\\,]+?)(?:\\s+(\\+?254\\d+|0\\d+))?(?:\\s+on|\\s+for|\\s+new|\\.)", Pattern.CASE_INSENSITIVE)
                val m = sentRegex.matcher(clean)
                if (m.find()) {
                    val name = m.group(1)?.trim()?.replace(Regex("\\s+"), " ") ?: "Recipient"
                    receiverName = name
                    partyName = name
                } else {
                    receiverName = extractedPhone ?: "Recipient"
                    partyName = receiverName
                }
            }

            // Case B: Received Money ("...You have received Ksh3,200.00 from David Omondi 0722987654...")
            lower.contains("received") || lower.contains("you have received") -> {
                type = TransactionType.RECEIVED
                val recRegex = Pattern.compile("from\\s+([^0-9\\.\\,]+?)(?:\\s+(\\+?254\\d+|0\\d+))?(?:\\s+on|\\s+new|\\.)", Pattern.CASE_INSENSITIVE)
                val m = recRegex.matcher(clean)
                if (m.find()) {
                    val name = m.group(1)?.trim()?.replace(Regex("\\s+"), " ") ?: "Sender"
                    senderName = name
                    partyName = name
                } else {
                    senderName = extractedPhone ?: "Sender"
                    partyName = senderName
                }
            }

            // Case C: Bill Payment & Paybill / Buy Goods ("...paid to KPLC PREPAID for account...")
            lower.contains("paid to") -> {
                type = TransactionType.BILL_PAYMENT
                val payRegex = Pattern.compile("paid to\\s+([^\\.\\,]+?)(?:\\s+for account\\s+([^\\.\\,]+?))?(?:\\s+on|\\s+new|\\.)", Pattern.CASE_INSENSITIVE)
                val m = payRegex.matcher(clean)
                if (m.find()) {
                    val merchant = m.group(1)?.trim() ?: "Merchant"
                    accountNumber = m.group(2)?.trim()
                    receiverName = merchant
                    partyName = if (!accountNumber.isNullOrBlank()) "$merchant (Acc: $accountNumber)" else merchant
                } else {
                    receiverName = "Merchant"
                    partyName = "Merchant"
                }
            }

            // Case D: Airtime Purchase ("...You bought Ksh200.00 of airtime...")
            lower.contains("bought") && lower.contains("airtime") -> {
                type = TransactionType.AIRTIME
                receiverName = if (!extractedPhone.isNullOrBlank()) "Airtime for $extractedPhone" else "Airtime Top-Up"
                partyName = receiverName
            }

            // Case E: Cash Withdrawal ("...Withdraw Ksh2,000.00 from 123456 - AGENT NAME...")
            lower.contains("withdraw") -> {
                type = TransactionType.WITHDRAWAL
                val withdrawRegex = Pattern.compile("from\\s+([0-9]+\\s*-\\s*[^\\.\\,]+)", Pattern.CASE_INSENSITIVE)
                val m = withdrawRegex.matcher(clean)
                val agent = if (m.find()) m.group(1)?.trim() ?: "M-PESA Agent" else "M-PESA Agent"
                receiverName = agent
                partyName = agent
            }

            // Case F: Balance Check or Other
            lower.contains("balance") -> {
                type = TransactionType.BALANCE
                partyName = "M-PESA Balance"
            }

            else -> {
                type = TransactionType.OTHER
                partyName = "M-PESA Transaction"
            }
        }

        return ParsedMpesaSms(
            transactionCode = code,
            type = type,
            amount = amountStr,
            rawAmount = amountVal,
            senderName = senderName,
            receiverName = receiverName,
            partyName = partyName,
            phoneNumber = extractedPhone,
            accountNumber = accountNumber,
            newBalance = newBalance,
            transactionCost = transactionCost,
            timestamp = timestamp,
            isSuccess = true,
            rawBody = clean
        )
    }

    /**
     * Converts a [ParsedMpesaSms] to the app's [TransactionItem] format.
     */
    fun toTransactionItem(id: String, parsed: ParsedMpesaSms): TransactionItem {
        return TransactionItem(
            id = id,
            mpesaCode = parsed.transactionCode,
            type = parsed.type,
            amount = parsed.amount,
            recipientOrSender = parsed.partyName,
            phoneNumber = parsed.phoneNumber ?: "",
            timestamp = parsed.timestamp,
            summary = parsed.rawBody.take(120),
            fullBody = parsed.rawBody,
            isVerifiedBySms = true,
            isVerifiedByUssd = false,
            source = "M-PESA SMS"
        )
    }
}
