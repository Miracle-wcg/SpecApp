package com.wcg.app.specapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
        ChartSection(viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        InstrumentConfigSingleLinePanel(viewModel)
        Spacer(modifier = Modifier.height(16.dp))
        AcquisitionProgressBar(viewModel)
    }
}

@Composable
private fun HeaderAndActions(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (lang == AppLanguage.Chinese) "光谱控制中心" else "Spectral Analysis", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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

                if (viewModel.isTcpConnected || viewModel.isBoardOpened) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(BorderDark))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (lang == AppLanguage.Chinese) "断开连接" else "DISCONNECT", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { viewModel.disconnectHardware() })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.stopAcquisition() },
                modifier = Modifier.height(44.dp).width(if (lang == AppLanguage.Chinese) 120.dp else 140.dp), shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextWhite)
            ) { Text(if (lang == AppLanguage.Chinese) "🛑 停止" else "🛑 STOP", fontWeight = FontWeight.Bold) }

            Button(
                onClick = { viewModel.startAcquisition() },
                modifier = Modifier.height(44.dp).width(if (lang == AppLanguage.Chinese) 160.dp else 200.dp), shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
            ) { Text(if (lang == AppLanguage.Chinese) "▶ 开始采集" else "▶ START", fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun AcquisitionProgressBar(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage
    val animatedProgress by animateFloatAsState(
        targetValue = viewModel.progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val statusText = if (viewModel.isAcquiring) {
            if (lang == AppLanguage.Chinese) "采集中" else "ACQUIRING"
        } else {
            if (lang == AppLanguage.Chinese) "待机就绪" else "READY"
        }
        val statusColor = if (viewModel.isAcquiring) AccentCyan else TextMuted

        Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp).height(8.dp).clip(RoundedCornerShape(50)),
            color = AccentCyan, trackColor = BgDark
        )

        Text("${(animatedProgress * 100).toInt()}%", color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun ChartSection(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    val textMeasurer = rememberTextMeasurer()
    var hoverX by remember { mutableStateOf<Float?>(null) }
    val data = viewModel.spectrumData

    val density = LocalDensity.current.density
    var scaleX by remember { mutableStateOf(1f) }
    var scaleY by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(PanelBg).border(1.dp, BorderDark, RoundedCornerShape(8.dp))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val paddingStart = 85f * density
        val paddingTop = 40f * density
        val paddingBottom = 45f * density
        val paddingEnd = 30f * density

        val chartWidth = widthPx - paddingStart - paddingEnd
        val chartHeight = heightPx - paddingTop - paddingBottom

        fun clampOffsets() {
            offsetX = offsetX.coerceIn(-(scaleX - 1) * chartWidth, 0f)
            offsetY = offsetY.coerceIn(-(scaleY - 1) * chartHeight, 0f)
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var isDragging = false
                        var lastPos = Offset.Zero

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()

                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val delta = change.scrollDelta.y
                                    val zoomFactor = if (delta > 0) 0.85f else 1.15f
                                    val isCtrl = event.keyboardModifiers.isCtrlPressed
                                    val isShift = event.keyboardModifiers.isShiftPressed

                                    val cx = change.position.x - paddingStart
                                    val cy = (paddingTop + chartHeight) - change.position.y

                                    if (isCtrl) {
                                        val dataY = (cy - offsetY) / scaleY
                                        scaleY = (scaleY * zoomFactor).coerceIn(1f, 100f)
                                        offsetY = cy - dataY * scaleY
                                    } else if (isShift) {
                                        val dataX = (cx - offsetX) / scaleX
                                        val dataY = (cy - offsetY) / scaleY
                                        scaleX = (scaleX * zoomFactor).coerceIn(1f, 100f)
                                        scaleY = (scaleY * zoomFactor).coerceIn(1f, 100f)
                                        offsetX = cx - dataX * scaleX
                                        offsetY = cy - dataY * scaleY
                                    } else {
                                        val dataX = (cx - offsetX) / scaleX
                                        scaleX = (scaleX * zoomFactor).coerceIn(1f, 100f)
                                        offsetX = cx - dataX * scaleX
                                    }
                                    clampOffsets()
                                    change.consume()
                                }
                                PointerEventType.Press -> { isDragging = true; lastPos = change.position }
                                PointerEventType.Release -> { isDragging = false }
                                PointerEventType.Move -> {
                                    hoverX = change.position.x
                                    if (isDragging) {
                                        offsetX += (change.position - lastPos).x
                                        offsetY -= (change.position - lastPos).y
                                        lastPos = change.position
                                        clampOffsets()
                                    }
                                }
                                PointerEventType.Exit -> { hoverX = null; isDragging = false }
                            }
                        }
                    }
                }
        ) {
            drawLine(color = BorderDark, start = Offset(paddingStart, paddingTop), end = Offset(paddingStart, paddingTop + chartHeight), strokeWidth = 2f)
            drawLine(color = BorderDark, start = Offset(paddingStart, paddingTop + chartHeight), end = Offset(paddingStart + chartWidth, paddingTop + chartHeight), strokeWidth = 2f)

            if (data.isNotEmpty()) {
                val minX = data.minOf { it.first }
                val maxX = data.maxOf { it.first }
                val minY = data.minOf { it.second }
                val maxY = data.maxOf { it.second }

                val rangeX = if (maxX == minX) 1.0 else maxX - minX
                val rangeY = if (maxY == minY) 1.0 else maxY - minY

                val renderMinY = minY - (rangeY * 0.1)
                val renderMaxY = maxY + (rangeY * 0.1)

                val dataH = renderMaxY - renderMinY

                val viewMaxX = maxX + (offsetX / (chartWidth * scaleX)) * rangeX
                val viewMinX = maxX - ((chartWidth - offsetX) / (chartWidth * scaleX)) * rangeX
                val viewMinY = renderMinY - (offsetY / (chartHeight * scaleY)) * dataH
                val viewMaxY = renderMinY + ((chartHeight - offsetY) / (chartHeight * scaleY)) * dataH

                val ySteps = 5
                for (i in 0..ySteps) {
                    val yRatio = i.toFloat() / ySteps
                    val yPos = paddingTop + chartHeight - (yRatio * chartHeight)
                    val yVal = viewMinY + yRatio * (viewMaxY - viewMinY)

                    drawLine(color = BorderDark.copy(alpha = 0.4f), start = Offset(paddingStart, yPos), end = Offset(paddingStart + chartWidth, yPos), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))

                    val textLayoutResult = textMeasurer.measure(String.format("%.5f", yVal), style = TextStyle(color = TextMuted, fontSize = 10.sp))
                    drawText(textLayoutResult, topLeft = Offset(paddingStart - textLayoutResult.size.width - 15f, yPos - textLayoutResult.size.height / 2))
                }

                val xSteps = 8
                for (i in 0..xSteps) {
                    val xRatio = i.toFloat() / xSteps
                    val xPos = paddingStart + xRatio * chartWidth
                    val xVal = viewMaxX - xRatio * (viewMaxX - viewMinX)

                    drawLine(color = BorderDark.copy(alpha = 0.4f), start = Offset(xPos, paddingTop), end = Offset(xPos, paddingTop + chartHeight), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))

                    val textLayoutResult = textMeasurer.measure(String.format("%.0f", xVal), style = TextStyle(color = TextMuted, fontSize = 10.sp))
                    drawText(textLayoutResult, topLeft = Offset(xPos - textLayoutResult.size.width / 2, paddingTop + chartHeight + 10f))
                }

                clipRect(left = paddingStart, top = paddingTop, right = paddingStart + chartWidth, bottom = paddingTop + chartHeight) {
                    val path = Path()
                    var closestPoint: Pair<Offset, Pair<Double, Double>>? = null
                    var minDistance = Float.MAX_VALUE

                    data.forEachIndexed { index, point ->
                        val px = paddingStart + ((viewMaxX - point.first) / (viewMaxX - viewMinX)).toFloat() * chartWidth
                        val py = paddingTop + chartHeight - ((point.second - viewMinY) / (viewMaxY - viewMinY)).toFloat() * chartHeight

                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)

                        hoverX?.let { hx ->
                            val dist = abs(px - hx)
                            if (dist < minDistance) {
                                minDistance = dist
                                closestPoint = Offset(px, py) to point
                            }
                        }
                    }
                    drawPath(path = path, color = AccentCyan, style = Stroke(width = 1.5.dp.toPx()))

                    closestPoint?.let { (cOffset, cPoint) ->
                        if (cOffset.x in paddingStart..(paddingStart + chartWidth) && cOffset.y in paddingTop..(paddingTop + chartHeight)) {
                            drawLine(color = WarningOrange, start = Offset(cOffset.x, paddingTop), end = Offset(cOffset.x, paddingTop + chartHeight), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                            drawLine(color = WarningOrange, start = Offset(paddingStart, cOffset.y), end = Offset(paddingStart + chartWidth, cOffset.y), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                            drawCircle(color = WarningOrange, radius = 4.dp.toPx(), center = cOffset)

                            val tooltipText = "X: ${cPoint.first}\nY: ${cPoint.second}"

                            val tr = textMeasurer.measure(tooltipText, style = TextStyle(color = BgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            val tw = tr.size.width + 30f
                            val th = tr.size.height + 20f

                            var tLeft = cOffset.x + 15f
                            var tTop = cOffset.y - th - 15f
                            if (tLeft + tw > paddingStart + chartWidth) tLeft = cOffset.x - tw - 15f
                            if (tTop < paddingTop) tTop = cOffset.y + 15f

                            drawRoundRect(color = AccentCyan, topLeft = Offset(tLeft, tTop), size = Size(tw, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            drawText(tr, topLeft = Offset(tLeft + 15f, tTop + 10f))
                        }
                    }
                }
            }
        }

        // --- Z轴上层：UI 控件 ---
        val yAxisText = if (lang == AppLanguage.Chinese) "强度" else "INTENSITY"
        val xAxisText = if (lang == AppLanguage.Chinese) "波数 [CM-1]" else "WAVENUMBER [CM-1]"
        val helperText = if (lang == AppLanguage.Chinese) "💡 滚轮: 缩放X轴 | Ctrl+滚轮: 缩放Y轴 | 左键: 拖拽漫游" else "💡 Scroll: Zoom X | Ctrl+Scroll: Zoom Y | Left Click: Pan"

        Text(yAxisText, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp))
        Text(xAxisText, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        Text(helperText, color = TextMuted.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))

        Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color(0xFF2A364B).copy(alpha = 0.8f), RoundedCornerShape(4.dp)).border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(110.dp)) {
                Text(if (lang == AppLanguage.Chinese) "峰值 X" else "Peak X", color = TextMuted, fontSize = 10.sp)
                Text(viewModel.peakX, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(110.dp)) {
                Text(if (lang == AppLanguage.Chinese) "峰值 Y" else "Peak Y", color = TextMuted, fontSize = 10.sp)
                Text(viewModel.peakY, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (scaleX > 1f || scaleY > 1f || offsetX != 0f || offsetY != 0f) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 160.dp)
                    .background(Color(0xFF1E2D4A).copy(alpha = 0.95f), RoundedCornerShape(4.dp))
                    .border(1.dp, AccentCyan, RoundedCornerShape(4.dp))
                    .clickable {
                        viewModel.logUserAction("Reset Canvas Viewport")
                        scaleX = 1f
                        scaleY = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (lang == AppLanguage.Chinese) "⤢ 恢复视图" else "⤢ Reset View", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InstrumentConfigSingleLinePanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    val config = viewModel.config
    val isAcquiring = viewModel.isAcquiring

    val resIndex = config.params.resolution.toInt()
    val resMap = listOf("1 cm-1", "2 cm-1", "4 cm-1", "8 cm-1", "16 cm-1", "32 cm-1", "64 cm-1", "128 cm-1")
    val resText = if (resIndex in resMap.indices) resMap[resIndex] else "$resIndex"

    val gainVal = when(config.params.firstGain.toInt()) {
        0 -> "28"; 1 -> "56"; 2 -> "112"; 3 -> "225"; 4 -> "450"; 5 -> "900"; 6 -> "1800"; 7 -> "3600"; else -> "Unknown"
    }

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📡", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(if (viewModel.isBoardOpened) "READY" else "OFFLINE", color = if (viewModel.isBoardOpened) AccentCyan else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            ConfigItemRow(if (lang == AppLanguage.Chinese) "波段范围" else "WAVE RANGE", "${config.params.startWave} - ${config.params.stopWave}")
            ConfigItemRow(if (lang == AppLanguage.Chinese) "分辨率" else "RESOLUTION", resText)
            ConfigItemRow(if (lang == AppLanguage.Chinese) "增益" else "GAIN", gainVal)
            ConfigItemRow(if (lang == AppLanguage.Chinese) "累加次数" else "CO-ADDS", "${viewModel.totalSweeps}")

            val statusVal = if (isAcquiring) (if (lang == AppLanguage.Chinese) "采集中" else "Acquiring") else (if (lang == AppLanguage.Chinese) "待机" else "Standby")
            ConfigItemRow(if (lang == AppLanguage.Chinese) "状态" else "STATUS", statusVal, isAcquiring)
        }
    }
}

@Composable
private fun ConfigItemRow(label: String, value: String, highlight: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(6.dp))
        if (highlight) { Box(modifier = Modifier.size(6.dp).background(WarningOrange, RoundedCornerShape(50))); Spacer(modifier = Modifier.width(6.dp)) }
        Text(value, color = if (highlight) WarningOrange else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}