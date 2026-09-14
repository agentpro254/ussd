package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.permissions.PermissionManager

class CodeeOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_SHOW_OVERLAY) {
            showOverlay()
        } else if (action == ACTION_HIDE_OVERLAY) {
            hideOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        // Floating system overlay card disabled in favor of dedicated in-app session screen
    }

    private fun hideOverlay() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "codee USSD Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active USSD automation overlay"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("codee USSD Automation")
            .setContentText("Intercepting USSD dialog in background")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "codee_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW_OVERLAY = "com.example.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.HIDE_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, CodeeOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CodeeOverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.stopService(intent)
        }
    }
}
