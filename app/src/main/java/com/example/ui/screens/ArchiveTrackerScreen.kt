package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeetingSession
import com.example.data.ResolutionStatusType
import com.example.data.local.AttendeeEntity
import com.example.data.local.BoardScheduleEntity
import com.example.data.local.ExecutiveSummaryEntity
import com.example.data.local.MemberTaskEntity
import com.example.data.local.MinutesFtsSearchResult
import com.example.data.resolveStatus
import com.example.ui.components.BoardAnalyticsDashboard
import com.example.ui.components.MeetingCalendarViewComponent
import com.example.ui.components.MeetingListFilterPanel
import com.example.ui.components.MinutesFullTextSearchComponent
import com.example.ui.components.QuantumPanel
import com.example.ui.components.ResolutionStatusBadge
import com.example.ui.components.StatCard
import com.example.ui.theme.*

@Composable
fun ArchiveTrackerScreen(
    session: MeetingSession,
    roomAttendees: List<AttendeeEntity> = session.members.mapIndexed { idx, m ->
        AttendeeEntity.fromMember(m, session.id, idx)
    },
    archiveList: List<MeetingSession>,
    ftsQuery: String = "",
    onFtsQueryChange: (String) -> Unit = {},
    ftsScopeFilter: String = "ALL",
    onFtsScopeFilterChange: (String) -> Unit = {},
    ftsStatusFilter: String = "ALL",
    onFtsStatusFilterChange: (String) -> Unit = {},
    ftsMemberFilter: String = "ALL",
    onFtsMemberFilterChange: (String) -> Unit = {},
    availableResponsibleMembers: List<String> = emptyList(),
    ftsSearchResults: List<MinutesFtsSearchResult> = emptyList(),
    onResetFtsFilters: () -> Unit = {},
    listFilterQuery: String = "",
    onListFilterQueryChange: (String) -> Unit = {},
    listFilterScope: String = "ALL",
    onListFilterScopeChange: (String) -> Unit = {},
    listFilterStatus: String = "ALL",
    onListFilterStatusChange: (String) -> Unit = {},
    listFilterMember: String = "ALL",
    onListFilterMemberChange: (String) -> Unit = {},
    onResetListFilters: () -> Unit = {},
    onToggleNoteDone: (String) -> Unit,
    onLoadSession: (MeetingSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onExportDatabaseJson: () -> String,
    onImportDatabaseJson: (String) -> Unit,
    onSendReminderForNote: ((String) -> Unit)? = null,
    onSendAllPendingReminders: (() -> Unit)? = null,
    roomMemberTasks: List<MemberTaskEntity> = emptyList(),
    onToggleMemberTaskDone: (MemberTaskEntity) -> Unit = {},
    onDeleteMemberTask: (Int) -> Unit = {},
    onSendReminderForMemberTask: ((MemberTaskEntity) -> Unit)? = null,
    onSendAllRoomTaskReminders: ((String?) -> Unit)? = null,
    onExportSessionCorporatePdf: ((MeetingSession) -> Unit)? = null,
    roomBoardScheduleItems: List<BoardScheduleEntity> = emptyList(),
    roomExecutiveSummaries: List<ExecutiveSummaryEntity> = emptyList(),
    onScheduleUpcomingMeeting: ((String, String, String, Int, String, String, String) -> Unit)? = null,
    onToggleBoardScheduleStatus: ((BoardScheduleEntity) -> Unit)? = null,
    onDeleteBoardScheduleItem: ((String) -> Unit)? = null,
    onLoadBoardScheduleItem: ((BoardScheduleEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var viewModeTab by remember { mutableStateOf(0) } // 0: Archive & Actions List, 1: Calendar & Density View
    var selectedTaskAssigneeFilter by remember { mutableStateOf("ALL") }

    val totalActiveNotes = session.notes.size
    val doneActiveNotes = session.notes.count { it.isDone }
    val pendingActiveNotes = totalActiveNotes - doneActiveNotes

    // Filter the archived sessions using the advanced filter criteria
    val filteredArchive = remember(
        archiveList,
        listFilterQuery,
        listFilterScope,
        listFilterStatus,
        listFilterMember
    ) {
        val q = listFilterQuery.trim().lowercase()
        archiveList.filter { s ->
            val matchesQuery = if (q.isBlank()) true else {
                s.title.lowercase().contains(q) ||
                s.date.lowercase().contains(q) ||
                s.extCompany.lowercase().contains(q) ||
                s.notes.any { it.text.lowercase().contains(q) || it.speaker.lowercase().contains(q) }
            }
            val matchesScope = if (listFilterScope == "ALL") true else s.scope.equals(listFilterScope, ignoreCase = true)
            val matchesMember = if (listFilterMember == "ALL") true else {
                s.members.any { it.name.trim().equals(listFilterMember.trim(), ignoreCase = true) } ||
                s.notes.any { it.speaker.trim().equals(listFilterMember.trim(), ignoreCase = true) }
            }
            val matchesStatus = when (listFilterStatus) {
                "ALL" -> true
                "IN_PROGRESS" -> s.notes.any { !it.isDone }
                "COMPLETED" -> s.notes.isNotEmpty() && s.notes.all { it.isDone }
                "DEFERRED" -> s.notes.any { it.resolveStatus() == ResolutionStatusType.DEFERRED }
                else -> true
            }
            matchesQuery && matchesScope && matchesMember && matchesStatus
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. Header & DB Backup / Restore Actions ---
        item {
            QuantumPanel(
                title = "🗂️ مرکز بایگانی، تقویم و کارتابل پیگیری مصوبات",
                badgeText = "امنیت ۱۰۰٪ محلی"
            ) {
                Text(
                    text = "مدیریت تمامی جلسات ذخیره‌شده، تقویم تراکم جلسات و سیستم ارسال اعلانات هوشمند یادآوری مصوبات.",
                    color = TextSub,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export DB JSON Button
                    Button(
                        onClick = {
                            val json = onExportDatabaseJson()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, json)
                                type = "application/json"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "صدور بکاپ دیتابیس اطلس (JSON)"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgInput,
                            contentColor = AccentEmeraldLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("export_json_btn")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("خروجی کل (.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Import DB JSON Button
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgInput,
                            contentColor = AccentCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("import_json_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بازیابی دیتابیس", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 1.5. View Mode Switcher (List & Actions vs. Calendar View) ---
        item {
            Surface(
                color = BgCard,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().testTag("view_mode_tab_strip")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // List Mode Tab
                    Surface(
                        color = if (viewModeTab == 0) AccentPurple.copy(alpha = 0.25f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModeTab == 0) AccentPurpleLight else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModeTab = 0 }
                            .testTag("tab_list_mode_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                tint = if (viewModeTab == 0) AccentPurpleLight else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📋 فهرست و کارتابل جلسات",
                                color = if (viewModeTab == 0) Color.White else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (viewModeTab == 0) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        }
                    }

                    // Calendar Mode Tab
                    Surface(
                        color = if (viewModeTab == 1) AccentCyan.copy(alpha = 0.25f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModeTab == 1) AccentCyan else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModeTab = 1 }
                            .testTag("tab_calendar_mode_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (viewModeTab == 1) AccentCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📅 تقویم تعاملی و تراکم",
                                color = if (viewModeTab == 1) Color.White else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (viewModeTab == 1) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // --- 2. KPI Cards Grid ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "کل جلسات بایگانی",
                    value = archiveList.size.toString(),
                    accentColor = TextMain,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "کل مصوبات جاری",
                    value = totalActiveNotes.toString(),
                    accentColor = AccentPurpleLight,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "انجام‌شده",
                    value = doneActiveNotes.toString(),
                    accentColor = AccentEmeraldLight,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "در انتظار اقدام",
                    value = pendingActiveNotes.toString(),
                    accentColor = AccentAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 2.05. Room-Backed Board Schedule Calendar View (Upcoming & Historical Meetings) ---
        item {
            MeetingCalendarViewComponent(
                sessions = archiveList,
                activeSession = session,
                onLoadSession = onLoadSession,
                memberTasks = roomMemberTasks,
                onToggleMemberTaskDone = onToggleMemberTaskDone,
                roomScheduleItems = roomBoardScheduleItems,
                roomExecutiveSummaries = roomExecutiveSummaries,
                onScheduleUpcomingMeeting = onScheduleUpcomingMeeting,
                onToggleScheduleStatus = onToggleBoardScheduleStatus,
                onDeleteScheduleItem = onDeleteBoardScheduleItem,
                onLoadBoardScheduleItem = onLoadBoardScheduleItem
            )
        }

        // --- 2.1. Room Member Task List Panel (Extracted by Gemini + Deadline Notifications) ---
        if (roomMemberTasks.isNotEmpty()) {
            item {
                val assignees = remember(roomMemberTasks) {
                    listOf("ALL") + roomMemberTasks.map { it.assigneeName }.distinct()
                }
                val filteredMemberTasks = remember(roomMemberTasks, selectedTaskAssigneeFilter) {
                    if (selectedTaskAssigneeFilter == "ALL") roomMemberTasks
                    else roomMemberTasks.filter { it.assigneeName == selectedTaskAssigneeFilter }
                }
                val pendingRoomTaskCount = filteredMemberTasks.count { !it.isDone }

                QuantumPanel(
                    title = "👥 لیست وظایف محوله اعضا در دیتابیس Room (استخراج‌شده با Gemini)",
                    badgeText = "${roomMemberTasks.count { it.isDone }}/${roomMemberTasks.size} تکمیل‌شده",
                    modifier = Modifier.testTag("archive_member_tasks_panel")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "وظایف استخراج‌شده در دیتابیس Room به همراه سیستم یادآوری مهلت تحویل:",
                            color = TextSub,
                            fontSize = 10.5.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (onSendAllRoomTaskReminders != null && pendingRoomTaskCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onSendAllRoomTaskReminders(selectedTaskAssigneeFilter) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentAmber.copy(alpha = 0.2f),
                                    contentColor = AccentAmber
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("send_all_room_task_reminders_btn")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🔔 یادآوری مهلت‌ها ($pendingRoomTaskCount)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Assignee Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        assignees.take(4).forEach { memberName ->
                            val isSelected = selectedTaskAssigneeFilter == memberName
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTaskAssigneeFilter = memberName },
                                label = {
                                    Text(
                                        text = if (memberName == "ALL") "همه اعضا (${roomMemberTasks.size})" else memberName,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredMemberTasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgInput)
                                    .border(
                                        1.dp,
                                        if (task.isDone) AccentEmerald.copy(alpha = 0.45f) else BorderSubtle,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
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
                                        text = "👤 ${task.assigneeName} • ⏰ مهلت: ${task.deadlineLabel} • 📅 ${task.calendarDate}",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = task.taskTitle,
                                        color = TextMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (onSendReminderForMemberTask != null && !task.isDone) {
                                        IconButton(
                                            onClick = { onSendReminderForMemberTask(task) },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .testTag("notify_room_task_btn_${task.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = "ارسال اعلان یادآوری مهلت وظیفه",
                                                tint = AccentAmber,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteMemberTask(task.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = TextMuted, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2.2. Room Database Full-Text Search (FTS4) Component ---
        item {
            MinutesFullTextSearchComponent(
                ftsQuery = ftsQuery,
                onFtsQueryChange = onFtsQueryChange,
                scopeFilter = ftsScopeFilter,
                onScopeFilterChange = onFtsScopeFilterChange,
                statusFilter = ftsStatusFilter,
                onStatusFilterChange = onFtsStatusFilterChange,
                memberFilter = ftsMemberFilter,
                onMemberFilterChange = onFtsMemberFilterChange,
                availableMembers = availableResponsibleMembers,
                searchResults = ftsSearchResults,
                onLoadMatchedSession = onLoadSession,
                onResetAllFilters = onResetFtsFilters
            )
        }

        // --- 2.5. Visual Recharts Dashboard: Board Attendance Trends & Action Completion Over Time ---
        item {
            BoardAnalyticsDashboard(
                session = session,
                roomAttendees = roomAttendees,
                archiveList = archiveList,
                roomMemberTasks = roomMemberTasks
            )
        }

        // --- 3. Action Tracker for Current Session (with Color-Coded Badges & Smart Notifications) ---
        item {
            QuantumPanel(
                title = "📌 کارتابل پیگیری مصوبات و تعهدات جلسه فعال",
                badgeText = "$doneActiveNotes از $totalActiveNotes اقدام شده"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "پایش وضعیت تعهدات و ارسال اعلان هوشمند به اعضای مسئول:",
                        color = TextSub,
                        fontSize = 10.5.sp
                    )

                    if (onSendAllPendingReminders != null && pendingActiveNotes > 0) {
                        Button(
                            onClick = onSendAllPendingReminders,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentAmber.copy(alpha = 0.2f),
                                contentColor = AccentAmber
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("send_all_reminders_btn")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🔔 یادآوری همه معوق‌ها", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (session.notes.isEmpty()) {
                    Text(
                        text = "در جلسه فعال فعلی هنوز مصوبه یا تعهدی ثبت نشده است.",
                        color = TextSub,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    session.notes.forEachIndexed { idx, note ->
                        val status = note.resolveStatus()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BgInput),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (status) {
                                    ResolutionStatusType.COMPLETED -> AccentEmerald.copy(alpha = 0.4f)
                                    ResolutionStatusType.DEFERRED -> AccentAmber.copy(alpha = 0.5f)
                                    ResolutionStatusType.IN_PROGRESS -> BorderSubtle
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .testTag("active_note_card_${note.id}")
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
                                        Text(
                                            text = note.speaker,
                                            color = AccentPurpleLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                        Text("• ${note.type}", color = TextSub, fontSize = 10.sp)
                                        if (note.due.isNotBlank()) {
                                            Text("• مهلت: ${note.due}", color = AccentAmber, fontSize = 10.sp)
                                        }

                                        // Color-coded badge for resolution status
                                        ResolutionStatusBadge(
                                            status = status,
                                            compact = true,
                                            onClick = { onToggleNoteDone(note.id) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = note.text,
                                        color = if (note.isDone) TextSub else TextMain,
                                        fontSize = 11.5.sp,
                                        style = if (note.isDone) androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle()
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // System notification reminder button
                                    if (onSendReminderForNote != null && !note.isDone) {
                                        IconButton(
                                            onClick = { onSendReminderForNote(note.id) },
                                            modifier = Modifier.size(28.dp).testTag("notify_note_btn_${note.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = "ارسال اعلان یادآوری",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Toggle done button
                                    Button(
                                        onClick = { onToggleNoteDone(note.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (note.isDone) AccentEmerald.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.15f),
                                            contentColor = if (note.isDone) AccentEmeraldLight else AccentAmber
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            text = if (note.isDone) "✅ انجام شد" else "⏳ در جریان",
                                            fontSize = 10.sp,
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

        // --- 4. Advanced Filter Panel above Meeting List ---
        item {
            MeetingListFilterPanel(
                searchQuery = listFilterQuery,
                onSearchQueryChange = onListFilterQueryChange,
                selectedScope = listFilterScope,
                onScopeChange = onListFilterScopeChange,
                selectedStatus = listFilterStatus,
                onStatusChange = onListFilterStatusChange,
                selectedMember = listFilterMember,
                onMemberChange = onListFilterMemberChange,
                availableMembers = availableResponsibleMembers,
                filteredCount = filteredArchive.size,
                totalCount = archiveList.size,
                onResetFilters = onResetListFilters
            )
        }

        // --- 5. Saved / Archived Sessions List (with Color-Coded Badges) ---
        item {
            QuantumPanel(
                title = "📚 فهرست جلسات بایگانی‌شده در سیستم",
                badgeText = "${filteredArchive.size} جلسه منطبق"
            ) {
                if (filteredArchive.isEmpty()) {
                    Text(
                        text = if (archiveList.isEmpty()) "هنوز جلسه‌ای در بایگانی ذخیره نشده است." else "جلسه‌ای با این مشخصات و فیلترهای انتخابی یافت نشد.",
                        color = TextSub,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    filteredArchive.forEach { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BgInput),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("archived_session_card_${item.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title.ifBlank { "جلسه بدون عنوان" },
                                        color = TextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Quick Export Finalized Corporate PDF button
                                        if (onExportSessionCorporatePdf != null) {
                                            Button(
                                                onClick = { onExportSessionCorporatePdf(item) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = AccentEmerald.copy(alpha = 0.2f),
                                                    contentColor = AccentEmeraldLight
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(28.dp).testTag("export_corporate_pdf_btn_${item.id}")
                                            ) {
                                                Text("📄 PDF با سربرگ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Load session button
                                        Button(
                                            onClick = { onLoadSession(item) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentPurple.copy(alpha = 0.2f),
                                                contentColor = AccentPurpleLight
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(28.dp).testTag("load_session_btn_${item.id}")
                                        ) {
                                            Text("📂 بازخوانی", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Delete session button
                                        IconButton(
                                            onClick = { onDeleteSession(item.id) },
                                            modifier = Modifier.size(28.dp).testTag("delete_session_btn_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRose, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🗓️ ${item.date} • ${if (item.extCompany.isNotBlank()) item.extCompany else "درون‌سازمانی"}",
                                        color = TextSub,
                                        fontSize = 10.5.sp
                                    )

                                    // Color-coded status badges for this meeting's resolutions
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val hasDone = item.notes.any { it.isDone }
                                        val hasPending = item.notes.any { !it.isDone }
                                        val hasDeferred = item.notes.any { it.resolveStatus() == ResolutionStatusType.DEFERRED }

                                        if (hasDeferred) {
                                            ResolutionStatusBadge(status = ResolutionStatusType.DEFERRED, compact = true)
                                        }
                                        if (hasPending && !hasDeferred) {
                                            ResolutionStatusBadge(status = ResolutionStatusType.IN_PROGRESS, compact = true)
                                        }
                                        if (hasDone && !hasPending) {
                                            ResolutionStatusBadge(status = ResolutionStatusType.COMPLETED, compact = true)
                                        }

                                        Text(
                                            text = "${item.notes.size} مصوبه",
                                            color = AccentCyan,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("بازیابی بانک اطلاعاتی (JSON)", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("محتوای JSON بکاپ صادرشده را در کادر زیر جای‌گذاری کنید:", color = TextMuted, fontSize = 11.5.sp)
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            onImportDatabaseJson(importJsonText)
                            showImportDialog = false
                            importJsonText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("تأیید و بازیابی", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("انصراف", color = TextSub)
                }
            },
            containerColor = BgCard
        )
    }
}
