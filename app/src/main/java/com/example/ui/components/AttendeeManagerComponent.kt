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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Member
import com.example.data.local.AttendeeEntity
import com.example.ui.theme.*

/**
 * Dedicated Jetpack Compose UI component to manage meeting attendees,
 * their organizational roles, departments, and attendance statuses backed by Room Database.
 */
@Composable
fun AttendeeManagementComponent(
    attendees: List<AttendeeEntity>,
    onAddAttendee: (name: String, role: String, department: String, isPresent: Boolean) -> Unit,
    onUpdateAttendee: (AttendeeEntity) -> Unit,
    onToggleAttendance: (AttendeeEntity) -> Unit,
    onChangeAttendanceStatus: (AttendeeEntity, String, Boolean) -> Unit,
    onDeleteAttendee: (String) -> Unit,
    onShowMemberQr: ((Member) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("") }
    var newDepartment by remember { mutableStateOf("") }
    var newIsPresent by remember { mutableStateOf(true) }

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PRESENT, ABSENT
    var searchQuery by remember { mutableStateOf("") }
    var editingAttendeeId by remember { mutableStateOf<String?>(null) }

    val totalCount = attendees.size
    val presentCount = attendees.count { it.isPresent }
    val absentCount = totalCount - presentCount
    val attendanceRate = if (totalCount > 0) (presentCount * 100) / totalCount else 0

    val filteredAttendees = remember(attendees, selectedFilter, searchQuery) {
        attendees.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "PRESENT" -> item.isPresent
                "ABSENT" -> !item.isPresent
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                item.name.contains(searchQuery, ignoreCase = true) ||
                    item.role.contains(searchQuery, ignoreCase = true) ||
                    item.department.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    val presetRoles = listOf(
        "مدیر عملیات" to "لجستیک و زنجیره تأمین",
        "مدیرعامل" to "مدیریت ارشد",
        "رئیس هیئت‌مدیره" to "هیئت‌مدیره",
        "مدیر زنجیره سرد" to "تضمین کیفیت ۵PL",
        "مشاور حقوقی" to "امور قراردادها",
        "مدیر مالی" to "مالی و بودجه",
        "دبیر جلسه" to "حوزه مدیریت"
    )

    QuantumPanel(
        title = "👥 مدیریت اعضا، نقش‌های سازمانی و وضعیت حضور (Room DB)",
        badgeText = "$presentCount از $totalCount حاضر ($attendanceRate%)",
        modifier = modifier.testTag("attendee_management_panel")
    ) {
        // 1. Live Attendance Metrics Strip
        Card(
            colors = CardDefaults.cardColors(containerColor = BgInput),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AttendeeStatBadge(
                        label = "کل اعضا",
                        value = "$totalCount نفر",
                        color = AccentCyan,
                        modifier = Modifier.weight(1f)
                    )
                    AttendeeStatBadge(
                        label = "حاضر در جلسه",
                        value = "$presentCount نفر",
                        color = AccentEmeraldLight,
                        modifier = Modifier.weight(1f)
                    )
                    AttendeeStatBadge(
                        label = "غایب / مرخصی",
                        value = "$absentCount نفر",
                        color = if (absentCount > 0) AccentRose else TextSub,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Attendance Progress Bar
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) presentCount.toFloat() / totalCount.toFloat() else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = AccentEmerald,
                    trackColor = BorderSubtle
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Filter Chips & Add New Attendee Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("همه ($totalCount)", fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentPurple.copy(alpha = 0.25f),
                        selectedLabelColor = AccentPurpleLight
                    ),
                    modifier = Modifier.testTag("filter_attendees_all")
                )
                FilterChip(
                    selected = selectedFilter == "PRESENT",
                    onClick = { selectedFilter = "PRESENT" },
                    label = { Text("حاضرین ($presentCount)", fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = AccentEmeraldLight
                    ),
                    modifier = Modifier.testTag("filter_attendees_present")
                )
                FilterChip(
                    selected = selectedFilter == "ABSENT",
                    onClick = { selectedFilter = "ABSENT" },
                    label = { Text("غایبین ($absentCount)", fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentRose.copy(alpha = 0.2f),
                        selectedLabelColor = AccentRose
                    ),
                    modifier = Modifier.testTag("filter_attendees_absent")
                )
            }

            Button(
                onClick = { showAddForm = !showAddForm },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showAddForm) AccentRose else AccentPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("toggle_add_attendee_form_btn")
            ) {
                Icon(
                    imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showAddForm) "بستن فرم" else "+ افزودن عضو",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. Expandable New Attendee Entry Card
        AnimatedVisibility(visible = showAddForm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .testTag("add_attendee_form_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "➕ ثبت عضو جدید در بانک اطلاعاتی Room:",
                        color = AccentPurpleLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Role Presets
                    Text(
                        text = "انتخاب سریع نقش سازمانی:",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetRoles.forEach { (roleTitle, deptName) ->
                            SuggestionChip(
                                onClick = {
                                    newRole = roleTitle
                                    if (newDepartment.isBlank()) newDepartment = deptName
                                },
                                label = { Text(roleTitle, fontSize = 10.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = BgInput,
                                    labelColor = if (newRole == roleTitle) AccentCyan else TextMain
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = if (newRole == roleTitle) AccentCyan else BorderSubtle
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("نام و نام خانوادگی", fontSize = 10.5.sp) },
                            placeholder = { Text("مثلاً: مهندس کریمی") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("new_attendee_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = newRole,
                            onValueChange = { newRole = it },
                            label = { Text("سمت / نقش در جلسه", fontSize = 10.5.sp) },
                            placeholder = { Text("مثلاً: مدیر لجستیک") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_attendee_role_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDepartment,
                            onValueChange = { newDepartment = it },
                            label = { Text("واحد / دپارتمان سازمانی", fontSize = 10.5.sp) },
                            placeholder = { Text("عملیات ۵PL") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("new_attendee_dept_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Initial Attendance Status Toggle
                        FilterChip(
                            selected = newIsPresent,
                            onClick = { newIsPresent = !newIsPresent },
                            label = {
                                Text(
                                    text = if (newIsPresent) "وضعیت: حاضر ✅" else "وضعیت: غایب ❌",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = AccentEmeraldLight,
                                containerColor = AccentRose.copy(alpha = 0.15f),
                                labelColor = AccentRose
                            ),
                            modifier = Modifier.testTag("new_attendee_status_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddAttendee(
                                    newName.trim(),
                                    newRole.trim().ifBlank { "عضو کارگروه" },
                                    newDepartment.trim().ifBlank { "ستاد مرکزی" },
                                    newIsPresent
                                )
                                newName = ""
                                newRole = ""
                                newDepartment = ""
                                newIsPresent = true
                                showAddForm = false
                            }
                        },
                        enabled = newName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = BgCanvas
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("confirm_add_attendee_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره عضو در جدول حاضرین (Room)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Search Bar
        if (attendees.size > 2) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجوی نام، سمت یا واحد سازمانی...", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاکسازی", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendee_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgInput,
                    unfocusedContainerColor = BgInput
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 5. Attendees List
        if (filteredAttendees.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgInput),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = TextSub,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "هیچ عضوی مطابق فیلتر انتخابی یافت نشد.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredAttendees.forEachIndexed { index, attendee ->
                    val isEditing = editingAttendeeId == attendee.id
                    AttendeeItemCard(
                        attendee = attendee,
                        index = index,
                        isEditing = isEditing,
                        onToggleEdit = {
                            editingAttendeeId = if (isEditing) null else attendee.id
                        },
                        onUpdateAttendee = { updated ->
                            onUpdateAttendee(updated)
                        },
                        onToggleAttendance = {
                            onToggleAttendance(attendee)
                        },
                        onChangeStatus = { statusLabel, presentFlag ->
                            onChangeAttendanceStatus(attendee, statusLabel, presentFlag)
                        },
                        onDelete = {
                            onDeleteAttendee(attendee.id)
                        },
                        onShowQr = onShowMemberQr?.let { callback ->
                            { callback(attendee.toMember()) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendeeStatBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun AttendeeItemCard(
    attendee: AttendeeEntity,
    index: Int,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onUpdateAttendee: (AttendeeEntity) -> Unit,
    onToggleAttendance: () -> Unit,
    onChangeStatus: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onShowQr: (() -> Unit)? = null
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    val statusColor = when {
        attendee.attendanceStatus == AttendeeEntity.STATUS_LATE -> AccentAmber
        attendee.attendanceStatus == AttendeeEntity.STATUS_EXCUSED -> AccentCyan
        attendee.isPresent -> AccentEmerald
        else -> AccentRose
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BgInput),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (attendee.isPresent) BorderSubtle else AccentRose.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendee_card_$index")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar + Name & Role Summary
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.18f))
                            .border(1.dp, statusColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = attendee.name.trim().take(1).ifBlank { "ع" },
                            color = statusColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attendee.name.ifBlank { "عضو بدون نام" },
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Role badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentPurple.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = attendee.role.ifBlank { "سمت نامشخص" },
                                    color = AccentPurpleLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (attendee.department.isNotBlank()) {
                                Text(
                                    text = "• ${attendee.department}",
                                    color = TextSub,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Attendance Status Chip + Dropdown
                Box {
                    FilterChip(
                        selected = attendee.isPresent,
                        onClick = onToggleAttendance,
                        label = {
                            Text(
                                text = attendee.attendanceStatus,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "تغییر وضعیت",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { statusMenuExpanded = true }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = statusColor.copy(alpha = 0.18f),
                            selectedLabelColor = statusColor,
                            containerColor = AccentRose.copy(alpha = 0.15f),
                            labelColor = AccentRose
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = statusColor.copy(alpha = 0.6f),
                            enabled = true,
                            selected = attendee.isPresent
                        ),
                        modifier = Modifier.testTag("attendee_status_toggle_$index")
                    )

                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false },
                        modifier = Modifier.background(BgCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("✅ حاضر در جلسه", color = AccentEmeraldLight, fontSize = 11.5.sp) },
                            onClick = {
                                statusMenuExpanded = false
                                onChangeStatus(AttendeeEntity.STATUS_PRESENT, true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⏳ حاضر (با تأخیر)", color = AccentAmber, fontSize = 11.5.sp) },
                            onClick = {
                                statusMenuExpanded = false
                                onChangeStatus(AttendeeEntity.STATUS_LATE, true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("❌ غایب", color = AccentRose, fontSize = 11.5.sp) },
                            onClick = {
                                statusMenuExpanded = false
                                onChangeStatus(AttendeeEntity.STATUS_ABSENT, false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📋 مرخصی / مأموریت", color = AccentCyan, fontSize = 11.5.sp) },
                            onClick = {
                                statusMenuExpanded = false
                                onChangeStatus(AttendeeEntity.STATUS_EXCUSED, false)
                            }
                        )
                    }
                }

                // Action Icons: Edit, QR, Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleEdit,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("edit_attendee_btn_$index")
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "ویرایش مشخصات و نقش",
                            tint = if (isEditing) AccentEmeraldLight else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (onShowQr != null) {
                        IconButton(
                            onClick = onShowQr,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("qr_attendee_btn_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "کارت QR عضو",
                                tint = AccentCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("delete_attendee_btn_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف عضو",
                            tint = AccentRose,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Expandable Inline Role & Info Editor
            AnimatedVisibility(visible = isEditing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = attendee.name,
                            onValueChange = { onUpdateAttendee(attendee.copy(name = it)) },
                            label = { Text("نام و نام خانوادگی", fontSize = 9.5.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = attendee.role,
                            onValueChange = { onUpdateAttendee(attendee.copy(role = it)) },
                            label = { Text("نقش / سمت سازمانی", fontSize = 9.5.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = attendee.department,
                            onValueChange = { onUpdateAttendee(attendee.copy(department = it)) },
                            label = { Text("واحد", fontSize = 9.5.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }
}
