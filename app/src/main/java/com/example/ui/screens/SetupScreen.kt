package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.AgendaSuggestion
import com.example.data.Attachment
import com.example.data.MeetingHealthReport
import com.example.data.MeetingSession
import com.example.data.Member
import com.example.data.TypedMeetingMinutesAnalysis
import com.example.data.local.AttendeeEntity
import com.example.data.local.BoardScheduleEntity
import com.example.service.GroundedSearchResult
import com.example.ui.components.AttendeeManagementComponent
import com.example.ui.components.BoardAnalyticsDashboard
import com.example.ui.components.HomeWorkspaceAndCalendarWidget
import com.example.ui.components.InstantSpeechToMinutesPanel
import com.example.ui.components.QuantumPanel
import com.example.ui.theme.*
import com.example.util.QrCodeGenerator
import java.io.File

@Composable
fun SetupScreen(
    session: MeetingSession,
    roomAttendees: List<AttendeeEntity> = session.members.mapIndexed { idx, m ->
        AttendeeEntity.fromMember(m, session.id, idx)
    },
    archiveList: List<MeetingSession> = emptyList(),
    roomBoardScheduleItems: List<BoardScheduleEntity> = emptyList(),
    homeSearchQuery: String = "",
    onHomeSearchQueryChange: (String) -> Unit = {},
    selectedCalendarDateFilter: String = "",
    onSelectCalendarDateFilter: (String) -> Unit = {},
    dateRangeStartFilter: String = "",
    dateRangeEndFilter: String = "",
    onUpdateDateRangeFilter: (String, String) -> Unit = { _, _ -> },
    onClearDateRangeFilter: () -> Unit = {},
    onClearAllHomeDateFilters: () -> Unit = {},
    onStartLiveRecordingSession: () -> Unit = {},
    onLoadBoardScheduleItem: (BoardScheduleEntity) -> Unit = {},
    onTitleChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExtCompanyChange: (String) -> Unit,
    onExtRefChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onLocationChange: (String) -> Unit,
    onCalendarTypeChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onAddMember: () -> Unit,
    onUpdateMember: (Int, Member) -> Unit,
    onRemoveMember: (Int) -> Unit,
    onAddAttendeeWithDetails: (String, String, String, Boolean) -> Unit = { name, role, dept, present ->
        onAddMember()
    },
    onUpdateAttendeeEntity: (AttendeeEntity) -> Unit = { entity ->
        val idx = session.members.indexOfFirst { it.id == entity.id }
        if (idx >= 0) onUpdateMember(idx, entity.toMember())
    },
    onToggleAttendeePresence: (AttendeeEntity) -> Unit = { entity ->
        val idx = session.members.indexOfFirst { it.id == entity.id }
        if (idx >= 0) onUpdateMember(idx, entity.toMember().copy(isPresent = !entity.isPresent))
    },
    onChangeAttendeeStatus: (AttendeeEntity, String, Boolean) -> Unit = { entity, _, present ->
        val idx = session.members.indexOfFirst { it.id == entity.id }
        if (idx >= 0) onUpdateMember(idx, entity.toMember().copy(isPresent = present))
    },
    onDeleteAttendeeById: (String) -> Unit = { id ->
        val idx = session.members.indexOfFirst { it.id == id }
        if (idx >= 0) onRemoveMember(idx)
    },
    onAddAttachment: () -> Unit,
    onUpdateAttachment: (Int, Attachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    isSearchingStandards: Boolean = false,
    searchStandardsResult: GroundedSearchResult? = null,
    searchStandardsError: String? = null,
    onSearchStandards: (String) -> Unit = {},
    onAddSearchResultToNotes: (GroundedSearchResult) -> Unit = {},
    isGeneratingAgenda: Boolean = false,
    agendaSuggestions: List<AgendaSuggestion> = emptyList(),
    onGenerateAgendaSuggestions: () -> Unit = {},
    onApplySuggestedTopic: (AgendaSuggestion) -> Unit = {},
    onApplyAllTopics: () -> Unit = {},
    meetingHealthReport: MeetingHealthReport? = null,
    isAnalyzingMeetingHealth: Boolean = false,
    onAnalyzeMeetingHealth: () -> Unit = {},
    isAudioRecording: Boolean = false,
    recordingDurationSec: Long = 0L,
    recordingAmplitude: Float = 0f,
    recordedAudioFile: File? = null,
    isListening: Boolean = false,
    isContinuousTranscribing: Boolean = false,
    interimTranscript: String = "",
    audioRmsLevel: Float = 0f,
    speechLanguage: String = "fa-IR",
    onLanguageChange: (String) -> Unit = {},
    activeTranscribingSpeaker: String = "سخنران",
    onTranscribingSpeakerChange: (String) -> Unit = {},
    liveTypedMinutesText: String = "",
    onUpdateLiveTypedMinutesText: (String) -> Unit = {},
    autoCommitSpeechToNotes: Boolean = true,
    onAutoCommitSpeechToNotesChange: (Boolean) -> Unit = {},
    onToggleCombinedAudioAndSpeech: () -> Unit = {},
    onToggleContinuousTranscription: () -> Unit = {},
    onToggleAudioRecording: () -> Unit = {},
    onAddNoteFromSpeech: (String, String, String) -> Unit = { _, _, _ -> },
    onSimulateInstantDictation: () -> Unit = {},
    autoAnalyzeTypedMinutesWithGemini: Boolean = true,
    onAutoAnalyzeTypedMinutesChange: (Boolean) -> Unit = {},
    isAnalyzingTypedMinutes: Boolean = false,
    typedMinutesAnalysis: TypedMeetingMinutesAnalysis? = session.typedMinutesAnalysis,
    typedMinutesAnalysisError: String? = null,
    onAnalyzeTypedMinutesWithGemini: (String) -> Unit = {},
    speechError: String? = null,
    onNavigateToLive: () -> Unit
) {
    val context = LocalContext.current
    var viewingMemberQr by remember { mutableStateOf<Member?>(null) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var calTypeMenuExpanded by remember { mutableStateOf(false) }

    val scopeLabels = mapOf(
        "internal" to "درون‌سازمانی / هیئت‌مدیره (Internal)",
        "external_partner" to "برون‌سازمانی - شریک استراتژیک (Partner)",
        "external_supplier" to "برون‌سازمانی - تأمین‌کننده (Supplier)",
        "external_contract" to "برون‌سازمانی - عقد قرارداد رسمی",
        "interview_hr" to "مصاحبه تخصصی و ارزیابی (HR)"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- 0. Top Search + Date Range Picker + Interactive Monthly Calendar Widget + Holding Meetings ---
        item {
            HomeWorkspaceAndCalendarWidget(
                allScheduleItems = roomBoardScheduleItems,
                activeSession = session,
                archiveList = archiveList,
                searchQuery = homeSearchQuery,
                onSearchQueryChange = onHomeSearchQueryChange,
                selectedCalendarDate = selectedCalendarDateFilter,
                onSelectCalendarDate = onSelectCalendarDateFilter,
                dateRangeStart = dateRangeStartFilter,
                dateRangeEnd = dateRangeEndFilter,
                onUpdateDateRange = onUpdateDateRangeFilter,
                onClearDateRange = onClearDateRangeFilter,
                onClearAllFilters = onClearAllHomeDateFilters,
                onLoadMeetingItem = onLoadBoardScheduleItem,
                onStartLiveRecordingSession = onStartLiveRecordingSession
            )
        }

        // --- 1. Basic Meeting Information ---
        item {
            QuantumPanel(
                title = "مشخصات بنیادین و راهبردی جلسه",
                badgeText = "معماری Local-First"
            ) {
                // Title
                OutlinedTextField(
                    value = session.title,
                    onValueChange = onTitleChange,
                    label = { Text("عنوان رسمی جلسه", fontSize = 11.5.sp) },
                    placeholder = { Text("مثلاً: جلسه راهبردی زنجیره سرد و ناوگان...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meeting_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scope & Duration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Scope Selector
                    Box(modifier = Modifier.weight(1.3f)) {
                        OutlinedTextField(
                            value = scopeLabels[session.scope] ?: "درون‌سازمانی",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("نوع تعامل", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = { scopeMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scopeMenuExpanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = scopeMenuExpanded,
                            onDismissRequest = { scopeMenuExpanded = false },
                            modifier = Modifier.background(BgCard)
                        ) {
                            scopeLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextMain, fontSize = 11.5.sp) },
                                    onClick = {
                                        scopeMenuExpanded = false
                                        onScopeChange(key)
                                    }
                                )
                            }
                        }
                    }

                    // Duration Selector
                    Box(modifier = Modifier.weight(0.9f)) {
                        OutlinedTextField(
                            value = "${session.durationMins} دقیقه",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("بازه تخصیصی", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = { durationMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { durationMenuExpanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = durationMenuExpanded,
                            onDismissRequest = { durationMenuExpanded = false },
                            modifier = Modifier.background(BgCard)
                        ) {
                            listOf(60, 90, 120, 150, 180).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("$mins دقیقه (${mins / 60.0} ساعت)", color = TextMain, fontSize = 12.sp) },
                                    onClick = {
                                        durationMenuExpanded = false
                                        onDurationChange(mins)
                                    }
                                )
                            }
                        }
                    }
                }

                // External Counterparty Box (if scope != internal)
                if (session.scope != "internal") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentPurple.copy(alpha = 0.05f))
                            .border(1.dp, BorderActive, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "اطلاعات طرف مقابل و مرجع قرارداد",
                                color = AccentPurpleLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = session.extCompany,
                                    onValueChange = onExtCompanyChange,
                                    label = { Text("نام سازمان / شرکت", fontSize = 10.5.sp) },
                                    placeholder = { Text("شرکت چاپار چابک...") },
                                    modifier = Modifier.weight(1.3f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentPurple,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedContainerColor = BgInput,
                                        unfocusedContainerColor = BgInput
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = session.extRef,
                                    onValueChange = onExtRefChange,
                                    label = { Text("شماره مرجع / پیگیری", fontSize = 10.5.sp) },
                                    placeholder = { Text("RN-OPS-2026") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentPurple,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedContainerColor = BgInput,
                                        unfocusedContainerColor = BgInput
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Location / Online link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = session.location,
                        onValueChange = onLocationChange,
                        label = { Text("مکان برگزاری یا لینک آنلاین", fontSize = 11.sp) },
                        placeholder = { Text("سالن کنفرانس یا Meet/Teams") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val loc = session.location.trim()
                            val targetUri = if (loc.startsWith("http://") || loc.startsWith("https://")) {
                                Uri.parse(loc)
                            } else {
                                Uri.parse("https://meet.google.com/new")
                            }
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                            } catch (e: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgInput,
                            contentColor = AccentCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "اتصال", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("اتصال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calendar Type
                    Box(modifier = Modifier.weight(0.9f)) {
                        OutlinedTextField(
                            value = if (session.calendarType == "jalali") "🇮🇷 شمسی" else "🇬🇧 میلادی",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تقویم", fontSize = 10.5.sp) },
                            trailingIcon = {
                                IconButton(onClick = { calTypeMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { calTypeMenuExpanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = calTypeMenuExpanded,
                            onDismissRequest = { calTypeMenuExpanded = false },
                            modifier = Modifier.background(BgCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("🇮🇷 تقویم شمسی", color = TextMain, fontSize = 11.5.sp) },
                                onClick = {
                                    calTypeMenuExpanded = false
                                    onCalendarTypeChange("jalali")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🇬🇧 تقویم میلادی", color = TextMain, fontSize = 11.5.sp) },
                                onClick = {
                                    calTypeMenuExpanded = false
                                    onCalendarTypeChange("gregorian")
                                }
                            )
                        }
                    }

                    // Date Input
                    OutlinedTextField(
                        value = session.date,
                        onValueChange = onDateChange,
                        label = { Text("تاریخ جلسه", fontSize = 10.5.sp) },
                        placeholder = { Text(if (session.calendarType == "jalali") "1405/08/15" else "2026-11-07") },
                        modifier = Modifier.weight(1.1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Start Time Input
                    OutlinedTextField(
                        value = session.startTime,
                        onValueChange = onStartTimeChange,
                        label = { Text("ساعت", fontSize = 10.5.sp) },
                        placeholder = { Text("10:00") },
                        modifier = Modifier.weight(0.8f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // --- 1.25. Android Audio Recording & Real-Time Speech-to-Text Instant Minutes Typing ---
        item {
            InstantSpeechToMinutesPanel(
                session = session,
                isAudioRecording = isAudioRecording,
                recordingDurationSec = recordingDurationSec,
                recordingAmplitude = recordingAmplitude,
                recordedAudioFile = recordedAudioFile,
                isListening = isListening,
                isContinuousTranscribing = isContinuousTranscribing,
                interimTranscript = interimTranscript,
                audioRmsLevel = audioRmsLevel,
                speechLanguage = speechLanguage,
                onLanguageChange = onLanguageChange,
                activeSpeaker = activeTranscribingSpeaker,
                onSpeakerChange = onTranscribingSpeakerChange,
                liveTypedMinutesText = liveTypedMinutesText,
                onUpdateLiveTypedMinutesText = onUpdateLiveTypedMinutesText,
                autoCommitSpeechToNotes = autoCommitSpeechToNotes,
                onAutoCommitSpeechToNotesChange = onAutoCommitSpeechToNotesChange,
                onToggleCombinedAudioAndSpeech = onToggleCombinedAudioAndSpeech,
                onToggleContinuousSpeech = onToggleContinuousTranscription,
                onToggleAudioRecording = onToggleAudioRecording,
                onAddNoteFromSpeech = onAddNoteFromSpeech,
                onSimulateInstantDictation = onSimulateInstantDictation,
                autoAnalyzeTypedMinutesWithGemini = autoAnalyzeTypedMinutesWithGemini,
                onAutoAnalyzeTypedMinutesChange = onAutoAnalyzeTypedMinutesChange,
                isAnalyzingTypedMinutes = isAnalyzingTypedMinutes,
                typedMinutesAnalysis = typedMinutesAnalysis,
                typedMinutesAnalysisError = typedMinutesAnalysisError,
                onAnalyzeTypedMinutesWithGemini = onAnalyzeTypedMinutesWithGemini,
                speechError = speechError
            )
        }

        // --- 1.5. Session QR Code Generator View (ZXing) ---
        item {
            var selectedQrTargetIdx by remember(session.members.size) { mutableStateOf(-1) } // -1 = Master Session QR, >=0 = Member index
            var targetMenuExpanded by remember { mutableStateOf(false) }

            val qrPayload = remember(selectedQrTargetIdx, session.id, session.title, session.date, session.startTime, session.members) {
                if (selectedQrTargetIdx in session.members.indices) {
                    val m = session.members[selectedQrTargetIdx]
                    QrCodeGenerator.createMemberCheckInPayload(
                        sessionId = session.id,
                        memberId = m.id,
                        name = m.name,
                        title = m.title
                    )
                } else {
                    QrCodeGenerator.createSessionCheckInPayload(session)
                }
            }

            val qrBitmap = remember(qrPayload) {
                QrCodeGenerator.generateQrBitmap(qrPayload, 420)
            }

            QuantumPanel(
                title = "📱 بارکد اختصاصی جلسه (Session QR Code)",
                badgeText = "ZXing QR"
            ) {
                Text(
                    text = "بارکد یکتای جلسه فعال یا کارت ورود اعضا جهت اسکن و ثبت خودکار حضور در تب بوم زنده:",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Selector between Session Master QR and specific Member QR
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentLabel = if (selectedQrTargetIdx in session.members.indices) {
                        val m = session.members[selectedQrTargetIdx]
                        "👤 کارت ورود عضو: ${m.name.ifBlank { "بدون نام" }} (${m.title})"
                    } else {
                        "🏢 بارکد یکتای کل جلسه (${session.id})"
                    }

                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("انتخاب بارکد جلسه / عضو", fontSize = 10.5.sp) },
                        trailingIcon = {
                            IconButton(onClick = { targetMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { targetMenuExpanded = true }
                            .testTag("setup_qr_type_selector"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    DropdownMenu(
                        expanded = targetMenuExpanded,
                        onDismissRequest = { targetMenuExpanded = false },
                        modifier = Modifier.background(BgCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("🏢 بارکد یکتای کل جلسه (${session.id})", color = AccentEmeraldLight, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            onClick = {
                                selectedQrTargetIdx = -1
                                targetMenuExpanded = false
                            }
                        )
                        session.members.forEachIndexed { idx, m ->
                            DropdownMenuItem(
                                text = { Text("👤 کارت عضو: ${m.name} (${m.title})", color = TextMain, fontSize = 11.5.sp) },
                                onClick = {
                                    selectedQrTargetIdx = idx
                                    targetMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(140.dp)
                            .border(2.dp, AccentPurple, RoundedCornerShape(12.dp))
                            .padding(6.dp)
                            .testTag("setup_session_qr_image")
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Session QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = AccentPurple)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = session.title.ifBlank { "جلسه راهبردی سازمانی" },
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = "شناسه یکتا: ${session.id}",
                            color = AccentPurpleLight,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "تاریخ: ${session.date} | ساعت: ${session.startTime}",
                            color = TextSub,
                            fontSize = 10.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QR Payload", qrPayload))
                                    android.widget.Toast.makeText(context, "کد بارکد کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmeraldLight),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("copy_session_qr_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("کپی کد", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "بارکد جلسه «${session.title}» (${session.id}):\n$qrPayload")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "اشتراک داده بارکد QR"))
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اشتراک", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- 1.8. Google Search Grounding: Corporate Governance & Logistics Standards ---
        item {
            var searchQuery by remember { mutableStateOf("") }

            QuantumPanel(
                title = "🌐 جستجوی زنده مقررات حاکمیت شرکتی و استانداردهای لجستیک",
                badgeText = "Google Search Tool"
            ) {
                Text(
                    text = "استعلام آخرین آیین‌نامه‌های حاکمیت شرکتی، استانداردهای زنجیره سرد دارویی (GDP / WHO) و لجستیک 5PL با ابزار Google Search جهت درج مستقیم در مصوبات جلسه:",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Preset Topic Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "مقررات حاکمیت شرکتی بورس" to "آخرین دستورالعمل حاکمیت شرکتی سازمان بورس و وظایف هیئت‌مدیره",
                        "استاندارد GDP زنجیره سرد" to "استانداردهای بین‌المللی WHO GDP برای زنجیره سرد دارویی 2 تا 8 درجه",
                        "استاندارد لجستیک 5PL و ISO" to "استانداردهای ISO 28000 امنیت زنجیره تأمین و لجستیک 5PL"
                    )
                    presets.forEach { (shortLabel, fullQuery) ->
                        SuggestionChip(
                            onClick = {
                                searchQuery = fullQuery
                                onSearchStandards(fullQuery)
                            },
                            label = { Text(shortLabel, fontSize = 9.5.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = BgInput,
                                labelColor = AccentCyan
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = AccentCyan.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Input & Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("جستجو با Google Search...", fontSize = 10.5.sp) },
                        placeholder = { Text("مثلاً: ضوابط کمیته حسابرسی یا استاندارد ایزو زنجیره سرد...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setup_google_search_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = { onSearchStandards(searchQuery) },
                        enabled = !isSearchingStandards,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = BgCanvas
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("setup_google_search_btn")
                    ) {
                        if (isSearchingStandards) {
                            CircularProgressIndicator(
                                color = BgCanvas,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "جستجو", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("جستجو", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (searchStandardsError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ $searchStandardsError",
                        color = AccentRose,
                        fontSize = 11.sp
                    )
                }

                if (searchStandardsResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgInput),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📌 نتیجه استعلام استاندارد: ${searchStandardsResult.query}",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = searchStandardsResult.summaryText,
                                color = TextMain,
                                fontSize = 11.5.sp,
                                lineHeight = 18.sp
                            )

                            if (searchStandardsResult.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "🔗 منابع معتبر وب (Google Search Grounding):",
                                    color = AccentPurpleLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                searchStandardsResult.sources.take(3).forEach { src ->
                                    Text(
                                        text = "• ${src.title}",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clickable {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.uri)))
                                                } catch (e: Exception) {}
                                            }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { onAddSearchResultToNotes(searchStandardsResult) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentEmerald,
                                    contentColor = BgCanvas
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("add_search_result_to_notes_btn")
                            ) {
                                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("افزودن مستقیم به مصوبات و یادداشت‌های جلسه +", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- 1.9. AI Agenda Suggestions (Gemini 2.5 Flash) ---
        item {
            QuantumPanel(
                title = "💡 پیشنهاد هوشمند دستور جلسه با Gemini (AI Agenda)",
                badgeText = if (agendaSuggestions.isNotEmpty()) "${agendaSuggestions.size} بند پیشنهادی" else "Gemini 2.5 Flash"
            ) {
                Text(
                    text = "استخراج و تنظیم خودکار موضوعات کلیدی دستور جلسه بر اساس مصوبات معوق گذشته، اهداف حاکمیت شرکتی و الزامات راهبردی زنجیره سرد با استفاده از هوش مصنوعی Gemini:",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onGenerateAgendaSuggestions,
                    enabled = !isGeneratingAgenda,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("generate_agenda_suggestions_btn")
                ) {
                    if (isGeneratingAgenda) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال تحلیل مصوبات گذشته و تدوین دستور جلسه...", fontSize = 11.5.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = AccentGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (agendaSuggestions.isEmpty()) "✨ تدوین هوشمند دستور جلسه با Gemini" else "🔄 بازتولید و استخراج مجدد با هوش مصنوعی",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (agendaSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📌 موضوعات استخراج‌شده پیشنهادی (${agendaSuggestions.size} مورد):",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = onApplyAllTopics,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmeraldLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("apply_all_agenda_topics_btn")
                        ) {
                            Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("افزودن همه به جلسه +", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        agendaSuggestions.forEachIndexed { idx, item ->
                            val objectiveBadgeColor = if (item.isRecurringObjective) AccentCyan else AccentPurpleLight

                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgInput),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("agenda_suggestion_card_$idx")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.topicTitle,
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Surface(
                                            color = objectiveBadgeColor.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, objectiveBadgeColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = if (item.isRecurringObjective) "🎯 هدف مستمر" else "📌 مصوبه پیگیری",
                                                color = objectiveBadgeColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "👤 ارائه: ${item.proposedSpeaker}",
                                            color = AccentCyan,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = "📋 نوع: ${item.suggestedActionType}",
                                            color = AccentPurpleLight,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = "⏱️ ${item.estimatedDurationMins} دقیقه",
                                            color = AccentEmeraldLight,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "💡 مبنا و ضرورت: ${item.rationalReason}",
                                        color = TextMuted,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { onApplySuggestedTopic(item) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentPurple.copy(alpha = 0.25f),
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                            .testTag("apply_agenda_topic_$idx")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("افزودن این بند به موضوعات و مصوبات جلسه +", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Room Database Attendees & Roles Management Component ---
        item {
            AttendeeManagementComponent(
                attendees = roomAttendees.ifEmpty {
                    session.members.mapIndexed { idx, m -> AttendeeEntity.fromMember(m, session.id, idx) }
                },
                onAddAttendee = { name, role, dept, isPresent ->
                    onAddAttendeeWithDetails(name, role, dept, isPresent)
                },
                onUpdateAttendee = { updatedEntity ->
                    onUpdateAttendeeEntity(updatedEntity)
                },
                onToggleAttendance = { entity ->
                    onToggleAttendeePresence(entity)
                },
                onChangeAttendanceStatus = { entity, status, isPresent ->
                    onChangeAttendeeStatus(entity, status, isPresent)
                },
                onDeleteAttendee = { entityId ->
                    onDeleteAttendeeById(entityId)
                },
                onShowMemberQr = { member ->
                    viewingMemberQr = member
                }
            )
        }

        // --- 2.5. Visual Recharts Dashboard: Board Attendance Trends & Action Completion Over Time ---
        item {
            BoardAnalyticsDashboard(
                session = session,
                roomAttendees = roomAttendees,
                archiveList = archiveList,
                meetingHealthReport = meetingHealthReport,
                isAnalyzingMeetingHealth = isAnalyzingMeetingHealth,
                onAnalyzeMeetingHealth = onAnalyzeMeetingHealth
            )
        }

        // --- 3. Attachments & Documents List ---
        item {
            QuantumPanel(
                title = "📎 اسناد، مدارک و پیوست‌ها",
                badgeText = "${session.attachments.size} سند"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مدارک، پلی‌بوک‌ها و گزارش‌های جلسه:",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )

                    Button(
                        onClick = onAddAttachment,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan.copy(alpha = 0.2f),
                            contentColor = AccentCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ افزودن سند", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (session.attachments.isEmpty()) {
                    Text(
                        text = "هنوز سندی پیوست نشده است.",
                        color = TextSub,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    session.attachments.forEachIndexed { idx, att ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BgInput),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = att.name,
                                    onValueChange = { onUpdateAttachment(idx, att.copy(name = it)) },
                                    label = { Text("نام فایل یا سند", fontSize = 9.sp) },
                                    placeholder = { Text("پلی‌بوک لجستیک.pdf") },
                                    modifier = Modifier.weight(1.3f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentCyan,
                                        unfocusedBorderColor = BorderSubtle
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = att.refCode,
                                    onValueChange = { onUpdateAttachment(idx, att.copy(refCode = it)) },
                                    label = { Text("کد سند / مرجع", fontSize = 9.sp) },
                                    placeholder = { Text("RN-DOC-01") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentCyan,
                                        unfocusedBorderColor = BorderSubtle
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                IconButton(
                                    onClick = { onRemoveAttachment(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = AccentRose, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Proceed to Live Room Button ---
        item {
            Button(
                onClick = onNavigateToLive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("go_to_live_button")
            ) {
                Text(
                    text = "تایید مشخصات و ورود به اتاق بوم جلسه ←",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    viewingMemberQr?.let { member ->
        val payload = remember(member, session.id) {
            QrCodeGenerator.createMemberCheckInPayload(
                sessionId = session.id,
                memberId = member.id,
                name = member.name,
                title = member.title
            )
        }
        val qrBitmap = remember(payload) {
            QrCodeGenerator.generateQrBitmap(payload, 380)
        }

        AlertDialog(
            onDismissRequest = { viewingMemberQr = null },
            title = {
                Text(
                    text = "کارت ورود: ${member.name.ifBlank { "عضو سازمانی" }}",
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${member.title} • ${member.unit}",
                        color = AccentPurpleLight,
                        fontSize = 11.5.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(190.dp)
                            .border(2.dp, AccentPurple, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = AccentPurple)
                            }
                        }
                    }

                    Text(
                        text = "این بارکد را در اتاق بوم زنده جلسه اسکن کنید تا حضور ثبت گردد.",
                        color = TextSub,
                        fontSize = 10.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewingMemberQr = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("بستن", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val shareText = "کارت ورود به جلسه «${session.title}»\nنام: ${member.name}\nسمت: ${member.title}\nشناسه QR:\n$payload"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "ارسال کارت ورود"))
                }) {
                    Text("اشتراک بارکد", color = AccentCyan)
                }
            },
            containerColor = BgCard
        )
    }
}
