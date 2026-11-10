package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for querying and managing upcoming board meetings (جلسات پیش‌رو)
 * and historical meeting records (سوابق جلسات برگزارشده) in the Board Calendar view.
 */
@Dao
interface BoardScheduleDao {

    @Query("SELECT * FROM board_schedule ORDER BY date ASC, startTime ASC")
    fun observeAllScheduleItems(): Flow<List<BoardScheduleEntity>>

    @Query("SELECT * FROM board_schedule ORDER BY date ASC, startTime ASC")
    suspend fun getAllScheduleItemsOnce(): List<BoardScheduleEntity>

    @Query(
        """
        SELECT * FROM board_schedule
        WHERE scheduleStatus = 'UPCOMING' OR date > :referenceDate
        ORDER BY date ASC, startTime ASC
        """
    )
    fun observeUpcomingMeetings(referenceDate: String): Flow<List<BoardScheduleEntity>>

    @Query(
        """
        SELECT * FROM board_schedule
        WHERE scheduleStatus = 'UPCOMING' OR date > :referenceDate
        ORDER BY date ASC, startTime ASC
        """
    )
    suspend fun getUpcomingMeetingsOnce(referenceDate: String): List<BoardScheduleEntity>

    @Query(
        """
        SELECT * FROM board_schedule
        WHERE scheduleStatus != 'UPCOMING' AND date <= :referenceDate
        ORDER BY date DESC, startTime DESC
        """
    )
    fun observeHistoricalMeetings(referenceDate: String): Flow<List<BoardScheduleEntity>>

    @Query(
        """
        SELECT * FROM board_schedule
        WHERE scheduleStatus != 'UPCOMING' AND date <= :referenceDate
        ORDER BY date DESC, startTime DESC
        """
    )
    suspend fun getHistoricalMeetingsOnce(referenceDate: String): List<BoardScheduleEntity>

    @Query("SELECT * FROM board_schedule WHERE date = :date ORDER BY startTime ASC")
    fun observeMeetingsForDate(date: String): Flow<List<BoardScheduleEntity>>

    @Query("SELECT * FROM board_schedule WHERE date = :date ORDER BY startTime ASC")
    suspend fun getMeetingsForDateOnce(date: String): List<BoardScheduleEntity>

    @Query(
        """
        SELECT * FROM board_schedule
        WHERE :memberName = 'ALL'
           OR attendeesCsv LIKE '%' || :memberName || '%'
           OR chairpersonName LIKE '%' || :memberName || '%'
        ORDER BY date ASC, startTime ASC
        """
    )
    fun observeMeetingsByMember(memberName: String): Flow<List<BoardScheduleEntity>>

    @Query("SELECT * FROM board_schedule WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getScheduleBySessionId(sessionId: String): BoardScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleItem(item: BoardScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllScheduleItems(items: List<BoardScheduleEntity>)

    @Query("DELETE FROM board_schedule WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM board_schedule")
    suspend fun clearAllScheduleItems()
}
