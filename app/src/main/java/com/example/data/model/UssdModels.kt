package com.example.data.model

import java.util.UUID

enum class UssdResponseType {
    MENU,
    BALANCE,
    TRANSACTION,
    PIN_REQUEST,
    INPUT_PROMPT,
    CONFIRMATION,
    SUCCESS_RESULT,
    ERROR_RESULT,
    INFO
}

enum class UssdInputType {
    NONE,
    PIN,
    AMOUNT,
    PHONE_NUMBER,
    NUMERIC,
    TEXT
}

enum class FlowStatus {
    ACTIVE,
    SUBMITTING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class UssdMenuOption(
    val id: String, // e.g. "1", "2", "00"
    val label: String, // e.g. "Buy Airtime", "Check Balance"
    val description: String = "",
    val isBack: Boolean = false,
    val isNext: Boolean = false
)

data class ParsedUssdResponse(
    val type: UssdResponseType,
    val title: String,
    val body: String,
    val balance: String? = null,
    val amount: String? = null,
    val recipient: String? = null,
    val sender: String? = null,
    val phoneNumber: String? = null,
    val confirmOption: String? = null,
    val cancelOption: String? = null,
    val transactionId: String? = null,
    val reference: String? = null,
    val options: List<UssdMenuOption> = emptyList(),
    val inputType: UssdInputType = UssdInputType.NONE,
    val inputHint: String = "",
    val rawText: String = "",
    val stepIndex: Int = 1,
    val isTerminal: Boolean = false,
    val isSuccess: Boolean = true
) {
    val isTransaction: Boolean
        get() = type == UssdResponseType.TRANSACTION || (!amount.isNullOrBlank() && !recipient.isNullOrBlank())
    
    val isBalance: Boolean
        get() = type == UssdResponseType.BALANCE || !balance.isNullOrBlank()
    
    val isMenu: Boolean
        get() = type == UssdResponseType.MENU || options.isNotEmpty()
    
    val isPinRequest: Boolean
        get() = type == UssdResponseType.PIN_REQUEST || inputType == UssdInputType.PIN
}

data class UssdFlowStepRecord(
    val stepIndex: Int,
    val rawPrompt: String,
    val parsedTitle: String,
    val parsedBody: String,
    val availableOptions: List<UssdMenuOption> = emptyList(),
    val inputType: UssdInputType = UssdInputType.NONE,
    val userResponse: String? = null,
    val selectedOptionLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isAutomated: Boolean = false
) {
    val displayInput: String
        get() = when {
            userResponse.isNullOrBlank() -> "-"
            inputType == UssdInputType.PIN -> "••••"
            else -> userResponse
        }

    val breadcrumbLabel: String
        get() = when {
            selectedOptionLabel != null -> "$userResponse. $selectedOptionLabel"
            userResponse != null -> if (inputType == UssdInputType.PIN) "PIN: ••••" else userResponse
            else -> "Step $stepIndex"
        }
}

data class UssdSessionFlow(
    val sessionId: String = UUID.randomUUID().toString(),
    val ussdCode: String,
    val simSlot: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: FlowStatus = FlowStatus.ACTIVE,
    val steps: List<UssdFlowStepRecord> = emptyList(),
    val finalSummary: String? = null,
    val errorMessage: String? = null
) {
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    val sequenceCsv: String
        get() = steps.mapNotNull { it.userResponse }.filter { it.isNotBlank() }.joinToString(",")

    val breadcrumbTrail: List<String>
        get() {
            val list = mutableListOf(ussdCode)
            steps.forEach { step ->
                if (step.userResponse != null) {
                    list.add(step.breadcrumbLabel)
                }
            }
            return list
        }
}

sealed interface UssdSessionState {
    data object Idle : UssdSessionState

    data class Dialing(
        val code: String,
        val simSlot: Int = 0,
        val activeFlow: UssdSessionFlow? = null
    ) : UssdSessionState

    data class ActiveSession(
        val code: String,
        val step: Int,
        val response: ParsedUssdResponse,
        val flow: UssdSessionFlow,
        val historySteps: List<StepLogItem> = emptyList(),
        val simSlot: Int = 0,
        val isAutomating: Boolean = false,
        val pendingInputs: List<String> = emptyList()
    ) : UssdSessionState

    data class Submitting(
        val input: String,
        val step: Int,
        val flow: UssdSessionFlow? = null
    ) : UssdSessionState

    data class Completed(
        val code: String,
        val summary: String,
        val response: ParsedUssdResponse,
        val flow: UssdSessionFlow? = null,
        val historySteps: List<StepLogItem> = emptyList(),
        val isSuccess: Boolean = true,
        val durationMs: Long = 0
    ) : UssdSessionState

    data class Failed(
        val code: String,
        val errorReason: String,
        val rawText: String,
        val flow: UssdSessionFlow? = null,
        val historySteps: List<StepLogItem> = emptyList()
    ) : UssdSessionState
}

data class StepLogItem(
    val stepNumber: Int,
    val promptText: String,
    val userInput: String?,
    val timestamp: Long = System.currentTimeMillis()
)

data class SimCardInfo(
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val subscriptionId: Int = -1,
    val isAvailable: Boolean = true
)

data class PresetUssdCategory(
    val id: String,
    val name: String,
    val icon: String
)
