package com.example.data.local

import com.example.data.MeetingSession
import com.example.data.PredefinedScenarios
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UI-ready search hit model representing a meeting minutes record matched
 * by the Room Full-Text Search (FTS) engine.
 */
data class MinutesFtsSearchResult(
    val entity: MeetingMinutesEntity,
    val matchedFields: List<String>,
    val primaryMatchSource: String,
    val matchedSnippet: String,
    val totalOccurrences: Int
)

/**
 * Repository managing Room Full-Text Search (FTS4 + filtered SQLite queries)
 * across saved meeting minutes (strictly indexing Title, Resolutions/Notes, and Executive Summary).
 */
class MinutesSearchRepository(private val minutesDao: MeetingMinutesDao) {

    val allMinutesFlow: Flow<List<MeetingMinutesEntity>> = minutesDao.observeAllMinutes()

    /**
     * Reactive Flow that returns filtered and FTS-matched meeting minutes with
     * extracted context snippets for keyword highlighting.
     */
    fun observeSearchMinutes(
        rawQuery: String,
        scopeFilter: String = "ALL",
        statusFilter: String = "ALL",
        memberFilter: String = "ALL"
    ): Flow<List<MinutesFtsSearchResult>> {
        val cleanQuery = rawQuery.trim()
        return minutesDao.observeFilteredMinutes(
            query = cleanQuery,
            scopeFilter = scopeFilter,
            statusFilter = statusFilter,
            memberFilter = memberFilter
        ).map { entities ->
            entities.mapNotNull { entity ->
                buildSearchResult(entity, cleanQuery)
            }
        }
    }

    /**
     * Executes both native SQLite FTS4 MATCH and filtered query for one-shot searches
     * and unit testing verification.
     */
    suspend fun searchMinutesOnce(
        rawQuery: String,
        scopeFilter: String = "ALL",
        statusFilter: String = "ALL",
        memberFilter: String = "ALL"
    ): List<MinutesFtsSearchResult> {
        val cleanQuery = rawQuery.trim()
        val ftsMatchedIds = mutableSetOf<String>()

        if (cleanQuery.isNotBlank()) {
            try {
                // Sanitize FTS token query with prefix wildcard
                val sanitizedToken = cleanQuery
                    .replace("\"", "")
                    .replace("'", "")
                    .replace("*", "")
                    .trim()
                if (sanitizedToken.isNotBlank()) {
                    val ftsHits = minutesDao.searchMinutesFtsMatch("$sanitizedToken*")
                    ftsHits.forEach { ftsMatchedIds.add(it.sessionId) }
                }
            } catch (e: Exception) {
                // Fallback to standard SQLite column check if FTS syntax has special chars
            }
        }

        val allEntities = minutesDao.getAllMinutesOnce()
        return allEntities.filter { entity ->
            val matchesQuery = if (cleanQuery.isBlank()) {
                true
            } else {
                ftsMatchedIds.contains(entity.sessionId) ||
                    entity.title.contains(cleanQuery, ignoreCase = true) ||
                    entity.notesTextCorpus.contains(cleanQuery, ignoreCase = true) ||
                    entity.executiveSummary.contains(cleanQuery, ignoreCase = true)
            }

            val matchesScope = scopeFilter == "ALL" || entity.scope == scopeFilter
            val matchesStatus = when (statusFilter) {
                "PENDING" -> entity.hasPendingActions
                "DONE" -> entity.allActionsDone
                else -> true
            }
            val matchesMember = memberFilter == "ALL" ||
                entity.responsibleMembersCsv.contains(memberFilter, ignoreCase = true) ||
                entity.notesTextCorpus.contains(memberFilter, ignoreCase = true)

            matchesQuery && matchesScope && matchesStatus && matchesMember
        }.mapNotNull { entity ->
            buildSearchResult(entity, cleanQuery)
        }
    }

    suspend fun upsertSessionMinutes(session: MeetingSession) {
        val existing = minutesDao.getMinutesBySessionId(session.id)
        val entity = MeetingMinutesEntity.fromMeetingSession(
            session = session,
            existingRowId = existing?.rowid ?: 0
        )
        minutesDao.insertMinutes(entity)
        try {
            minutesDao.rebuildFtsIndex()
        } catch (e: Exception) {}
    }

    suspend fun deleteSessionMinutes(sessionId: String) {
        minutesDao.deleteBySessionId(sessionId)
        try {
            minutesDao.rebuildFtsIndex()
        } catch (e: Exception) {}
    }

