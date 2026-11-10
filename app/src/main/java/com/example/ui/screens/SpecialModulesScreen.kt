package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HrData
import com.example.data.MeetingSession
import com.example.data.OpsData
import com.example.ui.components.QuantumPanel
import com.example.ui.theme.*

@Composable
fun SpecialModulesScreen(
    session: MeetingSession,
    onUpdateOps: (OpsData) -> Unit,
    onUpdateHr: (HrData) -> Unit
) {
    val ops = session.opsData
    val hr = session.hrData

    var coldChainDropdownExpanded by remember { mutableStateOf(false) }
    var hrDecisionDropdownExpanded by remember { mutableStateOf(false) }

    val coldChainOptions = listOf(
        "پایدار و منطبق با استاندارد (2°C تا 8°C)",
        "نیازمند کالیبراسیون سنسورها",
        "گزارش انحراف دمایی در مسیر"
    )

    val hrDecisions = listOf(
        "تایید قطعی جهت استخدام (Strong Hire)",
        "تایید مشروط سه ماهه (Conditional)",
        "نیازمند مصاحبه تخصصی مجدد",
        "عدم تایید (Reject)"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "ماژول‌های تخصصی: لجستیک 5PL، مالی و منابع انسانی",
                color = TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "اطلاعات این بخش در صورت فعال بودن تیک، مستقیماً در جدول صورتجلسه رسمی درج می‌شوند.",
                color = TextSub,
                fontSize = 11.sp
            )
        }

        // --- 1. 5PL Cold-Chain & Finance Module ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚛 ۱. پنل شاخص‌های لجستیک 5PL و مالی",
                            color = AccentEmeraldLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "درج در صورتجلسه",
                                color = if (ops.enabled) AccentEmeraldLight else TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = ops.enabled,
                                onCheckedChange = { onUpdateOps(ops.copy(enabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentEmerald
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fleet KPI & Cold-Chain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ops.fleetEff,
                            onValueChange = { onUpdateOps(ops.copy(fleetEff = it)) },
                            label = { Text("بهره‌وری ناوگان (Fleet KPI)", fontSize = 10.sp) },
                            placeholder = { Text("مثلاً: 96.4% تحویل موفق") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentEmerald,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Cold Chain status dropdown
                        Box(modifier = Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = ops.coldChain,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("پایداری زنجیره سرد", fontSize = 10.sp) },
                                trailingIcon = {
                                    IconButton(onClick = { coldChainDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentEmeraldLight)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { coldChainDropdownExpanded = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentEmerald,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = BgInput,
                                    unfocusedContainerColor = BgInput
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            DropdownMenu(
                                expanded = coldChainDropdownExpanded,
                                onDismissRequest = { coldChainDropdownExpanded = false },
                                modifier = Modifier.background(BgCard)
                            ) {
                                coldChainOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, color = TextMain, fontSize = 11.5.sp) },
                                        onClick = {
                                            coldChainDropdownExpanded = false
                                            onUpdateOps(ops.copy(coldChain = opt))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pallet Model & Budget
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ops.palletModel,
                            onValueChange = { onUpdateOps(ops.copy(palletModel = it)) },
                            label = { Text("ظرفیت انبارش و مدل پالت", fontSize = 10.sp) },
                            placeholder = { Text("تخصیص ۱۲۰۰ پالت دارویی") },
                            modifier = Modifier.weight(1.1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentEmerald,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = ops.budget,
                            onValueChange = { onUpdateOps(ops.copy(budget = it)) },
                            label = { Text("بودجه مصوب / سقف تعرفه", fontSize = 10.sp) },
                            placeholder = { Text("۵,۵۰۰,۰۰۰,۰۰۰ ریال") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentEmerald,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Control Tower summary
                    OutlinedTextField(
                        value = ops.summary,
                        onValueChange = { onUpdateOps(ops.copy(summary = it)) },
                        label = { Text("دستورات اجرایی برج کنترل (Control Tower)", fontSize = 10.sp) },
                        placeholder = { Text("یکپارچه‌سازی مسیرهای توزیع با سامانه نظارت TTAC...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentEmerald,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // --- 2. HR Assessment & Committee Module ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BgCard),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧬 ۲. پنل ارزیابی تخصصی کاندیدا و منابع انسانی",
                            color = AccentPurpleLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "درج در صورتجلسه",
                                color = if (hr.enabled) AccentPurpleLight else TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = hr.enabled,
                                onCheckedChange = { onUpdateHr(hr.copy(enabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentPurple
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Candidate Name & Position
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hr.candidate,
                            onValueChange = { onUpdateHr(hr.copy(candidate = it)) },
                            label = { Text("نام کاندیدا / فرد ارزیابی‌شونده", fontSize = 10.sp) },
                            placeholder = { Text("نام و نام خانوادگی...") },
                            modifier = Modifier.weight(1.1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = hr.position,
                            onValueChange = { onUpdateHr(hr.copy(position = it)) },
                            label = { Text("جایگاه شغلی (Position)", fontSize = 10.sp) },
                            placeholder = { Text("سرپرست عملیات انبار") },
                            modifier = Modifier.weight(1f),
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

                    // Score & Decision
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hr.score,
                            onValueChange = { onUpdateHr(hr.copy(score = it)) },
                            label = { Text("امتیاز کل شایستگی (از ۱۰۰)", fontSize = 10.sp) },
                            placeholder = { Text("مثلاً: 92 از 100") },
                            modifier = Modifier.weight(0.9f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgInput,
                                unfocusedContainerColor = BgInput
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Committee Decision Dropdown
                        Box(modifier = Modifier.weight(1.3f)) {
                            OutlinedTextField(
                                value = hr.decision,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("رأی نهایی کمیته جذب", fontSize = 10.sp) },
                                trailingIcon = {
                                    IconButton(onClick = { hrDecisionDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hrDecisionDropdownExpanded = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentPurple,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = BgInput,
                                    unfocusedContainerColor = BgInput
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            DropdownMenu(
                                expanded = hrDecisionDropdownExpanded,
                                onDismissRequest = { hrDecisionDropdownExpanded = false },
                                modifier = Modifier.background(BgCard)
                            ) {
                                hrDecisions.forEach { dec ->
                                    DropdownMenuItem(
                                        text = { Text(dec, color = TextMain, fontSize = 11.5.sp) },
                                        onClick = {
                                            hrDecisionDropdownExpanded = false
                                            onUpdateHr(hr.copy(decision = dec))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Qualitative Notes
                    OutlinedTextField(
                        value = hr.notes,
                        onValueChange = { onUpdateHr(hr.copy(notes = it)) },
                        label = { Text("جمع‌بندی کیفی و ملاحظات ارزیابان", fontSize = 10.sp) },
                        placeholder = { Text("نقاط قوت، تسلط عملیاتی، تسلط بر WMS و RFID...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgInput,
                            unfocusedContainerColor = BgInput
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
