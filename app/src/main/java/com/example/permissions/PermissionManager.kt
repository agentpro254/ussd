package com.example.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.data.model.SimCardInfo
import com.example.service.CodeeAccessibilityService

object PermissionManager {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val targetServiceId = "${context.packageName}/${CodeeAccessibilityService::class.java.canonicalName}"
        val shortTargetServiceId = "${context.packageName}/${CodeeAccessibilityService::class.java.name}"
        return enabledServices.any { 
            it.id.equals(targetServiceId, ignoreCase = true) ||
            it.id.equals(shortTargetServiceId, ignoreCase = true) ||
            it.id.contains(CodeeAccessibilityService::class.java.simpleName)
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isCallPhoneGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isReadPhoneStateGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isPostNotificationsGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isReadSmsGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getAvailableSimCards(context: Context): List<SimCardInfo> {
        val simList = mutableListOf<SimCardInfo>()
        try {
            if (isReadPhoneStateGranted(context)) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    for (info in activeList) {
                        simList.add(
                            SimCardInfo(
                                slotIndex = info.simSlotIndex,
                                carrierName = info.carrierName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                                displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                                subscriptionId = info.subscriptionId,
                                isAvailable = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback gracefully
        }

        if (simList.isEmpty()) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrier = tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Default SIM"
            simList.add(
                SimCardInfo(
                    slotIndex = 0,
                    carrierName = carrier,
                    displayName = "SIM 1 ($carrier)",
                    subscriptionId = 0,
                    isAvailable = true
                )
            )
            simList.add(
                SimCardInfo(
                    slotIndex = 1,
                    carrierName = "SIM 2",
                    displayName = "SIM 2 (Secondary)",
                    subscriptionId = 1,
                    isAvailable = true
                )
            )
        }
        return simList
    }
}
