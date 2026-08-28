package com.example.data.parser

import com.example.data.model.ParsedUssdResponse
import com.example.data.model.UssdInputType
import com.example.data.model.UssdMenuOption
import com.example.data.model.UssdResponseType
import java.util.regex.Pattern

object UssdParser {

    private val MENU_REGEX = Pattern.compile(
        """(?m)^[\s*#]*(\d+|\*|#|00)\s*[\.\)\:\>\-]\s*(.+)$"""
    )

    private val BALANCE_REGEX = Regex(
        """(?i)(?:airtime balance|current balance|account balance|balance|salio(?: lako ni)?|solde(?: actuel)?)\s*[:\-\=]?\s*([A-Za-z]{0,4}\.?\s*[\d,]+(?:\.\d{1,2})?)"""
    )

    private val AMOUNT_REGEX = Regex(
        """(?i)(?:KES|KShs?|Kenya Shillings?|USD|UGX|TZS|RWF)\s*([\d,]+(?:\.\d{1,2})?)"""
    )

    private val MONEY_SENT_REGEX = Regex(
        """(?i)(?:sent|send|tuma|paid|transferred|umelipa|umetuma)\s+(?:KES|KShs?)?\s*([\d,]+(?:\.\d{1,2})?)\s+(?:to|kwa|for)\s+([A-Za-z0-9\s\-]+?)(?:\.\s*|\s+on|\s+ref|\s+txn|\s+balance|\s*$)"""
    )

    private val MONEY_RECEIVED_REGEX = Regex(
        """(?i)(?:received|recieved|pokea|umepokea)\s+(?:KES|KShs?)?\s*([\d,]+(?:\.\d{1,2})?)\s+(?:from|kutoka)\s+([A-Za-z0-9\s\-]+?)(?:\.\s*|\s+on|\s+ref|\s+txn|\s+balance|\s*$)"""
    )

    private val RECIPIENT_REGEX = Regex(
        """(?i)(?:to|for|recipient|name|jina)\s*[:\-]?\s*([A-Za-z0-9\s\-]+?)(?:\.\s*|\n|\r|KES|KShs?|balance|$)"""
    )

    private val TXN_ID_REGEX = Regex(
        """(?i)(?:transaction id|txn id|ref(?:erence)?(?:\s+no)?|code|mpesa code|kuthibitisha)\s*[:\-\s]\s*([A-Za-z0-9]{6,16})"""
    )

    private val TOKEN_REGEX = Regex(
        """(?i)(?:token(?:\s+no)?|m-token|umeme token)\s*[:\-\s]\s*([\d\s\-]{12,24})"""
    )

    private val PIN_PATTERNS = listOf(
        "enter pin", "enter your pin", "input pin", "type pin", "pin ya",
        "secret code", "enter password", "weka namba ya siri", "mot de passe",
        "kod pin", "security code", "m-pesa pin", "airtel money pin", "t-kash pin"
    )

    private val AMOUNT_PATTERNS = listOf(
        "enter amount", "ingiza kiasi", "montant", "amount in", "amount to send",
        "amount:", "enter sum", "how much", "kiasi", "weka kiasi"
    )

    private val PHONE_PATTERNS = listOf(
        "enter phone", "phone number", "mobile number", "recipient number",
        "namba ya simu", "numero de telephone", "enter account number",
        "enter msisdn", "account no", "meter no", "card no", "paybill", "till number", "agent no"
    )

    private val ERROR_PATTERNS = listOf(
        "invalid", "error", "failed", "timed out", "connection problem",
        "mmi code", "service unavailable", "try again later",
        "insufficient balance", "not allowed", "session expired",
        "imefeli", "haikufanikiwa", "erreur", "solde insuffisant",
        "wrong pin", "incorrect pin", "session timed out"
    )

    private val SUCCESS_PATTERNS = listOf(
        "successful", "success", "confirmed", "your balance is",
        "account balance", "transaction id", "payment received",
        "you have sent", "salio lako ni", "umefanikiwa",
        "succès", "confirmé", "solde actuel", "token:", "has been paid"
    )

    fun parse(rawText: String, stepIndex: Int = 1): ParsedUssdResponse {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return ParsedUssdResponse(
                type = UssdResponseType.INFO,
                title = "USSD Session",
                body = "Awaiting response from carrier...",
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = false
            )
        }

        val clean = cleanText(trimmed)
        val lower = clean.lowercase()

