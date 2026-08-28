package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UssdDao {

    @Query("SELECT * FROM saved_routines ORDER BY isFavorite DESC, lastUsedTimestamp DESC, id DESC")
    fun getAllRoutines(): Flow<List<SavedUssdRoutine>>

    @Query("SELECT * FROM saved_routines WHERE isFavorite = 1 ORDER BY lastUsedTimestamp DESC")
    fun getFavoriteRoutines(): Flow<List<SavedUssdRoutine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: SavedUssdRoutine): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(routines: List<SavedUssdRoutine>)

    @Update
    suspend fun updateRoutine(routine: SavedUssdRoutine)

    @Delete
    suspend fun deleteRoutine(routine: SavedUssdRoutine)

    @Query("DELETE FROM saved_routines WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)

    @Query("SELECT * FROM ussd_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<UssdHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: UssdHistoryItem): Long

    @Query("DELETE FROM ussd_history")
    suspend fun clearHistory()

    @Query("DELETE FROM ussd_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)
}
