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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeetingSession
import com.example.data.local.BoardScheduleEntity
import com.example.data.local.BoardScheduleRepository
import com.example.ui.theme.*
import com.example.viewmodel.MeetingViewModel

private val OledPureBlack = Color(0xFF000000)
private val OledSurfaceDark = Color(0xFF050505)
private val OledCardBg = Color(0xFF0A0A0C)
private val NeonPurplePrimary = Color(0xFF9333EA)
private val NeonPurpleHover = Color(0xFFA855F7)
private val NeonPurpleDeep = Color(0xFF581C87)

/**
 * Main Page OLED Black & Neon Purple Workspace featuring:
 * 1. Top Search Section with integrated Date Range Picker (`انتخابگر محدوده زمانی در بخش جستجوی بالای صفحه`)
 *    and "جلسه جدید / ضبط زنده" action button.
 * 2. Interactive Monthly Calendar Widget (`ویجت تقویم تعاملی ماهانه در صفحه اصلی`) showing scheduled meetings
 *    across the month with single-date and date-range filtering.
 * 3. Holding & 5PL Logistics Meeting Cards (`جلسات کلان هلدینگ و لجستیک`) filtered dynamically by date,
 *    date range, and search query.
 */
@Composable
fun HomeWorkspaceAndCalendarWidget(
    allScheduleItems: List<BoardScheduleEntity>,
    activeSession: MeetingSession,
    archiveList: List<MeetingSession>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCalendarDate: String,
    onSelectCalendarDate: (String) -> Unit,
    dateRangeStart: String,
    dateRangeEnd: String,
    onUpdateDateRange: (String, String) -> Unit,
    onClearDateRange: () -> Unit,
    onClearAllFilters: () -> Unit,
    onLoadMeetingItem: (BoardScheduleEntity) -> Unit,
    onStartLiveRecordingSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Merge Room schedule items with default seed & active/archive sessions so calendar is always populated
    val unifiedMeetings = remember(allScheduleItems, activeSession, archiveList) {
        val map = linkedMapOf<String, BoardScheduleEntity>()
        val source = if (allScheduleItems.isNotEmpty()) {
            allScheduleItems
        } else {
            BoardScheduleRepository.buildDefaultBoardScheduleSeed()
        }
        source.forEach { map[it.sessionId] = it }
        BoardScheduleRepository.buildDefaultBoardScheduleSeed().forEach { seed ->
            if (!map.containsKey(seed.sessionId)) {
                map[seed.sessionId] = seed
            }
        }
        archiveList.forEach { s ->
            if (!map.containsKey(s.id)) {
                map[s.id] = BoardScheduleEntity.fromMeetingSession(s)
            }
        }
        if (activeSession.title.isNotBlank() || activeSession.notes.isNotEmpty()) {
            if (!map.containsKey(activeSession.id)) {
                map[activeSession.id] = BoardScheduleEntity.fromMeetingSession(activeSession)
            }
        }
        map.values.sortedWith(compareByDescending<BoardScheduleEntity> { BoardScheduleEntity.normalizeDate(it.date) }.thenBy { it.startTime })
    }

    val filteredMeetings = remember(unifiedMeetings, searchQuery, selectedCalendarDate, dateRangeStart, dateRangeEnd) {
        MeetingViewModel.filterBoardMeetingsByDateAndSearch(
            items = unifiedMeetings,
            searchQuery = searchQuery,
            selectedDate = selectedCalendarDate,
            dateRangeStart = dateRangeStart,
            dateRangeEnd = dateRangeEnd
        )
    }

    var isDateRangePickerExpanded by remember { mutableStateOf(true) }
    var isCalendarRangeSelectionMode by remember { mutableStateOf(false) }
    var draftStartDate by remember(dateRangeStart) { mutableStateOf(dateRangeStart.ifBlank { "1405/08/01" }) }
    var draftEndDate by remember(dateRangeEnd) { mutableStateOf(dateRangeEnd.ifBlank { "1405/08/30" }) }
    var rangePickStep by remember { mutableIntStateOf(0) } // 0 = pick start, 1 = pick end

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

    val currentMonthName = persianMonths[selectedMonthIndex].first
    val currentMonthNumber = persianMonths[selectedMonthIndex].second
    val daysInMonth = if (selectedMonthIndex < 6) 31 else 30

    fun formatDateKey(day: Int): String {
        val mStr = currentMonthNumber.toString().padStart(2, '0')
        val dStr = day.toString().padStart(2, '0')
        return "$selectedYear/$mStr/$dStr"
    }

    val meetingsByDate = remember(unifiedMeetings) {
        unifiedMeetings.groupBy { BoardScheduleEntity.normalizeDate(it.date) }
    }

    val hasActiveFilter = searchQuery.isNotBlank() ||
        selectedCalendarDate.isNotBlank() ||
        dateRangeStart.isNotBlank() ||
        dateRangeEnd.isNotBlank()

    Surface(
        color = OledPureBlack,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, NeonPurplePrimary.copy(alpha = 0.38f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_workspace_and_calendar_widget")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonPurpleDeep.copy(alpha = 0.16f),
                            OledPureBlack,
                            OledSurfaceDark
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- 1. TOP HEADER BAR WITH SEARCH & DATE RANGE PICKER ---
            Surface(
                color = OledSurfaceDark,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top_search_and_date_range_bar")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Brand + User Identity Row (from Atlas OLED Reference)
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
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(NeonPurplePrimary, NeonPurpleDeep)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "اتلس | Atlas",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "AI MEETING ASSISTANT • 5PL ERP",
                                    color = NeonPurpleHover,
                                    fontSize = 8.8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Rasool Najafi Profile Badge + New Live Recording CTA
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = OledCardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(NeonPurplePrimary.copy(alpha = 0.22f))
                                            .border(1.dp, NeonPurplePrimary.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "رن",
                                            color = NeonPurpleHover,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "سخنران",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "مدیر عملیات هلدینگ",
                                            color = TextMuted,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onStartLiveRecordingSession,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonPurplePrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("top_new_live_recording_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "جلسه جدید / ضبط زنده",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Search Input + Date Range Picker Toggle Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    text = "جستجو در جلسات، مصوبات یا تصمیمات...",
                                    fontSize = 10.2.sp,
                                    color = TextMuted
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = NeonPurpleHover,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "پاک کردن جستجو",
                                            tint = AccentRose,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurplePrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = OledCardBg,
                                unfocusedContainerColor = OledCardBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("top_meetings_search_input")
                        )

                        // Date Range Picker Trigger Button in Top Search Section
                        val hasDateRangeActive = dateRangeStart.isNotBlank() || dateRangeEnd.isNotBlank()
                        OutlinedButton(
                            onClick = { isDateRangePickerExpanded = !isDateRangePickerExpanded },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (hasDateRangeActive) NeonPurplePrimary.copy(alpha = 0.22f) else OledCardBg,
                                contentColor = if (hasDateRangeActive) NeonPurpleHover else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (hasDateRangeActive) NeonPurplePrimary else Color.White.copy(alpha = 0.14f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("top_date_range_picker_toggle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "انتخابگر محدوده زمانی",
                                tint = if (hasDateRangeActive) NeonPurpleHover else AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "محدوده زمانی",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (hasDateRangeActive) {
                                        "${dateRangeStart.takeLast(5)} تا ${dateRangeEnd.takeLast(5)}"
                                    } else {
                                        "Date Range"
                                    },
                                    fontSize = 8.2.sp,
                                    color = if (hasDateRangeActive) AccentEmeraldLight else TextMuted
                                )
                            }
                        }
                    }

                    // --- EXPANDABLE DATE RANGE PICKER IN TOP SEARCH SECTION ---
                    AnimatedVisibility(visible = isDateRangePickerExpanded) {
                        Surface(
                            color = OledCardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NeonPurplePrimary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("top_date_range_picker_panel")
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
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = NeonPurpleHover,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "📅 انتخابگر محدوده زمانی جلسات (Date Range Picker)",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    // Toggle interactive range selection on the monthly calendar below
                                    Surface(
                                        color = if (isCalendarRangeSelectionMode) AccentCyan.copy(alpha = 0.22f) else BgInput,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCalendarRangeSelectionMode) AccentCyan else BorderSubtle
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                isCalendarRangeSelectionMode = !isCalendarRangeSelectionMode
                                                rangePickStep = 0
                                            }
                                            .testTag("date_range_calendar_pick_mode_btn")
                                    ) {
                                        Text(
                                            text = if (isCalendarRangeSelectionMode) {
                                                if (rangePickStep == 0) "🎯 روز شروع را روی تقویم بزنید" else "🎯 روز پایان را روی تقویم بزنید"
                                            } else {
                                                "🖱️ انتخاب بازه روی تقویم"
                                            },
                                            color = if (isCalendarRangeSelectionMode) AccentCyan else TextSub,
                                            fontSize = 8.8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Start Date (از تاریخ) & End Date (تا تاریخ) Inputs + Apply / Clear Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = draftStartDate,
                                        onValueChange = { draftStartDate = it },
                                        label = { Text("از تاریخ (شروع)", fontSize = 9.5.sp) },
                                        placeholder = { Text("1405/08/01", fontSize = 9.5.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonPurplePrimary,
                                            unfocusedBorderColor = BorderSubtle,
                                            focusedContainerColor = BgInput,
                                            unfocusedContainerColor = BgInput
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("date_range_start_input")
                                    )

                                    OutlinedTextField(
                                        value = draftEndDate,
                                        onValueChange = { draftEndDate = it },
                                        label = { Text("تا تاریخ (پایان)", fontSize = 9.5.sp) },
                                        placeholder = { Text("1405/08/30", fontSize = 9.5.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonPurplePrimary,
                                            unfocusedBorderColor = BorderSubtle,
                                            focusedContainerColor = BgInput,
                                            unfocusedContainerColor = BgInput
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("date_range_end_input")
                                    )

                                    Button(
                                        onClick = {
                                            onUpdateDateRange(draftStartDate, draftEndDate)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonPurplePrimary,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("date_range_apply_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("اعمال بازه", fontSize = 9.8.sp, fontWeight = FontWeight.ExtraBold)
                                    }

                                    if (dateRangeStart.isNotBlank() || dateRangeEnd.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                draftStartDate = "1405/08/01"
                                                draftEndDate = "1405/08/30"
                                                onClearDateRange()
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AccentRose.copy(alpha = 0.18f))
                                                .border(1.dp, AccentRose.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                                .testTag("date_range_clear_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FilterAltOff,
                                                contentDescription = "حذف فیلتر بازه زمانی",
                                                tint = AccentRose,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick Date Range Presets Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ بازه‌های آماده:",
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val presets = listOf(
                                        Triple("all", "همه تاریخ‌ها", "" to ""),
                                        Triple("holding_days", "۱۶ تا ۱۷ آبان (هلدینگ)", "1405/08/16" to "1405/08/17"),
                                        Triple("first_half", "نیمه اول آبان (۰۱ تا ۱۵)", "1405/08/01" to "1405/08/15"),
                                        Triple("second_half", "نیمه دوم آبان (۱۶ تا ۳۰)", "1405/08/16" to "1405/08/30"),
                                        Triple("upcoming", "جلسات پیش‌رو (از ۱۸ آبان)", "1405/08/18" to "1405/09/30")
                                    )

                                    presets.forEach { (id, label, range) ->
                                        val isSelected = dateRangeStart == range.first && dateRangeEnd == range.second &&
                                            (range.first.isNotBlank() || selectedCalendarDate.isBlank())
                                        Surface(
                                            color = if (isSelected) NeonPurplePrimary.copy(alpha = 0.28f) else BgInput,
                                            shape = RoundedCornerShape(7.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) NeonPurpleHover else BorderSubtle
                                            ),
                                            modifier = Modifier
                                                .clickable {
                                                    if (range.first.isBlank() && range.second.isBlank()) {
                                                        onClearAllFilters()
                                                    } else {
                                                        draftStartDate = range.first
                                                        draftEndDate = range.second
                                                        onUpdateDateRange(range.first, range.second)
                                                    }
                                                }
                                                .testTag("date_range_preset_$id")
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else TextSub,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 2. INTERACTIVE MONTHLY CALENDAR WIDGET ON MAIN PAGE ---
            Surface(
                color = OledCardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_interactive_monthly_calendar_widget")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calendar Header: Title + Month/Year Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🗓️ تقویم تعاملی ماهانه جلسات برنامه‌ریزی‌شده",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "برای فیلتر کردن جلسات بر اساس روز، روی تاریخ مورد نظر کلیک کنید",
                                color = TextMuted,
                                fontSize = 9.2.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(BgInput)
                                    .testTag("home_cal_prev_month_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "ماه قبل",
                                    tint = NeonPurpleHover,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Surface(
                                color = NeonPurplePrimary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NeonPurplePrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "$currentMonthName $selectedYear",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("home_cal_current_month_label")
                                )
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
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(BgInput)
                                    .testTag("home_cal_next_month_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "ماه بعد",
                                    tint = NeonPurpleHover,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Weekday Headers (ش .. ج)
                    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        weekDays.forEach { wd ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = wd,
                                    color = TextMuted,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Monthly Days Grid (5 rows of 7 columns)
                    val totalCells = 35
                    val startOffset = (currentMonthNumber * 2) % 7
                    for (week in 0 until 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = week * 7 + col
                                val dayNumber = cellIndex - startOffset + 1
                                if (dayNumber in 1..daysInMonth) {
                                    val dateKey = formatDateKey(dayNumber)
                                    val dayMeetings = meetingsByDate[dateKey] ?: emptyList()
                                    val isSingleSelected = selectedCalendarDate == dateKey
                                    val isInDateRange = dateRangeStart.isNotBlank() &&
                                        dateRangeEnd.isNotBlank() &&
                                        dateKey >= dateRangeStart &&
                                        dateKey <= dateRangeEnd
                                    val isRangeBoundary = dateKey == dateRangeStart || dateKey == dateRangeEnd
                                    val hasUpcoming = dayMeetings.any { it.isUpcoming }
                                    val hasCompleted = dayMeetings.any { !it.isUpcoming }

                                    val cellBg = when {
                                        isSingleSelected || isRangeBoundary -> NeonPurplePrimary
                                        isInDateRange -> NeonPurplePrimary.copy(alpha = 0.24f)
                                        dayMeetings.isNotEmpty() -> Color(0xFF151622)
                                        else -> OledSurfaceDark
                                    }

                                    val cellBorder = when {
                                        isSingleSelected || isRangeBoundary -> Color.White
                                        isInDateRange -> NeonPurpleHover.copy(alpha = 0.65f)
                                        hasUpcoming -> AccentCyan.copy(alpha = 0.65f)
                                        hasCompleted -> AccentEmerald.copy(alpha = 0.65f)
                                        else -> Color.White.copy(alpha = 0.06f)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cellBg)
                                            .border(1.dp, cellBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (isCalendarRangeSelectionMode) {
                                                    if (rangePickStep == 0) {
                                                        draftStartDate = dateKey
                                                        draftEndDate = dateKey
                                                        rangePickStep = 1
                                                    } else {
                                                        draftEndDate = dateKey
                                                        onUpdateDateRange(draftStartDate, dateKey)
                                                        rangePickStep = 0
                                                        isCalendarRangeSelectionMode = false
                                                    }
                                                } else {
                                                    onSelectCalendarDate(dateKey)
                                                }
                                            }
                                            .testTag("home_cal_day_$dayNumber"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNumber",
                                                color = if (isSingleSelected || isRangeBoundary || dayMeetings.isNotEmpty()) {
                                                    Color.White
                                                } else {
                                                    TextMuted
                                                },
                                                fontSize = 10.5.sp,
                                                fontWeight = if (dayMeetings.isNotEmpty() || isSingleSelected) {
                                                    FontWeight.ExtraBold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )

                                            if (dayMeetings.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (hasUpcoming) AccentCyan else AccentEmeraldLight
                                                            )
                                                    )
                                                    Text(
                                                        text = "${dayMeetings.size} جلسه",
                                                        color = if (isSingleSelected || isRangeBoundary) Color.White else NeonPurpleHover,
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (cellIndex < totalCells) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Active Date / Range Filter Status Strip
                    if (hasActiveFilter) {
                        Surface(
                            color = NeonPurplePrimary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NeonPurplePrimary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_active_date_filter_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filterDesc = buildString {
                                    append("🔍 فیلتر فعال: ")
                                    if (selectedCalendarDate.isNotBlank()) {
                                        append("تاریخ $selectedCalendarDate ")
                                    }
                                    if (dateRangeStart.isNotBlank() || dateRangeEnd.isNotBlank()) {
                                        append("بازه زمانی ${dateRangeStart.ifBlank { "ابتدا" }} تا ${dateRangeEnd.ifBlank { "انتها" }} ")
                                    }
                                    if (searchQuery.isNotBlank()) {
                                        append("• عبارت «$searchQuery» ")
                                    }
                                    append("(${filteredMeetings.size} جلسه یافت شد)")
                                }
                                Text(
                                    text = filterDesc,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "نمایش همه جلسات ✕",
                                    color = AccentRose,
                                    fontSize = 9.2.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clickable { onClearAllFilters() }
                                        .padding(start = 8.dp)
                                        .testTag("home_clear_all_date_filters_btn")
                                )
                            }
                        }
                    }
                }
            }

            // --- 3. HOLDING & 5PL LOGISTICS MEETINGS SECTION (FROM ATLAS OLED REFERENCE) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_filtered_meetings_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "جلسات کلان هلدینگ و لجستیک",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "مدیریت هماهنگی میان چابکدارو، چاپار و زیرساخت 5PL",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Surface(
                        color = NeonPurplePrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(99.dp),
                        border = BorderStroke(1.dp, NeonPurplePrimary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "تاریخ امروز: ۱۴۰۵/۰۸/۱۸",
                            color = NeonPurpleHover,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (filteredMeetings.isEmpty()) {
                    Surface(
                        color = OledCardBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_empty_filtered_meetings_state")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = NeonPurpleHover,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "هیچ جلسه‌ای در تاریخ یا بازه زمانی انتخاب‌شده یافت نشد.",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = onClearAllFilters,
                                border = BorderStroke(1.dp, NeonPurplePrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("پاک‌سازی فیلتر تاریخ و نمایش همه جلسات", color = NeonPurpleHover, fontSize = 10.sp)
                            }
                        }
                    }
                } else {
                    filteredMeetings.take(6).forEach { meeting ->
                        AtlasOledMeetingCard(
                            meeting = meeting,
                            onClick = { onLoadMeetingItem(meeting) }
                        )
                    }
                }
            }

            // --- 4. OLED STATUS FOOTER BAR ---
            Surface(
                color = OledSurfaceDark,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                .background(AccentEmerald)
                        )
                        Text(
                            text = "Atlas AI Engine: Ready (Gemini Connected)",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "OLED Black & Neon Purple Theme Active",
                        color = NeonPurpleHover,
                        fontSize = 8.8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AtlasOledMeetingCard(
    meeting: BoardScheduleEntity,
    onClick: () -> Unit
) {
    val statusText = when {
        meeting.isFinalized || !meeting.isUpcoming -> "تایید شده"
        meeting.sessionId == "SES-HOLDING-1405-08-16" -> "در حال پیگیری"
        else -> "جلسه پیش‌رو"
    }

    val statusColor = when (statusText) {
        "تایید شده" -> NeonPurpleHover
        "در حال پیگیری" -> AccentAmber
        else -> AccentCyan
    }

    val attendees = remember(meeting.attendeesCsv) {
        meeting.getAttendeeNamesList().ifEmpty { listOf("سخنران", "مدیران", "فنی") }
    }

    Surface(
        color = OledCardBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NeonPurplePrimary.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("home_meeting_card_${meeting.sessionId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NeonPurplePrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${meeting.date} • ساعت ${meeting.startTime}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = meeting.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 18.sp
            )

            if (meeting.agendaSummary.isNotBlank()) {
                Text(
                    text = meeting.agendaSummary,
                    color = TextSub,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.5.sp
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Overlapping Avatar Stack
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    attendees.take(4).forEach { name ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(NeonPurplePrimary)
                                .border(1.5.dp, OledCardBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.trim().take(1).ifBlank { "ر" },
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = attendees.take(3).joinToString("، "),
                        color = TextMuted,
                        fontSize = 9.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "مشاهده گزارش",
                        color = NeonPurpleHover,
                        fontSize = 10.2.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = NeonPurpleHover,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
