package com.example.engine

import android.os.Handler
import android.os.Looper

class TimeoutManager {

    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    @Volatile
    private var isResponseReceived = false

    fun startTimeout(
        durationMs: Long = 30000L,
        onTimeout: () -> Unit
    ) {
        cancelTimeout()
        isResponseReceived = false

        val runnable = Runnable {
            if (!isResponseReceived) {
                onTimeout()
            }
        }
        timeoutRunnable = runnable
        handler.postDelayed(runnable, durationMs)
    }

    fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun markResponseReceived() {
        isResponseReceived = true
        cancelTimeout()
    }

    fun isReceived(): Boolean = isResponseReceived
}
