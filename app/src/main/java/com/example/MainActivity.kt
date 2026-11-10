package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AtlasHeader
import com.example.ui.components.AtlasTab
import com.example.ui.components.AtlasTabNavigation
import com.example.ui.components.OwnerSignatureFooter
import com.example.ui.components.VoiceRecordingBottomSheet
import com.example.ui.components.VoiceRecordingFab
import com.example.ui.screens.*
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MeetingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AtlasMainScreen()
                }
            }
        }
    }
}

@Composable
fun AtlasMainScreen(viewModel: MeetingViewModel = viewModel()) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AtlasTab.SETUP) }
    var showVoiceRecordingSheet by remember { mutableStateOf(false) }

    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val roomAttendees by viewModel.roomAttendees.collectAsStateWithLifecycle()
    val archiveList by viewModel.archiveList.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val isAudioRecording by viewModel.isAudioRecording.collectAsStateWithLifecycle()
    val recordingDurationSec by viewModel.recordingDurationSec.collectAsStateWithLifecycle()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsStateWithLifecycle()
    val micInputSensitivity by viewModel.micInputSensitivity.collectAsStateWithLifecycle()
    val silenceDetectionStatus by viewModel.silenceDetectionStatus.collectAsStateWithLifecycle()
    val recordedAudioFile by viewModel.recordedAudioFile.collectAsStateWithLifecycle()
    val isAudioProcessingWithGemini by viewModel.isAudioProcessingWithGemini.collectAsStateWithLifecycle()
    val audioProcessingError by viewModel.audioProcessingError.collectAsStateWithLifecycle()
    val audioMeetingAnalysis by viewModel.audioMeetingAnalysis.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isContinuousTranscribing by viewModel.isContinuousTranscribing.collectAsStateWithLifecycle()
    val interimTranscript by viewModel.interimTranscript.collectAsStateWithLifecycle()
    val audioRmsLevel by viewModel.audioRmsLevel.collectAsStateWithLifecycle()
    val speechLanguage by viewModel.speechLanguage.collectAsStateWithLifecycle()
    val activeTranscribingSpeaker by viewModel.activeTranscribingSpeaker.collectAsStateWithLifecycle()
    val liveTypedMinutesBuffer by viewModel.liveTypedMinutesBuffer.collectAsStateWithLifecycle()
    val autoCommitSpeechToNotes by viewModel.autoCommitSpeechToNotes.collectAsStateWithLifecycle()
    val autoAnalyzeTypedMinutesWithGemini by viewModel.autoAnalyzeTypedMinutesWithGemini.collectAsStateWithLifecycle()
    val isAnalyzingTypedMinutes by viewModel.isAnalyzingTypedMinutes.collectAsStateWithLifecycle()
    val typedMinutesAnalysis by viewModel.typedMinutesAnalysis.collectAsStateWithLifecycle()
    val typedMinutesAnalysisError by viewModel.typedMinutesAnalysisError.collectAsStateWithLifecycle()
    val speechError by viewModel.speechError.collectAsStateWithLifecycle()
    val isTranscriptionAiLoading by viewModel.isTranscriptionAiLoading.collectAsStateWithLifecycle()
    val transcriptionAiAnalysis by viewModel.transcriptionAiAnalysis.collectAsStateWithLifecycle()
    val transcriptionAiError by viewModel.transcriptionAiError.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    val isSearchingStandards by viewModel.isSearchingStandards.collectAsStateWithLifecycle()
    val searchStandardsResult by viewModel.searchStandardsResult.collectAsStateWithLifecycle()
    val searchStandardsError by viewModel.searchStandardsError.collectAsStateWithLifecycle()
    val ftsSearchQuery by viewModel.ftsSearchQuery.collectAsStateWithLifecycle()
    val ftsScopeFilter by viewModel.ftsScopeFilter.collectAsStateWithLifecycle()
    val ftsStatusFilter by viewModel.ftsStatusFilter.collectAsStateWithLifecycle()
    val ftsMemberFilter by viewModel.ftsMemberFilter.collectAsStateWithLifecycle()
    val availableResponsibleMembers by viewModel.availableResponsibleMembers.collectAsStateWithLifecycle()
    val ftsSearchResults by viewModel.ftsSearchResults.collectAsStateWithLifecycle()
    val isGeneratingAgenda by viewModel.isGeneratingAgenda.collectAsStateWithLifecycle()
    val agendaSuggestions by viewModel.agendaSuggestions.collectAsStateWithLifecycle()
    val isAnalyzingMeetingHealth by viewModel.isAnalyzingMeetingHealth.collectAsStateWithLifecycle()
    val meetingHealthReport by viewModel.meetingHealthReport.collectAsStateWithLifecycle()
    val listFilterQuery by viewModel.listFilterQuery.collectAsStateWithLifecycle()
    val listFilterScope by viewModel.listFilterScope.collectAsStateWithLifecycle()
    val listFilterStatus by viewModel.listFilterStatus.collectAsStateWithLifecycle()
    val listFilterMember by viewModel.listFilterMember.collectAsStateWithLifecycle()
    val isAnalyzingSentiment by viewModel.isAnalyzingSentiment.collectAsStateWithLifecycle()
    val sentimentAnalysisError by viewModel.sentimentAnalysisError.collectAsStateWithLifecycle()
    val audioSentimentAnalysis by viewModel.audioSentimentAnalysis.collectAsStateWithLifecycle()
    val autoSummarizeOnRecordingStop by viewModel.autoSummarizeOnRecordingStop.collectAsStateWithLifecycle()
    val latestRoomExecutiveSummary by viewModel.latestRoomExecutiveSummary.collectAsStateWithLifecycle()
    val lastPersistedRoomSummary by viewModel.lastPersistedRoomSummary.collectAsStateWithLifecycle()
    val roomMemberTasks by viewModel.roomMemberTasks.collectAsStateWithLifecycle()
    val roomBoardScheduleItems by viewModel.roomBoardScheduleItems.collectAsStateWithLifecycle()
    val homeSearchQuery by viewModel.homeSearchQuery.collectAsStateWithLifecycle()
    val selectedCalendarDateFilter by viewModel.selectedCalendarDateFilter.collectAsStateWithLifecycle()
    val dateRangeStartFilter by viewModel.dateRangeStartFilter.collectAsStateWithLifecycle()
    val dateRangeEndFilter by viewModel.dateRangeEndFilter.collectAsStateWithLifecycle()
    val allRoomExecutiveSummaries by viewModel.allRoomExecutiveSummaries.collectAsStateWithLifecycle()
    val activeSessionMemberTasks by viewModel.activeSessionMemberTasks.collectAsStateWithLifecycle()
    val lastExtractedMemberTasks by viewModel.lastExtractedMemberTasks.collectAsStateWithLifecycle()
    val isExtractingMemberTasks by viewModel.isExtractingMemberTasks.collectAsStateWithLifecycle()
    val memberTaskExtractionError by viewModel.memberTaskExtractionError.collectAsStateWithLifecycle()
    val autoSyncExtractedTasksToDbAndCalendar by viewModel.autoSyncExtractedTasksToDbAndCalendar.collectAsStateWithLifecycle()
    val isGeneratingMinutesDraft by viewModel.isGeneratingMinutesDraft.collectAsStateWithLifecycle()
    val minutesDraftError by viewModel.minutesDraftError.collectAsStateWithLifecycle()
    val officialMinutesDraft by viewModel.officialMinutesDraft.collectAsStateWithLifecycle()
    val selectedDraftTemplate by viewModel.selectedDraftTemplate.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    // Permission launcher for audio recording & speech recognition
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "جهت ضبط صوت و تایپ صوتی به مجوز میکروفون نیاز است.", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for system notification reminders
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "مجوز اعلان‌های یادآوری مصوبات فعال شد.", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle Toast notifications from ViewModel
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    // Android back navigation: if not on setup, back button returns to SETUP tab
    BackHandler(enabled = currentTab != AtlasTab.SETUP) {
        currentTab = AtlasTab.SETUP
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas)
            .windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            Column {
                AtlasHeader(
                    onLoadScenario = { key ->
                        viewModel.loadScenario(key)
                    },
                    onArchive = {
                        viewModel.archiveCurrentSession()
                    },
                    onNewSession = {
                        viewModel.startNewBlankSession()
                        currentTab = AtlasTab.SETUP
                    }
                )
                AtlasTabNavigation(
                    selectedTab = currentTab,
                    onTabSelected = { tab ->
                        currentTab = tab
                    },
                    archiveCount = archiveList.size
                )
            }
        },
        bottomBar = {
            OwnerSignatureFooter(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        },
        floatingActionButton = {
            VoiceRecordingFab(
                isAudioRecording = isAudioRecording,
                isContinuousTranscribing = isContinuousTranscribing,
                recordingDurationSec = recordingDurationSec,
                recordingAmplitude = recordingAmplitude,
                isAutoPausedBySilence = silenceDetectionStatus.isAutoPaused,
                onClick = { showVoiceRecordingSheet = true }
            )
        },
        containerColor = BgCanvas
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                AtlasTab.SETUP -> {
                    SetupScreen(
                        session = activeSession,
                        roomAttendees = roomAttendees,
                        archiveList = archiveList,
                        roomBoardScheduleItems = roomBoardScheduleItems,
                        homeSearchQuery = homeSearchQuery,
                        onHomeSearchQueryChange = { viewModel.updateHomeSearchQuery(it) },
                        selectedCalendarDateFilter = selectedCalendarDateFilter,
                        onSelectCalendarDateFilter = { viewModel.selectCalendarDateFilter(it) },
                        dateRangeStartFilter = dateRangeStartFilter,
                        dateRangeEndFilter = dateRangeEndFilter,
                        onUpdateDateRangeFilter = { start, end -> viewModel.updateDateRangeFilter(start, end) },
                        onClearDateRangeFilter = { viewModel.clearDateRangeFilter() },
                        onClearAllHomeDateFilters = { viewModel.clearAllHomeDateAndSearchFilters() },
                        onStartLiveRecordingSession = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            currentTab = AtlasTab.LIVE
                            if (!isAudioRecording && !isContinuousTranscribing) {
                                viewModel.toggleCombinedAudioAndSpeechToText()
                            }
                        },
                        onLoadBoardScheduleItem = { item ->
                            viewModel.loadBoardScheduleItem(item)
                            currentTab = AtlasTab.REPORT
                        },
                        onTitleChange = { viewModel.updateTitle(it) },
                        onScopeChange = { viewModel.updateScope(it) },
                        onExtCompanyChange = { viewModel.updateExtCompany(it) },
                        onExtRefChange = { viewModel.updateExtRef(it) },
                        onDurationChange = { viewModel.updateDurationMins(it) },
                        onLocationChange = { viewModel.updateLocation(it) },
                        onCalendarTypeChange = { viewModel.updateCalendarType(it) },
                        onDateChange = { viewModel.updateDate(it) },
                        onStartTimeChange = { viewModel.updateStartTime(it) },
                        onAddMember = { viewModel.addMember() },
                        onUpdateMember = { idx, m -> viewModel.updateMember(idx, m) },
                        onRemoveMember = { idx -> viewModel.removeMember(idx) },
                        onAddAttendeeWithDetails = { name, role, dept, isPresent ->
                            viewModel.addAttendeeWithDetails(name, role, dept, isPresent)
                        },
                        onUpdateAttendeeEntity = { entity ->
                            viewModel.updateAttendeeEntity(entity)
                        },
                        onToggleAttendeePresence = { entity ->
                            viewModel.toggleAttendeePresence(entity)
                        },
                        onChangeAttendeeStatus = { entity, status, isPresent ->
                            viewModel.changeAttendeeStatus(entity, status, isPresent)
                        },
                        onDeleteAttendeeById = { id ->
                            viewModel.deleteAttendeeById(id)
                        },
                        onAddAttachment = { viewModel.addAttachment() },
                        onUpdateAttachment = { idx, a -> viewModel.updateAttachment(idx, a) },
                        onRemoveAttachment = { idx -> viewModel.removeAttachment(idx) },
                        isSearchingStandards = isSearchingStandards,
                        searchStandardsResult = searchStandardsResult,
                        searchStandardsError = searchStandardsError,
                        onSearchStandards = { q -> viewModel.searchStandardsWithGoogle(q) },
                        onAddSearchResultToNotes = { res -> viewModel.addSearchResultToMeetingNotes(res) },
                        isGeneratingAgenda = isGeneratingAgenda,
                        agendaSuggestions = agendaSuggestions,
                        onGenerateAgendaSuggestions = { viewModel.generateAgendaSuggestionsWithGemini() },
                        onApplySuggestedTopic = { viewModel.applySuggestedAgendaTopic(it) },
                        onApplyAllTopics = { viewModel.applyAllSuggestedAgendaTopics() },
                        meetingHealthReport = meetingHealthReport,
                        isAnalyzingMeetingHealth = isAnalyzingMeetingHealth,
                        onAnalyzeMeetingHealth = { viewModel.analyzeMeetingHealthWithGemini() },
                        isAudioRecording = isAudioRecording,
                        recordingDurationSec = recordingDurationSec,
                        recordingAmplitude = recordingAmplitude,
                        recordedAudioFile = recordedAudioFile,
                        isListening = isListening,
                        isContinuousTranscribing = isContinuousTranscribing,
                        interimTranscript = interimTranscript,
                        audioRmsLevel = audioRmsLevel,
                        speechLanguage = speechLanguage,
                        onLanguageChange = { viewModel.setSpeechLanguage(it) },
                        activeTranscribingSpeaker = activeTranscribingSpeaker,
                        onTranscribingSpeakerChange = { viewModel.setTranscribingSpeaker(it) },
                        liveTypedMinutesText = liveTypedMinutesBuffer,
                        onUpdateLiveTypedMinutesText = { viewModel.updateLiveTypedMinutesBuffer(it) },
                        autoCommitSpeechToNotes = autoCommitSpeechToNotes,
                        onAutoCommitSpeechToNotesChange = { viewModel.setAutoCommitSpeechToNotes(it) },
                        onToggleCombinedAudioAndSpeech = {
                            viewModel.toggleCombinedAudioAndSpeechToText()
                        },
                        onToggleContinuousTranscription = {
                            viewModel.toggleContinuousTranscription()
                        },
                        onToggleAudioRecording = {
                            viewModel.toggleAudioRecording()
                        },
                        onAddNoteFromSpeech = { speaker, text, type ->
                            viewModel.addNote(speaker, text, type, "فوری / جاری")
                        },
                        onSimulateInstantDictation = {
                            viewModel.simulateInstantSpeechToMinutesDictation()
                        },
                        autoAnalyzeTypedMinutesWithGemini = autoAnalyzeTypedMinutesWithGemini,
                        onAutoAnalyzeTypedMinutesChange = { viewModel.setAutoAnalyzeTypedMinutesWithGemini(it) },
                        isAnalyzingTypedMinutes = isAnalyzingTypedMinutes,
                        typedMinutesAnalysis = typedMinutesAnalysis,
                        typedMinutesAnalysisError = typedMinutesAnalysisError,
                        onAnalyzeTypedMinutesWithGemini = { typedText ->
                            viewModel.analyzeTypedMeetingTextWithGemini(typedText, isAutoTriggered = false)
                        },
                        speechError = speechError,
                        onNavigateToLive = { currentTab = AtlasTab.LIVE }
                    )
                }

                AtlasTab.LIVE -> {
                    LiveMeetingScreen(
                        session = activeSession,
                        isTimerRunning = isTimerRunning,
                        onToggleTimer = { viewModel.toggleTimer() },
                        onResetTimer = { viewModel.resetTimer() },
                        isAudioRecording = isAudioRecording,
                        onToggleAudioRecording = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            viewModel.toggleAudioRecording()
                        },
                        recordedAudioFile = recordedAudioFile,
                        isListening = isListening,
                        onToggleSpeechRecognition = { onTextAppended ->
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            viewModel.toggleSpeechRecognition(onTextAppended)
                        },
                        isContinuousTranscribing = isContinuousTranscribing,
                        onToggleContinuousTranscription = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            viewModel.toggleContinuousTranscription()
                        },
                        interimTranscript = interimTranscript,
                        audioRmsLevel = audioRmsLevel,
                        speechLanguage = speechLanguage,
                        onLanguageChange = { viewModel.setSpeechLanguage(it) },
                        activeTranscribingSpeaker = activeTranscribingSpeaker,
                        onTranscribingSpeakerChange = { viewModel.setTranscribingSpeaker(it) },
                        onCommitTranscriptionSegment = { viewModel.commitTranscriptionSegmentToNote(it) },
                        onCommitAllTranscriptions = { viewModel.commitAllTranscriptionsToNotes() },
                        onRemoveTranscriptionSegment = { viewModel.removeTranscriptionSegment(it) },
                        onClearAllTranscriptions = { viewModel.clearAllTranscriptions() },
                        onAddManualTranscription = { text, speaker ->
                            viewModel.addTranscriptionSegmentManually(text, speaker)
                        },
                        onExportTranscriptionText = { viewModel.exportTranscriptionAsFormattedText() },
                        speechError = speechError,
                        isTranscriptionAiLoading = isTranscriptionAiLoading,
                        transcriptionAiAnalysis = transcriptionAiAnalysis,
                        transcriptionAiError = transcriptionAiError,
                        onAnalyzeTranscriptionsWithGemini = { viewModel.analyzeTranscriptionsWithGemini() },
                        onAddActionItemToNotes = { viewModel.addActionItemToMeetingNotes(it) },
                        onAddAllActionItemsToNotes = { viewModel.addAllActionItemsToMeetingNotes() },
                        onSaveTranscriptionSummaryAsMainAiSummary = { viewModel.saveTranscriptionSummaryAsMainAiSummary() },
                        onAddNote = { speaker, text, type, due ->
                            viewModel.addNote(speaker, text, type, due)
                        },
                        onToggleNoteDone = { noteId ->
                            viewModel.toggleNoteDone(noteId)
                        },
                        onRemoveNote = { noteId ->
                            viewModel.removeNote(noteId)
                        },
                        onNavigateToAi = { currentTab = AtlasTab.AI },
                        onCheckInByQr = { payload ->
                            viewModel.checkInAttendeeByQr(payload)
                        },
                        onCheckInById = { memberId ->
                            viewModel.checkInMemberById(memberId)
                        },
                        onExportPdf = { viewModel.shareMeetingPdf(context) },
                        recordingDurationSec = recordingDurationSec,
                        recordingAmplitude = recordingAmplitude,
                        micInputSensitivity = micInputSensitivity,
                        onMicInputSensitivityChange = { viewModel.updateMicInputSensitivity(it) },
                        silenceDetectionStatus = silenceDetectionStatus,
                        onSilenceDetectionEnabledChange = { viewModel.setSilenceDetectionEnabled(it) },
                        onForceResumeFromSilence = { viewModel.forceResumeAudioRecordingFromSilence() },
                        onSimulateSampleSpeechCheck = { viewModel.simulateLiveTranscriptionOverlaySample() },
                        isAudioProcessingWithGemini = isAudioProcessingWithGemini,
                        audioProcessingError = audioProcessingError,
                        audioMeetingAnalysis = audioMeetingAnalysis,
                        onProcessRecordedAudioWithGemini = { viewModel.processRecordedAudioWithGemini() },
                        onClearAudioMeetingAnalysis = { viewModel.clearAudioMeetingAnalysis() },
                        autoSummarizeOnRecordingStop = autoSummarizeOnRecordingStop,
                        onAutoSummarizeOnRecordingStopChange = { viewModel.setAutoSummarizeOnRecordingStop(it) },
                        latestRoomExecutiveSummary = latestRoomExecutiveSummary ?: lastPersistedRoomSummary
                    )
                }

                AtlasTab.SPECIAL -> {
                    SpecialModulesScreen(
                        session = activeSession,
                        onUpdateOps = { viewModel.updateOpsData(it) },
                        onUpdateHr = { viewModel.updateHrData(it) }
                    )
                }

                AtlasTab.AI -> {
                    AiAssistantScreen(
                        session = activeSession,
                        geminiApiKey = geminiApiKey,
                        onSaveApiKey = { viewModel.setGeminiApiKey(it) },
                        isAiLoading = isAiLoading,
                        aiError = aiError,
                        onRunAiSummary = { viewModel.runAiExecutiveSummary() },
                        onUpdateAiSummary = { viewModel.updateAiSummary(it) },
                        transcriptionAiAnalysis = transcriptionAiAnalysis,
                        isTranscriptionAiLoading = isTranscriptionAiLoading,
                        transcriptionAiError = transcriptionAiError,
                        onAnalyzeTranscriptionsWithGemini = { viewModel.analyzeTranscriptionsWithGemini() },
                        onAddActionItemToNotes = { viewModel.addActionItemToMeetingNotes(it) },
                        onAddAllActionItemsToNotes = { viewModel.addAllActionItemsToMeetingNotes() },
                        onExportPdf = { viewModel.shareOfficialDraftPdf(context) },
                        isAudioProcessingWithGemini = isAudioProcessingWithGemini,
                        audioProcessingError = audioProcessingError,
                        audioMeetingAnalysis = audioMeetingAnalysis,
                        onProcessRecordedAudioWithGemini = { viewModel.processRecordedAudioWithGemini() },
                        audioSentimentAnalysis = audioSentimentAnalysis,
                        isAnalyzingSentiment = isAnalyzingSentiment,
                        sentimentAnalysisError = sentimentAnalysisError,
                        onAnalyzeAudioSentiment = { viewModel.analyzeAudioSentimentWithGemini() },
                        onToggleSegmentReviewed = { id, notes -> viewModel.toggleFlaggedSegmentReviewed(id, notes) },
                        onAddResolutionFromSegment = { viewModel.applyFlaggedSegmentToMeetingNotes(it) },
                        onClearSentimentAnalysis = { viewModel.clearSentimentAnalysis() },
                        extractedMemberTasks = activeSessionMemberTasks.ifEmpty { lastExtractedMemberTasks },
                        isExtractingMemberTasks = isExtractingMemberTasks,
                        memberTaskExtractionError = memberTaskExtractionError,
                        autoSyncExtractedTasksToDbAndCalendar = autoSyncExtractedTasksToDbAndCalendar,
                        onAutoSyncExtractedTasksChange = { viewModel.setAutoSyncExtractedTasksToDbAndCalendar(it) },
                        onExtractAndAutoAssignMemberTasks = { customText ->
                            viewModel.extractAndAutoAssignMemberTasksWithGemini(customText)
                        },
                        onToggleMemberTaskDone = { viewModel.toggleMemberTaskDone(it) },
                        onDeleteMemberTask = { viewModel.deleteMemberTask(it) },
                        officialMinutesDraft = officialMinutesDraft,
                        isGeneratingMinutesDraft = isGeneratingMinutesDraft,
                        onGenerateOfficialDraftPdf = { autoShare ->
                            viewModel.generateAndCreateOfficialDraftPdf(context, autoShare)
                        },
                        onShareOfficialDraftPdf = {
                            viewModel.shareOfficialDraftPdf(context)
                        },
                        liveTypedMinutesText = liveTypedMinutesBuffer,
                        onUpdateLiveTypedMinutesText = { viewModel.updateLiveTypedMinutesBuffer(it) },
                        autoAnalyzeTypedMinutesWithGemini = autoAnalyzeTypedMinutesWithGemini,
                        onAutoAnalyzeTypedMinutesChange = { viewModel.setAutoAnalyzeTypedMinutesWithGemini(it) },
                        isAnalyzingTypedMinutes = isAnalyzingTypedMinutes,
                        typedMinutesAnalysis = typedMinutesAnalysis,
                        typedMinutesAnalysisError = typedMinutesAnalysisError,
                        onAnalyzeTypedMinutesWithGemini = { typedText ->
                            viewModel.analyzeTypedMeetingTextWithGemini(typedText, isAutoTriggered = false)
                        }
                    )
                }

                AtlasTab.REPORT -> {
                    OfficialReportScreen(
                        session = activeSession,
                        onArchiveSession = { viewModel.archiveCurrentSession() },
                        onExportPdf = { viewModel.shareMeetingPdf(context) },
                        onViewPdf = { viewModel.viewMeetingPdf(context) },
                        onUpdateCorporatePdfConfig = { viewModel.updateCorporatePdfConfig(it) },
                        onToggleSessionFinalized = { viewModel.toggleSessionFinalized(it) },
                        onConvertFinalizedToCorporatePdf = {
                            viewModel.convertFinalizedSessionToCorporatePdf(context, autoShare = false)
                        },
                        onShareFinalizedCorporatePdf = {
                            viewModel.shareFinalizedCorporatePdf(context)
                        },
                        onViewFinalizedCorporatePdf = {
                            viewModel.viewFinalizedCorporatePdf(context)
                        },
                        officialMinutesDraft = officialMinutesDraft,
                        isGeneratingMinutesDraft = isGeneratingMinutesDraft,
                        minutesDraftError = minutesDraftError,
                        selectedDraftTemplate = selectedDraftTemplate,
                        onSelectDraftTemplate = { viewModel.setSelectedDraftTemplate(it) },
                        onGenerateOfficialDraftPdf = { autoShare ->
                            viewModel.generateAndCreateOfficialDraftPdf(context, autoShare)
                        },
                        onShareOfficialDraftPdf = { viewModel.shareOfficialDraftPdf(context) },
                        onViewOfficialDraftPdf = { viewModel.viewOfficialDraftPdf(context) }
                    )
                }

                AtlasTab.ARCHIVE -> {
                    ArchiveTrackerScreen(
                        session = activeSession,
                        roomAttendees = roomAttendees,
                        archiveList = archiveList,
                        ftsQuery = ftsSearchQuery,
                        onFtsQueryChange = { viewModel.updateFtsSearchQuery(it) },
                        ftsScopeFilter = ftsScopeFilter,
                        onFtsScopeFilterChange = { viewModel.updateFtsScopeFilter(it) },
                        ftsStatusFilter = ftsStatusFilter,
                        onFtsStatusFilterChange = { viewModel.updateFtsStatusFilter(it) },
                        ftsMemberFilter = ftsMemberFilter,
                        onFtsMemberFilterChange = { viewModel.updateFtsMemberFilter(it) },
                        availableResponsibleMembers = availableResponsibleMembers,
                        ftsSearchResults = ftsSearchResults,
                        onResetFtsFilters = { viewModel.resetFtsFilters() },
                        listFilterQuery = listFilterQuery,
                        onListFilterQueryChange = { viewModel.updateListFilterQuery(it) },
                        listFilterScope = listFilterScope,
                        onListFilterScopeChange = { viewModel.updateListFilterScope(it) },
                        listFilterStatus = listFilterStatus,
                        onListFilterStatusChange = { viewModel.updateListFilterStatus(it) },
                        listFilterMember = listFilterMember,
                        onListFilterMemberChange = { viewModel.updateListFilterMember(it) },
                        onResetListFilters = { viewModel.resetListFilters() },
                        onToggleNoteDone = { noteId ->
                            viewModel.toggleNoteDone(noteId)
                        },
                        onLoadSession = { session ->
                            viewModel.loadSessionFromArchive(session)
                            currentTab = AtlasTab.REPORT
                        },
                        onDeleteSession = { id ->
                            viewModel.deleteArchivedSession(id)
                        },
                        onExportDatabaseJson = {
                            viewModel.exportDatabaseJson()
                        },
                        onImportDatabaseJson = { jsonStr ->
                            viewModel.importDatabaseJson(jsonStr)
                        },
                        onSendReminderForNote = { noteId ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.sendNotificationReminderForNote(context, noteId)
                        },
                        onSendAllPendingReminders = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.sendAllPendingResolutionReminders(context)
                        },
                        roomMemberTasks = roomMemberTasks.ifEmpty { lastExtractedMemberTasks },
                        onToggleMemberTaskDone = { viewModel.toggleMemberTaskDone(it) },
                        onDeleteMemberTask = { viewModel.deleteMemberTask(it) },
                        onSendReminderForMemberTask = { task ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.sendRoomTaskDeadlineReminder(context, task)
                        },
                        onSendAllRoomTaskReminders = { assigneeFilter ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.sendAllRoomTaskDeadlineReminders(context, assigneeFilter)
                        },
                        onExportSessionCorporatePdf = { session ->
                            viewModel.shareFinalizedCorporatePdf(context, session)
                        },
                        roomBoardScheduleItems = roomBoardScheduleItems,
                        roomExecutiveSummaries = allRoomExecutiveSummaries,
                        onScheduleUpcomingMeeting = { title, date, startTime, durationMins, location, chairperson, agenda ->
                            viewModel.scheduleUpcomingBoardMeeting(
                                title = title,
                                date = date,
                                startTime = startTime,
                                durationMins = durationMins,
                                location = location,
                                chairpersonName = chairperson,
                                agendaSummary = agenda
                            )
                        },
                        onToggleBoardScheduleStatus = { item ->
                            viewModel.toggleBoardScheduleStatus(item)
                        },
                        onDeleteBoardScheduleItem = { sessionId ->
                            viewModel.deleteBoardScheduleItem(sessionId)
                        },
                        onLoadBoardScheduleItem = { item ->
                            viewModel.loadBoardScheduleItem(item)
                            currentTab = AtlasTab.SETUP
                        }
                    )
                }
            }

            if (showVoiceRecordingSheet) {
                VoiceRecordingBottomSheet(
                    sessionTitle = activeSession.title,
                    isAudioRecording = isAudioRecording,
                    recordingDurationSec = recordingDurationSec,
                    recordingAmplitude = recordingAmplitude,
                    recordedAudioFile = recordedAudioFile,
                    isContinuousTranscribing = isContinuousTranscribing,
                    interimTranscript = interimTranscript,
                    audioRmsLevel = audioRmsLevel,
                    activeSpeaker = activeTranscribingSpeaker,
                    isAudioProcessingWithGemini = isAudioProcessingWithGemini,
                    audioProcessingError = audioProcessingError,
                    autoSummarizeOnRecordingStop = autoSummarizeOnRecordingStop,
                    onAutoSummarizeOnRecordingStopChange = {
                        viewModel.setAutoSummarizeOnRecordingStop(it)
                    },
                    isTranscriptionAiLoading = isTranscriptionAiLoading,
                    transcriptionAiAnalysis = transcriptionAiAnalysis ?: activeSession.transcriptionAnalysis,
                    onAnalyzeTranscriptionsWithGemini = {
                        viewModel.analyzeTranscriptionsWithGemini()
                    },
                    onAddActionItemToNotes = {
                        viewModel.addActionItemToMeetingNotes(it)
                    },
                    onAddAllActionItemsToNotes = {
                        viewModel.addAllActionItemsToMeetingNotes()
                    },
                    micInputSensitivity = micInputSensitivity,
                    onMicInputSensitivityChange = {
                        viewModel.updateMicInputSensitivity(it)
                    },
                    silenceDetectionStatus = silenceDetectionStatus,
                    onSilenceDetectionEnabledChange = {
                        viewModel.setSilenceDetectionEnabled(it)
                    },
                    onForceResumeFromSilence = {
                        viewModel.forceResumeAudioRecordingFromSilence()
                    },
                    recentTranscriptions = activeSession.transcriptions,
                    onSimulateSampleSpeechCheck = {
                        viewModel.simulateLiveTranscriptionOverlaySample()
                    },
                    onToggleAudioRecording = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        viewModel.toggleAudioRecording()
                    },
                    onToggleContinuousTranscription = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        viewModel.toggleContinuousTranscription()
                    },
                    onProcessRecordedAudioWithGemini = {
                        viewModel.processRecordedAudioWithGemini()
                    },
                    onNavigateToAiTab = {
                        showVoiceRecordingSheet = false
                        currentTab = AtlasTab.AI
                    },
                    onDismiss = {
                        showVoiceRecordingSheet = false
                    }
                )
            }
        }
    }
}
