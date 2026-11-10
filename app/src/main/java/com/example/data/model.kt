package com.example.data

import java.util.UUID

data class Member(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val title: String = "",
    val unit: String = "",
    val isPresent: Boolean = true
)

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val refCode: String = ""
)

data class MeetingNote(
    val id: String = UUID.randomUUID().toString(),
    val timeFormatted: String = "00:00:00",
    val speaker: String = "دبیر جلسه",
    val text: String = "",
    val type: String = "یادداشت",
    val due: String = "فوری / جاری",
    val isDone: Boolean = false
)

data class OpsData(
    val enabled: Boolean = false,
    val fleetEff: String = "",
    val coldChain: String = "",
    val palletModel: String = "",
    val budget: String = "",
    val summary: String = ""
)

data class HrData(
    val enabled: Boolean = false,
    val candidate: String = "",
    val position: String = "",
    val score: String = "",
    val decision: String = "",
    val notes: String = ""
)

data class TranscriptionSegment(
    val id: String = UUID.randomUUID().toString(),
    val timeFormatted: String = "00:00:00",
    val speaker: String = "دبیر جلسه",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isCommittedToNotes: Boolean = false
)

data class ExtractedActionItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val assignee: String = "دبیر جلسه",
    val deadline: String = "فوری / جاری",
    val priority: String = "بالا",
    val isAddedToNotes: Boolean = false
)

