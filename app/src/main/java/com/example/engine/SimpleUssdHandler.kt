package com.example.engine

import android.content.Context
import android.util.Log

object SimpleUssdHandler {

    private const val TAG = "SimpleUssdHandler"

    fun dialCode(context: Context, code: String, subscriptionId: Int, slotIndex: Int, callback: (String, Boolean) -> Unit) {
        Log.d("DIAL_DEBUG", "dialCode() ENTERED. code=$code, subId=$subscriptionId, slot=$slotIndex")
        try {
            val encodedUri = android.net.Uri.parse("tel:" + android.net.Uri.encode(code))
            val callIntent = android.content.Intent(android.content.Intent.ACTION_CALL, encodedUri).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                if (slotIndex >= 0) {
                    putExtra("com.android.phone.extra.slot", slotIndex)
                    putExtra("simSlot", slotIndex)
                }
            }
            val transparentIntent = android.content.Intent(context, com.example.ui.TransparentActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("call_intent", callIntent)
            }
            context.startActivity(transparentIntent)
            Log.d("DIAL_DEBUG", "Launched ACTION_CALL for $code")
        } catch (e: Exception) {
            Log.e("DIAL_DEBUG", "Failed to launch ACTION_CALL: ${e.message}", e)
        }
    }
}
