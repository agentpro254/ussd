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
     * Start a real USSD session and wait for live carrier response
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
            isSimulation = false,
            startTime = currentSessionStartTime,
            status = FlowStatus.ACTIVE
        )
        _currentFlow.value = flow

        // Set state to Dialing and wait for carrier USSD response
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
        val action = if (PermissionManager.isCallPhoneGranted(context)) {
            Intent.ACTION_CALL
        } else {
            Intent.ACTION_DIAL
        }
        val callIntent = Intent(action, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.extra.slot", simSlot)
            putExtra("simSlot", simSlot)
            putExtra("slot", simSlot)
            putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", simSlot)
        }
        context.startActivity(callIntent)
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
