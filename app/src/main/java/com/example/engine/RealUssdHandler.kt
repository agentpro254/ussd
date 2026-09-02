package com.example.engine

import android.content.Context
import android.util.Log

object RealUssdHandler {

    private const val TAG = "RealUssdHandler"
    private var isActive = false
    private var currentCode: String? = null

    fun dialCode(
        context: Context,
        code: String,
        simSlot: Int = 0,
        callback: UssdCallback
    ) {
        if (isActive) {
            callback.onError("Another USSD session is currently active. Please wait or cancel it.")
            return
        }

        // Validate code format
        val validation = CodeValidator.validateCode(code)
        if (validation is ValidationResult.Invalid) {
            callback.onError(validation.reason)
            return
        }
        if (validation is ValidationResult.Blocked) {
            callback.onError(validation.reason)
            return
        }

        // Check if SIM / Mobile network is ready
        if (!NetworkChecker.isMobileNetworkAvailable(context)) {
            Log.w(TAG, "Mobile network not available or SIM not ready")
        }

        isActive = true
        currentCode = code

        RealUssdService.dialCode(
            context = context,
            code = code,
            simSlot = simSlot,
            callback = object : UssdCallback {
                override fun onResponse(response: String) {
                    isActive = false
                    callback.onResponse(response)
                }

                override fun onError(error: String) {
                    isActive = false
                    callback.onError(error)
                }
            }
        )
    }

    fun cancelCurrentSession() {
        isActive = false
        currentCode = null
    }

    fun isSessionActive(): Boolean = isActive
}
