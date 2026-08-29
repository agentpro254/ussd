package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.AppDatabase
import com.example.data.local.UssdHistoryItem
import com.example.data.model.FlowStatus
import com.example.data.model.ParsedUssdResponse
import com.example.data.model.StepLogItem
import com.example.data.model.UssdFlowStepRecord
import com.example.data.model.UssdSessionFlow
import com.example.data.model.UssdSessionState
import com.example.data.parser.UssdParser
import com.example.permissions.PermissionManager
import com.example.service.CodeeAccessibilityService
import com.example.service.CodeeOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.UUID

object UssdSessionManager {

    private const val TAG = "UssdSessionManager"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _sessionState = MutableStateFlow<UssdSessionState>(UssdSessionState.Idle)
    val sessionState: StateFlow<UssdSessionState> = _sessionState.asStateFlow()

    private val _currentFlow = MutableStateFlow<UssdSessionFlow?>(null)
    val currentFlow: StateFlow<UssdSessionFlow?> = _currentFlow.asStateFlow()

    private var activeAccessibilityService: WeakReference<CodeeAccessibilityService>? = null
    private var lastInputNode: WeakReference<AccessibilityNodeInfo>? = null
    private var lastSendButton: WeakReference<AccessibilityNodeInfo>? = null
    private var lastCancelButton: WeakReference<AccessibilityNodeInfo>? = null

    private var currentSessionCode: String = ""
    private var currentSessionStartTime: Long = 0
    private var currentStepLogs = mutableListOf<StepLogItem>()
    private var currentFlowSteps = mutableListOf<UssdFlowStepRecord>()
    private var currentSimSlot: Int = 0

    private var database: AppDatabase? = null

    fun initialize(db: AppDatabase) {
        this.database = db
    }

    fun setAccessibilityServiceInstance(service: CodeeAccessibilityService?) {
        activeAccessibilityService = if (service != null) WeakReference(service) else null
    }

    fun isSessionActive(): Boolean {
        return _sessionState.value !is UssdSessionState.Idle
    }

    /**
     * Start a USSD session internally within Codee
     */
    fun startUssdSession(
        context: Context,
        rawCode: String,
        simSlot: Int = 0,
        automatedSteps: List<String> = emptyList(),
        forceSimulation: Boolean = false
    ) {
        val cleanCode = rawCode.trim()
        currentSessionCode = cleanCode
        currentSessionStartTime = System.currentTimeMillis()
        currentStepLogs.clear()
        currentFlowSteps.clear()
        currentSimSlot = simSlot

        val flow = UssdSessionFlow(
            sessionId = UUID.randomUUID().toString(),
            ussdCode = cleanCode,
            simSlot = simSlot,
            isSimulation = true,
            startTime = currentSessionStartTime,
            status = FlowStatus.ACTIVE
        )
        _currentFlow.value = flow

        // Set state to Dialing briefly, then present the interactive internal USSD screen
        _sessionState.value = UssdSessionState.Dialing(cleanCode, simSlot, isSimulation = true, activeFlow = flow)

        scope.launch {
            kotlinx.coroutines.delay(400) // Realistic telecom carrier connection latency
            val initialResponseText = generateInitialCarrierResponse(cleanCode)
            val parsed = UssdParser.parse(initialResponseText, 1)

            val stepRecord = UssdFlowStepRecord(
                stepIndex = 1,
                rawPrompt = initialResponseText,
                parsedTitle = parsed.title,
                parsedBody = parsed.body,
                availableOptions = parsed.options,
                inputType = parsed.inputType,
                timestamp = System.currentTimeMillis()
            )
            currentFlowSteps.add(stepRecord)
            currentStepLogs.add(
                StepLogItem(
                    stepNumber = 1,
                    promptText = parsed.body.ifBlank { parsed.title },
                    userInput = null
                )
            )

            val updatedFlow = flow.copy(steps = currentFlowSteps.toList())
            _currentFlow.value = updatedFlow

            if (parsed.isTerminal) {
                val duration = System.currentTimeMillis() - currentSessionStartTime
                val finalFlow = updatedFlow.copy(
                    status = FlowStatus.COMPLETED,
                    endTime = System.currentTimeMillis(),
                    finalSummary = parsed.body.ifBlank { parsed.title }
                )
                _currentFlow.value = finalFlow
                _sessionState.value = UssdSessionState.Completed(
                    code = cleanCode,
                    summary = parsed.body.ifBlank { parsed.title },
                    response = parsed,
                    flow = finalFlow,
                    historySteps = currentStepLogs.toList(),
                    isSuccess = parsed.isSuccess,
                    isSimulation = true,
                    durationMs = duration
                )
                saveHistoryToDatabase(parsed, isSuccess = parsed.isSuccess, isSimulation = true, flow = finalFlow)
            } else {
                _sessionState.value = UssdSessionState.ActiveSession(
                    code = cleanCode,
                    step = 1,
                    response = parsed,
                    flow = updatedFlow,
                    historySteps = currentStepLogs.toList(),
                    isSimulation = true,
                    simSlot = simSlot,
                    isAutomating = automatedSteps.isNotEmpty(),
                    pendingInputs = automatedSteps
                )

                // If automated steps are supplied, execute them sequentially
                if (automatedSteps.isNotEmpty()) {
                    scope.launch {
                        for ((idx, stepInput) in automatedSteps.withIndex()) {
                            kotlinx.coroutines.delay(650)
                            if (_sessionState.value !is UssdSessionState.ActiveSession) {
                                break
                            }
                            submitStepResponse(stepInput)
                            kotlinx.coroutines.delay(650)
                        }
                    }
                }
            }
        }
    }

