package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for querying and mutating meeting attendees reactively.
 */
@Dao
interface AttendeeDao {

    @Query("SELECT * FROM meeting_attendees ORDER BY createdAt ASC")
    fun getAllAttendees(): Flow<List<AttendeeEntity>>

    @Query("SELECT * FROM meeting_attendees WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getAttendeesBySession(sessionId: String): Flow<List<AttendeeEntity>>

    @Query("SELECT * FROM meeting_attendees WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getAttendeesBySessionOnce(sessionId: String): List<AttendeeEntity>

    @Query("SELECT * FROM meeting_attendees WHERE id = :id LIMIT 1")
    suspend fun getAttendeeById(id: String): AttendeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendee(attendee: AttendeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAttendees(attendees: List<AttendeeEntity>)

    @Update
    suspend fun updateAttendee(attendee: AttendeeEntity)

    @Query(
        "UPDATE meeting_attendees SET isPresent = :isPresent, attendanceStatus = :status, checkInTime = :checkInTime WHERE id = :id"
    )
    suspend fun updateAttendanceStatus(
        id: String,
        isPresent: Boolean,
        status: String,
        checkInTime: String?
    )

    @Query("UPDATE meeting_attendees SET role = :role WHERE id = :id")
    suspend fun updateAttendeeRole(id: String, role: String)

    @Delete
    suspend fun deleteAttendee(attendee: AttendeeEntity)

    @Query("DELETE FROM meeting_attendees WHERE id = :id")
    suspend fun deleteAttendeeById(id: String)

    @Query("DELETE FROM meeting_attendees WHERE sessionId = :sessionId")
    suspend fun clearSessionAttendees(sessionId: String)

    @Query("DELETE FROM meeting_attendees")
    suspend fun clearAllAttendees()
}
