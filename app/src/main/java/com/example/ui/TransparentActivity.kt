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

        val passedCallIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("call_intent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>("call_intent")
        }

        var launchedCall = false
        val codeForLog = code ?: "code"

        if (passedCallIntent != null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    startActivity(passedCallIntent)
                    launchedCall = true
                    Log.d("DIAL_DEBUG", "TransparentActivity launched ACTION_CALL for $codeForLog")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error launching passed call_intent", e)
                }
            } else {
                Log.w(TAG, "⚠️ CALL_PHONE permission not granted in TransparentActivity")
            }
        } else if (!code.isNullOrBlank()) {
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
                    launchedCall = true
                    Log.d("DIAL_DEBUG", "TransparentActivity launched ACTION_CALL for $code")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error launching ACTION_CALL", e)
                }
            } else {
                Log.w(TAG, "⚠️ CALL_PHONE permission not granted in TransparentActivity")
            }
        }

        if (launchedCall) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val bringIntent = Intent(this, com.example.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(bringIntent)
                    Log.d("DIAL_DEBUG", "TransparentActivity brought app to front after 1500ms")
                } catch (e: Exception) {
                    Log.w("DIAL_DEBUG", "bringAppToFront failed: ${e.message}")
                }
                finish()
            }, 1500L)
        } else {
            finish()
        }
    }
}