    private fun generateInitialCarrierResponse(code: String): String {
        return when {
            code == "*334#" || code.startsWith("*334") -> {
                "M-PESA Main Menu (Page 1/2)\n1. Send Money\n2. Withdraw Cash\n3. Buy Airtime\n4. Pay Bill\n5. Lipa Na M-PESA\n6. My Account\n99. Next\n0. Exit"
            }
            code == "*144#" || code.startsWith("*144") -> {
                "Airtime Balance: Your main account balance is KES 420.50. Valid until 15/09/2026. Free 50 SMS available."
            }
            code == "*544#" || code.startsWith("*544") -> {
                "Safaricom Tunukiwa & Data:\n1. 1.5GB 3hr @ Ksh 50\n2. 2.5GB 24hr @ Ksh 100\n3. 10GB 30days @ Ksh 1000\n4. Check Data Balance\n99. Next\n0. Exit"
            }
            code == "*141#" || code == "*185#" || code.startsWith("*185") -> {
                "Airtel Money & Self-Care:\n1. Send Money\n2. Buy Airtime\n3. Withdraw Cash\n4. Pay Bills & Utilities\n5. My Account & Balance\n99. Next\n0. Exit"
            }
            code == "*123#" || code.startsWith("*123") -> {
                "Telkom Kenya Selfcare:\n1. Check Balance\n2. Top Up Airtime\n3. Buy Data Bundles\n4. T-Kash Mobile Money\n0. Exit"
            }
            code == "*247#" || code.startsWith("*247") -> {
                "Equity Eazzy 247:\n1. Send Money\n2. Withdraw Cash\n3. Check Balance\n4. Mini Statement\n5. Loans & EquiLoan\n99. Next\n0. Exit"
            }
            code == "*522#" || code.startsWith("*522") -> {
                "KCB Banking:\n1. Funds Transfer\n2. Balance Enquiry\n3. Mini Statement\n4. KCB M-PESA Loan\n98. Back\n0. Exit"
            }
            code == "*667#" || code.startsWith("*667") -> {
                "Co-op Bank MCo-op Cash:\n1. Send Money\n2. Account Balance\n3. Mini Statement\n4. Pay Bill\n98. Back\n0. Exit"
            }
            else -> {
                "Carrier USSD Service ($code):\n1. Check Account Status\n2. Top Up / Recharge\n3. Active Subscriptions\n4. Customer Support\n98. Back\n0. Exit"
            }
        }
    }

