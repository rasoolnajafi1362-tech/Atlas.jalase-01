package com.example.data.local

import com.example.data.Member
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting Room database operations for meeting attendees.
 * Follows the Room skill mandate to separate DAO access from ViewModel and UI layers.
 */
class AttendeeRepository(private val attendeeDao: AttendeeDao) {

    val allAttendees: Flow<List<AttendeeEntity>> = attendeeDao.getAllAttendees()

    fun getAttendeesForSession(sessionId: String): Flow<List<AttendeeEntity>> {
        return attendeeDao.getAttendeesBySession(sessionId)
    }

    suspend fun getAttendeesForSessionOnce(sessionId: String): List<AttendeeEntity> {
        return attendeeDao.getAttendeesBySessionOnce(sessionId)
    }

    suspend fun upsertAttendee(attendee: AttendeeEntity) {
        attendeeDao.insertAttendee(attendee)
    }

    suspend fun upsertAll(attendees: List<AttendeeEntity>) {
        attendeeDao.insertAllAttendees(attendees)
    }

    suspend fun updateAttendee(attendee: AttendeeEntity) {
        attendeeDao.updateAttendee(attendee)
    }

    suspend fun updateAttendanceStatus(
        id: String,
        isPresent: Boolean,
        status: String = if (isPresent) AttendeeEntity.STATUS_PRESENT else AttendeeEntity.STATUS_ABSENT,
        checkInTime: String? = null
    ) {
        attendeeDao.updateAttendanceStatus(
            id = id,
            isPresent = isPresent,
            status = status,
            checkInTime = checkInTime
        )
    }

    suspend fun updateRole(id: String, role: String) {
        attendeeDao.updateAttendeeRole(id, role)
    }

    suspend fun deleteById(id: String) {
        attendeeDao.deleteAttendeeById(id)
    }

    suspend fun syncSessionMembersToRoom(sessionId: String, members: List<Member>) {
        val existingById = attendeeDao.getAttendeesBySessionOnce(sessionId).associateBy { it.id }
        attendeeDao.clearSessionAttendees(sessionId)
        val entities = members.mapIndexed { idx, member ->
            val prev = existingById[member.id]
            val preservedStatus = if (prev != null && prev.isPresent == member.isPresent) {
                prev.attendanceStatus
            } else {
                if (member.isPresent) AttendeeEntity.STATUS_PRESENT else AttendeeEntity.STATUS_ABSENT
            }
            AttendeeEntity(
                id = member.id,
                sessionId = sessionId,
                name = member.name,
                role = member.title,
                department = member.unit,
                isPresent = member.isPresent,
                attendanceStatus = preservedStatus,
                checkInTime = prev?.checkInTime,
                createdAt = prev?.createdAt ?: (System.currentTimeMillis() + idx)
            )
        }
        if (entities.isNotEmpty()) {
            attendeeDao.insertAllAttendees(entities)
        }
    }
}
