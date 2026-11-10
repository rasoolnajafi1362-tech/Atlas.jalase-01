package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for storing and querying Gemini-generated Executive Summaries
 * created immediately after meeting audio recording finishes.
 */
@Dao
interface ExecutiveSummaryDao {

    @Query("SELECT * FROM executive_summaries ORDER BY createdAt DESC")
    fun observeAllExecutiveSummaries(): Flow<List<ExecutiveSummaryEntity>>

    @Query("SELECT * FROM executive_summaries WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeSummariesForSession(sessionId: String): Flow<List<ExecutiveSummaryEntity>>

    @Query("SELECT * FROM executive_summaries WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestSummaryForSession(sessionId: String): Flow<ExecutiveSummaryEntity?>

    @Query("SELECT * FROM executive_summaries WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSummaryForSessionOnce(sessionId: String): ExecutiveSummaryEntity?

    @Query("SELECT * FROM executive_summaries WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getSummariesForSessionOnce(sessionId: String): List<ExecutiveSummaryEntity>

    @Query("SELECT * FROM executive_summaries ORDER BY createdAt DESC")
    suspend fun getAllSummariesOnce(): List<ExecutiveSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutiveSummary(entity: ExecutiveSummaryEntity): Long

    @Query("DELETE FROM executive_summaries WHERE id = :id")
    suspend fun deleteSummaryById(id: Int)

    @Query("DELETE FROM executive_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummariesForSession(sessionId: String)

    @Query("DELETE FROM executive_summaries")
    suspend fun clearAllSummaries()
}
