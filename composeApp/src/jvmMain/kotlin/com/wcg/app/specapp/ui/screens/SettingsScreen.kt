package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        // ── 采集参数默认值 ───────────────────────────────────────────────────────
        SettingsSection("采集参数默认值") {
            IntSettingField("默认积分时间 (ms)", settings.defaultIntegrationTimeMs, 1, 10000) {
                vm.update { copy(defaultIntegrationTimeMs = it) }
            }
            IntSettingField("默认平均次数", settings.defaultAveraging, 1, 100) {
                vm.update { copy(defaultAveraging = it) }
            }
            IntSettingField("默认平滑点数", settings.defaultSmoothingPoints, 0, 20) {
                vm.update { copy(defaultSmoothingPoints = it) }
            }
            FloatSettingField("默认起始波长 (nm)", settings.defaultWavelengthStart, 100f, 800f) {
                vm.update { copy(defaultWavelengthStart = it) }
            }
            FloatSettingField("默认截止波长 (nm)", settings.defaultWavelengthEnd, 200f, 1200f) {
                vm.update { copy(defaultWavelengthEnd = it) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 串口设置 ─────────────────────────────────────────────────────────────
        SettingsSection("串口设置") {
            IntSettingField("默认波特率", settings.defaultBaudRate, 1200, 921600) {
                vm.update { copy(defaultBaudRate = it) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 显示设置 ─────────────────────────────────────────────────────────────
        SettingsSection("显示设置") {
            SwitchSettingRow("显示网格线", settings.showGrid) {
                vm.update { copy(showGrid = it) }
            }
            SwitchSettingRow("显示峰值标注", settings.showPeakLabels) {
                vm.update { copy(showPeakLabels = it) }
            }
            SwitchSettingRow("Y轴自动缩放", settings.autoscaleY) {
                vm.update { copy(autoscaleY = it) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 文件路径 ─────────────────────────────────────────────────────────────
        SettingsSection("文件路径") {
            StringSettingField("数据存储目录", settings.dataDirectory) {
                vm.update { copy(dataDirectory = it) }
            }
            StringSettingField("导出目录", settings.exportDirectory) {
                vm.update { copy(exportDirectory = it) }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchSettingRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun IntSettingField(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
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
private fun FloatSettingField(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf("%.1f".format(value)) }
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

@Composable
private fun StringSettingField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
