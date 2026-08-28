package com.example.engine

import android.content.Context
import android.util.Log
import com.example.data.model.ParsedUssdResponse
import com.example.data.model.SmartFlowResult
import com.example.data.model.TransactionType
import com.example.data.model.UssdInputType
import com.example.data.model.UssdMenuOption
import com.example.data.model.UssdResponseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmartUssdFlowEngine {

    companion object {
        private const val TAG = "SmartUssdFlowEngine"

        val GOAL_KEYWORDS = mapOf(
            "send_money" to listOf("send", "send money", "tuma", "send cash", "send funds", "transfer", "p2p"),
            "withdraw" to listOf("withdraw", "cash", "agent", "withdrawal", "toa", "atm"),
            "buy_airtime" to listOf("airtime", "buy airtime", "purchase airtime", "bundle", "top up", "recharge", "nunua"),
            "pay_bill" to listOf("pay bill", "bill", "paybill", "payment", "lipa bill", "utilities"),
            "lipa_na_mpesa" to listOf("lipa", "na mpesa", "lipa na mpesa", "till", "pay at", "buy goods", "pochi"),
            "my_account" to listOf("account", "my account", "balance", "mini statement", "self service"),
            "fuliza" to listOf("fuliza", "overdraft", "credit"),
            "m-shwari" to listOf("m-shwari", "shwari", "savings", "loan"),
            "kcb" to listOf("kcb", "bank", "kcb mpesa"),
            "poch" to listOf("poch", "biashara", "business"),
            "check_balance" to listOf("balance", "check balance", "airtime balance", "mpesa balance", "account balance"),
            "data_bundles" to listOf("data", "bundle", "internet", "data bundle", "tunukiwa", "4g", "5g")
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    // Current State
    private var currentGoal: String = ""
    private var flowData = mutableMapOf<String, String>()
    private val navigationHistory = mutableListOf<String>()
    private var isEngineActive = false

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isWaitingForInput = MutableStateFlow(false)
    val isWaitingForInput: StateFlow<Boolean> = _isWaitingForInput.asStateFlow()

    // Callbacks
    var onStatusUpdate: ((String) -> Unit)? = null
    var onResponse: ((ParsedUssdResponse) -> Unit)? = null
    var onInputRequired: ((Map<String, Any>) -> Unit)? = null
    var onComplete: ((SmartFlowResult) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun startFlow(
        context: Context,
        ussdCode: String,
        goal: String,
        initialData: Map<String, String> = emptyMap(),
        simSlot: Int = 0,
        forceSimulation: Boolean = false
    ) {
        currentGoal = goal
        flowData.clear()
        flowData.putAll(initialData)
        navigationHistory.clear()
        isEngineActive = true

        val msg = "📱 Dialing $ussdCode for goal '$goal'..."
        _statusMessage.value = msg
        onStatusUpdate?.invoke(msg)

        UssdSessionManager.startUssdSession(
            context = context,
            rawCode = ussdCode,
            simSlot = simSlot,
            forceSimulation = forceSimulation
        )
    }

    /**
     * Process each incoming USSD response intelligently
     */
    fun handleResponse(response: ParsedUssdResponse) {
        if (!isEngineActive) return
        onResponse?.invoke(response)

        // Check if goal reached or terminal response
        if (response.isTerminal || isGoalReached(response, currentGoal)) {
            handleGoalReached(response, currentGoal)
            return
        }

        // Check if input is required
        if (needsUserInput(response)) {
            processUserInputStep(response)
            return
        }

        // Navigate menu intelligently
        if (response.isMenu && response.options.isNotEmpty()) {
            navigateMenu(response, currentGoal)
            return
        }

        // Fallback: request input or show menu
        requestUserInput(response)
    }

    private fun navigateMenu(response: ParsedUssdResponse, goal: String) {
        val targetOption = findOptionForGoal(response.options, goal)
        if (targetOption != null) {
            val status = "🔍 Found \"$goal\", selecting option ${targetOption.id} (${targetOption.label})..."
            _statusMessage.value = status
            onStatusUpdate?.invoke(status)
            navigationHistory.add("${targetOption.id}: ${targetOption.label}")

            scope.launch {
                delay(400)
                UssdSessionManager.submitStepResponse(targetOption.id)
            }
        } else {
            // Show options to user fallback
            showOptionsToUser(response)
        }
    }

    fun findOptionForGoal(options: List<UssdMenuOption>, goal: String): UssdMenuOption? {
        val keywords = GOAL_KEYWORDS[goal] ?: listOf(goal)

        val scored = options.map { opt ->
            val text = opt.label.lowercase()
            var score = 0

            for (kw in keywords) {
                if (text.contains(kw.lowercase())) {
                    score += 10
                }
            }

            if (keywords.any { text.equals(it, ignoreCase = true) }) {
                score += 15
            }

            Pair(opt, score)
        }.sortedByDescending { it.second }

        return if (scored.isNotEmpty() && scored.first().second > 0) {
            scored.first().first
        } else {
            options.firstOrNull()
        }
    }

    private fun isGoalReached(response: ParsedUssdResponse, goal: String): Boolean {
        val text = response.rawText.lowercase()
        if (goal == "check_balance" && (response.balance != null || text.contains("balance is") || text.contains("airtime bal"))) {
            return true
        }
        if (response.type == UssdResponseType.SUCCESS_RESULT || response.type == UssdResponseType.ERROR_RESULT) {
            return true
        }
        return false
    }

    private fun handleGoalReached(response: ParsedUssdResponse, goal: String) {
        isEngineActive = false
        val amount = response.amount ?: flowData["amount"] ?: "KES 0.00"
        val recipient = response.recipient ?: flowData["phone"] ?: flowData["recipient"] ?: "Recipient"
        val code = response.transactionId ?: "QJK" + (100000..999999).random()

        val type = when (goal) {
            "send_money" -> TransactionType.SENT
            "buy_airtime" -> TransactionType.AIRTIME
            "pay_bill", "lipa_na_mpesa" -> TransactionType.BILL_PAYMENT
            "withdraw" -> TransactionType.WITHDRAWAL
            "check_balance" -> TransactionType.BALANCE
            else -> TransactionType.OTHER
        }

        val result = SmartFlowResult(
            amount = if (amount.startsWith("KES") || amount.startsWith("$")) amount else "KES $amount",
            recipient = recipient,
            phoneNumber = flowData["phone"],
            mpesaCode = code,
            timestamp = System.currentTimeMillis(),
            type = type,
            summary = response.body.ifBlank { response.title },
            rawResponse = response.rawText
        )
        onComplete?.invoke(result)
    }

    private fun needsUserInput(response: ParsedUssdResponse): Boolean {
        val text = response.rawText.lowercase()
        val inputKeywords = listOf(
            "enter", "input", "type", "provide",
            "phone number", "amount", "pin", "password",
            "business number", "till number", "account number", "meter"
        )
        return response.inputType != UssdInputType.NONE || inputKeywords.any { text.contains(it) }
    }

    private fun processUserInputStep(response: ParsedUssdResponse) {
        val text = response.rawText.lowercase()

        // If asking for phone number and we already have it collected
        if ((text.contains("phone") || text.contains("number") || response.inputType == UssdInputType.PHONE_NUMBER) &&
            !text.contains("pin")
        ) {
            val phone = flowData["phone"]
            if (!phone.isNullOrBlank()) {
                val status = "📱 Auto-entering phone: $phone"
                _statusMessage.value = status
                onStatusUpdate?.invoke(status)
                scope.launch {
                    delay(500)
                    UssdSessionManager.submitStepResponse(phone)
                }
                return
            }
        }

        // If asking for amount and we already have it collected
        if ((text.contains("amount") || text.contains("kes") || response.inputType == UssdInputType.AMOUNT) &&
            !text.contains("pin")
        ) {
            val amount = flowData["amount"]
            if (!amount.isNullOrBlank()) {
                val status = "💰 Auto-entering amount: KES $amount"
                _statusMessage.value = status
                onStatusUpdate?.invoke(status)
                scope.launch {
                    delay(500)
                    UssdSessionManager.submitStepResponse(amount)
                }
                return
            }
        }

        // If asking for PIN and we already have it
        if (text.contains("pin") || response.inputType == UssdInputType.PIN) {
            val pin = flowData["pin"]
            if (!pin.isNullOrBlank()) {
                val status = "🔐 Entering secret PIN..."
                _statusMessage.value = status
                onStatusUpdate?.invoke(status)
                scope.launch {
                    delay(500)
                    UssdSessionManager.submitStepResponse(pin)
                }
                return
            }
        }

        // Otherwise request input from the user
        requestUserInput(response)
    }

    private fun requestUserInput(response: ParsedUssdResponse) {
        val inputType = detectInputType(response)
        val data = mapOf(
            "type" to inputType,
            "hint" to response.inputHint.ifBlank { "Enter value" },
            "response" to response
        )
        _isWaitingForInput.value = true
        onInputRequired?.invoke(data)
    }

    private fun detectInputType(response: ParsedUssdResponse): String {
        val text = response.rawText.lowercase()
        return when {
            text.contains("pin") || text.contains("password") -> "pin"
            text.contains("phone") || text.contains("number") -> "phone"
            text.contains("amount") || text.contains("kes") -> "amount"
            text.contains("business") || text.contains("till") -> "business"
            text.contains("account") -> "account"
            else -> "text"
        }
    }

    private fun showOptionsToUser(response: ParsedUssdResponse) {
        val data = mapOf(
            "type" to "menu",
            "options" to response.options,
            "response" to response
        )
        onInputRequired?.invoke(data)
    }

    fun submitUserInput(input: String) {
        _isWaitingForInput.value = false
        UssdSessionManager.submitStepResponse(input)
    }

    fun stop() {
        isEngineActive = false
        _isWaitingForInput.value = false
    }
}
