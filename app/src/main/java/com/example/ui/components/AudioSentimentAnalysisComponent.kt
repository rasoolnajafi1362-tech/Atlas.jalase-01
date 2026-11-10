package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioSentimentAnalysis
import com.example.data.HighTensionSegment
import com.example.ui.theme.*

@Composable
fun AudioSentimentAnalysisComponent(
    sentimentAnalysis: AudioSentimentAnalysis?,
    isAnalyzingSentiment: Boolean,
    sentimentError: String?,
    hasRecordedAudio: Boolean,
    onAnalyzeSentiment: () -> Unit,
    onToggleReviewed: (String, String) -> Unit,
    onAddResolutionFromSegment: (HighTensionSegment) -> Unit,
    onClearSentiment: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedReviewNotesId by remember { mutableStateOf<String?>(null) }
    var reviewNoteInput by remember { mutableStateOf("") }

    QuantumPanel(
        title = "🎙️ تحلیل لحن صوتی و پایش تنش (Gemini Sentiment)",
        badgeText = when {
            isAnalyzingSentiment -> "⏳ در حال ارزیابی..."
            sentimentAnalysis != null -> "تحلیل شد (${sentimentAnalysis.flaggedSegments.size} تنش)"
            else -> "آماده ارزیابی"
        },
        modifier = modifier.testTag("sentiment_analysis_panel")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Description
            Text(
                text = "ارزیابی هوشمند اتمسفر و لحن فایل‌های صوتی/مذاکرات جلسه با مدل Gemini 2.5 Flash؛ کشف نقاط اصطکاک و پرچم‌گذاری بخش‌های پرتنش جهت بازبینی مدیریتی و حل تعارض.",
                color = TextSub,
                fontSize = 11.5.sp,
                lineHeight = 18.sp
            )

            // Trigger & Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAnalyzeSentiment,
                    enabled = !isAnalyzingSentiment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("analyze_sentiment_button")
                ) {
                    if (isAnalyzingSentiment) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ارزیابی لحن با Gemini...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sentimentAnalysis == null) "تحلیل لحن صوتی و استخراج تنش‌ها" else "تحلیل مجدد لحن جلسه",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (sentimentAnalysis != null) {
                    OutlinedButton(
                        onClick = onClearSentiment,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("clear_sentiment_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بازنشانی", fontSize = 10.5.sp)
                    }
                }
            }

            // Audio Presence Warning
            if (!hasRecordedAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentCyan.copy(alpha = 0.08f))
                        .border(1.dp, AccentCyan.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Text(
                        text = "نکته: در صورت عدم وجود فایل ضبط‌شده، تحلیل بر اساس متن پیاده‌سازی صوتی و مذاکرات انجام می‌شود.",
                        color = AccentCyan,
                        fontSize = 10.sp
                    )
                }
            }

            // Error display
            if (sentimentError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentRose.copy(alpha = 0.12f))
                        .border(1.dp, AccentRose.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "خطا در تحلیل: $sentimentError",
                        color = AccentRose,
                        fontSize = 10.5.sp
                    )
                }
            }

            // Sentiment Results Panel
            if (sentimentAnalysis != null) {
                Spacer(modifier = Modifier.height(4.dp))

                // Tone & Harmony Overview Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgInput),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sentiment_overview_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Header Tone & Dominant Emotion
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("لحن عمومی حاکم بر مذاکرات:", color = TextSub, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sentimentAnalysis.overallTone,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            sentimentAnalysis.tensionScore > 35 -> AccentRose.copy(alpha = 0.18f)
                                            sentimentAnalysis.harmonyScore >= 70 -> AccentEmerald.copy(alpha = 0.18f)
                                            else -> AccentAmber.copy(alpha = 0.18f)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            sentimentAnalysis.tensionScore > 35 -> AccentRose.copy(alpha = 0.5f)
                                            sentimentAnalysis.harmonyScore >= 70 -> AccentEmerald.copy(alpha = 0.5f)
                                            else -> AccentAmber.copy(alpha = 0.5f)
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (sentimentAnalysis.tensionScore > 35) "⚠️ پرچالش / تنش‌زا" else "✨ تعامل سازنده",
                                    color = if (sentimentAnalysis.tensionScore > 35) AccentRose else AccentEmeraldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Score Gauges: Harmony vs Tension
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Harmony Score Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgCard),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("شاخص هارمونی و هم‌افزایی", color = TextSub, fontSize = 9.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${sentimentAnalysis.harmonyScore}%",
                                        color = AccentEmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            // Tension Score Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgCard),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (sentimentAnalysis.tensionScore > 30) AccentRose.copy(alpha = 0.5f) else AccentAmber.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("شاخص تنش و اصطکاک", color = TextSub, fontSize = 9.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${sentimentAnalysis.tensionScore}%",
                                        color = if (sentimentAnalysis.tensionScore > 30) AccentRose else AccentAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        // Discussion Distribution Bar
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("تفکیک محتوایی گفت‌وگوها:", color = TextSub, fontSize = 9.5.sp)
                                Text(
                                    text = "سازنده: ${sentimentAnalysis.constructivePercentage}% | خنثی: ${sentimentAnalysis.neutralPercentage}% | تنش: ${sentimentAnalysis.tensionPercentage}%",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }

                            // Proportional bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(BgCard)
                            ) {
                                val cWeight = sentimentAnalysis.constructivePercentage.coerceAtLeast(1).toFloat()
                                val nWeight = sentimentAnalysis.neutralPercentage.coerceAtLeast(1).toFloat()
                                val tWeight = sentimentAnalysis.tensionPercentage.coerceAtLeast(1).toFloat()

                                Box(
                                    modifier = Modifier
                                        .weight(cWeight)
                                        .fillMaxHeight()
                                        .background(AccentEmerald)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(nWeight)
                                        .fillMaxHeight()
                                        .background(AccentCyan.copy(alpha = 0.7f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(tWeight)
                                        .fillMaxHeight()
                                        .background(AccentRose)
                                )
                            }
                        }

                        // Dominant Emotion
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("گرایش احساسی غالب:", color = TextSub, fontSize = 9.5.sp)
                            Text(
                                text = sentimentAnalysis.dominantEmotion,
                                color = AccentPurpleLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            )
                        }

                        // Summary Evaluation
                        if (sentimentAnalysis.summaryEvaluation.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgCard)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = sentimentAnalysis.summaryEvaluation,
                                    color = TextMain,
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Flagged High-Tension Segments Section
                val unreviewedCount = sentimentAnalysis.flaggedSegments.count { !it.isReviewed }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                        Text(
                            text = "بخش‌های پرچم‌گذاری‌شده جهت بازبینی (${sentimentAnalysis.flaggedSegments.size} مورد):",
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                    if (unreviewedCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentRose.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$unreviewedCount مورد نیازمند بررسی",
                                color = AccentRose,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Segments List
                sentimentAnalysis.flaggedSegments.forEachIndexed { index, segment ->
                    HighTensionSegmentCard(
                        segment = segment,
                        onToggleReviewed = { notes -> onToggleReviewed(segment.id, notes) },
                        onAddResolution = { onAddResolutionFromSegment(segment) },
                        isNotesExpanded = expandedReviewNotesId == segment.id,
                        onExpandNotes = {
                            if (expandedReviewNotesId == segment.id) {
                                expandedReviewNotesId = null
                            } else {
                                expandedReviewNotesId = segment.id
                                reviewNoteInput = segment.reviewNotes
                            }
                        },
                        noteInput = reviewNoteInput,
                        onNoteInputChange = { reviewNoteInput = it }
                    )
                }
            }
        }
    }
}

