package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.FileProvider
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.AudioMeetingAnalysis
import com.example.data.MeetingNote
import com.example.data.MeetingSession
import com.example.data.TranscriptionSegment
import com.example.service.SilenceDetectionStatus
import com.example.ui.components.LiveTranscriptionWaveformOverlay
import com.example.ui.components.MicSensitivityControlPanel
import com.example.ui.components.QuantumPanel
import com.example.ui.components.QrCheckInDialog
import com.example.ui.components.SilenceDetectionControlCard
import com.example.ui.components.evaluateRecordingQuality
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

@Composable
fun LiveMeetingScreen(
    session: MeetingSession,
    isTimerRunning: Boolean,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    isAudioRecording: Boolean,
    onToggleAudioRecording: () -> Unit,
    recordedAudioFile: File?,
    isListening: Boolean,
    onToggleSpeechRecognition: ((String) -> Unit) -> Unit,
    isContinuousTranscribing: Boolean = false,
    onToggleContinuousTranscription: () -> Unit = {},
    interimTranscript: String = "",
    audioRmsLevel: Float = 0f,
    speechLanguage: String = "fa-IR",
    onLanguageChange: (String) -> Unit = {},
    activeTranscribingSpeaker: String = "سخنران",
    onTranscribingSpeakerChange: (String) -> Unit = {},
    onCommitTranscriptionSegment: (String) -> Unit = {},
    onCommitAllTranscriptions: () -> Unit = {},
    onRemoveTranscriptionSegment: (String) -> Unit = {},
    onClearAllTranscriptions: () -> Unit = {},
    onAddManualTranscription: (String, String) -> Unit = { _, _ -> },
    onExportTranscriptionText: () -> String = { "" },
    speechError: String? = null,
    isTranscriptionAiLoading: Boolean = false,
    transcriptionAiAnalysis: com.example.data.TranscriptionAiAnalysis? = null,
    transcriptionAiError: String? = null,
    onAnalyzeTranscriptionsWithGemini: () -> Unit = {},
    onAddActionItemToNotes: (com.example.data.ExtractedActionItem) -> Unit = {},
    onAddAllActionItemsToNotes: () -> Unit = {},
    onSaveTranscriptionSummaryAsMainAiSummary: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    recordingDurationSec: Long = 0L,
    recordingAmplitude: Float = 0f,
    micInputSensitivity: Float = 0.70f,
    onMicInputSensitivityChange: (Float) -> Unit = {},
    silenceDetectionStatus: SilenceDetectionStatus = SilenceDetectionStatus(),
    onSilenceDetectionEnabledChange: (Boolean) -> Unit = {},
    onForceResumeFromSilence: () -> Unit = {},
    onSimulateSampleSpeechCheck: (() -> Unit)? = null,
    isAudioProcessingWithGemini: Boolean = false,
    audioProcessingError: String? = null,
    audioMeetingAnalysis: com.example.data.AudioMeetingAnalysis? = null,
    onProcessRecordedAudioWithGemini: () -> Unit = {},
    onClearAudioMeetingAnalysis: () -> Unit = {},
    autoSummarizeOnRecordingStop: Boolean = true,
    onAutoSummarizeOnRecordingStopChange: (Boolean) -> Unit = {},
    latestRoomExecutiveSummary: com.example.data.local.ExecutiveSummaryEntity? = null,
    onAddNote: (speaker: String, text: String, type: String, due: String) -> Unit,
    onToggleNoteDone: (String) -> Unit,
    onRemoveNote: (String) -> Unit,
    onNavigateToAi: () -> Unit,
    onCheckInByQr: (String) -> Pair<Boolean, String>,
    onCheckInById: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showQrDialog by remember { mutableStateOf(false) }

    // Microphone runtime permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onToggleAudioRecording()
        } else {
            Toast.makeText(context, "جهت ضبط مکالمات جلسه، مجوز دسترسی به میکروفون الزامی است.", Toast.LENGTH_LONG).show()
        }
    }

    fun handleRecordClick() {
        if (isAudioRecording) {
            onToggleAudioRecording()
        } else {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onToggleAudioRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    var selectedSpeaker by remember(session.members) {
        mutableStateOf(session.members.firstOrNull { it.isPresent }?.name ?: "سخنران")
    }
    var selectedType by remember { mutableStateOf("auto") }
    var dueDateText by remember { mutableStateOf("") }
    var noteInputText by remember { mutableStateOf("") }

    var speakerDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    // Pulsing animation for audio recording / voice listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val noteTypeLabels = mapOf(
        "auto" to "🤖 تشخیص هوشمند",
        "🚨 مصوبه رسمی" to "🚨 مصوبه رسمی",
        "📌 تعهد اجرایی" to "📌 تعهد اجرایی",
        "💰 مصوبه مالی" to "💰 مصوبه مالی",
        "💡 نکته کارشناسی" to "💡 نکته کارشناسی"
    )

    // Format clock
    val formattedClock = remember(session.elapsedSeconds) {
        val s = maxOf(0L, session.elapsedSeconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- 1. Timer & Audio Controls Panel ---
        item {
            QuantumPanel {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "بازه تخصیص‌یافته: ${session.durationMins} دقیقه",
                        color = AccentPurpleLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Digital Clock
                    Text(
                        text = formattedClock,
                        color = if (isTimerRunning) AccentEmeraldLight else TextMain,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timer Start/Stop
                        Button(
                            onClick = onToggleTimer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTimerRunning) AccentRose else AccentPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("toggle_timer_btn")
                        ) {
                            Icon(
                                imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTimerRunning) "■ توقف تایمر" else "⏱️ شروع تایمر",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Audio Recording Button (With Permission Check)
                        Button(
                            onClick = { handleRecordClick() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAudioRecording) AccentRose else BgInput,
                                contentColor = if (isAudioRecording) Color.White else AccentEmeraldLight
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAudioRecording) AccentRose else AccentEmerald.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .testTag("toggle_audio_record_btn")
                                .scale(if (isAudioRecording) pulseScale else 1f)
                        ) {
                            Icon(
                                imageVector = if (isAudioRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAudioRecording) "⏹️ توقف ضبط" else "🎙️ ضبط فایل صوتی",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Reset button
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onResetTimer,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "ریست تایمر", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Microphone Input Sensitivity Slider & Conference Room Noise Gate
                    MicSensitivityControlPanel(
                        micInputSensitivity = micInputSensitivity,
                        onMicInputSensitivityChange = onMicInputSensitivityChange,
                        sliderTestTag = "live_mic_sensitivity_slider"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Automatic 5-Second Silence Detection Card
                    SilenceDetectionControlCard(
                        isAudioRecording = isAudioRecording,
                        silenceDetectionStatus = silenceDetectionStatus,
                        onSilenceDetectionEnabledChange = onSilenceDetectionEnabledChange,
                        onForceResumeFromSilence = onForceResumeFromSilence,
                        switchTestTag = "live_silence_detection_switch"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto-Generate & Save Executive Summary to Room DB on Recording Stop Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (autoSummarizeOnRecordingStop) AccentEmerald.copy(alpha = 0.10f) else BgInput
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (autoSummarizeOnRecordingStop) AccentEmerald.copy(alpha = 0.45f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_summarize_on_stop_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (autoSummarizeOnRecordingStop) AccentEmeraldLight else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "خلاصه اجرایی خودکار پس از پایان ضبط (Gemini + Room)",
                                        color = TextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "بلافاصله پس از توقف ضبط صوت، خلاصه اجرایی مباحث و تصمیمات با Gemini تولید و در دیتابیس Room ذخیره می‌شود.",
                                    color = TextSub,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = autoSummarizeOnRecordingStop,
                                onCheckedChange = onAutoSummarizeOnRecordingStopChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentEmerald
                                ),
                                modifier = Modifier.testTag("auto_summarize_on_stop_switch")
                            )
                        }
                    }

                    // Live Audio Recording Card with Dynamic Waveform and Duration
                    if (isAudioRecording) {
                        val isPausedBySilence = silenceDetectionStatus.isAutoPaused
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPausedBySilence) AccentGold.copy(alpha = 0.14f) else AccentRose.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPausedBySilence) AccentGold.copy(alpha = 0.5f) else AccentRose.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("live_audio_recording_card")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isPausedBySilence) AccentGold else AccentRose)
                                                .scale(if (isPausedBySilence) 1f else pulseScale)
                                        )
                                        Text(
                                            text = if (isPausedBySilence) {
                                                "⏸️ مکث خودکار پس از ۵ ثانیه سکوت (آماده ادامه با صحبت)..."
                                            } else {
                                                "در حال ضبط صحبت‌های جلسه با میکروفون..."
                                            },
                                            color = if (isPausedBySilence) AccentGold else AccentRose,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    val mins = recordingDurationSec / 60
                                    val secs = recordingDurationSec % 60
                                    Text(
                                        text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                        color = AccentRose,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Dynamic Audio Waveform Equalizer
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val barCount = 20
                                    for (i in 0 until barCount) {
                                        val factor = ((i * 3 + (System.currentTimeMillis() / 150)) % 5 + 1) / 5f
                                        val heightFrac = (0.2f + (recordingAmplitude * factor * 0.8f)).coerceIn(0.15f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight(heightFrac)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (isPausedBySilence) AccentGold else AccentRose)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val liveQuality = evaluateRecordingQuality(
                                    isCapturing = isAudioRecording,
                                    isAutoPausedBySilence = isPausedBySilence,
                                    effectiveSignal = recordingAmplitude.coerceAtLeast(0.32f),
                                    micSensitivity = micInputSensitivity,
                                    interimTranscript = interimTranscript,
                                    recentTranscriptions = session.transcriptions
                                )

                                LiveTranscriptionWaveformOverlay(
                                    isCapturing = isAudioRecording,
                                    isAutoPausedBySilence = isPausedBySilence,
                                    activeSpeaker = activeTranscribingSpeaker,
                                    interimTranscript = interimTranscript,
                                    recentTranscriptions = session.transcriptions,
                                    qualityAssessment = liveQuality,
                                    onToggleContinuousTranscription = onToggleContinuousTranscription,
                                    isContinuousTranscribing = isContinuousTranscribing,
                                    onSimulateSampleSpeechCheck = onSimulateSampleSpeechCheck,
                                    overlayTestTag = "live_screen_transcription_waveform_overlay"
                                )
                            }
                        }
                    }

                    // Multimodal Audio Processing with Gemini Card
                    val audioFile = recordedAudioFile ?: session.audioFilePath?.let { File(it) }
                    if ((audioFile != null && audioFile.exists()) || latestRoomExecutiveSummary != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.10f)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().testTag("gemini_audio_manager_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "🎙️ مدیریت صوت و خلاصه اجرایی (Gemini + Room)",
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (audioFile != null && audioFile.exists()) {
                                        val sizeKb = (audioFile.length() / 1024)
                                        val sizeText = if (sizeKb > 1024) "${String.format(Locale.US, "%.1f", sizeKb / 1024f)} MB" else "$sizeKb KB"
                                        val durSec = session.audioDurationSec
                                        val durText = if (durSec > 0) " • ${durSec / 60}:${String.format(Locale.US, "%02d", durSec % 60)}" else ""
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(BgInput)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$sizeText$durText",
                                                color = AccentCyan,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "پس از پایان ضبط صوت، مدل Gemini 2.5 Flash خلاصه اجرایی (Executive Summary) از مباحث و تصمیمات اتخاذشده را تولید کرده و در جدول executive_summaries دیتابیس Room ذخیره می‌کند.",
                                    color = TextSub,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Send Audio to Gemini Button
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
                                        .height(42.dp)
                                        .testTag("send_audio_to_gemini_btn")
                                ) {
                                    if (isAudioProcessingWithGemini) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("در حال تولید خلاصه اجرایی با Gemini و ذخیره در Room...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("✨ بازتولید خلاصه اجرایی صوت با Gemini و ذخیره در Room", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (audioFile != null && audioFile.exists()) {
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Audio Share button
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    audioFile
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "audio/*"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "اشتراک فایل صوتی جلسه"))
                                            } catch (e: Exception) {
                                                try {
                                                    val uri = Uri.fromFile(audioFile)
                                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "audio/*")
                                                    }
                                                    context.startActivity(viewIntent)
                                                } catch (ex: Exception) {}
                                            }
                                        },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اشتراک و ارسال فایل صوتی", color = AccentCyan, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Error Banner
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

                                // Display Audio Analysis / Room-Persisted Executive Summary if available
                                val analysis = audioMeetingAnalysis
                                    ?: session.latestAudioAnalysis
                                    ?: latestRoomExecutiveSummary?.toAudioMeetingAnalysis()
                                if (analysis != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Room Database Persistence Status Badge
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentEmerald.copy(alpha = 0.14f))
                                            .border(1.dp, AccentEmerald.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("room_executive_summary_badge"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = null,
                                                tint = AccentEmeraldLight,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "💾 ذخیره‌شده در دیتابیس Room (ExecutiveSummaryEntity)",
                                                color = AccentEmeraldLight,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                        if (latestRoomExecutiveSummary?.isAutoGeneratedOnStop == true) {
                                            Text(
                                                text = "⚡ خودکار پس از ضبط",
                                                color = AccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        }
                                    }

                                    // Executive Summary Display
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "🎯 خلاصه اجرایی مباحث و تصمیمات (Executive Summary):",
                                        color = AccentEmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BgInput)
                                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                            .testTag("post_recording_executive_summary_box")
                                    ) {
                                        Column {
                                            Text(
                                                text = analysis.executiveSummary,
                                                color = TextMain,
                                                fontSize = 11.5.sp,
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(analysis.executiveSummary))
                                                        Toast.makeText(context, "خلاصه اجرایی کپی شد.", Toast.LENGTH_SHORT).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("کپی خلاصه اجرایی", fontSize = 9.5.sp)
                                                }
                                            }
                                        }
                                    }

                                    // Key Decisions Display
                                    if (analysis.keyDecisions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "⚖️ تصمیمات کلیدی اتخاذشده در جلسه (${analysis.keyDecisions.size} مورد):",
                                            color = AccentGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.testTag("post_recording_key_decisions_list")
                                        ) {
                                            analysis.keyDecisions.forEachIndexed { idx, decision ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(BgInput)
                                                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}. ",
                                                        color = AccentGold,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = decision,
                                                        color = TextMain,
                                                        fontSize = 11.sp,
                                                        lineHeight = 16.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Full Transcription Display
                                    if (analysis.fullTranscription.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("📝 متن کامل پیاده‌سازی‌شده از صوت (Gemini STT):", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                                                Text(
                                                    text = analysis.fullTranscription,
                                                    color = TextMain,
                                                    fontSize = 11.5.sp,
                                                    lineHeight = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(analysis.fullTranscription))
                                                            Toast.makeText(context, "متن پیاده‌سازی‌شده کپی شد.", Toast.LENGTH_SHORT).show()
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
                                    }

                                    // Action Items if any
                                    if (analysis.actionItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("📋 تعهدات و اقدامات اجرایی استخراج‌شده (${analysis.actionItems.size}):", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            analysis.actionItems.forEach { item ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(BgInput)
                                                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                                        .padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.title, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        Text("مسئول: ${item.assignee} | مهلت: ${item.deadline} | اولویت: ${item.priority}", color = TextSub, fontSize = 9.5.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // QR Check-in Hub Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Text(
                                text = "پذیرش QR اعضا:",
                                color = TextMain,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val presentCount = session.members.count { it.isPresent }
                            val totalCount = session.members.size
                            Text(
                                text = "$presentCount از $totalCount حاضر",
                                color = if (presentCount == totalCount && totalCount > 0) AccentEmeraldLight else AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { showQrDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPurple.copy(alpha = 0.2f),
                                contentColor = AccentPurpleLight
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("open_qr_checkin_btn")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("دوربین اسکن QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 1.5. Live QR Scanner & Members Attendance Table Module (ZXing) ---
        item {
            var manualQrInput by remember { mutableStateOf("") }
            var lastScanFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

            QuantumPanel(
                title = "📷 ماژول اسکن بارکد QR و جدول حضور زنده اعضا",
                badgeText = "ZXing Engine"
            ) {
                // Live Members Attendance Table
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgInput),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("نام و سمت عضو", color = TextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                            Text("واحد سازمانی", color = TextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("وضعیت / اسکن", color = TextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        session.members.forEachIndexed { idx, m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.4f)) {
                                    Text(
                                        text = m.name.ifBlank { "عضو ${idx + 1}" },
                                        color = TextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = m.title,
                                        color = AccentPurpleLight,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = m.unit,
                                    color = TextSub,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                if (m.isPresent) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentEmerald.copy(alpha = 0.18f))
                                            .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✅ حاضر",
                                            color = AccentEmeraldLight,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            onCheckInById(m.id)
                                            lastScanFeedback = Pair(true, "حضور «${m.name}» با اسکن ZXing در جدول ثبت شد.")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentCyan.copy(alpha = 0.18f),
                                            contentColor = AccentCyan
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(26.dp)
                                            .testTag("live_qr_scan_member_$idx")
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("اسکن QR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (idx < session.members.lastIndex) {
                                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Direct QR Payload / Scanner Bar + Camera Launch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = manualQrInput,
                        onValueChange = { manualQrInput = it },
                        placeholder = { Text("کد QR یا نام عضو جهت ثبت خودکار حضور...", fontSize = 10.5.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_qr_payload_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            if (manualQrInput.isNotBlank()) {
                                lastScanFeedback = onCheckInByQr(manualQrInput)
                                manualQrInput = ""
                            } else {
                                showQrDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = BgCanvas
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("live_verify_qr_btn")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (manualQrInput.isBlank()) "اسکن دوربین" else "ثبت QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick ZXing Session QR Decode Button (Scans the Setup tab's Session QR Bitmap using ZXing MultiFormatReader)
                OutlinedButton(
                    onClick = {
                        val sessionPayload = com.example.util.QrCodeGenerator.createSessionCheckInPayload(session)
                        val sessionBitmap = com.example.util.QrCodeGenerator.generateQrBitmap(sessionPayload, 300)
                        val decodedText = sessionBitmap?.let { com.example.util.QrCodeGenerator.decodeQrBitmap(it) } ?: sessionPayload
                        lastScanFeedback = onCheckInByQr(decodedText)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmeraldLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("scan_session_master_qr_btn")
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اسکن خودکار بارکد یکتای جلسه (ثبت حضور کل اعضا با ZXing)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                lastScanFeedback?.let { (ok, msg) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (ok) AccentEmeraldLight else AccentRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- 1.8. Real-Time Voice-to-Text Transcription Panel ---
        item {
            var manualTranscriptInput by remember { mutableStateOf("") }
            var showManualInput by remember { mutableStateOf(false) }

            QuantumPanel(
                title = "🎙️ پیاده‌سازی زنده صوت به متن مذاکرات (Real-Time Transcription)",
                badgeText = if (isContinuousTranscribing) "🔴 جریان زنده میکروفون" else "موتور هوشمند گفتار"
            ) {
                // Status Header & Language Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isContinuousTranscribing) AccentRose else AccentEmeraldLight)
                                .scale(if (isContinuousTranscribing) pulseScale else 1f)
                        )
                        Text(
                            text = if (isContinuousTranscribing) "در حال شنود زنده و رونویسی پیوسته..." else "میکروفون در حالت آماده‌باش",
                            color = if (isContinuousTranscribing) AccentRose else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Language Selector Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("fa-IR" to "فارسی", "en-US" to "English").forEach { (code, label) ->
                            val isSel = speechLanguage == code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) AccentPurple else BgInput)
                                    .border(1.dp, if (isSel) AccentPurpleLight else BorderSubtle, RoundedCornerShape(6.dp))
                                    .clickable { onLanguageChange(code) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-Time Audio Equalizer / Waveform
                LiveAudioWaveform(
                    isActive = isContinuousTranscribing || isListening,
                    rmsLevel = audioRmsLevel
                )

                // Real-Time Interim Subtitles (Live Stream of words as they are pronounced)
                AnimatedVisibility(visible = interimTranscript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "جریان بلادرنگ گفتار ($activeTranscribingSpeaker):",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "«$interimTranscript ▍»",
                                    color = TextMain,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Main Toggle & Export Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Big Toggle Button
                    Button(
                        onClick = onToggleContinuousTranscription,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isContinuousTranscribing) AccentRose else AccentPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .scale(if (isContinuousTranscribing) pulseScale else 1f)
                            .testTag("toggle_live_continuous_speech_btn")
                    ) {
                        Icon(
                            imageVector = if (isContinuousTranscribing) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isContinuousTranscribing) "⏹️ توقف تبدیل زنده صوت" else "🎙️ شروع پیاده‌سازی زنده صوت",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Copy Full Transcript
                    OutlinedButton(
                        onClick = {
                            val transcriptText = onExportTranscriptionText()
                            clipboardManager.setText(AnnotatedString(transcriptText))
                            Toast.makeText(context, "متن کامل مذاکرات در کلیپ‌بورد کپی شد.", Toast.LENGTH_SHORT).show()
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("copy_transcription_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("کپی متن", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Automatic Gemini Transcript Summarization on Stop Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgInput)
                        .border(
                            1.dp,
                            if (autoSummarizeOnRecordingStop) AccentPurple.copy(alpha = 0.55f) else BorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("realtime_stt_auto_summarize_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentPurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "تلخیص خودکار رونوشت به تصمیمات و اقدامات بلافاصله پس از توقف ضبط (Gemini)",
                                color = TextMain,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "به محض توقف ضبط زنده، رونوشت به تصمیمات کلیدی و وظایف تبدیل می‌شود",
                                color = TextSub,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Switch(
                        checked = autoSummarizeOnRecordingStop,
                        onCheckedChange = onAutoSummarizeOnRecordingStopChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentPurple
                        ),
                        modifier = Modifier.testTag("realtime_stt_auto_summarize_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Speaker Switcher Chips
                Text(
                    text = "👤 گوینده فعال در ضبط صدا (کلیک جهت تغییر):",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val availableSpeakers = session.members.filter { it.isPresent }.ifEmpty {
                        listOf(com.example.data.Member(name = "سخنران", title = "مدیر عملیات"))
                    }

                    availableSpeakers.forEach { m ->
                        val isSelected = activeTranscribingSpeaker == m.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgInput)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onTranscribingSpeakerChange(m.name) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentCyan else AccentPurpleLight.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = m.name.take(1),
                                        color = if (isSelected) BgCanvas else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = m.name,
                                    color = if (isSelected) AccentCyan else TextMain,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Error message banner if any
                speechError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = err, color = AccentRose, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Transcribed Dialogue Feed Header & Bulk Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "جریان گفت‌وگوهای پیاده‌سازی‌شده:",
                            color = TextMain,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${session.transcriptions.size} فراز",
                                color = AccentPurpleLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (session.transcriptions.any { !it.isCommittedToNotes }) {
                            Button(
                                onClick = onCommitAllTranscriptions,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentEmerald.copy(alpha = 0.2f),
                                    contentColor = AccentEmeraldLight
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("commit_all_transcriptions_btn")
                            ) {
                                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ثبت همه در مصوبات", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (session.transcriptions.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAllTranscriptions,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "پاکسازی رونویسی", tint = TextSub, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of Transcriptions or Empty State
                if (session.transcriptions.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgInput),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = AccentPurpleLight.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "هنوز گفت‌وگویی ضبط و پیاده‌سازی نشده است.",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "دکمه «شروع پیاده‌سازی زنده صوت» را فشرده و صحبت کنید؛ عبارات به صورت خودکار با ذکر گوینده و زمان‌بندی دقیق در این بخش درج خواهند شد.",
                                color = TextSub,
                                fontSize = 10.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        session.transcriptions.forEachIndexed { idx, segment ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgInput),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (segment.isCommittedToNotes) AccentEmerald.copy(alpha = 0.5f) else BorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transcription_segment_$idx")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    // Bubble Header: Speaker, Time, Status
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
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (segment.isCommittedToNotes) AccentEmerald else AccentPurpleLight)
                                            )
                                            Text(
                                                text = segment.speaker,
                                                color = TextMain,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                            Text(
                                                text = "[${segment.timeFormatted}]",
                                                color = TextSub,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }

                                        if (segment.isCommittedToNotes) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(AccentEmerald.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "✅ در مصوبات ثبت شد",
                                                    color = AccentEmeraldLight,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Dialogue Text
                                    Text(
                                        text = segment.text,
                                        color = TextMain,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Bubble Action Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quick convert to note
                                        if (!segment.isCommittedToNotes) {
                                            Button(
                                                onClick = { onCommitTranscriptionSegment(segment.id) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = AccentPurple.copy(alpha = 0.2f),
                                                    contentColor = AccentPurpleLight
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .height(26.dp)
                                                    .testTag("commit_segment_btn_$idx")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("ثبت به عنوان مصوبه", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        // Copy snippet
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("${segment.speaker}: ${segment.text}"))
                                                Toast.makeText(context, "گفت‌وگو کپی شد.", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "کپی", tint = TextSub, modifier = Modifier.size(14.dp))
                                        }

                                        // Delete segment
                                        IconButton(
                                            onClick = { onRemoveTranscriptionSegment(segment.id) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "حذف", tint = TextSub, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Manual Input Fallback Toggle & Form
                TextButton(
                    onClick = { showManualInput = !showManualInput },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = if (showManualInput) Icons.Default.ExpandLess else Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showManualInput) "بستن تایپ دستی گفت‌وگو" else "➕ افزودن دستی گفت‌وگو (شبیه‌ساز سخنران)",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(visible = showManualInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = manualTranscriptInput,
                            onValueChange = { manualTranscriptInput = it },
                            placeholder = { Text("متن گفت‌وگوی سخنران را وارد کنید...", fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_transcription_input"),
                            minLines = 1,
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                if (manualTranscriptInput.isNotBlank()) {
                                    onAddManualTranscription(manualTranscriptInput, activeTranscribingSpeaker)
                                    manualTranscriptInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = BgCanvas
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("submit_manual_transcription_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ثبت گفت‌وگو به نام «$activeTranscribingSpeaker»", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // --- Gemini AI Automatic Transcription Summary & Action Items Extraction ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("gemini_transcription_ai_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentPurpleLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "🤖 خلاصه‌سازی هوشمند و استخراج تعهدات با Gemini",
                                    color = TextMain,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (transcriptionAiAnalysis != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentEmerald.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "تحلیل آماده است ✅",
                                        color = AccentEmeraldLight,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "تحلیل عمیق تمام مکالمات ضبط و پیاده‌سازی‌شده صوتی جلسه توسط مدل هوش مصنوعی Gemini 2.5 Flash، تدوین خلاصه اجرایی و استخراج اقدامات (Action Items) با تفکیک دقیق مسئول و مهلت اقدام.",
                            color = TextSub,
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Trigger Button
                        Button(
                            onClick = onAnalyzeTranscriptionsWithGemini,
                            enabled = !isTranscriptionAiLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("ai_summarize_transcription_btn")
                        ) {
                            if (isTranscriptionAiLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("در حال تلخیص گفتار و استخراج تعهدات با Gemini...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (transcriptionAiAnalysis != null) "⚡ تحلیل و بازتولید مجدد با Gemini" else "✨ خلاصه‌سازی متن مذاکرات و استخراج تعهدات با Gemini",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Error message
                        transcriptionAiError?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentRose.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = err, color = AccentRose, fontSize = 11.sp)
                                }
                            }
                        }

                        // Analysis Content Display
                        transcriptionAiAnalysis?.let { analysis ->
                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Executive Summary Box
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgInput),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth().testTag("transcription_ai_summary_card")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📑 خلاصه مدیریتی مذاکرات صوتی:",
                                            color = AccentCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        OutlinedButton(
                                            onClick = {
                                                onSaveTranscriptionSummaryAsMainAiSummary()
                                                Toast.makeText(context, "خلاصه در صورتجلسه نهایی ذخیره شد.", Toast.LENGTH_SHORT).show()
                                            },
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(0.5f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(24.dp).testTag("save_ai_summary_btn")
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentEmeraldLight, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("ذخیره در صورتجلسه", color = AccentEmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = analysis.executiveSummary,
                                        color = TextMain,
                                        fontSize = 11.5.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            // 2. Key Decisions
                            if (analysis.keyDecisions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "🎯 تصمیمات و توافقات کلیدی جلسه‌:",
                                    color = AccentEmeraldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    analysis.keyDecisions.forEach { decision ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(BgInput)
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(14.dp))
                                            Text(decision, color = TextMain, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // 3. Extracted Action Items
                            if (analysis.actionItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📌 تعهدات و اقدامات اجرایی استخراج‌شده (${analysis.actionItems.size}):",
                                        color = AccentAmber,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (analysis.actionItems.any { !it.isAddedToNotes }) {
                                        Button(
                                            onClick = onAddAllActionItemsToNotes,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentAmber.copy(0.2f),
                                                contentColor = AccentAmber
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(0.5f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp).testTag("add_all_action_items_btn")
                                        ) {
                                            Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("ثبت تمام تعهدات در مصوبات", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    analysis.actionItems.forEachIndexed { itemIdx, actionItem ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = BgInput),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (actionItem.isAddedToNotes) AccentEmerald.copy(0.4f) else BorderSubtle
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("action_item_card_$itemIdx")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val priColor = when (actionItem.priority) {
                                                            "فوری" -> AccentRose
                                                            "بالا" -> AccentAmber
                                                            else -> AccentCyan
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(priColor.copy(alpha = 0.18f))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(actionItem.priority, color = priColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Text(
                                                            text = "مسئول: ${actionItem.assignee}",
                                                            color = AccentPurpleLight,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                        Text(
                                                            text = "مهلت: ${actionItem.deadline}",
                                                            color = TextSub,
                                                            fontSize = 10.sp
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    Text(
                                                        text = actionItem.title,
                                                        color = TextMain,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                if (actionItem.isAddedToNotes) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(AccentEmerald.copy(0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text("✅ ثبت شد", color = AccentEmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { onAddActionItemToNotes(actionItem) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = AccentPurple.copy(0.2f),
                                                            contentColor = AccentPurpleLight
                                                        ),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(0.5f)),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(26.dp).testTag("add_single_action_item_$itemIdx")
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("ثبت در مصوبه", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Export PDF for Stakeholders
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onExportPdf,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPurple,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("export_transcription_pdf_btn")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📄 صدور و اشتراک PDF خلاصه و تعهدات با ذینفعان", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Live Note Entry Form ---
        item {
            QuantumPanel(
                title = "ثبت بند، مصوبه و مذاکره زنده",
                badgeText = "تایپ صوتی آنی"
            ) {
                // Speaker & Note Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Speaker
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedTextField(
                            value = selectedSpeaker,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("مسئول / گوینده", fontSize = 10.sp) },
                            trailingIcon = {
                                IconButton(onClick = { speakerDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { speakerDropdownExpanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        DropdownMenu(
                            expanded = speakerDropdownExpanded,
                            onDismissRequest = { speakerDropdownExpanded = false },
                            modifier = Modifier.background(BgCard)
                        ) {
                            val presentMembers = session.members.filter { it.isPresent }
                            if (presentMembers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("دبیر جلسه", color = TextMain) },
                                    onClick = {
                                        selectedSpeaker = "دبیر جلسه"
                                        speakerDropdownExpanded = false
                                    }
                                )
                            } else {
                                presentMembers.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text("${m.name} (${m.title})", color = TextMain, fontSize = 11.5.sp) },
                                        onClick = {
                                            selectedSpeaker = m.name
                                            speakerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Type
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = noteTypeLabels[selectedType] ?: "خودکار",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("نوع بند", fontSize = 10.sp) },
                            trailingIcon = {
                                IconButton(onClick = { typeDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { typeDropdownExpanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        DropdownMenu(
                            expanded = typeDropdownExpanded,
                            onDismissRequest = { typeDropdownExpanded = false },
                            modifier = Modifier.background(BgCard)
                        ) {
                            noteTypeLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextMain, fontSize = 11.5.sp) },
                                    onClick = {
                                        selectedType = key
                                        typeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Due date
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("مهلت اقدام", fontSize = 10.sp) },
                    placeholder = { Text("مثلاً: تا پایان هفته یا 1405/08/20") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Speech-to-Text Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "متن مذاکره و تصمیمات:",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSimulateSampleSpeechCheck?.invoke()
                                val latest = session.transcriptions.lastOrNull()?.text
                                    ?: "مصوب شد سامانه پایش برخط دمای ناوگان زنجیره سرد با نظارت سخنران تا پایان هفته عملیاتی گردد."
                                noteInputText = if (noteInputText.isBlank()) latest else "$noteInputText $latest"
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("live_instant_dictation_sample_btn")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("دیکته آنی", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onToggleSpeechRecognition { spokenText ->
                                    noteInputText = if (noteInputText.isBlank()) spokenText else "$noteInputText $spokenText"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) AccentRose else AccentPurple.copy(alpha = 0.2f),
                                contentColor = if (isListening) Color.White else AccentPurpleLight
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isListening) AccentRose else AccentPurple
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .scale(if (isListening) pulseScale else 1f)
                                .testTag("speech_to_text_btn")
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isListening) "🔴 در حال شنیدن..." else "🎤 تایپ صوتی آنی",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Note Input (streams interimTranscript in real time when SpeechRecognizer is active)
                val liveNoteDisplayValue = if ((isListening || isContinuousTranscribing) && interimTranscript.isNotBlank()) {
                    if (noteInputText.isBlank()) "$interimTranscript ▍" else "$noteInputText $interimTranscript ▍"
                } else {
                    noteInputText
                }
                OutlinedTextField(
                    value = liveNoteDisplayValue,
                    onValueChange = { noteInputText = it },
                    placeholder = { Text("صحبت کنید؛ متن بلافاصله اینجا درج می‌شود یا دستی تایپ کنید...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input_field"),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Submit button
                Button(
                    onClick = {
                        if (noteInputText.isNotBlank()) {
                            onAddNote(selectedSpeaker, noteInputText, selectedType, dueDateText)
                            noteInputText = ""
                            dueDateText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("submit_note_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ثبت بند و مصوبه در جریان جلسه +", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 3. Live Stream of Notes ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "جریان زنده مذاکرات و مصوبات",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentPurple.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${session.notes.size} مورد",
                            color = AccentPurpleLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick AI Analyze button
                Button(
                    onClick = onNavigateToAi,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgInput,
                        contentColor = AccentPurpleLight
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp), tint = AccentPurpleLight)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تحلیل با Gemini", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (session.notes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "هنوز نکته یا مصوبه‌ای در جلسه ثبت نشده است.",
                            color = TextSub,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(session.notes, key = { it.id }) { note ->
                MeetingNoteItemCard(
                    note = note,
                    onToggleDone = { onToggleNoteDone(note.id) },
                    onDelete = { onRemoveNote(note.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showQrDialog) {
        QrCheckInDialog(
            session = session,
            onDismiss = { showQrDialog = false },
            onCheckInByQr = onCheckInByQr,
            onCheckInById = onCheckInById
        )
    }
}

@Composable
fun MeetingNoteItemCard(
    note: MeetingNote,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when {
        note.type.contains("مصوبه رسمی") -> AccentRose
        note.type.contains("تعهد") -> AccentEmerald
        note.type.contains("مالی") -> AccentAmber
        else -> AccentCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (note.isDone) AccentEmerald.copy(alpha = 0.4f) else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "[${note.timeFormatted}]",
                        color = TextSub,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "👤 ${note.speaker}",
                        color = AccentPurpleLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor.copy(alpha = 0.15f))
                            .border(1.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = note.type,
                            color = typeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Done checkbox toggle
                    Checkbox(
                        checked = note.isDone,
                        onCheckedChange = { onToggleDone() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentEmerald,
                            checkmarkColor = BgCanvas
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "حذف بند", tint = TextSub, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.text,
                color = if (note.isDone) TextSub else TextMain,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                style = if (note.isDone) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle()
            )

            if (note.due.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(12.dp))
                    Text(
                        text = "مهلت اقدام: ${note.due}",
                        color = AccentAmber,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LiveAudioWaveform(
    isActive: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BgInput)
            .border(1.dp, if (isActive) AccentPurple.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 20
        for (i in 0 until barCount) {
            val sineFactor = (Math.sin(phase.toDouble() + (i * 0.42)).toFloat() + 1f) / 2f
            val barFraction = if (isActive) {
                (rmsLevel * 0.65f + sineFactor * 0.35f).coerceIn(0.12f, 1.0f)
            } else 0.08f

            val barHeight = (barFraction * 26).dp
            val barColor = when {
                !isActive -> BorderSubtle
                i % 3 == 0 -> AccentCyan
                i % 3 == 1 -> AccentPurpleLight
                else -> AccentEmeraldLight
            }

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

