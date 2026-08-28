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
        UssdSessionManager.initialize(database)
    }
}
