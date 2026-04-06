package com.wcg.app.specapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AnalysisScreen(viewModel: SpectrometerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderAndActions(viewModel)
        Spacer(modifier = Modifier.height(16.dp))

        // 【优化点 3】：分离的进度条和折线图区域
        ChartSection(viewModel, modifier = Modifier.weight(1.5f).fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        ProgressSection(viewModel)
        Spacer(modifier = Modifier.height(16.dp))

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

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                val isReady = viewModel.isBoardOpened
                val statusColor = if (isReady) Color(0xFF10B981) else WarningOrange
                Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isReady) "设备已连接就绪 (ONLINE)" else "设备未连接 (OFFLINE)", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

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

        // 【优化点 1】：按钮大小与布局优化
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { viewModel.stopAcquisition() },
                modifier = Modifier.height(40.dp).width(120.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = TextWhite),
                border = BorderStroke(1.dp, DangerRed)
            ) {
                Box(modifier = Modifier.size(8.dp).background(DangerRed))
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.startAcquisition() },
                modifier = Modifier.height(40.dp).width(160.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
            ) {
                Text("▶ 开始采集", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// 【优化点 3】：独立的进度条组件
@Composable
private fun ProgressSection(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("采集进度 SCAN PROGRESS", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("${(viewModel.progress * 100).toInt()}%", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { viewModel.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = AccentCyan,
            trackColor = PanelBg
        )
    }
}

@Composable
private fun ChartSection(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(PanelBg).border(1.dp, BorderDark, RoundedCornerShape(8.dp))) {
        Text("INTENSITY / ABSORBANCE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-30).dp).rotate(-90f))

        val data = viewModel.spectrumData

        // 测量和样式准备
        val textMeasurer = rememberTextMeasurer()
        val axisTextStyle = TextStyle(color = TextMuted, fontSize = 10.sp)

        // 鼠标位置状态
        var mousePosition by remember { mutableStateOf(Offset.Unspecified) }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(start = 70.dp, bottom = 40.dp, top = 30.dp, end = 30.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // 【优化点 4】：侦听鼠标移动，支持坐标展示
                        if (event.type == PointerEventType.Move) {
                            mousePosition = event.changes.first().position
                        } else if (event.type == PointerEventType.Exit) {
                            mousePosition = Offset.Unspecified
                        }
                    }
                }
            }
        ) {
            val width = size.width
            val height = size.height
            val gridLines = 8

            // 1. 绘制网格线
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
                val maxY = data.maxOf { it.second }

                val rangeY = maxY - minY
                val renderMinY = minY - (rangeY * 0.1)
                val renderMaxY = maxY + (rangeY * 0.1)

                // 2. 【优化点 5】：绘制高精度的 X、Y 轴刻度文本
                // 绘制 Y 轴数值 (左侧)
                for (i in 0..gridLines) {
                    val yValue = renderMaxY - i * (renderMaxY - renderMinY) / gridLines
                    val yPos = i * (height / gridLines)

                    // 【修复点】：先 measure 生成 TextLayoutResult，绕过 Canvas 边界检查
                    val yTextResult = textMeasurer.measure(
                        text = String.format("%.4f", yValue),
                        style = axisTextStyle
                    )
                    drawText(
                        textLayoutResult = yTextResult,
                        topLeft = Offset(-60.dp.toPx(), yPos - 6.dp.toPx())
                    )
                }

                // 绘制 X 轴数值 (底部，注意由于是波数，通常 X 轴由右向左递减)
                for (i in 0..gridLines) {
                    val xValue = maxX - i * (maxX - minX) / gridLines
                    val xPos = i * (width / gridLines)

                    // 【修复点】：先 measure，防止 height + 10.dp 导致 maxHeight 为负数崩溃
                    val xTextResult = textMeasurer.measure(
                        text = String.format("%.1f", xValue),
                        style = axisTextStyle
                    )
                    drawText(
                        textLayoutResult = xTextResult,
                        topLeft = Offset(xPos - 15.dp.toPx(), height + 10.dp.toPx())
                    )
                }

                // 3. 【优化点 4】：绘制细线条高精度折线图
                val path = Path()
                data.forEachIndexed { index, point ->
                    // 反向 X 轴：高波数在左，低波数在右
                    val px = ((maxX - point.first) / (maxX - minX)).toFloat() * width
                    val py = height - ((point.second - renderMinY) / (renderMaxY - renderMinY)).toFloat() * height

                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path = path, color = AccentCyan, style = Stroke(width = 1.dp.toPx()))

                // 4. 【优化点 4】：绘制鼠标悬停时的十字光标和数据提示框
                if (mousePosition != Offset.Unspecified) {
                    val mouseX = mousePosition.x
                    if (mouseX in 0f..width) {
                        // 反推当前鼠标对应的 X 真实值
                        val mappedX = maxX - (mouseX / width) * (maxX - minX)
                        // 寻找最靠近的数据点
                        val closest = data.minByOrNull { abs(it.first - mappedX) }
                        if (closest != null) {
                            val cx = ((maxX - closest.first) / (maxX - minX)).toFloat() * width
                            val cy = height - ((closest.second - renderMinY) / (renderMaxY - renderMinY)).toFloat() * height

                            // 十字光标线
                            drawLine(color = WarningOrange.copy(alpha = 0.6f), start = Offset(cx, 0f), end = Offset(cx, height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                            drawLine(color = WarningOrange.copy(alpha = 0.6f), start = Offset(0f, cy), end = Offset(width, cy), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))

                            // 交点圆圈
                            drawCircle(color = WarningOrange, radius = 4.dp.toPx(), center = Offset(cx, cy))

                            // 浮窗 Tooltip 绘制
                            val tooltipText = "X: ${String.format("%.2f", closest.first)} cm⁻¹\nY: ${String.format("%.5f", closest.second)}"
                            val textLayoutResult = textMeasurer.measure(tooltipText, TextStyle(color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            val tooltipWidth = textLayoutResult.size.width + 20.dp.toPx()
                            val tooltipHeight = textLayoutResult.size.height + 16.dp.toPx()

                            var tipX = cx + 12.dp.toPx()
                            var tipY = cy - tooltipHeight - 12.dp.toPx()

                            // 防止 Tooltip 越出画布边界
                            if (tipX + tooltipWidth > width) tipX = cx - tooltipWidth - 12.dp.toPx()
                            if (tipY < 0f) tipY = cy + 12.dp.toPx()

                            drawRoundRect(color = Color(0xFF1E2D4A).copy(alpha = 0.95f), topLeft = Offset(tipX, tipY), size = Size(tooltipWidth, tooltipHeight), cornerRadius = CornerRadius(4.dp.toPx()))
                            drawRoundRect(color = AccentCyan, topLeft = Offset(tipX, tipY), size = Size(tooltipWidth, tooltipHeight), cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))

                            drawText(textLayoutResult, color = TextWhite, topLeft = Offset(tipX + 10.dp.toPx(), tipY + 8.dp.toPx()))
                        }
                    }
                }
            }
        }

        Text("WAVENUMBER [CM-1] (波数)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
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
                    Text("实时采集配置 ", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(if (viewModel.isBoardOpened) "READY" else "OFFLINE", color = if (viewModel.isBoardOpened) AccentCyan else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.weight(1f)) {
                ConfigCol("波段范围 WAVE RANGE", "${config.params.startWave} - ${config.params.stopWave} cm-1", "分辨率 RESOLUTION", resText, "激光频率 LASER FREQ", "${config.laserFreq} Hz", Modifier.weight(1f))
                ConfigCol("运行状态 STATUS", if(isAcquiring) "采集进行中 Acquiring" else "待机 Standby", "扫描次数 CO-ADDS", "${viewModel.currentSweep} / ${viewModel.totalSweeps}", "硬件连接 H/W LINK", if (viewModel.isBoardOpened) "Connected" else "Disconnected", Modifier.weight(1f), isAcquiring)
                ConfigCol("目标 IP ADDRESS", config.serverIp, "增益配置 GAIN", "G1:${config.params.firstGain} / G2:${config.params.secondGain}", "全局导出格式 EXPORT FMT", viewModel.exportFormat, Modifier.weight(1f))
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