package com.example.engine

import com.example.data.model.ParsedSms
import com.example.data.model.SmsMessage
import com.example.data.model.SmsType

class SmsParser {

    fun parseSms(message: SmsMessage): ParsedSms {
        val body = message.body
        val type = detectType(body, message.sender)

        return when (type) {
            SmsType.MPESA_SENT -> parseMpesaSent(message)
            SmsType.MPESA_RECEIVED -> parseMpesaReceived(message)
            SmsType.MPESA_AIRTIME -> parseMpesaAirtime(message)
            SmsType.MPESA_BILL_PAYMENT -> parseMpesaBillPayment(message)
            SmsType.MPESA_WITHDRAWAL -> parseMpesaWithdrawal(message)
            SmsType.BANK_ALERT -> parseBankAlert(message)
            SmsType.GOVERNMENT -> parseGovernmentMessage(message)
            else -> ParsedSms(
                raw = message,
                type = SmsType.OTHER,
                isConfirmed = false
            )
        }
    }

    private fun detectType(body: String, sender: String): SmsType {
        val lower = body.lowercase()
        val s = sender.lowercase()

        // M-PESA detection
        if (s.contains("mpesa") || s.contains("m-pesa") || s.contains("safaricom") || lower.contains("confirmed. you bought")) {
            return when {
                lower.contains("received ksh") || lower.contains("you have received") || lower.contains("received from") -> SmsType.MPESA_RECEIVED
                lower.contains("sent to") || lower.contains("transferred to") -> SmsType.MPESA_SENT
                lower.contains("bought ksh") || lower.contains("airtime") -> SmsType.MPESA_AIRTIME
                lower.contains("paid to") || lower.contains("paybill") || lower.contains("till") -> SmsType.MPESA_BILL_PAYMENT
                lower.contains("withdrawn") || lower.contains("withdraw") || lower.contains("give ksh") -> SmsType.MPESA_WITHDRAWAL
                else -> SmsType.MPESA_SENT
            }
        }

        // Bank detection
        if (s.contains("equity") || s.contains("kcb") ||
            s.contains("co-op") || s.contains("coop") || s.contains("family") ||
            s.contains("standard") || s.contains("ncba") || s.contains("stanbic") ||
            s.contains("absa") || lower.contains("bank a/c") || lower.contains("account balance")) {
            return SmsType.BANK_ALERT
        }

        // Government detection
        if (s.contains("kra") || s.contains("nhif") ||
            s.contains("kplc") || s.contains("ecitizen") ||
            s.contains("helb") || s.contains("nssf") || s.contains("huduma")) {
            return SmsType.GOVERNMENT
        }

        return SmsType.OTHER
    }

