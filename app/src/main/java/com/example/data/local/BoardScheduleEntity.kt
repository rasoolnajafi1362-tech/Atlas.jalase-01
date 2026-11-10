package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.MeetingNote
import com.example.data.MeetingRepository
import com.example.data.MeetingSession
import com.example.data.Member
import org.json.JSONObject

/**
 * Room Entity storing board meeting schedules — both upcoming scheduled board meetings
 * (جلسات پیش‌رو هیئت‌مدیره) and historical records of held meetings (سوابق جلسات برگزارشده) —
 * so board members have a unified, Room-backed calendar view of the board schedule.
 */
@Entity(
    tableName = "board_schedule",
    indices = [
        Index(value = ["sessionId"], unique = true),
        Index(value = ["date"]),
        Index(value = ["scheduleStatus"])
    ]
)
data class BoardScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sessionId: String,
    val title: String,
    val date: String,              // Normalized Jalali date: YYYY/MM/DD (e.g., "1405/08/22")
    val startTime: String = "10:00",
    val durationMins: Int = 90,
    val location: String = "سالن کنفرانس مرکزی هیئت‌مدیره",
    val scope: String = "internal", // internal, external_supplier, external_contract, interview_hr
    val extCompany: String = "",
    val scheduleStatus: String = STATUS_UPCOMING, // UPCOMING, COMPLETED, IN_PROGRESS
    val chairpersonName: String = "سخنران",
    val attendeesCsv: String = "سخنران | سخنران | سخنران | سخنران",
    val agendaSummary: String = "",
    val keyResolutionsSummary: String = "",
    val totalResolutionsCount: Int = 0,
    val completedResolutionsCount: Int = 0,
    val pendingResolutionsCount: Int = 0,
    val isFinalized: Boolean = false,
    val fullSessionJson: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isUpcoming: Boolean
        get() = scheduleStatus == STATUS_UPCOMING

    val isHistorical: Boolean
        get() = scheduleStatus == STATUS_COMPLETED

    fun getAttendeeNamesList(): List<String> {
        return attendeesCsv
            .split("|", "،", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun getKeyResolutionsList(): List<String> {
        return keyResolutionsSummary
            .split("\n", " • ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun toMeetingSession(): MeetingSession {
        if (fullSessionJson.isNotBlank()) {
            try {
                return MeetingRepository.jsonToSession(JSONObject(fullSessionJson))
            } catch (_: Exception) {
                // Fallback to constructing MeetingSession from entity fields
            }
        }
        val membersList = getAttendeeNamesList().ifEmpty {
            listOf("سخنران", "سخنران", "سخنران", "سخنران")
        }.mapIndexed { idx, name ->
            Member(
                name = name,
                title = when {
                    name.contains("سخنران") -> "مدیر عملیات و دبیر هیئت‌مدیره"
                    name.contains("سخنران") || name.contains("مدیرعامل") -> "مدیرعامل"
                    name.contains("سخنران") || name.contains("رئیس") -> "رئیس هیئت‌مدیره"
                    name.contains("سخنران") || name.contains("مالی") -> "مدیر مالی"
                    else -> if (idx == 0) "دبیر جلسه" else "عضو هیئت‌مدیره"
                },
                unit = "هیئت‌مدیره و مدیریت ارشد",
                isPresent = !isUpcoming
            )
        }
        val notesList = getKeyResolutionsList().mapIndexed { idx, resText ->
            MeetingNote(
                id = "${sessionId}-NOTE-$idx",
                timeFormatted = startTime,
                speaker = chairpersonName.ifBlank { "سخنران" },
                text = resText,
                type = if (isUpcoming) "📌 دستور کار پیش‌رو" else "🚨 مصوبه رسمی",
                due = date,
                isDone = isHistorical && idx < completedResolutionsCount
            )
        }
        return MeetingSession(
            id = sessionId,
            title = title,
            scope = scope,
            extCompany = extCompany,
            durationMins = durationMins,
            location = location,
            calendarType = "jalali",
            date = date,
            startTime = startTime,
            members = membersList,
            notes = notesList,
            aiSummary = agendaSummary,
            isFinalized = isFinalized || isHistorical
        )
    }

    companion object {
        const val STATUS_UPCOMING = "UPCOMING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"

        /**
         * Normalizes Persian digits to ASCII digits and formats date as YYYY/MM/DD.
         */
        fun normalizeDate(rawDate: String, fallback: String = "1405/08/15"): String {
            val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
            val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
            val converted = buildString {
                for (ch in rawDate.trim()) {
                    val pIdx = persianDigits.indexOf(ch)
                    val aIdx = arabicDigits.indexOf(ch)
                    when {
                        pIdx >= 0 -> append(('0'.code + pIdx).toChar())
                        aIdx >= 0 -> append(('0'.code + aIdx).toChar())
                        ch == '-' || ch == '.' -> append('/')
                        else -> append(ch)
                    }
                }
            }
            val parts = converted.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size == 3) {
                val y = parts[0].padStart(4, '0')
                val m = parts[1].padStart(2, '0')
                val d = parts[2].padStart(2, '0')
                if (y.all { it.isDigit() } && m.all { it.isDigit() } && d.all { it.isDigit() }) {
                    return "$y/$m/$d"
                }
            }
            return fallback
        }

        fun fromMeetingSession(
            session: MeetingSession,
            referenceDate: String = "1405/08/15",
            explicitStatus: String? = null,
            existingId: Int = 0
        ): BoardScheduleEntity {
            val normalizedDate = normalizeDate(session.date, referenceDate)
            val cleanTitle = session.title.ifBlank { "جلسه راهبردی هیئت‌مدیره (${session.id})" }
            val totalNotes = session.notes.size
            val doneNotes = session.notes.count { it.isDone }
            val pendingNotes = totalNotes - doneNotes

            val computedStatus = explicitStatus ?: when {
                normalizedDate > referenceDate -> STATUS_UPCOMING
                normalizedDate < referenceDate -> STATUS_COMPLETED
                session.notes.isNotEmpty() || session.aiSummary.isNotBlank() -> STATUS_COMPLETED
                else -> STATUS_UPCOMING
            }

            val summaryText = listOfNotNull(
                session.aiSummary.takeIf { it.isNotBlank() },
                session.transcriptionAnalysis?.executiveSummary?.takeIf { it.isNotBlank() },
                session.latestAudioAnalysis?.executiveSummary?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: if (session.notes.isNotEmpty()) {
                session.notes.joinToString(" • ") { it.text }
            } else {
                "بررسی دستور کار هیئت‌مدیره، پایش مصوبات قبلی و تصمیم‌گیری در خصوص اولویت‌های راهبردی."
            }

            val resolutionsText = if (session.notes.isNotEmpty()) {
                session.notes.joinToString("\n") { note ->
                    "${note.speaker}: ${note.text} (${if (note.isDone) "انجام‌شده" else "در جریان"})"
                }
            } else {
                session.transcriptionAnalysis?.keyDecisions.orEmpty().joinToString("\n")
            }

            val attendees = session.members.map { it.name.trim() }.filter { it.isNotBlank() }
            val attendeesCsv = if (attendees.isNotEmpty()) {
                attendees.joinToString(" | ")
            } else {
                "سخنران | سخنران | سخنران | سخنران"
            }

            val chair = session.members.firstOrNull()?.name?.ifBlank { "سخنران" } ?: "سخنران"

            return BoardScheduleEntity(
                id = existingId,
                sessionId = session.id,
                title = cleanTitle,
                date = normalizedDate,
                startTime = session.startTime.ifBlank { "10:00" },
                durationMins = session.durationMins.coerceAtLeast(15),
                location = session.location.ifBlank { "سالن کنفرانس مرکزی" },
                scope = session.scope.ifBlank { "internal" },
                extCompany = session.extCompany,
                scheduleStatus = computedStatus,
                chairpersonName = chair,
                attendeesCsv = attendeesCsv,
                agendaSummary = summaryText,
                keyResolutionsSummary = resolutionsText,
                totalResolutionsCount = totalNotes,
                completedResolutionsCount = doneNotes,
                pendingResolutionsCount = pendingNotes,
                isFinalized = session.isFinalized,
                fullSessionJson = MeetingRepository.sessionToJson(session).toString(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
