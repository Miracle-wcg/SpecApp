package com.wcg.app.specapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnalysisScreen(viewModel: SpectrometerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderAndActions(viewModel)
        Spacer(modifier = Modifier.height(20.dp))
        ChartSection(viewModel, modifier = Modifier.weight(1.5f).fillMaxWidth())
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            InstrumentConfigPanel(viewModel, modifier = Modifier.weight(1.5f))
            DataExportPanel(viewModel, modifier = Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun HeaderAndActions(viewModel: SpectrometerViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("光谱控制中心 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Spectral Analysis", color = AccentCyan, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
            }

            // ===== 优化：全局状态同步与断开连接控制 =====
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                val isReady = viewModel.isBoardOpened
                val statusColor = if (isReady) Color(0xFF10B981) else WarningOrange
                Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isReady) "设备已连接就绪 (ONLINE)" else "设备未连接 (OFFLINE)", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // 如果处于已连接状态，则在状态旁边额外渲染一个红色的断开按钮
                if (viewModel.isTcpConnected || viewModel.isBoardOpened) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(BorderDark))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "断开连接 DISCONNECT",
                        color = DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.disconnectHardware() }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { viewModel.stopAcquisition() },
                modifier = Modifier.height(44.dp), shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = TextWhite),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Box(modifier = Modifier.size(8.dp).background(DangerRed))
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止 STOP", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.startAcquisition() },
                modifier = Modifier.height(44.dp).width(240.dp), shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
            ) {
                Text("▶ 开始采集 START ACQUISITION", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ===== 下方组件保持原样 =====
@Composable
private fun ChartSection(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(PanelBg).border(1.dp, BorderDark, RoundedCornerShape(8.dp))) {
        Text("ABSORBANCE [AU] (吸光度)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-30).dp).rotate(-90f))

        val data = viewModel.spectrumData
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 60.dp, bottom = 60.dp, top = 40.dp, end = 40.dp)) {
            val width = size.width
            val height = size.height
            val gridLines = 6
            for (i in 0..gridLines) {
                val y = i * (height / gridLines)
                drawLine(color = BorderDark, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                val x = i * (width / gridLines)
                drawLine(color = BorderDark, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            if (data.isNotEmpty()) {
                val minX = data.minOf { it.first }
                val maxX = data.maxOf { it.first }
                val minY = data.minOf { it.second }
                val maxY = data.maxOf { it.second } * 1.1

                val path = Path()
                data.forEachIndexed { index, point ->
                    val px = ((maxX - point.first) / (maxX - minX)).toFloat() * width
                    val py = height - ((point.second - minY) / (maxY - minY)).toFloat() * height
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path = path, color = AccentCyan, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Text("WAVENUMBER [CM-1] (波数)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))

        Column(modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color(0xFF2A364B).copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(100.dp)) {
                Text("Peak [X]", color = TextMuted, fontSize = 10.sp)
                Text(viewModel.peakX, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(100.dp)) {
                Text("Value [Y]", color = TextMuted, fontSize = 10.sp)
                Text(viewModel.peakY, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, end = 40.dp, bottom = 40.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("扫描进度 SCAN PROGRESS", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${(viewModel.progress * 100).toInt()}%", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { viewModel.progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)), color = AccentCyan, trackColor = BorderDark)
        }
    }
}

@Composable
private fun InstrumentConfigPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val config = viewModel.config
    val isAcquiring = viewModel.isAcquiring
    val resIndex = config.params.resolution.toInt()
    val resMap = listOf("1 cm-1", "2 cm-1", "4 cm-1", "8 cm-1", "16 cm-1", "32 cm-1", "64 cm-1")
    val resText = if (resIndex in resMap.indices) resMap[resIndex] else "$resIndex"

    Card(modifier = modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderDark)) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📡", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仪器配置 ", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Instrument Configuration", color = TextWhite, fontSize = 16.sp)
                }
                Box(modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(if (viewModel.isBoardOpened) "SYSTEM READY" else "OFFLINE", color = if (viewModel.isBoardOpened) AccentCyan else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.weight(1f)) {
                ConfigCol("波段范围 WAVE RANGE", "${config.params.startWave} - ${config.params.stopWave} cm-1", "分辨率 RESOLUTION", resText, "激光频率 LASER FREQ", "${config.laserFreq} Hz", Modifier.weight(1f))
                ConfigCol("运行状态 STATUS", if(isAcquiring) "采集进行中 Acquiring" else "待机 Standby", "扫描次数 CO-ADDS", "${viewModel.currentSweep} / ${viewModel.totalSweeps}", "硬件连接 H/W LINK", if (viewModel.isBoardOpened) "Connected" else "Disconnected", Modifier.weight(1f), isAcquiring)
                ConfigCol("设备名称 BOARD NAME", config.boardName, "增益配置 GAIN", "G1:${config.params.firstGain} / G2:${config.params.secondGain}", "全局导出格式 EXPORT FMT", viewModel.exportFormat, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DataExportPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val config = viewModel.config
    Card(modifier = modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderDark)) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("➔", color = TextWhite, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("数据导出 ", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Data Export", color = TextWhite, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            DarkTextField("保存路径 SAVE PATH", config.savePath, { config.savePath = it; config.savePathWindows = it }, Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Text("导出格式 EXPORT FORMAT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormatButton("TXT", "文本格式", viewModel.exportFormat == "TXT", Modifier.weight(1f)) { viewModel.exportFormat = "TXT" }
                FormatButton("SPC", "专业格式", viewModel.exportFormat == "SPC", Modifier.weight(1f)) { viewModel.exportFormat = "SPC" }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfigCol(l1: String, v1: String, l2: String, v2: String, l3: String, v3: String, modifier: Modifier, isHighlightV2: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        ConfigItem(l1, v1)
        ConfigItem(l2, v2, isHighlightV2)
        ConfigItem(l3, v3)
    }
}

@Composable
private fun ConfigItem(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (highlight) { Box(modifier = Modifier.size(6.dp).background(WarningOrange, RoundedCornerShape(50))); Spacer(modifier = Modifier.width(6.dp)) }
            Text(value, color = if (highlight) WarningOrange else TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FormatButton(title: String, subtitle: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
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