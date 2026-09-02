package com.example.engine

import android.content.Context
import android.util.Log

object EmergencyStopManager {

    private const val TAG = "EmergencyStopManager"
    private var isEmergencyStopActive = false
    private val activeSessions = mutableListOf<String>()

    fun registerSession(sessionId: String) {
        if (isEmergencyStopActive) {
            Log.w(TAG, "Blocked session registration because emergency stop is active")
            return
        }
        synchronized(activeSessions) {
            activeSessions.add(sessionId)
        }
    }

    fun unregisterSession(sessionId: String) {
        synchronized(activeSessions) {
            activeSessions.remove(sessionId)
        }
    }

    fun emergencyStopAll(context: Context? = null) {
        Log.i(TAG, "Initiating Emergency Stop on all USSD sessions...")
        isEmergencyStopActive = true
        synchronized(activeSessions) {
            activeSessions.clear()
        }
        UssdSessionManager.emergencyStop(context)
        isEmergencyStopActive = false
    }

    fun isEmergencyStopActive(): Boolean = isEmergencyStopActive
}
