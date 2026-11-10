package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeetingSession
import com.example.data.local.BoardScheduleEntity
import com.example.data.local.BoardScheduleRepository
import com.example.data.local.ExecutiveSummaryEntity
import com.example.data.local.MemberTaskEntity
import com.example.ui.theme.*

/**
 * Room-backed Board Calendar View (`نمای تقویمی جلسات پیش‌رو و سوابق هیئت‌مدیره بر اساس دیتابیس Room`).
 * Displays:
 * 1. Interactive monthly Jalali calendar grid with visual distinction between
 *    Upcoming Board Meetings (جلسات پیش‌رو), Historical Held Meetings (سوابق جلسات برگزارشده),
 *    and Member Task Deadlines (مهلت وظایف محوله اعضا).
 * 2. Board member schedule filter & mode filter (همه / جلسات پیش‌رو / سوابق برگزارشده).
 * 3. Quick inline scheduler to register new upcoming board meetings directly in Room DB.
 * 4. Selected date inspection sheet + chronological Upcoming & Historical timelines powered by Room.
 */
@Composable
fun MeetingCalendarViewComponent(
    sessions: List<MeetingSession>,
    activeSession: MeetingSession,
    onLoadSession: (MeetingSession) -> Unit,
    memberTasks: List<MemberTaskEntity> = emptyList(),
    onToggleMemberTaskDone: (MemberTaskEntity) -> Unit = {},
    roomScheduleItems: List<BoardScheduleEntity> = emptyList(),
    roomExecutiveSummaries: List<ExecutiveSummaryEntity> = emptyList(),
    referenceDate: String = BoardScheduleRepository.DEFAULT_REFERENCE_DATE,
    onScheduleUpcomingMeeting: ((
        title: String,
        date: String,
        startTime: String,
        durationMins: Int,
        location: String,
        chairperson: String,
        agendaSummary: String
    ) -> Unit)? = null,
    onToggleScheduleStatus: ((BoardScheduleEntity) -> Unit)? = null,
    onDeleteScheduleItem: ((String) -> Unit)? = null,
    onLoadBoardScheduleItem: ((BoardScheduleEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Merge Room board_schedule records with any in-memory sessions not yet in the list
    val unifiedScheduleItems = remember(roomScheduleItems, sessions, activeSession, referenceDate) {
        val mapById = linkedMapOf<String, BoardScheduleEntity>()

        // 1. Primary source: Room board_schedule table
        roomScheduleItems.forEach { entity ->
            mapById[entity.sessionId] = entity
        }

        // 2. Fallback / supplement from activeSession & archive sessions if not already present
        if (mapById.isEmpty()) {
            BoardScheduleRepository.buildDefaultBoardScheduleSeed().forEach { seed ->
                mapById[seed.sessionId] = seed
            }
        }
        sessions.forEach { s ->
            if (!mapById.containsKey(s.id)) {
                mapById[s.id] = BoardScheduleEntity.fromMeetingSession(s, referenceDate)
            }
        }
        if (activeSession.title.isNotBlank() || activeSession.notes.isNotEmpty()) {
            if (!mapById.containsKey(activeSession.id)) {
                mapById[activeSession.id] = BoardScheduleEntity.fromMeetingSession(activeSession, referenceDate)
            }
        }

        mapById.values.sortedWith(compareBy<BoardScheduleEntity> { it.date }.thenBy { it.startTime })
    }

    // Map Room Executive Summaries by sessionId for enriched historical records
    val executiveSummaryBySessionId = remember(roomExecutiveSummaries) {
        roomExecutiveSummaries.groupBy { it.sessionId }.mapValues { it.value.first() }
    }

    // Filter states: "ALL", "UPCOMING", "HISTORY"
    var scheduleFilterMode by remember { mutableStateOf("ALL") }
    var selectedBoardMemberFilter by remember { mutableStateOf("ALL") }

    // Inline form state for scheduling a new upcoming board meeting into Room
    var showScheduleForm by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf("1405/08/24") }
    var newStartTime by remember { mutableStateOf("10:30") }
    var newDurationMins by remember { mutableStateOf("90") }
    var newLocation by remember { mutableStateOf("سالن کنفرانس مرکزی هیئت‌مدیره") }
    var newChairperson by remember { mutableStateOf("سخنران") }
    var newAgenda by remember { mutableStateOf("") }

    // Extract distinct board members from Room schedule records & tasks
    val boardMembersList = remember(unifiedScheduleItems, memberTasks) {
        val fromSchedule = unifiedScheduleItems.flatMap { it.getAttendeeNamesList() + listOf(it.chairpersonName) }
        val fromTasks = memberTasks.map { it.assigneeName.trim() }
        (listOf("سخنران", "سخنران", "سخنران", "سخنران") + fromSchedule + fromTasks)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    // Apply member & mode filters
    val filteredScheduleItems = remember(unifiedScheduleItems, scheduleFilterMode, selectedBoardMemberFilter, referenceDate) {
        unifiedScheduleItems.filter { item ->
            val matchesMode = when (scheduleFilterMode) {
                "UPCOMING" -> item.isUpcoming || item.date > referenceDate
                "HISTORY" -> !item.isUpcoming && item.date <= referenceDate
                else -> true
            }
            val matchesMember = if (selectedBoardMemberFilter == "ALL") {
                true
            } else {
                item.attendeesCsv.contains(selectedBoardMemberFilter, ignoreCase = true) ||
                    item.chairpersonName.contains(selectedBoardMemberFilter, ignoreCase = true) ||
                    item.keyResolutionsSummary.contains(selectedBoardMemberFilter, ignoreCase = true)
            }
            matchesMode && matchesMember
        }
    }

    val upcomingMeetings = remember(filteredScheduleItems, referenceDate) {
        filteredScheduleItems
            .filter { it.isUpcoming || it.date > referenceDate }
            .sortedWith(compareBy<BoardScheduleEntity> { it.date }.thenBy { it.startTime })
    }

    val historicalMeetings = remember(filteredScheduleItems, referenceDate) {
        filteredScheduleItems
            .filter { !it.isUpcoming && it.date <= referenceDate }
            .sortedWith(compareByDescending<BoardScheduleEntity> { it.date }.thenByDescending { it.startTime })
    }

    // Persian Months (1 to 12)
    val persianMonths = remember {
        listOf(
            "فروردین" to 1, "اردیبهشت" to 2, "خرداد" to 3,
            "تیر" to 4, "مرداد" to 5, "شهریور" to 6,
            "مهر" to 7, "آبان" to 8, "آذر" to 9,
            "دی" to 10, "بهمن" to 11, "اسفند" to 12
        )
    }

    var selectedYear by remember { mutableIntStateOf(1405) }
    var selectedMonthIndex by remember { mutableIntStateOf(7) } // 7 -> آبان (08)
    var selectedDay by remember { mutableStateOf<Int?>(15) }

    val currentMonthName = persianMonths[selectedMonthIndex].first
    val currentMonthNumber = persianMonths[selectedMonthIndex].second

    // Group schedule items & tasks by normalized date "YYYY/MM/DD"
    val scheduleByDate = remember(filteredScheduleItems) {
        filteredScheduleItems.groupBy { BoardScheduleEntity.normalizeDate(it.date) }
    }

    val filteredTasks = remember(memberTasks, selectedBoardMemberFilter) {
        if (selectedBoardMemberFilter == "ALL") memberTasks
        else memberTasks.filter { it.assigneeName.contains(selectedBoardMemberFilter, ignoreCase = true) }
    }

    val tasksByDate = remember(filteredTasks) {
        filteredTasks.groupBy { BoardScheduleEntity.normalizeDate(it.calendarDate) }
    }

    fun makeDateKey(day: Int): String {
        val mStr = if (currentMonthNumber < 10) "0$currentMonthNumber" else "$currentMonthNumber"
        val dStr = if (day < 10) "0$day" else "$day"
        return "$selectedYear/$mStr/$dStr"
    }

    val daysInMonth = if (selectedMonthIndex < 6) 31 else 30

    val daysWithUpcoming = remember(scheduleByDate, selectedYear, currentMonthNumber, referenceDate) {
        (1..daysInMonth).mapNotNull { day ->
            val key = makeDateKey(day)
            val count = scheduleByDate[key]?.count { it.isUpcoming || it.date > referenceDate } ?: 0
            if (count > 0) day to count else null
        }.toMap()
    }

    val daysWithHistory = remember(scheduleByDate, selectedYear, currentMonthNumber, referenceDate) {
        (1..daysInMonth).mapNotNull { day ->
            val key = makeDateKey(day)
            val count = scheduleByDate[key]?.count { !it.isUpcoming && it.date <= referenceDate } ?: 0
            if (count > 0) day to count else null
        }.toMap()
    }

    val daysWithTasks = remember(tasksByDate, selectedYear, currentMonthNumber) {
        (1..daysInMonth).mapNotNull { day ->
            val key = makeDateKey(day)
            val count = tasksByDate[key]?.size ?: 0
            if (count > 0) day to count else null
        }.toMap()
    }

    val selectedDateKey = selectedDay?.let { makeDateKey(it) } ?: ""
    val selectedDateScheduleItems = scheduleByDate[selectedDateKey] ?: emptyList()
    val selectedDateTasks = tasksByDate[selectedDateKey] ?: emptyList()

    QuantumPanel(
        title = "📅 نمای تقویمی جلسات پیش‌رو و سوابق هیئت‌مدیره (Room DB)",
        badgeText = "${upcomingMeetings.size} پیش‌رو • ${historicalMeetings.size} سابقه",
        modifier = modifier.testTag("meeting_calendar_panel")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "دید جامع زمان‌بندی هیئت‌مدیره بر اساس داده‌های دیتابیس Room: جلسات پیش‌رو (آینده)، سوابق و مصوبات جلسات برگزارشده، و سررسید وظایف اعضا در یک نگاه.",
                color = TextSub,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            // --- 1. KPI Summary Strip from Room Database ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cal_kpi_summary_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalendarKpiMiniCard(
                    label = "🚀 جلسات پیش‌رو",
                    value = "${upcomingMeetings.size}",
                    subLabel = "برنامه‌ریزی‌شده در Room",
                    accentColor = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                CalendarKpiMiniCard(
                    label = "📜 سوابق جلسات",
                    value = "${historicalMeetings.size}",
                    subLabel = "برگزارشده و ثبت‌شده",
                    accentColor = AccentEmeraldLight,
                    modifier = Modifier.weight(1f)
                )
                CalendarKpiMiniCard(
                    label = "📌 مصوبات و وظایف",
                    value = "${filteredScheduleItems.sumOf { it.totalResolutionsCount } + filteredTasks.size}",
                    subLabel = "در تقویم هیئت‌مدیره",
                    accentColor = AccentAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- 2. Mode Filter Chips (All / Upcoming / Historical) + Add Upcoming Meeting Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CalendarModeChip(
                        text = "همه (${filteredScheduleItems.size})",
                        selected = scheduleFilterMode == "ALL",
                        activeColor = AccentPurpleLight,
                        onClick = { scheduleFilterMode = "ALL" },
                        tag = "cal_filter_all"
                    )
                    CalendarModeChip(
                        text = "🚀 پیش‌رو (${upcomingMeetings.size})",
                        selected = scheduleFilterMode == "UPCOMING",
                        activeColor = AccentCyan,
                        onClick = { scheduleFilterMode = "UPCOMING" },
                        tag = "cal_filter_upcoming"
                    )
                    CalendarModeChip(
                        text = "📜 سوابق (${historicalMeetings.size})",
                        selected = scheduleFilterMode == "HISTORY",
                        activeColor = AccentEmeraldLight,
                        onClick = { scheduleFilterMode = "HISTORY" },
                        tag = "cal_filter_history"
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (selectedDay != null) {
                            newDate = makeDateKey(selectedDay!!)
                        }
                        showScheduleForm = !showScheduleForm
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showScheduleForm) AccentRose.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.2f),
                        contentColor = if (showScheduleForm) AccentRose else AccentCyan
                    ),
                    border = BorderStroke(1.dp, if (showScheduleForm) AccentRose.copy(alpha = 0.5f) else AccentCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("cal_open_schedule_form_btn")
                ) {
                    Icon(
                        imageVector = if (showScheduleForm) Icons.Default.Close else Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showScheduleForm) "بستن فرم" else "ثبت جلسه پیش‌رو",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- 2.5. Board Member Schedule Filter Strip ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👤 فیلتر عضو هیئت‌مدیره:",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                FilterChip(
                    selected = selectedBoardMemberFilter == "ALL",
                    onClick = { selectedBoardMemberFilter = "ALL" },
                    label = { Text("همه اعضا", fontSize = 9.5.sp) },
                    modifier = Modifier.height(26.dp).testTag("cal_member_filter_ALL")
                )
                boardMembersList.forEach { memberName ->
                    FilterChip(
                        selected = selectedBoardMemberFilter == memberName,
                        onClick = {
                            selectedBoardMemberFilter = if (selectedBoardMemberFilter == memberName) "ALL" else memberName
                        },
                        label = { Text(memberName, fontSize = 9.5.sp) },
                        modifier = Modifier.height(26.dp).testTag("cal_member_filter_$memberName")
                    )
                }
            }

            // --- 2.8. Inline Form to Schedule a New Upcoming Board Meeting in Room ---
            AnimatedVisibility(visible = showScheduleForm) {
                Surface(
                    color = BgInput,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cal_schedule_upcoming_form")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🗓️ برنامه‌ریزی و ثبت جلسه پیش‌رو جدید در دیتابیس Room",
                            color = AccentCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("عنوان جلسه پیش‌رو هیئت‌مدیره", fontSize = 10.sp) },
                            placeholder = { Text("مثلاً: بررسی بودجه توسعه ناوگان یخچالی", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cal_new_title_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newDate,
                                onValueChange = { newDate = it },
                                label = { Text("تاریخ (YYYY/MM/DD)", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("cal_new_date_input")
                            )
                            OutlinedTextField(
                                value = newStartTime,
                                onValueChange = { newStartTime = it },
                                label = { Text("ساعت شروع", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("cal_new_time_input")
                            )
                            OutlinedTextField(
                                value = newChairperson,
                                onValueChange = { newChairperson = it },
                                label = { Text("دبیر / رئیس جلسه", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("cal_new_chair_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newLocation,
                                onValueChange = { newLocation = it },
                                label = { Text("مکان برگزاری", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )
                            OutlinedTextField(
                                value = newDurationMins,
                                onValueChange = { newDurationMins = it },
                                label = { Text("مدت (دقیقه)", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        OutlinedTextField(
                            value = newAgenda,
                            onValueChange = { newAgenda = it },
                            label = { Text("خلاصه دستور کار و محورهای تصمیم‌گیری", fontSize = 10.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cal_new_agenda_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val finalTitle = newTitle.trim().ifBlank { "جلسه راهبردی پیش‌رو هیئت‌مدیره" }
                                    val finalDate = BoardScheduleEntity.normalizeDate(newDate, "1405/08/24")
                                    val mins = newDurationMins.toIntOrNull() ?: 90
                                    onScheduleUpcomingMeeting?.invoke(
                                        finalTitle,
                                        finalDate,
                                        newStartTime.ifBlank { "10:00" },
                                        mins,
                                        newLocation.ifBlank { "سالن کنفرانس مرکزی هیئت‌مدیره" },
                                        newChairperson.ifBlank { "سخنران" },
                                        newAgenda
                                    )
                                    // Jump calendar to the newly scheduled date
                                    val parts = finalDate.split("/")
                                    if (parts.size == 3) {
                                        selectedYear = parts[0].toIntOrNull() ?: 1405
                                        val m = (parts[1].toIntOrNull() ?: 8).coerceIn(1, 12)
                                        selectedMonthIndex = m - 1
                                        selectedDay = parts[2].toIntOrNull() ?: 24
                                    }
                                    newTitle = ""
                                    newAgenda = ""
                                    showScheduleForm = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("cal_submit_upcoming_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ذخیره در تقویم دیتابیس Room", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // --- 3. Month Navigation Strip ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgInput)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selectedMonthIndex > 0) {
                            selectedMonthIndex--
                        } else {
                            selectedMonthIndex = 11
                            selectedYear--
                        }
                    },
                    modifier = Modifier.size(32.dp).testTag("cal_prev_month_btn")
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "ماه قبل", tint = AccentPurpleLight)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = "$currentMonthName $selectedYear",
                        color = TextMain,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val monthUpcomingCount = daysWithUpcoming.values.sum()
                    val monthHistoryCount = daysWithHistory.values.sum()
                    Surface(
                        color = AccentCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "$monthUpcomingCount پیش‌رو • $monthHistoryCount سابقه",
                            color = AccentCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (selectedMonthIndex < 11) {
                            selectedMonthIndex++
                        } else {
                            selectedMonthIndex = 0
                            selectedYear++
                        }
                    },
                    modifier = Modifier.size(32.dp).testTag("cal_next_month_btn")
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "ماه بعد", tint = AccentPurpleLight)
                }
            }

            // --- 4. Days of Week Header (Saturday to Friday) ---
            val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { wDay ->
                    Text(
                        text = wDay,
                        color = if (wDay == "ج") AccentRose else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // --- 5. Calendar Days Grid (7 columns per row) ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val totalRows = (daysInMonth + 6) / 7
                for (row in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val day = row * 7 + col + 1
                            if (day <= daysInMonth) {
                                val dateKey = makeDateKey(day)
                                val upcomingCount = daysWithUpcoming[day] ?: 0
                                val historyCount = daysWithHistory[day] ?: 0
                                val taskCount = daysWithTasks[day] ?: 0
                                val isSelected = selectedDay == day
                                val isReferenceToday = dateKey == referenceDate
                                val hasDensity = upcomingCount > 0 || historyCount > 0 || taskCount > 0

                                val dayBg = when {
                                    isSelected -> AccentPurple.copy(alpha = 0.38f)
                                    upcomingCount > 0 -> AccentCyan.copy(alpha = 0.16f)
                                    historyCount > 0 -> AccentEmerald.copy(alpha = 0.15f)
                                    taskCount > 0 -> AccentAmber.copy(alpha = 0.12f)
                                    else -> BgInput
                                }
                                val dayBorder = when {
                                    isSelected -> BorderStroke(1.6.dp, AccentCyan)
                                    isReferenceToday -> BorderStroke(1.3.dp, AccentPurpleLight)
                                    upcomingCount > 0 -> BorderStroke(1.dp, AccentCyan.copy(alpha = 0.65f))
                                    historyCount > 0 -> BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.55f))
                                    taskCount > 0 -> BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                                    else -> BorderStroke(1.dp, BorderSubtle)
                                }

                                Surface(
                                    color = dayBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = dayBorder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.02f)
                                        .padding(2.dp)
                                        .clickable { selectedDay = day }
                                        .testTag("cal_day_$day")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize().padding(2.dp)
                                    ) {
                                        Text(
                                            text = "$day",
                                            color = when {
                                                isSelected -> Color.White
                                                upcomingCount > 0 -> AccentCyan
                                                historyCount > 0 -> AccentEmeraldLight
                                                taskCount > 0 -> AccentAmber
                                                else -> TextMain
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = if (hasDensity || isSelected || isReferenceToday) FontWeight.ExtraBold else FontWeight.Normal
                                        )

                                        if (hasDensity) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (upcomingCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentCyan)
                                                    )
                                                }
                                                if (historyCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentEmeraldLight)
                                                    )
                                                }
                                                if (taskCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentAmber)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1.02f).padding(2.dp))
                            }
                        }
                    }
                }
            }

            // --- 6. Legend Strip ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentCyan))
                    Text("جلسه پیش‌رو (Room)", color = TextSub, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentEmeraldLight))
                    Text("سابقه برگزارشده (Room)", color = TextSub, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentAmber))
                    Text("مهلت وظایف اعضا", color = TextSub, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(7.dp).border(1.dp, AccentPurpleLight, CircleShape))
                    Text("تاریخ مرجع ($referenceDate)", color = TextSub, fontSize = 9.sp)
                }
            }

            // --- 7. Selected Date Inspection Card (Upcoming & Historical Sessions + Room Tasks) ---
            AnimatedVisibility(visible = selectedDay != null) {
                Surface(
                    color = BgInput,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedDateScheduleItems.isNotEmpty() || selectedDateTasks.isNotEmpty()) AccentCyan.copy(alpha = 0.5f) else BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("cal_selected_date_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.EventNote, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "رویدادها و سوابق تاریخ $selectedDateKey",
                                    color = TextMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${selectedDateScheduleItems.size} جلسه • ${selectedDateTasks.size} وظیفه",
                                color = if (selectedDateScheduleItems.isNotEmpty() || selectedDateTasks.isNotEmpty()) AccentEmeraldLight else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (selectedDateScheduleItems.isEmpty() && selectedDateTasks.isEmpty()) {
                            Text(
                                text = "جلسه یا وظیفه محوله‌ای برای تاریخ $selectedDateKey در دیتابیس Room ثبت نشده است. با دکمه «ثبت جلسه پیش‌رو» می‌توانید جلسه جدیدی در این تاریخ زمان‌بندی کنید.",
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            selectedDateScheduleItems.forEach { item ->
                                val linkedExecSummary = executiveSummaryBySessionId[item.sessionId]
                                BoardScheduleItemDetailCard(
                                    item = item,
                                    linkedExecutiveSummary = linkedExecSummary,
                                    referenceDate = referenceDate,
                                    onLoad = {
                                        if (onLoadBoardScheduleItem != null) {
                                            onLoadBoardScheduleItem(item)
                                        } else {
                                            onLoadSession(item.toMeetingSession())
                                        }
                                    },
                                    onToggleStatus = onToggleScheduleStatus?.let { cb -> { cb(item) } },
                                    onDelete = onDeleteScheduleItem?.let { cb -> { cb(item.sessionId) } }
                                )
                            }

                            if (selectedDateTasks.isNotEmpty()) {
                                Text(
                                    text = "📋 وظایف محوله اعضا در این تاریخ (${selectedDateTasks.size} مورد در دیتابیس Room):",
                                    color = AccentAmber,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                selectedDateTasks.forEach { task ->
                                    Surface(
                                        color = BgCard,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (task.isDone) AccentEmerald.copy(alpha = 0.45f) else AccentPurple.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Checkbox(
                                                checked = task.isDone,
                                                onCheckedChange = { onToggleMemberTaskDone(task) },
                                                colors = CheckboxDefaults.colors(checkedColor = AccentEmerald),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "👤 مسئول: ${task.assigneeName} (${task.assigneeRole})",
                                                    color = AccentCyan,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = task.taskTitle,
                                                    color = TextMain,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Text(
                                                text = task.priority,
                                                color = if (task.priority == "فوری") AccentRose else AccentAmber,
                                                fontSize = 9.sp,
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

            // --- 8. Chronological Board Timeline: Upcoming Meetings vs. Historical Records (Room DB) ---
            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            // 8A. Upcoming Board Meetings Section
            if (scheduleFilterMode == "ALL" || scheduleFilterMode == "UPCOMING") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("cal_upcoming_list_section")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚀 برنامه زمان‌بندی جلسات پیش‌رو هیئت‌مدیره (Room)",
                            color = AccentCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${upcomingMeetings.size} جلسه آینده",
                            color = TextSub,
                            fontSize = 9.5.sp
                        )
                    }

                    if (upcomingMeetings.isEmpty()) {
                        Text(
                            text = "جلسه پیش‌رویی با فیلتر فعلی یافت نشد.",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    } else {
                        upcomingMeetings.forEach { item ->
                            BoardTimelineRowCard(
                                item = item,
                                isUpcoming = true,
                                onSelectDateInGrid = {
                                    val parts = item.date.split("/")
                                    if (parts.size == 3) {
                                        selectedYear = parts[0].toIntOrNull() ?: 1405
                                        selectedMonthIndex = ((parts[1].toIntOrNull() ?: 8) - 1).coerceIn(0, 11)
                                        selectedDay = parts[2].toIntOrNull() ?: 15
                                    }
                                },
                                onLoad = {
                                    if (onLoadBoardScheduleItem != null) {
                                        onLoadBoardScheduleItem(item)
                                    } else {
                                        onLoadSession(item.toMeetingSession())
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 8B. Historical Board Meetings Section
            if (scheduleFilterMode == "ALL" || scheduleFilterMode == "HISTORY") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("cal_history_list_section")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📜 سوابق جلسات برگزارشده و عملکرد مصوبات (Room)",
                            color = AccentEmeraldLight,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${historicalMeetings.size} سابقه ثبت‌شده",
                            color = TextSub,
                            fontSize = 9.5.sp
                        )
                    }

                    if (historicalMeetings.isEmpty()) {
                        Text(
                            text = "سابقه جلسه‌ای با فیلتر فعلی یافت نشد.",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    } else {
                        historicalMeetings.forEach { item ->
                            BoardTimelineRowCard(
                                item = item,
                                isUpcoming = false,
                                onSelectDateInGrid = {
                                    val parts = item.date.split("/")
                                    if (parts.size == 3) {
                                        selectedYear = parts[0].toIntOrNull() ?: 1405
                                        selectedMonthIndex = ((parts[1].toIntOrNull() ?: 8) - 1).coerceIn(0, 11)
                                        selectedDay = parts[2].toIntOrNull() ?: 15
                                    }
                                },
                                onLoad = {
                                    if (onLoadBoardScheduleItem != null) {
                                        onLoadBoardScheduleItem(item)
                                    } else {
                                        onLoadSession(item.toMeetingSession())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarKpiMiniCard(
    label: String,
    value: String,
    subLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BgInput,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = TextSub, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            Text(text = value, color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = subLabel, color = TextMuted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CalendarModeChip(
    text: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = if (selected) activeColor.copy(alpha = 0.22f) else BgInput,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) activeColor else BorderSubtle),
        modifier = Modifier
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else TextMuted,
            fontSize = 9.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun BoardScheduleItemDetailCard(
    item: BoardScheduleEntity,
    linkedExecutiveSummary: ExecutiveSummaryEntity?,
    referenceDate: String,
    onLoad: () -> Unit,
    onToggleStatus: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val isUpcoming = item.isUpcoming || item.date > referenceDate
    val badgeColor = if (isUpcoming) AccentCyan else AccentEmeraldLight
    val badgeText = if (isUpcoming) "🚀 جلسه پیش‌رو هیئت‌مدیره" else "📜 سابقه جلسه برگزارشده"

    Surface(
        color = BgCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cal_detail_card_${item.sessionId}")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onToggleStatus != null) {
                        OutlinedButton(
                            onClick = onToggleStatus,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("cal_toggle_status_btn_${item.sessionId}")
                        ) {
                            Text(
                                text = if (isUpcoming) "✅ ثبت برگزاری" else "🔄 انتقال به پیش‌رو",
                                color = AccentAmber,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onLoad,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("cal_load_schedule_btn_${item.sessionId}")
                    ) {
                        Text(
                            text = if (isUpcoming) "▶️ باز کردن جلسه" else "📂 بازخوانی سابقه",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onDelete != null && item.sessionId.startsWith("SES-UPCOMING-")) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = AccentRose, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            Text(
                text = item.title.ifBlank { "جلسه راهبردی هیئت‌مدیره" },
                color = TextMain,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.5.sp
            )

            Text(
                text = "📍 ${item.location} • 🕒 ساعت ${item.startTime} (${item.durationMins} دقیقه) • 👤 دبیر/رئیس: ${item.chairpersonName}",
                color = TextSub,
                fontSize = 9.5.sp
            )

            // Participating board members
            val attendees = item.getAttendeeNamesList()
            if (attendees.isNotEmpty()) {
                Text(
                    text = "👥 اعضای هیئت‌مدیره: ${attendees.joinToString(" ، ")}",
                    color = AccentPurpleLight,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Executive summary or upcoming agenda summary
            val summaryToShow = linkedExecutiveSummary?.executiveSummary?.takeIf { it.isNotBlank() }
                ?: item.agendaSummary
            if (summaryToShow.isNotBlank()) {
                Text(
                    text = if (isUpcoming) "📋 دستور کار پیش‌رو: $summaryToShow" else "📝 خلاصه اجرایی سابقه (Room): $summaryToShow",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.5.sp
                )
            }

            // Key resolutions / agenda bullets
            val keyBullets = item.getKeyResolutionsList()
            if (keyBullets.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgInput)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isUpcoming) "📌 محورهای تصمیم‌گیری در این جلسه:" else "⚖️ مصوبات و نتایج ثبت‌شده در سابقه جلسه:",
                        color = badgeColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    keyBullets.take(3).forEach { line ->
                        Text(
                            text = "• $line",
                            color = TextMain,
                            fontSize = 9.5.sp,
                            lineHeight = 13.5.sp
                        )
                    }
                }
            }

            // Status summary badges for historical vs upcoming
            if (item.totalResolutionsCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUpcoming && item.completedResolutionsCount > 0) {
                        ResolutionStatusBadge(
                            status = com.example.data.ResolutionStatusType.COMPLETED,
                            compact = true
                        )
                    }
                    if (item.pendingResolutionsCount > 0) {
                        ResolutionStatusBadge(
                            status = com.example.data.ResolutionStatusType.IN_PROGRESS,
                            compact = true
                        )
                    }
                    Text(
                        text = if (isUpcoming) {
                            "${item.totalResolutionsCount} محور در دستور کار"
                        } else {
                            "${item.completedResolutionsCount} از ${item.totalResolutionsCount} مصوبه تکمیل‌شده"
                        },
                        color = TextSub,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardTimelineRowCard(
    item: BoardScheduleEntity,
    isUpcoming: Boolean,
    onSelectDateInGrid: () -> Unit,
    onLoad: () -> Unit
) {
    val accent = if (isUpcoming) AccentCyan else AccentEmeraldLight
    Surface(
        color = BgInput,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectDateInGrid() }
            .testTag("cal_timeline_item_${item.sessionId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Date & Time Pill
            Surface(
                color = accent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.date,
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "ساعت ${item.startTime}",
                        color = TextSub,
                        fontSize = 8.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextMain,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "👤 ${item.chairpersonName} • 📍 ${item.location}",
                    color = TextSub,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.agendaSummary.isNotBlank()) {
                    Text(
                        text = item.agendaSummary,
                        color = TextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            TextButton(
                onClick = onLoad,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text(
                    text = if (isUpcoming) "مشاهده ◀" else "سابقه ◀",
                    color = accent,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
