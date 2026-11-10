package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.MeetingSession
import com.example.data.TypedMeetingMinutesAnalysis
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

/**
 * Android Audio Recording (`MediaRecorder`) + Real-Time Speech-to-Text (`SpeechRecognizer`)
 * component for the Meeting Management page (`صفحه مدیریت جلسات`).
 *
 * Allows board members and secretaries to record meeting audio and convert speech to text
 * instantaneously (`تایپ آنی صورت‌جلسات هم‌زمان با صحبت`) with automatic classification
 * and registration into formal meeting minutes (`session.notes` & Room database).
 */
@Composable
fun InstantSpeechToMinutesPanel(
    session: MeetingSession,
    isAudioRecording: Boolean,
    recordingDurationSec: Long,
    recordingAmplitude: Float,
    recordedAudioFile: File?,
    isListening: Boolean,
    isContinuousTranscribing: Boolean,
    interimTranscript: String,
    audioRmsLevel: Float,
    speechLanguage: String,
    onLanguageChange: (String) -> Unit,
    activeSpeaker: String,
    onSpeakerChange: (String) -> Unit,
    liveTypedMinutesText: String,
    onUpdateLiveTypedMinutesText: (String) -> Unit,
    autoCommitSpeechToNotes: Boolean,
    onAutoCommitSpeechToNotesChange: (Boolean) -> Unit,
    onToggleCombinedAudioAndSpeech: () -> Unit,
    onToggleContinuousSpeech: () -> Unit,
    onToggleAudioRecording: () -> Unit,
    onAddNoteFromSpeech: (speaker: String, text: String, type: String) -> Unit,
    onSimulateInstantDictation: () -> Unit,
    autoAnalyzeTypedMinutesWithGemini: Boolean = true,
    onAutoAnalyzeTypedMinutesChange: (Boolean) -> Unit = {},
    isAnalyzingTypedMinutes: Boolean = false,
    typedMinutesAnalysis: TypedMeetingMinutesAnalysis? = session.typedMinutesAnalysis,
    typedMinutesAnalysisError: String? = null,
    onAnalyzeTypedMinutesWithGemini: (String) -> Unit = {},
    speechError: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingActionAfterPermission?.invoke()
            pendingActionAfterPermission = null
        } else {
            Toast.makeText(
                context,
                "جهت ضبط صدا و تایپ آنی صورت‌جلسه (Speech-to-Text)، مجوز میکروفون الزامی است.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingActionAfterPermission = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val isAnyVoiceActive = isAudioRecording || isContinuousTranscribing || isListening

    val infiniteTransition = rememberInfiniteTransition(label = "stt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stt_pulse_scale"
    )

    val speakersList = remember(session.members) {
        val fromSession = session.members.map { it.name.trim() }.filter { it.isNotBlank() }
        (fromSession + listOf("سخنران", "سخنران", "سخنران", "سخنران")).distinct()
    }

    // Compose the displayed text in the instant editor: accumulated text + live partial words
    val displayedEditorText = remember(liveTypedMinutesText, interimTranscript) {
        if (interimTranscript.isNotBlank()) {
            if (liveTypedMinutesText.isBlank()) {
                "$activeSpeaker: $interimTranscript ▍"
            } else {
                "$liveTypedMinutesText\n$activeSpeaker (در حال تایپ زنده...): $interimTranscript ▍"
            }
        } else {
            liveTypedMinutesText
        }
    }

    QuantumPanel(
        title = "🎙️ ضبط صدا و تایپ آنی صورت‌جلسه (Android Speech-to-Text)",
        badgeText = when {
            isAudioRecording && isContinuousTranscribing -> "🔴 ضبط + تایپ زنده فعال"
            isContinuousTranscribing || isListening -> "🎤 تایپ صوتی آنی فعال"
            isAudioRecording -> "⏺️ ضبط صدا فعال"
            else -> "MediaRecorder + SpeechRecognizer"
        },
        modifier = modifier.testTag("instant_speech_to_minutes_panel")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "با استفاده از کتابخانه‌های استاندارد اندروید (SpeechRecognizer و MediaRecorder)، مذاکرات جلسه را ضبط کرده و هم‌زمان به صورت آنی در متن صورت‌جلسه و فهرست مصوبات تایپ کنید.",
                color = TextSub,
                fontSize = 10.5.sp,
                lineHeight = 15.5.sp
            )

            // --- 1. Primary Action Row: Combined Record + STT / STT Only / Audio Record Only ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Combined Audio Recording + Instant Speech-to-Text Button
                Button(
                    onClick = {
                        if (isAudioRecording || isContinuousTranscribing) {
                            onToggleCombinedAudioAndSpeech()
                        } else {
                            runWithMicPermission { onToggleCombinedAudioAndSpeech() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAudioRecording && isContinuousTranscribing) AccentRose else AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.45f)
                        .height(44.dp)
                        .scale(if (isAudioRecording && isContinuousTranscribing) pulseScale else 1f)
                        .testTag("instant_stt_start_both_btn")
                ) {
                    Icon(
                        imageVector = if (isAudioRecording && isContinuousTranscribing) Icons.Default.StopCircle else Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAudioRecording && isContinuousTranscribing) {
                            "⏹️ توقف ضبط و تایپ آنی"
                        } else {
                            "🎙️ ضبط صدا + تایپ آنی"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Instant Speech-to-Text Only Button
                OutlinedButton(
                    onClick = {
                        if (isContinuousTranscribing) {
                            onToggleContinuousSpeech()
                        } else {
                            runWithMicPermission { onToggleContinuousSpeech() }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isContinuousTranscribing) AccentCyan.copy(alpha = 0.2f) else BgInput,
                        contentColor = if (isContinuousTranscribing) AccentCyan else TextMain
                    ),
                    border = BorderStroke(1.dp, if (isContinuousTranscribing) AccentCyan else BorderSubtle),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("instant_stt_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isContinuousTranscribing) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isContinuousTranscribing) AccentCyan else AccentPurpleLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isContinuousTranscribing) "توقف STT" else "تایپ صوتی",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Audio File Recorder Only Button
                OutlinedButton(
                    onClick = {
                        if (isAudioRecording) {
                            onToggleAudioRecording()
                        } else {
                            runWithMicPermission { onToggleAudioRecording() }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isAudioRecording) AccentRose.copy(alpha = 0.2f) else BgInput,
                        contentColor = if (isAudioRecording) AccentRose else AccentEmeraldLight
                    ),
                    border = BorderStroke(1.dp, if (isAudioRecording) AccentRose else AccentEmerald.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .height(44.dp)
                        .testTag("instant_audio_record_btn")
                ) {
                    val mins = recordingDurationSec / 60
                    val secs = recordingDurationSec % 60
                    val timerLabel = String.format(Locale.US, "%02d:%02d", mins, secs)
                    Text(
                        text = if (isAudioRecording) "⏺️ $timerLabel" else "ضبط صوت",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isAudioRecording) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }

            // --- 2. Speaker Selection & Language Chips ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👤 گوینده:",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    speakersList.forEach { speaker ->
                        val selected = activeSpeaker == speaker
                        Surface(
                            color = if (selected) AccentPurple.copy(alpha = 0.28f) else BgInput,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (selected) AccentPurpleLight else BorderSubtle),
                            modifier = Modifier
                                .clickable { onSpeakerChange(speaker) }
                                .testTag("instant_stt_speaker_$speaker")
                        ) {
                            Text(
                                text = speaker,
                                color = if (selected) Color.White else TextSub,
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("fa-IR" to "FA", "en-US" to "EN").forEach { (code, label) ->
                        val isSel = speechLanguage == code
                        Surface(
                            color = if (isSel) AccentCyan.copy(alpha = 0.25f) else BgInput,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSel) AccentCyan else BorderSubtle),
                            modifier = Modifier
                                .clickable { onLanguageChange(code) }
                                .testTag("instant_stt_lang_$code")
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) AccentCyan else TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // --- 3. Live Audio Waveform + Auto-Commit to Meeting Notes Switch ---
            Surface(
                color = BgInput,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (isAnyVoiceActive) AccentCyan.copy(alpha = 0.6f) else BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isAnyVoiceActive) AccentRose else AccentEmeraldLight)
                                    .scale(if (isAnyVoiceActive) pulseScale else 1f)
                            )
                            Text(
                                text = if (isAnyVoiceActive) {
                                    "در حال دریافت صدا و تبدیل بلادرنگ به متن صورت‌جلسه..."
                                } else {
                                    "آماده دریافت صدا از میکروفون و تایپ خودکار بندهای صورت‌جلسه"
                                },
                                color = if (isAnyVoiceActive) AccentCyan else TextSub,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ثبت خودکار مصوبه",
                                color = if (autoCommitSpeechToNotes) AccentEmeraldLight else TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = autoCommitSpeechToNotes,
                                onCheckedChange = onAutoCommitSpeechToNotesChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentEmerald
                                ),
                                modifier = Modifier
                                    .scale(0.8f)
                                    .testTag("instant_stt_auto_commit_switch")
                            )
                        }
                    }

                    // Dynamic 24-Bar Equalizer responding to both SpeechRecognizer RMS and MediaRecorder amplitude
                    val effectiveSignal = maxOf(recordingAmplitude, audioRmsLevel)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 24) {
                            val factor = ((i * 5 + 3) % 7 + 1) / 7f
                            val barFrac = if (isAnyVoiceActive) {
                                (0.22f + effectiveSignal.coerceAtLeast(0.35f) * factor * 0.78f).coerceIn(0.18f, 1f)
                            } else {
                                0.18f
                            }
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight(barFrac)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            isAudioRecording && isContinuousTranscribing -> AccentCyan
                                            isContinuousTranscribing || isListening -> AccentPurpleLight
                                            isAudioRecording -> AccentRose
                                            else -> BorderSubtle
                                        }
                                    )
                            )
                        }
                    }

                    // Live Interim Subtitle Stream Banner
                    AnimatedVisibility(visible = interimTranscript.isNotBlank()) {
                        Surface(
                            color = AccentPurple.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("instant_stt_live_stream_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "در حال تایپ آنی ($activeSpeaker): «$interimTranscript ▍»",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Audio File Status Pill if recorded
                    val audioFile = recordedAudioFile ?: session.audioFilePath?.let { File(it) }
                    if (audioFile != null && audioFile.exists()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎵 فایل صوتی ضبط‌شده: ${audioFile.name}",
                                color = AccentEmeraldLight,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(audioFile.length() / 1024).coerceAtLeast(1)} KB",
                                color = AccentCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!speechError.isNullOrBlank()) {
                        Text(
                            text = "⚠️ $speechError",
                            color = AccentAmber,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }

            // --- 4. Instant-Typed Meeting Minutes Editor (بوم تایپ آنی متن صورت‌جلسه) ---
            OutlinedTextField(
                value = displayedEditorText,
                onValueChange = { onUpdateLiveTypedMinutesText(it) },
                label = {
                    Text(
                        text = "📝 متن صورت‌جلسه تایپ‌شده با گفتار (آنی و قابل ویرایش)",
                        fontSize = 10.5.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "با فشردن دکمه «ضبط صدا + تایپ آنی» یا «دیکته صوتی نمونه»، صحبت‌های اعضای جلسه بلافاصله در این کادر تایپ و به مصوبات اضافه می‌شود...",
                        fontSize = 10.sp
                    )
                },
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgInput,
                    unfocusedContainerColor = BgInput
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("instant_stt_minutes_editor")
            )

            // --- 5. Quick Actions Bar: Save as Resolution / Simulate Live Dictation / Copy ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val clean = liveTypedMinutesText.trim()
                        if (clean.isNotBlank()) {
                            onAddNoteFromSpeech(activeSpeaker, clean, "auto")
                            Toast.makeText(
                                context,
                                "متن تایپ‌شده در فهرست مصوبات صورت‌جلسه ثبت شد.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = liveTypedMinutesText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(34.dp)
                        .testTag("instant_stt_save_note_btn")
                ) {
                    Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ثبت در بندهای صورت‌جلسه", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = onSimulateInstantDictation,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AccentPurple.copy(alpha = 0.16f),
                        contentColor = AccentPurpleLight
                    ),
                    border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(34.dp)
                        .testTag("instant_stt_simulate_dictation_btn")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("دیکته صوتی نمونه (تست آنی)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = {
                        if (liveTypedMinutesText.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(liveTypedMinutesText))
                            Toast.makeText(context, "متن صورت‌جلسه کپی شد.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgInput)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .testTag("instant_stt_copy_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی", tint = AccentCyan, modifier = Modifier.size(15.dp))
                }
            }

            // --- 6. Gemini API Automatic Analyzer for Typed Meeting Text (صورت‌جلسه، تصمیمات اتخاذ شده و وظایف محول شده به هر شخص) ---
            Surface(
                color = BgCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_typed_minutes_analyzer_section")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "🤖 تحلیل خودکار متن تایپ‌شده با Gemini API",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "تحلیل خودکار",
                                color = if (autoAnalyzeTypedMinutesWithGemini) AccentEmeraldLight else TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = autoAnalyzeTypedMinutesWithGemini,
                                onCheckedChange = onAutoAnalyzeTypedMinutesChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentPurple
                                ),
                                modifier = Modifier
                                    .scale(0.78f)
                                    .testTag("auto_analyze_typed_minutes_switch")
                            )
                        }
                    }

                    Text(
                        text = "استخراج خودکار ۱) متن رسمی صورت‌جلسه، ۲) تصمیمات اتخاذ شده و ۳) وظایف محول شده به هر شخص از متن تایپ‌شده جلسه.",
                        color = TextSub,
                        fontSize = 9.8.sp,
                        lineHeight = 14.sp
                    )

                    Button(
                        onClick = { onAnalyzeTypedMinutesWithGemini(liveTypedMinutesText) },
                        enabled = !isAnalyzingTypedMinutes,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("analyze_typed_minutes_gemini_btn")
                    ) {
                        if (isAnalyzingTypedMinutes) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "در حال تحلیل متن تایپ‌شده با Gemini 2.5 Flash...",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✨ تحلیل متن تایپ‌شده و استخراج صورت‌جلسه، تصمیمات و وظایف اشخاص",
                                fontSize = 10.2.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (!typedMinutesAnalysisError.isNullOrBlank()) {
                        Text(
                            text = "⚠️ $typedMinutesAnalysisError",
                            color = AccentRose,
                            fontSize = 9.5.sp
                        )
                    }

                    val effectiveAnalysis = typedMinutesAnalysis ?: session.typedMinutesAnalysis
                    if (effectiveAnalysis != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                                .border(1.dp, AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(9.dp)
                                .testTag("typed_minutes_analysis_result_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Formal Meeting Minutes (صورت‌جلسه استخراج‌شده)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("typed_minutes_extracted_minutes_box"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📄 ۱. صورت‌جلسه رسمی استخراج‌شده با Gemini:",
                                        color = AccentCyan,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Surface(
                                        color = AccentPurple.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(5.dp),
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(effectiveAnalysis.formalMinutesText))
                                            Toast.makeText(context, "متن صورت‌جلسه کپی شد.", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text(
                                            text = "📋 کپی صورت‌جلسه",
                                            color = AccentPurpleLight,
                                            fontSize = 8.8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = effectiveAnalysis.formalMinutesText,
                                    color = TextMain,
                                    fontSize = 9.8.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                            // 2. Decisions Made (تصمیمات اتخاذ شده)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("typed_minutes_extracted_decisions_list"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⚖️ ۲. تصمیمات اتخاذ شده (${effectiveAnalysis.decisionsMade.size} تصمیم کلیدی):",
                                    color = AccentEmeraldLight,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                effectiveAnalysis.decisionsMade.forEachIndexed { idx, decision ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentEmerald.copy(alpha = 0.1f))
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✓ مصوبه ${idx + 1}: $decision",
                                            color = TextMain,
                                            fontSize = 9.6.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                            // 3. Tasks Assigned to Each Person (وظایف محول شده به هر شخص)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("typed_minutes_extracted_person_tasks_list"),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val distinctPeopleCount = effectiveAnalysis.assignedTasksByPerson.map { it.personName }.distinct().size
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "👥 ۳. وظایف محول شده به هر شخص (${effectiveAnalysis.assignedTasksByPerson.size} وظیفه برای $distinctPeopleCount نفر):",
                                        color = AccentAmber,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "💾 ثبت در Room",
                                        color = AccentCyan,
                                        fontSize = 8.8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                effectiveAnalysis.assignedTasksByPerson.forEachIndexed { idx, task ->
                                    Surface(
                                        color = BgCard,
                                        shape = RoundedCornerShape(7.dp),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("typed_minutes_person_task_$idx")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(7.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Surface(
                                                        color = AccentPurple.copy(alpha = 0.25f),
                                                        shape = RoundedCornerShape(5.dp)
                                                    ) {
                                                        Text(
                                                            text = "👤 ${task.personName}",
                                                            color = Color.White,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    if (task.personRole.isNotBlank()) {
                                                        Text(
                                                            text = "(${task.personRole})",
                                                            color = TextSub,
                                                            fontSize = 8.8.sp
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = if (task.priority == "فوری") AccentRose.copy(alpha = 0.22f) else AccentCyan.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(5.dp)
                                                ) {
                                                    Text(
                                                        text = "مهلت: ${task.deadline} • ${task.priority}",
                                                        color = if (task.priority == "فوری") AccentRose else AccentCyan,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "📌 وظیفه محوله: ${task.taskTitle}",
                                                color = TextMain,
                                                fontSize = 9.6.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 7. Live Feed of Recently Typed Sentences & Auto-Registered Resolutions ---
            if (session.transcriptions.isNotEmpty()) {
                Surface(
                    color = BgInput,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instant_stt_recent_segments_card")
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ آخرین جملات تایپ‌شده با گفتار (${session.transcriptions.size} بند):",
                                color = AccentCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${session.notes.size} مصوبه ثبت‌شده",
                                color = AccentEmeraldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        session.transcriptions.takeLast(3).reversed().forEach { seg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• [${seg.timeFormatted}] ${seg.speaker}: ${seg.text}",
                                    color = TextMain,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (seg.isCommittedToNotes) {
                                    Text(
                                        text = "✅ مصوبه",
                                        color = AccentEmeraldLight,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
