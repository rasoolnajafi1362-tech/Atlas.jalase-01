package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Advanced Meeting Filter Panel embedded above meeting lists.
 * Allows filtering by:
 * 1. Meeting Type/Scope (نوع جلسه)
 * 2. Resolution Status (وضعیت مصوبات: در حال انجام، تکمیل شده، معوق)
 * 3. Responsible Member (نام عضو مسئول)
 */
@Composable
fun MeetingListFilterPanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedScope: String,
    onScopeChange: (String) -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    selectedMember: String,
    onMemberChange: (String) -> Unit,
    availableMembers: List<String>,
    filteredCount: Int,
    totalCount: Int,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scopeDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }

    val scopeOptions = remember {
        listOf(
            "ALL" to "همه انواع جلسات",
            "internal" to "درون‌سازمانی / هیئت‌مدیره",
            "external_partner" to "شریک استراتژیک (Partner)",
            "external_supplier" to "تأمین‌کننده لجستیک (Supplier)",
            "external_contract" to "عقد قرارداد رسمی",
            "interview_hr" to "مصاحبه تخصصی (HR)"
        )
    }

    val statusOptions = remember {
        listOf(
            "ALL" to "همه وضعیت‌ها",
            "IN_PROGRESS" to "⏳ در حال انجام",
            "COMPLETED" to "✅ کلیه مصوبات تکمیل‌شده",
            "DEFERRED" to "🚨 دارای مصوبه معوق / تاخیر"
        )
    }

    val hasActiveFilter = searchQuery.isNotBlank() ||
        selectedScope != "ALL" ||
        selectedStatus != "ALL" ||
        selectedMember != "ALL"

    QuantumPanel(
        title = "⚡ فیلتر هوشمند صورت‌جلسات (محدوده جلسه • وضعیت مصوبه • عضو مسئول)",
        badgeText = "$filteredCount از $totalCount صورت‌جلسه",
        modifier = modifier.testTag("advanced_meeting_filter_panel")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Text Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("جستجو در عنوان، تاریخ، شرکت یا بندهای مصوبه...", fontSize = 10.5.sp) },
                leadingIcon = {
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن", tint = AccentRose, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filter_search_text_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgInput,
                    unfocusedContainerColor = BgInput
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Dropdowns Row (محدوده جلسه, وضعیت مصوبه, عضو مسئول)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Meeting Scope (محدوده جلسه) Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    val currentScopeLabel = scopeOptions.find { it.first == selectedScope }?.second ?: "محدوده جلسه"
                    OutlinedButton(
                        onClick = { scopeDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (selectedScope != "ALL") AccentCyan else BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedScope != "ALL") AccentCyan.copy(alpha = 0.12f) else BgInput,
                            contentColor = if (selectedScope != "ALL") AccentCyan else TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("filter_scope_dropdown_btn")
                    ) {
                        Text(
                            text = if (selectedScope == "ALL") "محدوده جلسه" else currentScopeLabel.take(16),
                            fontSize = 9.5.sp,
                            fontWeight = if (selectedScope != "ALL") FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    DropdownMenu(
                        expanded = scopeDropdownExpanded,
                        onDismissRequest = { scopeDropdownExpanded = false }
                    ) {
                        scopeOptions.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 10.5.sp, fontWeight = if (selectedScope == code) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onScopeChange(code)
                                    scopeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Resolution Status (وضعیت مصوبه) Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    val currentStatusLabel = statusOptions.find { it.first == selectedStatus }?.second ?: "وضعیت مصوبه"
                    OutlinedButton(
                        onClick = { statusDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (selectedStatus != "ALL") AccentAmber else BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedStatus != "ALL") AccentAmber.copy(alpha = 0.12f) else BgInput,
                            contentColor = if (selectedStatus != "ALL") AccentAmber else TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("filter_status_dropdown_btn")
                    ) {
                        Text(
                            text = if (selectedStatus == "ALL") "وضعیت مصوبه" else currentStatusLabel.take(16),
                            fontSize = 9.5.sp,
                            fontWeight = if (selectedStatus != "ALL") FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    DropdownMenu(
                        expanded = statusDropdownExpanded,
                        onDismissRequest = { statusDropdownExpanded = false }
                    ) {
                        statusOptions.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 10.5.sp, fontWeight = if (selectedStatus == code) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onStatusChange(code)
                                    statusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 3. Responsible Member (عضو مسئول) Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { memberDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (selectedMember != "ALL") AccentPurpleLight else BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedMember != "ALL") AccentPurple.copy(alpha = 0.14f) else BgInput,
                            contentColor = if (selectedMember != "ALL") AccentPurpleLight else TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("filter_member_dropdown_btn")
                    ) {
                        Text(
                            text = if (selectedMember == "ALL") "عضو مسئول" else selectedMember.take(14),
                            fontSize = 9.5.sp,
                            fontWeight = if (selectedMember != "ALL") FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    DropdownMenu(
                        expanded = memberDropdownExpanded,
                        onDismissRequest = { memberDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("همه اعضای هیئت‌مدیره", fontSize = 10.5.sp) },
                            onClick = {
                                onMemberChange("ALL")
                                memberDropdownExpanded = false
                            }
                        )
                        availableMembers.forEach { memberName ->
                            DropdownMenuItem(
                                text = { Text(memberName, fontSize = 10.5.sp, fontWeight = if (selectedMember == memberName) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onMemberChange(memberName)
                                    memberDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Quick Scope Chips Row (محدوده جلسه)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("محدوده جلسه:", color = TextSub, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                scopeOptions.forEach { (code, label) ->
                    val isSelected = selectedScope == code
                    FilterChip(
                        selected = isSelected,
                        onClick = { onScopeChange(code) },
                        label = {
                            Text(
                                text = if (code == "ALL") "همه محدوده‌ها" else label,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AccentCyan,
                            containerColor = BgInput,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AccentCyan else BorderSubtle,
                            selectedBorderColor = AccentCyan
                        ),
                        modifier = Modifier.testTag("quick_scope_chip_$code")
                    )
                }
            }

            // Quick Resolution Status Chips & Reset Button Row (وضعیت مصوبه)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("وضعیت مصوبه:", color = TextSub, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)

                listOf(
                    Triple("ALL", "همه", BorderSubtle),
                    Triple("IN_PROGRESS", "⏳ در حال انجام", AccentCyan),
                    Triple("COMPLETED", "✅ تکمیل‌شده", AccentEmeraldLight),
                    Triple("DEFERRED", "🚨 معوق", AccentAmber)
                ).forEach { (code, label, chipColor) ->
                    val isSelected = selectedStatus == code
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStatusChange(code) },
                        label = { Text(label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.2f),
                            selectedLabelColor = chipColor,
                            containerColor = BgInput,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) chipColor else BorderSubtle,
                            selectedBorderColor = chipColor
                        ),
                        modifier = Modifier.testTag("quick_status_chip_$code")
                    )
                }

                if (hasActiveFilter) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onResetFilters,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("reset_advanced_filters_btn")
                    ) {
                        Icon(Icons.Default.FilterAltOff, contentDescription = null, tint = AccentRose, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("حذف فیلترها", color = AccentRose, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
