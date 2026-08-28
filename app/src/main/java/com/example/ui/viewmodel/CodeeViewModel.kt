package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CodeeApplication
import com.example.data.local.SavedUssdRoutine
import com.example.data.local.UssdHistoryItem
import com.example.data.model.SimCardInfo
import com.example.data.model.SmartFlowResult
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.UssdSessionState
import com.example.data.repository.SmsReaderRepository
import com.example.engine.SmartUssdFlowEngine
import com.example.engine.UssdSessionManager
import com.example.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isCallPhoneGranted: Boolean = false,
    val isReadPhoneStateGranted: Boolean = false,
    val isNotificationsGranted: Boolean = false,
    val isSmsGranted: Boolean = false
) {
    val isAllMandatoryGranted: Boolean
        get() = isAccessibilityGranted && isCallPhoneGranted

    val isAllRecommendedGranted: Boolean
        get() = isAccessibilityGranted && isOverlayGranted && isCallPhoneGranted && isReadPhoneStateGranted && isSmsGranted
}

class CodeeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CodeeApplication
    private val dao = app.database.ussdDao()

    val sessionState: StateFlow<UssdSessionState> = UssdSessionManager.sessionState
    val activeFlow: StateFlow<com.example.data.model.UssdSessionFlow?> = UssdSessionManager.currentFlow

    val savedRoutines: StateFlow<List<SavedUssdRoutine>> = dao.getAllRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyItems: StateFlow<List<UssdHistoryItem>> = dao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _availableSims = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val availableSims: StateFlow<List<SimCardInfo>> = _availableSims.asStateFlow()

    private val _selectedSimSlot = MutableStateFlow(0)
    val selectedSimSlot: StateFlow<Int> = _selectedSimSlot.asStateFlow()

    private val _dialpadText = MutableStateFlow("")
    val dialpadText: StateFlow<String> = _dialpadText.asStateFlow()

    private val _isDemoMode = MutableStateFlow(true) // Default to demo friendly so emulator works out of the box
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    // Smart USSD Engine
    val smartFlowEngine = SmartUssdFlowEngine()
    private val _smartFlowResult = MutableStateFlow<SmartFlowResult?>(null)
    val smartFlowResult: StateFlow<SmartFlowResult?> = _smartFlowResult.asStateFlow()

    // SMS Transactions
    private val _smsTransactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    val smsTransactions: StateFlow<List<TransactionItem>> = _smsTransactions.asStateFlow()

    // Unified list of Successful Transactions (USSD Completed + SMS parsed)
    val successfulTransactions: StateFlow<List<TransactionItem>> = combine(
        historyItems,
        _smsTransactions
    ) { ussdLogs, smsLogs ->
        val resultList = mutableListOf<TransactionItem>()

        // Add SMS verified transactions first
        resultList.addAll(smsLogs)

        // Convert successful USSD session logs into Transaction Items
        ussdLogs.filter { it.isSuccess }.forEach { ussd ->
            val alreadyInSms = ussd.transactionId?.let { code ->
                smsLogs.any { it.mpesaCode.equals(code, ignoreCase = true) }
            } ?: false

            if (!alreadyInSms) {
                val parsedType = when {
                    ussd.summary.contains("sent", ignoreCase = true) -> TransactionType.SENT
                    ussd.summary.contains("received", ignoreCase = true) -> TransactionType.RECEIVED
                    ussd.summary.contains("airtime", ignoreCase = true) -> TransactionType.AIRTIME
                    ussd.summary.contains("paid", ignoreCase = true) || ussd.summary.contains("bill", ignoreCase = true) -> TransactionType.BILL_PAYMENT
                    ussd.summary.contains("withdraw", ignoreCase = true) -> TransactionType.WITHDRAWAL
                    ussd.summary.contains("balance", ignoreCase = true) -> TransactionType.BALANCE
                    else -> TransactionType.OTHER
                }

                resultList.add(
                    TransactionItem(
                        id = "ussd_${ussd.id}",
                        mpesaCode = ussd.transactionId ?: "TRX-${ussd.id}",
                        type = parsedType,
                        amount = ussd.amount ?: "Confirmed",
                        recipientOrSender = ussd.recipient ?: "USSD Session",
                        phoneNumber = "",
                        timestamp = ussd.timestamp,
                        summary = ussd.summary,
                        fullBody = ussd.rawResponseText,
                        isVerifiedBySms = false,
                        isVerifiedByUssd = true,
                        source = "USSD Confirmed"
                    )
                )
            }
        }

        // Sort latest first
        resultList.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Preloaded Code Directory Favorites Tracking
    private val _favoriteCodeIds = MutableStateFlow<Set<String>>(
        com.example.data.model.PreloadedUssdRepository.allCodes
            .filter { it.isFavorite }
            .map { it.id }
            .toSet()
    )
    val favoriteCodeIds: StateFlow<Set<String>> = _favoriteCodeIds.asStateFlow()

    fun toggleFavoriteCode(codeId: String) {
        val current = _favoriteCodeIds.value.toMutableSet()
        if (current.contains(codeId)) {
            current.remove(codeId)
        } else {
            current.add(codeId)
        }
        _favoriteCodeIds.value = current
    }

    init {
        refreshPermissions()
        refreshSmsTransactions()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _permissionStatus.value = PermissionStatus(
            isAccessibilityGranted = PermissionManager.isAccessibilityEnabled(context),
            isOverlayGranted = PermissionManager.isOverlayPermissionGranted(context),
            isCallPhoneGranted = PermissionManager.isCallPhoneGranted(context),
            isReadPhoneStateGranted = PermissionManager.isReadPhoneStateGranted(context),
            isNotificationsGranted = PermissionManager.isPostNotificationsGranted(context),
            isSmsGranted = PermissionManager.isReadSmsGranted(context)
        )
        _availableSims.value = PermissionManager.getAvailableSimCards(context)
    }

    fun refreshSmsTransactions() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val loaded = SmsReaderRepository.readLocalSmsTransactions(context)
            _smsTransactions.value = loaded
        }
    }

    fun setSimSlot(slot: Int) {
        _selectedSimSlot.value = slot
    }

    fun setDialpadText(text: String) {
        _dialpadText.value = text
    }

    fun appendDialpadChar(char: Char) {
        _dialpadText.value += char
    }

    fun deleteDialpadChar() {
        if (_dialpadText.value.isNotEmpty()) {
            _dialpadText.value = _dialpadText.value.dropLast(1)
        }
    }

    fun clearDialpad() {
        _dialpadText.value = ""
    }

    fun toggleDemoMode() {
        _isDemoMode.value = !_isDemoMode.value
    }

    fun launchUssd(
        code: String = _dialpadText.value,
        simSlot: Int = _selectedSimSlot.value,
        automatedSteps: List<String> = emptyList(),
        forceSim: Boolean = _isDemoMode.value
    ) {
        if (code.isBlank()) return
        val context = getApplication<Application>()
        UssdSessionManager.startUssdSession(
            context = context,
            rawCode = code,
            simSlot = simSlot,
            automatedSteps = automatedSteps,
            forceSimulation = forceSim || !PermissionManager.isCallPhoneGranted(context)
        )
    }

    fun launchSmartFlow(
        code: String,
        goal: String,
        initialData: Map<String, String>,
        simSlot: Int = _selectedSimSlot.value
    ) {
        val context = getApplication<Application>()
        smartFlowEngine.onComplete = { result ->
            _smartFlowResult.value = result
            refreshSmsTransactions()
        }
        smartFlowEngine.startFlow(
            context = context,
            ussdCode = code,
            goal = goal,
            initialData = initialData,
            simSlot = simSlot,
            forceSimulation = _isDemoMode.value || !PermissionManager.isCallPhoneGranted(context)
        )
    }

    fun clearSmartFlowResult() {
        _smartFlowResult.value = null
    }

    fun submitStepInput(input: String) {
        UssdSessionManager.submitStepResponse(input)
    }

    fun dismissSession() {
        val context = getApplication<Application>()
        UssdSessionManager.dismissSession(context)
    }

    fun saveRoutine(
        title: String,
        code: String,
        category: String,
        stepsCsv: String = "",
        isFavorite: Boolean = false,
        iconName: String = "bolt",
        colorHex: String = "#0D9488",
        description: String = ""
    ) {
        viewModelScope.launch {
            val routine = SavedUssdRoutine(
                title = title,
                ussdCode = code,
                category = category,
                stepsCsv = stepsCsv,
                simSlot = _selectedSimSlot.value,
                isFavorite = isFavorite,
                iconName = iconName,
                colorHex = colorHex,
                description = description,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            dao.insertRoutine(routine)
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            dao.deleteRoutineById(id)
        }
    }

    fun toggleFavorite(routine: SavedUssdRoutine) {
        viewModelScope.launch {
            dao.updateRoutine(routine.copy(isFavorite = !routine.isFavorite))
        }
    }

    fun updateRoutineLastUsed(routine: SavedUssdRoutine) {
        viewModelScope.launch {
            dao.updateRoutine(routine.copy(lastUsedTimestamp = System.currentTimeMillis()))
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            dao.deleteHistoryById(id)
        }
    }
}
