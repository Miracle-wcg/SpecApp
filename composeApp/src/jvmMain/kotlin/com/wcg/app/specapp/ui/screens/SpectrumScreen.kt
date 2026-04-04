package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.data.DeviceManager
import com.wcg.app.specapp.data.model.DisplayMode
import com.wcg.app.specapp.ui.components.SpectrumChart

@Composable
fun SpectrumScreen(vm: SpectrumViewModel) {
    val config by vm.config.collectAsState()
    val currentSpectrum by vm.currentSpectrum.collectAsState()
    val darkSpectrum by vm.darkSpectrum.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val status by vm.status.collectAsState()
    val connectionState by vm.connectionState.collectAsState()
    val displayMode by vm.displayMode.collectAsState()
    val showGrid by vm.showGrid.collectAsState()
    val showPeaks by vm.showPeaks.collectAsState()
    val autoscaleY by vm.autoscaleY.collectAsState()

    val isConnected = connectionState == DeviceManager.ConnectionState.CONNECTED

    Row(modifier = Modifier.fillMaxSize()) {
        // ── chart area ──────────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            // toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (isScanning) vm.stopScan() else vm.startContinuousScan() },
                    enabled = isConnected,
                    colors = if (isScanning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isScanning) "■ 停止" else "▶ 连续")
                }
                OutlinedButton(onClick = vm::singleScan, enabled = isConnected && !isScanning) {
                    Text("▷ 单次")
                }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = vm::captureDark, enabled = isConnected && !isScanning) {
                    Text("暗背景")
                }
                OutlinedButton(onClick = vm::captureReference, enabled = isConnected && !isScanning) {
                    Text("参考")
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { vm.saveCurrentSpectrum() },
                    enabled = currentSpectrum != null
                ) { Text("💾 保存") }

                // display mode selector
                DisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = displayMode == mode,
                        onClick = { vm.setDisplayMode(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) }
                    )
                }
            }

            // status bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (darkSpectrum != null) {
                    AssistChip(
                        onClick = vm::clearDark,
                        label = { Text("暗背景 ✓", fontSize = 11.sp) }
                    )
                }
                val refSpectrum by vm.referenceSpectrum.collectAsState()
                if (refSpectrum != null) {
                    AssistChip(
                        onClick = vm::clearReference,
                        label = { Text("参考 ✓", fontSize = 11.sp) }
                    )
                }
            }

            // chart
            val spectraList = vm.spectraToDisplay()
            if (spectraList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "暂无光谱数据",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        if (!isConnected) {
                            Text(
                                "请先在「设备」页面连接光谱仪",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                SpectrumChart(
                    spectra = spectraList,
                    darkSpectrum = if (config.darkSubtraction) darkSpectrum else null,
                    showGrid = showGrid,
                    showPeaks = showPeaks,
                    autoscaleY = autoscaleY,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }

        // ── right parameter panel ───────────────────────────────────────────────
        Surface(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("参数设置", style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()

                IntField("积分时间 (ms)", config.integrationTimeMs, 1, 10000) { v ->
                    vm.updateConfig { copy(integrationTimeMs = v) }
                }
                IntField("平均次数", config.averaging, 1, 100) { v ->
                    vm.updateConfig { copy(averaging = v) }
                }
                IntField("平滑点数", config.smoothingPoints, 0, 20) { v ->
                    vm.updateConfig { copy(smoothingPoints = v) }
                }
                FloatField("起始波长 (nm)", config.wavelengthStart, 100f, 800f) { v ->
                    vm.updateConfig { copy(wavelengthStart = v) }
                }
                FloatField("截止波长 (nm)", config.wavelengthEnd, 200f, 1200f) { v ->
                    vm.updateConfig { copy(wavelengthEnd = v) }
                }

                HorizontalDivider()
                Text("显示选项", style = MaterialTheme.typography.titleSmall)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.darkSubtraction, onCheckedChange = { vm.updateConfig { copy(darkSubtraction = it) } })
                    Text("扣除暗背景", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showGrid, onCheckedChange = { vm.toggleGrid() })
                    Text("显示网格", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showPeaks, onCheckedChange = { vm.togglePeaks() })
                    Text("标记峰值", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoscaleY, onCheckedChange = { vm.toggleAutoscale() })
                    Text("Y轴自动缩放", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                OutlinedButton(
                    onClick = vm::clearOverlays,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("清除叠加光谱") }
            }
        }
    }
}

@Composable
private fun IntField(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.let { v -> if (v in min..max) onChange(v) }
        },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FloatField(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.let { v -> if (v in min..max) onChange(v) }
        },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