    private fun parseMpesaSent(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
        val amountStr = amount?.let { if (it.startsWith("Ksh", true)) it else "Ksh $it" }

        val recipient = extractPattern(
            body,
            Regex("sent to\\s+([A-Z0-9\\s]+?)(?:\\s+\\d{10,12}|\\s+on\\s+)", RegexOption.IGNORE_CASE)
        ) ?: extractPattern(body, Regex("sent to\\s+([A-Z\\s]+)", RegexOption.IGNORE_CASE))

        val phone = extractPattern(body, Regex("(07\\d{8}|01\\d{8}|\\+254\\d{9}|254\\d{9})"))

        val code = extractPattern(body, Regex("^([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE))

        val balance = extractPattern(
            body,
            Regex("New\\s+M-PESA\\s+balance\\s+is\\s+Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
        )
        val balanceStr = balance?.let { "Ksh $it" }

        val fee = extractPattern(
            body,
            Regex("Transaction cost,\\s*Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
        )
        val feeStr = fee?.let { "Ksh $it" }

        val dateTime = extractPattern(
            body,
            Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?)", RegexOption.IGNORE_CASE)
        )

        return ParsedSms(
            raw = message,
            type = SmsType.MPESA_SENT,
            amount = amountStr,
            recipient = recipient?.trim(),
            phoneNumber = phone,
            transactionCode = code,
            balance = balanceStr,
            fee = feeStr,
            dateTime = dateTime,
            isConfirmed = true
        )
    }

    private fun parseMpesaReceived(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
        val amountStr = amount?.let { if (it.startsWith("Ksh", true)) it else "Ksh $it" }

        val sender = extractPattern(
            body,
            Regex("received\\s+from\\s+([A-Z0-9\\s]+?)(?:\\s+\\d{10,12}|\\s+on\\s+)", RegexOption.IGNORE_CASE)
        ) ?: extractPattern(body, Regex("from\\s+([A-Z\\s]+)", RegexOption.IGNORE_CASE))

        val phone = extractPattern(body, Regex("(07\\d{8}|01\\d{8}|\\+254\\d{9}|254\\d{9})"))
        val code = extractPattern(body, Regex("([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("^([A-Z0-9]{8,12})", RegexOption.IGNORE_CASE))

        val balance = extractPattern(
            body,
            Regex("New\\s+M-PESA\\s+balance\\s+is\\s+Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
        )
        val balanceStr = balance?.let { "Ksh $it" }

        val dateTime = extractPattern(
            body,
            Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?)", RegexOption.IGNORE_CASE)
        )

        return ParsedSms(
            raw = message,
            type = SmsType.MPESA_RECEIVED,
            amount = amountStr,
            sender = sender?.trim(),
            phoneNumber = phone,
            transactionCode = code,
            balance = balanceStr,
            dateTime = dateTime,
            isConfirmed = true
        )
    }

    private fun parseMpesaAirtime(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("(?:bought|airtime of)\\s+Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
        val amountStr = amount?.let { if (it.startsWith("Ksh", true)) it else "Ksh $it" }

        val phone = extractPattern(body, Regex("(07\\d{8}|01\\d{8}|\\+254\\d{9}|254\\d{9})"))
        val code = extractPattern(body, Regex("([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE))

        val balance = extractPattern(
            body,
            Regex("New\\s+M-PESA\\s+balance\\s+is\\s+Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
        )

        return ParsedSms(
            raw = message,
            type = SmsType.MPESA_AIRTIME,
            amount = amountStr,
            phoneNumber = phone,
            transactionCode = code,
            balance = balance?.let { "Ksh $it" },
            isConfirmed = true
        )
    }

    private fun parseMpesaBillPayment(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
        val amountStr = amount?.let { if (it.startsWith("Ksh", true)) it else "Ksh $it" }

        val business = extractPattern(
            body,
            Regex("paid to\\s+([A-Za-z0-9\\s]+?)(?:\\s+for\\s+|\\s+on\\s+)", RegexOption.IGNORE_CASE)
        ) ?: extractPattern(body, Regex("paid to\\s+([A-Za-z0-9\\s]+)", RegexOption.IGNORE_CASE))

        val code = extractPattern(body, Regex("([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE))
        val balance = extractPattern(
            body,
            Regex("New\\s+M-PESA\\s+balance\\s+is\\s+Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
        )

        return ParsedSms(
            raw = message,
            type = SmsType.MPESA_BILL_PAYMENT,
            amount = amountStr,
            recipient = business?.trim(),
            transactionCode = code,
            balance = balance?.let { "Ksh $it" },
            isConfirmed = true
        )
    }

    private fun parseMpesaWithdrawal(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("Withdraw\\s+Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("Ksh\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
        val amountStr = amount?.let { if (it.startsWith("Ksh", true)) it else "Ksh $it" }

        val agent = extractPattern(
            body,
            Regex("from\\s+([A-Za-z0-9\\s]+?)(?:\\s+agent|\\s+on\\s+)", RegexOption.IGNORE_CASE)
        )
        val code = extractPattern(body, Regex("([A-Z0-9]{8,12})\\s+Confirmed", RegexOption.IGNORE_CASE))

        return ParsedSms(
            raw = message,
            type = SmsType.MPESA_WITHDRAWAL,
            amount = amountStr,
            sender = agent?.trim(),
            transactionCode = code,
            isConfirmed = true
        )
    }

    private fun parseBankAlert(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("Ksh\\.?\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("KES\\.?\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("([\\d,]+\\.\\d{2})", RegexOption.IGNORE_CASE))
        val amountStr = amount?.let { if (it.startsWith("Ksh", true) || it.startsWith("KES", true)) it else "Ksh $it" }

        val code = extractPattern(body, Regex("Ref(?:erence)?[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("Txn ID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE))

        return ParsedSms(
            raw = message,
            type = SmsType.BANK_ALERT,
            amount = amountStr,
            sender = message.sender,
            transactionCode = code,
            isConfirmed = true
        )
    }

    private fun parseGovernmentMessage(message: SmsMessage): ParsedSms {
        val body = message.body
        val amount = extractPattern(body, Regex("Ksh\\.?\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE))
        val code = extractPattern(body, Regex("Ref[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE))
            ?: extractPattern(body, Regex("PRN[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE))

        return ParsedSms(
            raw = message,
            type = SmsType.GOVERNMENT,
            amount = amount?.let { "Ksh $it" },
            sender = message.sender,
            transactionCode = code,
            isConfirmed = true
        )
    }

    private fun extractPattern(text: String, regex: Regex): String? {
        return regex.find(text)?.let {
            it.groupValues.getOrNull(1)?.trim()
        }
    }
}
