package com.wcg.app.specapp

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoScanScreen(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    Column(modifier = Modifier.fillMaxSize()) {
        // --- 头部标题区域 ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (lang == AppLanguage.Chinese) "自动采集序列 " else "Automated Sequence ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(if (lang == AppLanguage.Chinese) "/ 自动化" else "/ Auto Scan", color = AccentCyan, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    val isReady = viewModel.isConfigApplied
                    val statusColor = if (isReady) Color(0xFF10B981) else WarningOrange
                    Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(6.dp))
                    val statusText = if (isReady) {
                        if (lang == AppLanguage.Chinese) "硬件及参数已就绪" else "HARDWARE READY"
                    } else {
                        if (lang == AppLanguage.Chinese) "参数未下发，请先前往设置页" else "CONFIG PENDING"
                    }
                    Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // --- 主工作区 ---
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {

            // 左侧：配置表单
            Card(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
                    Text(if (lang == AppLanguage.Chinese) "🛠️ 序列规则配置 / Rule Config" else "🛠️ Sequence Rule Config", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))

                    // 模式切换
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ModeCard(
                            title = if (lang == AppLanguage.Chinese) "连续采集" else "Continuous",
                            desc = if (lang == AppLanguage.Chinese) "指定采集总次数" else "Run by Target Count",
                            isSelected = viewModel.autoScanMode == AutoScanMode.Continuous,
                            modifier = Modifier.weight(1f)
                        ) { if (!viewModel.isAutoSequenceRunning) viewModel.autoScanMode = AutoScanMode.Continuous }

                        ModeCard(
                            title = if (lang == AppLanguage.Chinese) "定时采集" else "Scheduled",
                            desc = if (lang == AppLanguage.Chinese) "指定运行总时长" else "Run by Time Duration",
                            isSelected = viewModel.autoScanMode == AutoScanMode.Scheduled,
                            modifier = Modifier.weight(1f)
                        ) { if (!viewModel.isAutoSequenceRunning) viewModel.autoScanMode = AutoScanMode.Scheduled }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 参数输入区
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (viewModel.autoScanMode == AutoScanMode.Continuous) {
                            AutoInputField(
                                label = if (lang == AppLanguage.Chinese) "采集总次数 / Total Scans" else "Total Scans Count",
                                value = viewModel.autoScanCount,
                                onValueChange = { viewModel.autoScanCount = it },
                                modifier = Modifier.weight(1f),
                                enabled = !viewModel.isAutoSequenceRunning
                            )
                        } else {
                            AutoInputField(
                                label = if (lang == AppLanguage.Chinese) "采集总时长(分钟) / Duration(min)" else "Total Duration (Min)",
                                value = viewModel.autoScanDurationMin,
                                onValueChange = { viewModel.autoScanDurationMin = it },
                                modifier = Modifier.weight(1f),
                                enabled = !viewModel.isAutoSequenceRunning
                            )
                        }

                        AutoInputField(
                            label = if (lang == AppLanguage.Chinese) "单次间隔(秒) / Interval(sec)" else "Interval (Sec)",
                            value = viewModel.autoScanIntervalSec,
                            onValueChange = { viewModel.autoScanIntervalSec = it },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isAutoSequenceRunning
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 状态提示
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A), RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Text(
                            text = if (lang == AppLanguage.Chinese) "⚠️ 提示：自动化采集产生的光谱数据将自动按时间戳后缀保存至您在【仪器设置】中配置的默认路径。"
                            else "⚠️ NOTE: Spectra will be auto-saved with timestamps to the export directory defined in Setup.",
                            color = AccentCyan, fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                }
            }

            // 右侧：执行与监控面板
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
                    Text(if (lang == AppLanguage.Chinese) "🚀 执行中心 / Execution" else "🚀 Execution Center", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))

                    // 进度统计
                    val targetText = if (viewModel.autoScanMode == AutoScanMode.Continuous) {
                        viewModel.autoScanCount.ifEmpty { "0" }
                    } else {
                        "~ (Time based)"
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBox(if (lang == AppLanguage.Chinese) "已完成 / Completed" else "Completed", "${viewModel.autoSequenceCompletedCount}", AccentCyan, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(16.dp))
                        StatBox(if (lang == AppLanguage.Chinese) "目标量 / Target" else "Target", targetText, TextMuted, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    val runningText = if (viewModel.isAutoSequenceRunning) {
                        if (lang == AppLanguage.Chinese) "🟢 序列运行中..." else "🟢 Sequence Running..."
                    } else {
                        if (lang == AppLanguage.Chinese) "⏸ 待命就绪" else "⏸ Standby Ready"
                    }
                    Text(runningText, color = if (viewModel.isAutoSequenceRunning) Color(0xFF10B981) else TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.weight(1f))

                    // 控制按钮
                    if (!viewModel.isAutoSequenceRunning) {
                        Button(
                            onClick = { viewModel.startAutoSequence() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
                        ) {
                            Text(if (lang == AppLanguage.Chinese) "▶ 启动自动化序列 / START SEQUENCE" else "▶ START AUTO SEQUENCE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopAutoSequence() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextWhite)
                        ) {
                            Text(if (lang == AppLanguage.Chinese) "🛑 终止执行 / ABORT SEQUENCE" else "🛑 ABORT SEQUENCE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- 内部 UI 组件 ---

@Composable
private fun ModeCard(title: String, desc: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF1E2D4A) else BgDark)
            .border(2.dp, if (isSelected) AccentCyan else BorderDark, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = if (isSelected) AccentCyan else TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AutoInputField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) }, // 仅允许输入数字
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderDark,
                focusedBorderColor = AccentCyan,
                unfocusedTextColor = TextWhite,
                focusedTextColor = TextWhite,
                unfocusedContainerColor = BgDark,
                focusedContainerColor = BgDark,
                disabledContainerColor = BgDark.copy(alpha = 0.5f),
                disabledTextColor = TextMuted
            ),
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BgDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, color = valueColor, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
    }
}