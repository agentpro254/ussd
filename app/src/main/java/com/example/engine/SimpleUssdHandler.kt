package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.receiver.UssdResponseReceiver
import com.example.service.CodeeAccessibilityService

class SimpleUssdHandler {

    companion object {
        private const val TAG = "SimpleUssdHandler"
    }

    private var isSessionActive = false
    private var responseCallback: ((response: String, isFinal: Boolean) -> Unit)? = null
    private var currentCode: String = ""
    private var activeSubId: Int = -1

    /**
     * Dials a real USSD code using the Android TelephonyManager sendUssdRequest API.
     * ZERO simulation or fake responses.
     */
    fun dialCode(
        context: Context,
        code: String,
        subscriptionId: Int = -1,
        slotIndex: Int = 0,
        onResponse: (response: String, isFinal: Boolean) -> Unit
    ) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            onResponse("❌ Error: Empty USSD code", true)
            return
        }

        if (isSessionActive) {
            cancelSession()
        }

        currentCode = clean
        activeSubId = subscriptionId
        responseCallback = onResponse
        isSessionActive = true

        // Register with native broadcast receiver for any asynchronous USSD broadcasts
        UssdResponseReceiver.onResponse = { response, isFinal ->
            Log.d(TAG, "📡 Broadcast receiver got USSD: $response (final=$isFinal)")
            handleResponse(response, isFinal || isTerminalResponse(response))
        }

        // Register with Accessibility Service for USSD dialog captures
        CodeeAccessibilityService.setUssdCallback { response ->
            Log.d(TAG, "♿ Accessibility Service captured USSD: $response")
            val isFinal = isTerminalResponse(response)
            handleResponse(response, isFinal)
        }

        // Check CALL_PHONE runtime permission
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPermission) {
            Log.w(TAG, "CALL_PHONE permission missing")
            handleResponse("❌ Permission Required: CALL_PHONE permission is needed to dial USSD codes.", isFinal = true)
            return
        }

        val baseTelephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (baseTelephony == null) {
            handleResponse("❌ Error: Telephony service unavailable on this device.", isFinal = true)
            return
        }

        // Create target TelephonyManager for the specific SIM card subscription if available
        val targetTelephony = if (subscriptionId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                baseTelephony.createForSubscriptionId(subscriptionId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not create TelephonyManager for subId $subscriptionId: ${e.message}")
                baseTelephony
            }
        } else {
            baseTelephony
        }

        // Execute Real USSD request using TelephonyManager.sendUssdRequest (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Log.d(TAG, "🚀 Sending REAL USSD request for $clean via subId=$subscriptionId (SIM ${slotIndex + 1})")
                targetTelephony.sendUssdRequest(
                    clean,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephony: TelephonyManager,
                            request: String,
                            response: CharSequence
                        ) {
                            val text = response.toString().trim()
                            val isFinal = isTerminalResponse(text)
                            Log.i(TAG, "✅ Real Carrier USSD Response: $text (isFinal=$isFinal)")
                            handleResponse(text, isFinal)
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephony: TelephonyManager,
                            request: String,
                            failureCode: Int
                        ) {
                            val errorMsg = formatUssdFailure(failureCode)
                            Log.e(TAG, "❌ Real USSD Failure: $errorMsg (code=$failureCode)")
                            handleResponse(errorMsg, isFinal = true)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException sending USSD request", e)
                handleResponse("❌ Permission error: ${e.message ?: "CALL_PHONE required"}", isFinal = true)
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending USSD request", e)
                handleResponse("❌ USSD Request Error: ${e.message ?: "Carrier unreachable"}", isFinal = true)
            }
        } else {
            handleResponse("❌ Error: Android 8.0+ (API 26) is required for in-app USSD execution.", isFinal = true)
        }
    }

    /**
     * Sends user interaction input to the active real USSD session.
     */
    fun sendInput(
        context: Context,
        input: String,
        onResponse: (response: String, isFinal: Boolean) -> Unit
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        responseCallback = onResponse

        // Try Accessibility Service response first
        if (CodeeAccessibilityService.sendUssdResponse(trimmed)) {
            Log.d(TAG, "✅ Response sent via Accessibility Service: '$trimmed'")
            return
        }

        val baseTelephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (baseTelephony == null) {
            handleResponse("❌ Error: Telephony service unavailable.", isFinal = true)
            return
        }

        val targetTelephony = if (activeSubId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                baseTelephony.createForSubscriptionId(activeSubId)
            } catch (e: Exception) {
                baseTelephony
            }
        } else {
            baseTelephony
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Log.d(TAG, "📨 Sending real USSD input response: '$trimmed'")
                targetTelephony.sendUssdRequest(
                    trimmed,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephony: TelephonyManager,
                            request: String,
                            response: CharSequence
                        ) {
                            val text = response.toString().trim()
                            val isFinal = isTerminalResponse(text)
                            Log.i(TAG, "✅ Next Real USSD Response: $text (isFinal=$isFinal)")
                            handleResponse(text, isFinal)
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephony: TelephonyManager,
                            request: String,
                            failureCode: Int
                        ) {
                            val errorMsg = formatUssdFailure(failureCode)
                            Log.e(TAG, "❌ USSD Input Failure: $errorMsg (code=$failureCode)")
                            handleResponse(errorMsg, isFinal = true)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during USSD input transmission", e)
                handleResponse("❌ Failed to send input: ${e.message ?: "Carrier error"}", isFinal = true)
            }
        } else {
            handleResponse("❌ Error: Unsupported OS version for in-app USSD replies.", isFinal = true)
        }
    }

    private fun handleResponse(response: String, isFinal: Boolean) {
        val cleanText = response.trim()
        responseCallback?.invoke(cleanText, isFinal)

        if (isFinal) {
            isSessionActive = false
            UssdResponseReceiver.onResponse = null
            CodeeAccessibilityService.clearCallbacks()
        }
    }

    fun cancelSession() {
        isSessionActive = false
        currentCode = ""
        activeSubId = -1
        responseCallback = null
        UssdResponseReceiver.onResponse = null
        CodeeAccessibilityService.clearCallbacks()
    }

    private fun formatUssdFailure(failureCode: Int): String {
        return when (failureCode) {
            TelephonyManager.USSD_RETURN_FAILURE -> "❌ Carrier returned a failure (Request rejected or invalid option)."
            -1 -> "❌ USSD request timed out or carrier network error."
            else -> "❌ Carrier USSD request failed with error code: $failureCode."
        }
    }

    /**
     * Determines whether the received response is terminal (final).
     */
    fun isTerminalResponse(response: String): Boolean {
        val clean = response.trim()
        if (clean.isEmpty()) return true
        if (clean.startsWith("END", ignoreCase = true)) return true
        if (clean.startsWith("❌")) return true
        if (clean.contains("Confirmed", ignoreCase = true)) return true
        if (clean.contains("Your balance is", ignoreCase = true)) return true
        if (clean.contains("Thank you", ignoreCase = true)) return true
        if (clean.contains("Transaction cost", ignoreCase = true)) return true
        if (clean.contains("Insufficient", ignoreCase = true)) return true
        if (clean.contains("Invalid PIN", ignoreCase = true)) return true
        if (clean.contains("Failed", ignoreCase = true)) return true

        // If response explicitly specifies continuation (CON), it is not terminal
        if (clean.startsWith("CON", ignoreCase = true)) return false

        // If it looks like a menu with numbered choices, it is waiting for input
        val lines = clean.lines()
        val hasNumberedChoices = lines.any { line ->
            val t = line.trim()
            t.startsWith("1.") || t.startsWith("1:") || t.startsWith("1 ") ||
            t.startsWith("2.") || t.startsWith("2:") || t.startsWith("2 ")
        }
        if (hasNumberedChoices) return false

        // If text contains prompts like "enter", "reply with", "select", "type"
        val lower = clean.lowercase()
        if (lower.contains("enter ") || lower.contains("reply with") || lower.contains("select ") || lower.contains("type ")) {
            return false
        }

        return true
    }
}
