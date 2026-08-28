package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SavedUssdRoutine::class, UssdHistoryItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ussdDao(): UssdDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "codee_ussd_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialPresets(database.ussdDao())
                    }
                }
            }

            private suspend fun populateInitialPresets(dao: UssdDao) {
                val presets = listOf(
                    SavedUssdRoutine(
                        title = "Check Main Balance",
                        ussdCode = "*123#",
                        category = "Balance",
                        stepsCsv = "",
                        simSlot = 0,
                        isFavorite = true,
                        iconName = "account_balance_wallet",
                        colorHex = "#0D9488",
                        description = "Quick balance & active tariff check",
                        lastUsedTimestamp = System.currentTimeMillis()
                    ),
                    SavedUssdRoutine(
                        title = "Quick Airtime (Self)",
                        ussdCode = "*131*1#",
                        category = "Airtime",
                        stepsCsv = "1,1",
                        simSlot = 0,
                        isFavorite = true,
                        iconName = "phone_android",
                        colorHex = "#6366F1",
                        description = "Instant airtime top-up via mobile wallet",
                        lastUsedTimestamp = System.currentTimeMillis() - 3600000
                    ),
                    SavedUssdRoutine(
                        title = "Daily 2GB Data Bundle",
                        ussdCode = "*141*2#",
                        category = "Data Bundles",
                        stepsCsv = "1,2,1",
                        simSlot = 0,
                        isFavorite = true,
                        iconName = "wifi",
                        colorHex = "#10B981",
                        description = "Automated high-speed daily internet bundle",
                        lastUsedTimestamp = System.currentTimeMillis() - 7200000
                    ),
                    SavedUssdRoutine(
                        title = "Mobile Money Transfer",
                        ussdCode = "*185#",
                        category = "Transfer",
                        stepsCsv = "1",
                        simSlot = 0,
                        isFavorite = false,
                        iconName = "send",
                        colorHex = "#F59E0B",
                        description = "P2P mobile wallet cash transfer",
                        lastUsedTimestamp = 0
                    ),
                    SavedUssdRoutine(
                        title = "Bank Instant Balance",
                        ussdCode = "*737*0#",
                        category = "Banking",
                        stepsCsv = "",
                        simSlot = 0,
                        isFavorite = false,
                        iconName = "account_balance",
                        colorHex = "#8B5CF6",
                        description = "Direct core banking balance check",
                        lastUsedTimestamp = 0
                    ),
                    SavedUssdRoutine(
                        title = "Pay Electricity Token",
                        ussdCode = "*150*00#",
                        category = "Utility",
                        stepsCsv = "4,1",
                        simSlot = 0,
                        isFavorite = false,
                        iconName = "flash_on",
                        colorHex = "#EC4899",
                        description = "Prepaid power utility recharge",
                        lastUsedTimestamp = 0
                    )
                )
                dao.insertRoutines(presets)
            }
        }
    }
}
