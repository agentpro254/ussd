// SmsParser.kt - Complete Updated Version

package com.example.engine

import android.util.Log
import com.example.data.model.ParsedSms
import com.example.data.model.SmsMessage
import com.example.data.model.SmsType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsParser {

    companion object {
        private const val TAG = "SmsParser"
    }

    fun parseSms(message: SmsMessage): ParsedSms {
        val body = message.body
        val sender = message.sender
        Log.d(TAG, "📱 Parsing SMS from: $sender")
        Log.d(TAG, "📝 Body: $body")

        val type = detectType(body, sender)

        return when (type) {
            SmsType.MPESA_SENT -> parseMpesaSent(body, message)
            SmsType.MPESA_RECEIVED -> parseMpesaReceived(body, message)
            SmsType.MPESA_PAID -> parseMpesaPaid(body, message)
            SmsType.MPESA_AIRTIME -> parseMpesaAirtime(body, message)
            SmsType.MPESA_BILL_PAYMENT -> parseMpesaBillPayment(body, message)
            SmsType.MPESA_WITHDRAWAL -> parseMpesaWithdrawal(body, message)
            SmsType.BANK_ALERT -> parseBankAlert(body, sender, message)
            SmsType.GOVERNMENT -> parseGovernmentMessage(body, sender, message)
            else -> ParsedSms(
                raw = message,
                type = SmsType.OTHER,
                isConfirmed = false,
                rawText = body
            )
        }
    }

    fun parseSms(body: String, sender: String = ""): ParsedSms {
        val dummyMessage = SmsMessage(
            id = System.currentTimeMillis().toString(),
            sender = sender,
            body = body,
            timestamp = System.currentTimeMillis()
        )
        return parseSms(dummyMessage)
    }

    private fun detectType(body: String, sender: String): SmsType {
        val lower = body.lowercase()
        val senderLower = sender.lowercase()

        // M-PESA / Safaricom
        if (senderLower.contains("mpesa") || 
            senderLower.contains("safaricom") ||
            lower.contains("mpesa") ||
            lower.contains("m-pesa")) {
            
            return when {
                lower.contains("received ksh") || 
                lower.contains("received from") -> SmsType.MPESA_RECEIVED
                
                lower.contains("paid to") -> SmsType.MPESA_PAID
                
                lower.contains("sent ksh") || 
                lower.contains("sent to") -> SmsType.MPESA_SENT
                
                lower.contains("airtime") -> SmsType.MPESA_AIRTIME
                
                lower.contains("paybill") || 
                lower.contains("bill") || 
                lower.contains("pay bill") -> SmsType.MPESA_BILL_PAYMENT
                
                lower.contains("withdrawn") || 
                lower.contains("withdraw") || 
                lower.contains("agent") -> SmsType.MPESA_WITHDRAWAL
                
                else -> SmsType.MPESA_SENT
            }
        }

        // Banks
        if (senderLower.contains("equity") || 
            senderLower.contains("kcb") ||
            senderLower.contains("co-op") || 
            senderLower.contains("cooperative") ||
            senderLower.contains("family bank") ||
            senderLower.contains("standard chartered") ||
            senderLower.contains("ncba") ||
            senderLower.contains("absa") ||
            senderLower.contains("stanbic") ||
            senderLower.contains("dtb") ||
            lower.contains("equity") ||
            lower.contains("kcb")) {
            return SmsType.BANK_ALERT
        }

        // Government
        if (senderLower.contains("kra") || 
            senderLower.contains("nhif") ||
            senderLower.contains("kplc") ||
            senderLower.contains("ecitizen") ||
            senderLower.contains("helb") ||
            senderLower.contains("nssf")) {
            return SmsType.GOVERNMENT
        }

        return SmsType.OTHER
    }

    // ✅ FORMAT 1: "you have sent Ksh. 100.0 to HEZRON O HELLEN for 40069635"
    private fun parseMpesaSent(body: String, originalMessage: SmsMessage? = null): ParsedSms {
        Log.d(TAG, "🔍 Parsing SENT: $body")

        // Extract amount - handles "Ksh. 100.0" or "Ksh100.00"
        val amount = extractAmount(body)
        
        // Extract recipient name - handles "HEZRON O HELLEN"
        val recipient = extractSentRecipient(body)
        
        // Extract phone/account number - handles "for 40069635"
        val accountNumber = extractAccountNumber(body)
        
        // Extract transaction code - handles "UHR6M4WZ3I"
        val code = extractTransactionCode(body)
        
        // Extract date/time - handles "08/27/2026 at 11:04:05"
        val dateTime = extractDateTime(body)

        Log.d(TAG, "✅ Parsed SENT - Amount: $amount, Recipient: $recipient, Account: $accountNumber, Code: $code")

        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.MPESA_SENT,
            amount = amount,
            recipient = recipient,
            phoneNumber = accountNumber,
            transactionCode = code,
            dateTime = dateTime,
            isConfirmed = true,
            rawText = body
        )
    }

    // ✅ FORMAT 2 & 4: "You have received Ksh10.00 from Hellen Odinga 0720***813"
    private fun parseMpesaReceived(body: String, originalMessage: SmsMessage? = null): ParsedSms {
        Log.d(TAG, "🔍 Parsing RECEIVED: $body")

        // Extract amount
        val amount = extractAmount(body)
        
        // Extract sender name - handles "Hellen Odinga" or "CELESTINE UNGUKU"
        val sender = extractReceivedSender(body)
        
        // Extract phone number - handles "0720***813" or "0141***004"
        val phone = extractPhoneNumber(body)
        
        // Extract transaction code - handles "UI2NG57BB3"
        val code = extractTransactionCode(body)
        
        // Extract date/time - handles "2/9/26 at 3:50 PM"
        val dateTime = extractDateTime(body)

        Log.d(TAG, "✅ Parsed RECEIVED - Amount: $amount, Sender: $sender, Phone: $phone, Code: $code")

        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.MPESA_RECEIVED,
            amount = amount,
            sender = sender,
            phoneNumber = phone,
            transactionCode = code,
            dateTime = dateTime,
            isConfirmed = true,
            rawText = body
        )
    }

    // ✅ FORMAT 3: "Ksh60.00 paid to MOSES NJUGUNA"
    private fun parseMpesaPaid(body: String, originalMessage: SmsMessage? = null): ParsedSms {
        Log.d(TAG, "🔍 Parsing PAID: $body")

        // Extract amount
        val amount = extractAmount(body)
        
        // Extract recipient name - handles "MOSES NJUGUNA"
        val recipient = extractPaidRecipient(body)
        
        // Extract transaction code
        val code = extractTransactionCode(body)
        
        // Extract date/time
        val dateTime = extractDateTime(body)

        Log.d(TAG, "✅ Parsed PAID - Amount: $amount, Recipient: $recipient, Code: $code")

        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.MPESA_PAID,
            amount = amount,
            recipient = recipient,
            transactionCode = code,
            dateTime = dateTime,
            isConfirmed = true,
            rawText = body
        )
    }

    // ✅ EXTRACT AMOUNT - Handles all formats
    private fun extractAmount(text: String): String? {
        // Pattern: "Ksh. 100.0" or "Ksh100.00" or "Ksh 100.00" or "Ksh10.00"
        val patterns = listOf(
            Regex("Ksh\\.\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("Ksh\\s*([\\d,]+\\.\\d{2})", RegexOption.IGNORE_CASE),
            Regex("Ksh\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("([\\d,]+\\.\\d{2})\\s*paid", RegexOption.IGNORE_CASE),
            Regex("([\\d,]+\\.\\d{2})\\s*sent", RegexOption.IGNORE_CASE),
            Regex("([\\d,]+\\.\\d{2})\\s*received", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            pattern.find(text)?.let {
                val value = it.groupValues[1].trim()
                if (value.isNotEmpty()) {
                    // Format properly
                    val formatted = if (value.contains(".")) value else "$value.00"
                    return "Ksh $formatted"
                }
            }
        }
        
        return null
    }

    // ✅ EXTRACT SENT RECIPIENT - "HEZRON O HELLEN"
    private fun extractSentRecipient(text: String): String? {
        // Pattern: "to HEZRON O HELLEN for"
        val pattern1 = Regex("to\\s+([A-Za-z\\s]+?)\\s+for\\s+\\d+", RegexOption.IGNORE_CASE)
        pattern1.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && name.length > 2) return name
        }

        // Pattern: "to HEZRON O HELLEN"
        val pattern2 = Regex("to\\s+([A-Za-z\\s]+?)(?:\\s+\\d|\\n|\\.|,|$)", RegexOption.IGNORE_CASE)
        pattern2.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && name.length > 2) return name
        }

        return null
    }

    // ✅ EXTRACT RECEIVED SENDER - "Hellen Odinga" or "CELESTINE UNGUKU"
    private fun extractReceivedSender(text: String): String? {
        // Pattern: "from Hellen Odinga 0720***813"
        val pattern1 = Regex("from\\s+([A-Za-z\\s]+?)\\s+\\d{4}\\*{3}\\d{3,4}", RegexOption.IGNORE_CASE)
        pattern1.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && name.length > 2) return name
        }

        // Pattern: "from Hellen Odinga"
        val pattern2 = Regex("from\\s+([A-Za-z\\s]+?)(?:\\s+\\d|\\n|\\.|,|$)", RegexOption.IGNORE_CASE)
        pattern2.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && name.length > 2) return name
        }

        return null
    }

    // ✅ EXTRACT PAID RECIPIENT - "MOSES NJUGUNA"
    private fun extractPaidRecipient(text: String): String? {
        // Pattern: "paid to MOSES NJUGUNA"
        val pattern1 = Regex("paid to\\s+([A-Za-z\\s]+?)(?:\\s+on|\\n|\\.|,|$)", RegexOption.IGNORE_CASE)
        pattern1.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && name.length > 2) return name
        }

        // Pattern: "to MOSES NJUGUNA"
        val pattern2 = Regex("to\\s+([A-Za-z\\s]+?)(?:\\s+on|\\n|\\.|,|$)", RegexOption.IGNORE_CASE)
        pattern2.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty() && name.length > 2) return name
        }

        return null
    }

    // ✅ EXTRACT ACCOUNT NUMBER - "for 40069635"
    private fun extractAccountNumber(text: String): String? {
        // Pattern: "for 40069635"
        val pattern1 = Regex("for\\s+(\\d{8,10})", RegexOption.IGNORE_CASE)
        pattern1.find(text)?.let {
            return it.groupValues[1]
        }

        // Pattern: "account 40069635"
        val pattern2 = Regex("account\\s+(\\d{8,10})", RegexOption.IGNORE_CASE)
        pattern2.find(text)?.let {
            return it.groupValues[1]
        }

        return null
    }

    // ✅ EXTRACT TRANSACTION CODE
    private fun extractTransactionCode(text: String): String? {
        // Pattern: "MPESA Ref. UHR6M4WZ3I" or "UI2NG57BB3"
        val patterns = listOf(
            Regex("MPESA\\s+Ref\\.\\s*([A-Z0-9]{7,12})", RegexOption.IGNORE_CASE),
            Regex("Ref\\.\\s*([A-Z0-9]{7,12})", RegexOption.IGNORE_CASE),
            Regex("MPESA\\s+Ref\\s*[:\\-]?\\s*([A-Z0-9]{7,12})", RegexOption.IGNORE_CASE),
            Regex("\\b([A-Z]{1,2}[A-Z0-9]{6,11})\\b") // Generic alphanumeric code
        )
        
        for (pattern in patterns) {
            pattern.find(text)?.let {
                val code = it.groupValues[1].trim()
                if (code.length >= 6 && code.matches(Regex("[A-Z0-9]+"))) {
                    return code
                }
            }
        }
        
        return null
    }

    // ✅ EXTRACT DATE/TIME
    private fun extractDateTime(text: String): String? {
        // Pattern 1: "08/27/2026 at 11:04:05"
        val pattern1 = Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}:\\d{2})", RegexOption.IGNORE_CASE)
        pattern1.find(text)?.let {
            return "${it.groupValues[1]} ${it.groupValues[2]}"
        }

        // Pattern 2: "2/9/26 at 3:50 PM"
        val pattern2 = Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2})\\s+(AM|PM)", RegexOption.IGNORE_CASE)
        pattern2.find(text)?.let {
            return "${it.groupValues[1]} ${it.groupValues[2]}:00 ${it.groupValues[3]}"
        }

        // Pattern 3: "08/27/2026 at 11:04:05" (without "on")
        val pattern3 = Regex("(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}:\\d{2})", RegexOption.IGNORE_CASE)
        pattern3.find(text)?.let {
            return "${it.groupValues[1]} ${it.groupValues[2]}"
        }

        return null
    }

    // ✅ EXTRACT PHONE NUMBER (including partial)
    private fun extractPhoneNumber(text: String): String? {
        // Pattern: "0720***813" or "0141***004"
        val pattern1 = Regex("(\\d{4}\\*{3}\\d{3,4})")
        pattern1.find(text)?.let {
            return it.groupValues[1]
        }

        // Pattern: "07xxxxxxxx"
        val pattern2 = Regex("(07\\d{8}|01\\d{8}|\\+254\\d{9})")
        pattern2.find(text)?.let {
            return it.value
        }

        // Pattern: "0720 123 456"
        val pattern3 = Regex("(07\\d{2}\\s\\d{3}\\s\\d{3})")
        pattern3.find(text)?.let {
            return it.value.replace(" ", "")
        }

        return null
    }

    private fun parseMpesaAirtime(body: String, originalMessage: SmsMessage? = null): ParsedSms {
        val amount = extractAmount(body)
        val phone = extractPhoneNumber(body)
        val code = extractTransactionCode(body)

        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.MPESA_AIRTIME,
            amount = amount,
            phoneNumber = phone,
            transactionCode = code,
            isConfirmed = true,
            rawText = body
        )
    }

    private fun parseMpesaBillPayment(body: String, originalMessage: SmsMessage? = null): ParsedSms {
        val amount = extractAmount(body)
        val business = extractPaidRecipient(body)
        val code = extractTransactionCode(body)

        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.MPESA_BILL_PAYMENT,
            amount = amount,
            recipient = business,
            transactionCode = code,
            isConfirmed = true,
            rawText = body
        )
    }

    private fun parseMpesaWithdrawal(body: String, originalMessage: SmsMessage? = null): ParsedSms {
        val amount = extractAmount(body)
        val code = extractTransactionCode(body)

        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.MPESA_WITHDRAWAL,
            amount = amount,
            transactionCode = code,
            isConfirmed = true,
            rawText = body
        )
    }

    private fun parseBankAlert(body: String, sender: String, originalMessage: SmsMessage? = null): ParsedSms {
        val amount = extractAmount(body)
        val bankName = sender.replace("Bank", "").trim()
        
        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.BANK_ALERT,
            amount = amount,
            recipient = bankName,
            isConfirmed = true,
            rawText = body
        )
    }

    private fun parseGovernmentMessage(body: String, sender: String, originalMessage: SmsMessage? = null): ParsedSms {
        val amount = extractAmount(body)
        
        return ParsedSms(
            raw = originalMessage ?: SmsMessage("", "", body, System.currentTimeMillis()),
            type = SmsType.GOVERNMENT,
            amount = amount,
            recipient = sender,
            isConfirmed = true,
            rawText = body
        )
    }
}
