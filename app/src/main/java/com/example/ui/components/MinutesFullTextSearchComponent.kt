package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeetingSession
import com.example.data.ResolutionStatusType
import com.example.data.local.MinutesFtsSearchResult
import com.example.ui.components.ResolutionStatusBadge
import com.example.ui.theme.*

/**
 * Full-Text Search (FTS) UI Component backed by Room Database (`meeting_minutes` + `meeting_minutes_fts`).
 *
 * Implements the user-confirmed capabilities:
 * 1. Indexes & searches strictly across Meeting Title, Resolutions/Notes Text, and Executive Summary.
 * 2. Displays matched context snippet with visual keyword highlighting + direct "بازخوانی صورتجلسه" button.
 * 3. Provides combined filters for Meeting Scope/Type, Resolution Completion Status, and Responsible Member.
 */
@Composable
fun MinutesFullTextSearchComponent(
    ftsQuery: String,
    onFtsQueryChange: (String) -> Unit,
    scopeFilter: String,
    onScopeFilterChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    memberFilter: String,
    onMemberFilterChange: (String) -> Unit,
    availableMembers: List<String>,
    searchResults: List<MinutesFtsSearchResult>,
    onLoadMatchedSession: (MeetingSession) -> Unit,
    onResetAllFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scopeDropdownExpanded by remember { mutableStateOf(false) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }

    val scopeOptions = remember {
        listOf(
            "ALL" to "همه انواع جلسات",
            "internal" to "درون‌سازمانی / هیئت‌مدیره",
            "external_partner" to "شریک استراتژیک (Partner)",
            "external_supplier" to "تأمین‌کننده (Supplier)",
            "external_contract" to "عقد قرارداد رسمی",
            "interview_hr" to "مصاحبه تخصصی (HR)"
        )
    }

    val statusOptions = remember {
        listOf(
            "ALL" to "همه وضعیت‌ها",
            "PENDING" to "⏳ دارای مصوبه در جریان",
            "DONE" to "✅ مصوبات تکمیل‌شده"
        )
    }

    val hasActiveFilter = ftsQuery.isNotBlank() ||
        scopeFilter != "ALL" ||
        statusFilter != "ALL" ||
        memberFilter != "ALL"

    QuantumPanel(
        modifier = modifier.testTag("room_fts_search_panel"),
        title = "🔍 جستجوی تمام‌متن صورتجلسات (Room FTS4)",
        badgeText = "${searchResults.size} صورتجلسه منطبق"
    ) {
        Text(
            text = "کاوش سریع و تمام‌متن در «عنوان جلسه»، «متن مصوبات» و «خلاصه اجرایی» ذخیره‌شده در دیتابیس Room به همراه فیلترهای ترکیبی مدیران:",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- 1. Full-Text Search Query Input ---
        OutlinedTextField(
            value = ftsQuery,
            onValueChange = onFtsQueryChange,
            label = { Text("جستجوی تمام‌متن (واژه، موضوع، مصوبه یا خلاصه اجرایی)...", fontSize = 10.5.sp) },
            placeholder = { Text("مثلاً: زنجیره سرد، بودجه، پالت، سنسور، استخدام...") },
            leadingIcon = {
                Icon(
                    Icons.Default.ManageSearch,
                    contentDescription = "جستجوی تمام‌متن",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (ftsQuery.isNotBlank()) {
                    IconButton(
                        onClick = { onFtsQueryChange("") },
                        modifier = Modifier.testTag("fts_clear_query_btn")
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "پاک کردن عبارت",
                            tint = AccentRose,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("room_fts_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = BgInput,
                unfocusedContainerColor = BgInput
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Topic Suggestion Chips for Instant Executive Search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "کلیدواژه‌های پرتکرار:",
                color = TextSub,
                fontSize = 9.5.sp
            )
            val quickTopics = listOf("زنجیره سرد", "بودجه", "پالت", "سنسور", "برج کنترل", "قرارداد")
            quickTopics.forEach { topic ->
                val isSelected = ftsQuery.equals(topic, ignoreCase = true)
                SuggestionChip(
                    onClick = {
                        onFtsQueryChange(if (isSelected) "" else topic)
                    },
                    label = {
                        Text(
                            text = topic,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgInput,
                        labelColor = if (isSelected) AccentCyan else TextMuted
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = if (isSelected) AccentCyan else BorderSubtle
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. Combined Filters Row (Scope + Responsible Member Dropdowns) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter A: Meeting Scope/Type
            Box(modifier = Modifier.weight(1f)) {
                val activeScopeLabel = scopeOptions.find { it.first == scopeFilter }?.second ?: "همه انواع جلسات"
                OutlinedButton(
                    onClick = { scopeDropdownExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = BgInput,
                        contentColor = if (scopeFilter == "ALL") TextMain else AccentPurpleLight
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (scopeFilter == "ALL") BorderSubtle else AccentPurple
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("fts_scope_filter_btn")
                ) {
                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeScopeLabel,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                }

                DropdownMenu(
                    expanded = scopeDropdownExpanded,
                    onDismissRequest = { scopeDropdownExpanded = false },
                    modifier = Modifier.background(BgCard)
                ) {
                    scopeOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (key == scopeFilter) AccentPurpleLight else TextMain,
                                    fontSize = 11.sp,
                                    fontWeight = if (key == scopeFilter) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onScopeFilterChange(key)
                                scopeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Filter B: Responsible Member / Speaker
            Box(modifier = Modifier.weight(1f)) {
                val activeMemberLabel = if (memberFilter == "ALL") "عضو مسئول: همه اعضا" else "مسئول: $memberFilter"
                OutlinedButton(
                    onClick = { memberDropdownExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = BgInput,
                        contentColor = if (memberFilter == "ALL") TextMain else AccentCyan
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (memberFilter == "ALL") BorderSubtle else AccentCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("fts_member_filter_btn")
                ) {
                    Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeMemberLabel,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                }

                DropdownMenu(
                    expanded = memberDropdownExpanded,
                    onDismissRequest = { memberDropdownExpanded = false },
                    modifier = Modifier.background(BgCard)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "👥 همه اعضای مسئول",
                                color = if (memberFilter == "ALL") AccentCyan else TextMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            onMemberFilterChange("ALL")
                            memberDropdownExpanded = false
                        }
                    )
                    availableMembers.forEach { memberName ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "👤 $memberName",
                                    color = if (memberFilter == memberName) AccentCyan else TextMain,
                                    fontSize = 11.sp
                                )
                            },
                            onClick = {
                                onMemberFilterChange(memberName)
                                memberDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filter C: Resolution Completion Status Chips + Reset Filters Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            statusOptions.forEach { (key, label) ->
                val isSelected = statusFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onStatusFilterChange(key) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentPurple.copy(alpha = 0.25f),
                        selectedLabelColor = Color.White,
                        containerColor = BgInput,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) AccentPurple else BorderSubtle
                    ),
                    modifier = Modifier.testTag("fts_status_chip_${key.lowercase()}")
                )
            }

            if (hasActiveFilter) {
                TextButton(
                    onClick = onResetAllFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("fts_reset_filters_btn")
                ) {
                    Icon(Icons.Default.FilterAltOff, contentDescription = null, tint = AccentRose, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف فیلترها", color = AccentRose, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Highlighted FTS Results List ---
        if (searchResults.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgInput),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fts_empty_results_box")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = TextSub,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "صورتجلسه‌ای منطبق با عبارت یا فیلترهای انتخابی در دیتابیس Room یافت نشد.",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                searchResults.forEach { result ->
                    FtsResultItemCard(
                        result = result,
                        highlightQuery = ftsQuery.trim(),
                        onLoadSession = {
                            result.entity.toMeetingSession()?.let { restored ->
                                onLoadMatchedSession(restored)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FtsResultItemCard(
    result: MinutesFtsSearchResult,
    highlightQuery: String,
    onLoadSession: () -> Unit
) {
    val entity = result.entity

    Card(
        colors = CardDefaults.cardColors(containerColor = BgInput),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlightQuery.isNotBlank()) AccentCyan.copy(alpha = 0.45f) else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fts_result_card_${entity.sessionId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Highlighted Title + Direct Load Session Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildHighlightedAnnotatedString(
                        text = entity.title,
                        query = highlightQuery,
                        baseColor = TextMain,
                        highlightBackground = AccentPurple.copy(alpha = 0.45f),
                        highlightColor = Color.White
                    ),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onLoadSession,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("fts_load_session_btn_${entity.sessionId}")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("بازخوانی جلسه", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val resolutionStatus = when {
                entity.pendingNotesCount == 0 && entity.doneNotesCount > 0 -> ResolutionStatusType.COMPLETED
                entity.pendingNotesCount > 0 && (entity.title.contains("معوق") || entity.responsibleMembersCsv.contains("معوق")) -> ResolutionStatusType.DEFERRED
                else -> ResolutionStatusType.IN_PROGRESS
            }

            // Metadata & Matched Fields Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🗓️ ${entity.date} • ${if (entity.extCompany.isNotBlank()) entity.extCompany else "درون‌سازمانی"}",
                        color = TextSub,
                        fontSize = 10.sp
                    )
                    ResolutionStatusBadge(status = resolutionStatus)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    result.matchedFields.forEach { fieldName ->
                        Surface(
                            color = AccentCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = fieldName,
                                color = AccentCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (result.totalOccurrences > 0) {
                        Surface(
                            color = AccentEmerald.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${result.totalOccurrences} تطابق",
                                color = AccentEmeraldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Highlighted Snippet Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .testTag("fts_snippet_box_${entity.sessionId}")
            ) {
                Column {
                    Text(
                        text = "📌 گزیده منطبق از «${result.primaryMatchSource}»:",
                        color = AccentPurpleLight,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildHighlightedAnnotatedString(
                            text = result.matchedSnippet,
                            query = highlightQuery,
                            baseColor = TextMuted,
                            highlightBackground = AccentCyan.copy(alpha = 0.32f),
                            highlightColor = Color.White
                        ),
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Items Summary Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مصوبات: ${entity.doneNotesCount} انجام‌شده | ${entity.pendingNotesCount} در انتظار اقدام",
                    color = if (entity.hasPendingActions) AccentAmber else AccentEmeraldLight,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (entity.responsibleMembersCsv.isNotBlank()) {
                    Text(
                        text = "مسئولین: ${entity.responsibleMembersCsv.take(40)}",
                        color = TextSub,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Builds an [AnnotatedString] that highlights all case-insensitive occurrences of [query]
 * inside [text].
 */
fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    baseColor: Color,
    highlightBackground: Color,
    highlightColor: Color
): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank() || text.isBlank()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) {
                append(text)
            }
        }
    }

    return buildAnnotatedString {
        var currentIndex = 0
        val qLen = cleanQuery.length
        while (currentIndex < text.length) {
            val matchIndex = text.indexOf(cleanQuery, currentIndex, ignoreCase = true)
            if (matchIndex < 0) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(currentIndex))
                }
                break
            }
            if (matchIndex > currentIndex) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(currentIndex, matchIndex))
                }
            }
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    background = highlightBackground,
                    fontWeight = FontWeight.ExtraBold
                )
            ) {
                append(text.substring(matchIndex, matchIndex + qLen))
            }
            currentIndex = matchIndex + qLen
        }
    }
}
