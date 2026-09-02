package com.example.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.service.CodeeAccessibilityService

object AccessibilityHelper {

    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClassName: String = CodeeAccessibilityService::class.java.name
    ): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK or AccessibilityServiceInfo.FEEDBACK_GENERIC
            )

            val simpleName = CodeeAccessibilityService::class.java.simpleName

            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo?.serviceInfo ?: continue
                val id = "${serviceInfo.packageName}/${serviceInfo.name}"
                if (id.contains(serviceClassName, ignoreCase = true) ||
                    id.contains(simpleName, ignoreCase = true) ||
                    serviceInfo.name.contains(simpleName, ignoreCase = true)
                ) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ignored: Exception) {
            }
        }
    }
}
