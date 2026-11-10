package com.example.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Repository managing Room database persistence for Gemini-extracted member tasks
 * (لیست وظایف اعضا) and automatic calendar scheduling (`calendarDate`).
 */
class MemberTaskRepository(
    private val memberTaskDao: MemberTaskDao
) {

    val allMemberTasksFlow: Flow<List<MemberTaskEntity>> =
        memberTaskDao.observeAllMemberTasks()

    fun observeTasksForSession(sessionId: String): Flow<List<MemberTaskEntity>> =
        memberTaskDao.observeTasksForSession(sessionId)

    fun observeTasksForAssignee(assigneeName: String): Flow<List<MemberTaskEntity>> =
        memberTaskDao.observeTasksForAssignee(assigneeName)

    fun observeTasksForCalendarDate(calendarDate: String): Flow<List<MemberTaskEntity>> =
        memberTaskDao.observeTasksForCalendarDate(calendarDate)

    suspend fun getAllMemberTasksOnce(): List<MemberTaskEntity> =
        memberTaskDao.getAllMemberTasksOnce()

    suspend fun getTasksForSessionOnce(sessionId: String): List<MemberTaskEntity> =
        memberTaskDao.getTasksForSessionOnce(sessionId)

    suspend fun getTasksForAssigneeOnce(assigneeName: String): List<MemberTaskEntity> =
        memberTaskDao.getTasksForAssigneeOnce(assigneeName)

    suspend fun getTasksForCalendarDateOnce(calendarDate: String): List<MemberTaskEntity> =
        memberTaskDao.getTasksForCalendarDateOnce(calendarDate)

    val pendingDeadlineTasksFlow: Flow<List<MemberTaskEntity>> =
        memberTaskDao.observePendingDeadlineTasks()

    suspend fun getPendingDeadlineTasksOnce(): List<MemberTaskEntity> =
        memberTaskDao.getPendingDeadlineTasksOnce()

    suspend fun getPendingDeadlineTasksForMemberOnce(memberName: String): List<MemberTaskEntity> =
        memberTaskDao.getPendingDeadlineTasksForMemberOnce(memberName)

    /**
     * Automatically saves extracted member tasks into the Room database (`member_tasks` table),
     * deduplicating by (sessionId, assigneeName, taskTitle) so repeated runs update cleanly.
     */
    suspend fun saveExtractedMemberTasks(
        sessionId: String,
        tasks: List<MemberTaskEntity>
    ): List<MemberTaskEntity> {
        val existing = memberTaskDao.getTasksForSessionOnce(sessionId)
        val result = mutableListOf<MemberTaskEntity>()

        for (task in tasks) {
            val duplicate = existing.find {
                it.assigneeName.trim() == task.assigneeName.trim() &&
                    it.taskTitle.trim() == task.taskTitle.trim()
            }
            if (duplicate != null) {
                val updated = task.copy(id = duplicate.id, isDone = duplicate.isDone, status = duplicate.status)
                memberTaskDao.updateMemberTask(updated)
                result.add(updated)
            } else {
                val rowId = memberTaskDao.insertMemberTask(task)
                result.add(task.copy(id = rowId.toInt()))
            }
        }
        return result
    }

    suspend fun toggleTaskDone(task: MemberTaskEntity): MemberTaskEntity {
        val nextDone = !task.isDone
        val nextStatus = if (nextDone) "COMPLETED" else "IN_PROGRESS"
        val updated = task.copy(isDone = nextDone, status = nextStatus)
        memberTaskDao.updateMemberTask(updated)
        return updated
    }

    suspend fun deleteTaskById(id: Int) {
        memberTaskDao.deleteTaskById(id)
    }

    suspend fun deleteTasksForSession(sessionId: String) {
        memberTaskDao.deleteTasksForSession(sessionId)
    }

    suspend fun clearAll() {
        memberTaskDao.clearAllMemberTasks()
    }
}