        // Extract metadata items if available
        var balance: String? = null
        var amount: String? = null
        var recipient: String? = null
        var transactionId: String? = null
        var reference: String? = null

        // 1. Transaction ID extraction
        TXN_ID_REGEX.find(clean)?.let { match ->
            transactionId = match.groupValues.getOrNull(1)?.trim()
        }

        // 2. Token extraction
        TOKEN_REGEX.find(clean)?.let { match ->
            reference = "Token: ${match.groupValues.getOrNull(1)?.trim()}"
        }

        // 3. Balance extraction
        BALANCE_REGEX.find(clean)?.let { match ->
            val value = match.groupValues.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) {
                balance = if (!value.startsWith("KES", ignoreCase = true) && !value.startsWith("KSH", ignoreCase = true) && !value.startsWith("$")) {
                    "KES $value"
                } else {
                    value
                }
            }
        }

        // 4. Money Sent extraction
        MONEY_SENT_REGEX.find(clean)?.let { match ->
            val amt = match.groupValues.getOrNull(1)?.trim()
            val rec = match.groupValues.getOrNull(2)?.trim()
            if (!amt.isNullOrBlank()) amount = if (amt.startsWith("KES", ignoreCase = true)) amt else "KES $amt"
            if (!rec.isNullOrBlank()) recipient = rec
        }

        // 5. Money Received extraction
        if (amount == null) {
            MONEY_RECEIVED_REGEX.find(clean)?.let { match ->
                val amt = match.groupValues.getOrNull(1)?.trim()
                val rec = match.groupValues.getOrNull(2)?.trim()
                if (!amt.isNullOrBlank()) amount = if (amt.startsWith("KES", ignoreCase = true)) amt else "KES $amt"
                if (!rec.isNullOrBlank()) recipient = rec
            }
        }

        // 6. Generic Amount & Recipient fallback extraction
        if (amount == null) {
            AMOUNT_REGEX.find(clean)?.let { match ->
                val amt = match.groupValues.getOrNull(1)?.trim()
                if (!amt.isNullOrBlank()) amount = "KES $amt"
            }
        }
        if (recipient == null) {
            RECIPIENT_REGEX.find(clean)?.let { match ->
                val rec = match.groupValues.getOrNull(1)?.trim()
                if (!rec.isNullOrBlank() && rec.length > 2) recipient = rec
            }
        }

        // Case A: Explicit Error Terminal State
        if (ERROR_PATTERNS.any { lower.contains(it) }) {
            return ParsedUssdResponse(
                type = UssdResponseType.ERROR_RESULT,
                title = "Session Failed",
                body = clean,
                transactionId = transactionId,
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = true,
                isSuccess = false
            )
        }

        // Case B: Explicit Success / Balance / Transaction Terminal State
        val hasOptions = hasMenuOptions(clean)
        if (!hasOptions && (SUCCESS_PATTERNS.any { lower.contains(it) } || amount != null || balance != null)) {
            val isTxn = amount != null || lower.contains("sent") || lower.contains("received") || lower.contains("paid")
            val isBal = balance != null && !isTxn

            val responseType = when {
                isTxn -> UssdResponseType.TRANSACTION
                isBal -> UssdResponseType.BALANCE
                else -> UssdResponseType.SUCCESS_RESULT
            }

            val title = when {
                isTxn -> "Transaction Successful"
                isBal -> "Current Balance"
                else -> "Transaction Complete"
            }

            return ParsedUssdResponse(
                type = responseType,
                title = title,
                body = clean,
                balance = balance,
                amount = amount,
                recipient = recipient,
                transactionId = transactionId,
                reference = reference,
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = true,
                isSuccess = true
            )
        }

        // Case C: Menu Options (Numbered / Bulleted Choices)
        val menuOptions = extractMenuOptions(clean)
        if (menuOptions.isNotEmpty()) {
            val (title, body) = extractHeaderAndBody(clean, menuOptions)
            return ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = if (title.isNotBlank()) title else "Select an Option",
                body = body,
                options = menuOptions,
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = false
            )
        }

        // Case D: Confirmation Dialog (Yes / No / Confirm / Cancel)
        if (isConfirmationPrompt(lower)) {
            val options = listOf(
                UssdMenuOption(id = "1", label = "Confirm / Accept"),
                UssdMenuOption(id = "2", label = "Cancel Transaction", isBack = true)
            )
            return ParsedUssdResponse(
                type = UssdResponseType.CONFIRMATION,
                title = "Confirm Transaction",
                body = clean,
                amount = amount,
                recipient = recipient,
                options = options,
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = false
            )
        }

        // Case E: PIN / Secret Code Request
        if (PIN_PATTERNS.any { lower.contains(it) }) {
            return ParsedUssdResponse(
                type = UssdResponseType.PIN_REQUEST,
                title = "Enter Security PIN",
                body = clean,
                inputType = UssdInputType.PIN,
                inputHint = "Enter 4-digit PIN",
                amount = amount,
                recipient = recipient,
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = false
            )
        }

        // Case F: Amount / Phone / Specific Input Prompt
        val (inputType, inputHint, promptTitle) = detectInputType(lower)
        if (inputType != UssdInputType.NONE) {
            return ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = promptTitle,
                body = clean,
                inputType = inputType,
                inputHint = inputHint,
                amount = amount,
                recipient = recipient,
                rawText = rawText,
                stepIndex = stepIndex,
                isTerminal = false
            )
        }

        // Fallback: General interactive input prompt
        return ParsedUssdResponse(
            type = UssdResponseType.INPUT_PROMPT,
            title = "Enter Response",
            body = clean,
            inputType = UssdInputType.TEXT,
            inputHint = "Type response here...",
            rawText = rawText,
            stepIndex = stepIndex,
            isTerminal = false
        )
    }

    private fun hasMenuOptions(text: String): Boolean {
        val matcher = MENU_REGEX.matcher(text)
        return matcher.find()
    }

    private fun extractMenuOptions(text: String): List<UssdMenuOption> {
        val options = mutableListOf<UssdMenuOption>()
        val lines = text.lines()
        for (line in lines) {
            val trimmedLine = line.trim()
            val matcher = MENU_REGEX.matcher(trimmedLine)
            if (matcher.find()) {
                val key = matcher.group(1)?.trim() ?: ""
                val label = matcher.group(2)?.trim() ?: ""
                val isBack = key == "0" || key == "00" || label.contains("back", ignoreCase = true) || label.contains("cancel", ignoreCase = true)
                val isNext = key == "*" || key == "#" || label.contains("next", ignoreCase = true) || label.contains("more", ignoreCase = true)
                options.add(
                    UssdMenuOption(
                        id = key,
                        label = label,
                        isBack = isBack,
                        isNext = isNext
                    )
                )
            }
        }
        return options
    }

    private fun extractHeaderAndBody(text: String, options: List<UssdMenuOption>): Pair<String, String> {
        val lines = text.lines()
        val headerLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = MENU_REGEX.matcher(trimmed)
            if (matcher.find()) {
                break
            } else {
                headerLines.add(trimmed)
            }
        }

        val title = if (headerLines.isNotEmpty()) headerLines.first() else "Menu Options"
        val body = if (headerLines.size > 1) headerLines.drop(1).joinToString("\n") else ""
        return Pair(cleanText(title), cleanText(body))
    }

    private fun isConfirmationPrompt(lower: String): Boolean {
        val hasConfirmWord = lower.contains("confirm") || lower.contains("do you want to") ||
                lower.contains("thibitisha") || lower.contains("authorize") || lower.contains("proceed")
        val hasOptions = lower.contains("yes") || lower.contains("cancel") || lower.contains("1:") || lower.contains("1.") || lower.contains("1 for yes")
        return hasConfirmWord && (hasOptions || lower.contains("1") || lower.contains("pin"))
    }

    private fun detectInputType(lower: String): Triple<UssdInputType, String, String> {
        if (PIN_PATTERNS.any { lower.contains(it) }) {
            return Triple(UssdInputType.PIN, "Enter 4-digit PIN", "Security PIN Required")
        }
        if (AMOUNT_PATTERNS.any { lower.contains(it) }) {
            return Triple(UssdInputType.AMOUNT, "Enter amount (e.g. 100)", "Enter Amount")
        }
        if (PHONE_PATTERNS.any { lower.contains(it) }) {
            return Triple(UssdInputType.PHONE_NUMBER, "Enter phone/account number", "Recipient / Account")
        }
        if (lower.contains("enter") || lower.contains("type") || lower.contains("weka") || lower.contains("entrez") || lower.contains("ingiza")) {
            return Triple(UssdInputType.NUMERIC, "Enter input", "Input Required")
        }
        return Triple(UssdInputType.NONE, "", "")
    }

    private fun cleanText(text: String): String {
        return text
            .replace("CON", "")
            .replace("END", "")
            .replace(Regex("""\r\n|\r"""), "\n")
            .trim()
    }
}

