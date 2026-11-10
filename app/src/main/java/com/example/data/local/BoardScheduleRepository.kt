package com.example.data.local

import com.example.data.MeetingSession
import com.example.data.PredefinedScenarios
import kotlinx.coroutines.flow.Flow

/**
 * Repository managing Room persistence and reactive queries for the Board Calendar View
 * (نمای تقویمی جلسات پیش‌رو و سوابق هیئت‌مدیره بر اساس داده‌های دیتابیس Room).
 */
class BoardScheduleRepository(
    private val scheduleDao: BoardScheduleDao,
    private val minutesSearchRepository: MinutesSearchRepository? = null
) {

    val allScheduleItemsFlow: Flow<List<BoardScheduleEntity>> =
        scheduleDao.observeAllScheduleItems()

    fun observeUpcomingMeetings(referenceDate: String = DEFAULT_REFERENCE_DATE): Flow<List<BoardScheduleEntity>> =
        scheduleDao.observeUpcomingMeetings(referenceDate)

    fun observeHistoricalMeetings(referenceDate: String = DEFAULT_REFERENCE_DATE): Flow<List<BoardScheduleEntity>> =
        scheduleDao.observeHistoricalMeetings(referenceDate)

    fun observeMeetingsForDate(date: String): Flow<List<BoardScheduleEntity>> =
        scheduleDao.observeMeetingsForDate(BoardScheduleEntity.normalizeDate(date))

    fun observeMeetingsByMember(memberName: String): Flow<List<BoardScheduleEntity>> =
        scheduleDao.observeMeetingsByMember(memberName)

    suspend fun getAllScheduleItemsOnce(): List<BoardScheduleEntity> =
        scheduleDao.getAllScheduleItemsOnce()

    suspend fun getUpcomingMeetingsOnce(referenceDate: String = DEFAULT_REFERENCE_DATE): List<BoardScheduleEntity> =
        scheduleDao.getUpcomingMeetingsOnce(referenceDate)

    suspend fun getHistoricalMeetingsOnce(referenceDate: String = DEFAULT_REFERENCE_DATE): List<BoardScheduleEntity> =
        scheduleDao.getHistoricalMeetingsOnce(referenceDate)

    suspend fun getMeetingsForDateOnce(date: String): List<BoardScheduleEntity> =
        scheduleDao.getMeetingsForDateOnce(BoardScheduleEntity.normalizeDate(date))

    /**
     * Upserts a [MeetingSession] into the Room `board_schedule` table.
     */
    suspend fun upsertSessionSchedule(
        session: MeetingSession,
        referenceDate: String = DEFAULT_REFERENCE_DATE,
        explicitStatus: String? = null
    ): BoardScheduleEntity {
        val existing = scheduleDao.getScheduleBySessionId(session.id)
        val entity = BoardScheduleEntity.fromMeetingSession(
            session = session,
            referenceDate = referenceDate,
            explicitStatus = explicitStatus ?: existing?.scheduleStatus,
            existingId = existing?.id ?: 0
        )
        val rowId = scheduleDao.insertScheduleItem(entity)
        return entity.copy(id = if (existing != null) existing.id else rowId.toInt())
    }

    /**
     * Schedules a new upcoming board meeting directly into the Room database
     * and synchronizes it with the `meeting_minutes` table.
     */
    suspend fun scheduleUpcomingMeeting(
        title: String,
        date: String,
        startTime: String = "10:00",
        durationMins: Int = 90,
        location: String = "سالن کنفرانس مرکزی هیئت‌مدیره",
        scope: String = "internal",
        extCompany: String = "",
        chairpersonName: String = "سخنران",
        attendeesCsv: String = "سخنران | سخنران | سخنران | سخنران",
        agendaSummary: String = "",
        keyAgendaItems: List<String> = emptyList()
    ): BoardScheduleEntity {
        val normalizedDate = BoardScheduleEntity.normalizeDate(date, "1405/08/22")
        val sessionId = "SES-UPCOMING-" + System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { "جلسه پیش‌رو هیئت‌مدیره ($normalizedDate)" }
        val cleanAgenda = agendaSummary.trim().ifBlank {
            "بررسی دستور کار راهبردی هیئت‌مدیره، پایش شاخص‌های کلیدی عملکرد (KPI) و پیگیری مصوبات قبلی."
        }
        val keyItemsSummary = if (keyAgendaItems.isNotEmpty()) {
            keyAgendaItems.joinToString("\n")
        } else {
            "۱. بررسی گزارش عملکرد عملیات و زنجیره سرد (${chairpersonName})\n۲. پایش بودجه و تعهدات مالی فصل جاری\n۳. تصویب برنامه عملیاتی دوره آتی"
        }

        val entity = BoardScheduleEntity(
            sessionId = sessionId,
            title = cleanTitle,
            date = normalizedDate,
            startTime = startTime.ifBlank { "10:00" },
            durationMins = durationMins.coerceAtLeast(15),
            location = location.ifBlank { "سالن کنفرانس مرکزی هیئت‌مدیره" },
            scope = scope.ifBlank { "internal" },
            extCompany = extCompany,
            scheduleStatus = BoardScheduleEntity.STATUS_UPCOMING,
            chairpersonName = chairpersonName.ifBlank { "سخنران" },
            attendeesCsv = attendeesCsv.ifBlank { "سخنران | سخنران | سخنران | سخنران" },
            agendaSummary = cleanAgenda,
            keyResolutionsSummary = keyItemsSummary,
            totalResolutionsCount = keyAgendaItems.size.coerceAtLeast(3),
            completedResolutionsCount = 0,
            pendingResolutionsCount = keyAgendaItems.size.coerceAtLeast(3),
            isFinalized = false
        )
        val fullSession = entity.toMeetingSession()
        val withJson = entity.copy(
            fullSessionJson = com.example.data.MeetingRepository.sessionToJson(fullSession).toString()
        )
        val rowId = scheduleDao.insertScheduleItem(withJson)
        val saved = withJson.copy(id = rowId.toInt())

        minutesSearchRepository?.upsertSessionMinutes(fullSession)
        return saved
    }

    /**
     * Toggles or updates the status of a scheduled meeting between UPCOMING and COMPLETED (historical record).
     */
    suspend fun updateMeetingScheduleStatus(
        sessionId: String,
        newStatus: String
    ): BoardScheduleEntity? {
        val existing = scheduleDao.getScheduleBySessionId(sessionId) ?: return null
        val updated = existing.copy(
            scheduleStatus = newStatus,
            completedResolutionsCount = if (newStatus == BoardScheduleEntity.STATUS_COMPLETED && existing.completedResolutionsCount == 0) {
                existing.totalResolutionsCount.coerceAtLeast(1)
            } else {
                existing.completedResolutionsCount
            },
            pendingResolutionsCount = if (newStatus == BoardScheduleEntity.STATUS_COMPLETED) {
                0
            } else {
                existing.totalResolutionsCount
            },
            isFinalized = newStatus == BoardScheduleEntity.STATUS_COMPLETED,
            updatedAt = System.currentTimeMillis()
        )
        scheduleDao.insertScheduleItem(updated)
        return updated
    }

    suspend fun deleteScheduleBySessionId(sessionId: String) {
        scheduleDao.deleteBySessionId(sessionId)
    }

    /**
     * Synchronizes active and archived sessions into the Room `board_schedule` table
     * and seeds default historical records and upcoming board meetings so board members
     * immediately have a complete past-and-future schedule in the calendar view.
     */
    suspend fun syncAndSeedBoardSchedule(
        activeSession: MeetingSession,
        archiveList: List<MeetingSession>,
        referenceDate: String = DEFAULT_REFERENCE_DATE
    ) {
        val existingAll = scheduleDao.getAllScheduleItemsOnce()
        val existingById = existingAll.associateBy { it.sessionId }

        // 1. Seed default historical records & upcoming board meetings if not yet seeded
        val defaultSeedItems = buildDefaultBoardScheduleSeed()
        for (seed in defaultSeedItems) {
            if (!existingById.containsKey(seed.sessionId)) {
                scheduleDao.insertScheduleItem(seed)
            }
        }

        // 2. Sync predefined scenarios (historical on <= 1405/08/15, upcoming on > 1405/08/15)
        listOf(
            PredefinedScenarios.getScenario("board_cold_chain"),
            PredefinedScenarios.getScenario("hr_interview"),
            PredefinedScenarios.getScenario("supplier_contract")
        ).forEach { scenario ->
            val prev = scheduleDao.getScheduleBySessionId(scenario.id)
            val normalized = BoardScheduleEntity.normalizeDate(scenario.date, referenceDate)
            val status = prev?.scheduleStatus ?: if (normalized <= referenceDate) {
                BoardScheduleEntity.STATUS_COMPLETED
            } else {
                BoardScheduleEntity.STATUS_UPCOMING
            }
            val entity = BoardScheduleEntity.fromMeetingSession(
                session = scenario,
                referenceDate = referenceDate,
                explicitStatus = status,
                existingId = prev?.id ?: 0
            )
            scheduleDao.insertScheduleItem(entity)
        }

        // 3. Sync archived sessions
        for (archived in archiveList) {
            val prev = scheduleDao.getScheduleBySessionId(archived.id)
            val normalized = BoardScheduleEntity.normalizeDate(archived.date, referenceDate)
            val status = prev?.scheduleStatus ?: if (normalized > referenceDate) {
                BoardScheduleEntity.STATUS_UPCOMING
            } else {
                BoardScheduleEntity.STATUS_COMPLETED
            }
            val entity = BoardScheduleEntity.fromMeetingSession(
                session = archived,
                referenceDate = referenceDate,
                explicitStatus = status,
                existingId = prev?.id ?: 0
            )
            scheduleDao.insertScheduleItem(entity)
        }

        // 4. Sync active session if it has content
        if (activeSession.title.isNotBlank() || activeSession.notes.isNotEmpty() || activeSession.aiSummary.isNotBlank()) {
            val prev = scheduleDao.getScheduleBySessionId(activeSession.id)
            val normalized = BoardScheduleEntity.normalizeDate(activeSession.date, referenceDate)
            val status = prev?.scheduleStatus ?: if (normalized > referenceDate) {
                BoardScheduleEntity.STATUS_UPCOMING
            } else {
                BoardScheduleEntity.STATUS_COMPLETED
            }
            val entity = BoardScheduleEntity.fromMeetingSession(
                session = activeSession,
                referenceDate = referenceDate,
                explicitStatus = status,
                existingId = prev?.id ?: 0
            )
            scheduleDao.insertScheduleItem(entity)
        }
    }

    companion object {
        const val DEFAULT_REFERENCE_DATE = "1405/08/15"

        fun buildDefaultBoardScheduleSeed(): List<BoardScheduleEntity> {
            return listOf(
                // --- Historical Board Meetings (سوابق جلسات برگزارشده در دیتابیس Room) ---
                BoardScheduleEntity(
                    sessionId = "SES-HIST-1405-08-03",
                    title = "جلسه ممیزی استانداردهای GDP و کنترل کیفیت سردخانه‌های مرکزی",
                    date = "1405/08/03",
                    startTime = "09:30",
                    durationMins = 105,
                    location = "سالن کنفرانس مرکزی هیئت‌مدیره",
                    scope = "internal",
                    extCompany = "واحد تضمین کیفیت اطلس",
                    scheduleStatus = BoardScheduleEntity.STATUS_COMPLETED,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | سخنران | سخنران | سارا احمدی",
                    agendaSummary = "ممیزی کامل پایش پیوسته دمایی انبارهای زنجیره سرد دارویی، تأیید کالیبراسیون ۱۲۰ دیتالاگر و تصویب دستورالعمل واکنش سریع به انحراف دما.",
                    keyResolutionsSummary = "سخنران: نصب سنسورهای پشتیبان در سردخانه شماره ۲ (انجام‌شده)\nسارا احمدی: تدوین گزارش انطباق WHO-GDP برای بازرسی وزارت بهداشت (انجام‌شده)\nسخنران: ابلاغ پروتکل جدید قرنطینه دارویی (انجام‌شده)",
                    totalResolutionsCount = 3,
                    completedResolutionsCount = 3,
                    pendingResolutionsCount = 0,
                    isFinalized = true
                ),
                BoardScheduleEntity(
                    sessionId = "SES-HIST-1405-08-09",
                    title = "نشست بررسی عملکرد مالی سه‌ماهه و بودجه نوسازی ناوگان 5PL",
                    date = "1405/08/09",
                    startTime = "11:00",
                    durationMins = 90,
                    location = "اتاق شورا طبقه هشتم",
                    scope = "internal",
                    extCompany = "معاونت مالی و اقتصادی",
                    scheduleStatus = BoardScheduleEntity.STATUS_COMPLETED,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | سخنران | سخنران | سخنران",
                    agendaSummary = "بررسی صورت‌های مالی میان‌دوره‌ای، تحلیل بهای تمام‌شده حمل زنجیره سرد و تصویب ردیف اعتباری خرید کشنده‌های یخچال‌دار جدید.",
                    keyResolutionsSummary = "سخنران: ارائه جدول جریان نقدینگی خرید ۶ دستگاه کامیونت یخچالی (انجام‌شده)\nسخنران: ارزیابی فنی بهره‌وری سوخت و مسیرهای توزیع استانی (انجام‌شده)\nسخنران: مذاکره نهایی با بانک عامل جهت تسهیلات نوسازی ناوگان (در جریان)",
                    totalResolutionsCount = 3,
                    completedResolutionsCount = 2,
                    pendingResolutionsCount = 1,
                    isFinalized = true
                ),
                BoardScheduleEntity(
                    sessionId = "SES-HOLDING-1405-08-16",
                    title = "هماهنگی زنجیره سرد و انطباق با استانداردهای TTAC",
                    date = "1405/08/16",
                    startTime = "10:30",
                    durationMins = 90,
                    location = "سالن کنفرانس مرکزی هلدینگ",
                    scope = "external_partner",
                    extCompany = "چابکدارو و سازمان غذا و دارو",
                    scheduleStatus = BoardScheduleEntity.STATUS_UPCOMING,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | دارو | سخنران | سارا احمدی",
                    agendaSummary = "مدیریت هماهنگی میان چابکدارو، چاپار و زیرساخت 5PL جهت پایش زنجیره سرد و انطباق کامل با استانداردهای رهگیری TTAC.",
                    keyResolutionsSummary = "۱. یکپارچه‌سازی وب‌سرویس استعلام اصالت دارو (TTAC) با برج کنترل لجستیک\n۲. پایش لحظه‌ای دمای ناوگان چابکدارو تحت نظارت سخنران",
                    totalResolutionsCount = 2,
                    completedResolutionsCount = 1,
                    pendingResolutionsCount = 1,
                    isFinalized = false
                ),
                BoardScheduleEntity(
                    sessionId = "SES-HOLDING-1405-08-17",
                    title = "بررسی معماری Multi-Tenant و سناریوهای استقرار 5PL",
                    date = "1405/08/17",
                    startTime = "14:00",
                    durationMins = 120,
                    location = "مرکز فرماندهی عملیات هلدینگ اطلس",
                    scope = "internal",
                    extCompany = "هلدینگ چابکدارو و چاپار",
                    scheduleStatus = BoardScheduleEntity.STATUS_COMPLETED,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | مدیران | فنی | سخنران",
                    agendaSummary = "بررسی معماری چندمستأجری (Multi-Tenant) سامانه لجستیک 5PL و سناریوهای استقرار یکپارچه میان شرکت‌های تابعه هلدینگ.",
                    keyResolutionsSummary = "۱. تایید معماری ایزولاسیون داده‌های شرکت‌های تابعه در هسته 5PL\n۲. استقرار پایلوت سامانه در هاب مرکزی با راهبری سخنران",
                    totalResolutionsCount = 2,
                    completedResolutionsCount = 2,
                    pendingResolutionsCount = 0,
                    isFinalized = true
                ),
                // --- Upcoming Scheduled Board Meetings (جلسات پیش‌رو هیئت‌مدیره در دیتابیس Room) ---
                BoardScheduleEntity(
                    sessionId = "SES-UPCOMING-1405-08-21",
                    title = "جلسه پیش‌رو هیئت‌مدیره: تصویب فاز دوم برج کنترل هوشمند و هوش مصنوعی لجستیک",
                    date = "1405/08/21",
                    startTime = "10:00",
                    durationMins = 120,
                    location = "سالن کنفرانس مرکزی هیئت‌مدیره",
                    scope = "internal",
                    extCompany = "مرکز فناوری و عملیات اطلس",
                    scheduleStatus = BoardScheduleEntity.STATUS_UPCOMING,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | سخنران | سخنران | سخنران | علی رضایی",
                    agendaSummary = "ارائه داشبورد پیش‌بینی تقاضا مبتنی بر هوش مصنوعی، بررسی گزارش تست گرم ناوگان زنجیره سرد و تصویب بودجه توسعه سامانه تله‌متری ماهواره‌ای.",
                    keyResolutionsSummary = "۱. ارائه گزارش فنی فاز دوم برج کنترل توسط سخنران\n۲. بررسی و تصویب سقف بودجه زیرساخت هوش مصنوعی توسط سخنران و سخنران\n۳. تعیین شاخص‌های SLA توزیع واکسن و داروهای بیولوژیک در نیمسال دوم",
                    totalResolutionsCount = 3,
                    completedResolutionsCount = 0,
                    pendingResolutionsCount = 3,
                    isFinalized = false
                ),
                BoardScheduleEntity(
                    sessionId = "SES-UPCOMING-1405-08-26",
                    title = "کمیته مشترک حسابرسی، ریسک و انطباق زنجیره تأمین دارویی (TTAC)",
                    date = "1405/08/26",
                    startTime = "13:30",
                    durationMins = 90,
                    location = "سالن جلسات حاکمیت شرکتی",
                    scope = "external_partner",
                    extCompany = "سازمان غذا و دارو / شرکای توزیع",
                    scheduleStatus = BoardScheduleEntity.STATUS_UPCOMING,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | سخنران | سارا احمدی | سخنران",
                    agendaSummary = "پایش ریسک‌های عملیاتی زنجیره سرد در فصل سرما، بررسی یکپارچگی سامانه رهگیری اصالت دارو (TTAC) و ارزیابی عملکرد پیمانکاران حمل.",
                    keyResolutionsSummary = "۱. مرور چک‌لیست ممیزی زمستانی ناوگان توسط سخنران و سارا احمدی\n۲. بررسی گزارش تطبیق سریال‌های رهگیری TTAC\n۳. تصمیم‌گیری در خصوص تمدید قرارداد پیمانکاران لجستیک منطقه‌ای",
                    totalResolutionsCount = 3,
                    completedResolutionsCount = 0,
                    pendingResolutionsCount = 3,
                    isFinalized = false
                ),
                BoardScheduleEntity(
                    sessionId = "SES-UPCOMING-1405-09-04",
                    title = "مجمع راهبردی هیئت‌مدیره: برنامه‌ریزی بودجه سالانه و توسعه هاب‌های منطقه‌ای",
                    date = "1405/09/04",
                    startTime = "09:00",
                    durationMins = 150,
                    location = "سالن کنفرانس مرکزی هیئت‌مدیره",
                    scope = "internal",
                    extCompany = "هلدینگ لجستیک هوشمند اطلس",
                    scheduleStatus = BoardScheduleEntity.STATUS_UPCOMING,
                    chairpersonName = "سخنران",
                    attendeesCsv = "سخنران | سخنران | سخنران | سخنران | سارا احمدی",
                    agendaSummary = "تدوین نقشه راه توسعه مراکز توزیع سردخانه‌ای در ۵ کلان‌شهر، بررسی اهداف رشد درآمدی و تصویب ساختار سازمانی جدید عملیات.",
                    keyResolutionsSummary = "۱. ارائه طرح توجیهی احداث هاب سردخانه‌ای شرق و جنوب کشور (سخنران)\n۲. بررسی لایحه بودجه سرمایه‌ای (CAPEX) سال آینده (سخنران)\n۳. تصویب نهایی شاخص‌های پاداش عملکرد مدیریتی هیئت‌مدیره",
                    totalResolutionsCount = 3,
                    completedResolutionsCount = 0,
                    pendingResolutionsCount = 3,
                    isFinalized = false
                )
            )
        }
    }
}
