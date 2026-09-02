package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity

class TransparentActivity : Activity() {

    companion object {
        const val EXTRA_USSD_CODE = "extra_ussd_code"
        const val EXTRA_SUBSCRIPTION_ID = "extra_subscription_id"
        const val EXTRA_SLOT_INDEX = "extra_slot_index"
        private const val TAG = "TransparentActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent.getStringExtra(EXTRA_USSD_CODE)
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)

        if (!code.isNullOrBlank()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val encodedUri = Uri.parse("tel:" + Uri.encode(code))
                    val callIntent = Intent(Intent.ACTION_CALL, encodedUri).apply {
                        if (subscriptionId >= 0) {
                            putExtra("subscription_id", subscriptionId)
                            putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", subscriptionId)
                            putExtra("com.android.phone.extra.slot", slotIndex)
                            putExtra("simSlot", slotIndex)
                        }
                    }
                    startActivity(callIntent)
                    Log.d(TAG, "🚀 Launched ACTION_CALL via TransparentActivity for $code")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error launching ACTION_CALL", e)
                }
            } else {
                Log.w(TAG, "⚠️ CALL_PHONE permission not granted in TransparentActivity")
            }
        }

        // [BRING MY APP TO FRONT]: Immediately reorder MainActivity back to front to cover system dialer popup
        try {
            val bringAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(bringAppIntent)
            Log.d(TAG, "📲 Reordered MainActivity to front immediately")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring MainActivity to front", e)
        }

        finish()
    }
}
