package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

interface UssdCallback {
    fun onResponse(response: String)
    fun onError(error: String)
}

object RealUssdService {

    private const val TAG = "RealUssdService"

    /**
     * Executes internal USSD requests using Android TelephonyManager.sendUssdRequest (API 26+)
     * without opening any external system dialer.
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
            callback.onError("Permission required: Please grant Phone/Call permission in Trust Center to execute USSD requests internally.")
            return
        }

        // 3. Network and SIM state checks
        val telephonyManager = getTelephonyManagerForSlot(context, simSlot)
        if (telephonyManager.simState == TelephonyManager.SIM_STATE_ABSENT) {
            callback.onError("No active SIM card found in slot ${simSlot + 1}. Please insert a valid SIM.")
            return
        }

        // 4. Direct internal USSD execution via TelephonyManager.sendUssdRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                telephonyManager.sendUssdRequest(
                    cleanCode,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephony: TelephonyManager,
                            request: String,
                            response: CharSequence
                        ) {
                            Log.i(TAG, "Real carrier USSD response received internally for $request: $response")
                            val responseText = response.toString().trim()
                            if (responseText.isEmpty()) {
                                callback.onError("Empty response received from mobile carrier.")
                                return
                            }
                            if (UssdSessionManager.isCarrierErrorResponse(responseText)) {
                                callback.onError("Carrier error: $responseText")
                                return
                            }
                            callback.onResponse(responseText)
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephony: TelephonyManager,
                            request: String,
                            failureCode: Int
                        ) {
                            val errorDesc = when (failureCode) {
                                TelephonyManager.USSD_RETURN_FAILURE -> "Carrier rejected USSD request or returned an error."
                                -1 -> "Invalid USSD format or carrier timeout."
                                else -> "Carrier USSD request failed (Code: $failureCode)."
                            }
                            Log.w(TAG, "Real carrier USSD failed ($failureCode): $errorDesc")
                            callback.onError(errorDesc)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
                Log.i(TAG, "Dispatched internal sendUssdRequest for $cleanCode on SIM slot $simSlot")
                return
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException on sendUssdRequest", se)
                callback.onError("Permission denied to send USSD request internally: ${se.message}")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Exception on sendUssdRequest", e)
                callback.onError("Failed to execute internal USSD request: ${e.message}")
                return
            }
        } else {
            callback.onError("Internal USSD requires Android 8.0 (API 26) or newer.")
        }
    }

    private fun getTelephonyManagerForSlot(context: Context, simSlot: Int): TelephonyManager {
        val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subList = subManager?.activeSubscriptionInfoList
                val targetSub = subList?.firstOrNull { it.simSlotIndex == simSlot }
                if (targetSub != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return baseTm.createForSubscriptionId(targetSub.subscriptionId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not create TelephonyManager for subscription slot $simSlot", e)
            }
        }
        return baseTm
    }
}