@Composable
fun HighTensionSegmentCard(
    segment: HighTensionSegment,
    onToggleReviewed: (String) -> Unit,
    onAddResolution: () -> Unit,
    isNotesExpanded: Boolean,
    onExpandNotes: () -> Unit,
    noteInput: String,
    onNoteInputChange: (String) -> Unit
) {
    val levelColor = when (segment.tensionLevel) {
        "بحرانی" -> AccentRose
        "شدید" -> AccentAmber
        else -> AccentCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (segment.isReviewed) BorderSubtle else levelColor.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("flagged_segment_${segment.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Timestamp, Tension Badge & Speaker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Level badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(levelColor.copy(alpha = 0.15f))
                            .border(1.dp, levelColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "تنش ${segment.tensionLevel}",
                            color = levelColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }

                    // Timestamp
                    Text(
                        text = "⏱️ ${segment.timestampRange}",
                        color = TextSub,
                        fontSize = 10.sp
                    )
                }

                // Reviewed status chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (segment.isReviewed) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (segment.isReviewed) "تأیید شد ✅" else "نیازمند بازبینی ⚠️",
                        color = if (segment.isReviewed) AccentEmeraldLight else AccentRose,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Speaker & Trigger Topic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "👤 ${segment.speaker}:",
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = segment.triggerTopic,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }

            // Contentious Dialogue Excerpt (Quote Box)
            if (segment.quoteExcerpt.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgInput)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = segment.quoteExcerpt,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // Executive Recommendation
            if (segment.reviewRecommendation.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("💡 پیشنهاد رفع تعارض:", color = AccentEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(segment.reviewRecommendation, color = TextSub, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }

            // Existing review notes (if any)
            if (segment.reviewNotes.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("📝 یادداشت بازبینی:", color = AccentPurpleLight, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                    Text(segment.reviewNotes, color = TextMain, fontSize = 9.5.sp)
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Review Button
                Button(
                    onClick = { onToggleReviewed(noteInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (segment.isReviewed) AccentEmerald.copy(alpha = 0.2f) else AccentPurple,
                        contentColor = if (segment.isReviewed) AccentEmeraldLight else Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("toggle_review_${segment.id}")
                ) {
                    Icon(
                        if (segment.isReviewed) Icons.Default.CheckCircle else Icons.Default.Done,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (segment.isReviewed) "بازبینی‌شده" else "ثبت بازبینی",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Add to Meeting Resolutions Button
                OutlinedButton(
                    onClick = onAddResolution,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmeraldLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("add_resolution_${segment.id}")
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("درج در مصوبات", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                // Add Note Toggle
                IconButton(
                    onClick = onExpandNotes,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = "ثبت یادداشت بازبینی",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Review Note Input Field
            AnimatedVisibility(
                visible = isNotesExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = onNoteInputChange,
                        placeholder = { Text("یادداشت یا دستور اجرایی مدیر در خصوص این بخش پرتنش...", fontSize = 10.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_note_input_${segment.id}"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        maxLines = 3,
                        shape = RoundedCornerShape(6.dp)
                    )

                    Button(
                        onClick = { onToggleReviewed(noteInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(28.dp)
                    ) {
                        Text("ذخیره یادداشت و ثبت بازبینی", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
