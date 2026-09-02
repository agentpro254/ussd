package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

object NetworkChecker {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isMobileNetworkAvailable(context: Context): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false

        val simReady = telephonyManager.simState == TelephonyManager.SIM_STATE_READY
        val hasOperator = !telephonyManager.networkOperator.isNullOrEmpty() ||
                !telephonyManager.simOperatorName.isNullOrEmpty() ||
                !telephonyManager.networkOperatorName.isNullOrEmpty()

        return simReady || hasOperator
    }

    fun isSimReady(context: Context): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        return telephonyManager.simState == TelephonyManager.SIM_STATE_READY
    }

    fun getNetworkStatusSummary(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simState = when (telephonyManager?.simState) {
            TelephonyManager.SIM_STATE_READY -> "SIM Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM Card"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Locked"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Locked"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            else -> "SIM Available"
        }
        val opName = telephonyManager?.networkOperatorName?.ifBlank { null }
            ?: telephonyManager?.simOperatorName?.ifBlank { null }
            ?: "Mobile Network"
        return "$opName • $simState"
    }
}
