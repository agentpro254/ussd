package com.example.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ui.TransparentActivity

interface UssdCallback {
    fun onResponse(response: String)
    fun onError(error: String)
}

object RealUssdService {

    private const val TAG = "RealUssdService"

    /**
     * Initiates USSD requests via TransparentActivity (Intent.ACTION_CALL).
     * CodeeAccessibilityService captures carrier dialogs from the background dialer window.
     */
    fun dialCode(
        context: Context,
        code: String,
        simSlot: Int = 0,
        callback: UssdCallback
    ) {
        val cleanCode = code.trim()

        // 1. Validation
        val validation = CodeValidator.validateCode(cleanCode)
        if (validation is ValidationResult.Invalid) {
            callback.onError(validation.reason)
            return
        }
        if (validation is ValidationResult.Blocked) {
            callback.onError(validation.reason)
            return
        }

        // 2. Telephony permission check
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPermission) {
            callback.onError("Permission required: Please grant Phone/Call permission in Trust Center to execute USSD requests.")
            return
        }

        // 3. Initiate hidden dialing via TransparentActivity
        try {
            Log.d(TAG, "🚀 Initiating hidden dialing via TransparentActivity for $cleanCode (slot=$simSlot)")
            val intent = Intent(context, TransparentActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(TransparentActivity.EXTRA_USSD_CODE, cleanCode)
                putExtra(TransparentActivity.EXTRA_SLOT_INDEX, simSlot)
            }
            context.startActivity(intent)
            callback.onResponse("Waiting for carrier response...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch TransparentActivity", e)
            callback.onError("Failed to initiate USSD request: ${e.message}")
        }
    }
}
