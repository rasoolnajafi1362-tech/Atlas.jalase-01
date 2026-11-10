package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CorporatePdfConfig
import com.example.data.DigitalSignaturePlaceholder
import com.example.data.MeetingSession
import com.example.data.OfficialMinutesDraft
import com.example.ui.components.QuantumPanel
import com.example.ui.theme.*
import java.io.File

@Composable
fun OfficialReportScreen(
    session: MeetingSession,
    onArchiveSession: () -> Unit,
    onExportPdf: () -> Unit = {},
    onViewPdf: () -> Unit = {},
    officialMinutesDraft: OfficialMinutesDraft? = session.officialMinutesDraft,
    isGeneratingMinutesDraft: Boolean = false,
    minutesDraftError: String? = null,
    selectedDraftTemplate: String = "الگوی رسمی هیئت‌مدیره و حاکمیت شرکتی",
    onSelectDraftTemplate: (String) -> Unit = {},
    onGenerateOfficialDraftPdf: (Boolean) -> Unit = {},
    onShareOfficialDraftPdf: () -> Unit = {},
    onViewOfficialDraftPdf: () -> Unit = {},
    onUpdateCorporatePdfConfig: (CorporatePdfConfig) -> Unit = {},
    onToggleSessionFinalized: (Boolean) -> Unit = {},
    onConvertFinalizedToCorporatePdf: () -> Unit = {},
    onShareFinalizedCorporatePdf: () -> Unit = {},
    onViewFinalizedCorporatePdf: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveDraft = officialMinutesDraft ?: session.officialMinutesDraft
    val corporateConfig = session.corporatePdfConfig
    var showCorporateSettingsEditor by remember { mutableStateOf(false) }

    fun generateFullReportText(): String {
        return buildString {
            append("============================================================\n")
            append("صورتجلسه رسمی: ${session.title.ifBlank { "جلسه راهبردی سازمانی" }}\n")
            append("تاریخ: ${session.date} | ساعت شروع: ${session.startTime} | مدت: ${session.durationMins} دقیقه\n")
            append("مکان: ${session.location.ifBlank { "سالن کنفرانس مرکزی" }}\n")
            if (session.extCompany.isNotBlank()) {
                append("طرف مقابل: ${session.extCompany} (کد مرجع: ${session.extRef.ifBlank { "—" }})\n")
            }
            append("============================================================\n\n")

            val aiSummary = session.transcriptionAnalysis?.executiveSummary?.takeIf { it.isNotBlank() }
                ?: session.aiSummary.takeIf { it.isNotBlank() }

            if (aiSummary != null) {
                append("--- خلاصه مدیریتی و جمع‌بندی هوشمند Gemini ---\n")
                append("$aiSummary\n\n")
            }

            val sentiment = session.audioSentimentAnalysis ?: session.latestAudioAnalysis?.sentimentAnalysis
            if (sentiment != null) {
                append("--- تحلیل لحن و عواطف صوتی جلسه (Gemini Sentiment) ---\n")
                append("• لحن کلی: ${sentiment.overallTone}\n")
                append("• شاخص هارمونی: ${sentiment.harmonyScore}% | شاخص تنش و اصطکاک: ${sentiment.tensionScore}%\n")
                append("• تفکیک گفت‌وگو: سازنده: ${sentiment.constructivePercentage}% | خنثی: ${sentiment.neutralPercentage}% | اصطکاک: ${sentiment.tensionPercentage}%\n")
                append("• گرایش احساسی غالب: ${sentiment.dominantEmotion}\n")
                if (sentiment.summaryEvaluation.isNotBlank()) {
                    append("• ارزیابی: ${sentiment.summaryEvaluation}\n")
                }
                if (sentiment.flaggedSegments.isNotEmpty()) {
                    append("• بخش‌های دارای تنش شدید پرچم‌گذاری‌شده (${sentiment.flaggedSegments.size} مورد):\n")
                    sentiment.flaggedSegments.forEachIndexed { i, s ->
                        val reviewStatus = if (s.isReviewed) "[بازبینی‌شده]" else "[نیازمند بررسی]"
                        append("  ${i + 1}. $reviewStatus [${s.timestampRange}] (${s.speaker}) - تنش: ${s.tensionLevel} | ریشه: ${s.triggerTopic}\n")
                        if (s.quoteExcerpt.isNotBlank()) append("     نقل‌قول: ${s.quoteExcerpt}\n")
                        if (s.reviewRecommendation.isNotBlank()) append("     پیشنهاد: ${s.reviewRecommendation}\n")
                    }
                }
                append("\n")
            }

            session.transcriptionAnalysis?.let { analysis ->
                if (analysis.keyDecisions.isNotEmpty()) {
                    append("--- تصمیمات کلیدی جلسه ---\n")
                    analysis.keyDecisions.forEachIndexed { i, d ->
                        append("${i + 1}. $d\n")
                    }
                    append("\n")
                }
                if (analysis.actionItems.isNotEmpty()) {
                    append("--- تعهدات و اقدامات اجرایی استخراج‌شده (${analysis.actionItems.size} اقدام) ---\n")
                    analysis.actionItems.forEachIndexed { i, item ->
                        append("${i + 1}. [${item.priority}] ${item.title} | مسئول: ${item.assignee} | مهلت: ${item.deadline}\n")
                    }
                    append("\n")
                }
            }

            append("--- ۱. اعضای حاضر در جلسه ---\n")
            session.members.filter { it.isPresent }.forEachIndexed { idx, m ->
                append("${idx + 1}. ${m.name} | سمت: ${m.title} | واحد: ${m.unit}\n")
            }

            if (session.opsData.enabled) {
                append("\n--- شاخص‌های لجستیک 5PL و مالی ---\n")
                append("• بهره‌وری ناوگان: ${session.opsData.fleetEff}\n")
                append("• زنجیره سرد: ${session.opsData.coldChain}\n")
                append("• مدل پالت: ${session.opsData.palletModel}\n")
                append("• بودجه مصوب: ${session.opsData.budget}\n")
                append("• خلاصه اجرایی: ${session.opsData.summary}\n")
            }

            if (session.hrData.enabled) {
                append("\n--- ارزیابی کمیته منابع انسانی (HR) ---\n")
                append("• کاندیدا: ${session.hrData.candidate} | جایگاه: ${session.hrData.position} | امتیاز: ${session.hrData.score}\n")
                append("• تصمیم کمیته: ${session.hrData.decision}\n")
                append("• ملاحظات: ${session.hrData.notes}\n")
            }

            append("\n--- ۲. مصوبات و تعهدات ثبت‌شده (${session.notes.size} مورد) ---\n")
            session.notes.forEachIndexed { idx, n ->
                val status = if (n.isDone) "انجام‌شده ✅" else "در جریان ⏳"
                append("${idx + 1}. [${n.timeFormatted}] (${n.speaker}) - [${n.type}]: ${n.text} | مهلت: ${n.due} | وضعیت: $status\n")
            }

            val validAttach = session.attachments.filter { it.name.isNotBlank() }
            if (validAttach.isNotEmpty()) {
                append("\n--- ۳. اسناد و فایل‌های پیوست ---\n")
                validAttach.forEach { a ->
                    append("📎 ${a.name} (کد مرجع: ${a.refCode.ifBlank { "—" }})\n")
                }
            }

            append("\n============================================================\n")
            append("صادرشده توسط اکوسیستم اطلس جلسه ۰۱ (AI Master OS) • سخنران\n")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header Actions ---
        item {
            QuantumPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "صورتجلسه رسمی",
                            color = TextMain,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = session.title.ifBlank { "جلسه راهبردی سازمانی" },
                            color = AccentPurpleLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Export / Share PDF Button
                        Button(
                            onClick = onExportPdf,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("اشتراک PDF", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // View PDF Button
                        OutlinedButton(
                            onClick = onViewPdf,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentCyan
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("view_pdf_button")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("مشاهده", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Archive Button
                        Button(
                            onClick = onArchiveSession,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = BgCanvas
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("بایگانی", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share Text Button
                        IconButton(
                            onClick = {
                                val report = generateFullReportText()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, report)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "ارسال و اشتراک صورتجلسه"))
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "اشتراک متنی", tint = TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgInput)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "🗓️ تاریخ: ${session.date} | ⏱️ ساعت: ${session.startTime} | ⏳ مدت: ${session.durationMins} دقیقه | 📍 مکان: ${session.location}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        if (session.extCompany.isNotBlank()) {
                            Text(
                                text = "🏢 طرف مقابل: ${session.extCompany} | 🔖 کد مرجع: ${session.extRef.ifBlank { "—" }}",
                                color = AccentPurpleLight,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Prominent PDF Export Callout Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("stakeholder_pdf_banner")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "📄 خروجی رسمی PDF صورتجلسه و تعهدات (جهت ارائه به ذینفعان)",
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("A4 استاندارد • برداری", color = AccentEmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "شامل خلاصه اجرایی Gemini، جدول تعهدات و اقدامات مصوب با تفکیک مسئول و مهلت، شاخص‌های لجستیک، لیست اعضا و بلوک امضای رسمی سخنران، مدیرعامل و رئیس هیئت‌مدیره.",
                            color = TextSub,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = onExportPdf,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPurple,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اشتراک و ارسال مستقیم PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onViewPdf,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاهده سند", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Standard Corporate Letterhead & Digital Signature Placeholder PDF Converter Panel ---
        item {
            QuantumPanel(
                title = "🏛️ تبدیل صورت‌جلسه نهایی‌شده به PDF استاندارد با سربرگ شرکتی و امضای دیجیتال",
                badgeText = if (session.isFinalized) "✅ نهایی‌شده (Finalized)" else "📝 پیش‌نویس",
                modifier = Modifier.testTag("corporate_letterhead_pdf_panel")
            ) {
                // 1. Finalization Status Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (session.isFinalized) AccentEmerald.copy(alpha = 0.14f) else AccentAmber.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            if (session.isFinalized) AccentEmerald.copy(alpha = 0.45f) else AccentAmber.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (session.isFinalized) "✅ وضعیت صورت‌جلسه: نهایی‌شده و آماده صدور رسمی" else "⏳ وضعیت صورت‌جلسه: پیش‌نویس در حال تکمیل",
                            color = if (session.isFinalized) AccentEmeraldLight else AccentAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "سند نهایی با سربرگ شرکتی استاندارد، کادر شماره/تاریخ/پیوست و جایگاه امضای دیجیتال (PKI Placeholder) صادر می‌شود.",
                            color = TextSub,
                            fontSize = 9.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { onToggleSessionFinalized(!session.isFinalized) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (session.isFinalized) AccentEmeraldLight else AccentAmber
                        ),
                        border = BorderStroke(1.dp, if (session.isFinalized) AccentEmerald else AccentAmber),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("finalize_session_toggle_btn")
                    ) {
                        Text(
                            text = if (session.isFinalized) "تغییر به پیش‌نویس" else "نهایی‌سازی صورت‌جلسه",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Live Preview of Corporate Letterhead (سربرگ شرکتی)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("corporate_letterhead_preview_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Gold Corporate Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(AccentAmber)
                        )
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "« بسمه تعالی • سربرگ رسمی صورت‌جلسات هیئت‌مدیره »",
                                color = AccentGold,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Right side (in RTL): Emblem + Company Name + Registration
                                Row(
                                    modifier = Modifier.weight(1.3f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(AccentCyan.copy(alpha = 0.18f))
                                            .border(1.5.dp, AccentGold, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.VerifiedUser,
                                            contentDescription = "نشان شرکتی",
                                            tint = AccentGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = corporateConfig.companyNameFa,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = corporateConfig.companyNameEn,
                                            color = AccentCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.5.sp
                                        )
                                        Text(
                                            text = corporateConfig.registrationInfo,
                                            color = TextSub,
                                            fontSize = 8.5.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Left side (in RTL): Official Correspondence Number/Date/Attachment Box
                                Surface(
                                    color = BgInput,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(0.85f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "شماره سند: ${corporateConfig.documentNumber}",
                                            color = TextMain,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "تاریخ صدور: ${session.date}",
                                            color = TextMuted,
                                            fontSize = 8.5.sp
                                        )
                                        Text(
                                            text = "پیوست: ${corporateConfig.attachmentLabel.take(20)}",
                                            color = TextMuted,
                                            fontSize = 8.sp
                                        )
                                        Text(
                                            text = "طبقه‌بندی: ${corporateConfig.classificationLevel}",
                                            color = AccentGold,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Feature Toggles & Header Customizer Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = corporateConfig.includeCorporateLetterhead,
                            onClick = {
                                onUpdateCorporatePdfConfig(
                                    corporateConfig.copy(includeCorporateLetterhead = !corporateConfig.includeCorporateLetterhead)
                                )
                            },
                            label = { Text("🏛️ سربرگ شرکتی", fontSize = 9.5.sp) },
                            modifier = Modifier.testTag("toggle_corporate_letterhead_chip")
                        )
                        FilterChip(
                            selected = corporateConfig.includeDigitalSignaturePlaceholders,
                            onClick = {
                                onUpdateCorporatePdfConfig(
                                    corporateConfig.copy(includeDigitalSignaturePlaceholders = !corporateConfig.includeDigitalSignaturePlaceholders)
                                )
                            },
                            label = { Text("🔏 امضای دیجیتال (Placeholder)", fontSize = 9.5.sp) },
                            modifier = Modifier.testTag("toggle_digital_sig_placeholder_chip")
                        )
                    }

                    TextButton(
                        onClick = { showCorporateSettingsEditor = !showCorporateSettingsEditor },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("edit_corporate_header_btn")
                    ) {
                        Icon(
                            if (showCorporateSettingsEditor) Icons.Default.ExpandLess else Icons.Default.EditNote,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (showCorporateSettingsEditor) "بستن تنظیمات" else "ویرایش سربرگ",
                            color = AccentCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Expandable Letterhead Customization Fields
                AnimatedVisibility(visible = showCorporateSettingsEditor) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgInput)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = corporateConfig.companyNameFa,
                            onValueChange = { onUpdateCorporatePdfConfig(corporateConfig.copy(companyNameFa = it)) },
                            label = { Text("عنوان رسمی شرکت در سربرگ (فارسی)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("corporate_name_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = corporateConfig.documentNumber,
                                onValueChange = { onUpdateCorporatePdfConfig(corporateConfig.copy(documentNumber = it)) },
                                label = { Text("شماره سند رسمی", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("corporate_doc_num_input")
                            )
                            OutlinedTextField(
                                value = corporateConfig.classificationLevel,
                                onValueChange = { onUpdateCorporatePdfConfig(corporateConfig.copy(classificationLevel = it)) },
                                label = { Text("سطح محرمانگی", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("corporate_classification_input")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Digital Signature Placeholders Preview Grid
                Text(
                    text = "🔏 پیش‌نمایش جایگاه امضای دیجیتال و مهر سازمانی در انتهای فایل PDF:",
                    color = AccentPurpleLight,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("digital_signature_placeholders_preview"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    corporateConfig.signatories.take(3).forEachIndexed { idx, sig ->
                        Surface(
                            color = BgInput,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("digital_signature_placeholder_card_$idx")
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = sig.roleTitle,
                                    color = TextMuted,
                                    fontSize = 8.5.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                                Text(
                                    text = sig.signerName,
                                    color = TextMain,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AccentPurple.copy(alpha = 0.12f))
                                        .border(1.dp, AccentPurpleLight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "✍️ [امضای دیجیتال • Placeholder]",
                                            color = AccentCyan,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "💮 ${sig.stampLabel}",
                                            color = AccentGold,
                                            fontSize = 7.5.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Text(
                                    text = "Cert: ${sig.certificateSerial}",
                                    color = AccentEmeraldLight,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                corporateConfig.lastGeneratedPdfPath?.let { path ->
                    val f = File(path)
                    if (f.exists()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✅ آخرین فایل PDF رسمی صادرشده با سربرگ و امضای دیجیتال: ${f.name} (${maxOf(1L, f.length() / 1024)} KB)",
                            color = AccentEmeraldLight,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Action Buttons: Convert Finalized Minutes to Standard Corporate PDF, Share, View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConvertFinalizedToCorporatePdf,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp)
                            .testTag("convert_finalized_corporate_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("تبدیل به PDF استاندارد با سربرگ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onShareFinalizedCorporatePdf,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(40.dp)
                            .testTag("share_finalized_corporate_pdf_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("اشتراک رسمی", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onViewFinalizedCorporatePdf,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("view_finalized_corporate_pdf_btn")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // --- Gemini AI Official Meeting Minutes Draft PDF Generator & Sharing Panel ---
        item {
            val templates = listOf(
                "الگوی رسمی هیئت‌مدیره و حاکمیت شرکتی",
                "الگوی کمیته اجرایی و لجستیک ۵PL",
                "الگوی تفاهم‌نامه و صورتجلسه حقوقی"
            )

            QuantumPanel(
                title = "📜 تولید پیش‌نویس صورتجلسه رسمی (PDF) با الگوی هوش مصنوعی Gemini",
                badgeText = if (isGeneratingMinutesDraft) "⏳ در حال تدوین..." else effectiveDraft?.documentNumber ?: "آماده صدور PDF",
                modifier = Modifier.testTag("gemini_official_draft_pdf_panel")
            ) {
                Text(
                    text = "تولید خودکار فایل پیش‌نویس صورتجلسه رسمی (فرمت PDF برداری A4) بر اساس نکات کلیدی، تصمیمات و مصوبات استخراج‌شده با استفاده از الگوی استاندارد هوش مصنوعی Gemini و امکان اشتراک‌گذاری مستقیم.",
                    color = TextSub,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "انتخاب الگوی نگارش هوش مصنوعی Gemini:",
                    color = AccentCyan,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    templates.forEachIndexed { idx, tpl ->
                        val isSelected = selectedDraftTemplate == tpl
                        Surface(
                            color = if (isSelected) AccentPurple.copy(alpha = 0.22f) else BgInput,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AccentPurpleLight else BorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("draft_template_option_$idx"),
                            onClick = { onSelectDraftTemplate(tpl) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tpl,
                                    color = if (isSelected) Color.White else TextMain,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmeraldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
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
                            containerColor = AccentEmerald,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("generate_official_draft_pdf_btn")
                    ) {
                        if (isGeneratingMinutesDraft) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("در حال تولید پیش‌نویس PDF...", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("✨ تولید پیش‌نویس PDF با Gemini", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onShareOfficialDraftPdf,
                        enabled = !isGeneratingMinutesDraft,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("share_official_draft_pdf_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("📤 اشتراک‌گذاری PDF پیش‌نویس", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                minutesDraftError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "خطا: $err", color = AccentRose, fontSize = 10.5.sp)
                }

                if (effectiveDraft != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgInput),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generated_official_draft_preview_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄 ${effectiveDraft.draftTitle}",
                                    color = AccentEmeraldLight,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "شماره: ${effectiveDraft.documentNumber}",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            }

                            effectiveDraft.generatedPdfPath?.let { path ->
                                val pdfFile = File(path)
                                if (pdfFile.exists()) {
                                    Text(
                                        text = "✅ فایل PDF آماده اشتراک‌گذاری: ${pdfFile.name} (${maxOf(1L, pdfFile.length() / 1024)} KB)",
                                        color = AccentGold,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = effectiveDraft.formalPreamble,
                                color = TextMain,
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )

                            if (effectiveDraft.keyPointsSummary.isNotEmpty()) {
                                Text(
                                    text = "📌 نکات کلیدی استخراج‌شده (${effectiveDraft.keyPointsSummary.size} محور):",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                                effectiveDraft.keyPointsSummary.forEachIndexed { i, pt ->
                                    Text(
                                        text = "${i + 1}. $pt",
                                        color = TextSub,
                                        fontSize = 10.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            if (effectiveDraft.formalResolutions.isNotEmpty()) {
                                Text(
                                    text = "⚖️ بندهای مصوبات رسمی تدوین‌شده با الگوی Gemini (${effectiveDraft.formalResolutions.size} ماده):",
                                    color = AccentPurpleLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                                effectiveDraft.formalResolutions.forEach { res ->
                                    Text(
                                        text = "• $res",
                                        color = TextMain,
                                        fontSize = 10.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            if (effectiveDraft.closingClause.isNotBlank()) {
                                Text(
                                    text = effectiveDraft.closingClause,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onViewOfficialDraftPdf,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مشاهده PDF پیش‌نویس", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Preamble ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "مقدمه و دستور کار: جلسه رسمی با حضور امضاکنندگان ذیل در تاریخ و ساعت مقرر جهت بررسی دستور کار، پایش شاخص‌های عملکرد و اتخاذ تصمیمات راهبردی تشکیل گردید. پس از استماع گزارش‌ها، مصوبات و تعهدات زیر جهت ابلاغ و اجرا به تصویب رسید:",
                    color = TextMain,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Justify
                )
            }
        }

        // --- AI Executive Summary Section ---
        val effectiveSummary = session.transcriptionAnalysis?.executiveSummary?.takeIf { it.isNotBlank() }
            ?: session.aiSummary.takeIf { it.isNotBlank() }

        if (effectiveSummary != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(16.dp))
                            Text(
                                text = "خلاصه مدیریتی و جمع‌بندی هوشمند جلسه (Gemini):",
                                color = AccentPurpleLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = effectiveSummary,
                            color = TextMain,
                            fontSize = 12.sp,
                            lineHeight = 20.sp
                        )

                        // Key decisions if available
                        session.transcriptionAnalysis?.let { analysis ->
                            if (analysis.keyDecisions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("📌 تصمیمات کلیدی اتخاذ شده:", color = AccentEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                analysis.keyDecisions.forEach { dec ->
                                    Text("• $dec", color = TextMain, fontSize = 11.5.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Tone Evaluation & High-Tension Analysis in Official Report ---
        val sentimentData = session.audioSentimentAnalysis ?: session.latestAudioAnalysis?.sentimentAnalysis
        if (sentimentData != null) {
            item {
                QuantumPanel(
                    title = "🎙️ ارزیابی لحن صوتی و پایش تنش (Gemini Sentiment)",
                    badgeText = "هارمونی: ${sentimentData.harmonyScore}% | تنش: ${sentimentData.tensionScore}%",
                    modifier = Modifier.testTag("report_sentiment_panel")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("لحن عمومی ثبت‌شده:", color = TextSub, fontSize = 10.sp)
                                Text(sentimentData.overallTone, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sentimentData.tensionScore > 30) AccentRose.copy(alpha = 0.15f) else AccentEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (sentimentData.tensionScore > 30) "⚠️ نیازمند بازبینی" else "تعامل پایدار ✅",
                                    color = if (sentimentData.tensionScore > 30) AccentRose else AccentEmeraldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Breakdown row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BgInput)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("سازنده: ${sentimentData.constructivePercentage}%", color = AccentEmeraldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("خنثی: ${sentimentData.neutralPercentage}%", color = AccentCyan, fontSize = 10.sp)
                            Text("اصطکاک: ${sentimentData.tensionPercentage}%", color = AccentRose, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        if (sentimentData.summaryEvaluation.isNotBlank()) {
                            Text(sentimentData.summaryEvaluation, color = TextSub, fontSize = 10.5.sp, lineHeight = 16.sp)
                        }

                        if (sentimentData.flaggedSegments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("بخش‌های دارای تنش شدید پرچم‌گذاری‌شده:", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp)

                            sentimentData.flaggedSegments.forEachIndexed { i, seg ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = BgInput),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth().testTag("report_flagged_segment_$i")
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("⏱️ ${seg.timestampRange} • ${seg.speaker}", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                            Text(
                                                text = if (seg.isReviewed) "بازبینی‌شده ✅" else "در انتظار بازبینی ⚠️",
                                                color = if (seg.isReviewed) AccentEmeraldLight else AccentRose,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text("علت: ${seg.triggerTopic}", color = TextMain, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                        if (seg.quoteExcerpt.isNotBlank()) {
                                            Text("نقل‌قول: ${seg.quoteExcerpt}", color = TextSub, fontSize = 10.sp)
                                        }
                                        if (seg.reviewRecommendation.isNotBlank()) {
                                            Text("توصیه: ${seg.reviewRecommendation}", color = AccentEmeraldLight, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Extracted Action Items Table ---
        val extractedItems = session.transcriptionAnalysis?.actionItems ?: emptyList()
        if (extractedItems.isNotEmpty()) {
            item {
                QuantumPanel(
                    title = "📋 تعهدات، تکالیف و اقدامات مصوب (${extractedItems.size} اقدام استخراج‌شده)",
                    badgeText = "هوش مصنوعی"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        extractedItems.forEachIndexed { idx, item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgInput),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth().testTag("report_action_item_$idx")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val priColor = when (item.priority) {
                                                "فوری" -> AccentRose
                                                "بالا" -> AccentAmber
                                                else -> AccentCyan
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(priColor.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(item.priority, color = priColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Text("مسئول: ${item.assignee}", color = AccentPurpleLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }

                                        Text("مهلت: ${item.deadline}", color = TextSub, fontSize = 10.5.sp)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(item.title, color = TextMain, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 1. Attendees ---
        item {
            QuantumPanel(title = "۱. فهرست اعضای حاضر در جلسه") {
                val presentMembers = session.members.filter { it.isPresent }
                if (presentMembers.isEmpty()) {
                    Text("هیچ عضوی به عنوان حاضر ثبت نشده است.", color = TextSub, fontSize = 11.5.sp)
                } else {
                    presentMembers.forEachIndexed { i, m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${i + 1}.", color = TextSub, fontSize = 11.sp)
                                Text(m.name, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("• ${m.title}", color = AccentPurpleLight, fontSize = 11.5.sp)
                            }
                            Text(m.unit, color = TextMuted, fontSize = 11.sp)
                        }
                        if (i < presentMembers.lastIndex) {
                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // --- 2. 5PL Cold-Chain & Financial Section ---
        if (session.opsData.enabled) {
            item {
                QuantumPanel(title = "📊 شاخص‌های عملیاتی لجستیک 5PL و زنجیره سرد") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("بهره‌وری ناوگان:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.opsData.fleetEff.ifBlank { "—" }, color = AccentEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("پایداری زنجیره سرد:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.opsData.coldChain, color = TextMain, fontSize = 11.5.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ظرفیت انبارش و مدل پالت:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.opsData.palletModel.ifBlank { "—" }, color = TextMain, fontSize = 11.5.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("بودجه / تعرفه مصوب:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.opsData.budget.ifBlank { "—" }, color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (session.opsData.summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("دستور اجرایی برج کنترل:", color = AccentEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            Text(session.opsData.summary, color = TextMain, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // --- 3. HR Committee Section ---
        if (session.hrData.enabled) {
            item {
                QuantumPanel(title = "🧬 نتیجه ارزیابی تخصصی کمیته منابع انسانی (HR)") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("نام کاندیدا:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.hrData.candidate.ifBlank { "—" }, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جایگاه شغلی:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.hrData.position.ifBlank { "—" }, color = AccentPurpleLight, fontSize = 11.5.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("امتیاز کل شایستگی:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.hrData.score.ifBlank { "—" }, color = AccentEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("رأی نهایی کمیته جذب:", color = TextMuted, fontSize = 11.5.sp)
                            Text(session.hrData.decision, color = AccentPurpleLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (session.hrData.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("جمع‌بندی کیفی ارزیابان:", color = TextMuted, fontSize = 11.sp)
                            Text(session.hrData.notes, color = TextMain, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // --- 4. Notes & Resolutions Table ---
        item {
            QuantumPanel(title = "۲. شرح مصوبات، تعهدات اجرایی و مذاکرات (${session.notes.size} بند)") {
                if (session.notes.isEmpty()) {
                    Text("هنوز بندی در جلسه ثبت نشده است.", color = TextSub, fontSize = 11.5.sp)
                } else {
                    session.notes.forEachIndexed { idx, note ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("بند ${idx + 1}.", color = TextSub, fontSize = 11.sp)
                                    Text(note.speaker, color = AccentPurpleLight, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    Text("• ${note.type}", color = TextMuted, fontSize = 11.sp)
                                }
                                Text(
                                    text = if (note.isDone) "انجام شد ✅" else "مهلت: ${note.due}",
                                    color = if (note.isDone) AccentEmeraldLight else AccentAmber,
                                    fontSize = 10.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(note.text, color = TextMain, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        if (idx < session.notes.lastIndex) {
                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // --- 5. Attachments ---
        val validAttach = session.attachments.filter { it.name.isNotBlank() }
        if (validAttach.isNotEmpty()) {
            item {
                QuantumPanel(title = "۳. فهرست اسناد و مستندات پیوست (${validAttach.size} سند)") {
                    validAttach.forEachIndexed { i, a ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("📎 ${a.name}", color = TextMain, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text(a.refCode.ifBlank { "—" }, color = AccentCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- 6. Official Signature Blocks & Digital Signature Placeholders ---
        item {
            QuantumPanel(
                title = "🔏 تأییدیه، مهر سازمانی و جایگاه امضای دیجیتال اعضای جلسه",
                badgeText = "PKI Digital Signature Placeholders"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val sigs = corporateConfig.signatories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SignatureBox(
                            title = sigs.getOrNull(0)?.roleTitle ?: "مدیر لجستیک و عملیات",
                            signerName = sigs.getOrNull(0)?.signerName ?: "سخنران",
                            certificateSerial = sigs.getOrNull(0)?.certificateSerial ?: "PKI-ATS-2026-RN01",
                            hashPlaceholder = sigs.getOrNull(0)?.signatureHashPlaceholder ?: "SHA256: 9A4E71B2... [PLACEHOLDER]",
                            stampLabel = sigs.getOrNull(0)?.stampLabel ?: "امضای دیجیتال تنظیم‌کننده",
                            modifier = Modifier.weight(1f)
                        )
                        SignatureBox(
                            title = sigs.getOrNull(1)?.roleTitle ?: "مدیرعامل",
                            signerName = sigs.getOrNull(1)?.signerName ?: "مدیرعامل",
                            certificateSerial = sigs.getOrNull(1)?.certificateSerial ?: "PKI-ATS-2026-CEO2",
                            hashPlaceholder = sigs.getOrNull(1)?.signatureHashPlaceholder ?: "SHA256: 4C1D88F0... [PLACEHOLDER]",
                            stampLabel = sigs.getOrNull(1)?.stampLabel ?: "محل امضای دیجیتال و مهر مدیرعامل",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SignatureBox(
                        title = sigs.getOrNull(2)?.roleTitle ?: "رئیس هیئت‌مدیره / نماینده طرف مقابل",
                        signerName = sigs.getOrNull(2)?.signerName ?: "رئیس هیئت‌مدیره",
                        certificateSerial = sigs.getOrNull(2)?.certificateSerial ?: "PKI-ATS-2026-BRD3",
                        hashPlaceholder = sigs.getOrNull(2)?.signatureHashPlaceholder ?: "SHA256: 7B3F09A4... [PLACEHOLDER]",
                        stampLabel = sigs.getOrNull(2)?.stampLabel ?: "محل امضای دیجیتال و مهر رسمی شرکت",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SignatureBox(
    title: String,
    signerName: String,
    certificateSerial: String = "PKI-ATS-2026-8841",
    hashPlaceholder: String = "SHA256: 8F4B2E91... [DIGITAL-SIG-PLACEHOLDER]",
    stampLabel: String = "محل مهر و امضای دیجیتال سازمانی",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgInput)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = signerName,
                color = TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentPurple.copy(alpha = 0.1f))
                    .border(1.dp, AccentPurpleLight.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "✍️ [ DIGITAL SIGNATURE PLACEHOLDER ]",
                        color = AccentCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "💮 $stampLabel",
                        color = AccentGold,
                        fontSize = 8.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cert: $certificateSerial • $hashPlaceholder",
                        color = AccentEmeraldLight,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
