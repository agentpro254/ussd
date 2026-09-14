package com.example.engine

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.telephony.TelephonyManager
import android.util.Log
import java.lang.reflect.Method

/**
 * Helper class that handles USSD requests using official TelephonyManager on Android O+
 * and graceful fallback without violating Android 9+ Hidden API restrictions.
 */
object ITelephonyReflection {

    private const val TAG = "ITelephonyReflection"
    private const val ITELEPHONY_CLASS_NAME = "com.android.internal.telephony.ITelephony"

    interface ReflectionCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    /**
     * Executes a background USSD request via official SDK or legacy reflection.
     */
    fun sendUssdRequest(
        context: Context,
        code: String,
        subId: Int = -1,
        callback: ReflectionCallback? = null
    ): Boolean {
        return sendUssdViaReflection(context, code, subId, callback)
    }

    /**
     * Overload for direct code execution with Context.
     */
    fun sendUssdRequest(
        context: Context,
        code: String
    ): Boolean {
        return sendUssdViaReflection(context, code, -1, null)
    }

    /**
     * Attempts to execute USSD. On Android 8.0+ (API 26+), uses official public TelephonyManager.sendUssdRequest.
     * On Android 9.0+ (API 28+), bypasses blocked hidden API reflection.
     */
    fun sendUssdViaReflection(
        context: Context,
        ussdCode: String,
        subId: Int = -1,
        callback: ReflectionCallback? = null
    ): Boolean {
        // 1. On Android O (API 26) and above, use the official, public TelephonyManager.sendUssdRequest API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (tm != null) {
                    val telephonyManager = if (subId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        tm.createForSubscriptionId(subId)
                    } else {
                        tm
                    }
                    telephonyManager.sendUssdRequest(
                        ussdCode,
                        object : TelephonyManager.UssdResponseCallback() {
                            override fun onReceiveUssdResponse(
                                telephonyManager: TelephonyManager?,
                                request: String?,
                                response: CharSequence?
                            ) {
                                val resp = response?.toString() ?: ""
                                Log.d(TAG, "✅ Official TelephonyManager.sendUssdRequest response: $resp")
                                if (resp.isNotBlank()) {
                                    callback?.onSuccess(resp)
                                }
                            }

                            override fun onReceiveUssdResponseFailed(
                                telephonyManager: TelephonyManager?,
                                request: String?,
                                failureCode: Int
                            ) {
                                Log.w(TAG, "⚠️ TelephonyManager.sendUssdRequest failureCode: $failureCode")
                                callback?.onError("USSD failed with code: $failureCode")
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                    return true
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException in official sendUssdRequest: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Exception in official sendUssdRequest: ${e.message}")
            }
        }

        // 2. On Android 9.0+ (API 28+), TelephonyManager.getITelephony is blocked by hiddenapi enforcement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d(TAG, "Skipping hidden ITelephony reflection on Android 9+ (blocked API)")
            return false
        }

        // 3. Legacy reflection for older Android versions (API < 28)
        try {
            val iTelephony = getITelephonyInstance(context) ?: run {
                Log.w(TAG, "Unable to obtain ITelephony instance")
                callback?.onError("ITelephony instance unavailable")
                return false
            }

            Log.d(TAG, "🔍 Retrieved legacy ITelephony instance: ${iTelephony.javaClass.name}")

            val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val response = resultData?.getCharSequence("UssdResponse")?.toString()
                        ?: resultData?.getString("response")
                        ?: resultData?.getString("ussd_message")
                        ?: ""
                    Log.d(TAG, "📥 ITelephony ResultReceiver code=$resultCode, response=$response")
                    if (response.isNotBlank()) {
                        callback?.onSuccess(response)
                    } else if (resultCode != 0) {
                        callback?.onError("Carrier returned code: $resultCode")
                    }
                }
            }

            val methods = iTelephony.javaClass.declaredMethods

            val sendUssdWithSub = methods.firstOrNull { 
                it.name == "sendUssdRequest" && it.parameterTypes.size == 3 && 
                (it.parameterTypes[0] == Int::class.javaPrimitiveType || it.parameterTypes[0] == Integer::class.java) 
            }
            if (sendUssdWithSub != null && subId >= 0) {
                sendUssdWithSub.isAccessible = true
                sendUssdWithSub.invoke(iTelephony, subId, ussdCode, resultReceiver)
                Log.d(TAG, "✅ Invoked sendUssdRequest(subId, code, receiver)")
                return true
            }

            val sendUssdBasic = methods.firstOrNull { 
                it.name == "sendUssdRequest" && it.parameterTypes.size == 2 && 
                it.parameterTypes[0] == String::class.java 
            }
            if (sendUssdBasic != null) {
                sendUssdBasic.isAccessible = true
                sendUssdBasic.invoke(iTelephony, ussdCode, resultReceiver)
                Log.d(TAG, "✅ Invoked sendUssdRequest(code, receiver)")
                return true
            }

            val handlePinMmiSub = methods.firstOrNull {
                it.name == "handlePinMmiForSubscriber" && it.parameterTypes.size == 2
            }
            if (handlePinMmiSub != null && subId >= 0) {
                handlePinMmiSub.isAccessible = true
                val result = handlePinMmiSub.invoke(iTelephony, subId, ussdCode)
                Log.d(TAG, "✅ Invoked handlePinMmiForSubscriber -> $result")
                return true
            }

            val handlePinMmi = methods.firstOrNull {
                it.name == "handlePinMmi" && it.parameterTypes.size == 1
            }
            if (handlePinMmi != null) {
                handlePinMmi.isAccessible = true
                val result = handlePinMmi.invoke(iTelephony, ussdCode)
                Log.d(TAG, "✅ Invoked handlePinMmi -> $result")
                return true
            }

            Log.w(TAG, "No matching ITelephony USSD method found on this legacy device")
            callback?.onError("No matching ITelephony USSD method")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception in legacy reflection: ${e.message}", e)
            callback?.onError(e.message ?: "Reflection exception")
            return false
        }
    }

    /**
     * Resolves com.android.internal.telephony.ITelephony for legacy devices (Android < 9).
     */
    private fun getITelephonyInstance(context: Context): Any? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return null
        }

        // Strategy 1: TelephonyManager.getITelephony()
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                val getITelephonyMethod: Method = tm.javaClass.getDeclaredMethod("getITelephony").apply {
                    isAccessible = true
                }
                val instance = getITelephonyMethod.invoke(tm)
                if (instance != null) return instance
            }
        } catch (e: Exception) {
            Log.d(TAG, "TelephonyManager.getITelephony failed: ${e.message}")
        }

        // Strategy 2: ServiceManager.getService("phone") -> ITelephony.Stub.asInterface(binder)
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val phoneBinder = getServiceMethod.invoke(null, "phone") as? IBinder
            if (phoneBinder != null) {
                val iTelephonyStub = Class.forName("$ITELEPHONY_CLASS_NAME\$Stub")
                val asInterfaceMethod = iTelephonyStub.getMethod("asInterface", IBinder::class.java)
                val instance = asInterfaceMethod.invoke(null, phoneBinder)
                if (instance != null) return instance
            }
        } catch (e: Exception) {
            Log.d(TAG, "ServiceManager ITelephony stub reflection failed: ${e.message}")
        }

        return null
    }
}
