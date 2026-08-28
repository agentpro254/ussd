package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routines")
data class SavedUssdRoutine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val ussdCode: String,
    val category: String = "General",
    val stepsCsv: String = "", // e.g. "1,2,100"
    val simSlot: Int = 0,
    val isFavorite: Boolean = false,
    val iconName: String = "bolt",
    val colorHex: String = "#0D9488",
    val description: String = "",
    val lastUsedTimestamp: Long = 0
)

@Entity(tableName = "ussd_history")
data class UssdHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ussdCode: String,
    val serviceName: String,
    val summary: String,
    val rawLogs: String = "",
    val stepCount: Int = 1,
    val durationMs: Long = 0,
    val isSuccess: Boolean = true,
    val isSimulation: Boolean = false,
    val responseSequence: String = "", // e.g. "1 ➔ 0772123456 ➔ 50"
    val stepsSummary: String = ""
)