    /**
     * Called by CodeeAccessibilityService when a live USSD dialog is captured from the carrier.
     * The app purely parses and displays the response, and waits for user interaction.
     */
    fun onUssdDialogCaptured(
        text: String,
        inputNode: AccessibilityNodeInfo?,
        sendButton: AccessibilityNodeInfo?,
        cancelButton: AccessibilityNodeInfo?
    ) {
        lastInputNode = if (inputNode != null) WeakReference(inputNode) else null
        lastSendButton = if (sendButton != null) WeakReference(sendButton) else null
        lastCancelButton = if (cancelButton != null) WeakReference(cancelButton) else null

        val currentStep = currentFlowSteps.size + 1
        val parsed = UssdParser.parse(text, currentStep)

        currentStepLogs.add(
            StepLogItem(
                stepNumber = currentStep,
                promptText = parsed.body.ifBlank { parsed.title },
                userInput = null
            )
        )

        val stepRecord = UssdFlowStepRecord(
            stepIndex = currentStep,
            rawPrompt = text,
            parsedTitle = parsed.title,
            parsedBody = parsed.body,
            availableOptions = parsed.options,
            inputType = parsed.inputType,
            timestamp = System.currentTimeMillis()
        )
        currentFlowSteps.add(stepRecord)

        val updatedFlow = (_currentFlow.value ?: UssdSessionFlow(
            ussdCode = currentSessionCode,
            simSlot = currentSimSlot,
            isSimulation = false
        )).copy(steps = currentFlowSteps.toList())
        _currentFlow.value = updatedFlow

        if (parsed.isTerminal) {
            val duration = System.currentTimeMillis() - currentSessionStartTime
            val finalFlow = updatedFlow.copy(
                status = FlowStatus.COMPLETED,
                endTime = System.currentTimeMillis(),
                finalSummary = parsed.body.ifBlank { parsed.title }
            )
            _currentFlow.value = finalFlow
            _sessionState.value = UssdSessionState.Completed(
                code = currentSessionCode,
                summary = parsed.body.ifBlank { parsed.title },
                response = parsed,
                flow = finalFlow,
                historySteps = currentStepLogs.toList(),
                isSuccess = parsed.isSuccess,
                isSimulation = false,
                durationMs = duration
            )
            saveHistoryToDatabase(parsed, isSuccess = parsed.isSuccess, isSimulation = false, flow = finalFlow)
            return
        }

        // Live interactive USSD session: simply display and wait for user's manual choice
        _sessionState.value = UssdSessionState.ActiveSession(
            code = currentSessionCode,
            step = currentStep,
            response = parsed,
            flow = updatedFlow,
            historySteps = currentStepLogs.toList(),
            isSimulation = false,
            simSlot = currentSimSlot,
            isAutomating = false,
            pendingInputs = emptyList()
        )
    }

