package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MeetingRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("atlas_meeting_master_prefs", Context.MODE_PRIVATE)

    private val _activeSession = MutableStateFlow(MeetingSession())
    val activeSession: StateFlow<MeetingSession> = _activeSession.asStateFlow()

    private val _archiveList = MutableStateFlow<List<MeetingSession>>(emptyList())
    val archiveList: StateFlow<List<MeetingSession>> = _archiveList.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val activeJsonStr = prefs.getString(KEY_ACTIVE_SESSION, null)
        if (!activeJsonStr.isNullOrEmpty()) {
            try {
                val json = JSONObject(activeJsonStr)
                _activeSession.value = jsonToSession(json)
            } catch (e: Exception) {
                _activeSession.value = MeetingSession()
            }
        }

        val archiveJsonStr = prefs.getString(KEY_ARCHIVE_LIST, null)
        if (!archiveJsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(archiveJsonStr)
                val list = mutableListOf<MeetingSession>()
                for (i in 0 until array.length()) {
                    list.add(jsonToSession(array.getJSONObject(i)))
                }
                _archiveList.value = list
            } catch (e: Exception) {
                _archiveList.value = emptyList()
            }
        }

        _geminiApiKey.value = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
    }

    fun saveActiveSession(session: MeetingSession) {
        _activeSession.value = session
        val json = sessionToJson(session)
        prefs.edit().putString(KEY_ACTIVE_SESSION, json.toString()).apply()
    }

    fun saveGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        prefs.edit().putString(KEY_GEMINI_KEY, key).apply()
    }

    fun archiveSession(session: MeetingSession): Boolean {
        val title = if (session.title.isBlank()) "جلسه بدون عنوان" else session.title.trim()
        val toSave = session.copy(title = title)

        val currentList = _archiveList.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == toSave.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = toSave
        } else {
            currentList.add(0, toSave)
        }
        _archiveList.value = currentList

        val array = JSONArray()
        currentList.forEach { array.put(sessionToJson(it)) }
        prefs.edit().putString(KEY_ARCHIVE_LIST, array.toString()).apply()
        return true
    }

    fun deleteArchivedSession(id: String) {
        val currentList = _archiveList.value.filter { it.id != id }
        _archiveList.value = currentList
        val array = JSONArray()
        currentList.forEach { array.put(sessionToJson(it)) }
        prefs.edit().putString(KEY_ARCHIVE_LIST, array.toString()).apply()
    }

    fun loadSession(session: MeetingSession) {
        saveActiveSession(session)
    }

    fun resetToNewSession(): MeetingSession {
        val newSession = MeetingSession()
        saveActiveSession(newSession)
        return newSession
    }

    fun exportFullDatabaseJson(): String {
        val root = JSONObject()
        root.put("version", "4.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("activeSession", sessionToJson(_activeSession.value))

        val archiveArray = JSONArray()
        _archiveList.value.forEach { archiveArray.put(sessionToJson(it)) }
        root.put("archiveList", archiveArray)
        return root.toString(2)
    }

    fun importFullDatabaseJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            if (root.has("activeSession")) {
                val active = jsonToSession(root.getJSONObject("activeSession"))
                saveActiveSession(active)
            }
            if (root.has("archiveList")) {
                val array = root.getJSONArray("archiveList")
                val list = mutableListOf<MeetingSession>()
                for (i in 0 until array.length()) {
                    list.add(jsonToSession(array.getJSONObject(i)))
                }
                _archiveList.value = list
                prefs.edit().putString(KEY_ARCHIVE_LIST, array.toString()).apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val KEY_ACTIVE_SESSION = "active_session_v4"
        private const val KEY_ARCHIVE_LIST = "archive_list_v4"
        private const val KEY_GEMINI_KEY = "atlas_gemini_api_key"

        fun sessionToJson(s: MeetingSession): JSONObject {
            val json = JSONObject()
            json.put("id", s.id)
            json.put("title", s.title)
            json.put("scope", s.scope)
            json.put("extCompany", s.extCompany)
            json.put("extRef", s.extRef)
            json.put("durationMins", s.durationMins)
            json.put("location", s.location)
            json.put("calendarType", s.calendarType)
            json.put("date", s.date)
            json.put("startTime", s.startTime)
            json.put("aiSummary", s.aiSummary)
            json.put("elapsedSeconds", s.elapsedSeconds)
            json.put("audioFilePath", s.audioFilePath ?: "")
            json.put("audioDurationSec", s.audioDurationSec)
            json.put("createdAt", s.createdAt)

            val membersArray = JSONArray()
            s.members.forEach { m ->
                val mJson = JSONObject()
                mJson.put("id", m.id)
                mJson.put("name", m.name)
                mJson.put("title", m.title)
                mJson.put("unit", m.unit)
                mJson.put("isPresent", m.isPresent)
                membersArray.put(mJson)
            }
            json.put("members", membersArray)

            val attachmentsArray = JSONArray()
            s.attachments.forEach { a ->
                val aJson = JSONObject()
                aJson.put("id", a.id)
                aJson.put("name", a.name)
                aJson.put("refCode", a.refCode)
                attachmentsArray.put(aJson)
            }
            json.put("attachments", attachmentsArray)

            val notesArray = JSONArray()
            s.notes.forEach { n ->
                val nJson = JSONObject()
                nJson.put("id", n.id)
                nJson.put("timeFormatted", n.timeFormatted)
                nJson.put("speaker", n.speaker)
                nJson.put("text", n.text)
                nJson.put("type", n.type)
                nJson.put("due", n.due)
                nJson.put("isDone", n.isDone)
                notesArray.put(nJson)
            }
            json.put("notes", notesArray)

            val transArray = JSONArray()
            s.transcriptions.forEach { t ->
                val tJson = JSONObject()
                tJson.put("id", t.id)
                tJson.put("timeFormatted", t.timeFormatted)
                tJson.put("speaker", t.speaker)
                tJson.put("text", t.text)
                tJson.put("timestamp", t.timestamp)
                tJson.put("isCommittedToNotes", t.isCommittedToNotes)
                transArray.put(tJson)
            }
            json.put("transcriptions", transArray)

            s.transcriptionAnalysis?.let { analysis ->
                val aJson = JSONObject()
                aJson.put("executiveSummary", analysis.executiveSummary)
                val decisionsArr = JSONArray()
                analysis.keyDecisions.forEach { decisionsArr.put(it) }
                aJson.put("keyDecisions", decisionsArr)

                val itemsArr = JSONArray()
                analysis.actionItems.forEach { item ->
                    val iJson = JSONObject()
                    iJson.put("id", item.id)
                    iJson.put("title", item.title)
                    iJson.put("assignee", item.assignee)
                    iJson.put("deadline", item.deadline)
                    iJson.put("priority", item.priority)
                    iJson.put("isAddedToNotes", item.isAddedToNotes)
                    itemsArr.put(iJson)
                }
                aJson.put("actionItems", itemsArr)
                aJson.put("timestamp", analysis.timestamp)
                json.put("transcriptionAnalysis", aJson)
            }

            s.typedMinutesAnalysis?.let { typedAnalysis ->
                val tmJson = JSONObject()
                tmJson.put("id", typedAnalysis.id)
                tmJson.put("sourceTypedText", typedAnalysis.sourceTypedText)
                tmJson.put("formalMinutesText", typedAnalysis.formalMinutesText)
                tmJson.put("autoAnalyzed", typedAnalysis.autoAnalyzed)
                tmJson.put("timestamp", typedAnalysis.timestamp)

                val decArr = JSONArray()
                typedAnalysis.decisionsMade.forEach { decArr.put(it) }
                tmJson.put("decisionsMade", decArr)

                val tasksArr = JSONArray()
                typedAnalysis.assignedTasksByPerson.forEach { t ->
                    val tJson = JSONObject()
                    tJson.put("id", t.id)
                    tJson.put("personName", t.personName)
                    tJson.put("personRole", t.personRole)
                    tJson.put("taskTitle", t.taskTitle)
                    tJson.put("deadline", t.deadline)
                    tJson.put("priority", t.priority)
                    tJson.put("sourceExcerpt", t.sourceExcerpt)
                    tJson.put("isSyncedToRoom", t.isSyncedToRoom)
                    tasksArr.put(tJson)
                }
                tmJson.put("assignedTasksByPerson", tasksArr)
                json.put("typedMinutesAnalysis", tmJson)
            }

            s.latestAudioAnalysis?.let { audioAnalysis ->
                val audioJson = JSONObject()
                audioJson.put("fullTranscription", audioAnalysis.fullTranscription)
                audioJson.put("executiveSummary", audioAnalysis.executiveSummary)
                audioJson.put("audioDurationSec", audioAnalysis.audioDurationSec)
                audioJson.put("audioFileName", audioAnalysis.audioFileName)
                audioJson.put("timestamp", audioAnalysis.timestamp)

                val decArr = JSONArray()
                audioAnalysis.keyDecisions.forEach { decArr.put(it) }
                audioJson.put("keyDecisions", decArr)

                val itemsArr = JSONArray()
                audioAnalysis.actionItems.forEach { item ->
                    val iJson = JSONObject()
                    iJson.put("id", item.id)
                    iJson.put("title", item.title)
                    iJson.put("assignee", item.assignee)
                    iJson.put("deadline", item.deadline)
                    iJson.put("priority", item.priority)
                    iJson.put("isAddedToNotes", item.isAddedToNotes)
                    itemsArr.put(iJson)
                }
                audioJson.put("actionItems", itemsArr)
                json.put("latestAudioAnalysis", audioJson)
            }

            s.audioSentimentAnalysis?.let { sentiment ->
                val sJson = JSONObject()
                sJson.put("id", sentiment.id)
                sJson.put("overallTone", sentiment.overallTone)
                sJson.put("harmonyScore", sentiment.harmonyScore)
                sJson.put("tensionScore", sentiment.tensionScore)
                sJson.put("constructivePercentage", sentiment.constructivePercentage)
                sJson.put("neutralPercentage", sentiment.neutralPercentage)
                sJson.put("tensionPercentage", sentiment.tensionPercentage)
                sJson.put("dominantEmotion", sentiment.dominantEmotion)
                sJson.put("summaryEvaluation", sentiment.summaryEvaluation)
                sJson.put("timestamp", sentiment.timestamp)

                val segArr = JSONArray()
                sentiment.flaggedSegments.forEach { seg ->
                    val segJson = JSONObject()
                    segJson.put("id", seg.id)
                    segJson.put("timestampRange", seg.timestampRange)
                    segJson.put("speaker", seg.speaker)
                    segJson.put("tensionLevel", seg.tensionLevel)
                    segJson.put("triggerTopic", seg.triggerTopic)
                    segJson.put("quoteExcerpt", seg.quoteExcerpt)
                    segJson.put("reviewRecommendation", seg.reviewRecommendation)
                    segJson.put("isReviewed", seg.isReviewed)
                    segJson.put("reviewNotes", seg.reviewNotes)
                    segArr.put(segJson)
                }
                sJson.put("flaggedSegments", segArr)
                json.put("audioSentimentAnalysis", sJson)
            }

            val opsJson = JSONObject()
            opsJson.put("enabled", s.opsData.enabled)
            opsJson.put("fleetEff", s.opsData.fleetEff)
            opsJson.put("coldChain", s.opsData.coldChain)
            opsJson.put("palletModel", s.opsData.palletModel)
            opsJson.put("budget", s.opsData.budget)
            opsJson.put("summary", s.opsData.summary)
            json.put("opsData", opsJson)

            val hrJson = JSONObject()
            hrJson.put("enabled", s.hrData.enabled)
            hrJson.put("candidate", s.hrData.candidate)
            hrJson.put("position", s.hrData.position)
            hrJson.put("score", s.hrData.score)
            hrJson.put("decision", s.hrData.decision)
            hrJson.put("notes", s.hrData.notes)
            json.put("hrData", hrJson)

            return json
        }

        fun jsonToSession(json: JSONObject): MeetingSession {
            val members = mutableListOf<Member>()
            if (json.has("members")) {
                val array = json.getJSONArray("members")
                for (i in 0 until array.length()) {
                    val m = array.getJSONObject(i)
                    members.add(
                        Member(
                            id = m.optString("id", ""),
                            name = m.optString("name", ""),
                            title = m.optString("title", ""),
                            unit = m.optString("unit", ""),
                            isPresent = m.optBoolean("isPresent", true)
                        )
                    )
                }
            }

            val attachments = mutableListOf<Attachment>()
            if (json.has("attachments")) {
                val array = json.getJSONArray("attachments")
                for (i in 0 until array.length()) {
                    val a = array.getJSONObject(i)
                    attachments.add(
                        Attachment(
                            id = a.optString("id", ""),
                            name = a.optString("name", ""),
                            refCode = a.optString("refCode", "")
                        )
                    )
                }
            }

            val notes = mutableListOf<MeetingNote>()
            if (json.has("notes")) {
                val array = json.getJSONArray("notes")
                for (i in 0 until array.length()) {
                    val n = array.getJSONObject(i)
                    notes.add(
                        MeetingNote(
                            id = n.optString("id", ""),
                            timeFormatted = n.optString("timeFormatted", "00:00:00"),
                            speaker = n.optString("speaker", "دبیر جلسه"),
                            text = n.optString("text", ""),
                            type = n.optString("type", "🚨 مصوبه رسمی"),
                            due = n.optString("due", "فوری / جاری"),
                            isDone = n.optBoolean("isDone", false)
                        )
                    )
                }
            }

            val transcriptions = mutableListOf<TranscriptionSegment>()
            if (json.has("transcriptions")) {
                val array = json.getJSONArray("transcriptions")
                for (i in 0 until array.length()) {
                    val t = array.getJSONObject(i)
                    transcriptions.add(
                        TranscriptionSegment(
                            id = t.optString("id", ""),
                            timeFormatted = t.optString("timeFormatted", "00:00:00"),
                            speaker = t.optString("speaker", "دبیر جلسه"),
                            text = t.optString("text", ""),
                            timestamp = t.optLong("timestamp", System.currentTimeMillis()),
                            isCommittedToNotes = t.optBoolean("isCommittedToNotes", false)
                        )
                    )
                }
            }

            val transAnalysisJson = json.optJSONObject("transcriptionAnalysis")
            val transcriptionAnalysis = if (transAnalysisJson != null) {
                val decisions = mutableListOf<String>()
                val dArr = transAnalysisJson.optJSONArray("keyDecisions")
                if (dArr != null) {
                    for (i in 0 until dArr.length()) {
                        decisions.add(dArr.optString(i))
                    }
                }

                val actionItems = mutableListOf<ExtractedActionItem>()
                val aArr = transAnalysisJson.optJSONArray("actionItems")
                if (aArr != null) {
                    for (i in 0 until aArr.length()) {
                        val itemJson = aArr.getJSONObject(i)
                        actionItems.add(
                            ExtractedActionItem(
                                id = itemJson.optString("id", UUID.randomUUID().toString()),
                                title = itemJson.optString("title", ""),
                                assignee = itemJson.optString("assignee", "دبیر جلسه"),
                                deadline = itemJson.optString("deadline", "فوری / جاری"),
                                priority = itemJson.optString("priority", "بالا"),
                                isAddedToNotes = itemJson.optBoolean("isAddedToNotes", false)
                            )
                        )
                    }
                }

                TranscriptionAiAnalysis(
                    executiveSummary = transAnalysisJson.optString("executiveSummary", ""),
                    keyDecisions = decisions,
                    actionItems = actionItems,
                    timestamp = transAnalysisJson.optLong("timestamp", System.currentTimeMillis())
                )
            } else null

            val typedAnalysisJson = json.optJSONObject("typedMinutesAnalysis")
            val typedMinutesAnalysis = if (typedAnalysisJson != null) {
                val decisions = mutableListOf<String>()
                val dArr = typedAnalysisJson.optJSONArray("decisionsMade")
                if (dArr != null) {
                    for (i in 0 until dArr.length()) {
                        decisions.add(dArr.optString(i))
                    }
                }
                val personTasks = mutableListOf<PersonAssignedTask>()
                val tArr = typedAnalysisJson.optJSONArray("assignedTasksByPerson")
                if (tArr != null) {
                    for (i in 0 until tArr.length()) {
                        val itemJson = tArr.getJSONObject(i)
                        personTasks.add(
                            PersonAssignedTask(
                                id = itemJson.optString("id", UUID.randomUUID().toString()),
                                personName = itemJson.optString("personName", "سخنران جلسه"),
                                personRole = itemJson.optString("personRole", "مدیر عملیات"),
                                taskTitle = itemJson.optString("taskTitle", ""),
                                deadline = itemJson.optString("deadline", "فوری / جاری"),
                                priority = itemJson.optString("priority", "بالا"),
                                sourceExcerpt = itemJson.optString("sourceExcerpt", ""),
                                isSyncedToRoom = itemJson.optBoolean("isSyncedToRoom", true)
                            )
                        )
                    }
                }
                TypedMeetingMinutesAnalysis(
                    id = typedAnalysisJson.optString("id", UUID.randomUUID().toString()),
                    sourceTypedText = typedAnalysisJson.optString("sourceTypedText", ""),
                    formalMinutesText = typedAnalysisJson.optString("formalMinutesText", ""),
                    decisionsMade = decisions,
                    assignedTasksByPerson = personTasks,
                    autoAnalyzed = typedAnalysisJson.optBoolean("autoAnalyzed", true),
                    timestamp = typedAnalysisJson.optLong("timestamp", System.currentTimeMillis())
                )
            } else null

            val opsJson = json.optJSONObject("opsData")
            val opsData = if (opsJson != null) {
                OpsData(
                    enabled = opsJson.optBoolean("enabled", false),
                    fleetEff = opsJson.optString("fleetEff", ""),
                    coldChain = opsJson.optString("coldChain", "پایدار و منطبق با استاندارد (2°C تا 8°C)"),
                    palletModel = opsJson.optString("palletModel", ""),
                    budget = opsJson.optString("budget", ""),
                    summary = opsJson.optString("summary", "")
                )
            } else OpsData()

            val hrJson = json.optJSONObject("hrData")
            val hrData = if (hrJson != null) {
                HrData(
                    enabled = hrJson.optBoolean("enabled", false),
                    candidate = hrJson.optString("candidate", ""),
                    position = hrJson.optString("position", ""),
                    score = hrJson.optString("score", ""),
                    decision = hrJson.optString("decision", "تایید قطعی جهت استخدام (Strong Hire)"),
                    notes = hrJson.optString("notes", "")
                )
            } else HrData()

            val latestAudioAnalysis = if (json.has("latestAudioAnalysis")) {
                val aJson = json.getJSONObject("latestAudioAnalysis")
                val decisions = mutableListOf<String>()
                if (aJson.has("keyDecisions")) {
                    val arr = aJson.getJSONArray("keyDecisions")
                    for (i in 0 until arr.length()) decisions.add(arr.getString(i))
                }
                val actionItems = mutableListOf<ExtractedActionItem>()
                if (aJson.has("actionItems")) {
                    val arr = aJson.getJSONArray("actionItems")
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        actionItems.add(
                            ExtractedActionItem(
                                id = item.optString("id", UUID.randomUUID().toString()),
                                title = item.optString("title", ""),
                                assignee = item.optString("assignee", "دبیر جلسه"),
                                deadline = item.optString("deadline", "فوری / جاری"),
                                priority = item.optString("priority", "بالا"),
                                isAddedToNotes = item.optBoolean("isAddedToNotes", false)
                            )
                        )
                    }
                }
                AudioMeetingAnalysis(
                    fullTranscription = aJson.optString("fullTranscription", ""),
                    executiveSummary = aJson.optString("executiveSummary", ""),
                    keyDecisions = decisions,
                    actionItems = actionItems,
                    audioDurationSec = aJson.optLong("audioDurationSec", 0L),
                    audioFileName = aJson.optString("audioFileName", ""),
                    timestamp = aJson.optLong("timestamp", System.currentTimeMillis())
                )
            } else null

            val audioSentimentAnalysis = if (json.has("audioSentimentAnalysis")) {
                val sJson = json.getJSONObject("audioSentimentAnalysis")
                val segments = mutableListOf<HighTensionSegment>()
                if (sJson.has("flaggedSegments")) {
                    val sArr = sJson.getJSONArray("flaggedSegments")
                    for (i in 0 until sArr.length()) {
                        val item = sArr.getJSONObject(i)
                        segments.add(
                            HighTensionSegment(
                                id = item.optString("id", UUID.randomUUID().toString()),
                                timestampRange = item.optString("timestampRange", "00:00 - 01:00"),
                                speaker = item.optString("speaker", "دبیر جلسه"),
                                tensionLevel = item.optString("tensionLevel", "شدید"),
                                triggerTopic = item.optString("triggerTopic", "موضوع چالش‌برانگیز"),
                                quoteExcerpt = item.optString("quoteExcerpt", ""),
                                reviewRecommendation = item.optString("reviewRecommendation", ""),
                                isReviewed = item.optBoolean("isReviewed", false),
                                reviewNotes = item.optString("reviewNotes", "")
                            )
                        )
                    }
                }
                AudioSentimentAnalysis(
                    id = sJson.optString("id", UUID.randomUUID().toString()),
                    overallTone = sJson.optString("overallTone", "حرفه‌ای و سازنده"),
                    harmonyScore = sJson.optInt("harmonyScore", 82),
                    tensionScore = sJson.optInt("tensionScore", 22),
                    constructivePercentage = sJson.optInt("constructivePercentage", 70),
                    neutralPercentage = sJson.optInt("neutralPercentage", 20),
                    tensionPercentage = sJson.optInt("tensionPercentage", 10),
                    dominantEmotion = sJson.optString("dominantEmotion", "جدیت در مذاکره و تصمیم‌گیری"),
                    summaryEvaluation = sJson.optString("summaryEvaluation", ""),
                    flaggedSegments = segments,
                    timestamp = sJson.optLong("timestamp", System.currentTimeMillis())
                )
            } else null

            return MeetingSession(
                id = json.optString("id", "SES-" + System.currentTimeMillis()),
                title = json.optString("title", ""),
                scope = json.optString("scope", "internal"),
                extCompany = json.optString("extCompany", ""),
                extRef = json.optString("extRef", ""),
                durationMins = json.optInt("durationMins", 60),
                location = json.optString("location", ""),
                calendarType = json.optString("calendarType", "jalali"),
                date = json.optString("date", ""),
                startTime = json.optString("startTime", ""),
                members = members,
                attachments = attachments,
                notes = notes,
                transcriptions = transcriptions,
                transcriptionAnalysis = transcriptionAnalysis,
                typedMinutesAnalysis = typedMinutesAnalysis,
                latestAudioAnalysis = latestAudioAnalysis,
                audioSentimentAnalysis = audioSentimentAnalysis,
                aiSummary = json.optString("aiSummary", ""),
                opsData = opsData,
                hrData = hrData,
                elapsedSeconds = json.optLong("elapsedSeconds", 0),
                audioFilePath = json.optString("audioFilePath", "").ifEmpty { null },
                audioDurationSec = json.optLong("audioDurationSec", 0),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