data class TranscriptionAiAnalysis(
    val executiveSummary: String = "",
    val keyDecisions: List<String> = emptyList(),
    val actionItems: List<ExtractedActionItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class HighTensionSegment(
    val id: String = UUID.randomUUID().toString(),
    val timestampRange: String = "00:00 - 01:00",
    val speaker: String = "دبیر جلسه",
    val tensionLevel: String = "شدید", // شدید, متوسط, بحرانی
    val triggerTopic: String = "موضوع چالش‌برانگیز",
    val quoteExcerpt: String = "",
    val reviewRecommendation: String = "",
    val isReviewed: Boolean = false,
    val reviewNotes: String = ""
)

data class AudioSentimentAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val overallTone: String = "حرفه‌ای و سازنده",
    val harmonyScore: Int = 82, // 0..100
    val tensionScore: Int = 22, // 0..100
    val constructivePercentage: Int = 70,
    val neutralPercentage: Int = 20,
    val tensionPercentage: Int = 10,
    val dominantEmotion: String = "جدیت در مذاکره و تصمیم‌گیری",
    val summaryEvaluation: String = "",
    val flaggedSegments: List<HighTensionSegment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class AudioMeetingAnalysis(
    val fullTranscription: String = "",
    val executiveSummary: String = "",
    val keyDecisions: List<String> = emptyList(),
    val actionItems: List<ExtractedActionItem> = emptyList(),
    val sentimentAnalysis: AudioSentimentAnalysis? = null,
    val audioDurationSec: Long = 0L,
    val audioFileName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class OfficialMinutesDraft(
    val documentNumber: String = "DRF-" + System.currentTimeMillis().toString().takeLast(6),
    val draftTitle: String = "",
    val templateStyle: String = "الگوی رسمی هیئت‌مدیره و حاکمیت شرکتی",
    val formalPreamble: String = "",
    val keyPointsSummary: List<String> = emptyList(),
    val formalResolutions: List<String> = emptyList(),
    val assignedCommitments: List<ExtractedActionItem> = emptyList(),
    val closingClause: String = "",
    val generatedPdfPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DigitalSignaturePlaceholder(
    val id: String = UUID.randomUUID().toString(),
    val roleTitle: String = "",
    val signerName: String = "",
    val department: String = "",
    val certificateSerial: String = "PKI-IR-2026-8841",
    val signatureHashPlaceholder: String = "SHA256: 8F4B2E91...C71A [DIGITAL-SIG-PLACEHOLDER]",
    val stampLabel: String = "محل مهر و امضای دیجیتال سازمانی",
    val statusBadge: String = "جایگاه امضای دیجیتال (Placeholder)",
    val isSignedPlaceholder: Boolean = true
)

data class CorporatePdfConfig(
    val companyNameFa: String = "هلدینگ لجستیک هوشمند و زنجیره سرد اطلس (سهامی خاص)",
    val companyNameEn: String = "ATLAS SMART 5PL & COLD-CHAIN ENTERPRISE GROUP",
    val registrationInfo: String = "شماره ثبت: ۵۸۴۹۲۰ • شناسه ملی: ۱۴۰۱۰۹۵۸۴۲۰ • گواهی استاندارد: ISO 9001 / WHO-GDP",
    val documentNumber: String = "ATS-FIN-1405-0815",
    val attachmentLabel: String = "دارد (پیوست مصوبات و مستندات)",
    val classificationLevel: String = "محرمانه / ویژه اعضای هیئت‌مدیره",
    val includeCorporateLetterhead: Boolean = true,
    val includeDigitalSignaturePlaceholders: Boolean = true,
    val includeOfficialStampPlaceholder: Boolean = true,
    val includeSecurityWatermark: Boolean = true,
    val signatories: List<DigitalSignaturePlaceholder> = listOf(
        DigitalSignaturePlaceholder(
            roleTitle = "مدیر لجستیک و عملیات (دبیر شورا)",
            signerName = "سخنران جلسه",
            department = "لجستیک و زنجیره تأمین",
            certificateSerial = "PKI-ATS-2026-RN01",
            signatureHashPlaceholder = "SHA256: 9A4E71B2...3D90 [PLACEHOLDER]",
            stampLabel = "امضای دیجیتال تنظیم‌کننده"
        ),
        DigitalSignaturePlaceholder(
            roleTitle = "مدیرعامل و نایب‌رئیس هیئت‌مدیره",
            signerName = "مدیرعامل",
            department = "مدیریت ارشد اجرایی",
            certificateSerial = "PKI-ATS-2026-CEO2",
            signatureHashPlaceholder = "SHA256: 4C1D88F0...7E12 [PLACEHOLDER]",
            stampLabel = "محل امضای دیجیتال و مهر مدیرعامل"
        ),
        DigitalSignaturePlaceholder(
            roleTitle = "رئیس هیئت‌مدیره / مقام تصویب‌کننده",
            signerName = "رئیس هیئت‌مدیره",
            department = "حاکمیت شرکتی",
            certificateSerial = "PKI-ATS-2026-BRD3",
            signatureHashPlaceholder = "SHA256: 7B3F09A4...1C65 [PLACEHOLDER]",
            stampLabel = "محل امضای دیجیتال و مهر رسمی شرکت"
        )
    ),
    val lastGeneratedPdfPath: String? = null
)

data class PersonAssignedTask(
    val id: String = UUID.randomUUID().toString(),
    val personName: String = "سخنران جلسه",
    val personRole: String = "مدیر عملیات",
    val taskTitle: String = "",
    val deadline: String = "فوری / جاری",
    val priority: String = "بالا",
    val sourceExcerpt: String = "",
    val isSyncedToRoom: Boolean = true
)

data class TypedMeetingMinutesAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val sourceTypedText: String = "",
    val formalMinutesText: String = "",
    val decisionsMade: List<String> = emptyList(),
    val assignedTasksByPerson: List<PersonAssignedTask> = emptyList(),
    val autoAnalyzed: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class MeetingSession(
    val id: String = "SES-" + System.currentTimeMillis(),
    val title: String = "",
    val scope: String = "internal", // internal, external_partner, external_supplier, external_contract, interview_hr
    val extCompany: String = "",
    val extRef: String = "",
    val durationMins: Int = 60,
    val location: String = "",
    val calendarType: String = "jalali", // jalali, gregorian
    val date: String = "",
    val startTime: String = "10:00",
    val members: List<Member> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val notes: List<MeetingNote> = emptyList(),
    val transcriptions: List<TranscriptionSegment> = emptyList(),
    val transcriptionAnalysis: TranscriptionAiAnalysis? = null,
    val typedMinutesAnalysis: TypedMeetingMinutesAnalysis? = null,
    val latestAudioAnalysis: AudioMeetingAnalysis? = null,
    val audioSentimentAnalysis: AudioSentimentAnalysis? = null,
    val officialMinutesDraft: OfficialMinutesDraft? = null,
    val isFinalized: Boolean = true,
    val corporatePdfConfig: CorporatePdfConfig = CorporatePdfConfig(),
    val aiSummary: String = "",
    val opsData: OpsData = OpsData(),
    val hrData: HrData = HrData(),
    val elapsedSeconds: Long = 0,
    val audioFilePath: String? = null,
    val audioDurationSec: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ResolutionStatusType(val label: String, val code: String) {
    IN_PROGRESS("در حال انجام", "IN_PROGRESS"),
    COMPLETED("تکمیل شده", "COMPLETED"),
    DEFERRED("معوق", "DEFERRED")
}

fun MeetingNote.resolveStatus(): ResolutionStatusType {
    return when {
        isDone -> ResolutionStatusType.COMPLETED
        due.contains("معوق") || due.contains("تاخیر") || due.contains("تعویق") || text.contains("معوق") -> ResolutionStatusType.DEFERRED
        else -> ResolutionStatusType.IN_PROGRESS
    }
}

data class AgendaSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val topicTitle: String,
    val rationalReason: String,
    val estimatedDurationMins: Int = 20,
    val proposedSpeaker: String = "سخنران جلسه",
    val suggestedActionType: String = "🚨 مصوبه رسمی",
    val isRecurringObjective: Boolean = false
)

data class RecurringBlocker(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val severity: String = "متوسط", // بحرانی, بالا, متوسط, پایین
    val frequencyCount: Int = 2,
    val impactedAreas: String = "لجستیک و بودجه",
    val mitigationStrategy: String = ""
)

data class MeetingHealthReport(
    val healthScore: Int = 0, // 0..100
    val gradeLabel: String = "",
    val followThroughRatePercent: Int = 0,
    val decisionVelocity: String = "",
    val recurringBlockers: List<RecurringBlocker> = emptyList(),
    val efficiencyRecommendations: List<String> = emptyList(),
    val summaryInsight: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AiArchiveMatchedSession(
    val sessionId: String = "",
    val sessionTitle: String = "",
    val sessionDate: String = "",
    val relevanceScore: Int = 0,
    val matchedSnippet: String = "",
    val matchedResolutions: List<String> = emptyList()
)

data class AiArchiveSearchResult(
    val query: String = "",
    val aiAnswer: String = "",
    val matchedSessions: List<AiArchiveMatchedSession> = emptyList(),
    val relatedTopics: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)


