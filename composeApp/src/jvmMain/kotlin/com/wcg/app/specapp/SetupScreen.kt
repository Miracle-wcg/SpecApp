package com.wcg.app.specapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SetupScreen(viewModel: SpectrometerViewModel) {
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
    var boardName by remember { mutableStateOf(config.boardName) }
    var laserFreq by remember { mutableStateOf(config.laserFreq.toString()) }
    var startWave by remember { mutableStateOf(config.params.startWave.toString()) }
    var stopWave by remember { mutableStateOf(config.params.stopWave.toString()) }
    var numScans by remember { mutableStateOf(config.params.numScans.toString()) }
    var numRuns by remember { mutableStateOf(config.params.numRuns.toString()) }
    var resolution by remember { mutableStateOf(config.params.resolution.toString()) }

    // 仅保留 First Gain
    var firstGain by remember { mutableStateOf(config.params.firstGain) }

    var savePath by remember { mutableStateOf(config.savePath) }
    var timeoutMs by remember { mutableStateOf(config.autoCollect.timeoutMs.toString()) }

    // 【优化点 3】：按图示倒推增益值：3600(7), 1800(6), 900(5), 450(4), 225(3), 112(2), 56(1), 28(0)
    val gainOptions = listOf(
        "1.00" to 0.toShort(),
        "3.01" to 1.toShort(),
        "9.06" to 2.toShort(),
        "27.13" to 3.toShort(),
        "80.68" to 4.toShort(),
        "237.84" to 5.toShort(),
        "671.41" to 6.toShort(),
        "3600.00" to 7.toShort()
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("仪器设置 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("/ Instrument Setup", color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        val isReady = viewModel.isBoardOpened
                        val statusColor = if (isReady) Color(0xFF10B981) else WarningOrange
                        Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isReady) "设备已连接就绪 (ONLINE)" else "设备未连接 (OFFLINE)", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (viewModel.isTcpConnected || viewModel.isBoardOpened) {
                    OutlinedButton(
                        onClick = { viewModel.disconnectHardware() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = BorderStroke(1.dp, DangerRed)
                    ) {
                        Text("断开连接设备", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ConnectionPipelineBanner(isTcpOk = viewModel.isTcpConnected, isBoardOk = viewModel.isBoardOpened, isConfigOk = viewModel.isConfigApplied)
            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(bottom = 80.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                    // === 左列：通讯与参数 ===
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                        SetupCard(title = "🌐 步骤 1：通讯配置 (TCP)", subtitle = "TCP COMMUNICATION") {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField("SERVER IP", serverIp, { serverIp = it; config.serverIp = it }, Modifier.weight(1.5f))
                                DarkTextField("TCP PORT", tcpPort, { tcpPort = it; it.toIntOrNull()?.let { v -> config.tcpPort = v } }, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.connectTcp() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isTcpConnected) Color(0xFF10B981) else AccentCyan)
                            ) {
                                Text(if (viewModel.isTcpConnected) "✓ TCP 已连接" else "1. 建立基础 TCP 连接", fontWeight = FontWeight.Bold, color = if (viewModel.isTcpConnected) TextWhite else BgDark)
                            }
                        }

                        SetupCard(title = "📡 步骤 2：板卡握手 (UDP)", subtitle = "BOARD INITIALIZATION") {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField("BOARD NAME", boardName, { boardName = it; config.boardName = it }, Modifier.weight(1.5f))
                                DarkTextField("UDP PORT", udpPort, { udpPort = it; it.toIntOrNull()?.let { v -> config.udpPort = v } }, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.openBoard() },
                                enabled = viewModel.isTcpConnected,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isBoardOpened) Color(0xFF10B981) else Color(0xFF2B3648), disabledContainerColor = BgDark)
                            ) {
                                Text(if (viewModel.isBoardOpened) "✓ 板卡已就绪" else "2. 获取板卡信息并打开", fontWeight = FontWeight.Bold, color = if (viewModel.isTcpConnected) TextWhite else TextMuted)
                            }
                        }

                        SetupCard(title = "☷ 步骤 3：扫描与光学参数", subtitle = "PARAMETERS & OPTICS") {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField("START WAVE", startWave, { startWave = it; it.toFloatOrNull()?.let { v -> config.params.startWave = v } }, Modifier.weight(1f))
                                DarkTextField("STOP WAVE", stopWave, { stopWave = it; it.toFloatOrNull()?.let { v -> config.params.stopWave = v } }, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField("NUM SCANS (累加次数)", numScans, { numScans = it; it.toIntOrNull()?.let { v -> config.params.numScans = v } }, Modifier.weight(1f))
                                DarkTextField("NUM RUNS", numRuns, { numRuns = it; it.toIntOrNull()?.let { v -> config.params.numRuns = v } }, Modifier.weight(1f))
                                DarkTextField("LASER FREQ", laserFreq, { laserFreq = it; it.toDoubleOrNull()?.let { v -> config.laserFreq = v } }, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 【优化点 1&2】：移除 Second Gain，并将增益改为美化后的下拉选框
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DarkTextField("RESOLUTION", resolution, { resolution = it; it.toShortOrNull()?.let { v -> config.params.resolution = v } }, Modifier.weight(1f))
                                DarkDropdownField("GAIN (增益)", firstGain, gainOptions, { firstGain = it; config.params.firstGain = it }, Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.applyParameters() },
                                enabled = viewModel.isBoardOpened,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isConfigApplied) Color(0xFF10B981) else WarningOrange, disabledContainerColor = BgDark)
                            ) {
                                Text(if (viewModel.isConfigApplied) "✓ 参数已下发就绪" else "3. 下发参数至硬件并预热", fontWeight = FontWeight.Bold, color = if (viewModel.isConfigApplied) TextWhite else if (viewModel.isBoardOpened) BgDark else TextMuted)
                            }
                        }
                    }

                    // === 右列：身份反馈与监控 ===
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                        SetupCard(title = "🏥 仪器身份与健康监控", subtitle = "HEALTH & DIAGNOSTICS") {

                            InfoRow("Instrument Type", viewModel.instrumentType)
                            InfoRow("Firmware Version", viewModel.firmwareVersion)

                            val sysMeta = viewModel.systemMetadata
                            if (sysMeta != null) {
                                val configSetup = sysMeta["Configuration Setup"]
                                val valId = sysMeta["Validation ID"]
                                val valState = sysMeta["Validation State"]
                                val usbConn = sysMeta["USB connection Flag"]
                                val usbAcc = sysMeta["USB Accessory Type"]

                                if (configSetup != null) InfoRow("Config Setup", configSetup)
                                if (valId != null) InfoRow("Validation ID", valId)
                                if (valState != null) InfoRow("Validation State", valState)
                                if (usbConn != null) InfoRow("USB Connection", usbConn)
                                if (usbAcc != null) InfoRow("USB Accessory", usbAcc)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("HARDWARE SENSORS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { viewModel.checkHealth() },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648))
                                ) {
                                    Text("⟲ CHECK HEALTH", fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            val report = viewModel.healthReport
                            if (report == null) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp).border(1.dp, BorderDark, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                    Text("暂无诊断数据，请打开板卡或手动刷新", color = TextMuted, fontSize = 12.sp)
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(8.dp)) {
                                    report.forEach { (groupName, groupDataObj) ->
                                        val groupData = groupDataObj as? Map<*, *> ?: return@forEach
                                        val isHealthy = groupData["isHealthy"] as? Boolean ?: true
                                        val stateCode = groupData["state"]?.toString() ?: "0"
                                        val details = groupData["details"] as? Map<*, *> ?: emptyMap<String, String>()

                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(6.dp).background(if (isHealthy) Color(0xFF10B981) else WarningOrange, RoundedCornerShape(50)))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(groupName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(if (isHealthy) "OK" else "ERR:$stateCode", color = if (isHealthy) Color(0xFF10B981) else WarningOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (details.isNotEmpty()) {
                                            Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                                                details.forEach { (itemName, itemState) ->
                                                    val itemStateStr = itemState?.toString() ?: "0"
                                                    val isItemOk = itemStateStr == "0"
                                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("└ $itemName", color = TextMuted, fontSize = 10.sp)
                                                        Text(if(isItemOk) "OK" else "WARN($itemStateStr)", color = if(isItemOk) TextMuted else WarningOrange, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        SetupCard(title = "📁 存储与自动化", subtitle = "STORAGE") {
                            Text("DEFAULT EXPORT PATH (默认导出路径)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DarkTextField("", savePath, { savePath = it; config.savePath = it; config.savePathWindows = it }, Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val chooser = JFileChooser(savePath).apply {
                                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                            dialogTitle = "选择光谱默认导出目录"
                                        }
                                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            val path = chooser.selectedFile.absolutePath + File.separator
                                            savePath = path
                                            config.savePath = path
                                            config.savePathWindows = path
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)),
                                    modifier = Modifier.height(56.dp).padding(top = 8.dp)
                                ) { Text("📁") }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            DarkTextField("ACQUISITION TIMEOUT (超时 ms)", timeoutMs, { timeoutMs = it; it.toLongOrNull()?.let { v -> config.autoCollect.timeoutMs = v } }, Modifier.fillMaxWidth())

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("DEFAULT DATA FORMAT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FormatBox(".SPC", "High density binary", viewModel.exportFormat == "SPC", Modifier.weight(1f)) { viewModel.exportFormat = "SPC" }
                                FormatBox(".TXT", "Human readable", viewModel.exportFormat == "TXT", Modifier.weight(1f)) { viewModel.exportFormat = "TXT" }
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

// -------------------------------------------------------------------------
// 核心自定义组件区 (整合优化后的下拉框)
// -------------------------------------------------------------------------

@Composable
fun ConnectionPipelineBanner(isTcpOk: Boolean, isBoardOk: Boolean, isConfigOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatusStep(step = "1. TCP 网络连接", isActive = isTcpOk)
        Text("━━▶", color = if (isTcpOk) AccentCyan else BorderDark, fontSize = 12.sp)
        StatusStep(step = "2. UDP 板卡握手", isActive = isBoardOk)
        Text("━━▶", color = if (isBoardOk) AccentCyan else BorderDark, fontSize = 12.sp)
        StatusStep(step = "3. 硬件就绪", isActive = isConfigOk)
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

/**
 * 【完整对齐版】：使用 OutlinedTextField 保证与 DarkTextField 高度 100% 统一
 */
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
            // 使用与 DarkTextField 完全相同的 OutlinedTextField 组件来保证高度与样式统一
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true, // 只读，禁止输入
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                trailingIcon = {
                    Text("▾", color = if (expanded) AccentCyan else TextMuted, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
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

            // 覆盖一层透明的 Box 来拦截点击事件，触发下拉菜单
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )

            // 下拉菜单
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(PanelBg).border(1.dp, BorderDark)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(option.first, color = TextWhite, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(32.dp))
                                // 右侧高亮显示映射的数值 Value
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