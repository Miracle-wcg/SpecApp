package com.wcg.app.specapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(viewModel: SpectrometerViewModel? = null) {
    // 1. 动态运行时长
    var uptimeSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            uptimeSeconds++
        }
    }
    val hours = uptimeSeconds / 3600
    val minutes = (uptimeSeconds % 3600) / 60
    val seconds = uptimeSeconds % 60
    val uptimeString = String.format("%02d : %02d : %02d", hours, minutes, seconds)

    // 2. 双向数据绑定：与 ViewModel 的核心采集参数联动
    var scanCount by remember { mutableStateOf(viewModel?.config?.params?.numScans ?: 16) }
    var currentFormat by remember { mutableStateOf(viewModel?.exportFormat ?: "SPC") }

    val isConnected = viewModel?.isBoardOpened == true
    val connectionState = if (isConnected) "在线 (ACTIVE)" else "离线 (OFFLINE)"
    val connectionColor = if (isConnected) AccentCyan else TextMuted

    Column(modifier = Modifier.fillMaxSize()) {
        // --- 顶部标题 ---
        Row(verticalAlignment = Alignment.Bottom) {
            Text("系统设置 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("SETTINGS ", color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
            Text("SPECTRAL CORE CONTROL CENTER", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))

        // --- 主体分栏 ---
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {

            // ================= 左侧栏 =================
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {

                // 1. 账户信息
                SetupCard(title = "👤  账户信息", subtitle = "CURRENT LOGIN", modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(100.dp).background(Color(0xFF86B3D1).copy(alpha = 0.15f), RoundedCornerShape(50.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("👨‍💼", fontSize = 50.sp) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("System Operator", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("UID: SPC-8842-X", color = TextMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Badge("AUTHORIZED", Color(0xFF1E3A8A))
                            Badge("LEVEL 4", WarningOrange.copy(alpha = 0.2f), WarningOrange)
                        }
                    }
                }

                // 2. 核心信息
                SetupCard(title = "ℹ️  基础信息", subtitle = "CORE INFO") {
                    StatRow(icon = "🏷", label = "系统核心版本", value = "v2.4.0-STABLE", valueColor = TextMuted)
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 16.dp))
                    StatRow(icon = "🔑", label = "授权许可类型", value = "ENTERPRISE-882", valueColor = TextMuted)
                }
            }

            // ================= 右侧栏 =================
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {

                // 3. 运行状态
                SetupCard(title = "📊  运行状态与监控", subtitle = "RUNTIME STATUS") {
                    StatRow(icon = "⏱", label = "软件运行时长", value = uptimeString, valueColor = AccentCyan)
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 16.dp))
                    StatRow(icon = "📡", label = "底层硬件连接", value = connectionState, valueColor = connectionColor)
                }

                // 4. 快捷参数设置 (核心联动区)
                SetupCard(title = "⚙️  快捷参数配置", subtitle = "QUICK CONFIGURATION") {

                    // 联动：采集次数设定
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                            Text("单组预设采集次数", color = TextMuted, fontSize = 15.sp)
                        }

                        // 步进器组件
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ControlButton("-") {
                                if (scanCount > 1) {
                                    scanCount--
                                    viewModel?.config?.params?.numScans = scanCount
                                }
                            }
                            Text(
                                text = "$scanCount",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(48.dp),
                                textAlign = TextAlign.Center
                            )
                            ControlButton("+") {
                                scanCount++
                                viewModel?.config?.params?.numScans = scanCount
                            }
                        }
                    }

                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 16.dp))

                    // 联动：导出格式设定
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💾", fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
                            Text("数据默认导出格式", color = TextMuted, fontSize = 15.sp)
                        }

                        // 格式切换组件
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            FormatToggleBtn("SPC", currentFormat == "SPC") {
                                currentFormat = "SPC"
                                viewModel?.exportFormat = "SPC"
                            }
                            FormatToggleBtn("TXT", currentFormat == "TXT") {
                                currentFormat = "TXT"
                                viewModel?.exportFormat = "TXT"
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= UI 辅助组件 =================

@Composable
fun StatRow(icon: String, label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
            Text(label, color = TextMuted, fontSize = 15.sp)
        }
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ControlButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2A2A2A))
            .clickable { onClick() }
            .border(1.dp, BorderDark, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AccentCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FormatToggleBtn(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) AccentCyan.copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (isSelected) AccentCyan else TextMuted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}