package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.Member
import java.util.UUID

/**
 * Room Database Entity representing a meeting attendee, their organizational role,
 * department/unit, and real-time attendance status.
 */
@Entity(tableName = "meeting_attendees")
data class AttendeeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "DEFAULT",
    val name: String = "",
    val role: String = "",
    val department: String = "",
    val isPresent: Boolean = true,
    val attendanceStatus: String = if (isPresent) STATUS_PRESENT else STATUS_ABSENT,
    val checkInTime: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PRESENT = "حاضر"
        const val STATUS_ABSENT = "غایب"
        const val STATUS_LATE = "با تأخیر"
        const val STATUS_EXCUSED = "مرخصی / مأموریت"

        fun fromMember(member: Member, sessionId: String, index: Int = 0): AttendeeEntity {
            return AttendeeEntity(
                id = member.id,
                sessionId = sessionId,
                name = member.name,
                role = member.title,
                department = member.unit,
                isPresent = member.isPresent,
                attendanceStatus = if (member.isPresent) STATUS_PRESENT else STATUS_ABSENT,
                createdAt = System.currentTimeMillis() + index
            )
        }
    }

    fun toMember(): Member {
        return Member(
            id = id,
            name = name,
            title = role,
            unit = department,
            isPresent = isPresent
        )
    }
}
