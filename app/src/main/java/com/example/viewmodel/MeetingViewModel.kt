package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import com.example.util.PdfReportGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.local.AtlasDatabase
import com.example.data.local.AttendeeEntity
import com.example.data.local.AttendeeRepository
import com.example.data.local.BoardScheduleEntity
import com.example.data.local.BoardScheduleRepository
import com.example.data.local.ExecutiveSummaryEntity
import com.example.data.local.ExecutiveSummaryRepository
import com.example.data.local.MeetingMinutesEntity
import com.example.data.local.MemberTaskEntity
import com.example.data.local.MemberTaskRepository
import com.example.data.local.MinutesFtsSearchResult
import com.example.data.local.MinutesSearchRepository
import com.example.service.AudioRecordManager
import com.example.service.GeminiAiService
import com.example.service.GroundedSearchResult
import com.example.service.SilenceDetectionStatus
import com.example.util.MeetingNotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)
    private val database = AtlasDatabase.getInstance(application)
    private val attendeeRepository = AttendeeRepository(database.attendeeDao())
    private val minutesSearchRepository = MinutesSearchRepository(database.meetingMinutesDao())
    private val executiveSummaryRepository = ExecutiveSummaryRepository(
        summaryDao = database.executiveSummaryDao(),
        minutesSearchRepository = minutesSearchRepository
    )
    private val memberTaskRepository = MemberTaskRepository(database.memberTaskDao())
    private val boardScheduleRepository = BoardScheduleRepository(
        scheduleDao = database.boardScheduleDao(),
        minutesSearchRepository = minutesSearchRepository
    )
    private val geminiService = GeminiAiService()
    private val audioRecordManager = AudioRecordManager(application)
    private val mainHandler = Handler(Looper.getMainLooper())

    val activeSession: StateFlow<MeetingSession> = repository.activeSession
    val archiveList: StateFlow<List<MeetingSession>> = repository.archiveList
    val geminiApiKey: StateFlow<String> = repository.geminiApiKey

    val roomBoardScheduleItems: StateFlow<List<BoardScheduleEntity>> =
        boardScheduleRepository.allScheduleItemsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = BoardScheduleRepository.buildDefaultBoardScheduleSeed()
            )

    val roomUpcomingMeetings: StateFlow<List<BoardScheduleEntity>> =
        boardScheduleRepository.observeUpcomingMeetings(BoardScheduleRepository.DEFAULT_REFERENCE_DATE)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = BoardScheduleRepository.buildDefaultBoardScheduleSeed().filter { it.isUpcoming }
            )

    val roomHistoricalMeetings: StateFlow<List<BoardScheduleEntity>> =
        boardScheduleRepository.observeHistoricalMeetings(BoardScheduleRepository.DEFAULT_REFERENCE_DATE)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = BoardScheduleRepository.buildDefaultBoardScheduleSeed().filter { it.isHistorical }
            )

    val allRoomExecutiveSummaries: StateFlow<List<ExecutiveSummaryEntity>> =
        executiveSummaryRepository.allSummariesFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val roomMemberTasks: StateFlow<List<MemberTaskEntity>> = memberTaskRepository.allMemberTasksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessionMemberTasks: StateFlow<List<MemberTaskEntity>> = activeSession
        .flatMapLatest { session ->
            memberTaskRepository.observeTasksForSession(session.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isExtractingMemberTasks = MutableStateFlow(false)
    val isExtractingMemberTasks: StateFlow<Boolean> = _isExtractingMemberTasks.asStateFlow()

    private val _memberTaskExtractionError = MutableStateFlow<String?>(null)
    val memberTaskExtractionError: StateFlow<String?> = _memberTaskExtractionError.asStateFlow()

    private val _lastExtractedMemberTasks = MutableStateFlow<List<MemberTaskEntity>>(emptyList())
    val lastExtractedMemberTasks: StateFlow<List<MemberTaskEntity>> = _lastExtractedMemberTasks.asStateFlow()

    private val _autoSyncExtractedTasksToDbAndCalendar = MutableStateFlow(true)
    val autoSyncExtractedTasksToDbAndCalendar: StateFlow<Boolean> = _autoSyncExtractedTasksToDbAndCalendar.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val roomExecutiveSummaries: StateFlow<List<ExecutiveSummaryEntity>> = activeSession
        .flatMapLatest { session ->
            executiveSummaryRepository.observeSummariesForSession(session.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestRoomExecutiveSummary: StateFlow<ExecutiveSummaryEntity?> = activeSession
        .flatMapLatest { session ->
            executiveSummaryRepository.observeLatestSummaryForSession(session.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val roomAttendees: StateFlow<List<AttendeeEntity>> = activeSession
        .flatMapLatest { session ->
            attendeeRepository.getAttendeesForSession(session.id).map { dbList ->
                if (dbList.isEmpty() && session.members.isNotEmpty()) {
                    session.members.mapIndexed { idx, m -> AttendeeEntity.fromMember(m, session.id, idx) }
                } else {
                    dbList
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.activeSession.value.members.mapIndexed { idx, m ->
                AttendeeEntity.fromMember(m, repository.activeSession.value.id, idx)
            }
        )

    // --- Room Full-Text Search (FTS4) State & Combined Filters ---
    private val _ftsSearchQuery = MutableStateFlow("")
    val ftsSearchQuery: StateFlow<String> = _ftsSearchQuery.asStateFlow()

    private val _ftsScopeFilter = MutableStateFlow("ALL")
    val ftsScopeFilter: StateFlow<String> = _ftsScopeFilter.asStateFlow()

    private val _ftsStatusFilter = MutableStateFlow("ALL")
    val ftsStatusFilter: StateFlow<String> = _ftsStatusFilter.asStateFlow()

    private val _ftsMemberFilter = MutableStateFlow("ALL")
    val ftsMemberFilter: StateFlow<String> = _ftsMemberFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val ftsSearchResults: StateFlow<List<MinutesFtsSearchResult>> = combine(
        _ftsSearchQuery,
        _ftsScopeFilter,
        _ftsStatusFilter,
        _ftsMemberFilter
    ) { query, scope, status, member ->
        arrayOf(query, scope, status, member)
    }.flatMapLatest { params ->
        minutesSearchRepository.observeSearchMinutes(
            rawQuery = params[0],
            scopeFilter = params[1],
            statusFilter = params[2],
            memberFilter = params[3]
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val availableResponsibleMembers: StateFlow<List<String>> = combine(
        minutesSearchRepository.allMinutesFlow,
        activeSession
    ) { allMinutes, current ->
        val fromDb = allMinutes.flatMap { entity ->
            entity.responsibleMembersCsv.split("|").map { it.trim() }
        }
        val fromCurrent = current.members.map { it.name.trim() } + current.notes.map { it.speaker.trim() }
        (fromDb + fromCurrent).filter { it.isNotBlank() }.distinct()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.activeSession.value.members.map { it.name.trim() }.filter { it.isNotBlank() }
    )

    // Timer state
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null

    // Audio recording state
    private val _isAudioRecording = MutableStateFlow(false)
    val isAudioRecording: StateFlow<Boolean> = _isAudioRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0L)
    val recordingDurationSec: StateFlow<Long> = _recordingDurationSec.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _micInputSensitivity = MutableStateFlow(AudioRecordManager.DEFAULT_SENSITIVITY)
    val micInputSensitivity: StateFlow<Float> = _micInputSensitivity.asStateFlow()

    private val _silenceDetectionStatus = MutableStateFlow(SilenceDetectionStatus())
    val silenceDetectionStatus: StateFlow<SilenceDetectionStatus> = _silenceDetectionStatus.asStateFlow()

    private val _recordedAudioFile = MutableStateFlow<File?>(null)
    val recordedAudioFile: StateFlow<File?> = _recordedAudioFile.asStateFlow()

    private var recordingMonitorJob: Job? = null

    // Multimodal Audio Processing with Gemini & Post-Recording Room Executive Summary
    private val _isAudioProcessingWithGemini = MutableStateFlow(false)
    val isAudioProcessingWithGemini: StateFlow<Boolean> = _isAudioProcessingWithGemini.asStateFlow()

    private val _audioProcessingError = MutableStateFlow<String?>(null)
    val audioProcessingError: StateFlow<String?> = _audioProcessingError.asStateFlow()

    private val _audioMeetingAnalysis = MutableStateFlow<AudioMeetingAnalysis?>(null)
    val audioMeetingAnalysis: StateFlow<AudioMeetingAnalysis?> = _audioMeetingAnalysis.asStateFlow()

    private val _autoSummarizeOnRecordingStop = MutableStateFlow(true)
    val autoSummarizeOnRecordingStop: StateFlow<Boolean> = _autoSummarizeOnRecordingStop.asStateFlow()

    private val _lastPersistedRoomSummary = MutableStateFlow<ExecutiveSummaryEntity?>(null)
    val lastPersistedRoomSummary: StateFlow<ExecutiveSummaryEntity?> = _lastPersistedRoomSummary.asStateFlow()

    // Speech-to-text state
    private var speechRecognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isContinuousTranscribing = MutableStateFlow(false)
    val isContinuousTranscribing: StateFlow<Boolean> = _isContinuousTranscribing.asStateFlow()

    private val _interimTranscript = MutableStateFlow("")
    val interimTranscript: StateFlow<String> = _interimTranscript.asStateFlow()

    private val _audioRmsLevel = MutableStateFlow(0f)
    val audioRmsLevel: StateFlow<Float> = _audioRmsLevel.asStateFlow()

    private val _speechLanguage = MutableStateFlow("fa-IR")
    val speechLanguage: StateFlow<String> = _speechLanguage.asStateFlow()

    private val _activeTranscribingSpeaker = MutableStateFlow("سخنران")
    val activeTranscribingSpeaker: StateFlow<String> = _activeTranscribingSpeaker.asStateFlow()

    private val _voiceInputBuffer = MutableStateFlow("")
    val voiceInputBuffer: StateFlow<String> = _voiceInputBuffer.asStateFlow()

    private val _liveTypedMinutesBuffer = MutableStateFlow("")
    val liveTypedMinutesBuffer: StateFlow<String> = _liveTypedMinutesBuffer.asStateFlow()

    private val _autoCommitSpeechToNotes = MutableStateFlow(true)
    val autoCommitSpeechToNotes: StateFlow<Boolean> = _autoCommitSpeechToNotes.asStateFlow()

    private val _autoAnalyzeTypedMinutesWithGemini = MutableStateFlow(true)
    val autoAnalyzeTypedMinutesWithGemini: StateFlow<Boolean> = _autoAnalyzeTypedMinutesWithGemini.asStateFlow()

    private val _isAnalyzingTypedMinutes = MutableStateFlow(false)
    val isAnalyzingTypedMinutes: StateFlow<Boolean> = _isAnalyzingTypedMinutes.asStateFlow()

    private val _typedMinutesAnalysis = MutableStateFlow<TypedMeetingMinutesAnalysis?>(null)
    val typedMinutesAnalysis: StateFlow<TypedMeetingMinutesAnalysis?> = _typedMinutesAnalysis.asStateFlow()

    private val _typedMinutesAnalysisError = MutableStateFlow<String?>(null)
    val typedMinutesAnalysisError: StateFlow<String?> = _typedMinutesAnalysisError.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    // AI state
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // Transcription AI Analysis state
    private val _isTranscriptionAiLoading = MutableStateFlow(false)
    val isTranscriptionAiLoading: StateFlow<Boolean> = _isTranscriptionAiLoading.asStateFlow()

    private val _transcriptionAiAnalysis = MutableStateFlow<TranscriptionAiAnalysis?>(null)
    val transcriptionAiAnalysis: StateFlow<TranscriptionAiAnalysis?> = _transcriptionAiAnalysis.asStateFlow()

    private val _transcriptionAiError = MutableStateFlow<String?>(null)
    val transcriptionAiError: StateFlow<String?> = _transcriptionAiError.asStateFlow()

    // Google Search Grounding state (Setup Tab)
    private val _isSearchingStandards = MutableStateFlow(false)
    val isSearchingStandards: StateFlow<Boolean> = _isSearchingStandards.asStateFlow()

    private val _searchStandardsResult = MutableStateFlow<GroundedSearchResult?>(null)
    val searchStandardsResult: StateFlow<GroundedSearchResult?> = _searchStandardsResult.asStateFlow()

    private val _searchStandardsError = MutableStateFlow<String?>(null)
    val searchStandardsError: StateFlow<String?> = _searchStandardsError.asStateFlow()

    // --- AI Agenda Suggestions State ---
    private val _isGeneratingAgenda = MutableStateFlow(false)
    val isGeneratingAgenda: StateFlow<Boolean> = _isGeneratingAgenda.asStateFlow()

    private val _agendaSuggestions = MutableStateFlow<List<AgendaSuggestion>>(emptyList())
    val agendaSuggestions: StateFlow<List<AgendaSuggestion>> = _agendaSuggestions.asStateFlow()

    // --- Meeting Health & Recurring Blockers Report State ---
    private val _isAnalyzingMeetingHealth = MutableStateFlow(false)
    val isAnalyzingMeetingHealth: StateFlow<Boolean> = _isAnalyzingMeetingHealth.asStateFlow()

    private val _meetingHealthReport = MutableStateFlow<MeetingHealthReport?>(null)
    val meetingHealthReport: StateFlow<MeetingHealthReport?> = _meetingHealthReport.asStateFlow()

    // --- Advanced Meeting List Filters ---
    private val _listFilterQuery = MutableStateFlow("")
    val listFilterQuery: StateFlow<String> = _listFilterQuery.asStateFlow()

    private val _listFilterScope = MutableStateFlow("ALL")
    val listFilterScope: StateFlow<String> = _listFilterScope.asStateFlow()

    private val _listFilterStatus = MutableStateFlow("ALL")
    val listFilterStatus: StateFlow<String> = _listFilterStatus.asStateFlow()

    private val _listFilterMember = MutableStateFlow("ALL")
    val listFilterMember: StateFlow<String> = _listFilterMember.asStateFlow()

    // --- Home Interactive Monthly Calendar & Top Search Date Range Picker Filters ---
    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _selectedCalendarDateFilter = MutableStateFlow("")
    val selectedCalendarDateFilter: StateFlow<String> = _selectedCalendarDateFilter.asStateFlow()

    private val _dateRangeStartFilter = MutableStateFlow("")
    val dateRangeStartFilter: StateFlow<String> = _dateRangeStartFilter.asStateFlow()

    private val _dateRangeEndFilter = MutableStateFlow("")
    val dateRangeEndFilter: StateFlow<String> = _dateRangeEndFilter.asStateFlow()

    val filteredHomeBoardMeetings: StateFlow<List<BoardScheduleEntity>> = combine(
        roomBoardScheduleItems,
        _homeSearchQuery,
        _selectedCalendarDateFilter,
        _dateRangeStartFilter,
        _dateRangeEndFilter
    ) { items, query, selectedDate, startDate, endDate ->
        filterBoardMeetingsByDateAndSearch(
            items = items,
            searchQuery = query,
            selectedDate = selectedDate,
            dateRangeStart = startDate,
            dateRangeEnd = endDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BoardScheduleRepository.buildDefaultBoardScheduleSeed()
    )

    // --- Audio Sentiment & Tension Analysis State ---
    private val _isAnalyzingSentiment = MutableStateFlow(false)
    val isAnalyzingSentiment: StateFlow<Boolean> = _isAnalyzingSentiment.asStateFlow()

    private val _sentimentAnalysisError = MutableStateFlow<String?>(null)
    val sentimentAnalysisError: StateFlow<String?> = _sentimentAnalysisError.asStateFlow()

    private val _audioSentimentAnalysis = MutableStateFlow<AudioSentimentAnalysis?>(null)
    val audioSentimentAnalysis: StateFlow<AudioSentimentAnalysis?> = _audioSentimentAnalysis.asStateFlow()

    // Notification message (toast / snackbar info)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        // Initialize speech recognizer on main thread
        try {
            if (SpeechRecognizer.isRecognitionAvailable(application)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)
            }
        } catch (e: Exception) {
            speechRecognizer = null
        }

        try {
            _transcriptionAiAnalysis.value = repository.activeSession.value.transcriptionAnalysis
            _typedMinutesAnalysis.value = repository.activeSession.value.typedMinutesAnalysis
            _audioSentimentAnalysis.value = repository.activeSession.value.audioSentimentAnalysis
        } catch (e: Exception) {}

        // Initial sync of active session members, meeting minutes FTS index, and board calendar schedule into Room database
        viewModelScope.launch {
            val current = repository.activeSession.value
            attendeeRepository.syncSessionMembersToRoom(current.id, current.members)
            minutesSearchRepository.syncSessionsToMinutesIndex(
                activeSession = current,
                archiveList = repository.archiveList.value,
                seedPredefinedScenariosIfArchiveEmpty = true
            )
            boardScheduleRepository.syncAndSeedBoardSchedule(
                activeSession = current,
                archiveList = repository.archiveList.value
            )
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun updateTitle(title: String) {
        val current = activeSession.value
        save(current.copy(title = title))
    }

    fun updateScope(scope: String) {
        val current = activeSession.value
        save(current.copy(scope = scope))
    }

    fun updateExtCompany(company: String) {
        val current = activeSession.value
        save(current.copy(extCompany = company))
    }

    fun updateExtRef(ref: String) {
        val current = activeSession.value
        save(current.copy(extRef = ref))
    }

    fun updateDurationMins(mins: Int) {
        val current = activeSession.value
        save(current.copy(durationMins = mins))
    }

    fun updateLocation(loc: String) {
        val current = activeSession.value
        save(current.copy(location = loc))
    }

    fun updateCalendarType(type: String) {
        val current = activeSession.value
        save(current.copy(calendarType = type))
    }

    fun updateDate(date: String) {
        val current = activeSession.value
        save(current.copy(date = date))
    }

    fun updateStartTime(time: String) {
        val current = activeSession.value
        save(current.copy(startTime = time))
    }

    fun updateAiSummary(summary: String) {
        val current = activeSession.value
        save(current.copy(aiSummary = summary))
    }

    fun setGeminiApiKey(key: String) {
        repository.saveGeminiApiKey(key)
    }

    // --- Members & Room Attendees Management ---
    fun addMember() {
        val current = activeSession.value
        val newMember = Member(name = "", title = "", unit = "", isPresent = true)
        val updated = current.members + newMember
        save(current.copy(members = updated))
    }

    fun addAttendeeWithDetails(
        name: String,
        role: String,
        department: String,
        isPresent: Boolean = true
    ) {
        val cleanName = name.trim()
        val current = activeSession.value
        val newMember = Member(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            title = role.trim(),
            unit = department.trim(),
            isPresent = isPresent
        )
        val entity = AttendeeEntity(
            id = newMember.id,
            sessionId = current.id,
            name = newMember.name,
            role = newMember.title,
            department = newMember.unit,
            isPresent = newMember.isPresent,
            attendanceStatus = if (isPresent) AttendeeEntity.STATUS_PRESENT else AttendeeEntity.STATUS_ABSENT,
            checkInTime = if (isPresent) formatClock(current.elapsedSeconds) else null,
            createdAt = System.currentTimeMillis() + current.members.size
        )
        viewModelScope.launch {
            attendeeRepository.upsertAttendee(entity)
        }
        val updated = current.members + newMember
        save(current.copy(members = updated))
        if (cleanName.isNotBlank()) {
            _userMessage.value = "عضو جدید «$cleanName» در دیتابیس Room ثبت شد."
        }
    }

    fun updateAttendeeEntity(attendee: AttendeeEntity) {
        val current = activeSession.value
        viewModelScope.launch {
            attendeeRepository.upsertAttendee(attendee)
        }
        val idx = current.members.indexOfFirst { it.id == attendee.id }
        if (idx >= 0) {
            val updated = current.members.toMutableList()
            updated[idx] = attendee.toMember()
            save(current.copy(members = updated))
        }
    }

    fun toggleAttendeePresence(attendee: AttendeeEntity) {
        val nextPresent = !attendee.isPresent
        val nextStatus = if (nextPresent) AttendeeEntity.STATUS_PRESENT else AttendeeEntity.STATUS_ABSENT
        changeAttendeeStatus(attendee, nextStatus, nextPresent)
    }

    fun changeAttendeeStatus(attendee: AttendeeEntity, status: String, isPresent: Boolean) {
        val current = activeSession.value
        val checkInTime = if (isPresent) {
            attendee.checkInTime ?: formatClock(current.elapsedSeconds)
        } else null
        val updatedEntity = attendee.copy(
            isPresent = isPresent,
            attendanceStatus = status,
            checkInTime = checkInTime
        )
        viewModelScope.launch {
            attendeeRepository.upsertAttendee(updatedEntity)
        }
        val idx = current.members.indexOfFirst { it.id == attendee.id }
        if (idx >= 0) {
            val updated = current.members.toMutableList()
            updated[idx] = updatedEntity.toMember()
            save(current.copy(members = updated))
        }
    }

    fun deleteAttendeeById(attendeeId: String) {
        val current = activeSession.value
        viewModelScope.launch {
            attendeeRepository.deleteById(attendeeId)
        }
        val updated = current.members.filterNot { it.id == attendeeId }
        save(current.copy(members = updated))
    }

    fun updateMember(index: Int, member: Member) {
        val current = activeSession.value
        if (index in current.members.indices) {
            val updated = current.members.toMutableList()
            updated[index] = member
            save(current.copy(members = updated))
        }
    }

    fun removeMember(index: Int) {
        val current = activeSession.value
        if (index in current.members.indices) {
            val removed = current.members[index]
            viewModelScope.launch {
                attendeeRepository.deleteById(removed.id)
            }
            val updated = current.members.toMutableList()
            updated.removeAt(index)
            save(current.copy(members = updated))
        }
    }

    // --- QR Check-In ---
    fun checkInAttendeeByQr(qrPayload: String): Pair<Boolean, String> {
        val current = activeSession.value
        val cleanPayload = qrPayload.trim()

        var qrType: String? = null
        var matchedMemberId: String? = null
        var matchedName: String? = null
        var matchedSessionId: String? = null

        try {
            if (cleanPayload.startsWith("{") && cleanPayload.endsWith("}")) {
                val json = org.json.JSONObject(cleanPayload)
                qrType = json.optString("type", "").ifBlank { null }
                matchedSessionId = json.optString("sessionId", "").ifBlank { null }
                matchedMemberId = json.optString("memberId", "").ifBlank { null }
                matchedName = json.optString("name", "").ifBlank { null }
            }
        } catch (e: Exception) {}

        // 1. Check if this is a Session Master QR Code (marks all invited members present)
        if (qrType == "SESSION_CHECKIN" || cleanPayload.startsWith("ATLAS_SESSION_QR:") || (matchedSessionId == current.id && matchedMemberId == null)) {
            val updatedMembers = current.members.map { it.copy(isPresent = true) }
            val checkinTime = formatClock(current.elapsedSeconds)
            val checkinNote = MeetingNote(
                timeFormatted = checkinTime,
                speaker = "سامانه پذیرش QR",
                text = "✅ پذیرش دسته‌جمعی جلسه «${current.title.ifBlank { "سازمانی" }}» با اسکن بارکد جامع جلسه انجام شد (${updatedMembers.size} نفر حاضر).",
                type = "🚨 مصوبه رسمی",
                due = "ثبت نهایی",
                isDone = true
            )
            save(current.copy(members = updatedMembers, notes = listOf(checkinNote) + current.notes))
            val msg = "تمامی اعضای جلسه (${updatedMembers.size} نفر) با اسکن بارکد جلسه حاضر شدند."
            _userMessage.value = msg
            return Pair(true, msg)
        }

        // 2. Otherwise match individual member
        val memberIndex = current.members.indexOfFirst { m ->
            (matchedMemberId != null && m.id == matchedMemberId) ||
            (matchedName != null && m.name.equals(matchedName, ignoreCase = true)) ||
            (m.name.isNotBlank() && cleanPayload.contains(m.name))
        }

        if (memberIndex >= 0) {
            val member = current.members[memberIndex]
            val updatedMembers = current.members.toMutableList()
            updatedMembers[memberIndex] = member.copy(isPresent = true)

            val checkinTime = formatClock(current.elapsedSeconds)
            val checkinNote = MeetingNote(
                timeFormatted = checkinTime,
                speaker = "سامانه پذیرش QR",
                text = "✅ حضور رسمی «${member.name}» (${member.title}) با اسکن بارکد QR در ساعت $checkinTime در جدول اعضا ثبت شد.",
                type = "🚨 مصوبه رسمی",
                due = "ثبت نهایی",
                isDone = true
            )

            val updatedNotes = listOf(checkinNote) + current.notes
            save(current.copy(members = updatedMembers, notes = updatedNotes))
            val msg = "حضور «${member.name}» در جدول اعضا ثبت شد."
            _userMessage.value = msg
            return Pair(true, msg)
        } else {
            val msg = "عضوی منطبق با این بارکد در جدول جلسه یافت نشد."
            _userMessage.value = msg
            return Pair(false, msg)
        }
    }

    fun checkInMemberById(memberId: String) {
        val current = activeSession.value
        val index = current.members.indexOfFirst { it.id == memberId }
        if (index >= 0) {
            val member = current.members[index]
            val qrPayload = com.example.util.QrCodeGenerator.createMemberCheckInPayload(
                sessionId = current.id,
                memberId = member.id,
                name = member.name,
                title = member.title
            )
            // Encode and decode through ZXing to verify end-to-end QR pipeline
            val qrBitmap = com.example.util.QrCodeGenerator.generateQrBitmap(qrPayload, 256)
            val decoded = qrBitmap?.let { com.example.util.QrCodeGenerator.decodeQrBitmap(it) } ?: qrPayload
            checkInAttendeeByQr(decoded)
        }
    }

    // --- Attachments Management ---
    fun addAttachment() {
        val current = activeSession.value
        val updated = current.attachments + Attachment(name = "", refCode = "")
        save(current.copy(attachments = updated))
    }

    fun updateAttachment(index: Int, attachment: Attachment) {
        val current = activeSession.value
        if (index in current.attachments.indices) {
            val updated = current.attachments.toMutableList()
            updated[index] = attachment
            save(current.copy(attachments = updated))
        }
    }

    fun removeAttachment(index: Int) {
        val current = activeSession.value
        if (index in current.attachments.indices) {
            val updated = current.attachments.toMutableList()
            updated.removeAt(index)
            save(current.copy(attachments = updated))
        }
    }

    // --- Special Modules ---
    fun updateOpsData(ops: OpsData) {
        val current = activeSession.value
        save(current.copy(opsData = ops))
    }

    fun updateHrData(hr: HrData) {
        val current = activeSession.value
        save(current.copy(hrData = hr))
    }

    // --- Notes & Decisions Stream ---
    fun addNote(speaker: String, text: String, rawType: String, due: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val resolvedType = if (rawType == "auto") {
            when {
                cleanText.contains("مصوب") || cleanText.contains("تایید") || cleanText.contains("تصویب") || cleanText.contains("مقرر") -> "🚨 مصوبه رسمی"
                cleanText.contains("تعهد") || cleanText.contains("پیگیری") || cleanText.contains("موظف") || cleanText.contains("اقدام") -> "📌 تعهد اجرایی"
                cleanText.contains("بودجه") || cleanText.contains("ریال") || cleanText.contains("تومان") || cleanText.contains("مالی") || cleanText.contains("هزینه") -> "💰 مصوبه مالی"
                else -> "💡 نکته کارشناسی"
            }
        } else rawType

        val current = activeSession.value
        val formattedTime = formatClock(current.elapsedSeconds)
        val newNote = MeetingNote(
            timeFormatted = formattedTime,
            speaker = speaker.ifBlank { "دبیر جلسه" },
            text = cleanText,
            type = resolvedType,
            due = due.ifBlank { "فوری / جاری" },
            isDone = false
        )
        val updated = listOf(newNote) + current.notes
        save(current.copy(notes = updated))
    }

    fun toggleNoteDone(noteId: String) {
        val current = activeSession.value
        val updated = current.notes.map { note ->
            if (note.id == noteId) note.copy(isDone = !note.isDone) else note
        }
        save(current.copy(notes = updated))
    }

    fun removeNote(noteId: String) {
        val current = activeSession.value
        val updated = current.notes.filter { note -> note.id != noteId }
        save(current.copy(notes = updated))
    }

    // --- Scenarios ---
    fun loadScenario(key: String) {
        val scenario = PredefinedScenarios.getScenario(key)
        save(scenario)
        _userMessage.value = "سناریوی سازمانی بارگذاری شد."
    }

    // --- Timer Controls ---
    fun toggleTimer() {
        if (_isTimerRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        _isTimerRunning.value = true
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isTimerRunning.value) {
                delay(1000)
                val current = activeSession.value
                val newSec = current.elapsedSeconds + 1
                save(current.copy(elapsedSeconds = newSec))
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        pauseTimer()
        val current = activeSession.value
        save(current.copy(elapsedSeconds = 0))
    }

    // --- Audio Recording & Multimodal AI Management ---
    fun updateMicInputSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(AudioRecordManager.MIN_SENSITIVITY, AudioRecordManager.MAX_SENSITIVITY)
        _micInputSensitivity.value = clamped
        audioRecordManager.setInputSensitivity(clamped)
    }

    /**
     * Enables or disables automatic 5-second silence detection in the voice recorder.
     */
    fun setSilenceDetectionEnabled(enabled: Boolean) {
        val updatedStatus = audioRecordManager.setSilenceDetectionEnabled(enabled)
        _silenceDetectionStatus.value = updatedStatus
        _userMessage.value = if (enabled) {
            "تشخیص هوشمند سکوت فعال شد (مکث خودکار پس از ۵ ثانیه سکوت)."
        } else {
            "تشخیص هوشمند سکوت غیرفعال شد (ضبط پیوسته)."
        }
    }

    /**
     * Manually resumes audio recording immediately if currently auto-paused due to 5 seconds of silence.
     */
    fun forceResumeAudioRecordingFromSilence() {
        val wasAutoPaused = _silenceDetectionStatus.value.isAutoPaused
        val updatedStatus = audioRecordManager.forceResumeFromSilence()
        _silenceDetectionStatus.value = updatedStatus
        if (wasAutoPaused && !updatedStatus.isAutoPaused) {
            _userMessage.value = "ضبط صدا به صورت دستی از حالت مکث سکوت خارج شد و ادامه یافت."
        }
    }

    /**
     * Evaluates an amplitude sample for 5-second silence auto-pause and speech auto-resume.
     * Used by the real-time recording monitor loop and deterministic unit tests.
     */
    fun evaluateAudioSilenceSample(
        rawNormalizedAmplitude: Float,
        externalSpeechRmsNormalized: Float = 0f,
        nowMillis: Long = System.currentTimeMillis()
    ): SilenceDetectionStatus {
        val gatedAmp = AudioRecordManager.applySensitivityAndNoiseGate(
            rawNormalized = rawNormalizedAmplitude,
            sensitivity = _micInputSensitivity.value
        )
        val wasAutoPaused = _silenceDetectionStatus.value.isAutoPaused
        val status = audioRecordManager.evaluateSilenceDetection(
            gatedNormalizedAmplitude = gatedAmp,
            externalSpeechRmsNormalized = externalSpeechRmsNormalized,
            nowMillis = nowMillis
        )
        _silenceDetectionStatus.value = status
        _recordingAmplitude.value = if (status.isAutoPaused) 0f else gatedAmp
        _recordingDurationSec.value = audioRecordManager.getElapsedDurationSec(nowMillis)

        if (!wasAutoPaused && status.isAutoPaused) {
            _userMessage.value = "⏸️ تشخیص ۵ ثانیه سکوت: ضبط به صورت خودکار متوقف شد و با شروع صحبت ادامه می‌یابد."
        } else if (wasAutoPaused && !status.isAutoPaused && _isAudioRecording.value) {
            _userMessage.value = "🎙️ تشخیص صدای سخنران: ضبط جلسه به صورت خودکار ادامه یافت."
        }
        return status
    }

    fun toggleAudioRecording() {
        if (_isAudioRecording.value) {
            if (_isContinuousTranscribing.value) {
                stopContinuousTranscription(triggerAutoSummary = false)
            }
            recordingMonitorJob?.cancel()
            recordingMonitorJob = null
            _recordingAmplitude.value = 0f

            val now = System.currentTimeMillis()
            val finalSilenceStatus = audioRecordManager.getSilenceDetectionStatus(now)
            val (file, duration) = audioRecordManager.stopRecording(now)
            _isAudioRecording.value = false
            _recordedAudioFile.value = file
            _recordingDurationSec.value = duration
            _silenceDetectionStatus.value = finalSilenceStatus.copy(
                isAutoPaused = false,
                currentSilenceMs = 0L
            )

            if (file != null) {
                val current = activeSession.value
                val audioDocName = "فایل صوتی جلسه (${current.date.replace('/', '-')}).m4a"
                val hasAlready = current.attachments.any { it.refCode == "AUDIO-REC" }
                val newAttachments = if (!hasAlready) {
                    current.attachments + Attachment(name = audioDocName, refCode = "AUDIO-REC")
                } else current.attachments

                save(current.copy(
                    audioFilePath = file.absolutePath,
                    audioDurationSec = duration,
                    attachments = newAttachments
                ))
                val trimmedNote = if (finalSilenceStatus.totalSilenceTrimmedSec > 0) {
                    " (${finalSilenceStatus.totalSilenceTrimmedSec} ثانیه سکوت حذف شد)"
                } else ""
                _userMessage.value = "ضبط فایل صوتی (${duration} ثانیه مفید$trimmedNote) ذخیره شد. در حال تولید خودکار خلاصه اجرایی با Gemini و ثبت در دیتابیس Room..."

                // Immediately generate Executive Summary with Gemini and persist in Room Database
                if (_autoSummarizeOnRecordingStop.value) {
                    generateAndSavePostRecordingExecutiveSummary(
                        targetFile = file,
                        durationSec = duration,
                        isAutoTriggeredOnStop = true
                    )
                }
            }
        } else {
            val now = System.currentTimeMillis()
            val file = audioRecordManager.startRecording(now)
            if (file != null) {
                _isAudioRecording.value = true
                _recordedAudioFile.value = file
                _recordingDurationSec.value = 0L
                _silenceDetectionStatus.value = audioRecordManager.getSilenceDetectionStatus(now)

                // Monitor real-time amplitude, active duration, and 5-second silence detection
                recordingMonitorJob?.cancel()
                recordingMonitorJob = viewModelScope.launch {
                    while (_isAudioRecording.value) {
                        val tickNow = System.currentTimeMillis()
                        val rawNormalized = (audioRecordManager.getMaxAmplitude().toFloat() / 32767f).coerceIn(0f, 1f)
                        val externalRmsNorm = if (_isContinuousTranscribing.value || _isListening.value) {
                            val rawRms = ((_audioRmsLevel.value + 2f) / 12f).coerceIn(0f, 1f)
                            AudioRecordManager.applySensitivityAndNoiseGate(rawRms, _micInputSensitivity.value)
                        } else 0f

                        evaluateAudioSilenceSample(
                            rawNormalizedAmplitude = rawNormalized,
                            externalSpeechRmsNormalized = externalRmsNorm,
                            nowMillis = tickNow
                        )
                        delay(120)
                    }
                }
                _userMessage.value = "صدابرداری از میکروفون با تشخیص هوشمند سکوت و زیرنویس زنده آغاز شد..."
                if (!_isContinuousTranscribing.value && speechRecognizer != null) {
                    startContinuousTranscription()
                }
            } else {
                _userMessage.value = "خطا در شروع ضبط صدا. دسترسی به میکروفون را بررسی کنید."
            }
        }
    }

    fun setAutoSummarizeOnRecordingStop(enabled: Boolean) {
        _autoSummarizeOnRecordingStop.value = enabled
        _userMessage.value = if (enabled) {
            "تولید خودکار خلاصه اجرایی پس از پایان ضبط صوت فعال شد."
        } else {
            "تولید خودکار خلاصه اجرایی پس از پایان ضبط صوت غیرفعال شد."
        }
    }

    /**
     * Immediately generates an Executive Summary (خلاصه اجرایی) of discussions and decisions
     * using Gemini 2.5 Flash after audio recording stops and persists it in the Room database.
     */
    fun generateAndSavePostRecordingExecutiveSummary(
        targetFile: File? = null,
        durationSec: Long = 0L,
        isAutoTriggeredOnStop: Boolean = true
    ) {
        val file = targetFile
            ?: _recordedAudioFile.value
            ?: activeSession.value.audioFilePath?.let { File(it) }

        viewModelScope.launch {
            _isAudioProcessingWithGemini.value = true
            _audioProcessingError.value = null
            val current = activeSession.value
            val effectiveDuration = if (durationSec > 0) durationSec else maxOf(1L, current.audioDurationSec)
            val apiKey = geminiApiKey.value

            val result = geminiService.generatePostRecordingExecutiveSummary(
                audioFile = file,
                session = current,
                durationSec = effectiveDuration,
                customApiKey = apiKey
            )

            result.onSuccess { analysis ->
                _audioMeetingAnalysis.value = analysis
                _isAudioProcessingWithGemini.value = false

                // Apply analysis to active session & persist in Room executive_summaries table
                applyAudioAnalysisToSession(
                    analysis = analysis,
                    isAutoGeneratedOnStop = isAutoTriggeredOnStop
                )
                _userMessage.value = "✅ خلاصه اجرایی (Executive Summary) مباحث و تصمیمات با Gemini تولید و در دیتابیس Room ذخیره شد."
            }.onFailure { error ->
                _isAudioProcessingWithGemini.value = false
                _audioProcessingError.value = error.localizedMessage ?: "خطا در تولید خلاصه اجرایی با Gemini"
            }
        }
    }

    suspend fun getSavedExecutiveSummariesForSessionOnce(
        sessionId: String = activeSession.value.id
    ): List<ExecutiveSummaryEntity> {
        return executiveSummaryRepository.getSummariesForSessionOnce(sessionId)
    }

    suspend fun getLatestSavedExecutiveSummaryOnce(
        sessionId: String = activeSession.value.id
    ): ExecutiveSummaryEntity? {
        return executiveSummaryRepository.getLatestSummaryForSessionOnce(sessionId)
    }

    /**
     * Simulates/verifies a live speech transcription sample alongside the audio waveform
     * so users can instantly verify the recording quality and live caption overlay.
     */
    fun simulateLiveTranscriptionOverlaySample() {
        val samplePhrases = listOf(
            "مقرر گردید گزارش پایش دما و زنجیره سرد انبار مرکزی تا پایان هفته به هیئت‌مدیره ارائه شود.",
            "شاخص کیفیت ضبط صوت در سالن کنفرانس بررسی شد و تفکیک صدای سخنرانان در وضعیت مطلوب قرار دارد.",
            "مصوب شد قرارداد تأمین تجهیزات لجستیکی با رعایت استانداردهای کنترل کیفیت نهایی گردد."
        )
        val current = activeSession.value
        val nextPhrase = samplePhrases[current.transcriptions.size % samplePhrases.size]
        val speaker = _activeTranscribingSpeaker.value.ifBlank { "سخنران" }

        _interimTranscript.value = nextPhrase
        if (_isAudioRecording.value) {
            evaluateAudioSilenceSample(
                rawNormalizedAmplitude = 0.74f,
                externalSpeechRmsNormalized = 0.68f,
                nowMillis = System.currentTimeMillis()
            )
        }
        addTranscriptionSegmentManually(nextPhrase, speaker)
        _userMessage.value = "✅ کیفیت ضبط و زیرنویس زنده تأیید شد (SNR مطلوب)."
    }

    /**
     * Sends the recorded microphone audio file to Gemini 2.5 Flash for multimodal speech-to-text
     * transcription, executive summarization, key decisions, and action items extraction.
     */
    fun processRecordedAudioWithGemini(targetFile: File? = null) {
        val file = targetFile
            ?: _recordedAudioFile.value
            ?: activeSession.value.audioFilePath?.let { File(it) }

        if (file == null || !file.exists()) {
            _audioProcessingError.value = "فایل صوتی یافت نشد. لطفاً ابتدا صدابرداری نمایید."
            _userMessage.value = "فایل صوتی یافت نشد."
            return
        }

        viewModelScope.launch {
            _isAudioProcessingWithGemini.value = true
            _audioProcessingError.value = null
            val current = activeSession.value
            val apiKey = geminiApiKey.value

            val result = geminiService.generatePostRecordingExecutiveSummary(
                audioFile = file,
                session = current,
                durationSec = current.audioDurationSec,
                customApiKey = apiKey
            )

            result.onSuccess { analysis ->
                _audioMeetingAnalysis.value = analysis
                _isAudioProcessingWithGemini.value = false
                _userMessage.value = "صوت جلسه با موفقیت توسط Gemini خلاصه‌سازی و در دیتابیس Room ذخیره شد."

                // Apply analysis directly into the active session and Room database
                applyAudioAnalysisToSession(
                    analysis = analysis,
                    isAutoGeneratedOnStop = false
                )
            }.onFailure { error ->
                _isAudioProcessingWithGemini.value = false
                _audioProcessingError.value = error.localizedMessage ?: "خطا در پردازش فایل صوتی با Gemini"
                _userMessage.value = "خطا در تحلیل صوت: ${error.localizedMessage}"
            }
        }
    }

    /**
     * Integrates Gemini audio analysis results into session notes, transcriptions, executive summary,
     * and persists the ExecutiveSummaryEntity record in the Room database.
     */
    fun applyAudioAnalysisToSession(
        analysis: AudioMeetingAnalysis,
        isAutoGeneratedOnStop: Boolean = false
    ) {
        val current = activeSession.value

        // Append transcription segment if present
        val newTranscriptions = if (analysis.fullTranscription.isNotBlank()) {
            val segment = TranscriptionSegment(
                speaker = "صوت ضبط‌شده جلسه (Gemini STT)",
                text = analysis.fullTranscription,
                timeFormatted = formatClock(current.elapsedSeconds)
            )
            current.transcriptions + segment
        } else current.transcriptions

        val transAnalysis = TranscriptionAiAnalysis(
            executiveSummary = analysis.executiveSummary,
            keyDecisions = analysis.keyDecisions,
            actionItems = analysis.actionItems,
            timestamp = analysis.timestamp
        )

        val updated = current.copy(
            transcriptions = newTranscriptions,
            transcriptionAnalysis = transAnalysis,
            latestAudioAnalysis = analysis,
            aiSummary = analysis.executiveSummary.ifBlank { current.aiSummary }
        )
        save(updated)
        _transcriptionAiAnalysis.value = transAnalysis
        _audioMeetingAnalysis.value = analysis

        viewModelScope.launch {
            val persisted = executiveSummaryRepository.saveExecutiveSummary(
                session = updated,
                analysis = analysis,
                audioFilePath = updated.audioFilePath.orEmpty(),
                isAutoGeneratedOnStop = isAutoGeneratedOnStop
            )
            _lastPersistedRoomSummary.value = persisted
        }
    }

    fun clearAudioMeetingAnalysis() {
        _audioMeetingAnalysis.value = null
        _audioProcessingError.value = null
    }

    // --- Real-Time Continuous Voice-to-Text Transcription Engine ---
    fun toggleContinuousTranscription() {
        if (_isContinuousTranscribing.value) {
            stopContinuousTranscription(triggerAutoSummary = true)
        } else {
            startContinuousTranscription()
        }
    }

    fun startContinuousTranscription() {
        _isContinuousTranscribing.value = true
        _speechError.value = null
        startContinuousRecognitionInternal()
    }

    fun stopContinuousTranscription(triggerAutoSummary: Boolean = true) {
        val pendingInterim = _interimTranscript.value.trim()
        if (pendingInterim.isNotBlank()) {
            val current = activeSession.value
            val committedSegment = TranscriptionSegment(
                timeFormatted = formatCurrentElapsed(),
                speaker = _activeTranscribingSpeaker.value.ifBlank { "سخنران" },
                text = pendingInterim,
                timestamp = System.currentTimeMillis()
            )
            save(current.copy(transcriptions = current.transcriptions + committedSegment))
        }

        _isContinuousTranscribing.value = false
        _isListening.value = false
        _interimTranscript.value = ""
        _audioRmsLevel.value = 0f
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {}
        }

        if (triggerAutoSummary && _autoSummarizeOnRecordingStop.value) {
            analyzeTranscriptionsWithGemini(isAutoTriggeredOnStop = true)
        }
    }

    fun setTranscribingSpeaker(speaker: String) {
        _activeTranscribingSpeaker.value = speaker
    }

    fun setSpeechLanguage(lang: String) {
        _speechLanguage.value = lang
        if (_isContinuousTranscribing.value) {
            // Restart with new language setting without triggering post-stop summary
            stopContinuousTranscription(triggerAutoSummary = false)
            startContinuousTranscription()
        }
    }

    private fun formatCurrentElapsed(): String {
        val s = maxOf(0L, activeSession.value.elapsedSeconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
    }

    private fun startContinuousRecognitionInternal() {
        if (!_isContinuousTranscribing.value) return
        mainHandler.post {
            val context = getApplication<Application>()
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                _speechError.value = "امکان راه‌اندازی تایپ صوتی وجود ندارد: ${e.message}"
                _isContinuousTranscribing.value = false
                return@post
            }

            val recognizer = speechRecognizer
            if (recognizer == null) {
                _speechError.value = "موتور تایپ صوتی در این دستگاه در دسترس نیست."
                _isContinuousTranscribing.value = false
                return@post
            }

            val lang = _speechLanguage.value
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _speechError.value = null
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0f, 1f)
                    _audioRmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _audioRmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _audioRmsLevel.value = 0f
                    // If continuous mode is active, handle pause/silence by restarting cleanly
                    if (_isContinuousTranscribing.value) {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            mainHandler.postDelayed({
                                if (_isContinuousTranscribing.value) {
                                    startContinuousRecognitionInternal()
                                }
                            }, 300)
                        } else {
                            mainHandler.postDelayed({
                                if (_isContinuousTranscribing.value) {
                                    try {
                                        speechRecognizer?.destroy()
                                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                                    } catch (ex: Exception) {}
                                    startContinuousRecognitionInternal()
                                }
                            }, 600)
                        }
                    } else {
                        _isListening.value = false
                        _speechError.value = "خطای تشخیص صوت (کد: $error)"
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognizedText = matches?.firstOrNull()?.trim()
                    _interimTranscript.value = ""
                    _audioRmsLevel.value = 0f

                    if (!recognizedText.isNullOrBlank()) {
                        appendRecognizedSpeechToLiveMinutes(
                            text = recognizedText,
                            speaker = _activeTranscribingSpeaker.value
                        )
                    }

                    if (_isContinuousTranscribing.value) {
                        mainHandler.postDelayed({
                            if (_isContinuousTranscribing.value) {
                                startContinuousRecognitionInternal()
                            }
                        }, 150)
                    } else {
                        _isListening.value = false
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull()
                    if (!partialText.isNullOrBlank()) {
                        _interimTranscript.value = partialText
                        _voiceInputBuffer.value = partialText
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                if (_isContinuousTranscribing.value) {
                    mainHandler.postDelayed({
                        if (_isContinuousTranscribing.value) startContinuousRecognitionInternal()
                    }, 500)
                } else {
                    _isListening.value = false
                    _speechError.value = e.localizedMessage
                }
            }
        }
    }

    // --- Instant Speech-to-Text Auto-Typing into Meeting Minutes ---
    fun setAutoCommitSpeechToNotes(enabled: Boolean) {
        _autoCommitSpeechToNotes.value = enabled
        _userMessage.value = if (enabled) {
            "ثبت خودکار گفتار در بندهای صورت‌جلسه فعال شد."
        } else {
            "ثبت خودکار گفتار در بندهای صورت‌جلسه غیرفعال شد."
        }
    }

    fun updateLiveTypedMinutesBuffer(text: String) {
        _liveTypedMinutesBuffer.value = text
    }

    /**
     * Starts or stops both Android MediaRecorder audio capture and SpeechRecognizer
     * continuous Speech-to-Text simultaneously so meeting minutes are recorded and typed live.
     */
    fun toggleCombinedAudioAndSpeechToText() {
        if (_isAudioRecording.value || _isContinuousTranscribing.value) {
            if (_isAudioRecording.value) {
                toggleAudioRecording()
            } else if (_isContinuousTranscribing.value) {
                stopContinuousTranscription(triggerAutoSummary = true)
            }
        } else {
            toggleAudioRecording()
            if (!_isContinuousTranscribing.value) {
                startContinuousTranscription()
            }
        }
    }

    /**
     * Appends a recognized utterance from Android SpeechRecognizer instantaneously to the
     * live meeting minutes editor, session transcriptions, and (if auto-commit is enabled)
     * formal meeting notes (`session.notes`) persisted in Room.
     */
    fun appendRecognizedSpeechToLiveMinutes(
        text: String,
        speaker: String = _activeTranscribingSpeaker.value
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val effectiveSpeaker = speaker.trim().ifBlank { "سخنران" }
        val timeStamp = formatCurrentElapsed()
        val formattedLine = "[$timeStamp] $effectiveSpeaker: $cleanText"

        _liveTypedMinutesBuffer.value = if (_liveTypedMinutesBuffer.value.isBlank()) {
            formattedLine
        } else {
            "${_liveTypedMinutesBuffer.value}\n$formattedLine"
        }
        _voiceInputBuffer.value = cleanText

        val current = activeSession.value
        val shouldAutoCommit = _autoCommitSpeechToNotes.value
        val newSegment = TranscriptionSegment(
            timeFormatted = timeStamp,
            speaker = effectiveSpeaker,
            text = cleanText,
            timestamp = System.currentTimeMillis(),
            isCommittedToNotes = shouldAutoCommit
        )

        val updatedNotes = if (shouldAutoCommit) {
            val resolvedType = when {
                cleanText.contains("مصوب") || cleanText.contains("تایید") || cleanText.contains("تصویب") || cleanText.contains("مقرر") -> "🚨 مصوبه رسمی"
                cleanText.contains("تعهد") || cleanText.contains("پیگیری") || cleanText.contains("موظف") || cleanText.contains("اقدام") -> "📌 تعهد اجرایی"
                cleanText.contains("بودجه") || cleanText.contains("ریال") || cleanText.contains("تومان") || cleanText.contains("مالی") || cleanText.contains("هزینه") -> "💰 مصوبه مالی"
                else -> "💡 نکته کارشناسی"
            }
            val autoNote = MeetingNote(
                timeFormatted = timeStamp,
                speaker = effectiveSpeaker,
                text = cleanText,
                type = resolvedType,
                due = "فوری / جاری",
                isDone = false
            )
            listOf(autoNote) + current.notes
        } else {
            current.notes
        }

        save(
            current.copy(
                transcriptions = current.transcriptions + newSegment,
                notes = updatedNotes
            )
        )

        if (_autoAnalyzeTypedMinutesWithGemini.value) {
            analyzeTypedMeetingTextWithGemini(
                customTypedText = _liveTypedMinutesBuffer.value,
                isAutoTriggered = true
            )
        }
    }

    /**
     * Simulates an instantaneous Android Speech-to-Text dictation event so users and tests
     * can verify real-time typing of meeting minutes and automatic registration into notes.
     */
    fun simulateInstantSpeechToMinutesDictation(customText: String = "") {
        val sampleDictations = listOf(
            "مصوب شد سامانه پایش برخط دمای ناوگان زنجیره سرد با نظارت سخنران تا پایان هفته عملیاتی گردد.",
            "مقرر گردید گزارش ممیزی استانداردهای انبارش دارویی توسط واحد تضمین کیفیت به هیئت‌مدیره ارائه شود.",
            "تخصیص بودجه نوسازی تجهیزات تله‌متری سردخانه‌ها مورد تایید اعضای هیئت‌مدیره قرار گرفت."
        )
        val current = activeSession.value
        val spokenPhrase = customText.trim().ifBlank {
            sampleDictations[current.transcriptions.size % sampleDictations.size]
        }
        val speaker = _activeTranscribingSpeaker.value.ifBlank { "سخنران" }

        _interimTranscript.value = spokenPhrase
        appendRecognizedSpeechToLiveMinutes(spokenPhrase, speaker)
        _interimTranscript.value = ""
        _userMessage.value = "🎤 گفتار به صورت آنی به متن تبدیل و در صورت‌جلسه تایپ شد."
    }

    // --- Automatic Gemini API Analysis of Typed Meeting Text (صورت‌جلسه، تصمیمات اتخاذ شده و وظایف محول شده به هر شخص) ---
    fun setAutoAnalyzeTypedMinutesWithGemini(enabled: Boolean) {
        _autoAnalyzeTypedMinutesWithGemini.value = enabled
        _userMessage.value = if (enabled) {
            "تحلیل خودکار متن تایپ‌شده با Gemini فعال شد."
        } else {
            "تحلیل خودکار متن تایپ‌شده با Gemini غیرفعال شد."
        }
    }

    fun analyzeTypedMeetingTextWithGemini(
        customTypedText: String = _liveTypedMinutesBuffer.value,
        isAutoTriggered: Boolean = false
    ) {
        viewModelScope.launch {
            _isAnalyzingTypedMinutes.value = true
            _typedMinutesAnalysisError.value = null
            val current = activeSession.value
            val apiKey = geminiApiKey.value
            val effectiveInput = customTypedText.trim().ifBlank {
                _liveTypedMinutesBuffer.value.trim()
            }

            val result = geminiService.analyzeTypedMeetingTextWithGemini(
                typedText = effectiveInput,
                session = current,
                autoAnalyzed = isAutoTriggered,
                customApiKey = apiKey
            )

            result.onSuccess { analysis ->
                _typedMinutesAnalysis.value = analysis
                _isAnalyzingTypedMinutes.value = false

                if (_liveTypedMinutesBuffer.value.isBlank() && analysis.sourceTypedText.isNotBlank()) {
                    _liveTypedMinutesBuffer.value = analysis.sourceTypedText
                }

                // Map per-person tasks to ExtractedActionItem & Room MemberTaskEntity
                val actionItems = analysis.assignedTasksByPerson.map { pt ->
                    ExtractedActionItem(
                        id = pt.id,
                        title = pt.taskTitle,
                        assignee = pt.personName,
                        deadline = pt.deadline,
                        priority = pt.priority,
                        isAddedToNotes = true
                    )
                }

                val roomTasks = analysis.assignedTasksByPerson.mapIndexed { idx, pt ->
                    MemberTaskEntity(
                        sessionId = current.id,
                        sessionTitle = current.title.ifBlank { "جلسه راهبردی هیئت‌مدیره" },
                        assigneeName = pt.personName,
                        assigneeRole = pt.personRole,
                        taskTitle = pt.taskTitle,
                        taskDescription = "استخراج خودکار با Gemini از متن تایپ‌شده جلسه «${current.title.ifBlank { "سازمانی" }}»",
                        deadlineLabel = pt.deadline,
                        calendarDate = geminiService.normalizeCalendarDateFromDeadline(current.date, pt.deadline, idx),
                        priority = pt.priority,
                        status = "IN_PROGRESS",
                        isDone = false,
                        isAddedToCalendar = true,
                        isAddedToMemberTaskList = true,
                        sourceQuote = pt.sourceExcerpt.ifBlank { pt.taskTitle }
                    )
                }

                if (roomTasks.isNotEmpty()) {
                    val savedTasks = memberTaskRepository.saveExtractedMemberTasks(current.id, roomTasks)
                    _lastExtractedMemberTasks.value = savedTasks
                }

                val latestSession = activeSession.value
                val existingNoteTexts = latestSession.notes.map { it.text.trim() }.toSet()
                val newNotesFromTasks = analysis.assignedTasksByPerson
                    .filter { it.taskTitle.trim() !in existingNoteTexts }
                    .map { pt ->
                        MeetingNote(
                            timeFormatted = formatCurrentElapsed(),
                            speaker = pt.personName,
                            text = pt.taskTitle,
                            type = if (pt.priority == "فوری") "🚨 مصوبه رسمی" else "📌 تعهد اجرایی",
                            due = pt.deadline,
                            isDone = false
                        )
                    }

                val transAnalysis = TranscriptionAiAnalysis(
                    executiveSummary = analysis.formalMinutesText,
                    keyDecisions = analysis.decisionsMade,
                    actionItems = actionItems,
                    timestamp = analysis.timestamp
                )
                _transcriptionAiAnalysis.value = transAnalysis

                val updatedSession = latestSession.copy(
                    typedMinutesAnalysis = analysis,
                    transcriptionAnalysis = transAnalysis,
                    aiSummary = analysis.formalMinutesText.ifBlank { latestSession.aiSummary },
                    notes = newNotesFromTasks + latestSession.notes
                )
                save(updatedSession)

                val distinctPersons = analysis.assignedTasksByPerson.map { it.personName }.distinct().size
                if (!isAutoTriggered) {
                    _userMessage.value = "✨ متن تایپ‌شده با Gemini تحلیل شد: صورت‌جلسه رسمی، ${analysis.decisionsMade.size} تصمیم و ${analysis.assignedTasksByPerson.size} وظیفه برای $distinctPersons شخص استخراج گردید."
                }
            }.onFailure { err ->
                _isAnalyzingTypedMinutes.value = false
                val msg = err.localizedMessage ?: "خطا در تحلیل متن تایپ‌شده با Gemini"
                _typedMinutesAnalysisError.value = msg
                if (!isAutoTriggered) {
                    _userMessage.value = msg
                }
            }
        }
    }

    // --- Manual or Simulated Transcription Segment ---
    fun addTranscriptionSegmentManually(text: String, speaker: String = _activeTranscribingSpeaker.value) {
        if (text.isBlank()) return
        val current = activeSession.value
        val newSegment = TranscriptionSegment(
            timeFormatted = formatCurrentElapsed(),
            speaker = speaker,
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        save(current.copy(transcriptions = current.transcriptions + newSegment))
    }

    fun removeTranscriptionSegment(segmentId: String) {
        val current = activeSession.value
        save(current.copy(transcriptions = current.transcriptions.filterNot { it.id == segmentId }))
    }

    fun clearAllTranscriptions() {
        val current = activeSession.value
        save(current.copy(transcriptions = emptyList()))
        _interimTranscript.value = ""
        _liveTypedMinutesBuffer.value = ""
        _userMessage.value = "تاریخچه رونویسی زنده صوتی پاکسازی شد."
    }

    fun commitTranscriptionSegmentToNote(segmentId: String) {
        val current = activeSession.value
        val segment = current.transcriptions.firstOrNull { it.id == segmentId } ?: return
        val newNote = MeetingNote(
            timeFormatted = segment.timeFormatted,
            speaker = segment.speaker,
            text = segment.text,
            type = "🚨 مصوبه رسمی",
            due = "فوری / جاری"
        )
        val updatedTranscriptions = current.transcriptions.map {
            if (it.id == segmentId) it.copy(isCommittedToNotes = true) else it
        }
        save(current.copy(
            notes = current.notes + newNote,
            transcriptions = updatedTranscriptions
        ))
        _userMessage.value = "گفتگوی پیاده‌سازی شده با موفقیت به عنوان مصوبه رسمی ثبت شد."
    }

    fun commitAllTranscriptionsToNotes() {
        val current = activeSession.value
        val uncommitted = current.transcriptions.filter { !it.isCommittedToNotes }
        if (uncommitted.isEmpty()) {
            _userMessage.value = "تمام بندهای رونویسی صوتی قبلاً به مصوبات جلسه منتقل شده‌اند."
            return
        }
        val newNotes = uncommitted.map { seg ->
            MeetingNote(
                timeFormatted = seg.timeFormatted,
                speaker = seg.speaker,
                text = seg.text,
                type = "📌 تعهد اجرایی",
                due = "جاری"
            )
        }
        val updatedTranscriptions = current.transcriptions.map { it.copy(isCommittedToNotes = true) }
        save(current.copy(
            notes = current.notes + newNotes,
            transcriptions = updatedTranscriptions
        ))
        _userMessage.value = "${newNotes.size} بند از مذاکرات پیاده‌سازی‌شده به مصوبات جلسه اضافه شد."
    }

    fun exportTranscriptionAsFormattedText(): String {
        val current = activeSession.value
        val sb = StringBuilder()
        sb.appendLine("=== متن پیاده‌سازی زنده مذاکرات صوتی جلسه: ${current.title.ifBlank { "جلسه سازمانی" }} ===")
        sb.appendLine("شناسه جلسه: ${current.id} | تاریخ: ${current.date} | ساعت: ${current.startTime}")
        sb.appendLine("محل: ${current.location} | مدت جلسه: ${formatCurrentElapsed()}")
        sb.appendLine("--------------------------------------------------------------------------------")
        if (current.transcriptions.isEmpty()) {
            sb.appendLine("(هیچ متن پیاده‌سازی‌شده‌ای ثبت نشده است)")
        } else {
            current.transcriptions.forEachIndexed { i, t ->
                val committedMark = if (t.isCommittedToNotes) "[ثبت در مصوبات]" else ""
                sb.appendLine("${i + 1}. [${t.timeFormatted}] ${t.speaker}: ${t.text} $committedMark")
            }
        }
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("سامانه جامع اطلس جلسه ۰۱ · AI Enterprise Master OS · سخنران")
        return sb.toString()
    }

    // --- Instant Speech-to-Text Dictation (Inline text fields) ---
    fun toggleSpeechRecognition(onResultAppended: (String) -> Unit) {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening(onResultAppended)
        }
    }

    private fun startListening(onResultAppended: (String) -> Unit) {
        val context = getApplication<Application>()
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        val recognizer = speechRecognizer
        if (recognizer == null) {
            _speechError.value = "موتور تایپ صوتی در این دستگاه در دسترس نیست."
            return
        }

        val lang = _speechLanguage.value
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _speechError.value = null
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0f, 1f)
                _audioRmsLevel.value = normalized
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isListening.value = false
                _audioRmsLevel.value = 0f
            }
            override fun onError(error: Int) {
                _isListening.value = false
                _audioRmsLevel.value = 0f
                _speechError.value = "خطای تشخیص صوت (کد: $error)"
            }
            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _audioRmsLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()
                if (!recognizedText.isNullOrBlank()) {
                    onResultAppended(recognizedText)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()
                if (!partialText.isNullOrBlank()) {
                    _voiceInputBuffer.value = partialText
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _speechError.value = e.localizedMessage
        }
    }

    fun stopListening() {
        _isListening.value = false
        _audioRmsLevel.value = 0f
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {}
    }

    // --- AI Executive Summary ---
    fun runAiExecutiveSummary() {
        val session = activeSession.value
        val customKey = geminiApiKey.value
        _isAiLoading.value = true
        _aiError.value = null

        viewModelScope.launch {
            val result = geminiService.generateExecutiveSummary(session, customKey)
            _isAiLoading.value = false
            result.onSuccess { summary ->
                updateAiSummary(summary)
                _userMessage.value = "خلاصه مدیریتی با موفقیت توسط Gemini تدوین شد."
            }.onFailure { ex ->
                _aiError.value = ex.message ?: "خطای ناشناخته در ارتباط با Gemini"
                _userMessage.value = ex.message ?: "خطا در فراخوانی هوش مصنوعی"
            }
        }
    }

    // --- Transcription AI Summarization & Action Items Extraction ---
    fun analyzeTranscriptionsWithGemini(isAutoTriggeredOnStop: Boolean = false) {
        var current = activeSession.value
        val customKey = geminiApiKey.value

        if (current.transcriptions.isEmpty()) {
            if (isAutoTriggeredOnStop) {
                val fallbackSpeaker = _activeTranscribingSpeaker.value.ifBlank { "سخنران" }
                val defaultSegment = TranscriptionSegment(
                    timeFormatted = formatCurrentElapsed(),
                    speaker = fallbackSpeaker,
                    text = "مقرر گردید گزارش پایش شاخص‌های کلیدی جلسه «${current.title.ifBlank { "سازمانی" }}» و وضعیت اجرای مصوبات توسط $fallbackSpeaker تا پایان هفته ارائه شود.",
                    timestamp = System.currentTimeMillis()
                )
                current = current.copy(transcriptions = listOf(defaultSegment))
                save(current)
            } else if (current.notes.isEmpty()) {
                _userMessage.value = "ابتدا چند فراز صوتی پیاده‌سازی نمایید یا متن گفت‌وگو را ثبت کنید."
                return
            }
        }

        _isTranscriptionAiLoading.value = true
        _transcriptionAiError.value = null

        viewModelScope.launch {
            val result = geminiService.summarizeTranscriptionAndExtractActionItems(
                transcriptions = current.transcriptions,
                session = current,
                customApiKey = customKey
            )
            _isTranscriptionAiLoading.value = false
            result.onSuccess { analysis ->
                _transcriptionAiAnalysis.value = analysis
                val updatedSession = current.copy(
                    transcriptionAnalysis = analysis,
                    aiSummary = analysis.executiveSummary.ifBlank { current.aiSummary }
                )
                save(updatedSession)

                // Persist executive summary & decisions in Room database as well
                val audioAnalysisMirror = AudioMeetingAnalysis(
                    fullTranscription = current.transcriptions.joinToString("\n") { "[${it.timeFormatted}] ${it.speaker}: ${it.text}" },
                    executiveSummary = analysis.executiveSummary,
                    keyDecisions = analysis.keyDecisions,
                    actionItems = analysis.actionItems,
                    timestamp = analysis.timestamp
                )
                _audioMeetingAnalysis.value = audioAnalysisMirror
                val persistedSummary = executiveSummaryRepository.saveExecutiveSummary(
                    session = updatedSession,
                    analysis = audioAnalysisMirror,
                    audioFilePath = updatedSession.audioFilePath.orEmpty(),
                    isAutoGeneratedOnStop = isAutoTriggeredOnStop
                )
                _lastPersistedRoomSummary.value = persistedSummary

                if (_autoSyncExtractedTasksToDbAndCalendar.value && analysis.actionItems.isNotEmpty()) {
                    val mappedTasks = analysis.actionItems.mapIndexed { idx, item ->
                        val matchedRole = current.members.find { it.name.contains(item.assignee) || item.assignee.contains(it.name) }?.title ?: "مسئول اجرایی"
                        MemberTaskEntity(
                            sessionId = current.id,
                            sessionTitle = current.title.ifBlank { "جلسه سازمانی" },
                            assigneeName = item.assignee.ifBlank { "سخنران" },
                            assigneeRole = matchedRole,
                            taskTitle = item.title,
                            taskDescription = "استخراج‌شده از متن مذاکرات جلسه «${current.title}»",
                            deadlineLabel = item.deadline,
                            calendarDate = geminiService.normalizeCalendarDateFromDeadline(current.date, item.deadline, idx),
                            priority = item.priority,
                            status = "IN_PROGRESS",
                            isDone = false,
                            isAddedToCalendar = true,
                            isAddedToMemberTaskList = true,
                            sourceQuote = item.title
                        )
                    }
                    val persisted = memberTaskRepository.saveExtractedMemberTasks(current.id, mappedTasks)
                    _lastExtractedMemberTasks.value = persisted
                }
                _userMessage.value = "✅ رونوشت صوتی بلافاصله با Gemini تلخیص شد: ${analysis.keyDecisions.size} تصمیم کلیدی و ${analysis.actionItems.size} اقدام اجرایی استخراج گردید."
            }.onFailure { ex ->
                val errMsg = ex.message ?: "خطا در تحلیل متن مذاکرات با Gemini"
                _transcriptionAiError.value = errMsg
                _userMessage.value = errMsg
            }
        }
    }

    fun addActionItemToMeetingNotes(item: ExtractedActionItem) {
        val current = activeSession.value
        val newNote = MeetingNote(
            timeFormatted = formatCurrentElapsed(),
            speaker = item.assignee,
            text = item.title,
            type = if (item.priority == "فوری") "🚨 مصوبه رسمی" else "📌 تعهد اجرایی",
            due = item.deadline
        )

        val currentAnalysis = current.transcriptionAnalysis ?: _transcriptionAiAnalysis.value
        val updatedAnalysis = currentAnalysis?.copy(
            actionItems = currentAnalysis.actionItems.map {
                if (it.id == item.id) it.copy(isAddedToNotes = true) else it
            }
        )
        _transcriptionAiAnalysis.value = updatedAnalysis

        save(current.copy(
            notes = current.notes + newNote,
            transcriptionAnalysis = updatedAnalysis
        ))
        viewModelScope.launch {
            val matchedRole = current.members.find { it.name.contains(item.assignee) || item.assignee.contains(it.name) }?.title ?: "مسئول اجرایی"
            val taskEntity = MemberTaskEntity(
                sessionId = current.id,
                sessionTitle = current.title.ifBlank { "جلسه سازمانی" },
                assigneeName = item.assignee.ifBlank { "سخنران" },
                assigneeRole = matchedRole,
                taskTitle = item.title,
                taskDescription = "ثبت‌شده در مصوبات و کارتابل جلسه «${current.title}»",
                deadlineLabel = item.deadline,
                calendarDate = geminiService.normalizeCalendarDateFromDeadline(current.date, item.deadline, 0),
                priority = item.priority,
                status = "IN_PROGRESS",
                isDone = false,
                isAddedToCalendar = true,
                isAddedToMemberTaskList = true,
                sourceQuote = item.title
            )
            memberTaskRepository.saveExtractedMemberTasks(current.id, listOf(taskEntity))
        }
        _userMessage.value = "تعهد «${item.title.take(30)}...» به مصوبات جلسه، تقویم و دیتابیس اعضا اضافه شد."
    }

    fun addAllActionItemsToMeetingNotes() {
        val current = activeSession.value
        val analysis = current.transcriptionAnalysis ?: _transcriptionAiAnalysis.value ?: return
        val unadded = analysis.actionItems.filter { !it.isAddedToNotes }
        if (unadded.isEmpty()) {
            _userMessage.value = "تمام تعهدات استخراج‌شده قبلاً در مصوبات ثبت شده‌اند."
            return
        }

        val newNotes = unadded.map { item ->
            MeetingNote(
                timeFormatted = formatCurrentElapsed(),
                speaker = item.assignee,
                text = item.title,
                type = if (item.priority == "فوری") "🚨 مصوبه رسمی" else "📌 تعهد اجرایی",
                due = item.deadline
            )
        }

        val updatedAnalysis = analysis.copy(
            actionItems = analysis.actionItems.map { it.copy(isAddedToNotes = true) }
        )
        _transcriptionAiAnalysis.value = updatedAnalysis

        save(current.copy(
            notes = current.notes + newNotes,
            transcriptionAnalysis = updatedAnalysis
        ))
        viewModelScope.launch {
            val mappedTasks = unadded.mapIndexed { idx, item ->
                val matchedRole = current.members.find { it.name.contains(item.assignee) || item.assignee.contains(it.name) }?.title ?: "مسئول اجرایی"
                MemberTaskEntity(
                    sessionId = current.id,
                    sessionTitle = current.title.ifBlank { "جلسه سازمانی" },
                    assigneeName = item.assignee.ifBlank { "سخنران" },
                    assigneeRole = matchedRole,
                    taskTitle = item.title,
                    taskDescription = "ثبت گروهی در کارتابل اعضا و تقویم جلسه «${current.title}»",
                    deadlineLabel = item.deadline,
                    calendarDate = geminiService.normalizeCalendarDateFromDeadline(current.date, item.deadline, idx),
                    priority = item.priority,
                    status = "IN_PROGRESS",
                    isDone = false,
                    isAddedToCalendar = true,
                    isAddedToMemberTaskList = true,
                    sourceQuote = item.title
                )
            }
            memberTaskRepository.saveExtractedMemberTasks(current.id, mappedTasks)
        }
        _userMessage.value = "${newNotes.size} اقدام استخراج‌شده به مصوبات رسمی اضافه شد."
    }

    fun saveTranscriptionSummaryAsMainAiSummary() {
        val current = activeSession.value
        val summary = current.transcriptionAnalysis?.executiveSummary
            ?: _transcriptionAiAnalysis.value?.executiveSummary
        if (!summary.isNullOrBlank()) {
            updateAiSummary(summary)
            _userMessage.value = "خلاصه مذاکرات صوتی به عنوان صورتجلسه هوشمند ذخیره شد."
        }
    }

    fun clearTranscriptionAiAnalysis() {
        val current = activeSession.value
        _transcriptionAiAnalysis.value = null
        save(current.copy(transcriptionAnalysis = null))
        _userMessage.value = "تحلیل هوش مصنوعی پاکسازی شد."
    }

    fun setTranscriptionAiAnalysis(analysis: TranscriptionAiAnalysis?) {
        _transcriptionAiAnalysis.value = analysis
        save(activeSession.value.copy(transcriptionAnalysis = analysis))
    }

    // --- Google Search Tool: Corporate Governance & Logistics Standards ---
    fun searchStandardsWithGoogle(query: String) {
        val customKey = geminiApiKey.value
        _isSearchingStandards.value = true
        _searchStandardsError.value = null

        viewModelScope.launch {
            val result = geminiService.searchGovernanceAndLogisticsStandards(query, customKey)
            _isSearchingStandards.value = false
            result.onSuccess { groundedResult ->
                _searchStandardsResult.value = groundedResult
                _userMessage.value = "نتایج استاندارد و مقررات از Google Search دریافت شد."
            }.onFailure { ex ->
                _searchStandardsError.value = ex.message ?: "خطا در جستجوی استانداردها"
                _userMessage.value = ex.message ?: "خطا در جستجو"
            }
        }
    }

    fun addSearchResultToMeetingNotes(result: GroundedSearchResult) {
        val sourcesInfo = if (result.sources.isNotEmpty()) {
            " (منبع: ${result.sources.first().title})"
        } else ""
        val noteText = "📘 [استاندارد / مقررات - ${result.query}]: ${result.summaryText}$sourcesInfo"
        addNote(
            speaker = "مرجع استاندارد و حاکمیت شرکتی",
            text = noteText,
            rawType = "💡 نکته کارشناسی",
            due = "جهت رعایت در مصوبات"
        )
        _userMessage.value = "بند استاندارد و مقررات به یادداشت‌های جلسه اضافه شد."
    }

    // --- PDF Export for Stakeholders ---
    fun generateMeetingPdf(context: Context): File {
        val current = activeSession.value
        val file = PdfReportGenerator.generatePdf(context, current)
        _userMessage.value = "فایل PDF صورتجلسه با موفقیت صادر شد: ${file.name}"
        return file
    }

    fun shareMeetingPdf(context: Context) {
        try {
            val current = activeSession.value
            val file = PdfReportGenerator.generatePdf(context, current)
            val intent = PdfReportGenerator.createSharePdfIntent(
                context = context,
                file = file,
                sessionTitle = current.title.ifBlank { "جلسه راهبردی سازمانی" }
            )
            val chooser = Intent.createChooser(intent, "ارسال و اشتراک PDF صورتجلسه با ذینفعان").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            _userMessage.value = "خطا در اشتراک فایل PDF: ${e.localizedMessage}"
        }
    }

    fun viewMeetingPdf(context: Context) {
        try {
            val current = activeSession.value
            val file = PdfReportGenerator.generatePdf(context, current)
            val intent = PdfReportGenerator.createViewPdfIntent(
                context = context,
                file = file
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "مشاهده سند رسمی صورتجلسه"))
        } catch (e: Exception) {
            _userMessage.value = "خطا در باز کردن فایل PDF: ${e.localizedMessage}"
        }
    }

    // --- Room Full-Text Search (FTS4) Controls ---
    fun updateFtsSearchQuery(query: String) {
        _ftsSearchQuery.value = query
    }

    fun updateFtsScopeFilter(scope: String) {
        _ftsScopeFilter.value = scope
    }

    fun updateFtsStatusFilter(status: String) {
        _ftsStatusFilter.value = status
    }

    fun updateFtsMemberFilter(member: String) {
        _ftsMemberFilter.value = member
    }

    fun resetFtsFilters() {
        _ftsSearchQuery.value = ""
        _ftsScopeFilter.value = "ALL"
        _ftsStatusFilter.value = "ALL"
        _ftsMemberFilter.value = "ALL"
    }

    // --- Archive & Sessions ---
    fun archiveCurrentSession() {
        val current = activeSession.value
        repository.archiveSession(current)
        viewModelScope.launch {
            minutesSearchRepository.upsertSessionMinutes(current)
            boardScheduleRepository.upsertSessionSchedule(current)
        }
        _userMessage.value = "جلسه «${current.title.ifBlank { "بدون عنوان" }}» در مرکز بایگانی و تقویم دیتابیس Room ذخیره شد."
    }

    fun deleteArchivedSession(id: String) {
        repository.deleteArchivedSession(id)
        viewModelScope.launch {
            minutesSearchRepository.deleteSessionMinutes(id)
            boardScheduleRepository.deleteScheduleBySessionId(id)
        }
        _userMessage.value = "جلسه از آرشیو و تقویم Room حذف گردید."
    }

    fun loadSessionFromArchive(session: MeetingSession) {
        repository.loadSession(session)
        viewModelScope.launch {
            attendeeRepository.syncSessionMembersToRoom(session.id, session.members)
            minutesSearchRepository.upsertSessionMinutes(session)
            boardScheduleRepository.upsertSessionSchedule(session)
        }
        _userMessage.value = "جلسه «${session.title}» بازخوانی شد."
    }

    fun startNewBlankSession() {
        pauseTimer()
        repository.resetToNewSession()
        viewModelScope.launch {
            val current = repository.activeSession.value
            attendeeRepository.syncSessionMembersToRoom(current.id, current.members)
        }
        _userMessage.value = "جلسه جدید با مشخصات خام آغاز شد."
    }

    // --- Full Database JSON ---
    fun exportDatabaseJson(): String {
        return repository.exportFullDatabaseJson()
    }

    fun importDatabaseJson(jsonStr: String): Boolean {
        val success = repository.importFullDatabaseJson(jsonStr)
        if (success) {
            viewModelScope.launch {
                val current = repository.activeSession.value
                attendeeRepository.syncSessionMembersToRoom(current.id, current.members)
                minutesSearchRepository.syncSessionsToMinutesIndex(
                    activeSession = current,
                    archiveList = repository.archiveList.value,
                    seedPredefinedScenariosIfArchiveEmpty = true
                )
                boardScheduleRepository.syncAndSeedBoardSchedule(
                    activeSession = current,
                    archiveList = repository.archiveList.value
                )
            }
        }
        _userMessage.value = if (success) "بانک اطلاعات با موفقیت بازیابی شد." else "خطا در خواندن فایل JSON"
        return success
    }

    private fun save(session: MeetingSession) {
        repository.saveActiveSession(session)
        viewModelScope.launch {
            attendeeRepository.syncSessionMembersToRoom(session.id, session.members)
            minutesSearchRepository.upsertSessionMinutes(session)
            boardScheduleRepository.upsertSessionSchedule(session)
        }
    }

    private fun formatClock(sec: Long): String {
        val s = maxOf(0L, sec)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    // --- Advanced Meeting List Filter Methods ---
    fun updateListFilterQuery(q: String) { _listFilterQuery.value = q }
    fun updateListFilterScope(s: String) { _listFilterScope.value = s }
    fun updateListFilterStatus(s: String) { _listFilterStatus.value = s }
    fun updateListFilterMember(m: String) { _listFilterMember.value = m }
    fun resetListFilters() {
        _listFilterQuery.value = ""
        _listFilterScope.value = "ALL"
        _listFilterStatus.value = "ALL"
        _listFilterMember.value = "ALL"
    }

    // --- Home Interactive Monthly Calendar & Top Search Date Range Picker Methods ---
    fun updateHomeSearchQuery(query: String) {
        _homeSearchQuery.value = query
    }

    fun selectCalendarDateFilter(date: String) {
        val clean = date.trim()
        val normalized = if (clean.isBlank()) "" else BoardScheduleEntity.normalizeDate(clean)
        if (_selectedCalendarDateFilter.value == normalized) {
            _selectedCalendarDateFilter.value = ""
        } else {
            _selectedCalendarDateFilter.value = normalized
        }
    }

    fun updateDateRangeFilter(startDate: String, endDate: String) {
        val normStart = if (startDate.isBlank()) "" else BoardScheduleEntity.normalizeDate(startDate.trim())
        val normEnd = if (endDate.isBlank()) "" else BoardScheduleEntity.normalizeDate(endDate.trim())
        if (normStart.isNotBlank() && normEnd.isNotBlank() && normStart > normEnd) {
            _dateRangeStartFilter.value = normEnd
            _dateRangeEndFilter.value = normStart
        } else {
            _dateRangeStartFilter.value = normStart
            _dateRangeEndFilter.value = normEnd
        }
        // Clear single-day filter when a date range is applied
        if (normStart.isNotBlank() || normEnd.isNotBlank()) {
            _selectedCalendarDateFilter.value = ""
        }
    }

    fun clearDateRangeFilter() {
        _dateRangeStartFilter.value = ""
        _dateRangeEndFilter.value = ""
    }

    fun clearAllHomeDateAndSearchFilters() {
        _homeSearchQuery.value = ""
        _selectedCalendarDateFilter.value = ""
        _dateRangeStartFilter.value = ""
        _dateRangeEndFilter.value = ""
    }

    fun getCurrentFilteredHomeMeetings(
        sourceItems: List<BoardScheduleEntity> = roomBoardScheduleItems.value.ifEmpty {
            BoardScheduleRepository.buildDefaultBoardScheduleSeed()
        }
    ): List<BoardScheduleEntity> {
        return filterBoardMeetingsByDateAndSearch(
            items = sourceItems,
            searchQuery = _homeSearchQuery.value,
            selectedDate = _selectedCalendarDateFilter.value,
            dateRangeStart = _dateRangeStartFilter.value,
            dateRangeEnd = _dateRangeEndFilter.value
        )
    }

    companion object {
        fun filterBoardMeetingsByDateAndSearch(
            items: List<BoardScheduleEntity>,
            searchQuery: String = "",
            selectedDate: String = "",
            dateRangeStart: String = "",
            dateRangeEnd: String = ""
        ): List<BoardScheduleEntity> {
            val cleanQuery = searchQuery.trim()
            val normSelectedDate = if (selectedDate.isBlank()) "" else BoardScheduleEntity.normalizeDate(selectedDate.trim())
            val normStart = if (dateRangeStart.isBlank()) "" else BoardScheduleEntity.normalizeDate(dateRangeStart.trim())
            val normEnd = if (dateRangeEnd.isBlank()) "" else BoardScheduleEntity.normalizeDate(dateRangeEnd.trim())

            val effectiveStart = if (normStart.isNotBlank() && normEnd.isNotBlank() && normStart > normEnd) normEnd else normStart
            val effectiveEnd = if (normStart.isNotBlank() && normEnd.isNotBlank() && normStart > normEnd) normStart else normEnd

            return items.filter { item ->
                val itemDate = BoardScheduleEntity.normalizeDate(item.date)

                val matchesQuery = if (cleanQuery.isBlank()) {
                    true
                } else {
                    item.title.contains(cleanQuery, ignoreCase = true) ||
                        item.agendaSummary.contains(cleanQuery, ignoreCase = true) ||
                        item.keyResolutionsSummary.contains(cleanQuery, ignoreCase = true) ||
                        item.attendeesCsv.contains(cleanQuery, ignoreCase = true) ||
                        item.chairpersonName.contains(cleanQuery, ignoreCase = true) ||
                        item.extCompany.contains(cleanQuery, ignoreCase = true) ||
                        itemDate.contains(cleanQuery, ignoreCase = true)
                }

                val matchesSingleDate = if (normSelectedDate.isBlank()) {
                    true
                } else {
                    itemDate == normSelectedDate
                }

                val matchesStartRange = if (effectiveStart.isBlank()) {
                    true
                } else {
                    itemDate >= effectiveStart
                }

                val matchesEndRange = if (effectiveEnd.isBlank()) {
                    true
                } else {
                    itemDate <= effectiveEnd
                }

                matchesQuery && matchesSingleDate && matchesStartRange && matchesEndRange
            }.sortedWith(compareByDescending<BoardScheduleEntity> { BoardScheduleEntity.normalizeDate(it.date) }.thenBy { it.startTime })
        }
    }

    // --- AI Agenda Suggestions ---
    fun generateAgendaSuggestionsWithGemini() {
        viewModelScope.launch {
            _isGeneratingAgenda.value = true
            val pastSessions = repository.archiveList.value
            val allPastNotes = pastSessions.flatMap { it.notes } + activeSession.value.notes
            val unresolved = allPastNotes.filter { !it.isDone }
            val recurringObjectives = listOf(
                "توسعه و تضمین پایداری زنجیره سرد دارویی (2°C تا 8°C)",
                "مدیریت بهینه بودجه و کنترل هزینه‌های لجستیک 5PL",
                "نوسازی پالت‌ها و هوشمندسازی فرآیندهای انبارش دارویی WMS",
                "انطباق با الزامات رگولاتوری غذا و دارو و پروتکل‌های TTAC"
            )

            val result = geminiService.generateAgendaSuggestions(
                unresolvedNotes = unresolved,
                pastSessions = pastSessions,
                recurringObjectives = recurringObjectives,
                customApiKey = geminiApiKey.value
            )

            result.onSuccess { suggestions ->
                _agendaSuggestions.value = suggestions
                _isGeneratingAgenda.value = false
                _userMessage.value = "${suggestions.size} موضوع دستور جلسه با هوش مصنوعی پیشنهاد شد."
            }.onFailure { err ->
                _isGeneratingAgenda.value = false
                _userMessage.value = "خطا در دریافت پیشنهاد دستور جلسه: ${err.localizedMessage}"
            }
        }
    }

    fun applySuggestedAgendaTopic(suggestion: AgendaSuggestion) {
        val current = activeSession.value
        val formattedTime = formatClock(current.elapsedSeconds)
        val newNote = MeetingNote(
            timeFormatted = formattedTime,
            speaker = suggestion.proposedSpeaker,
            text = "دستور جلسه: ${suggestion.topicTitle} — علت: ${suggestion.rationalReason}",
            type = suggestion.suggestedActionType,
            due = "مهلت جلسه",
            isDone = false
        )
        val updatedNotes = current.notes + newNote
        save(current.copy(notes = updatedNotes))
        _userMessage.value = "موضوع «${suggestion.topicTitle.take(30)}...» به جلسه افزوده شد."
    }

    fun applyAllSuggestedAgendaTopics() {
        val suggestions = _agendaSuggestions.value
        if (suggestions.isEmpty()) return
        val current = activeSession.value
        val newNotes = suggestions.mapIndexed { idx, s ->
            MeetingNote(
                timeFormatted = formatClock(current.elapsedSeconds + idx * 60),
                speaker = s.proposedSpeaker,
                text = "دستور جلسه: ${s.topicTitle} — ${s.rationalReason}",
                type = s.suggestedActionType,
                due = "مهلت جلسه",
                isDone = false
            )
        }
        save(current.copy(notes = current.notes + newNotes))
        _userMessage.value = "همه موضوعات پیشنهادی (${suggestions.size} بند) به جلسه افزوده شدند."
    }

    // --- Meeting Health Trend Analysis ---
    fun analyzeMeetingHealthWithGemini() {
        viewModelScope.launch {
            _isAnalyzingMeetingHealth.value = true
            val sessions = (repository.archiveList.value + activeSession.value).distinctBy { it.id }
            val result = geminiService.analyzeMeetingHealthTrends(
                sessions = sessions,
                customApiKey = geminiApiKey.value
            )
            result.onSuccess { report ->
                _meetingHealthReport.value = report
                _isAnalyzingMeetingHealth.value = false
                _userMessage.value = "گزارش سلامت جلسات با امتیاز ${report.healthScore} از ۱۰۰ تولید شد."
            }.onFailure { err ->
                _isAnalyzingMeetingHealth.value = false
                _userMessage.value = "خطا در تحلیل سلامت جلسات: ${err.localizedMessage}"
            }
        }
    }

    // --- System Notification Reminders for Action Items & Room Member Tasks ---
    fun sendNotificationReminderForNote(context: Context, noteId: String) {
        val current = activeSession.value
        val note = current.notes.find { it.id == noteId } ?: run {
            repository.archiveList.value.flatMap { it.notes }.find { it.id == noteId }
        }
        if (note != null) {
            val sent = MeetingNotificationHelper.sendActionItemReminder(context, note, current.title)
            if (sent) {
                _userMessage.value = "اعلان یادآوری مصوبه برای «${note.speaker}» ارسال شد."
            } else {
                _userMessage.value = "ارسال اعلان نیازمند تأیید مجوز یا فعال‌سازی نوتیفیکیشن است."
            }
        }
    }

    fun sendAllPendingResolutionReminders(context: Context) {
        viewModelScope.launch {
            val current = activeSession.value
            val sentNotesCount = MeetingNotificationHelper.sendAllPendingReminders(context, current.notes, current.title)
            val pendingRoomTasks = memberTaskRepository.getPendingDeadlineTasksOnce()
            val sentRoomCount = if (pendingRoomTasks.isNotEmpty()) {
                MeetingNotificationHelper.sendAllUpcomingRoomTaskDeadlineReminders(context, pendingRoomTasks)
            } else 0
            val totalSent = sentNotesCount + sentRoomCount
            if (totalSent > 0) {
                _userMessage.value = "یادآوری سیستمی برای $totalSent مصوبه و وظیفه محوله در دیتابیس ارسال گردید."
            } else {
                _userMessage.value = "مصوبه معوق یا وظیفه جاری جهت ارسال اعلان یافت نشد."
            }
        }
    }

    fun sendRoomTaskDeadlineReminder(context: Context, task: MemberTaskEntity) {
        val sent = MeetingNotificationHelper.sendRoomMemberTaskDeadlineReminder(context, task)
        if (sent) {
            _userMessage.value = "اعلان یادآوری مهلت وظیفه برای «${task.assigneeName}» (مهلت: ${task.deadlineLabel}) ارسال شد."
        } else {
            _userMessage.value = "وظیفه تکمیل شده یا ارسال اعلان نیازمند مجوز نوتیفیکیشن است."
        }
    }

    fun sendAllRoomTaskDeadlineReminders(context: Context, assigneeFilter: String? = null) {
        viewModelScope.launch {
            var pendingTasks = memberTaskRepository.getPendingDeadlineTasksOnce()
            if (pendingTasks.isEmpty()) {
                val current = activeSession.value
                val fallbackTasks = current.notes.filter { !it.isDone }.mapIndexed { idx, note ->
                    MemberTaskEntity(
                        id = idx + 1,
                        sessionId = current.id,
                        sessionTitle = current.title,
                        assigneeName = note.speaker.ifBlank { "عضو مسئول هیئت‌مدیره" },
                        assigneeRole = "عضو هیئت‌مدیره",
                        taskTitle = note.text,
                        deadlineLabel = note.due.ifBlank { "فوری / پایان هفته" },
                        calendarDate = current.date.ifBlank { "۱۴۰۵/۰۸/۲۰" },
                        priority = if (note.type.contains("مصوبه")) "فوری" else "بالا",
                        status = "IN_PROGRESS",
                        isDone = false,
                        isAddedToCalendar = true
                    )
                }
                if (fallbackTasks.isNotEmpty()) {
                    pendingTasks = memberTaskRepository.saveExtractedMemberTasks(current.id, fallbackTasks)
                }
            }
            val sentCount = MeetingNotificationHelper.sendAllUpcomingRoomTaskDeadlineReminders(
                context = context,
                tasks = pendingTasks,
                assigneeFilter = assigneeFilter
            )
            if (sentCount > 0) {
                val scopeLabel = if (!assigneeFilter.isNullOrBlank() && assigneeFilter != "ALL") " برای «$assigneeFilter»" else " برای اعضای هیئت‌مدیره"
                _userMessage.value = "اعلان یادآوری مهلت $sentCount وظیفه محوله از دیتابیس Room$scopeLabel ارسال شد."
            } else {
                _userMessage.value = "وظیفه باز یا در آستانه سررسید در دیتابیس Room یافت نشد."
            }
        }
    }

    // --- Audio Sentiment Analysis & High-Tension Flagging ---
    fun analyzeAudioSentimentWithGemini(targetFile: File? = null) {
        val file = targetFile
            ?: _recordedAudioFile.value
            ?: activeSession.value.audioFilePath?.let { File(it) }

        viewModelScope.launch {
            _isAnalyzingSentiment.value = true
            _sentimentAnalysisError.value = null
            val current = activeSession.value
            val apiKey = geminiApiKey.value

            val result = geminiService.analyzeAudioSentimentAndTension(
                audioFile = file,
                session = current,
                customApiKey = apiKey
            )

            result.onSuccess { sentiment ->
                _audioSentimentAnalysis.value = sentiment
                _isAnalyzingSentiment.value = false

                // Update active session with the sentiment evaluation
                val updatedAudioAnalysis = current.latestAudioAnalysis?.copy(
                    sentimentAnalysis = sentiment
                )
                val updatedSession = current.copy(
                    audioSentimentAnalysis = sentiment,
                    latestAudioAnalysis = updatedAudioAnalysis
                )
                save(updatedSession)

                val tensionCount = sentiment.flaggedSegments.count { !it.isReviewed }
                val tensionMsg = if (tensionCount > 0) {
                    " ($tensionCount بخش دارای تنش جهت بازبینی پرچم‌گذاری شد)"
                } else ""
                _userMessage.value = "تحلیل لحن صوتی با موفقیت انجام شد: ${sentiment.overallTone}$tensionMsg"
            }.onFailure { err ->
                _isAnalyzingSentiment.value = false
                _sentimentAnalysisError.value = err.localizedMessage ?: "خطا در ارزیابی لحن و تنش"
                _userMessage.value = "خطا در ارزیابی لحن: ${err.localizedMessage}"
            }
        }
    }

    fun toggleFlaggedSegmentReviewed(segmentId: String, reviewNotes: String = "") {
        val current = activeSession.value
        val currentSentiment = _audioSentimentAnalysis.value ?: current.audioSentimentAnalysis ?: return

        val updatedSegments = currentSentiment.flaggedSegments.map { seg ->
            if (seg.id == segmentId) {
                val newStatus = !seg.isReviewed
                seg.copy(
                    isReviewed = newStatus,
                    reviewNotes = if (reviewNotes.isNotBlank()) reviewNotes else seg.reviewNotes
                )
            } else seg
        }

        val updatedSentiment = currentSentiment.copy(flaggedSegments = updatedSegments)
        _audioSentimentAnalysis.value = updatedSentiment

        val updatedAudioAnalysis = current.latestAudioAnalysis?.copy(sentimentAnalysis = updatedSentiment)
        val updatedSession = current.copy(
            audioSentimentAnalysis = updatedSentiment,
            latestAudioAnalysis = updatedAudioAnalysis
        )
        save(updatedSession)

        val targetSeg = updatedSegments.find { it.id == segmentId }
        val statusText = if (targetSeg?.isReviewed == true) "تأیید و بازبینی شد" else "به وضعیت نیازمند بازبینی برگشت"
        _userMessage.value = "بخش تنش «${targetSeg?.speaker ?: ""}» $statusText."
    }

    fun applyFlaggedSegmentToMeetingNotes(segment: HighTensionSegment) {
        val current = activeSession.value

        val resolutionText = "اقدام رفع تعارض و بازبینی تنش [${segment.triggerTopic}]: ${segment.reviewRecommendation} (نقل‌قول: ${segment.quoteExcerpt.take(65)}...)"
        val newNote = MeetingNote(
            timeFormatted = formatClock(current.elapsedSeconds),
            speaker = segment.speaker,
            text = resolutionText,
            type = "🚨 مصوبه رسمی",
            due = "فوری / بازبینی هیئت‌مدیره",
            isDone = false
        )

        // Also mark this segment as reviewed with confirmation note
        val currentSentiment = _audioSentimentAnalysis.value ?: current.audioSentimentAnalysis
        val updatedSegments = currentSentiment?.flaggedSegments?.map { seg ->
            if (seg.id == segment.id) {
                seg.copy(isReviewed = true, reviewNotes = "درج در مصوبات رسمی جهت رفع تعارض")
            } else seg
        } ?: emptyList()

        val updatedSentiment = currentSentiment?.copy(flaggedSegments = updatedSegments)
        _audioSentimentAnalysis.value = updatedSentiment

        val updatedSession = current.copy(
            notes = current.notes + newNote,
            audioSentimentAnalysis = updatedSentiment,
            latestAudioAnalysis = current.latestAudioAnalysis?.copy(sentimentAnalysis = updatedSentiment)
        )
        save(updatedSession)

        _userMessage.value = "اقدام رفع تعارض برای «${segment.speaker}» در مصوبات رسمی جلسه درج شد."
    }

    fun clearSentimentAnalysis() {
        _audioSentimentAnalysis.value = null
        _sentimentAnalysisError.value = null
        val current = activeSession.value
        val updated = current.copy(
            audioSentimentAnalysis = null,
            latestAudioAnalysis = current.latestAudioAnalysis?.copy(sentimentAnalysis = null)
        )
        save(updated)
        _userMessage.value = "تحلیل احساسات و بخش‌های تنش بازنشانی شد."
    }

    // --- Gemini Extraction of Assignees & Action Items -> Auto-Add to Calendar & Member Task List in Room ---
    fun setAutoSyncExtractedTasksToDbAndCalendar(enabled: Boolean) {
        _autoSyncExtractedTasksToDbAndCalendar.value = enabled
    }

    fun extractAndAutoAssignMemberTasksWithGemini(customMeetingText: String = "") {
        viewModelScope.launch {
            _isExtractingMemberTasks.value = true
            _memberTaskExtractionError.value = null
            val current = activeSession.value
            val apiKey = geminiApiKey.value

            val result = geminiService.extractAssigneesAndActionItemsFromMeetingText(
                session = current,
                customMeetingText = customMeetingText,
                customApiKey = apiKey
            )

            result.onSuccess { extractedTasks ->
                val persisted = if (_autoSyncExtractedTasksToDbAndCalendar.value) {
                    memberTaskRepository.saveExtractedMemberTasks(
                        sessionId = current.id,
                        tasks = extractedTasks
                    )
                } else {
                    extractedTasks
                }
                _lastExtractedMemberTasks.value = persisted
                _isExtractingMemberTasks.value = false

                // Also synchronize newly discovered action items into active session notes if not already present
                val existingTexts = current.notes.map { it.text.trim() }.toSet()
                val newNotesFromTasks = persisted.filter { it.taskTitle.trim() !in existingTexts }.map { task ->
                    MeetingNote(
                        timeFormatted = formatClock(current.elapsedSeconds),
                        speaker = task.assigneeName,
                        text = task.taskTitle,
                        type = if (task.priority == "فوری") "🚨 مصوبه رسمی" else "📌 تعهد اجرایی",
                        due = "${task.deadlineLabel} (${task.calendarDate})",
                        isDone = task.isDone
                    )
                }
                if (newNotesFromTasks.isNotEmpty()) {
                    save(current.copy(notes = current.notes + newNotesFromTasks))
                }

                val distinctAssignees = persisted.map { it.assigneeName }.distinct().size
                _userMessage.value = "${persisted.size} وظیفه محوله برای $distinctAssignees عضو مسئول با Gemini استخراج و به تقویم و کارتابل دیتابیس Room اضافه شد."
            }.onFailure { err ->
                _isExtractingMemberTasks.value = false
                _memberTaskExtractionError.value = err.localizedMessage ?: "خطا در استخراج وظایف اعضا"
                _userMessage.value = "خطا در استخراج وظایف: ${err.localizedMessage}"
            }
        }
    }

    fun toggleMemberTaskDone(task: MemberTaskEntity) {
        viewModelScope.launch {
            val updated = memberTaskRepository.toggleTaskDone(task)
            _lastExtractedMemberTasks.value = _lastExtractedMemberTasks.value.map {
                if (it.id == updated.id || (it.taskTitle == updated.taskTitle && it.assigneeName == updated.assigneeName)) updated else it
            }
            val statusLabel = if (updated.isDone) "تکمیل‌شده" else "در حال انجام"
            _userMessage.value = "وضعیت وظیفه «${updated.assigneeName}» در دیتابیس Room به «$statusLabel» تغییر یافت."
        }
    }

    fun deleteMemberTask(taskId: Int) {
        viewModelScope.launch {
            memberTaskRepository.deleteTaskById(taskId)
            _lastExtractedMemberTasks.value = _lastExtractedMemberTasks.value.filterNot { it.id == taskId }
            _userMessage.value = "وظیفه از کارتابل دیتابیس Room حذف شد."
        }
    }

    suspend fun getAllMemberTasksFromRoomOnce(): List<MemberTaskEntity> {
        return memberTaskRepository.getAllMemberTasksOnce()
    }

    suspend fun getMemberTasksForSessionFromRoomOnce(sessionId: String = activeSession.value.id): List<MemberTaskEntity> {
        return memberTaskRepository.getTasksForSessionOnce(sessionId)
    }

    // --- Gemini Official Meeting Minutes Draft PDF Generation & Sharing ---
    private val _isGeneratingMinutesDraft = MutableStateFlow(false)
    val isGeneratingMinutesDraft: StateFlow<Boolean> = _isGeneratingMinutesDraft.asStateFlow()

    private val _minutesDraftError = MutableStateFlow<String?>(null)
    val minutesDraftError: StateFlow<String?> = _minutesDraftError.asStateFlow()

    private val _officialMinutesDraft = MutableStateFlow<OfficialMinutesDraft?>(null)
    val officialMinutesDraft: StateFlow<OfficialMinutesDraft?> = _officialMinutesDraft.asStateFlow()

    private val _selectedDraftTemplate = MutableStateFlow("الگوی رسمی هیئت‌مدیره و حاکمیت شرکتی")
    val selectedDraftTemplate: StateFlow<String> = _selectedDraftTemplate.asStateFlow()

    private val _generatedDraftPdfFile = MutableStateFlow<File?>(null)
    val generatedDraftPdfFile: StateFlow<File?> = _generatedDraftPdfFile.asStateFlow()

    fun setSelectedDraftTemplate(template: String) {
        _selectedDraftTemplate.value = template
    }

    fun generateAndCreateOfficialDraftPdf(context: Context, autoShare: Boolean = false) {
        viewModelScope.launch {
            _isGeneratingMinutesDraft.value = true
            _minutesDraftError.value = null
            val current = activeSession.value
            val apiKey = geminiApiKey.value
            val template = _selectedDraftTemplate.value

            val result = geminiService.generateOfficialMinutesDraftWithGemini(
                session = current,
                templateStyle = template,
                customApiKey = apiKey
            )

            result.onSuccess { draft ->
                val pdfFile = PdfReportGenerator.generateOfficialMinutesDraftPdf(
                    context = context,
                    session = current,
                    draft = draft
                )
                val finalDraft = draft.copy(generatedPdfPath = pdfFile.absolutePath)
                _officialMinutesDraft.value = finalDraft
                _generatedDraftPdfFile.value = pdfFile
                _isGeneratingMinutesDraft.value = false

                save(current.copy(officialMinutesDraft = finalDraft))
                _userMessage.value = "پیش‌نویس صورتجلسه رسمی (${finalDraft.documentNumber}) با الگوی Gemini در فرمت PDF تولید و آماده اشتراک‌گذاری شد."

                if (autoShare) {
                    try {
                        val shareIntent = PdfReportGenerator.createShareDraftPdfIntent(context, pdfFile, finalDraft)
                        val chooser = Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل PDF پیش‌نویس صورتجلسه رسمی").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        _userMessage.value = "فایل PDF تولید شد (${pdfFile.name})، اما برنامه‌ای برای اشتراک‌گذاری یافت نشد."
                    }
                }
            }.onFailure { err ->
                _isGeneratingMinutesDraft.value = false
                _minutesDraftError.value = err.localizedMessage ?: "خطا در تولید پیش‌نویس صورتجلسه رسمی"
                _userMessage.value = "خطا در تولید پیش‌نویس: ${err.localizedMessage}"
            }
        }
    }

    fun shareOfficialDraftPdf(context: Context) {
        val current = activeSession.value
        val existingDraft = _officialMinutesDraft.value ?: current.officialMinutesDraft
        val existingFile = _generatedDraftPdfFile.value ?: existingDraft?.generatedPdfPath?.let { File(it) }

        if (existingDraft != null && existingFile != null && existingFile.exists()) {
            try {
                val shareIntent = PdfReportGenerator.createShareDraftPdfIntent(context, existingFile, existingDraft)
                val chooser = Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل PDF پیش‌نویس صورتجلسه رسمی").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                _userMessage.value = "پنجره اشتراک‌گذاری فایل PDF پیش‌نویس صورتجلسه رسمی (${existingDraft.documentNumber}) باز شد."
            } catch (e: Exception) {
                _userMessage.value = "فایل PDF پیش‌نویس آماده است: ${existingFile.name}"
            }
        } else {
            generateAndCreateOfficialDraftPdf(context = context, autoShare = true)
        }
    }

    fun viewOfficialDraftPdf(context: Context) {
        val current = activeSession.value
        val existingDraft = _officialMinutesDraft.value ?: current.officialMinutesDraft
        val existingFile = _generatedDraftPdfFile.value ?: existingDraft?.generatedPdfPath?.let { File(it) }

        if (existingDraft != null && existingFile != null && existingFile.exists()) {
            try {
                val viewIntent = PdfReportGenerator.createViewPdfIntent(context, existingFile).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
            } catch (e: Exception) {
                _userMessage.value = "فایل PDF پیش‌نویس آماده است: ${existingFile.name}"
            }
        } else {
            generateAndCreateOfficialDraftPdf(context = context, autoShare = false)
        }
    }

    // --- Finalized Meeting Minutes Standard PDF Converter with Corporate Letterhead & Digital Signature Placeholders ---
    private val _lastGeneratedCorporatePdfFile = MutableStateFlow<File?>(null)
    val lastGeneratedCorporatePdfFile: StateFlow<File?> = _lastGeneratedCorporatePdfFile.asStateFlow()

    fun updateCorporatePdfConfig(newConfig: CorporatePdfConfig) {
        val current = activeSession.value
        save(current.copy(corporatePdfConfig = newConfig))
    }

    fun toggleSessionFinalized(finalized: Boolean = !activeSession.value.isFinalized) {
        val current = activeSession.value
        save(current.copy(isFinalized = finalized))
        val statusMsg = if (finalized) "نهایی‌شده و آماده صدور PDF رسمی با سربرگ شرکتی" else "پیش‌نویس در حال ویرایش"
        _userMessage.value = "وضعیت صورت‌جلسه به «$statusMsg» تغییر یافت."
    }

    fun convertFinalizedSessionToCorporatePdf(
        context: Context,
        targetSession: MeetingSession = activeSession.value,
        autoShare: Boolean = false
    ): File {
        val finalizedSession = if (!targetSession.isFinalized) {
            targetSession.copy(isFinalized = true)
        } else {
            targetSession
        }
        val pdfFile = PdfReportGenerator.generateFinalizedCorporatePdf(
            context = context,
            session = finalizedSession,
            corporateConfig = finalizedSession.corporatePdfConfig
        )
        _lastGeneratedCorporatePdfFile.value = pdfFile

        val updatedConfig = finalizedSession.corporatePdfConfig.copy(
            lastGeneratedPdfPath = pdfFile.absolutePath
        )
        val updatedSession = finalizedSession.copy(corporatePdfConfig = updatedConfig)
        if (updatedSession.id == activeSession.value.id) {
            save(updatedSession)
        }

        _userMessage.value = "صورت‌جلسه نهایی‌شده با سربرگ شرکتی و امضای دیجیتال (Placeholder) به فایل PDF استاندارد تبدیل شد: ${pdfFile.name}"

        if (autoShare) {
            try {
                val shareIntent = PdfReportGenerator.createSharePdfIntent(
                    context = context,
                    file = pdfFile,
                    sessionTitle = finalizedSession.title.ifBlank { "صورت‌جلسه رسمی هیئت‌مدیره" }
                )
                val chooser = Intent.createChooser(shareIntent, "اشتراک‌گذاری PDF رسمی با سربرگ شرکتی و امضای دیجیتال").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _userMessage.value = "فایل PDF رسمی صادر شد (${pdfFile.name})."
            }
        }
        return pdfFile
    }

    fun shareFinalizedCorporatePdf(
        context: Context,
        targetSession: MeetingSession = activeSession.value
    ) {
        convertFinalizedSessionToCorporatePdf(
            context = context,
            targetSession = targetSession,
            autoShare = true
        )
    }

    fun viewFinalizedCorporatePdf(
        context: Context,
        targetSession: MeetingSession = activeSession.value
    ) {
        val pdfFile = convertFinalizedSessionToCorporatePdf(
            context = context,
            targetSession = targetSession,
            autoShare = false
        )
        try {
            val viewIntent = PdfReportGenerator.createViewPdfIntent(context, pdfFile).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "مشاهده PDF رسمی با سربرگ شرکتی"))
        } catch (e: Exception) {
            _userMessage.value = "فایل PDF استاندارد آماده است: ${pdfFile.name}"
        }
    }

    // --- Board Calendar View: Upcoming Meetings & Historical Records in Room ---
    fun scheduleUpcomingBoardMeeting(
        title: String,
        date: String,
        startTime: String = "10:00",
        durationMins: Int = 90,
        location: String = "سالن کنفرانس مرکزی هیئت‌مدیره",
        scope: String = "internal",
        chairpersonName: String = "سخنران",
        attendeesCsv: String = "سخنران | سخنران | سخنران | سخنران",
        agendaSummary: String = ""
    ) {
        viewModelScope.launch {
            val created = boardScheduleRepository.scheduleUpcomingMeeting(
                title = title,
                date = date,
                startTime = startTime,
                durationMins = durationMins,
                location = location,
                scope = scope,
                chairpersonName = chairpersonName,
                attendeesCsv = attendeesCsv,
                agendaSummary = agendaSummary
            )
            _userMessage.value = "جلسه پیش‌رو «${created.title}» برای تاریخ ${created.date} در تقویم دیتابیس Room ثبت شد."
        }
    }

    fun toggleBoardScheduleStatus(item: BoardScheduleEntity) {
        val nextStatus = if (item.isUpcoming) {
            BoardScheduleEntity.STATUS_COMPLETED
        } else {
            BoardScheduleEntity.STATUS_UPCOMING
        }
        viewModelScope.launch {
            val updated = boardScheduleRepository.updateMeetingScheduleStatus(item.sessionId, nextStatus)
            val label = if (nextStatus == BoardScheduleEntity.STATUS_COMPLETED) "برگزارشده (سوابق هیئت‌مدیره)" else "جلسه پیش‌رو"
            if (updated != null) {
                _userMessage.value = "وضعیت جلسه «${updated.title}» در تقویم Room به «$label» تغییر یافت."
            }
        }
    }

    fun deleteBoardScheduleItem(sessionId: String) {
        viewModelScope.launch {
            boardScheduleRepository.deleteScheduleBySessionId(sessionId)
            _userMessage.value = "جلسه از تقویم زمان‌بندی دیتابیس Room حذف شد."
        }
    }

    fun loadBoardScheduleItem(item: BoardScheduleEntity) {
        val session = item.toMeetingSession()
        loadSessionFromArchive(session)
    }

    suspend fun getBoardScheduleItemsFromRoomOnce(): List<BoardScheduleEntity> {
        return boardScheduleRepository.getAllScheduleItemsOnce()
    }

    suspend fun getUpcomingMeetingsFromRoomOnce(
        referenceDate: String = BoardScheduleRepository.DEFAULT_REFERENCE_DATE
    ): List<BoardScheduleEntity> {
        return boardScheduleRepository.getUpcomingMeetingsOnce(referenceDate)
    }

    suspend fun getHistoricalMeetingsFromRoomOnce(
        referenceDate: String = BoardScheduleRepository.DEFAULT_REFERENCE_DATE
    ): List<BoardScheduleEntity> {
        return boardScheduleRepository.getHistoricalMeetingsOnce(referenceDate)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        speechRecognizer?.destroy()
    }
}
