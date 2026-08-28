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
        goal: String = "",
        simSlot: Int = 0,
        forceSimulation: Boolean = false
    ) {
        currentGoal = goal
        navigationHistory.clear()
        isEngineActive = true

        val msg = if (goal.isNotBlank()) "📱 Dialing $ussdCode for '$goal'..." else "📱 Dialing $ussdCode..."
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
     * Process each incoming USSD response - 100% interactive, zero silent recipient/account automation
     */
    fun handleResponse(response: ParsedUssdResponse) {
        if (!isEngineActive) return
        onResponse?.invoke(response)

        // Check if goal reached or terminal response
        if (response.isTerminal || isGoalReached(response, currentGoal)) {
            handleGoalReached(response, currentGoal)
            return
        }

        // Always prompt user directly for input - no hidden recipient/account prefilling or auto-submission
        if (needsUserInput(response)) {
            processUserInputStep(response)
            return
        }

        // For menus, present options to the user with recommendation instead of silent automatic submission
        if (response.isMenu && response.options.isNotEmpty()) {
            val suggested = findOptionForGoal(response.options, currentGoal)
            val msg = if (suggested != null) {
                "💡 Suggested: ${suggested.id}. ${suggested.label}"
            } else {
                "Please select an option:"
            }
            _statusMessage.value = msg
            onStatusUpdate?.invoke(msg)
            showOptionsToUser(response, suggested)
            return
        }

        // Fallback: request input or show response to user
        requestUserInput(response)
    }

    private fun processUserInputStep(response: ParsedUssdResponse) {
        val detected = detectInputType(response)
        val msg = "⚠️ Please enter $detected:"
        _statusMessage.value = msg
        onStatusUpdate?.invoke(msg)

        val data = mapOf(
            "type" to detected,
            "hint" to response.inputHint.ifBlank { "Enter value" },
            "response" to response
        )
        _isWaitingForInput.value = true
        onInputRequired?.invoke(data)
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

    private fun showOptionsToUser(response: ParsedUssdResponse, suggestedOption: UssdMenuOption? = null) {
        val data = mutableMapOf<String, Any>(
            "type" to "menu",
            "options" to response.options,
            "response" to response
        )
        if (suggestedOption != null) {
            data["suggestedOption"] = suggestedOption
        }
        _isWaitingForInput.value = true
        onInputRequired?.invoke(data)
    }

    private fun isGoalReached(response: ParsedUssdResponse, goal: String): Boolean {
        if (response.isTerminal) return true
        if (goal.isBlank()) return false
        val text = response.rawText.lowercase()
        return when (goal) {
            "check_balance" -> response.isBalance || text.contains("balance")
            "send_money" -> text.contains("sent") || text.contains("confirmed") || (response.amount != null && response.recipient != null)
            "buy_airtime" -> text.contains("airtime") && (text.contains("successful") || text.contains("bought"))
            else -> response.isTerminal || response.type == UssdResponseType.SUCCESS_RESULT
        }
    }

    private fun handleGoalReached(response: ParsedUssdResponse, goal: String) {
        val result = SmartFlowResult(
            amount = response.amount ?: response.balance ?: "",
            recipient = response.recipient,
            phoneNumber = null,
            mpesaCode = response.transactionId,
            timestamp = System.currentTimeMillis(),
            type = when (goal) {
                "check_balance" -> TransactionType.BALANCE
                "send_money" -> TransactionType.SENT
                "buy_airtime" -> TransactionType.AIRTIME
                "pay_bill" -> TransactionType.BILL_PAYMENT
                "withdraw" -> TransactionType.WITHDRAWAL
                else -> TransactionType.OTHER
            },
            summary = response.body.ifBlank { response.title },
            rawResponse = response.rawText
        )
        val msg = "✅ Completed: ${response.title.ifBlank { "Flow finished" }}"
        _statusMessage.value = msg
        onStatusUpdate?.invoke(msg)
        onComplete?.invoke(result)
        isEngineActive = false
        _isWaitingForInput.value = false
    }

    private fun needsUserInput(response: ParsedUssdResponse): Boolean {
        if (response.isPinRequest || response.inputType != UssdInputType.NONE) return true
        val text = response.rawText.lowercase()
        return text.contains("enter") || text.contains("pin") || text.contains("amount") || text.contains("number") || text.contains("weka")
    }

    private fun findOptionForGoal(options: List<UssdMenuOption>, goal: String): UssdMenuOption? {
        val keywords = GOAL_KEYWORDS[goal] ?: listOf(goal)
        for (option in options) {
            val labelLower = option.label.lowercase()
            if (keywords.any { labelLower.contains(it) }) {
                return option
            }
        }
        return null
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
