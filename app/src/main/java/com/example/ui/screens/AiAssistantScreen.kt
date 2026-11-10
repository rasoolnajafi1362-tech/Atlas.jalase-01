package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import android.content.Intent
import android.provider.CalendarContract
import com.example.data.AudioSentimentAnalysis
import com.example.data.HighTensionSegment
import com.example.data.MeetingSession
import com.example.data.TypedMeetingMinutesAnalysis
import com.example.data.local.MemberTaskEntity
import com.example.ui.components.AudioSentimentAnalysisComponent
import com.example.ui.components.QuantumPanel
import com.example.ui.theme.*

@Composable
fun AiAssistantScreen(
    session: MeetingSession,
    geminiApiKey: String,
    onSaveApiKey: (String) -> Unit,
    isAiLoading: Boolean,
    aiError: String?,
    onRunAiSummary: () -> Unit,
    onUpdateAiSummary: (String) -> Unit,
    transcriptionAiAnalysis: com.example.data.TranscriptionAiAnalysis? = null,
    isTranscriptionAiLoading: Boolean = false,
    transcriptionAiError: String? = null,
    onAnalyzeTranscriptionsWithGemini: () -> Unit = {},
    onAddActionItemToNotes: (com.example.data.ExtractedActionItem) -> Unit = {},
    onAddAllActionItemsToNotes: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    isAudioProcessingWithGemini: Boolean = false,
    audioProcessingError: String? = null,
    audioMeetingAnalysis: com.example.data.AudioMeetingAnalysis? = null,
    onProcessRecordedAudioWithGemini: () -> Unit = {},
    audioSentimentAnalysis: AudioSentimentAnalysis? = null,
    isAnalyzingSentiment: Boolean = false,
    sentimentAnalysisError: String? = null,
    onAnalyzeAudioSentiment: () -> Unit = {},
    onToggleSegmentReviewed: (String, String) -> Unit = { _, _ -> },
    onAddResolutionFromSegment: (HighTensionSegment) -> Unit = {},
    onClearSentimentAnalysis: () -> Unit = {},
    extractedMemberTasks: List<MemberTaskEntity> = emptyList(),
    isExtractingMemberTasks: Boolean = false,
    memberTaskExtractionError: String? = null,
    autoSyncExtractedTasksToDbAndCalendar: Boolean = true,
    onAutoSyncExtractedTasksChange: (Boolean) -> Unit = {},
    onExtractAndAutoAssignMemberTasks: (String) -> Unit = {},
    onToggleMemberTaskDone: (MemberTaskEntity) -> Unit = {},
    onDeleteMemberTask: (Int) -> Unit = {},
    officialMinutesDraft: com.example.data.OfficialMinutesDraft? = null,
    isGeneratingMinutesDraft: Boolean = false,
    onGenerateOfficialDraftPdf: (Boolean) -> Unit = {},
    onShareOfficialDraftPdf: () -> Unit = {},
    liveTypedMinutesText: String = "",
    onUpdateLiveTypedMinutesText: (String) -> Unit = {},
    autoAnalyzeTypedMinutesWithGemini: Boolean = true,
    onAutoAnalyzeTypedMinutesChange: (Boolean) -> Unit = {},
    isAnalyzingTypedMinutes: Boolean = false,
    typedMinutesAnalysis: TypedMeetingMinutesAnalysis? = session.typedMinutesAnalysis,
    typedMinutesAnalysisError: String? = null,
    onAnalyzeTypedMinutesWithGemini: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var inputKey by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var customMeetingTextForTasks by remember { mutableStateOf("") }
    var typedTextForGeminiAnalysis by remember(liveTypedMinutesText) {
        mutableStateOf(liveTypedMinutesText)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            QuantumPanel(
                title = "🤖 موتور هوش مصنوعی اطلس (Gemini API)",
                badgeText = if (isAiLoading) "⏳ در حال پردازش..." else if (aiError != null) "❌ خطا" else "آماده تحلیل"
            ) {
                Text(
                    text = "تبدیل خودکار یادداشت‌های خام، مذاکرات و شاخص‌های جلسه به خلاصه مدیریتی، ویراستاری حقوقی و تصمیمات کلیدی.",
                    color = TextSub,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // API Key Field
                OutlinedTextField(
                    value = inputKey,
                    onValueChange = {
                        inputKey = it
                        onSaveApiKey(it)
                    },
                    label = { Text("کلید اختصاصی Gemini API Key", fontSize = 11.sp) },
                    placeholder = { Text("AIzaSy... (ذخیره امن فقط در حافظه محلی)") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Generate Button
                Button(
                    onClick = onRunAiSummary,
                    enabled = !isAiLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_ai_summary_button")
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال تدوین خلاصه با Gemini 2.5 Flash...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✨ تولید خلاصه مدیریتی و ویراستاری هوشمند", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (aiError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "خطا: $aiError",
                        color = AccentRose,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // --- Gemini Automatic Analysis of Typed Meeting Text (صورت‌جلسه، تصمیمات اتخاذ شده و وظایف محول شده به هر شخص) ---
        item {
            val effectiveAnalysis = typedMinutesAnalysis ?: session.typedMinutesAnalysis
            QuantumPanel(
                title = "🧠 تحلیل خودکار متن تایپ‌شده جلسه با Gemini API (صورت‌جلسه، تصمیمات و وظایف اشخاص)",
                badgeText = if (isAnalyzingTypedMinutes) "⏳ در حال تحلیل..." else if (effectiveAnalysis != null) "✅ تحلیل تکمیل شد" else "Gemini 2.5 Flash",
                modifier = Modifier.testTag("ai_typed_minutes_analyzer_panel")
            ) {
                Text(
                    text = "متن تایپ‌شده از جلسات (تایپ صوتی Speech-to-Text یا یادداشت دستی) به صورت خودکار توسط Gemini API واکاوی شده و ۱) متن رسمی صورت‌جلسه، ۲) تصمیمات اتخاذ شده و ۳) وظایف محول شده به هر شخص استخراج و در دیتابیس Room ثبت می‌گردد.",
                    color = TextSub,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = typedTextForGeminiAnalysis,
                    onValueChange = {
                        typedTextForGeminiAnalysis = it
                        onUpdateLiveTypedMinutesText(it)
                    },
                    label = { Text("متن تایپ‌شده جلسه جهت تحلیل با Gemini API", fontSize = 10.5.sp) },
                    placeholder = {
                        Text(
                            "مثال: مصوب شد سامانه پایش برخط دمای ناوگان زنجیره سرد با مسئولیت سخنران تا پایان هفته عملیاتی گردد؛ مقرر شد مدیر مالی متمم بودجه تجهیز سنسورها را تا فردا ارائه نماید...",
                            fontSize = 10.sp
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_typed_minutes_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onAnalyzeTypedMinutesWithGemini(typedTextForGeminiAnalysis) },
                    enabled = !isAnalyzingTypedMinutes,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("ai_run_typed_minutes_analysis_btn")
                ) {
                    if (isAnalyzingTypedMinutes) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("در حال تحلیل متن تایپ‌شده با Gemini...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✨ تحلیل خودکار متن تایپ‌شده و استخراج صورت‌جلسه، تصمیمات و وظایف اشخاص", fontSize = 10.8.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                if (!typedMinutesAnalysisError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "خطا: $typedMinutesAnalysisError", color = AccentRose, fontSize = 10.5.sp)
                }

                if (effectiveAnalysis != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgInput)
                            .border(1.dp, AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("ai_typed_minutes_analysis_output"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📄 ۱. صورت‌جلسه رسمی استخراج‌شده:",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = effectiveAnalysis.formalMinutesText,
                            color = TextMain,
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        Text(
                            text = "⚖️ ۲. تصمیمات اتخاذ شده (${effectiveAnalysis.decisionsMade.size} مورد):",
                            color = AccentEmeraldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        effectiveAnalysis.decisionsMade.forEachIndexed { idx, dec ->
                            Text(
                                text = "• مصوبه ${idx + 1}: $dec",
                                color = TextMain,
                                fontSize = 10.2.sp,
                                lineHeight = 15.sp
                            )
                        }

                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        Text(
                            text = "👥 ۳. وظایف محول شده به هر شخص (${effectiveAnalysis.assignedTasksByPerson.size} وظیفه):",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        effectiveAnalysis.assignedTasksByPerson.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(7.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "👤 ${task.personName} (${task.personRole})",
                                        color = AccentPurpleLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = task.taskTitle,
                                        color = TextMain,
                                        fontSize = 10.sp,
                                        lineHeight = 14.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${task.deadline}\n(${task.priority})",
                                    color = if (task.priority == "فوری") AccentRose else AccentCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Gemini Assignee & Action Item Extraction -> Auto-Add to Calendar & Member Task List in Room ---
        item {
            QuantumPanel(
                title = "📋 استخراج هوشمند مسئولین و وظایف محوله با Gemini (ثبت خودکار در تقویم و دیتابیس اعضا)",
                badgeText = if (isExtractingMemberTasks) "⏳ در حال تحلیل..." else "${extractedMemberTasks.size} وظیفه در Room",
                modifier = Modifier.testTag("ai_extract_member_tasks_panel")
            ) {
                Text(
                    text = "تحلیل متن کامل جلسه (یادداشت‌ها، مذاکرات صوتی یا متن دلخواه) با هوش مصنوعی Gemini جهت شناسایی نام افراد مسئول (Assignees) و وظایف محوله (Action Items) و افزودن خودکار به تقویم سازمانی و لیست وظایف اعضا در دیتابیس Room.",
                    color = TextSub,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-sync to Calendar & Room Database Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (autoSyncExtractedTasksToDbAndCalendar) AccentEmerald.copy(alpha = 0.12f) else BgInput)
                        .border(
                            1.dp,
                            if (autoSyncExtractedTasksToDbAndCalendar) AccentEmerald.copy(alpha = 0.45f) else BorderSubtle,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = if (autoSyncExtractedTasksToDbAndCalendar) AccentEmeraldLight else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "افزودن خودکار وظایف استخراج‌شده به تقویم و جدول member_tasks در دیتابیس Room",
                            color = TextMain,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = autoSyncExtractedTasksToDbAndCalendar,
                        onCheckedChange = onAutoSyncExtractedTasksChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentEmerald
                        ),
                        modifier = Modifier.testTag("auto_sync_tasks_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Meeting Text Input for Direct Analysis
                OutlinedTextField(
                    value = customMeetingTextForTasks,
                    onValueChange = { customMeetingTextForTasks = it },
                    label = { Text("متن جلسه یا صورت‌مذاکرات جهت استخراج وظایف (اختیاری - در صورت خالی بودن از متن جلسه فعال استفاده می‌شود)", fontSize = 10.sp) },
                    placeholder = { Text("مثال: مقرر شد سخنران تا ۲۰ آبان کالیبراسیون سنسورهای دمایی را تکمیل نماید؛ مدیر مالی تا فردا متمم بودجه را ارسال کند...", fontSize = 10.5.sp) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_meeting_text_for_tasks_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onExtractAndAutoAssignMemberTasks(customMeetingTextForTasks) },
                    enabled = !isExtractingMemberTasks,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("extract_and_save_member_tasks_btn")
                ) {
                    if (isExtractingMemberTasks) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("در حال استخراج مسئولین و ثبت در تقویم و دیتابیس Room...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✨ استخراج مسئولین و وظایف با Gemini + ثبت خودکار در تقویم و دیتابیس", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                memberTaskExtractionError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "خطا: $err", color = AccentRose, fontSize = 11.sp)
                }

                if (extractedMemberTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val distinctAssignees = extractedMemberTasks.map { it.assigneeName }.distinct()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👥 لیست وظایف اعضا و زمان‌بندی تقویم (${extractedMemberTasks.size} وظیفه برای ${distinctAssignees.size} عضو):",
                            color = AccentEmeraldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "💾 جدول member_tasks",
                            color = AccentCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("extracted_member_tasks_list")
                    ) {
                        extractedMemberTasks.forEach { task ->
                            val priorityColor = when (task.priority) {
                                "فوری" -> AccentRose
                                "بالا" -> AccentAmber
                                else -> AccentCyan
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (task.isDone) AccentEmerald.copy(alpha = 0.08f) else BgInput
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (task.isDone) AccentEmerald.copy(alpha = 0.45f) else BorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("member_task_card_${task.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = task.isDone,
                                                onCheckedChange = { onToggleMemberTaskDone(task) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = AccentEmerald,
                                                    uncheckedColor = AccentPurpleLight
                                                ),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "👤 مسئول: ${task.assigneeName}",
                                                    color = AccentCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                if (task.assigneeRole.isNotBlank()) {
                                                    Text(
                                                        text = task.assigneeRole,
                                                        color = TextMuted,
                                                        fontSize = 9.5.sp
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(priorityColor.copy(alpha = 0.16f))
                                                    .border(1.dp, priorityColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "اولویت: ${task.priority}",
                                                    color = priorityColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            IconButton(
                                                onClick = { onDeleteMemberTask(task.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "حذف وظیفه",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = task.taskTitle,
                                        color = TextMain,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 17.sp
                                    )

                                    if (task.taskDescription.isNotBlank() && task.taskDescription != task.taskTitle) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = task.taskDescription,
                                            color = TextSub,
                                            fontSize = 10.sp,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(AccentPurple.copy(alpha = 0.16f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "📅 تقویم: ${task.calendarDate} (${task.deadlineLabel})",
                                                    color = AccentPurpleLight,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(AccentEmerald.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (task.isDone) "✅ تکمیل‌شده" else "📌 ثبت در تقویم و کارتابل",
                                                    color = AccentEmeraldLight,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // System Calendar Intent Button
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                                        data = CalendarContract.Events.CONTENT_URI
                                                        putExtra(CalendarContract.Events.TITLE, "[وظیفه ${task.assigneeName}] ${task.taskTitle}")
                                                        putExtra(
                                                            CalendarContract.Events.DESCRIPTION,
                                                            "مسئول: ${task.assigneeName} (${task.assigneeRole})\nمهلت: ${task.deadlineLabel} (${task.calendarDate})\nجلسه: ${task.sessionTitle}"
                                                        )
                                                    }
                                                    context.startActivity(calIntent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "وظیفه در تقویم داخلی برنامه (تاریخ ${task.calendarDate}) ثبت شده است.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ارسال به تقویم گوشی", color = AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Transcribed Audio Analysis & Action Items Panel in AI Tab ---
        item {
            QuantumPanel(
                title = "🎙️ خلاصه‌سازی صوت پیاده‌سازی‌شده و استخراج تعهدات (Action Items)",
                badgeText = if (isTranscriptionAiLoading) "⏳ در حال پردازش..." else "${session.transcriptions.size} فراز صوتی"
            ) {
                Text(
                    text = "ارسال کل متن مذاکرات ضبط‌شده صوتی به Gemini API جهت استخراج وظایف محوله به اعضا، زمان‌بندی اقدام و خلاصه گفت‌وگوها.",
                    color = TextSub,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onAnalyzeTranscriptionsWithGemini,
                    enabled = !isTranscriptionAiLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple.copy(alpha = 0.25f),
                        contentColor = AccentPurpleLight
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("ai_tab_transcription_analyze_btn")
                ) {
                    if (isTranscriptionAiLoading) {
                        CircularProgressIndicator(color = AccentPurpleLight, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("در حال پردازش صوت با Gemini...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استخراج خودکار خلاصه و تعهدات اجرایی از صوت", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                transcriptionAiError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "خطا: $err", color = AccentRose, fontSize = 11.sp)
                }

                // Render Action Items if available
                transcriptionAiAnalysis?.let { analysis ->
                    Spacer(modifier = Modifier.height(12.dp))

                    if (analysis.executiveSummary.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BgInput),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("خلاصه مذاکرات صوتی:", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysis.executiveSummary, color = TextMain, fontSize = 11.5.sp, lineHeight = 18.sp)
                            }
                        }
                    }

                    if (analysis.actionItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تعهدات اجرایی استخراج‌شده (${analysis.actionItems.size}):",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (analysis.actionItems.any { !it.isAddedToNotes }) {
                                OutlinedButton(
                                    onClick = onAddAllActionItemsToNotes,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(0.5f)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("ثبت تمام تعهدات در مصوبات", color = AccentAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            analysis.actionItems.forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BgInput)
                                        .border(1.dp, if (item.isAddedToNotes) AccentEmerald.copy(0.4f) else BorderSubtle, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("${item.assignee} | مهلت: ${item.deadline} | اولویت: ${item.priority}", color = TextSub, fontSize = 9.5.sp)
                                    }

                                    if (item.isAddedToNotes) {
                                        Text("✅ ثبت شد", color = AccentEmeraldLight, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Button(
                                            onClick = { onAddActionItemToNotes(item) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("ثبت در مصوبه", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Multimodal Audio AI Analysis Card in AI Tab ---
        val audioFileExists = session.audioFilePath?.let { java.io.File(it).exists() } == true || audioMeetingAnalysis != null
        if (audioFileExists) {
            item {
                QuantumPanel(
                    title = "🎙️ پردازش چندحالته فایل صوتی ضبط‌شده با Gemini",
                    badgeText = if (isAudioProcessingWithGemini) "⏳ در حال پردازش..." else "صوت پیوست‌شده"
                ) {
                    Text(
                        text = "ارسال مستقیم فایل صوتی ضبط‌شده از میکروفون به مدل هوش مصنوعی Gemini 2.5 Flash جهت پیاده‌سازی کلمه به کلمه متن (STT)، تدوین خلاصه اجرایی و استخراج تصمیمات.",
                        color = TextSub,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onProcessRecordedAudioWithGemini,
                        enabled = !isAudioProcessingWithGemini,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("ai_tab_process_audio_btn")
                    ) {
                        if (isAudioProcessingWithGemini) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("در حال پیاده‌سازی و خلاصه‌سازی صوت با Gemini...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✨ ارسال فایل صوتی به Gemini جهت تبدیل به متن و خلاصه", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    audioProcessingError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(err, color = AccentRose, fontSize = 11.sp)
                            }
                        }
                    }

                    val analysis = audioMeetingAnalysis ?: session.latestAudioAnalysis
                    if (analysis != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("📝 متن پیاده‌سازی‌شده صوت توسط Gemini:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(analysis.fullTranscription, color = TextMain, fontSize = 11.5.sp, lineHeight = 18.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", analysis.fullTranscription))
                                            Toast.makeText(context, "متن در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("کپی متن", fontSize = 9.5.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("🎯 خلاصه مدیریتی استخراج‌شده:", color = AccentEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(analysis.executiveSummary, color = TextMain, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }

        // --- Sentiment Analysis & High-Tension Flagging Module ---
        item {
            val effectiveSentiment = audioSentimentAnalysis
                ?: session.audioSentimentAnalysis
                ?: session.latestAudioAnalysis?.sentimentAnalysis

            AudioSentimentAnalysisComponent(
                sentimentAnalysis = effectiveSentiment,
                isAnalyzingSentiment = isAnalyzingSentiment,
                sentimentError = sentimentAnalysisError,
                hasRecordedAudio = session.audioFilePath != null,
                onAnalyzeSentiment = onAnalyzeAudioSentiment,
                onToggleReviewed = onToggleSegmentReviewed,
                onAddResolutionFromSegment = onAddResolutionFromSegment,
                onClearSentiment = onClearSentimentAnalysis
            )
        }

        // Summary Output Area
        item {
            val effectiveSentiment = audioSentimentAnalysis
                ?: session.audioSentimentAnalysis
                ?: session.latestAudioAnalysis?.sentimentAnalysis
            val unreviewedTensionCount = effectiveSentiment?.flaggedSegments?.count { !it.isReviewed } ?: 0

            QuantumPanel(
                title = "خروجی ویراسته هوش مصنوعی",
                badgeText = if (unreviewedTensionCount > 0) "⚠️ $unreviewedTensionCount بخش تنش نیازمند بازبینی" else "درج مستقیم در صورتجلسه"
            ) {
                if (unreviewedTensionCount > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("executive_tension_alert_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AccentRose, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "هشدار حاکمیتی: بخش‌های پرتنش حل‌نشده در جلسه وجود دارد!",
                                    color = AccentRose,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "جهت تضمین انضباط اداری، لطفاً پیش از نهایی‌سازی صورتجلسه، بخش‌های پرچم‌گذاری‌شده بالا را بازبینی کرده یا دکمه «درج در مصوبات» را لمس نمایید.",
                                    color = TextMain,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "متن قابل ویرایش جهت صورتجلسه نهایی:",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Copy button
                        IconButton(
                            onClick = {
                                if (session.aiSummary.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Summary", session.aiSummary))
                                    Toast.makeText(context, "متن در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "کپی", tint = AccentCyan, modifier = Modifier.size(16.dp))
                        }

                        // Clear button
                        IconButton(
                            onClick = { onUpdateAiSummary("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "پاکسازی", tint = TextSub, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = session.aiSummary,
                    onValueChange = onUpdateAiSummary,
                    placeholder = {
                        Text(
                            text = "پس از زدن دکمه بالا، جمع‌بندی رسمی، تصمیمات راهبردی و تحلیل حقوقی هوش مصنوعی در اینجا قرار می‌گیرد و می‌توانید آن را مستقیماً ویرایش نمایید...",
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_summary_output"),
                    minLines = 8,
                    maxLines = 14,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // PDF Export Action Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("ai_pdf_export_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(16.dp))
                                Text("📄 خروجی رسمی PDF جهت ارائه به ذینفعان", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("A4 وکتور", color = AccentEmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تولید فایل پیش‌نویس صورت‌جلسه رسمی (PDF) با الگوی هوش مصنوعی Gemini بر اساس نکات کلیدی، مصوبات استخراج‌شده و تعهدات اعضا به همراه قابلیت اشتراک‌گذاری مستقیم.",
                            color = TextSub,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )

                        val activeDraft = officialMinutesDraft ?: session.officialMinutesDraft
                        if (activeDraft != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgInput)
                                    .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "✅ ${activeDraft.draftTitle} (${activeDraft.documentNumber})",
                                    color = AccentEmeraldLight,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "نکات کلیدی: ${activeDraft.keyPointsSummary.size} مورد • مصوبات رسمی: ${activeDraft.formalResolutions.size} بند • تعهدات: ${activeDraft.assignedCommitments.size} مورد",
                                    color = TextSub,
                                    fontSize = 9.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onGenerateOfficialDraftPdf(false) },
                                enabled = !isGeneratingMinutesDraft,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPurple,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(42.dp)
                                    .testTag("ai_generate_official_draft_pdf_btn")
                            ) {
                                if (isGeneratingMinutesDraft) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("در حال تولید...", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("تولید پیش‌نویس PDF با Gemini", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    if (activeDraft != null) {
                                        onShareOfficialDraftPdf()
                                    } else {
                                        onExportPdf()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentEmerald,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(0.95f)
                                    .height(42.dp)
                                    .testTag("ai_export_pdf_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("اشتراک‌گذاری PDF", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
