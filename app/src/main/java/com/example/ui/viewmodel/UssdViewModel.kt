package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ParsedUssdResponse
import com.example.data.model.SimCardInfo
import com.example.data.model.UssdSessionFlow
import com.example.data.model.UssdSessionState
import com.example.data.parser.UssdParser
import com.example.engine.UssdSessionManager
import com.example.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TelephonyIntentEvent {
    data class Success(val ussdCode: String, val simSlot: Int, val isDirectCall: Boolean) : TelephonyIntentEvent
    data class Error(val ussdCode: String, val message: String) : TelephonyIntentEvent
}

class UssdViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "UssdViewModel"
    }

    // Input state for USSD dialer
    private val _inputCode = MutableStateFlow("*144#")
    val inputCode: StateFlow<String> = _inputCode.asStateFlow()

    private val _selectedSimSlot = MutableStateFlow(0)
    val selectedSimSlot: StateFlow<Int> = _selectedSimSlot.asStateFlow()

    private val _activeStepInput = MutableStateFlow("")
    val activeStepInput: StateFlow<String> = _activeStepInput.asStateFlow()

    private val _simCards = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val simCards: StateFlow<List<SimCardInfo>> = _simCards.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _telephonyEvents = MutableSharedFlow<TelephonyIntentEvent>()
    val telephonyEvents: SharedFlow<TelephonyIntentEvent> = _telephonyEvents.asSharedFlow()

    // Expose core USSD Session State from UssdSessionManager
    val sessionState: StateFlow<UssdSessionState> = UssdSessionManager.sessionState
    val currentFlow: StateFlow<UssdSessionFlow?> = UssdSessionManager.currentFlow

    init {
        refreshSimCards()
    }

    fun refreshSimCards() {
        val context = getApplication<Application>()
        _simCards.value = PermissionManager.getAvailableSimCards(context)
    }

    fun setInputCode(code: String) {
        _inputCode.value = code
    }

    fun onDigit(digit: String) {
        _inputCode.value += digit
    }

    fun onBackspace() {
        if (_inputCode.value.isNotEmpty()) {
            _inputCode.value = _inputCode.value.dropLast(1)
        }
    }

    fun onClearInput() {
        _inputCode.value = ""
    }

    fun setSimSlot(slot: Int) {
        _selectedSimSlot.value = slot
    }

    fun onStepInputChanged(input: String) {
        _activeStepInput.value = input
    }

    fun submitCurrentStep(input: String = _activeStepInput.value) {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            UssdSessionManager.submitStepResponse(trimmed)
            _activeStepInput.value = ""
        }
    }

    fun dismissSession() {
        val context = getApplication<Application>()
        UssdSessionManager.dismissSession(context)
        _activeStepInput.value = ""
    }

    /**
     * Trigger telephony intent to dial or execute the specified USSD code.
     * Handles dual-SIM routing, CALL_PHONE permission checking, URI encoding,
     * and fallback to ACTION_DIAL when needed.
     */
    fun triggerTelephonyIntent(
        code: String = _inputCode.value,
        simSlot: Int = _selectedSimSlot.value
    ) {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) {
            _statusMessage.value = "Please enter a USSD code"
            viewModelScope.launch {
                _telephonyEvents.emit(TelephonyIntentEvent.Error(cleanCode, "Empty USSD code"))
            }
            return
        }

        val context = getApplication<Application>()
        val hasCallPermission = PermissionManager.isCallPhoneGranted(context)

        try {
            // USSD strings contain '#' which MUST be percent-encoded (%23) in tel: URIs
            val encodedUri = Uri.parse("tel:" + Uri.encode(cleanCode))
            val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, encodedUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Dual-SIM account selection extras
            val selectedSim = _simCards.value.firstOrNull { it.slotIndex == simSlot }
            val subId = selectedSim?.subscriptionId

            intent.putExtra("com.android.phone.extra.slot", simSlot)
            intent.putExtra("simSlot", simSlot)
            intent.putExtra("slot", simSlot)
            intent.putExtra("phone_subscription_id", simSlot)
            if (subId != null && subId > 0) {
                intent.putExtra("subscription", subId)
                intent.putExtra("subscription_id", subId)
            }

            // Attach PhoneAccountHandle if available on TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    if (telecomManager != null && hasCallPermission) {
                        val phoneAccounts = telecomManager.callCapablePhoneAccounts
                        val matchingAccount = phoneAccounts.getOrNull(simSlot)
                        if (matchingAccount != null) {
                            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchingAccount)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not attach PhoneAccountHandle extra", e)
                }
            }

            context.startActivity(intent)

            // Start internal session tracking in UssdSessionManager
            UssdSessionManager.startUssdSession(
                context = context,
                rawCode = cleanCode,
                simSlot = simSlot,
                automatedSteps = emptyList(),
                userInitiated = true
            )

            _statusMessage.value = "Telephony intent triggered for $cleanCode (SIM ${simSlot + 1})"
            viewModelScope.launch {
                _telephonyEvents.emit(
                    TelephonyIntentEvent.Success(
                        ussdCode = cleanCode,
                        simSlot = simSlot,
                        isDirectCall = hasCallPermission
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger telephony intent for $cleanCode", e)
            _statusMessage.value = "Failed to launch telephony: ${e.message}"
            viewModelScope.launch {
                _telephonyEvents.emit(
                    TelephonyIntentEvent.Error(
                        ussdCode = cleanCode,
                        message = e.message ?: "Unknown telephony error"
                    )
                )
            }
        }
    }

    /**
     * Trigger USSD execution via Intent.ACTION_CALL
     */
    fun triggerNativeUssdRequest(
        code: String = _inputCode.value,
        simSlot: Int = _selectedSimSlot.value
    ) {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return
        triggerTelephonyIntent(cleanCode, simSlot)
    }

    /**
     * Quick action to dial a pre-configured code
     */
    fun quickDial(code: String, simSlot: Int = _selectedSimSlot.value) {
        _inputCode.value = code
        triggerTelephonyIntent(code, simSlot)
    }
}
