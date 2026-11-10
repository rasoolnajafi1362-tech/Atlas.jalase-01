package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for Full-Text Search (FTS4 + filtered SQLite queries)
 * across saved meeting minutes (Title, Resolutions/Notes, and Executive Summary).
 */
@Dao
interface MeetingMinutesDao {

    @Query("SELECT * FROM meeting_minutes ORDER BY updatedAt DESC")
    fun observeAllMinutes(): Flow<List<MeetingMinutesEntity>>

    @Query("SELECT * FROM meeting_minutes ORDER BY updatedAt DESC")
    suspend fun getAllMinutesOnce(): List<MeetingMinutesEntity>

    @Query("SELECT * FROM meeting_minutes WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getMinutesBySessionId(sessionId: String): MeetingMinutesEntity?

    /**
     * Native SQLite FTS4 MATCH query against the virtual table [meeting_minutes_fts],
     * searching strictly across title, notesTextCorpus, and executiveSummary.
     */
    @Query(
        """
        SELECT meeting_minutes.* FROM meeting_minutes
        JOIN meeting_minutes_fts ON meeting_minutes.rowid = meeting_minutes_fts.rowid
        WHERE meeting_minutes_fts MATCH :ftsQuery
        ORDER BY meeting_minutes.updatedAt DESC
        """
    )
    suspend fun searchMinutesFtsMatch(ftsQuery: String): List<MeetingMinutesEntity>

    /**
     * Reactive Full-Text & Combined Filter query across the 3 user-confirmed fields
     * (title, notesTextCorpus, executiveSummary) and the 3 combined filters:
     * - [scopeFilter] (نوع جلسه)
     * - [statusFilter] (وضعیت مصوبات: ALL, PENDING, DONE)
     * - [memberFilter] (نام عضو مسئول)
     */
    @Query(
        """
        SELECT * FROM meeting_minutes
        WHERE (
            :query = ''
            OR title LIKE '%' || :query || '%'
            OR notesTextCorpus LIKE '%' || :query || '%'
            OR executiveSummary LIKE '%' || :query || '%'
        )
        AND (:scopeFilter = 'ALL' OR scope = :scopeFilter)
        AND (
            :statusFilter = 'ALL'
            OR (:statusFilter = 'PENDING' AND hasPendingActions = 1)
            OR (:statusFilter = 'DONE' AND allActionsDone = 1)
        )
        AND (
            :memberFilter = 'ALL'
            OR responsibleMembersCsv LIKE '%' || :memberFilter || '%'
            OR notesTextCorpus LIKE '%' || :memberFilter || '%'
        )
        ORDER BY updatedAt DESC
        """
    )
    fun observeFilteredMinutes(
        query: String,
        scopeFilter: String = "ALL",
        statusFilter: String = "ALL",
        memberFilter: String = "ALL"
    ): Flow<List<MeetingMinutesEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMinutes(entity: MeetingMinutesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMinutes(entities: List<MeetingMinutesEntity>)

    @Query("INSERT INTO meeting_minutes_fts(meeting_minutes_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    @Query("DELETE FROM meeting_minutes WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM meeting_minutes")
    suspend fun clearAllMinutes()
}
