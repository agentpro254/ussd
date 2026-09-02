package com.example.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
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
     * Dials a real USSD code exclusively using Intent.ACTION_CALL to launch
     * the system dialer/carrier interface. CodeeAccessibilityService captures the response.
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

        // Register with Accessibility Service for USSD dialog captures from system dialer
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

        // 1. Attempt direct ITelephony reflection first
        var reflectionHandled = false
        try {
            reflectionHandled = ITelephonyReflection.sendUssdViaReflection(
                context = context,
                ussdCode = clean,
                subId = subscriptionId,
                callback = object : ITelephonyReflection.ReflectionCallback {
                    override fun onSuccess(response: String) {
                        Log.d(TAG, "⚡ Direct ITelephony response received: $response")
                        Log.d("ACCESS_DEBUG", "Found dialog: $response")
                        val isFinal = isTerminalResponse(response)
                        handleResponse(response, isFinal)
                    }

                    override fun onError(error: String) {
                        Log.w(TAG, "⚡ ITelephony reflection error: $error")
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "ITelephony reflection invocation failed: ${e.message}")
        }

        // 2. Always launch hidden dialing via TransparentActivity + CodeeAccessibilityService
        // to guarantee carrier network execution and prevent getting stuck if reflection is ignored by OEM
        try {
            Log.d(TAG, "🚀 Initiating dialing via TransparentActivity: $clean (slotIndex=$slotIndex, subId=$subscriptionId, reflection=$reflectionHandled)")
            
            val intent = Intent(context, com.example.ui.TransparentActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(com.example.ui.TransparentActivity.EXTRA_USSD_CODE, clean)
                putExtra(com.example.ui.TransparentActivity.EXTRA_SUBSCRIPTION_ID, subscriptionId)
                putExtra(com.example.ui.TransparentActivity.EXTRA_SLOT_INDEX, slotIndex)
            }

            context.startActivity(intent)

            // Notify UI that call is launched and waiting for carrier response
            onResponse("Waiting for carrier response...", false)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException launching TransparentActivity", e)
            handleResponse("❌ Permission error: ${e.message ?: "CALL_PHONE required"}", isFinal = true)
        } catch (e: Exception) {
            Log.e(TAG, "Exception launching TransparentActivity", e)
            handleResponse("❌ Failed to initiate call: ${e.message ?: "Unknown error"}", isFinal = true)
        }
    }

    /**
     * Sends user interaction input to the active USSD session.
     * Uses ITelephonyReflection as primary method, and CodeeAccessibilityService.sendUssdResponse
     * to inject text into the hidden system dialog and click 'SEND'.
     */
    fun sendInput(
        context: Context,
        input: String,
        onResponse: (response: String, isFinal: Boolean) -> Unit
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        responseCallback = onResponse

        // 1. Primary Method: ITelephony direct reflection
        val reflectionInitiated = ITelephonyReflection.sendUssdViaReflection(
            context = context,
            ussdCode = trimmed,
            subId = activeSubId,
            callback = object : ITelephonyReflection.ReflectionCallback {
                override fun onSuccess(response: String) {
                    Log.d(TAG, "⚡ Direct ITelephony input response received: $response")
                    val isFinal = isTerminalResponse(response)
                    handleResponse(response, isFinal)
                }

                override fun onError(error: String) {
                    Log.w(TAG, "⚡ ITelephony reflection input error: $error")
                }
            }
        )

        // 2. Secondary / Parallel Method: Automatically inject text and click 'SEND' via Accessibility
        val accessibilitySent = CodeeAccessibilityService.sendUssdResponse(trimmed)

        if (reflectionInitiated || accessibilitySent) {
            Log.d(TAG, "✅ Response sent for '$trimmed' (reflection=$reflectionInitiated, accessibility=$accessibilitySent)")
            onResponse("Waiting for carrier response...", false)
        } else {
            Log.d(TAG, "⏳ Sent response '$trimmed', waiting for carrier network...")
            onResponse("Waiting for carrier response...", false)
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

