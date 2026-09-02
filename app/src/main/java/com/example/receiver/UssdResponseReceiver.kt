package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UssdResponseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UssdResponseReceiver"
        const val ACTION_USSD_RESPONSE = "android.intent.action.USSD_RESPONSE"
        const val EXTRA_USSD_RESPONSE = "ussd_response"
        const val EXTRA_USSD_IS_FINAL = "ussd_is_final"

        var onResponse: ((String, Boolean) -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_USSD_RESPONSE && !action.contains("USSD", ignoreCase = true)) {
            return
        }

        val message = intent.getStringExtra(EXTRA_USSD_RESPONSE)
            ?: intent.getStringExtra("message")
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: return

        val isFinal = intent.getBooleanExtra(EXTRA_USSD_IS_FINAL, false)

        try {
            // Consume the broadcast to prevent system dialer from taking over
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not abort broadcast: ${e.message}")
        }

        Log.d(TAG, "📱 USSD Response captured: $message | Final: $isFinal")
        onResponse?.invoke(message, isFinal)
    }
}