    /**
     * Submit an option or text response to the active session when the user clicks/types
     */
    fun submitStepResponse(userInput: String) {
        val currentState = _sessionState.value
        val step = if (currentState is UssdSessionState.ActiveSession) currentState.step else currentFlowSteps.size

        if (currentStepLogs.isNotEmpty()) {
            val last = currentStepLogs.last()
            currentStepLogs[currentStepLogs.lastIndex] = last.copy(userInput = userInput)
        }

        if (currentFlowSteps.isNotEmpty()) {
            val lastStep = currentFlowSteps.last()
            val matchedOption = lastStep.availableOptions.firstOrNull { it.id == userInput }
            currentFlowSteps[currentFlowSteps.lastIndex] = lastStep.copy(
                userResponse = userInput,
                selectedOptionLabel = matchedOption?.label,
                isAutomated = false
            )
            _currentFlow.value = _currentFlow.value?.copy(steps = currentFlowSteps.toList())
        }

        _sessionState.value = UssdSessionState.Submitting(
            input = userInput,
            step = step,
            flow = _currentFlow.value,
            isSimulation = true
        )

        val service = activeAccessibilityService?.get()
        val inputNode = lastInputNode?.get()
        val sendBtn = lastSendButton?.get()

        if (service != null && inputNode != null) {
            val success = service.submitTextToActiveDialog(inputNode, userInput, sendBtn)
            if (!success) {
                Log.w(TAG, "Accessibility node submit failed, trying fallback")
            }
        } else {
            // Run internal interactive engine
            scope.launch {
                kotlinx.coroutines.delay(350)
                val nextStepIndex = step + 1
                val nextResponseText = generateNextStepResponse(currentSessionCode, nextStepIndex, userInput, currentStepLogs)
                val parsed = UssdParser.parse(nextResponseText, nextStepIndex)

                val stepRecord = UssdFlowStepRecord(
                    stepIndex = nextStepIndex,
                    rawPrompt = nextResponseText,
                    parsedTitle = parsed.title,
                    parsedBody = parsed.body,
                    availableOptions = parsed.options,
                    inputType = parsed.inputType,
                    timestamp = System.currentTimeMillis()
                )
                currentFlowSteps.add(stepRecord)
                currentStepLogs.add(
                    StepLogItem(
                        stepNumber = nextStepIndex,
                        promptText = parsed.body.ifBlank { parsed.title },
                        userInput = null
                    )
                )

                val updatedFlow = (_currentFlow.value ?: UssdSessionFlow(
                    ussdCode = currentSessionCode,
                    simSlot = currentSimSlot,
                    isSimulation = true
                )).copy(steps = currentFlowSteps.toList())
                _currentFlow.value = updatedFlow

                if (parsed.isTerminal) {
                    val duration = System.currentTimeMillis() - currentSessionStartTime
                    val finalFlow = updatedFlow.copy(
                        status = FlowStatus.COMPLETED,
                        endTime = System.currentTimeMillis(),
                        finalSummary = parsed.body.ifBlank { parsed.title }
                    )
                    _currentFlow.value = finalFlow
                    _sessionState.value = UssdSessionState.Completed(
                        code = currentSessionCode,
                        summary = parsed.body.ifBlank { parsed.title },
                        response = parsed,
                        flow = finalFlow,
                        historySteps = currentStepLogs.toList(),
                        isSuccess = parsed.isSuccess,
                        isSimulation = true,
                        durationMs = duration
                    )
                    saveHistoryToDatabase(parsed, isSuccess = parsed.isSuccess, isSimulation = true, flow = finalFlow)
                } else {
                    _sessionState.value = UssdSessionState.ActiveSession(
                        code = currentSessionCode,
                        step = nextStepIndex,
                        response = parsed,
                        flow = updatedFlow,
                        historySteps = currentStepLogs.toList(),
                        isSimulation = true,
                        simSlot = currentSimSlot,
                        isAutomating = false,
                        pendingInputs = emptyList()
                    )
                }
            }
        }
    }

