package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for querying and mutating Gemini-extracted member tasks and calendar action items.
 */
@Dao
interface MemberTaskDao {

    @Query("SELECT * FROM member_tasks ORDER BY createdAt DESC, id DESC")
    fun observeAllMemberTasks(): Flow<List<MemberTaskEntity>>

    @Query("SELECT * FROM member_tasks WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeTasksForSession(sessionId: String): Flow<List<MemberTaskEntity>>

    @Query("SELECT * FROM member_tasks WHERE assigneeName LIKE '%' || :assigneeName || '%' ORDER BY calendarDate ASC, id DESC")
    fun observeTasksForAssignee(assigneeName: String): Flow<List<MemberTaskEntity>>

    @Query("SELECT * FROM member_tasks WHERE calendarDate = :calendarDate ORDER BY id ASC")
    fun observeTasksForCalendarDate(calendarDate: String): Flow<List<MemberTaskEntity>>

    @Query("SELECT * FROM member_tasks ORDER BY createdAt DESC, id DESC")
    suspend fun getAllMemberTasksOnce(): List<MemberTaskEntity>

    @Query("SELECT * FROM member_tasks WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getTasksForSessionOnce(sessionId: String): List<MemberTaskEntity>

    @Query("SELECT * FROM member_tasks WHERE assigneeName LIKE '%' || :assigneeName || '%' ORDER BY calendarDate ASC, id DESC")
    suspend fun getTasksForAssigneeOnce(assigneeName: String): List<MemberTaskEntity>

    @Query("SELECT * FROM member_tasks WHERE calendarDate = :calendarDate ORDER BY id ASC")
    suspend fun getTasksForCalendarDateOnce(calendarDate: String): List<MemberTaskEntity>

    @Query("SELECT * FROM member_tasks WHERE isDone = 0 ORDER BY calendarDate ASC, id ASC")
    fun observePendingDeadlineTasks(): Flow<List<MemberTaskEntity>>

    @Query("SELECT * FROM member_tasks WHERE isDone = 0 ORDER BY calendarDate ASC, id ASC")
    suspend fun getPendingDeadlineTasksOnce(): List<MemberTaskEntity>

    @Query("SELECT * FROM member_tasks WHERE isDone = 0 AND assigneeName LIKE '%' || :memberName || '%' ORDER BY calendarDate ASC, id ASC")
    suspend fun getPendingDeadlineTasksForMemberOnce(memberName: String): List<MemberTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberTask(task: MemberTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberTasks(tasks: List<MemberTaskEntity>): List<Long>

    @Update
    suspend fun updateMemberTask(task: MemberTaskEntity)

    @Query("UPDATE member_tasks SET isDone = :isDone, status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, isDone: Boolean, status: String)

    @Query("DELETE FROM member_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("DELETE FROM member_tasks WHERE sessionId = :sessionId")
    suspend fun deleteTasksForSession(sessionId: String)

    @Query("DELETE FROM member_tasks")
    suspend fun clearAllMemberTasks()
}
