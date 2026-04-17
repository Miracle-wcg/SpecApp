package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.ui.theme.*
import com.wcg.app.specapp.viewmodel.AppLanguage
import com.wcg.app.specapp.ui.components.DarkTextField
import com.wcg.app.specapp.viewmodel.SpectrometerViewModel
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SetupScreen(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage
    val config = viewModel.config
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.uiMessage) {
        viewModel.uiMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    var serverIp by remember { mutableStateOf(config.serverIp) }
    var tcpPort by remember { mutableStateOf(config.tcpPort.toString()) }
    var udpPort by remember { mutableStateOf(config.udpPort.toString()) }

    var laserFreq by remember { mutableStateOf(config.laserFreq.toString()) }
    var startWave by remember { mutableStateOf(config.params.startWave.toString()) }
    var stopWave by remember { mutableStateOf(config.params.stopWave.toString()) }
    var numScans by remember { mutableStateOf(config.params.numScans.toString()) }
    var numRuns by remember { mutableStateOf(config.params.numRuns.toString()) }

    var resolution by remember { mutableStateOf(config.params.resolution) }
    var firstGain by remember { mutableStateOf(config.params.firstGain) }

    var savePath by remember { mutableStateOf(config.savePath) }
    var timeoutMs by remember { mutableStateOf(config.autoCollect.timeoutMs.toString()) }

    val resolutionOptions = listOf(
        "1 cm-1" to 0.toShort(), "2 cm-1" to 1.toShort(), "4 cm-1" to 2.toShort(), "8 cm-1" to 3.toShort(),
        "16 cm-1" to 4.toShort(), "32 cm-1" to 5.toShort(), "64 cm-1" to 6.toShort(), "128 cm-1" to 7.toShort()
    )

    val gainOptions = listOf(
        "28" to 0.toShort(), "56" to 1.toShort(), "112" to 2.toShort(), "225" to 3.toShort(),
        "450" to 4.toShort(), "900" to 5.toShort(), "1800" to 6.toShort(), "3600" to 7.toShort()
    )

    val fixedHealthGroups = listOf(
        "IR Source", "Metrology", "Electronic", "Detector",
        "Interferometer", "Co-addition", "Firmware"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (lang == AppLanguage.Chinese) "仪器设置" else "Instrument Setup", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        val isReady = viewModel.isBoardOpened
                        val statusColor = if (isReady) Color(0xFF10B981) else WarningOrange
                        Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(6.dp))
                        val statusText = if (isReady) {
                            if (lang == AppLanguage.Chinese) "设备已连接就绪" else "ONLINE"
                        } else {
                            if (lang == AppLanguage.Chinese) "设备未连接" else "OFFLINE"
                        }
                        Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (viewModel.isTcpConnected || viewModel.isBoardOpened) {
                    OutlinedButton(
                        onClick = { viewModel.disconnectHardware() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = BorderStroke(1.dp, DangerRed)
                    ) { Text(if (lang == AppLanguage.Chinese) "断开连接" else "DISCONNECT", fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ConnectionPipelineBanner(viewModel.appLanguage, isTcpOk = viewModel.isTcpConnected, isBoardOk = viewModel.isBoardOpened, isConfigOk = viewModel.isConfigApplied)
            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(bottom = 80.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                    // === 左列：通讯与参数 ===
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                        SetupCard(
                            icon = Icons.Default.Public,
                            title = if (lang == AppLanguage.Chinese) "步骤 1：通讯配置" else "Step 1: TCP Configuration",
                            subtitle = "TCP COMMUNICATION"
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "服务器 IP" else "SERVER IP",
                                    serverIp,
                                    { serverIp = it; config.serverIp = it },
                                    Modifier.weight(1.5f)
                                )
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "TCP 端口" else "TCP PORT",
                                    tcpPort,
                                    { tcpPort = it; it.toIntOrNull()?.let { v -> config.tcpPort = v } },
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.connectTcp() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isTcpConnected) Color(0xFF10B981) else AccentCyan)
                            ) {
                                val txt = if (viewModel.isTcpConnected) {
                                    if (lang == AppLanguage.Chinese) "✓ TCP 已连接" else "✓ TCP Connected"
                                } else {
                                    if (lang == AppLanguage.Chinese) "1. 建立基础TCP连接" else "1. Establish TCP"
                                }
                                Text(txt, fontWeight = FontWeight.Bold, color = if (viewModel.isTcpConnected) TextWhite else BgDark)
                            }
                        }

                        SetupCard(
                            icon = Icons.Default.SettingsInputComponent,
                            title = if (lang == AppLanguage.Chinese) "步骤 2：板卡握手" else "Step 2: Board Initialization",
                            subtitle = "BOARD INIT"
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "板卡名称" else "BOARD NAME",
                                    viewModel.boardName,
                                    { viewModel.boardName = it; config.boardName = it },
                                    Modifier.weight(1.5f)
                                )
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "UDP 端口" else "UDP PORT",
                                    udpPort,
                                    { udpPort = it; it.toIntOrNull()?.let { v -> config.udpPort = v } },
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.openBoard() },
                                enabled = viewModel.isTcpConnected,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isBoardOpened) Color(0xFF10B981) else Color(0xFF2B3648), disabledContainerColor = BgDark)
                            ) {
                                val txt = if (viewModel.isBoardOpened) {
                                    if (lang == AppLanguage.Chinese) "✓ 板卡已就绪" else "✓ Board Ready"
                                } else {
                                    if (lang == AppLanguage.Chinese) "2. 获取板卡信息并打开" else "2. Open Board"
                                }
                                Text(txt, fontWeight = FontWeight.Bold, color = if (viewModel.isTcpConnected) TextWhite else TextMuted)
                            }
                        }

                        SetupCard(
                            icon = Icons.Default.Tune,
                            title = if (lang == AppLanguage.Chinese) "步骤 3：扫描与光学参数" else "Step 3: Parameters",
                            subtitle = "OPTICS"
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "起始波段" else "START WAVE",
                                    startWave,
                                    {
                                        startWave = it; it.toFloatOrNull()
                                        ?.let { v -> config.params.startWave = v }; viewModel.isConfigApplied = false
                                    },
                                    Modifier.weight(1f)
                                )
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "截止波段" else "STOP WAVE",
                                    stopWave,
                                    {
                                        stopWave = it; it.toFloatOrNull()
                                        ?.let { v -> config.params.stopWave = v }; viewModel.isConfigApplied = false
                                    },
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "累加次数" else "NUM SCANS",
                                    numScans,
                                    {
                                        numScans = it; it.toIntOrNull()
                                        ?.let { v -> config.params.numScans = v }; viewModel.isConfigApplied = false
                                    },
                                    Modifier.weight(1f)
                                )
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "运行次数" else "NUM RUNS",
                                    numRuns,
                                    {
                                        numRuns = it; it.toIntOrNull()
                                        ?.let { v -> config.params.numRuns = v }; viewModel.isConfigApplied = false
                                    },
                                    Modifier.weight(1f)
                                )
                                DarkTextField(
                                    if (lang == AppLanguage.Chinese) "激光频率" else "LASER FREQ",
                                    laserFreq,
                                    {
                                        laserFreq = it; it.toDoubleOrNull()
                                        ?.let { v -> config.laserFreq = v }; viewModel.isConfigApplied = false
                                    },
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkDropdownField(if (lang == AppLanguage.Chinese) "分辨率" else "RESOLUTION", resolution, resolutionOptions, { resolution = it; config.params.resolution = it; viewModel.markConfigDirty("Resolution Code", it.toString()) }, Modifier.weight(1f))
                                DarkDropdownField(if (lang == AppLanguage.Chinese) "硬件增益" else "GAIN", firstGain, gainOptions, { firstGain = it; config.params.firstGain = it; viewModel.markConfigDirty("Hardware Gain", it.toString()) }, Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.applyParameters() },
                                enabled = viewModel.isBoardOpened,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isConfigApplied) Color(0xFF10B981) else WarningOrange, disabledContainerColor = BgDark)
                            ) {
                                val btnText = if (viewModel.isConfigApplied) {
                                    if (lang == AppLanguage.Chinese) "✓ 参数已下发就绪" else "✓ Config Applied"
                                } else {
                                    if (lang == AppLanguage.Chinese) "3. 下发参数至硬件并预热" else "3. Apply Parameters"
                                }
                                val txtColor = if (viewModel.isConfigApplied) TextWhite else if (viewModel.isBoardOpened) BgDark else TextMuted
                                Text(btnText, fontWeight = FontWeight.Bold, color = txtColor)
                            }
                        }
                    }

                    // === 右列：身份反馈与监控 ===
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                        SetupCard(
                            icon = Icons.Default.MonitorHeart,
                            title = if (lang == AppLanguage.Chinese) "仪器身份与健康监控" else "Identity & Health",
                            subtitle = "DIAGNOSTICS"
                        ) {
                            InfoRow(if (lang == AppLanguage.Chinese) "服务器 IP" else "Server IP", serverIp)
                            val displayBoardName = viewModel.boardName.ifEmpty { if (lang == AppLanguage.Chinese) "未识别" else "Unknown" }
                            InfoRow(if (lang == AppLanguage.Chinese) "板卡名称" else "Board Name", displayBoardName)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (lang == AppLanguage.Chinese) "硬件传感器" else "HARDWARE SENSORS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { viewModel.checkHealth() },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (lang == AppLanguage.Chinese) "检查健康状态" else "CHECK HEALTH", fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            val report = viewModel.healthReport

                            Column(modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(8.dp)) {
                                fixedHealthGroups.forEachIndexed { index, groupName ->
                                    val groupData = report?.get(groupName) as? Map<*, *>
                                    val isHealthy = groupData?.get("isHealthy") as? Boolean
                                    val stateCode = groupData?.get("state")?.toString() ?: "0"

                                    val statusColor = when {
                                        report == null -> TextMuted
                                        isHealthy == true -> Color(0xFF10B981)
                                        else -> WarningOrange
                                    }
                                    val statusText = when {
                                        report == null -> "WAITING"
                                        isHealthy == true -> "OK"
                                        else -> "ERR:$stateCode"
                                    }

                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(groupName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    if (index < fixedHealthGroups.size - 1) {
                                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        SetupCard(
                            icon = Icons.Default.Folder,
                            title = if (lang == AppLanguage.Chinese) "存储与自动化" else "Storage & Auto",
                            subtitle = "EXPORT"
                        ) {
                            Text(if (lang == AppLanguage.Chinese) "默认导出路径" else "DEFAULT EXPORT PATH", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DarkTextField(
                                    "",
                                    savePath,
                                    { savePath = it; config.savePath = it; config.savePathWindows = it },
                                    Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.logUserAction("Opened Directory Chooser for Default Export Path")
                                        val chooser = JFileChooser(savePath).apply {
                                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                            dialogTitle = if (lang == AppLanguage.Chinese) "选择光谱默认导出目录" else "Choose Export Directory"
                                        }
                                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            val path = chooser.selectedFile.absolutePath + File.separator
                                            savePath = path
                                            viewModel.setExportPathOpt(path)
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)),
                                    modifier = Modifier.height(48.dp).width(48.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextWhite) }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            DarkTextField(
                                if (lang == AppLanguage.Chinese) "采集超时(ms)" else "ACQ TIMEOUT(ms)",
                                timeoutMs,
                                { timeoutMs = it; it.toLongOrNull()?.let { v -> config.autoCollect.timeoutMs = v } },
                                Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(if (lang == AppLanguage.Chinese) "默认数据格式" else "DEFAULT DATA FORMAT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FormatButton("TXT", if (lang == AppLanguage.Chinese) "文本格式" else "Text Format", viewModel.exportFormat == "TXT", Modifier.weight(1f)) { viewModel.setExportFormatOpt("TXT") }
                                FormatButton("SPC", if (lang == AppLanguage.Chinese) "专业格式" else "Galactic Format", viewModel.exportFormat == "SPC", Modifier.weight(1f)) { viewModel.setExportFormatOpt("SPC") }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) { data ->
            Snackbar(snackbarData = data, containerColor = Color(0xFF1E2D4A), contentColor = TextWhite, shape = RoundedCornerShape(8.dp))
        }
    }
}

@Composable
fun SetupCard(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp).padding(bottom = 2.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(subtitle, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun ConnectionPipelineBanner(lang: AppLanguage, isTcpOk: Boolean, isBoardOk: Boolean, isConfigOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(8.dp)).border(1.dp,
            BorderDark, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatusStep(step = if (lang == AppLanguage.Chinese) "1. TCP网络连接" else "1. TCP Network", isActive = isTcpOk)
        Text("━━▶", color = if (isTcpOk) AccentCyan else BorderDark, fontSize = 12.sp)
        StatusStep(step = if (lang == AppLanguage.Chinese) "2. UDP板卡握手" else "2. UDP Handshake", isActive = isBoardOk)
        Text("━━▶", color = if (isBoardOk) AccentCyan else BorderDark, fontSize = 12.sp)
        StatusStep(step = if (lang == AppLanguage.Chinese) "3. 硬件就绪" else "3. Hardware Ready", isActive = isConfigOk)
    }
}

@Composable
fun StatusStep(step: String, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(if (isActive) Color(0xFF10B981) else Color(0xFF334155), RoundedCornerShape(50)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = step, color = if (isActive) TextWhite else TextMuted, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FormatButton(title: String, subtitle: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF1E2D4A) else BgDark)
            .border(1.dp, if (isSelected) AccentCyan else BorderDark, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(subtitle, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
fun DarkDropdownField(
    label: String,
    selectedValue: Short,
    options: List<Pair<String, Short>>,
    onValueChange: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.second == selectedValue }?.first ?: selectedValue.toString()

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        Box {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (expanded) AccentCyan else TextMuted)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderDark,
                    focusedBorderColor = AccentCyan,
                    unfocusedTextColor = TextWhite,
                    focusedTextColor = TextWhite,
                    unfocusedContainerColor = BgDark,
                    focusedContainerColor = BgDark
                ),
                shape = RoundedCornerShape(4.dp)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(PanelBg).border(1.dp, BorderDark)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(option.first, color = TextWhite, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(32.dp))
                                Text("${option.second}", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        },
                        onClick = {
                            onValueChange(option.second)
                            expanded = false
                        },
                        modifier = Modifier.background(if (selectedValue == option.second) Color(0xFF1E2D4A) else Color.Transparent)
                    )
                }
            }
        }
    }
}