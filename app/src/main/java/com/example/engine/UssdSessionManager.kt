package com.example.engine

import android.content.Context
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
    private const val MAX_ATTEMPTS = 3
    private const val TIMEOUT_DURATION_MS = 30000L

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val timeoutManager = TimeoutManager()

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

    // Anti-Loop & Concurrency Locks
    private var isSessionRunning = false
    private var dialAttempts = 0

    private var database: AppDatabase? = null

    fun initialize(db: AppDatabase) {
        this.database = db
    }

    fun setAccessibilityServiceInstance(service: CodeeAccessibilityService?) {
        activeAccessibilityService = if (service != null) WeakReference(service) else null
    }

    fun isSessionActive(): Boolean {
        return isSessionRunning || _sessionState.value !is UssdSessionState.Idle
    }

    fun resetAttemptCounters() {
        dialAttempts = 0
    }

    /**
     * Start an internal USSD session using real carrier network services (no external dialer).
     */
    fun startUssdSession(
        context: Context,
        rawCode: String,
        simSlot: Int = 0,
        automatedSteps: List<String> = emptyList(),
        userInitiated: Boolean = true
    ) {
        val cleanCode = rawCode.trim()

        // 1. Guard against non-user-initiated auto-dialing loops
        if (!userInitiated) {
            Log.w(TAG, "❌ Blocked auto-dial attempt for $cleanCode. All USSD operations require explicit user interaction.")
            return
        }

        // 2. Guard against concurrent session re-entry
        if (isSessionRunning && _sessionState.value !is UssdSessionState.Idle) {
            Log.w(TAG, "⚠️ Session already active for $currentSessionCode. Blocked concurrent dial request for $cleanCode.")
            return
        }

        // 3. Rate limiting and loop prevention
        if (dialAttempts >= MAX_ATTEMPTS) {
            Log.e(TAG, "🛑 Maximum dial attempts ($MAX_ATTEMPTS) reached for $cleanCode. Terminating to prevent infinite loop.")
            _sessionState.value = UssdSessionState.Completed(
                code = cleanCode,
                summary = "Dialing stopped: Maximum dial attempts ($MAX_ATTEMPTS) reached. Please check signal or try manually.",
                response = ParsedUssdResponse(
                    type = com.example.data.model.UssdResponseType.ERROR_RESULT,
                    title = "Connection Limit Reached",
                    body = "Dialing was stopped to protect your device from repetitive carrier queries.",
                    isTerminal = true,
                    isSuccess = false
                ),
                isSuccess = false
            )
            isSessionRunning = false
            return
        }

        dialAttempts++
        isSessionRunning = true
        currentSessionCode = cleanCode
        currentSessionStartTime = System.currentTimeMillis()
        currentStepLogs.clear()
        currentFlowSteps.clear()
        currentSimSlot = simSlot

        val sessionId = UUID.randomUUID().toString()
        EmergencyStopManager.registerSession(sessionId)

        val flow = UssdSessionFlow(
            sessionId = sessionId,
            ussdCode = cleanCode,
            simSlot = simSlot,
            startTime = currentSessionStartTime,
            status = FlowStatus.ACTIVE
        )
        _currentFlow.value = flow

        // Set state to Dialing with live flow
        _sessionState.value = UssdSessionState.Dialing(cleanCode, simSlot, activeFlow = flow)

        // Start overlay service so Codee floating UI can appear
        CodeeOverlayService.start(context)

        // Start timeout timer (30 seconds) that is cancelled immediately upon response
        timeoutManager.startTimeout(TIMEOUT_DURATION_MS) {
            if (isSessionRunning) {
                Log.w(TAG, "USSD request for $cleanCode timed out after ${TIMEOUT_DURATION_MS / 1000}s.")
                onSessionEnd()
                val duration = System.currentTimeMillis() - currentSessionStartTime
                val finalFlow = flow.copy(
                    status = FlowStatus.FAILED,
                    endTime = System.currentTimeMillis(),
                    finalSummary = "USSD request timed out. Please check mobile signal and try again."
                )
                _currentFlow.value = finalFlow
                val errorParsed = ParsedUssdResponse(
                    type = com.example.data.model.UssdResponseType.ERROR_RESULT,
                    title = "Carrier Request Timeout",
                    body = "The mobile carrier did not respond within ${TIMEOUT_DURATION_MS / 1000} seconds. Please check your signal or try again.",
                    isTerminal = true,
                    isSuccess = false
                )
                _sessionState.value = UssdSessionState.Completed(
                    code = cleanCode,
                    summary = "USSD request timed out after 30 seconds",
                    response = errorParsed,
                    flow = finalFlow,
                    historySteps = currentStepLogs.toList(),
                    isSuccess = false,
                    durationMs = duration
                )
            }
        }

        // Execute internal USSD Request to the carrier network
        RealUssdHandler.dialCode(
            context = context,
            code = cleanCode,
            simSlot = simSlot,
            callback = object : UssdCallback {
                override fun onResponse(response: String) {
                    // Response received: cancel timeout immediately
                    timeoutManager.markResponseReceived()
                    Log.i(TAG, "Real carrier response received: $response")
                    handleCarrierResponse(response, isTerminalOverride = false)
                }

                override fun onError(error: String) {
                    timeoutManager.cancelTimeout()
                    Log.w(TAG, "Carrier error / dial failure: $error")
                    onSessionEnd()
                    val duration = System.currentTimeMillis() - currentSessionStartTime
                    val finalFlow = flow.copy(
                        status = FlowStatus.FAILED,
                        endTime = System.currentTimeMillis(),
                        finalSummary = error
                    )
                    _currentFlow.value = finalFlow
                    val errorParsed = ParsedUssdResponse(
                        type = com.example.data.model.UssdResponseType.ERROR_RESULT,
                        title = "Carrier Service Notice",
                        body = error,
                        isTerminal = true,
                        isSuccess = false
                    )
                    _sessionState.value = UssdSessionState.Completed(
                        code = cleanCode,
                        summary = error,
                        response = errorParsed,
                        flow = finalFlow,
                        historySteps = currentStepLogs.toList(),
                        isSuccess = false,
                        durationMs = duration
                    )
                    saveHistoryToDatabase(errorParsed, isSuccess = false, isSimulation = false, flow = finalFlow)
                }
            }
        )
    }

    /**
     * Process real response text from carrier (either from TelephonyManager or Accessibility Service).
     */
    private fun handleCarrierResponse(text: String, isTerminalOverride: Boolean) {
        // Immediate cancellation of timeout on any incoming response
        timeoutManager.markResponseReceived()

        val currentStep = currentFlowSteps.size + 1
        val parsed = UssdParser.parse(text, currentStep)
        val isError = isCarrierErrorResponse(text) || !parsed.isSuccess
        val isTerminal = parsed.isTerminal || isTerminalOverride || isError

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
        currentStepLogs.add(
            StepLogItem(
                stepNumber = currentStep,
                promptText = parsed.body.ifBlank { parsed.title },
                userInput = null
            )
        )

        val updatedFlow = (_currentFlow.value ?: UssdSessionFlow(
            ussdCode = currentSessionCode,
            simSlot = currentSimSlot
        )).copy(steps = currentFlowSteps.toList())
        _currentFlow.value = updatedFlow

        if (isTerminal) {
            onSessionEnd()
            val duration = System.currentTimeMillis() - currentSessionStartTime
            val isSuccess = parsed.isSuccess && !isError
            val finalFlow = updatedFlow.copy(
                status = if (isSuccess) FlowStatus.COMPLETED else FlowStatus.FAILED,
                endTime = System.currentTimeMillis(),
                finalSummary = parsed.body.ifBlank { parsed.title }
            )
            _currentFlow.value = finalFlow
            _sessionState.value = UssdSessionState.Completed(
                code = currentSessionCode,
                summary = parsed.body.ifBlank { parsed.title },
                response = parsed.copy(isSuccess = isSuccess, isTerminal = true),
                flow = finalFlow,
                historySteps = currentStepLogs.toList(),
                isSuccess = isSuccess,
                durationMs = duration
            )
            saveHistoryToDatabase(parsed, isSuccess = isSuccess, isSimulation = false, flow = finalFlow)
        } else {
            // Interactive live session awaiting user choice
            _sessionState.value = UssdSessionState.ActiveSession(
                code = currentSessionCode,
                step = currentStep,
                response = parsed,
                flow = updatedFlow,
                historySteps = currentStepLogs.toList(),
                simSlot = currentSimSlot,
                isAutomating = false,
                pendingInputs = emptyList()
            )
        }
    }

    /**
     * Called by CodeeAccessibilityService when a live USSD dialog is captured from the carrier.
     */
    fun onUssdDialogCaptured(
        text: String,
        inputNode: AccessibilityNodeInfo?,
        sendButton: AccessibilityNodeInfo?,
        cancelButton: AccessibilityNodeInfo?
    ) {
        timeoutManager.markResponseReceived()
        lastInputNode = if (inputNode != null) WeakReference(inputNode) else null
        lastSendButton = if (sendButton != null) WeakReference(sendButton) else null
        lastCancelButton = if (cancelButton != null) WeakReference(cancelButton) else null

        handleCarrierResponse(text, isTerminalOverride = false)
    }

    /**
     * Submit an option or text response to the active carrier session when the user clicks or types.
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
            flow = _currentFlow.value
        )

        // Reset timeout for next step response
        timeoutManager.startTimeout(TIMEOUT_DURATION_MS) {
            if (isSessionRunning) {
                Log.w(TAG, "Step submit timed out waiting for carrier.")
                timeoutManager.cancelTimeout()
            }
        }

        val service = activeAccessibilityService?.get()
        val inputNode = lastInputNode?.get()
        val sendBtn = lastSendButton?.get()

        if (service != null && inputNode != null) {
            val success = service.submitTextToActiveDialog(inputNode, userInput, sendBtn)
            if (!success) {
                Log.w(TAG, "Accessibility node submit failed on live dialog")
            }
        } else {
            Log.i(TAG, "Submitted step response: $userInput (waiting for live carrier response)")
        }
    }

    fun onSessionEnd() {
        isSessionRunning = false
        timeoutManager.cancelTimeout()
    }

    fun isCarrierErrorResponse(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("connection problem") ||
                lower.contains("invalid mmi") ||
                lower.contains("mmi code") ||
                lower.contains("service unavailable") ||
                lower.contains("session timed out") ||
                lower.contains("session expired") ||
                lower.contains("try again later") ||
                lower.contains("system busy")
    }

    fun emergencyStop(context: Context? = null) {
        Log.i(TAG, "Emergency Stop triggered - halting all sessions and resetting locks.")
        dialAttempts = 0
        isSessionRunning = false
        timeoutManager.cancelTimeout()
        RealUssdHandler.cancelCurrentSession()
        val service = activeAccessibilityService?.get()
        val cancelBtn = lastCancelButton?.get()
        service?.dismissActiveDialog(cancelBtn)
        if (context != null) {
            CodeeOverlayService.stop(context)
        }
        _currentFlow.value = _currentFlow.value?.copy(
            status = FlowStatus.CANCELLED,
            endTime = System.currentTimeMillis()
        )
        _sessionState.value = UssdSessionState.Idle
    }

    fun dismissSession(context: Context? = null) {
        onSessionEnd()
        timeoutManager.cancelTimeout()
        RealUssdHandler.cancelCurrentSession()
        val service = activeAccessibilityService?.get()
        val cancelBtn = lastCancelButton?.get()
        service?.dismissActiveDialog(cancelBtn)
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