    suspend fun syncSessionsToMinutesIndex(
        activeSession: MeetingSession,
        archiveList: List<MeetingSession>,
        seedPredefinedScenariosIfArchiveEmpty: Boolean = true
    ) {
        val sessionsById = linkedMapOf<String, MeetingSession>()

        // Include predefined enterprise scenarios in the FTS index so managers can search
        // across all corporate governance, HR, and 5PL logistics templates right away
        if (seedPredefinedScenariosIfArchiveEmpty) {
            listOf(
                PredefinedScenarios.getScenario("board_cold_chain"),
                PredefinedScenarios.getScenario("hr_interview"),
                PredefinedScenarios.getScenario("supplier_contract")
            ).forEach { scenario ->
                sessionsById[scenario.id] = scenario
            }
        }

        archiveList.forEach { archived ->
            sessionsById[archived.id] = archived
        }

        // Active session always takes precedence for its ID
        if (activeSession.title.isNotBlank() || activeSession.notes.isNotEmpty() || activeSession.aiSummary.isNotBlank()) {
            sessionsById[activeSession.id] = activeSession
        }

        for ((id, sess) in sessionsById) {
            val existing = minutesDao.getMinutesBySessionId(id)
            val entity = MeetingMinutesEntity.fromMeetingSession(
                session = sess,
                existingRowId = existing?.rowid ?: 0
            )
            minutesDao.insertMinutes(entity)
        }

        try {
            minutesDao.rebuildFtsIndex()
        } catch (e: Exception) {}
    }

    companion object {
        const val FIELD_TITLE = "عنوان جلسه"
        const val FIELD_NOTES = "متن مصوبات"
        const val FIELD_SUMMARY = "خلاصه اجرایی"

        /**
         * Builds a [MinutesFtsSearchResult] strictly evaluating the 3 confirmed fields:
         * 1. title (عنوان جلسه)
         * 2. notesTextCorpus (متن مصوبات)
         * 3. executiveSummary (خلاصه اجرایی)
         */
        fun buildSearchResult(
            entity: MeetingMinutesEntity,
            cleanQuery: String
        ): MinutesFtsSearchResult? {
            if (cleanQuery.isBlank()) {
                val defaultPreview = when {
                    entity.executiveSummary.isNotBlank() -> entity.executiveSummary.take(150)
                    entity.notesTextCorpus.isNotBlank() -> entity.notesTextCorpus.take(150)
                    else -> entity.title
                }
                val defaultSource = when {
                    entity.executiveSummary.isNotBlank() -> FIELD_SUMMARY
                    entity.notesTextCorpus.isNotBlank() -> FIELD_NOTES
                    else -> FIELD_TITLE
                }
                val presentFields = buildList {
                    if (entity.title.isNotBlank()) add(FIELD_TITLE)
                    if (entity.notesTextCorpus.isNotBlank()) add(FIELD_NOTES)
                    if (entity.executiveSummary.isNotBlank()) add(FIELD_SUMMARY)
                }
                return MinutesFtsSearchResult(
                    entity = entity,
                    matchedFields = presentFields,
                    primaryMatchSource = defaultSource,
                    matchedSnippet = defaultPreview,
                    totalOccurrences = 0
                )
            }

            val matchedFields = mutableListOf<String>()
            var totalOccurrences = 0

            val titleOccurrences = countOccurrences(entity.title, cleanQuery)
            if (titleOccurrences > 0) {
                matchedFields.add(FIELD_TITLE)
                totalOccurrences += titleOccurrences
            }

            val notesOccurrences = countOccurrences(entity.notesTextCorpus, cleanQuery)
            if (notesOccurrences > 0) {
                matchedFields.add(FIELD_NOTES)
                totalOccurrences += notesOccurrences
            }

            val summaryOccurrences = countOccurrences(entity.executiveSummary, cleanQuery)
            if (summaryOccurrences > 0) {
                matchedFields.add(FIELD_SUMMARY)
                totalOccurrences += summaryOccurrences
            }

            if (matchedFields.isEmpty()) return null

            val (primarySource, sourceText) = when {
                notesOccurrences > 0 -> FIELD_NOTES to entity.notesTextCorpus
                summaryOccurrences > 0 -> FIELD_SUMMARY to entity.executiveSummary
                else -> FIELD_TITLE to entity.title
            }

            val snippet = extractSnippetAroundQuery(sourceText, cleanQuery, contextRadius = 65)
            return MinutesFtsSearchResult(
                entity = entity,
                matchedFields = matchedFields,
                primaryMatchSource = primarySource,
                matchedSnippet = snippet,
                totalOccurrences = totalOccurrences
            )
        }

        fun countOccurrences(text: String, query: String): Int {
            if (text.isBlank() || query.isBlank()) return 0
            var count = 0
            var startIndex = 0
            while (true) {
                val idx = text.indexOf(query, startIndex, ignoreCase = true)
                if (idx < 0) break
                count++
                startIndex = idx + query.length.coerceAtLeast(1)
            }
            return count
        }

        fun extractSnippetAroundQuery(
            text: String,
            query: String,
            contextRadius: Int = 65
        ): String {
            val normalizedText = text.replace("\n", " • ").trim()
            if (query.isBlank() || normalizedText.isBlank()) {
                return normalizedText.take(contextRadius * 2)
            }
            val matchIdx = normalizedText.indexOf(query, ignoreCase = true)
            if (matchIdx < 0) {
                return normalizedText.take(contextRadius * 2)
            }
            val start = (matchIdx - contextRadius).coerceAtLeast(0)
            val end = (matchIdx + query.length + contextRadius).coerceAtMost(normalizedText.length)
            val prefix = if (start > 0) "…" else ""
            val suffix = if (end < normalizedText.length) "…" else ""
            return prefix + normalizedText.substring(start, end).trim() + suffix
        }
    }
}
