package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TranscriptionSegment
import com.example.service.AudioRecordManager
import com.example.service.SilenceDetectionStatus
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Formats elapsed recording seconds into HH:MM:SS or MM:SS format.
 */
fun formatRecordingElapsedTime(durationSec: Long, includeHours: Boolean = true): String {
    val safeSec = maxOf(0L, durationSec)
    val hours = safeSec / 3600
    val minutes = (safeSec % 3600) / 60
    val seconds = safeSec % 60
    return if (includeHours || hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Computes a normalized 0f..1f waveform bar height combining real microphone
 * amplitude (`recordingAmplitude`, `audioRmsLevel`), the user-selected
 * microphone input sensitivity (`micSensitivity`), and conference room noise gating.
 */
fun computeEffectiveWaveSample(
    isCapturing: Boolean,
    recordingAmplitude: Float,
    audioRmsLevel: Float,
    tickIndex: Int,
    micSensitivity: Float = AudioRecordManager.DEFAULT_SENSITIVITY
): Float {
    val clampedSens = micSensitivity.coerceIn(
        AudioRecordManager.MIN_SENSITIVITY,
        AudioRecordManager.MAX_SENSITIVITY
    )
    if (!isCapturing) return 0.08f

    val normalizedRms = ((audioRmsLevel + 2f) / 12f).coerceIn(0f, 1f)
    val gatedRms = AudioRecordManager.applySensitivityAndNoiseGate(normalizedRms, clampedSens)
    val rawSignal = max(recordingAmplitude.coerceIn(0f, 1f), gatedRms)

    return if (rawSignal > 0.04f) {
        (rawSignal * (0.65f + clampedSens * 0.55f)).coerceIn(0.10f, 1f)
    } else {
        // Subtle carrier wave scaled by microphone input sensitivity when awaiting speech peaks
        val baseCarrier = (0.12f + 0.08f * sin(tickIndex * 0.65f)) * (0.55f + clampedSens * 0.65f)
        baseCarrier.coerceIn(0.06f, 0.45f)
    }
}

/**
 * Returns a human-readable Persian acoustic profile label for a given [micSensitivity].
 */
fun getConferenceSensitivityProfileLabel(micSensitivity: Float): String {
    return when {
        micSensitivity <= 0.45f -> "🏢 سالن بزرگ پرنویز (فیلتر قوی نویز محیط)"
        micSensitivity <= 0.80f -> "⚖️ استاندارد اتاق کنفرانس هیئت‌مدیره"
        else -> "🎙️ تقویت صدای سخنرانان دور (حساسیت حداکثری)"
    }
}

/**
 * Snapshot of real-time recording quality and speech clarity metrics displayed in the
 * live transcription overlay alongside the audio waveform.
 */
data class RecordingQualityAssessment(
    val clarityScorePercent: Int,
    val snrEstimateDb: Int,
    val qualityLabel: String,
    val recommendationHint: String,
    val recognizedWordCount: Int,
    val isOptimalQuality: Boolean
)

/**
 * Computes real-time recording quality and speech intelligibility metrics from the
 * active signal amplitude, noise gate threshold, silence state, and live transcription text.
 */
fun evaluateRecordingQuality(
    isCapturing: Boolean,
    isAutoPausedBySilence: Boolean,
    effectiveSignal: Float,
    micSensitivity: Float,
    interimTranscript: String,
    recentTranscriptions: List<TranscriptionSegment>
): RecordingQualityAssessment {
    val combinedText = buildString {
        recentTranscriptions.takeLast(3).forEach { append(it.text).append(" ") }
        if (interimTranscript.isNotBlank()) append(interimTranscript)
    }.trim()
    val wordCount = if (combinedText.isBlank()) 0 else combinedText.split(Regex("\\s+")).size

    if (!isCapturing) {
        return RecordingQualityAssessment(
            clarityScorePercent = 0,
            snrEstimateDb = 0,
            qualityLabel = "آماده پایش کیفیت ضبط",
            recommendationHint = "با شروع ضبط، متن زنده و شاخص شفافیت صوت (SNR) در لحظه نمایش داده می‌شود.",
            recognizedWordCount = wordCount,
            isOptimalQuality = false
        )
    }

    if (isAutoPausedBySilence) {
        return RecordingQualityAssessment(
            clarityScorePercent = 92,
            snrEstimateDb = 24,
            qualityLabel = "⏸️ مکث هوشمند (حذف سکوت)",
            recommendationHint = "نویز محیط حذف شده و ضبط تا شروع مجدد صحبت در حالت مکث تمیز قرار دارد.",
            recognizedWordCount = wordCount,
            isOptimalQuality = true
        )
    }

    val gate = AudioRecordManager.computeNoiseGateThreshold(micSensitivity)
    val clampedSig = effectiveSignal.coerceIn(0f, 1f)
    val snrDb = (12 + (clampedSig * 20f) + ((0.40f - gate) * 10f)).roundToInt().coerceIn(8, 34)
    val baseClarity = (68 + (clampedSig * 28f) + if (interimTranscript.isNotBlank() || wordCount > 0) 4f else 0f)
        .roundToInt()
        .coerceIn(55, 99)

    return when {
        clampedSig >= 0.92f -> RecordingQualityAssessment(
            clarityScorePercent = 78,
            snrEstimateDb = snrDb,
            qualityLabel = "⚠️ سیگنال بسیار قوی (احتمال اشباع)",
            recommendationHint = "حساسیت میکروفون را کمی کاهش دهید تا از اعوجاج صدا جلوگیری شود.",
            recognizedWordCount = wordCount,
            isOptimalQuality = false
        )
        clampedSig >= 0.18f || interimTranscript.isNotBlank() -> RecordingQualityAssessment(
            clarityScorePercent = baseClarity,
            snrEstimateDb = snrDb,
            qualityLabel = "✅ کیفیت عالی (HD Speech)",
            recommendationHint = "تفکیک واژگان و سطح سیگنال به نویز برای خلاصه‌سازی Gemini ایده‌آل است.",
            recognizedWordCount = wordCount,
            isOptimalQuality = true
        )
        else -> RecordingQualityAssessment(
            clarityScorePercent = baseClarity.coerceAtMost(82),
            snrEstimateDb = snrDb,
            qualityLabel = "🎙️ دریافت ملایم (نزدیک‌تر صحبت کنید)",
            recommendationHint = "برای تشخیص دقیق‌تر کلمات در سالن بزرگ، حساسیت را افزایش دهید.",
            recognizedWordCount = wordCount,
            isOptimalQuality = true
        )
    }
}

/**
 * Reusable Live Transcription & Recording Quality Overlay View displayed directly
 * alongside / overlaid on the audio waveform so users can verify recording quality instantly.
 */
@Composable
fun LiveTranscriptionWaveformOverlay(
    isCapturing: Boolean,
    isAutoPausedBySilence: Boolean,
    activeSpeaker: String,
    interimTranscript: String,
    recentTranscriptions: List<TranscriptionSegment>,
    qualityAssessment: RecordingQualityAssessment,
    onToggleContinuousTranscription: () -> Unit = {},
    isContinuousTranscribing: Boolean = false,
    onSimulateSampleSpeechCheck: (() -> Unit)? = null,
    overlayTestTag: String = "live_transcription_waveform_overlay",
    modifier: Modifier = Modifier
) {
    val latestCommittedSegment = recentTranscriptions.lastOrNull()
    val hasLiveText = interimTranscript.isNotBlank() || latestCommittedSegment != null

    val statusColor = when {
        !isCapturing -> TextSub
        isAutoPausedBySilence -> AccentGold
        qualityAssessment.isOptimalQuality -> AccentEmeraldLight
        else -> AccentAmber
    }

    Surface(
        color = BgCard.copy(alpha = 0.90f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCapturing) AccentCyan.copy(alpha = 0.50f) else BorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(overlayTestTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Top Telemetry Row: Live Transcription Badge + Instant Quality / SNR Verification Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "زیرنویس زنده و پایش کیفیت ضبط",
                        color = AccentCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeSpeaker.isNotBlank()) {
                        Surface(
                            color = AccentPurple.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = activeSpeaker,
                                color = AccentPurpleLight,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Instant Recording Quality & SNR Badge
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("recording_quality_verification_badge")
                ) {
                    Text(
                        text = if (isCapturing) {
                            "${qualityAssessment.qualityLabel} • ${qualityAssessment.clarityScorePercent}% (${qualityAssessment.snrEstimateDb}dB)"
                        } else {
                            qualityAssessment.qualityLabel
                        },
                        color = statusColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Live Transcription Text Stream
            Surface(
                color = BgInput.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (interimTranscript.isNotBlank()) AccentEmerald.copy(alpha = 0.45f) else BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_transcription_overlay_text_box")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (interimTranscript.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(AccentEmeraldLight)
                            )
                            Text(
                                text = "$interimTranscript ▋",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp,
                                modifier = Modifier.testTag("live_overlay_interim_text")
                            )
                        }
                    }

                    if (latestCommittedSegment != null) {
                        Text(
                            text = "✓ [${latestCommittedSegment.speaker}]: ${latestCommittedSegment.text}",
                            color = if (interimTranscript.isNotBlank()) TextMuted else TextMain,
                            fontSize = 10.sp,
                            fontWeight = if (interimTranscript.isNotBlank()) FontWeight.Normal else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp,
                            modifier = Modifier.testTag("live_overlay_committed_text")
                        )
                    }

                    if (!hasLiveText) {
                        Text(
                            text = when {
                                isAutoPausedBySilence -> "⏸️ مکث خودکار در سکوت ۵ ثانیه‌ای — با شروع صحبت، متن زنده بلافاصله نمایش داده می‌شود..."
                                isCapturing && isContinuousTranscribing -> "🎙️ در حال گوش دادن به مذاکرات جلسه... صحبت کنید تا متن زنده و کیفیت صدا را بررسی نمایید."
                                isCapturing -> "🎙️ صدابرداری فعال است — جهت مشاهده هم‌زمان متن کلمات هنگام ضبط، «تایپ زنده» یا دکمه تست کیفیت زیر را بزنید."
                                else -> "💬 با شروع ضبط، زیرنویس زنده صحبت‌ها در کنار موج صوتی جهت اطمینان از کیفیت صدا نمایش داده می‌شود."
                            },
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.testTag("live_overlay_placeholder_text")
                        )
                    }
                }
            }

            // Bottom Quality Hint + Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 واژگان ثبت‌شده: ${qualityAssessment.recognizedWordCount} کلمه • ${qualityAssessment.recommendationHint}",
                    color = TextSub,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (onSimulateSampleSpeechCheck != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = AccentCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSimulateSampleSpeechCheck() }
                            .testTag("verify_live_transcription_sample_btn")
                    ) {
                        Text(
                            text = "⚡ تست زیرنویس زنده",
                            color = AccentCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Microphone Input Sensitivity & Conference Room Noise Gate Slider Panel.
 * Used in both the Voice Recording Bottom Sheet and the Live Meeting Screen.
 */
@Composable
fun MicSensitivityControlPanel(
    micInputSensitivity: Float,
    onMicInputSensitivityChange: (Float) -> Unit,
    sliderTestTag: String = "mic_sensitivity_slider",
    modifier: Modifier = Modifier
) {
    val sensitivityPercent = (micInputSensitivity * 100).roundToInt().coerceIn(10, 100)
    val noiseGatePercent = (AudioRecordManager.computeNoiseGateThreshold(micInputSensitivity) * 100).roundToInt()
    val profileLabel = getConferenceSensitivityProfileLabel(micInputSensitivity)

    Surface(
        color = BgInput,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mic_sensitivity_control_panel")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "حساسیت میکروفون و حذف نویز سالن کنفرانس",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }

                Surface(
                    color = AccentCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "حساسیت: $sensitivityPercent%",
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("mic_sensitivity_value_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profileLabel,
                    color = AccentPurpleLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "آستانه Noise Gate: $noiseGatePercent%",
                    color = AccentEmeraldLight,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = micInputSensitivity.coerceIn(
                    AudioRecordManager.MIN_SENSITIVITY,
                    AudioRecordManager.MAX_SENSITIVITY
                ),
                onValueChange = { onMicInputSensitivityChange(it) },
                valueRange = AudioRecordManager.MIN_SENSITIVITY..AudioRecordManager.MAX_SENSITIVITY,
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(sliderTestTag)
            )

            // Quick Conference Room Acoustic Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    Triple(0.35f, "حذف نویز قوی (۳۵٪)", AccentEmeraldLight),
                    Triple(0.70f, "استاندارد شورا (۷۰٪)", AccentCyan),
                    Triple(0.95f, "تقویت صدای دور (۹۵٪)", AccentPurpleLight)
                )
                presets.forEach { (presetVal, label, chipColor) ->
                    val isSelected = kotlin.math.abs(micInputSensitivity - presetVal) < 0.06f
                    Surface(
                        color = if (isSelected) chipColor.copy(alpha = 0.22f) else BgCard,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) chipColor else BorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMicInputSensitivityChange(presetVal) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) chipColor else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable 5-Second Silence Detection (Auto-Pause on Silence & Auto-Resume on Speech) Control Card.
 * Displays a toggle switch, live 5-second silence countdown progress bar, auto-paused state badge,
 * manual resume action, and cumulative silence trimmed metrics to keep Gemini summaries clean.
 */
@Composable
fun SilenceDetectionControlCard(
    isAudioRecording: Boolean,
    silenceDetectionStatus: SilenceDetectionStatus,
    onSilenceDetectionEnabledChange: (Boolean) -> Unit,
    onForceResumeFromSilence: () -> Unit = {},
    switchTestTag: String = "silence_detection_switch",
    modifier: Modifier = Modifier
) {
    val isAutoPaused = isAudioRecording && silenceDetectionStatus.isAutoPaused
    val isCountingDownSilence = isAudioRecording &&
        silenceDetectionStatus.isEnabled &&
        !silenceDetectionStatus.isAutoPaused &&
        silenceDetectionStatus.currentSilenceMs >= 300L

    val borderColor = when {
        isAutoPaused -> AccentGold
        silenceDetectionStatus.isEnabled -> AccentEmerald.copy(alpha = 0.45f)
        else -> BorderSubtle
    }

    Surface(
        color = BgInput,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("silence_detection_control_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isAutoPaused) Icons.Default.PauseCircleFilled else Icons.Default.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = if (isAutoPaused) AccentGold else AccentEmeraldLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "تشخیص هوشمند سکوت (مکث خودکار ۵ ثانیه‌ای)",
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "توقف خودکار ضبط پس از ۵ ثانیه سکوت و ادامه فوری با شروع صحبت جهت خلاصه‌سازی تمیز",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }

                Switch(
                    checked = silenceDetectionStatus.isEnabled,
                    onCheckedChange = { onSilenceDetectionEnabledChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgCanvas,
                        checkedTrackColor = AccentEmeraldLight,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BgCard
                    ),
                    modifier = Modifier.testTag(switchTestTag)
                )
            }

            if (silenceDetectionStatus.isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                // Live Silence / Speech Activity Status Banner
                Surface(
                    color = when {
                        isAutoPaused -> AccentGold.copy(alpha = 0.16f)
                        isCountingDownSilence -> AccentCyan.copy(alpha = 0.12f)
                        isAudioRecording -> AccentEmerald.copy(alpha = 0.12f)
                        else -> BgCard
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isAutoPaused -> AccentGold.copy(alpha = 0.6f)
                            isCountingDownSilence -> AccentCyan.copy(alpha = 0.4f)
                            isAudioRecording -> AccentEmerald.copy(alpha = 0.35f)
                            else -> BorderSubtle
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("silence_detection_status_banner")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusText = when {
                                isAutoPaused -> "⏸️ مکث خودکار در سکوت ۵ ثانیه‌ای — در انتظار صدای سخنران..."
                                isCountingDownSilence -> String.format(
                                    Locale.US,
                                    "⏳ سکوت تشخیص داده شد (%.1f ثانیه تا مکث خودکار)",
                                    silenceDetectionStatus.remainingSecondsToAutoPause
                                )
                                isAudioRecording -> "🗣️ گفتار فعال تشخیص داده شد — ضبط بدون وقفه"
                                else -> "✅ آماده پایش سکوت (مکث پس از ۵ ثانیه سکوت)"
                            }
                            Text(
                                text = statusText,
                                color = when {
                                    isAutoPaused -> AccentGold
                                    isCountingDownSilence -> AccentCyan
                                    isAudioRecording -> AccentEmeraldLight
                                    else -> TextMuted
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            if (isAutoPaused) {
                                Surface(
                                    color = AccentGold,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onForceResumeFromSilence() }
                                        .testTag("force_resume_silence_btn")
                                ) {
                                    Text(
                                        text = "▶️ ادامه فوری",
                                        color = BgCanvas,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (isCountingDownSilence || isAutoPaused) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (isAutoPaused) 1f else silenceDetectionStatus.silenceCountdownProgress
                                },
                                color = if (isAutoPaused) AccentGold else AccentCyan,
                                trackColor = BgCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .testTag("silence_countdown_progress_bar")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Metrics Row: Trimmed silence seconds & Auto-pause count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✂️ سکوت حذف‌شده از فایل: ${silenceDetectionStatus.totalSilenceTrimmedSec} ثانیه",
                        color = AccentEmeraldLight,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("silence_trimmed_sec_text")
                    )
                    Text(
                        text = "🔄 مکث هوشمند: ${silenceDetectionStatus.autoPauseEventsCount} نوبت",
                        color = AccentPurpleLight,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("silence_autopause_count_text")
                    )
                }
            }
        }
    }
}

/**
 * Floating Action Button (FAB) for launching the real-time meeting voice recording
 * bottom sheet. Displays live elapsed time and pulsing indicator while recording.
 */
@Composable
fun VoiceRecordingFab(
    isAudioRecording: Boolean,
    isContinuousTranscribing: Boolean,
    recordingDurationSec: Long,
    recordingAmplitude: Float,
    isAutoPausedBySilence: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCapturing = isAudioRecording || isContinuousTranscribing

    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCapturing && !isAutoPausedBySilence) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isAudioRecording && isAutoPausedBySilence -> AccentGold
            isCapturing -> AccentRose
            else -> AccentPurple
        },
        label = "fab_bg"
    )

    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = if (isAudioRecording && isAutoPausedBySilence) BgCanvas else Color.White,
        shape = RoundedCornerShape(99.dp),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        ),
        modifier = modifier
            .scale(pulseScale)
            .border(
                width = 1.5.dp,
                color = if (isCapturing) Color.White.copy(alpha = 0.65f) else AccentPurpleLight.copy(alpha = 0.5f),
                shape = RoundedCornerShape(99.dp)
            )
            .testTag("voice_recording_fab")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            if (isAudioRecording && isAutoPausedBySilence) {
                Icon(
                    imageVector = Icons.Default.PauseCircleFilled,
                    contentDescription = "مکث خودکار در سکوت",
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "مکث سکوت • ${formatRecordingElapsedTime(recordingDurationSec, includeHours = false)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            } else if (isCapturing) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "در حال ضبط صوت",
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "ضبط زنده • ${formatRecordingElapsedTime(recordingDurationSec, includeHours = false)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "استودیو ضبط صوت جلسه",
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "ضبط صوت جلسه",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Interactive Bottom Sheet UI (`ModalBottomSheet`) for real-time meeting voice recording,
 * displaying a live multi-bar visual audio waveform, elapsed capture timer, microphone
 * sensitivity slider (conference room noise gate), 5-second silence auto-pause, and direct Gemini AI processing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecordingBottomSheet(
    sessionTitle: String,
    isAudioRecording: Boolean,
    recordingDurationSec: Long,
    recordingAmplitude: Float,
    micInputSensitivity: Float = AudioRecordManager.DEFAULT_SENSITIVITY,
    onMicInputSensitivityChange: (Float) -> Unit = {},
    silenceDetectionStatus: SilenceDetectionStatus = SilenceDetectionStatus(),
    onSilenceDetectionEnabledChange: (Boolean) -> Unit = {},
    onForceResumeFromSilence: () -> Unit = {},
    recentTranscriptions: List<TranscriptionSegment> = emptyList(),
    onSimulateSampleSpeechCheck: (() -> Unit)? = null,
    recordedAudioFile: File?,
    isContinuousTranscribing: Boolean,
    interimTranscript: String,
    audioRmsLevel: Float,
    activeSpeaker: String,
    isAudioProcessingWithGemini: Boolean,
    audioProcessingError: String?,
    autoSummarizeOnRecordingStop: Boolean = true,
    onAutoSummarizeOnRecordingStopChange: (Boolean) -> Unit = {},
    isTranscriptionAiLoading: Boolean = false,
    transcriptionAiAnalysis: com.example.data.TranscriptionAiAnalysis? = null,
    onAnalyzeTranscriptionsWithGemini: () -> Unit = {},
    onAddActionItemToNotes: (com.example.data.ExtractedActionItem) -> Unit = {},
    onAddAllActionItemsToNotes: () -> Unit = {},
    onToggleAudioRecording: () -> Unit,
    onToggleContinuousTranscription: () -> Unit,
    onProcessRecordedAudioWithGemini: () -> Unit,
    onNavigateToAiTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        contentColor = TextMain,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = AccentPurpleLight.copy(alpha = 0.6f))
        },
        modifier = Modifier.testTag("voice_recording_bottom_sheet")
    ) {
        VoiceRecordingSheetContent(
            sessionTitle = sessionTitle,
            isAudioRecording = isAudioRecording,
            recordingDurationSec = recordingDurationSec,
            recordingAmplitude = recordingAmplitude,
            micInputSensitivity = micInputSensitivity,
            onMicInputSensitivityChange = onMicInputSensitivityChange,
            silenceDetectionStatus = silenceDetectionStatus,
            onSilenceDetectionEnabledChange = onSilenceDetectionEnabledChange,
            onForceResumeFromSilence = onForceResumeFromSilence,
            recentTranscriptions = recentTranscriptions,
            onSimulateSampleSpeechCheck = onSimulateSampleSpeechCheck,
            recordedAudioFile = recordedAudioFile,
            isContinuousTranscribing = isContinuousTranscribing,
            interimTranscript = interimTranscript,
            audioRmsLevel = audioRmsLevel,
            activeSpeaker = activeSpeaker,
            isAudioProcessingWithGemini = isAudioProcessingWithGemini,
            audioProcessingError = audioProcessingError,
            autoSummarizeOnRecordingStop = autoSummarizeOnRecordingStop,
            onAutoSummarizeOnRecordingStopChange = onAutoSummarizeOnRecordingStopChange,
            isTranscriptionAiLoading = isTranscriptionAiLoading,
            transcriptionAiAnalysis = transcriptionAiAnalysis,
            onAnalyzeTranscriptionsWithGemini = onAnalyzeTranscriptionsWithGemini,
            onAddActionItemToNotes = onAddActionItemToNotes,
            onAddAllActionItemsToNotes = onAddAllActionItemsToNotes,
            onToggleAudioRecording = onToggleAudioRecording,
            onToggleContinuousTranscription = onToggleContinuousTranscription,
            onProcessRecordedAudioWithGemini = onProcessRecordedAudioWithGemini,
            onNavigateToAiTab = onNavigateToAiTab,
            onDismiss = onDismiss
        )
    }
}

/**
 * Inner content composable of the Voice Recording Bottom Sheet.
 */
@Composable
fun VoiceRecordingSheetContent(
    sessionTitle: String,
    isAudioRecording: Boolean,
    recordingDurationSec: Long,
    recordingAmplitude: Float,
    micInputSensitivity: Float = AudioRecordManager.DEFAULT_SENSITIVITY,
    onMicInputSensitivityChange: (Float) -> Unit = {},
    silenceDetectionStatus: SilenceDetectionStatus = SilenceDetectionStatus(),
    onSilenceDetectionEnabledChange: (Boolean) -> Unit = {},
    onForceResumeFromSilence: () -> Unit = {},
    recentTranscriptions: List<TranscriptionSegment> = emptyList(),
    onSimulateSampleSpeechCheck: (() -> Unit)? = null,
    recordedAudioFile: File?,
    isContinuousTranscribing: Boolean,
    interimTranscript: String,
    audioRmsLevel: Float,
    activeSpeaker: String,
    isAudioProcessingWithGemini: Boolean,
    audioProcessingError: String?,
    autoSummarizeOnRecordingStop: Boolean = true,
    onAutoSummarizeOnRecordingStopChange: (Boolean) -> Unit = {},
    isTranscriptionAiLoading: Boolean = false,
    transcriptionAiAnalysis: com.example.data.TranscriptionAiAnalysis? = null,
    onAnalyzeTranscriptionsWithGemini: () -> Unit = {},
    onAddActionItemToNotes: (com.example.data.ExtractedActionItem) -> Unit = {},
    onAddAllActionItemsToNotes: () -> Unit = {},
    onToggleAudioRecording: () -> Unit,
    onToggleContinuousTranscription: () -> Unit,
    onProcessRecordedAudioWithGemini: () -> Unit,
    onNavigateToAiTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCapturing = isAudioRecording || isContinuousTranscribing
    val isAutoPaused = isAudioRecording && silenceDetectionStatus.isAutoPaused
    val barCount = 36

    // Rolling buffer of 36 normalized amplitude bars (0f..1f)
    val waveformBars = remember {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.08f) }
        }
    }
    var tickCounter by remember { mutableStateOf(0) }

    // Continuously sample microphone amplitude every 100ms while sheet is visible
    LaunchedEffect(isCapturing, isAutoPaused, recordingAmplitude, audioRmsLevel, micInputSensitivity) {
        while (true) {
            tickCounter++
            val sample = computeEffectiveWaveSample(
                isCapturing = isCapturing && !isAutoPaused,
                recordingAmplitude = recordingAmplitude,
                audioRmsLevel = audioRmsLevel,
                tickIndex = tickCounter,
                micSensitivity = micInputSensitivity
            )
            if (waveformBars.size >= barCount) {
                waveformBars.removeAt(0)
            }
            waveformBars.add(sample)
            delay(100L)
        }
    }

    val currentSignalPercent = remember(isCapturing, recordingAmplitude, audioRmsLevel, tickCounter, micInputSensitivity) {
        if (!isCapturing) 0
        else (computeEffectiveWaveSample(true, recordingAmplitude, audioRmsLevel, tickCounter, micInputSensitivity) * 100)
            .toInt()
            .coerceIn(5, 100)
    }

    val noiseGateThreshold = remember(micInputSensitivity) {
        AudioRecordManager.computeNoiseGateThreshold(micInputSensitivity)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Top Header Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isCapturing) AccentRose.copy(alpha = 0.2f) else AccentPurple.copy(alpha = 0.2f)
                        )
                        .border(
                            1.dp,
                            if (isCapturing) AccentRose else AccentPurple,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCapturing) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isCapturing) AccentRose else AccentPurpleLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "استودیو ضبط زنده مذاکرات جلسه",
                        color = TextMain,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = sessionTitle.ifBlank { "جلسه راهبردی سازمانی" },
                        color = AccentCyan,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BgInput)
                    .testTag("close_voice_sheet_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "بستن پنجره ضبط",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 2. Elapsed Time & Live Signal Status Panel ---
        Surface(
            color = BgInput,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isCapturing) AccentRose.copy(alpha = 0.55f) else BorderSubtle
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_recording_timer_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Pill
                Surface(
                    color = if (isCapturing) AccentRose.copy(alpha = 0.18f) else AccentPurple.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCapturing) AccentRose.copy(alpha = 0.5f) else AccentPurple.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isAutoPaused -> AccentGold
                                        isCapturing -> AccentRose
                                        else -> TextSub
                                    }
                                )
                        )
                        Text(
                            text = when {
                                isAutoPaused -> "⏸️ توقف خودکار در سکوت ۵ ثانیه‌ای (آماده ادامه با شروع صحبت)"
                                isAudioRecording -> "در حال ضبط صوت با فیلتر نویز سالن (AAC / M4A)"
                                isContinuousTranscribing -> "در حال دریافت امواج صوتی و تایپ زنده"
                                else -> "آماده شروع ضبط مذاکرات جلسه"
                            },
                            color = when {
                                isAutoPaused -> AccentGold
                                isCapturing -> AccentRose
                                else -> AccentPurpleLight
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Large Elapsed Time Counter
                Text(
                    text = formatRecordingElapsedTime(recordingDurationSec, includeHours = true),
                    color = if (isCapturing) Color.White else TextMain,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.testTag("voice_recording_elapsed_time_text")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Signal intensity & active speaker metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎙️ سخنران: $activeSpeaker",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "📊 شدت سیگنال: $currentSignalPercent%",
                        color = if (isCapturing) AccentEmeraldLight else TextSub,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Real-Time Visual Audio Waveform Canvas + Integrated Live Transcription Overlay ---
        var showTranscriptionOverlay by remember { mutableStateOf(true) }
        var isSideBySideOverlayMode by remember { mutableStateOf(false) }

        val effectiveWaveSignal = remember(isCapturing, isAutoPaused, recordingAmplitude, audioRmsLevel, tickCounter, micInputSensitivity) {
            computeEffectiveWaveSample(
                isCapturing = isCapturing && !isAutoPaused,
                recordingAmplitude = recordingAmplitude,
                audioRmsLevel = audioRmsLevel,
                tickIndex = tickCounter,
                micSensitivity = micInputSensitivity
            )
        }

        val qualityAssessment = remember(
            isCapturing,
            isAutoPaused,
            effectiveWaveSignal,
            micInputSensitivity,
            interimTranscript,
            recentTranscriptions
        ) {
            evaluateRecordingQuality(
                isCapturing = isCapturing,
                isAutoPausedBySilence = isAutoPaused,
                effectiveSignal = effectiveWaveSignal,
                micSensitivity = micInputSensitivity,
                interimTranscript = interimTranscript,
                recentTranscriptions = recentTranscriptions
            )
        }

        Surface(
            color = BgInput,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isCapturing) AccentCyan.copy(alpha = 0.45f) else BorderSubtle
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_recording_waveform_canvas")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Control Strip inside Waveform Panel: Mode Switch & Overlay Toggle
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
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isCapturing) AccentCyan else TextSub)
                        )
                        Text(
                            text = if (isCapturing) "LIVE WAVEFORM + TRANSCRIPT OVERLAY" else "WAVEFORM & LIVE TRANSCRIPT STANDBY",
                            color = if (isCapturing) AccentCyan else TextSub,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showTranscriptionOverlay) {
                            Surface(
                                color = BgCard,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { isSideBySideOverlayMode = !isSideBySideOverlayMode }
                                    .testTag("toggle_overlay_layout_mode_btn")
                            ) {
                                Text(
                                    text = if (isSideBySideOverlayMode) "چیدمان: کنار موج" else "چیدمان: روی موج",
                                    color = AccentPurpleLight,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            color = if (showTranscriptionOverlay) AccentCyan.copy(alpha = 0.18f) else BgCard,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (showTranscriptionOverlay) AccentCyan.copy(alpha = 0.5f) else BorderSubtle
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showTranscriptionOverlay = !showTranscriptionOverlay }
                                .testTag("toggle_transcription_overlay_btn")
                        ) {
                            Text(
                                text = if (showTranscriptionOverlay) "👁️ زیرنویس زنده: فعال" else "👁️ زیرنویس زنده: مخفی",
                                color = if (showTranscriptionOverlay) AccentCyan else TextMuted,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (showTranscriptionOverlay && isSideBySideOverlayMode) {
                    // Side-by-Side Split Layout: Waveform on one side, Live Transcription Overlay on the other
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.95f)
                                .height(115.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgCard.copy(alpha = 0.55f))
                                .padding(6.dp)
                        ) {
                            WaveformBarsCanvas(
                                waveformBars = waveformBars,
                                isCapturing = isCapturing,
                                noiseGateThreshold = noiseGateThreshold
                            )
                        }

                        LiveTranscriptionWaveformOverlay(
                            isCapturing = isCapturing,
                            isAutoPausedBySilence = isAutoPaused,
                            activeSpeaker = activeSpeaker,
                            interimTranscript = interimTranscript,
                            recentTranscriptions = recentTranscriptions,
                            qualityAssessment = qualityAssessment,
                            onToggleContinuousTranscription = onToggleContinuousTranscription,
                            isContinuousTranscribing = isContinuousTranscribing,
                            onSimulateSampleSpeechCheck = onSimulateSampleSpeechCheck,
                            modifier = Modifier.weight(1.25f)
                        )
                    }
                } else {
                    // Stacked / HUD Overlay Mode: Waveform Canvas + Translucent Live Transcription Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                    ) {
                        WaveformBarsCanvas(
                            waveformBars = waveformBars,
                            isCapturing = isCapturing,
                            noiseGateThreshold = noiseGateThreshold
                        )
                    }

                    AnimatedVisibility(visible = showTranscriptionOverlay) {
                        LiveTranscriptionWaveformOverlay(
                            isCapturing = isCapturing,
                            isAutoPausedBySilence = isAutoPaused,
                            activeSpeaker = activeSpeaker,
                            interimTranscript = interimTranscript,
                            recentTranscriptions = recentTranscriptions,
                            qualityAssessment = qualityAssessment,
                            onToggleContinuousTranscription = onToggleContinuousTranscription,
                            isContinuousTranscribing = isContinuousTranscribing,
                            onSimulateSampleSpeechCheck = onSimulateSampleSpeechCheck
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3.5. Microphone Input Sensitivity & Background Noise Gate Slider ---
        MicSensitivityControlPanel(
            micInputSensitivity = micInputSensitivity,
            onMicInputSensitivityChange = onMicInputSensitivityChange,
            sliderTestTag = "mic_sensitivity_slider"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3.6. Automatic 5-Second Silence Detection Card ---
        SilenceDetectionControlCard(
            isAudioRecording = isAudioRecording,
            silenceDetectionStatus = silenceDetectionStatus,
            onSilenceDetectionEnabledChange = onSilenceDetectionEnabledChange,
            onForceResumeFromSilence = onForceResumeFromSilence,
            switchTestTag = "silence_detection_switch"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- 4. Primary Capture Controls Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary Record / Stop Audio Button
            Button(
                onClick = onToggleAudioRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAudioRecording) AccentRose else AccentPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .testTag("voice_sheet_record_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isAudioRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAudioRecording) "توقف و ذخیره ضبط" else "شروع ضبط صوت جلسه",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Secondary Live Speech-to-Text Toggle Button
            OutlinedButton(
                onClick = onToggleContinuousTranscription,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isContinuousTranscribing) AccentCyan.copy(alpha = 0.18f) else BgInput,
                    contentColor = if (isContinuousTranscribing) AccentCyan else TextMain
                ),
                border = BorderStroke(
                    1.dp,
                    if (isContinuousTranscribing) AccentCyan else BorderSubtle
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("voice_sheet_stt_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isContinuousTranscribing) Icons.Default.HearingDisabled else Icons.Default.Hearing,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isContinuousTranscribing) "توقف تایپ زنده" else "تایپ صوتی زنده",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- 4.5. Automatic Post-Recording Gemini Transcript Summarization Switch ---
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = BgInput,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (autoSummarizeOnRecordingStop) AccentPurple.copy(alpha = 0.55f) else BorderSubtle
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_sheet_auto_summarize_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentPurpleLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "خلاصه‌سازی خودکار رونوشت به تصمیمات و اقدامات (Gemini)",
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "بلافاصله پس از توقف ضبط، متن گفتار به تصمیمات کلیدی و وظایف تبدیل می‌شود",
                            color = TextSub,
                            fontSize = 9.5.sp
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
                    modifier = Modifier.testTag("voice_sheet_auto_summarize_switch")
                )
            }
        }

        // --- 4.8. Immediate Post-Stop Gemini Transcript Summary, Key Decisions & Action Items ---
        if ((isTranscriptionAiLoading || isAudioProcessingWithGemini || transcriptionAiAnalysis != null) && !isCapturing) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = BgInput,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_sheet_gemini_instant_summary_panel")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentEmeraldLight,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "🤖 خلاصه فوری رونوشت، تصمیمات و اقدامات (Gemini)",
                                color = TextMain,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                        Surface(
                            color = AccentEmerald.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isTranscriptionAiLoading || isAudioProcessingWithGemini) "⏳ در حال تلخیص..." else "✅ استخراج فوری",
                                color = AccentEmeraldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isTranscriptionAiLoading || isAudioProcessingWithGemini) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                color = AccentPurpleLight,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "در حال تحلیل رونوشت صوتی با Gemini و استخراج تصمیمات کلیدی و وظایف...",
                                color = AccentPurpleLight,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    transcriptionAiAnalysis?.let { analysis ->
                        Text(
                            text = analysis.executiveSummary,
                            color = TextMain,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.testTag("voice_sheet_summary_text")
                        )

                        if (analysis.keyDecisions.isNotEmpty()) {
                            Text(
                                text = "🎯 تصمیمات کلیدی استخراج‌شده (${analysis.keyDecisions.size}):",
                                color = AccentEmeraldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.testTag("voice_sheet_key_decisions_list")
                            ) {
                                analysis.keyDecisions.forEach { dec ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BgCard)
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = AccentEmeraldLight,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = dec,
                                            color = TextMain,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (analysis.actionItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📌 اقدامات و وظایف محوله (${analysis.actionItems.size}):",
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                                if (analysis.actionItems.any { !it.isAddedToNotes }) {
                                    Surface(
                                        color = AccentAmber.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onAddAllActionItemsToNotes() }
                                            .testTag("voice_sheet_add_all_actions_btn")
                                    ) {
                                        Text(
                                            text = "➕ ثبت همه در مصوبات",
                                            color = AccentAmber,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.testTag("voice_sheet_action_items_list")
                            ) {
                                analysis.actionItems.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BgCard)
                                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                color = TextMain,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "👤 مسئول: ${item.assignee} • ⏳ مهلت: ${item.deadline} • اولویت: ${item.priority}",
                                                color = AccentCyan,
                                                fontSize = 9.sp
                                            )
                                        }
                                        if (!item.isAddedToNotes) {
                                            IconButton(
                                                onClick = { onAddActionItemToNotes(item) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddCircleOutline,
                                                    contentDescription = "افزودن به مصوبات",
                                                    tint = AccentEmeraldLight,
                                                    modifier = Modifier.size(16.dp)
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
        }

        // --- 5. Recorded Audio File & Instant Gemini AI Processing Card ---
        if (recordedAudioFile != null && !isAudioRecording) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = BgInput,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_sheet_recorded_file_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = AccentEmeraldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "فایل صوتی آماده پردازش: ${recordedAudioFile.name}",
                                    color = TextMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "حجم: ${(recordedAudioFile.length() / 1024).coerceAtLeast(1)} KB • مدت: ${formatRecordingElapsedTime(recordingDurationSec, false)}",
                                    color = TextSub,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }

                    if (audioProcessingError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ $audioProcessingError",
                            color = AccentRose,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onProcessRecordedAudioWithGemini()
                            onNavigateToAiTab()
                        },
                        enabled = !isAudioProcessingWithGemini,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = BgCanvas
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("voice_sheet_gemini_analyze_btn")
                    ) {
                        if (isAudioProcessingWithGemini) {
                            CircularProgressIndicator(
                                color = BgCanvas,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("در حال ارسال صوت به Gemini...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ارسال فایل صوتی به Gemini جهت تبدیل به متن و خلاصه‌سازی ✨",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Renders the multi-bar audio waveform with center reference horizon and upper/lower
 * Noise Gate threshold cutoff lines.
 */
@Composable
fun WaveformBarsCanvas(
    waveformBars: List<Float>,
    isCapturing: Boolean,
    noiseGateThreshold: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        val gateOffset = height * 0.45f * noiseGateThreshold
        drawLine(
            color = AccentEmerald.copy(alpha = 0.45f),
            start = Offset(0f, centerY - gateOffset),
            end = Offset(width, centerY - gateOffset),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = AccentEmerald.copy(alpha = 0.45f),
            start = Offset(0f, centerY + gateOffset),
            end = Offset(width, centerY + gateOffset),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        val totalBars = waveformBars.size.coerceAtLeast(1)
        val slotWidth = width / totalBars.toFloat()
        val barWidth = (slotWidth * 0.58f).coerceAtLeast(2.dp.toPx())

        val activeBrush = Brush.verticalGradient(
            colors = listOf(
                AccentCyan,
                AccentPurpleLight,
                AccentRose,
                AccentPurpleLight,
                AccentCyan
            )
        )
        val gatedBrush = Brush.verticalGradient(
            colors = listOf(
                TextSub.copy(alpha = 0.35f),
                TextSub.copy(alpha = 0.18f)
            )
        )

        waveformBars.forEachIndexed { idx, amplitude ->
            val normalized = amplitude.coerceIn(0.06f, 1f)
            val isAboveNoiseGate = isCapturing && normalized >= noiseGateThreshold
            val barHalfHeight = (height * 0.45f * normalized).coerceAtLeast(3.dp.toPx())
            val x = idx * slotWidth + (slotWidth - barWidth) / 2f

            drawRoundRect(
                brush = if (isAboveNoiseGate) activeBrush else gatedBrush,
                topLeft = Offset(x, centerY - barHalfHeight),
                size = Size(barWidth, barHalfHeight * 2f),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

