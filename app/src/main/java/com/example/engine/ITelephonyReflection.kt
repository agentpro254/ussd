package com.example.engine

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.telephony.TelephonyManager
import android.util.Log
import java.lang.reflect.Method

object ITelephonyReflection {

    private const val TAG = "ITelephonyReflection"

    interface ReflectionCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    /**
     * Attempts to execute USSD via internal ITelephony reflection.
     * Returns true if invocation succeeded, false if reflection failed.
     */
    fun sendUssdViaReflection(
        context: Context,
        ussdCode: String,
        subId: Int = -1,
        callback: ReflectionCallback
    ): Boolean {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false

            // Get ITelephony interface through reflection
            val getITelephonyMethod: Method = tm.javaClass.getDeclaredMethod("getITelephony").apply {
                isAccessible = true
            }
            val iTelephony = getITelephonyMethod.invoke(tm) ?: run {
                Log.w(TAG, "getITelephony returned null")
                return false
            }

            Log.d(TAG, "🔍 Retrieved ITelephony instance: ${iTelephony.javaClass.name}")

            // Prepare ResultReceiver for callbacks
            val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val response = resultData?.getCharSequence("UssdResponse")?.toString()
                        ?: resultData?.getString("response")
                        ?: resultData?.getString("ussd_message")
                        ?: ""
                    Log.d(TAG, "📥 ITelephony ResultReceiver code=$resultCode, response=$response")
                    if (response.isNotBlank()) {
                        callback.onSuccess(response)
                    } else if (resultCode != 0) {
                        callback.onError("Carrier returned code: $resultCode")
                    }
                }
            }

            // Try different ITelephony method signatures across Android versions & OEMs
            val methods = iTelephony.javaClass.declaredMethods

            // 1. sendUssdRequest(subId, ussdCode, resultReceiver)
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

            // 2. sendUssdRequest(ussdCode, resultReceiver)
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

            // 3. handlePinMmiForSubscriber(subId, dialString)
            val handlePinMmiSub = methods.firstOrNull {
                it.name == "handlePinMmiForSubscriber" && it.parameterTypes.size == 2
            }
            if (handlePinMmiSub != null && subId >= 0) {
                handlePinMmiSub.isAccessible = true
                val result = handlePinMmiSub.invoke(iTelephony, subId, ussdCode)
                Log.d(TAG, "✅ Invoked handlePinMmiForSubscriber -> $result")
                return true
            }

            // 4. handlePinMmi(dialString)
            val handlePinMmi = methods.firstOrNull {
                it.name == "handlePinMmi" && it.parameterTypes.size == 1
            }
            if (handlePinMmi != null) {
                handlePinMmi.isAccessible = true
                val result = handlePinMmi.invoke(iTelephony, ussdCode)
                Log.d(TAG, "✅ Invoked handlePinMmi -> $result")
                return true
            }

            Log.w(TAG, "No matching ITelephony USSD method found on this device")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception in ITelephony reflection: ${e.message}", e)
            return false
        }
    }
}