    private fun generateNextStepResponse(
        code: String,
        stepIndex: Int,
        lastInput: String,
        logs: List<StepLogItem>
    ): String {
        // Universal Smart Navigation Intercepts
        if (lastInput == "0" && (logs.lastOrNull()?.promptText?.contains("Exit", ignoreCase = true) == true || stepIndex == 2)) {
            return "Session terminated by user. Thank you for using Codee USSD Selfcare."
        }
        if (lastInput == "0" || lastInput.contains("main", ignoreCase = true)) {
            return generateInitialCarrierResponse(code)
        }
        if (lastInput == "98" || lastInput.contains("back", ignoreCase = true) || lastInput.contains("rudi", ignoreCase = true)) {
            return generateInitialCarrierResponse(code)
        }
        if (lastInput == "99" || lastInput.contains("next", ignoreCase = true) || lastInput.contains("mbele", ignoreCase = true)) {
            if (code.contains("334") || code == "*334#") {
                return "M-PESA Menu (Page 2/2)\n7. Fuliza M-PESA\n8. M-Shwari & KCB M-PESA\n9. Global Pay (Virtual Visa)\n10. Halal M-PESA\n98. Back\n0. Main Menu"
            } else if (code.contains("544")) {
                return "Safaricom Data & Packs (Page 2/2)\n5. Monthly 25GB @ Ksh 2000\n6. YouTube & Social Packs\n7. PostPay Unlimited\n98. Back\n0. Main Menu"
            } else {
                return "Additional Services (Page 2/2):\n5. Statements & Reports\n6. Tariff & Roaming Plans\n7. Security & Pin Settings\n98. Back\n0. Main Menu"
            }
        }

        val firstInput = logs.firstOrNull()?.userInput ?: lastInput

        if (code.contains("334") || code == "*334#") {
            // M-PESA Flow
            if (firstInput == "1" || firstInput.contains("send", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "Enter recipient phone number (e.g. 0712345678):"
                    3 -> "Enter Amount in KES (Min 10, Max 250,000):"
                    4 -> "Enter 4-digit M-PESA PIN:"
                    5 -> "Send KES 15,250.00 to EMMAH KILONZO 0708814308? Fee KES 0.00.\n1. Confirm & Send\n2. Cancel"
                    else -> "TFB517W619 Confirmed. Ksh 15,250.00 sent to EMMAH KILONZO 0708814308 on 28/8/26 at 2:21 PM. New M-PESA balance is Ksh 34,210.00. Transaction cost, Ksh 0.00."
                }
            } else if (firstInput == "2" || firstInput.contains("withdraw", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "Enter Agent Number (6 digits):"
                    3 -> "Enter Store Number (if applicable) or 0:"
                    4 -> "Enter Amount to Withdraw (KES):"
                    5 -> "Enter 4-digit M-PESA PIN:"
                    else -> "WTD892104 Confirmed. Ksh 5,000.00 withdrawn from Agent 248190 on 28/8/26 at 2:22 PM. New M-PESA balance is Ksh 29,210.00."
                }
            } else if (firstInput == "3" || firstInput.contains("airtime", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "Buy Airtime for:\n1. My Phone\n2. Other Phone\n98. Back"
                    3 -> "Enter Amount in KES:"
                    4 -> "Enter 4-digit M-PESA PIN:"
                    else -> "AIR441920 Confirmed. Ksh 200.00 airtime purchased successfully on 28/8/26 at 2:23 PM. Balance Ksh 29,010.00."
                }
            } else if (firstInput == "4" || firstInput.contains("bill", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "Enter Business Number (Paybill):"
                    3 -> "Enter Account Number:"
                    4 -> "Enter Amount in KES:"
                    5 -> "Enter 4-digit M-PESA PIN:"
                    else -> "PBL991204 Confirmed. Ksh 1,200.00 paid to KPLC PREPAID 888888 for account 142890123."
                }
            } else if (firstInput == "5" || firstInput.contains("lipa", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "1. Buy Goods and Services (Till)\n2. Pochi La Biashara\n98. Back"
                    3 -> "Enter Till / Merchant Number:"
                    4 -> "Enter Amount in KES:"
                    5 -> "Enter 4-digit M-PESA PIN:"
                    else -> "LIP812034 Confirmed. Ksh 850.00 paid to SUPERMARKET STORE 551020 on 28/8/26."
                }
            } else if (firstInput == "6" || firstInput.contains("account", ignoreCase = true) || firstInput.contains("balance", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "My Account:\n1. Check Balance\n2. Mini Statement\n3. Change PIN\n4. Reset PIN\n98. Back\n0. Main Menu"
                    3 -> "Enter 4-digit M-PESA PIN to view balance:"
                    else -> "M-PESA Balance: Your balance is Ksh 15,250.00. Available Fuliza Limit is Ksh 12,000.00. Transacted securely via Codee."
                }
            } else if (firstInput == "7" || firstInput.contains("fuliza", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "Fuliza M-PESA:\n1. Opt In\n2. Check Limit & Balance\n3. Mini Statement\n98. Back\n0. Main Menu"
                    3 -> "Enter 4-digit M-PESA PIN:"
                    else -> "Fuliza M-PESA Limit: Available limit is Ksh 12,000.00. Outstanding balance Ksh 0.00."
                }
            } else if (firstInput == "8" || firstInput.contains("shwari", ignoreCase = true)) {
                return when (stepIndex) {
                    2 -> "M-Shwari Services:\n1. Send to M-Shwari\n2. Withdraw from M-Shwari\n3. Lock Savings Account\n4. Loan Request\n98. Back\n0. Main Menu"
                    3 -> "Enter 4-digit M-PESA PIN:"
                    else -> "M-Shwari Balance: Savings account balance is Ksh 8,450.00. Available loan limit is Ksh 15,000.00."
                }
            }
        }

        // Default multi-step fallback
        return when (stepIndex) {
            2 -> "Option selected ($lastInput). Please enter requested details or reference number:"
            3 -> "Enter confirmation Amount or Security PIN:"
            4 -> "Confirm operation ($lastInput)?\n1. Confirm & Execute\n2. Cancel"
            else -> "TRX-${System.currentTimeMillis().toString().takeLast(6)} Confirmed. Service request successfully completed via Codee."
        }
    }

