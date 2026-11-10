package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.ExtractedActionItem
import java.util.UUID

/**
 * Room Entity storing individual member tasks (Action Items / وظایف محوله)
 * extracted by Gemini from meeting text and automatically linked to both
 * the member's task list and the organizational calendar (`calendarDate`).
 */
@Entity(
    tableName = "member_tasks",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["assigneeName"]),
        Index(value = ["calendarDate"])
    ]
)
data class MemberTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskId: String = "TASK-" + UUID.randomUUID().toString().take(8).uppercase(),
    val sessionId: String,
    val sessionTitle: String,
    val assigneeName: String,
    val assigneeRole: String = "",
    val taskTitle: String,
    val taskDescription: String = "",
    val deadlineLabel: String,
    val calendarDate: String,
    val priority: String = "بالا",
    val status: String = "IN_PROGRESS",
    val isDone: Boolean = false,
    val isAddedToCalendar: Boolean = true,
    val isAddedToMemberTaskList: Boolean = true,
    val sourceQuote: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toExtractedActionItem(): ExtractedActionItem {
        return ExtractedActionItem(
            id = taskId,
            title = taskTitle,
            assignee = assigneeName,
            deadline = "$deadlineLabel ($calendarDate)",
            priority = priority,
            isAddedToNotes = true
        )
    }
}
