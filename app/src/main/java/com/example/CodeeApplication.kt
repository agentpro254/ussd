package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.engine.UssdSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CodeeApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy {
        AppDatabase.getDatabase(this, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CRASH_DEBUG", "CRASH in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                getSharedPreferences("codee_crash_log", MODE_PRIVATE).edit()
                    .putString("last_crash", "${throwable.javaClass.simpleName}: ${throwable.message}\n" + throwable.stackTraceToString())
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            UssdSessionManager.initialize(database)
        } catch (e: Exception) {
            android.util.Log.e("CodeeApplication", "Failed to initialize database/session manager", e)
        }
    }
}
