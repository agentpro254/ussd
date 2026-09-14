package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class CodeeOverlayService : Service() {

    override fun onCreate() {
        super.onCreate()
        // No foreground service. No notification. No crash.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        // Floating overlay disabled in favor of the dedicated in-app session screen
    }

    private fun hideOverlay() {
        // Nothing to hide
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.example.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.HIDE_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, CodeeOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CodeeOverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.stopService(intent)
        }
    }
}
