package com.example.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AtlasHeader(
    onLoadScenario: (String) -> Unit,
    onArchive: () -> Unit,
    onNewSession: () -> Unit,
    saveStatusText: String = "🟢 ذخیره‌شده"
) {
    var scenarioMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = BgCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentPurple, Color(0xFF581C87))
                                )
                            )
                            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }

                    Column {
                        Text(
                            text = "اطلس جلسه ۰۱",
                            color = TextMain,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "AI Enterprise Master OS",
                            color = AccentPurpleLight,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    }
                }

                // Quick Status & Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentPurple.copy(alpha = 0.15f))
                            .border(1.dp, AccentPurple.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = saveStatusText,
                            color = AccentPurpleLight,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Archive button
                    IconButton(
                        onClick = onArchive,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentEmerald.copy(alpha = 0.15f))
                            .testTag("archive_quick_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "بایگانی جلسه",
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // New session button
                    IconButton(
                        onClick = onNewSession,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentRose.copy(alpha = 0.15f))
                            .testTag("new_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "جلسه جدید",
                            tint = AccentRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle & Scenario Quick Loader Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "حاکمیت شرکتی • لجستیک 5PL • صوت زنده • Gemini AI",
                    color = TextSub,
                    fontSize = 10.sp
                )

                Box {
                    Button(
                        onClick = { scenarioMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgInput,
                            contentColor = AccentPurpleLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("scenario_dropdown_btn")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بارگذاری سناریو", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = scenarioMenuExpanded,
                        onDismissRequest = { scenarioMenuExpanded = false },
                        modifier = Modifier.background(BgCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("🏢 هیئت‌مدیره: زنجیره سرد 5PL", color = TextMain, fontSize = 12.sp) },
                            onClick = {
                                scenarioMenuExpanded = false
                                onLoadScenario("board_cold_chain")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("👥 مصاحبه استخدام مدیر عملیات (HR)", color = TextMain, fontSize = 12.sp) },
                            onClick = {
                                scenarioMenuExpanded = false
                                onLoadScenario("hr_interview")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📜 قرارداد رسمی تامین پالت و ناوگان", color = TextMain, fontSize = 12.sp) },
                            onClick = {
                                scenarioMenuExpanded = false
                                onLoadScenario("supplier_contract")
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class AtlasTab(val label: String, val icon: ImageVector) {
    SETUP("تنظیمات", Icons.Default.Settings),
    LIVE("بوم زنده", Icons.Default.Mic),
    SPECIAL("ماژول 5PL", Icons.Default.LocalShipping),
    AI("هوش مصنوعی", Icons.Default.AutoAwesome),
    REPORT("صورتجلسه", Icons.Default.Description),
    ARCHIVE("آرشیو", Icons.Default.Folder)
}

@Composable
fun AtlasTabNavigation(
    selectedTab: AtlasTab,
    onTabSelected: (AtlasTab) -> Unit,
    archiveCount: Int = 0
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = BgInput,
        contentColor = AccentPurpleLight,
        edgePadding = 8.dp,
        indicator = {},
        divider = {}
    ) {
        AtlasTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (isSelected) AccentPurple else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (isSelected) AccentPurple else BorderSubtle,
                        RoundedCornerShape(99.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("tab_${tab.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (tab == AtlasTab.ARCHIVE && archiveCount > 0) "${tab.label} ($archiveCount)" else tab.label,
                        color = if (isSelected) Color.White else TextMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuantumPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (title != null || badgeText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            color = TextMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (badgeText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentPurple.copy(alpha = 0.15f))
                                .border(1.dp, AccentPurple.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = AccentPurpleLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgInput),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Visual color-coded badge for resolution/action-item status:
 * - IN_PROGRESS (در حال انجام) -> Cyan
 * - COMPLETED (تکمیل شده) -> Emerald Green
 * - DEFERRED (معوق) -> Amber / Orange
 */
@Composable
fun ResolutionStatusBadge(
    status: com.example.data.ResolutionStatusType,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bgColor = when (status) {
        com.example.data.ResolutionStatusType.COMPLETED -> AccentEmerald.copy(alpha = 0.18f)
        com.example.data.ResolutionStatusType.DEFERRED -> AccentAmber.copy(alpha = 0.18f)
        com.example.data.ResolutionStatusType.IN_PROGRESS -> AccentCyan.copy(alpha = 0.18f)
    }
    val borderColor = when (status) {
        com.example.data.ResolutionStatusType.COMPLETED -> AccentEmerald.copy(alpha = 0.55f)
        com.example.data.ResolutionStatusType.DEFERRED -> AccentAmber.copy(alpha = 0.55f)
        com.example.data.ResolutionStatusType.IN_PROGRESS -> AccentCyan.copy(alpha = 0.55f)
    }
    val textColor = when (status) {
        com.example.data.ResolutionStatusType.COMPLETED -> AccentEmeraldLight
        com.example.data.ResolutionStatusType.DEFERRED -> AccentAmber
        com.example.data.ResolutionStatusType.IN_PROGRESS -> AccentCyan
    }
    val icon = when (status) {
        com.example.data.ResolutionStatusType.COMPLETED -> Icons.Default.CheckCircle
        com.example.data.ResolutionStatusType.DEFERRED -> Icons.Default.Schedule
        com.example.data.ResolutionStatusType.IN_PROGRESS -> Icons.Default.HourglassTop
    }

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else modifier

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = clickableModifier.testTag("resolution_status_badge_${status.code}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.label,
                tint = textColor,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
            Text(
                text = status.label,
                color = textColor,
                fontSize = if (compact) 9.sp else 10.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}


@Composable
fun OwnerSignatureFooter(modifier: Modifier = Modifier) {
    Surface(
        color = BgCard,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "اکوسیستم جامع اطلس جلسه ۰۱ (AI Enterprise Master Build) • پایداری و امنیت ۱۰۰٪ محلی",
                color = TextSub,
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "⚡ طراحی و معماری سیستم:",
                    color = AccentPurpleLight,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "تیم توسعه اطلس جلسه ۰۱",
                    color = TextMain,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
