package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.MeetingRepository
import com.example.data.MeetingSession
import org.json.JSONObject

/**
 * Room Entity storing saved meeting minutes for Full-Text Search (FTS)
 * and structured executive filtering.
 *
 * Indexed FTS fields (confirmed by user):
 * 1. title (عنوان رسمی جلسه)
 * 2. notesTextCorpus (متن مصوبات و تعهدات اجرایی)
 * 3. executiveSummary (خلاصه اجرایی صورتجلسه)
 */
@Entity(
    tableName = "meeting_minutes",
    indices = [Index(value = ["sessionId"], unique = true)]
)
data class MeetingMinutesEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowid: Int = 0,
    val sessionId: String,
    val title: String,
    val notesTextCorpus: String,
    val executiveSummary: String,
    val scope: String,
    val date: String,
    val extCompany: String,
    val totalNotesCount: Int,
    val doneNotesCount: Int,
    val pendingNotesCount: Int,
    val hasPendingActions: Boolean,
    val allActionsDone: Boolean,
    val responsibleMembersCsv: String,
    val fullSessionJson: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMeetingSession(): MeetingSession? {
        return try {
            MeetingRepository.jsonToSession(JSONObject(fullSessionJson))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun fromMeetingSession(session: MeetingSession, existingRowId: Int = 0): MeetingMinutesEntity {
            val cleanTitle = session.title.ifBlank { "جلسه بدون عنوان (${session.id})" }

            // Build resolutions & notes corpus (متن مصوبات و تعهدات)
            val notesLines = session.notes.map { note ->
                val statusTag = if (note.isDone) "[انجام‌شده]" else "[در جریان]"
                "${note.type} - ${note.speaker}: ${note.text} (مهلت: ${note.due}) $statusTag"
            }
            val aiDecisionsLines = session.transcriptionAnalysis?.keyDecisions.orEmpty()
            val aiActionsLines = session.transcriptionAnalysis?.actionItems.orEmpty().map { item ->
                "اقدام: ${item.title} - مسئول: ${item.assignee} (مهلت: ${item.deadline})"
            }
            val notesCorpus = (notesLines + aiDecisionsLines + aiActionsLines)
                .filter { it.isNotBlank() }
                .joinToString("\n")

            // Build executive summary corpus (خلاصه اجرایی)
            val summaryParts = listOfNotNull(
                session.aiSummary.takeIf { it.isNotBlank() },
                session.transcriptionAnalysis?.executiveSummary?.takeIf { it.isNotBlank() },
                session.latestAudioAnalysis?.executiveSummary?.takeIf { it.isNotBlank() }
            ).distinct()
            val executiveSummaryText = summaryParts.joinToString("\n\n")

            val totalNotes = session.notes.size
            val doneNotes = session.notes.count { it.isDone }
            val pendingNotes = totalNotes - doneNotes

            // Collect all responsible members / speakers for the Responsible Member filter
            val speakersAndMembers = (
                session.notes.map { it.speaker.trim() } +
                session.members.map { it.name.trim() } +
                session.transcriptionAnalysis?.actionItems.orEmpty().map { it.assignee.trim() }
            )
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" | ")

            return MeetingMinutesEntity(
                rowid = existingRowId,
                sessionId = session.id,
                title = cleanTitle,
                notesTextCorpus = notesCorpus,
                executiveSummary = executiveSummaryText,
                scope = session.scope.ifBlank { "internal" },
                date = session.date,
                extCompany = session.extCompany,
                totalNotesCount = totalNotes,
                doneNotesCount = doneNotes,
                pendingNotesCount = pendingNotes,
                hasPendingActions = pendingNotes > 0,
                allActionsDone = totalNotes > 0 && pendingNotes == 0,
                responsibleMembersCsv = speakersAndMembers,
                fullSessionJson = MeetingRepository.sessionToJson(session).toString(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}

/**
 * SQLite FTS4 Virtual Table backed by [MeetingMinutesEntity].
 * Indexes ONLY the 3 user-confirmed fields:
 * - [title] (عنوان جلسه)
 * - [notesTextCorpus] (متن مصوبات)
 * - [executiveSummary] (خلاصه اجرایی)
 */
@Fts4(contentEntity = MeetingMinutesEntity::class)
@Entity(tableName = "meeting_minutes_fts")
data class MeetingMinutesFtsEntity(
    val title: String,
    val notesTextCorpus: String,
    val executiveSummary: String
)
