package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.MeetingSession
import com.example.data.Member
import com.example.ui.theme.*
import com.example.util.QrCodeAnalyzer
import com.example.util.QrCodeGenerator
import java.util.concurrent.Executors

enum class QrDialogTab(val title: String) {
    SCAN("📷 اسکن بارکد"),
    GENERATE("🏷️ تولید کارت ورود (Pass)")
}

@Composable
fun QrCheckInDialog(
    session: MeetingSession,
    onDismiss: () -> Unit,
    onCheckInByQr: (String) -> Pair<Boolean, String>,
    onCheckInById: (String) -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(QrDialogTab.SCAN) }
    var scanResultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessResult by remember { mutableStateOf(true) }

    // Selected member for generating pass
    var selectedMemberIndex by remember {
        mutableStateOf(if (session.members.isNotEmpty()) 0 else -1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, BorderActive, RoundedCornerShape(20.dp)),
            color = BgCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "سامانه هوشمند پذیرش QR",
                                color = TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = "ثبت بلادرنگ حضور اعضا در جلسه",
                                color = TextSub,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgInput)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QrDialogTab.values().forEach { tab ->
                        val isSelected = tab == currentTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentPurple else Color.Transparent)
                                .clickable { currentTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                color = if (isSelected) Color.White else TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                when (currentTab) {
                    QrDialogTab.SCAN -> {
                        ScanQrTabContent(
                            session = session,
                            lastResultMessage = scanResultMessage,
                            isSuccessResult = isSuccessResult,
                            onScanDetected = { payload ->
                                val (ok, msg) = onCheckInByQr(payload)
                                scanResultMessage = msg
                                isSuccessResult = ok
                            },
                            onFastCheckIn = { member ->
                                onCheckInById(member.id)
                                scanResultMessage = "حضور «${member.name}» با موفقیت ثبت شد."
                                isSuccessResult = true
                            }
                        )
                    }
                    QrDialogTab.GENERATE -> {
                        GenerateQrTabContent(
                            session = session,
                            selectedIndex = selectedMemberIndex,
                            onSelectIndex = { selectedMemberIndex = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanQrTabContent(
    session: MeetingSession,
    lastResultMessage: String?,
    isSuccessResult: Boolean,
    onScanDetected: (String) -> Unit,
    onFastCheckIn: (Member) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "مجوز دوربین رد شد. می‌توانید از دکمه‌های پذیرش سریع استفاده کنید.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Last Scan Feedback Banner
        AnimatedVisibility(visible = lastResultMessage != null) {
            lastResultMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccessResult) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSuccessResult) AccentEmerald else AccentRose
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccessResult) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSuccessResult) AccentEmeraldLight else AccentRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = msg,
                            color = if (isSuccessResult) AccentEmeraldLight else AccentRose,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Camera viewfinder or Permission Request Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.5.dp, BorderActive, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val cameraExecutor = Executors.newSingleThreadExecutor()

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { text ->
                                            previewView.post {
                                                onScanDetected(text)
                                            }
                                        })
                                    }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalyzer
                                )
                            } catch (e: Exception) {
                                // Camera preview unavailable in some emulator configurations
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Laser scan targeting frame overlay
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .border(2.dp, AccentCyan, RoundedCornerShape(12.dp))
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(36.dp))
                    Text("جهت اسکن زنده با دوربین نیاز به مجوز است", color = TextMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("اعطای مجوز دوربین", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fast Simulator Check-In Bar (especially useful for testing in emulator)
        Text(
            text = "⚡ پذیرش سریع اعضا (تست و ثبت فوری):",
            color = AccentCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val absentMembers = session.members.filter { !it.isPresent }
            if (absentMembers.isEmpty()) {
                item {
                    Text("تمامی اعضای این جلسه حاضر می‌باشند. ✅", color = AccentEmeraldLight, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(absentMembers) { member ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgInput),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFastCheckIn(member) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(member.name, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${member.title} • ${member.unit}", color = TextSub, fontSize = 10.sp)
                            }
                            Button(
                                onClick = { onFastCheckIn(member) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentEmerald.copy(alpha = 0.2f),
                                    contentColor = AccentEmeraldLight
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("ثبت حضور +", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenerateQrTabContent(
    session: MeetingSession,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val context = LocalContext.current
    var dropdownExpanded by remember { mutableStateOf(false) }

    val currentMember = if (selectedIndex in session.members.indices) session.members[selectedIndex] else null

    val qrContent = remember(selectedIndex, session.id, currentMember) {
        if (currentMember != null) {
            QrCodeGenerator.createMemberCheckInPayload(
                sessionId = session.id,
                memberId = currentMember.id,
                name = currentMember.name,
                title = currentMember.title
            )
        } else {
            "ATLAS_MEETING_PASS:${session.id}:${session.title}"
        }
    }

    val qrBitmap = remember(qrContent) {
        QrCodeGenerator.generateQrBitmap(
            content = qrContent,
            sizePx = 400,
            darkColor = android.graphics.Color.BLACK,
            lightColor = android.graphics.Color.WHITE
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Member selector dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = currentMember?.let { "${it.name} (${it.title})" } ?: "کارت عمومی جلسه",
                onValueChange = {},
                readOnly = true,
                label = { Text("انتخاب عضو جهت صدور کارت ورود", fontSize = 10.5.sp) },
                trailingIcon = {
                    IconButton(onClick = { dropdownExpanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentPurpleLight)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dropdownExpanded = true },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPurple,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgInput,
                    unfocusedContainerColor = BgInput
                ),
                shape = RoundedCornerShape(10.dp)
            )

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(BgCard)
            ) {
                session.members.forEachIndexed { i, m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${m.name} - ${m.title} ${if (m.isPresent) "✅" else "⏳"}",
                                color = TextMain,
                                fontSize = 11.5.sp
                            )
                        },
                        onClick = {
                            onSelectIndex(i)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // QR Code Display Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .size(200.dp)
                .border(2.dp, AccentPurple, RoundedCornerShape(14.dp))
                .padding(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "بارکد ورود QR",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = AccentPurple)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Badge & Attendee info
        if (currentMember != null) {
            Text(
                text = currentMember.name.ifBlank { "عضو سازمانی" },
                color = TextMain,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
            Text(
                text = "${currentMember.title} • ${currentMember.unit}",
                color = AccentPurpleLight,
                fontSize = 11.sp
            )
        } else {
            Text(
                text = session.title.ifBlank { "کارت ورود جلسه" },
                color = TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Share pass text button
        OutlinedButton(
            onClick = {
                val shareText = if (currentMember != null) {
                    "کارت ورود به جلسه «${session.title}»\nنام: ${currentMember.name}\nسمت: ${currentMember.title}\nشناسه QR:\n$qrContent"
                } else {
                    "کارت جلسه «${session.title}»:\n$qrContent"
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "ارسال کارت ورود QR"))
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ارسال و اشتراک بارکد ورود", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