    fun dismissSession(context: Context? = null) {
        val currentState = _sessionState.value
        if (currentState is UssdSessionState.ActiveSession && !currentState.isSimulation) {
            val service = activeAccessibilityService?.get()
            val cancelBtn = lastCancelButton?.get()
            service?.dismissActiveDialog(cancelBtn)
        }
        if (context != null) {
            CodeeOverlayService.stop(context)
        }
        _currentFlow.value = _currentFlow.value?.copy(
            status = FlowStatus.CANCELLED,
            endTime = System.currentTimeMillis()
        )
        _sessionState.value = UssdSessionState.Idle
    }

    private fun saveHistoryToDatabase(
        response: ParsedUssdResponse,
        isSuccess: Boolean,
        isSimulation: Boolean,
        flow: UssdSessionFlow? = null
    ) {
        val db = database ?: return
        val duration = System.currentTimeMillis() - currentSessionStartTime
        val sequenceFormatted = flow?.breadcrumbTrail?.joinToString(" ➔ ") ?: ""
        val stepsSummaryText = flow?.steps?.joinToString("\n") { step ->
            "Step ${step.stepIndex}: ${step.parsedTitle} -> Input: ${step.displayInput}"
        } ?: ""

        scope.launch(Dispatchers.IO) {
            try {
                val item = UssdHistoryItem(
                    timestamp = System.currentTimeMillis(),
                    ussdCode = currentSessionCode,
                    serviceName = response.title,
                    summary = response.body.ifBlank { response.title },
                    rawLogs = response.rawText,
                    stepCount = currentFlowSteps.size.coerceAtLeast(1),
                    durationMs = duration,
                    isSuccess = isSuccess,
                    isSimulation = isSimulation,
                    responseSequence = sequenceFormatted,
                    stepsSummary = stepsSummaryText,
                    transactionId = response.transactionId,
                    amount = response.amount,
                    recipient = response.recipient,
                    rawResponseText = response.rawText
                )
                db.ussdDao().insertHistory(item)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save USSD history to DB", e)
            }
        }
    }
}
