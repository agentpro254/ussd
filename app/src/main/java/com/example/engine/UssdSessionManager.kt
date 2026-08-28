package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.AppDatabase
import com.example.data.local.UssdHistoryItem
import com.example.data.model.FlowStatus
import com.example.data.model.ParsedUssdResponse
import com.example.data.model.StepLogItem
import com.example.data.model.UssdFlowStepRecord
import com.example.data.model.UssdInputType
import com.example.data.model.UssdMenuOption
import com.example.data.model.UssdResponseType
import com.example.data.model.UssdSessionFlow
import com.example.data.model.UssdSessionState
import com.example.data.parser.UssdParser
import com.example.permissions.PermissionManager
import com.example.service.CodeeAccessibilityService
import com.example.service.CodeeOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var pendingAutoSteps = mutableListOf<String>()

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
     * Start a real USSD call or fallback to simulator if permissions are missing or simulated
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
        pendingAutoSteps.clear()
        pendingAutoSteps.addAll(automatedSteps)

        val flow = UssdSessionFlow(
            sessionId = UUID.randomUUID().toString(),
            ussdCode = cleanCode,
            simSlot = simSlot,
            isSimulation = forceSimulation,
            startTime = currentSessionStartTime,
            status = FlowStatus.ACTIVE
        )
        _currentFlow.value = flow

        val hasCallPermission = PermissionManager.isCallPhoneGranted(context)

        if (forceSimulation || !hasCallPermission) {
            // Run interactive simulation mode
            startSimulationSession(cleanCode, simSlot, flow)
            return
        }

        // Real USSD session launch
        _sessionState.value = UssdSessionState.Dialing(cleanCode, simSlot, isSimulation = false, activeFlow = flow)
        if (PermissionManager.isOverlayPermissionGranted(context)) {
            CodeeOverlayService.start(context)
        }

        try {
            dialNativeUssd(context, cleanCode, simSlot)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dial USSD code", e)
            val failedFlow = flow.copy(
                status = FlowStatus.FAILED,
                endTime = System.currentTimeMillis(),
                errorMessage = e.localizedMessage ?: "Failed to dial USSD code"
            )
            _currentFlow.value = failedFlow
            _sessionState.value = UssdSessionState.Failed(
                code = cleanCode,
                errorReason = e.localizedMessage ?: "Failed to dial USSD code",
                rawText = "Failed to launch native dialer",
                flow = failedFlow,
                isSimulation = false
            )
        }
    }

    private fun dialNativeUssd(context: Context, ussdCode: String, simSlot: Int) {
        val encodedHash = Uri.encode("#")
        val formattedCode = ussdCode.replace("#", encodedHash)
        val uri = Uri.parse("tel:$formattedCode")
        val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.extra.slot", simSlot)
            putExtra("simSlot", simSlot)
            putExtra("slot", simSlot)
            putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", simSlot)
        }
        context.startActivity(callIntent)
    }

    /**
     * Called by CodeeAccessibilityService when dialog text is captured
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

        // Interactive step progression - wait for user input
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
     * Submit an option or text response to the active session
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
                isAutomated = currentState is UssdSessionState.ActiveSession && currentState.isAutomating
            )
            _currentFlow.value = _currentFlow.value?.copy(steps = currentFlowSteps.toList())
        }

        if (currentState is UssdSessionState.ActiveSession && currentState.isSimulation) {
            submitSimulationResponse(userInput)
            return
        }

        _sessionState.value = UssdSessionState.Submitting(
            input = userInput,
            step = step,
            flow = _currentFlow.value,
            isSimulation = false
        )

        val service = activeAccessibilityService?.get()
        val inputNode = lastInputNode?.get()
        val sendBtn = lastSendButton?.get()

        if (service != null) {
            val success = service.submitTextToActiveDialog(inputNode, userInput, sendBtn)
            if (!success) {
                Log.w(TAG, "Accessibility node submit failed, trying fallback click")
            }
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

    // ==========================================
    // INTERACTIVE USSD SIMULATOR ENGINE
    // Emulates realistic carrier USSD trees
    // ==========================================

    private var simulationTreeState: SimulationTreeState? = null

    private data class SimulationTreeState(
        val code: String,
        var currentStage: Int = 1,
        var selectedCategory: String = "",
        var enteredPhone: String = "",
        var enteredAmount: String = "",
        var enteredPin: String = "",
        var customSteps: List<String> = emptyList()
    )

    fun startSimulationSession(code: String, simSlot: Int = 0, initialFlow: UssdSessionFlow? = null) {
        val flow = initialFlow ?: UssdSessionFlow(
            ussdCode = code,
            simSlot = simSlot,
            isSimulation = true,
            startTime = System.currentTimeMillis(),
            status = FlowStatus.ACTIVE
        )
        _currentFlow.value = flow
        _sessionState.value = UssdSessionState.Dialing(code, simSlot, isSimulation = true, activeFlow = flow)
        simulationTreeState = SimulationTreeState(code = code)

        scope.launch {
            delay(700) // Simulate cellular connection negotiation
            val initialResponse = generateSimulationResponse(simulationTreeState!!, "")
            currentStepLogs.add(
                StepLogItem(
                    stepNumber = 1,
                    promptText = initialResponse.body.ifBlank { initialResponse.title },
                    userInput = null
                )
            )

            val stepRecord = UssdFlowStepRecord(
                stepIndex = 1,
                rawPrompt = initialResponse.rawText,
                parsedTitle = initialResponse.title,
                parsedBody = initialResponse.body,
                availableOptions = initialResponse.options,
                inputType = initialResponse.inputType,
                timestamp = System.currentTimeMillis()
            )
            currentFlowSteps.add(stepRecord)
            val updatedFlow = flow.copy(steps = currentFlowSteps.toList())
            _currentFlow.value = updatedFlow

            // Interactive step progression for simulation
            _sessionState.value = UssdSessionState.ActiveSession(
                code = code,
                step = 1,
                response = initialResponse,
                flow = updatedFlow,
                historySteps = currentStepLogs.toList(),
                isSimulation = true,
                simSlot = simSlot,
                isAutomating = false,
                pendingInputs = emptyList()
            )
        }
    }

    private fun submitSimulationResponse(userInput: String) {
        val state = simulationTreeState ?: return
        val step = currentFlowSteps.size
        _sessionState.value = UssdSessionState.Submitting(
            input = userInput,
            step = step,
            flow = _currentFlow.value,
            isSimulation = true
        )

        scope.launch {
            delay(650) // Realistic USSD network delay
            state.currentStage++
            val response = generateSimulationResponse(state, userInput)

            val nextStep = currentFlowSteps.size + 1
            currentStepLogs.add(
                StepLogItem(
                    stepNumber = nextStep,
                    promptText = response.body.ifBlank { response.title },
                    userInput = null
                )
            )

            val stepRecord = UssdFlowStepRecord(
                stepIndex = nextStep,
                rawPrompt = response.rawText,
                parsedTitle = response.title,
                parsedBody = response.body,
                availableOptions = response.options,
                inputType = response.inputType,
                timestamp = System.currentTimeMillis()
            )
            currentFlowSteps.add(stepRecord)

            val updatedFlow = (_currentFlow.value ?: UssdSessionFlow(
                ussdCode = currentSessionCode,
                simSlot = currentSimSlot,
                isSimulation = true
            )).copy(steps = currentFlowSteps.toList())
            _currentFlow.value = updatedFlow

            if (response.isTerminal) {
                val duration = System.currentTimeMillis() - currentSessionStartTime
                val finalFlow = updatedFlow.copy(
                    status = FlowStatus.COMPLETED,
                    endTime = System.currentTimeMillis(),
                    finalSummary = response.body.ifBlank { response.title }
                )
                _currentFlow.value = finalFlow
                _sessionState.value = UssdSessionState.Completed(
                    code = currentSessionCode,
                    summary = response.body.ifBlank { response.title },
                    response = response,
                    flow = finalFlow,
                    historySteps = currentStepLogs.toList(),
                    isSuccess = response.isSuccess,
                    isSimulation = true,
                    durationMs = duration
                )
                saveHistoryToDatabase(response, isSuccess = response.isSuccess, isSimulation = true, flow = finalFlow)
            } else {
                _sessionState.value = UssdSessionState.ActiveSession(
                    code = currentSessionCode,
                    step = nextStep,
                    response = response,
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

    private fun generateSimulationResponse(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        val code = state.code.lowercase()
        return when {
            code.contains("#06#") || code.contains("06") -> {
                ParsedUssdResponse(
                    type = UssdResponseType.INFO,
                    title = "Device IMEI",
                    body = "IMEI 1: 354892019482012\nIMEI 2: 354892019482019\nSerial No: R58M81948LA\nStatus: Registered",
                    rawText = "IMEI: 354892019482012 / 354892019482019",
                    stepIndex = 1,
                    isTerminal = true,
                    isSuccess = true
                )
            }
            code.contains("144") -> {
                ParsedUssdResponse(
                    type = UssdResponseType.SUCCESS_RESULT,
                    title = "Safaricom Airtime Balance",
                    body = "Main Balance: KES 342.50\nBonga Points: 1,840 pts\nData Bundle: 2.4 GB (Valid till 30/08 23:59)\nTunukiwa Minutes: 45 Mins",
                    rawText = "Main Bal: KES 342.50. Bonga: 1840 pts. Data: 2.4GB. Min: 45",
                    stepIndex = 1,
                    isTerminal = true,
                    isSuccess = true
                )
            }
            code.contains("133") -> {
                ParsedUssdResponse(
                    type = UssdResponseType.SUCCESS_RESULT,
                    title = "Airtel Airtime Balance",
                    body = "Main Account: KES 180.00\nAirtel Voice: 28 Mins\nAirtel 4G: 1.8 GB (Expires in 5 days)\nBonus: KES 50.00",
                    rawText = "Airtel Bal: KES 180.00. Voice: 28 mins. Data: 1.8GB",
                    stepIndex = 1,
                    isTerminal = true,
                    isSuccess = true
                )
            }
            code.contains("188") -> {
                ParsedUssdResponse(
                    type = UssdResponseType.SUCCESS_RESULT,
                    title = "Telkom Kenya Self-Service",
                    body = "Airtime Bal: KES 95.00\nT-Kash Wallet: KES 1,420.00\nData Bundle: 4.5 GB\nNight Owl Pass: Active (12AM - 6AM)",
                    rawText = "Telkom Bal: KES 95.00. T-Kash: KES 1,420.00. Data: 4.5GB",
                    stepIndex = 1,
                    isTerminal = true,
                    isSuccess = true
                )
            }
            code.contains("977") -> {
                generateKplcPowerFlow(state, lastInput)
            }
            code.contains("222") -> {
                generateEcitizenFlow(state, lastInput)
            }
            code.contains("185") || code.contains("momo") || code.contains("money") -> {
                generateMtnMomoFlow(state, lastInput)
            }
            code.contains("334") || code.contains("mpesa") -> {
                generateMpesaFlow(state, lastInput)
            }
            code.contains("141") || code.contains("131") || code.contains("180") || code.contains("data") -> {
                generateDataBundlesFlow(state, lastInput)
            }
            code.contains("247") || code.contains("522") || code.contains("667") || code.contains("737") || code.contains("bank") -> {
                generateBankingFlow(state, lastInput)
            }
            else -> {
                generateGeneralTelecomFlow(state, lastInput)
            }
        }
    }

    private fun generateKplcPowerFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "Kenya Power (KPLC)",
                body = "Welcome to KPLC M-Huduma. Select a service:",
                options = listOf(
                    UssdMenuOption("1", "Buy Prepaid Electricity Tokens"),
                    UssdMenuOption("2", "Postpaid Bill Statement & Pay"),
                    UssdMenuOption("3", "Report Power Outage"),
                    UssdMenuOption("4", "Check Last 3 Purchased Tokens")
                ),
                rawText = "1. Buy Tokens\n2. Postpaid Bill\n3. Report Outage\n4. Last Tokens",
                stepIndex = 1
            )
            2 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "KPLC Meter Number",
                body = "Enter your 11-digit Prepaid Meter Number or Account:",
                inputType = UssdInputType.PHONE_NUMBER,
                inputHint = "e.g. 14283920194",
                rawText = "Enter 11-digit Meter No:",
                stepIndex = 2
            )
            3 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "Token Purchase Amount",
                body = "Enter amount in KES to purchase tokens (Min KES 100):",
                inputType = UssdInputType.AMOUNT,
                inputHint = "Amount in KES (e.g. 500)",
                rawText = "Enter amount in KES:",
                stepIndex = 3
            )
            else -> ParsedUssdResponse(
                type = UssdResponseType.SUCCESS_RESULT,
                title = "KPLC Tokens Generated",
                body = "Token: 4829-1048-2910-4829-1048\nUnits: 34.2 kWh\nAmount: KES 500.00\nMeter: 14283920194\nFuel Energy Charge: KES 84.10",
                rawText = "Token: 4829-1048-2910-4829-1048. Units: 34.2 kWh. Amount: KES 500.00",
                stepIndex = state.currentStage,
                isTerminal = true,
                isSuccess = true
            )
        }
    }

    private fun generateEcitizenFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "eCitizen Kenya Portal",
                body = "Welcome to Government of Kenya eCitizen Services:",
                options = listOf(
                    UssdMenuOption("1", "NTSA (Driving Licence & Logbooks)"),
                    UssdMenuOption("2", "Immigration (Passport Status)"),
                    UssdMenuOption("3", "Civil Registration (Birth/Death)"),
                    UssdMenuOption("4", "Business Registration (BRS)"),
                    UssdMenuOption("5", "Pay eCitizen Invoice / Reference")
                ),
                rawText = "1. NTSA\n2. Immigration\n3. Civil Reg\n4. Business\n5. Pay Invoice",
                stepIndex = 1
            )
            2 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "eCitizen Reference / ID",
                body = "Enter your National ID Number or Invoice Reference Number:",
                inputType = UssdInputType.TEXT,
                inputHint = "e.g. 38291048 or PR-94820",
                rawText = "Enter National ID or Invoice Ref:",
                stepIndex = 2
            )
            else -> ParsedUssdResponse(
                type = UssdResponseType.SUCCESS_RESULT,
                title = "eCitizen Record Found",
                body = "Invoice Ref: PR-94820\nService: NTSA Smart Driving Licence\nStatus: Pending Payment\nAmount: KES 3,050.00\nPay via M-PESA Paybill 222222.",
                rawText = "Invoice: PR-94820. Smart DL. Status: Ready for collection.",
                stepIndex = state.currentStage,
                isTerminal = true,
                isSuccess = true
            )
        }
    }

    private fun generateMtnMomoFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "MoMo Telecom Service",
                body = "Welcome to Mobile Money. Select a service to continue:",
                options = listOf(
                    UssdMenuOption("1", "Send Money (P2P)"),
                    UssdMenuOption("2", "Buy Airtime / Bundles"),
                    UssdMenuOption("3", "Pay Utilities & Bills"),
                    UssdMenuOption("4", "Check Wallet Balance"),
                    UssdMenuOption("00", "Exit", isBack = true)
                ),
                rawText = "Welcome to MoMo.\n1. Send Money\n2. Buy Airtime\n3. Pay Bills\n4. Check Balance\n00. Exit",
                stepIndex = 1
            )
            2 -> {
                state.selectedCategory = lastInput
                if (lastInput == "4") {
                    // Check balance prompt PIN
                    ParsedUssdResponse(
                        type = UssdResponseType.INPUT_PROMPT,
                        title = "Security PIN",
                        body = "Enter your 4-digit MoMo PIN to check account balance:",
                        inputType = UssdInputType.PIN,
                        inputHint = "4-digit PIN",
                        rawText = "Enter 4-digit PIN to check balance:",
                        stepIndex = 2
                    )
                } else if (lastInput == "2") {
                    ParsedUssdResponse(
                        type = UssdResponseType.MENU,
                        title = "Buy Airtime",
                        body = "Choose recipient for airtime:",
                        options = listOf(
                            UssdMenuOption("1", "For My Number"),
                            UssdMenuOption("2", "For Other Number"),
                            UssdMenuOption("0", "Back", isBack = true)
                        ),
                        rawText = "1. My Number\n2. Other Number\n0. Back",
                        stepIndex = 2
                    )
                } else {
                    ParsedUssdResponse(
                        type = UssdResponseType.INPUT_PROMPT,
                        title = "Recipient Mobile Number",
                        body = "Enter the 10-digit mobile number of the recipient:",
                        inputType = UssdInputType.PHONE_NUMBER,
                        inputHint = "e.g. 0772123456",
                        rawText = "Enter 10-digit recipient phone number:",
                        stepIndex = 2
                    )
                }
            }
            3 -> {
                if (state.selectedCategory == "4") {
                    // Direct terminal balance result
                    ParsedUssdResponse(
                        type = UssdResponseType.SUCCESS_RESULT,
                        title = "Wallet Balance",
                        body = "Your current MoMo balance is \$245.80. Available credit limit: \$50.00. Fee: \$0.00.",
                        rawText = "Your MoMo balance is $245.80. Available credit: $50.00.",
                        stepIndex = 3,
                        isTerminal = true,
                        isSuccess = true
                    )
                } else {
                    state.enteredPhone = if (lastInput.isNotBlank()) lastInput else "0772123456"
                    ParsedUssdResponse(
                        type = UssdResponseType.INPUT_PROMPT,
                        title = "Transfer Amount",
                        body = "Enter amount in USD to send to ${state.enteredPhone} (Min \$1, Max \$2,000):",
                        inputType = UssdInputType.AMOUNT,
                        inputHint = "Amount (e.g. 50)",
                        rawText = "Enter amount in USD:",
                        stepIndex = 3
                    )
                }
            }
            4 -> {
                state.enteredAmount = if (lastInput.isNotBlank()) lastInput else "25.00"
                ParsedUssdResponse(
                    type = UssdResponseType.CONFIRMATION,
                    title = "Confirm Transaction",
                    body = "Send \$${state.enteredAmount} to John Doe (${state.enteredPhone})?\nCharge: \$0.50. Total: \$${(state.enteredAmount.toDoubleOrNull() ?: 25.0) + 0.50}",
                    options = listOf(
                        UssdMenuOption("1", "Confirm & Send"),
                        UssdMenuOption("2", "Cancel Transfer", isBack = true)
                    ),
                    rawText = "Confirm \$${state.enteredAmount} to John Doe (${state.enteredPhone})? Fee: \$0.50.\n1. Confirm\n2. Cancel",
                    stepIndex = 4
                )
            }
            5 -> {
                if (lastInput == "2") {
                    ParsedUssdResponse(
                        type = UssdResponseType.ERROR_RESULT,
                        title = "Transaction Cancelled",
                        body = "You cancelled the MoMo transfer session.",
                        rawText = "Transaction cancelled by user.",
                        stepIndex = 5,
                        isTerminal = true,
                        isSuccess = false
                    )
                } else {
                    ParsedUssdResponse(
                        type = UssdResponseType.INPUT_PROMPT,
                        title = "Enter Security PIN",
                        body = "Enter your 4-digit PIN to authorize payment of \$${state.enteredAmount}:",
                        inputType = UssdInputType.PIN,
                        inputHint = "4-digit secret PIN",
                        rawText = "Enter 4-digit PIN to authorize payment:",
                        stepIndex = 5
                    )
                }
            }
            else -> {
                ParsedUssdResponse(
                    type = UssdResponseType.SUCCESS_RESULT,
                    title = "Payment Successful!",
                    body = "Confirmed! \$${state.enteredAmount} sent to John Doe (${state.enteredPhone}).\nTransaction ID: MM938104820.\nNew MoMo Balance: \$195.30.",
                    rawText = "Confirmed! \$${state.enteredAmount} sent to John Doe. Txn ID: MM938104820. New Balance: \$195.30.",
                    stepIndex = state.currentStage,
                    isTerminal = true,
                    isSuccess = true
                )
            }
        }
    }

    private fun generateDataBundlesFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "High-Speed 4G/5G Data",
                body = "Select your preferred internet bundle package:",
                options = listOf(
                    UssdMenuOption("1", "Daily Bundles (1GB - 3GB)"),
                    UssdMenuOption("2", "Weekly Bundles (5GB - 15GB)"),
                    UssdMenuOption("3", "Monthly Bundles (30GB - 100GB)"),
                    UssdMenuOption("4", "Unlimited Night Owl"),
                    UssdMenuOption("0", "Cancel", isBack = true)
                ),
                rawText = "1. Daily Bundles\n2. Weekly Bundles\n3. Monthly Bundles\n4. Unlimited Night Owl\n0. Cancel",
                stepIndex = 1
            )
            2 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "Select Daily Package",
                body = "Choose volume package:",
                options = listOf(
                    UssdMenuOption("1", "1.5 GB @ \$1.00 (24h)"),
                    UssdMenuOption("2", "3.0 GB @ \$2.00 (24h)"),
                    UssdMenuOption("3", "5.0 GB Ultra @ \$3.50 (24h)"),
                    UssdMenuOption("0", "Back", isBack = true)
                ),
                rawText = "1. 1.5GB @ \$1.00\n2. 3.0GB @ \$2.00\n3. 5.0GB @ \$3.50\n0. Back",
                stepIndex = 2
            )
            3 -> ParsedUssdResponse(
                type = UssdResponseType.CONFIRMATION,
                title = "Payment Source",
                body = "Subscribe to 3.0 GB Daily Package for \$2.00?\nSelect billing method:",
                options = listOf(
                    UssdMenuOption("1", "Pay with Airtime Balance"),
                    UssdMenuOption("2", "Pay with Mobile Money Wallet"),
                    UssdMenuOption("0", "Cancel", isBack = true)
                ),
                rawText = "Subscribe 3GB for \$2.00?\n1. Airtime\n2. MoMo\n0. Cancel",
                stepIndex = 3
            )
            else -> ParsedUssdResponse(
                type = UssdResponseType.SUCCESS_RESULT,
                title = "Bundle Activated",
                body = "You have successfully subscribed to 3.0 GB Daily 4G Bundle. Valid for 24 hours. Enjoy fast browsing!",
                rawText = "Successful! You have received 3.0 GB Daily Bundle. Valid until tomorrow.",
                stepIndex = state.currentStage,
                isTerminal = true,
                isSuccess = true
            )
        }
    }

    private fun generateBankingFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "Instant Banking Service",
                body = "Welcome to Express Banking. Select option:",
                options = listOf(
                    UssdMenuOption("1", "Quick Balance Inquiry"),
                    UssdMenuOption("2", "Transfer to Other Bank"),
                    UssdMenuOption("3", "Buy Airtime / Recharge"),
                    UssdMenuOption("4", "Mini Statement (Last 5 Txns)")
                ),
                rawText = "1. Quick Balance\n2. Transfer\n3. Buy Airtime\n4. Mini Statement",
                stepIndex = 1
            )
            2 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "Bank Secret PIN",
                body = "Enter your 4-digit Banking PIN to proceed securely:",
                inputType = UssdInputType.PIN,
                inputHint = "4-digit PIN",
                rawText = "Enter 4-digit Banking PIN:",
                stepIndex = 2
            )
            else -> ParsedUssdResponse(
                type = UssdResponseType.SUCCESS_RESULT,
                title = "Account Statement",
                body = "Acct: **8291\nAvailable Bal: \$1,842.30\nBook Bal: \$1,842.30\nLast Txn: -\$45.00 Grocery Store on 27/08.",
                rawText = "Acct: **8291. Available Balance: \$1,842.30. Ledger: \$1,842.30.",
                stepIndex = state.currentStage,
                isTerminal = true,
                isSuccess = true
            )
        }
    }

    private fun generateMpesaFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "M-PESA Menu",
                body = "Select M-PESA Service:",
                options = listOf(
                    UssdMenuOption("1", "Send Money"),
                    UssdMenuOption("2", "Withdraw Cash"),
                    UssdMenuOption("3", "Buy Airtime"),
                    UssdMenuOption("4", "Lipa na M-PESA"),
                    UssdMenuOption("5", "My Account / Balance")
                ),
                rawText = "1. Send Money\n2. Withdraw Cash\n3. Buy Airtime\n4. Lipa na M-PESA\n5. My Account",
                stepIndex = 1
            )
            2 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "M-PESA Phone Number",
                body = "Enter recipient mobile number:",
                inputType = UssdInputType.PHONE_NUMBER,
                inputHint = "07XXXXXXXX",
                rawText = "Enter phone number:",
                stepIndex = 2
            )
            3 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "Amount",
                body = "Enter amount to transfer:",
                inputType = UssdInputType.AMOUNT,
                inputHint = "Amount (KES / USD)",
                rawText = "Enter amount:",
                stepIndex = 3
            )
            4 -> ParsedUssdResponse(
                type = UssdResponseType.INPUT_PROMPT,
                title = "M-PESA PIN",
                body = "Enter M-PESA secret PIN:",
                inputType = UssdInputType.PIN,
                inputHint = "4-digit PIN",
                rawText = "Enter M-PESA PIN:",
                stepIndex = 4
            )
            else -> ParsedUssdResponse(
                type = UssdResponseType.SUCCESS_RESULT,
                title = "M-PESA Confirmed",
                body = "QJK918374 Confirmed. \$30.00 sent to Alice Smith 0712345678 on 28/08 at 09:41 AM. New M-PESA balance is \$184.20. Transaction cost: \$0.22.",
                rawText = "QJK918374 Confirmed. \$30.00 sent to Alice Smith. Balance: \$184.20.",
                stepIndex = state.currentStage,
                isTerminal = true,
                isSuccess = true
            )
        }
    }

    private fun generateGeneralTelecomFlow(state: SimulationTreeState, lastInput: String): ParsedUssdResponse {
        return when (state.currentStage) {
            1 -> ParsedUssdResponse(
                type = UssdResponseType.MENU,
                title = "Telecom Self-Service",
                body = "Main Menu for ${state.code}:",
                options = listOf(
                    UssdMenuOption("1", "Check Account Balance"),
                    UssdMenuOption("2", "Top-Up Airtime"),
                    UssdMenuOption("3", "Data & Voice Bundles"),
                    UssdMenuOption("4", "Caller Tunes & Value-Added"),
                    UssdMenuOption("0", "Help / Customer Care")
                ),
                rawText = "1. Account Balance\n2. Top-Up\n3. Data Bundles\n4. Value-Added\n0. Help",
                stepIndex = 1
            )
            2 -> {
                if (lastInput == "1" || lastInput.isEmpty()) {
                    ParsedUssdResponse(
                        type = UssdResponseType.SUCCESS_RESULT,
                        title = "Account Balance",
                        body = "Your main airtime balance is \$18.45. Bonus voice: 42 mins. SMS: 120 SMS. Data balance: 3.2 GB. Valid for 18 days.",
                        rawText = "Balance: \$18.45. Voice: 42 mins. Data: 3.2 GB.",
                        stepIndex = 2,
                        isTerminal = true,
                        isSuccess = true
                    )
                } else {
                    ParsedUssdResponse(
                        type = UssdResponseType.INPUT_PROMPT,
                        title = "Recharge Voucher PIN",
                        body = "Enter 14-digit recharge PIN on your scratch card:",
                        inputType = UssdInputType.NUMERIC,
                        inputHint = "14-digit scratch card PIN",
                        rawText = "Enter 14-digit recharge PIN:",
                        stepIndex = 2
                    )
                }
            }
            else -> ParsedUssdResponse(
                type = UssdResponseType.SUCCESS_RESULT,
                title = "Recharge Successful",
                body = "Recharge of \$10.00 successful. Your new balance is \$28.45. Thank you for choosing our network!",
                rawText = "Recharge successful! New balance is \$28.45.",
                stepIndex = state.currentStage,
                isTerminal = true,
                isSuccess = true
            )
        }
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
                    stepsSummary = stepsSummaryText
                )
                db.ussdDao().insertHistory(item)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save USSD history to DB", e)
            }
        }
    }
}
