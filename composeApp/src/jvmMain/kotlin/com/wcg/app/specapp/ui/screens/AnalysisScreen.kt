package com.wcg.app.specapp.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.ui.theme.*
import com.wcg.app.specapp.viewmodel.AppLanguage
import com.wcg.app.specapp.viewmodel.SpectrometerViewModel
import kotlin.math.abs

@Composable
fun AnalysisScreen(viewModel: SpectrometerViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        HeaderAndActions(viewModel)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ChartSection(viewModel, modifier = Modifier.weight(1f).fillMaxHeight())
            Spacer(modifier = Modifier.width(16.dp))
            SpectrumManagementPanel(viewModel, modifier = Modifier.width(280.dp).fillMaxHeight())
        }

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
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == AppLanguage.Chinese) "停止" else "STOP", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.startAcquisition() },
                modifier = Modifier.height(44.dp).width(if (lang == AppLanguage.Chinese) 160.dp else 200.dp), shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == AppLanguage.Chinese) "开始采集" else "START", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SpectrumManagementPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    var selectedTabIndex by remember { mutableStateOf(0) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = AccentCyan,
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[selectedTabIndex])
                            .height(3.dp)
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(AccentCyan)
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(if (lang == AppLanguage.Chinese) "数据轨道" else "Tracks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(selectedTabIndex==0) AccentCyan else TextMuted) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(if (lang == AppLanguage.Chinese) "分析配方" else "Recipe", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(selectedTabIndex==1) AccentCyan else TextMuted) }
                )
            }

            Box(modifier = Modifier.weight(1f).padding(12.dp)) {
                if (selectedTabIndex == 0) {
                    TracksTabContent(viewModel)
                } else {
                    PipelineTabContent(viewModel)
                }
            }
        }
    }
}

// ==============================================================================
// 🌟 轨道面板内容更新：增加了一键清空按钮
// ==============================================================================
@Composable
private fun TracksTabContent(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage
    val spectra = viewModel.loadedSpectra

    Column(modifier = Modifier.fillMaxSize()) {
        if (spectra.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (lang == AppLanguage.Chinese) "暂无数据载入" else "No data loaded", color = TextMuted, fontSize = 12.sp)
            }
        } else {
            // 🌟 新增：统计信息与一键清空操作条
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (lang == AppLanguage.Chinese) "已加载: ${spectra.size}" else "Loaded: ${spectra.size}", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.clickable { viewModel.clearAllSpectra() }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == AppLanguage.Chinese) "清空全部" else "Clear All", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(spectra) { spec ->
                    val isCompareSelected = viewModel.selectedForCompare.contains(spec)
                    val isReference = viewModel.referenceSpectrumId == spec.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(if(isCompareSelected) AccentCyan.copy(alpha = 0.1f) else BgDark, RoundedCornerShape(6.dp))
                            .border(1.dp, if(isCompareSelected) AccentCyan.copy(alpha = 0.5f) else BorderDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCompareSelected,
                            onCheckedChange = { viewModel.toggleSelectionForCompare(spec) },
                            modifier = Modifier.size(20.dp),
                            colors = CheckboxDefaults.colors(checkedColor = AccentCyan, uncheckedColor = TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(10.dp).background(if(spec.isVisible) spec.color else Color.Transparent, RoundedCornerShape(2.dp)).border(1.dp, spec.color, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = spec.name, color = if(spec.isVisible) TextWhite else TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isReference) {
                                Text("⭐ 差谱基准", color = WarningOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(onClick = { spec.isVisible = !spec.isVisible }, modifier = Modifier.size(22.dp)) {
                            Icon(imageVector = if(spec.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = if(spec.isVisible) AccentCyan else TextMuted, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = { viewModel.removeSpectrum(spec) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (viewModel.selectedForCompare.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.prepareComparison() },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("推送至精度验证 (${viewModel.selectedForCompare.size}/2)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PipelineTabContent(viewModel: SpectrometerViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("预处理流水线 (Preprocessing)", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = BgDark), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, BorderDark)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.pipelineSgSmooth = !viewModel.pipelineSgSmooth; viewModel.applyPipeline() }) {
                    Checkbox(checked = viewModel.pipelineSgSmooth, onCheckedChange = { viewModel.pipelineSgSmooth = it; viewModel.applyPipeline() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("SG 平滑去噪", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Savitzky-Golay Smoothing", color = TextMuted, fontSize = 9.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.pipelineSnv = !viewModel.pipelineSnv; viewModel.applyPipeline() }) {
                    Checkbox(checked = viewModel.pipelineSnv, onCheckedChange = { viewModel.pipelineSnv = it; viewModel.applyPipeline() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("SNV 标准正态变换", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Standard Normal Variate", color = TextMuted, fontSize = 9.sp)
                    }
                }
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.pipeline1stDeriv = !viewModel.pipeline1stDeriv; viewModel.applyPipeline() }) {
                    Checkbox(checked = viewModel.pipeline1stDeriv, onCheckedChange = { viewModel.pipeline1stDeriv = it; viewModel.applyPipeline() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("一阶导数变换", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("1st Derivative Enhancement", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("差谱基准映射 (Diff Reference)", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        var expanded by remember { mutableStateOf(false) }
        val currentRefName = viewModel.loadedSpectra.find { it.id == viewModel.referenceSpectrumId }?.name ?: "无 (关闭差谱)"

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                border = BorderStroke(1.dp, if(viewModel.referenceSpectrumId != "NONE") WarningOrange else BorderDark)
            ) {
                Text(currentRefName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if(viewModel.referenceSpectrumId != "NONE") WarningOrange else TextWhite)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(PanelBg).border(1.dp, BorderDark)) {
                DropdownMenuItem(
                    text = { Text("无 (关闭差谱)", color = TextMuted, fontSize = 12.sp) },
                    onClick = { viewModel.referenceSpectrumId = "NONE"; viewModel.applyPipeline(); expanded = false }
                )
                viewModel.loadedSpectra.forEach { spec ->
                    DropdownMenuItem(
                        text = { Text(spec.name, color = TextWhite, fontSize = 12.sp) },
                        onClick = { viewModel.referenceSpectrumId = spec.id; viewModel.applyPipeline(); expanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth().background(AccentCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("图表已开启批处理实时预览，更改参数后将自动应用至所有轨道。", color = AccentCyan, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ChartSection(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    val textMeasurer = rememberTextMeasurer()
    var hoverX by remember { mutableStateOf<Float?>(null) }

    val liveData = viewModel.spectrumData
    val visibleSpectra = viewModel.loadedSpectra.filter { it.isVisible }
    val hasLiveData = viewModel.isAcquiring && liveData.isNotEmpty()

    val density = LocalDensity.current.density
    var scaleX by remember { mutableStateOf(1f) }
    var scaleY by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    var stackOffsetRatio by remember { mutableStateOf(0f) }
    var showPeaks by remember { mutableStateOf(false) }
    var showMiniMap by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(PanelBg).border(1.dp, BorderDark, RoundedCornerShape(8.dp))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val paddingStart = 85f * density; val paddingTop = 40f * density; val paddingBottom = 45f * density; val paddingEnd = 30f * density
        val chartWidth = widthPx - paddingStart - paddingEnd
        val chartHeight = heightPx - paddingTop - paddingBottom

        fun clampOffsets() {
            val overscrollX = chartWidth * 0.4f
            val minOffsetX = -(scaleX - 1) * chartWidth - overscrollX
            offsetX = offsetX.coerceIn(minOffsetX, overscrollX)
            val overscrollY = chartHeight * 0.4f
            val minOffsetY = -(scaleY - 1) * chartHeight - overscrollY
            offsetY = offsetY.coerceIn(minOffsetY, overscrollY)
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var isDragging = false; var lastPos = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val deltaX = change.scrollDelta.x
                                    val deltaY = change.scrollDelta.y
                                    if (abs(deltaX) > 0f) offsetX -= deltaX * 25f
                                    if (abs(deltaY) > 0f) {
                                        val zoomFactor = if (deltaY > 0) 0.85f else 1.15f
                                        val cx = change.position.x - paddingStart
                                        val cy = (paddingTop + chartHeight) - change.position.y

                                        if (event.keyboardModifiers.isCtrlPressed) {
                                            val oldScale = scaleY
                                            scaleY = (scaleY * zoomFactor).coerceIn(1f, 100f)
                                            offsetY = cy - ((cy - offsetY) / oldScale) * scaleY
                                        } else if (event.keyboardModifiers.isShiftPressed) {
                                            offsetX -= deltaY * 25f
                                        } else {
                                            val oldScale = scaleX
                                            scaleX = (scaleX * zoomFactor).coerceIn(1f, 100f)
                                            offsetX = cx - ((cx - offsetX) / oldScale) * scaleX
                                        }
                                    }
                                    clampOffsets(); change.consume()
                                }
                                PointerEventType.Press -> { isDragging = true; lastPos = change.position }
                                PointerEventType.Release -> { isDragging = false }
                                PointerEventType.Move -> {
                                    hoverX = change.position.x
                                    if (isDragging) {
                                        offsetX += (change.position - lastPos).x
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

            if (visibleSpectra.isNotEmpty() || hasLiveData) {
                var baseMinY = Double.MAX_VALUE; var baseMaxY = -Double.MAX_VALUE
                if (hasLiveData) {
                    baseMinY = minOf(baseMinY, liveData.minOf { it.second })
                    baseMaxY = maxOf(baseMaxY, liveData.maxOf { it.second })
                }
                visibleSpectra.forEach { spec ->
                    if (spec.displayData.isNotEmpty()) {
                        baseMinY = minOf(baseMinY, spec.displayData.minOf { it.second })
                        baseMaxY = maxOf(baseMaxY, spec.displayData.maxOf { it.second })
                    }
                }
                val baseRangeY = if (baseMaxY == baseMinY) 1.0 else baseMaxY - baseMinY
                val offsetStep = baseRangeY * stackOffsetRatio

                var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
                var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE

                if (hasLiveData) {
                    minX = minOf(minX, liveData.minOf { it.first })
                    maxX = maxOf(maxX, liveData.maxOf { it.first })
                    minY = minOf(minY, liveData.minOf { it.second })
                    maxY = maxOf(maxY, liveData.maxOf { it.second })
                }
                visibleSpectra.forEachIndexed { index, spec ->
                    if (spec.displayData.isNotEmpty()) {
                        val shift = index * offsetStep
                        minX = minOf(minX, spec.displayData.minOf { it.first })
                        maxX = maxOf(maxX, spec.displayData.maxOf { it.first })
                        minY = minOf(minY, spec.displayData.minOf { it.second + shift })
                        maxY = maxOf(maxY, spec.displayData.maxOf { it.second + shift })
                    }
                }

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
                    var closestPoint: Pair<Offset, Pair<Double, Double>>? = null
                    var minDistance = Float.MAX_VALUE
                    var closestColor = AccentCyan
                    var closestName = "Live"

                    fun drawAndDetect(pts: List<Pair<Double, Double>>, curveColor: Color, curveName: String, shift: Double, isReference: Boolean = false) {
                        val path = Path()
                        var localPeakRaw: Pair<Double, Double>? = null
                        var localPeakCanvas: Offset? = null
                        var currentMax = -Double.MAX_VALUE

                        pts.forEachIndexed { index, point ->
                            val shiftedY = point.second + shift
                            val px = paddingStart + ((viewMaxX - point.first) / (viewMaxX - viewMinX)).toFloat() * chartWidth
                            val py = paddingTop + chartHeight - ((shiftedY - viewMinY) / (viewMaxY - viewMinY)).toFloat() * chartHeight

                            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)

                            if (showPeaks && point.second > currentMax) {
                                currentMax = point.second
                                localPeakRaw = point
                                localPeakCanvas = Offset(px, py)
                            }

                            hoverX?.let { hx ->
                                val dist = abs(px - hx)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    closestPoint = Offset(px, py) to point
                                    closestColor = curveColor
                                    closestName = curveName
                                }
                            }
                        }

                        val strokeStyle = if(isReference) Stroke(width = 3.dp.toPx()) else Stroke(width = 1.5.dp.toPx())
                        drawPath(path = path, color = curveColor, style = strokeStyle)

                        if (showPeaks && localPeakCanvas != null && localPeakRaw != null) {
                            val lpx = localPeakCanvas!!.x; val lpy = localPeakCanvas!!.y
                            if (lpx in paddingStart..(paddingStart + chartWidth) && lpy in paddingTop..(paddingTop + chartHeight)) {
                                val tri = Path().apply { moveTo(lpx, lpy - 4.dp.toPx()); lineTo(lpx - 4.dp.toPx(), lpy - 12.dp.toPx()); lineTo(lpx + 4.dp.toPx(), lpy - 12.dp.toPx()); close() }
                                drawPath(tri, color = curveColor)
                                val pText = textMeasurer.measure(String.format("%.1f", localPeakRaw!!.first), style = TextStyle(color = curveColor, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                drawText(pText, topLeft = Offset(lpx - pText.size.width / 2, lpy - 26.dp.toPx()))
                            }
                        }
                    }

                    visibleSpectra.forEachIndexed { i, spec ->
                        val isRef = spec.id == viewModel.referenceSpectrumId
                        drawAndDetect(spec.displayData, spec.color, spec.name, i * offsetStep, isRef)
                    }
                    if (hasLiveData) drawAndDetect(liveData, AccentCyan, "Live Acquisition", 0.0)

                    closestPoint?.let { (cOffset, cPoint) ->
                        if (cOffset.x in paddingStart..(paddingStart + chartWidth) && cOffset.y in paddingTop..(paddingTop + chartHeight)) {
                            drawLine(color = closestColor, start = Offset(cOffset.x, paddingTop), end = Offset(cOffset.x, paddingTop + chartHeight), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                            drawLine(color = closestColor, start = Offset(paddingStart, cOffset.y), end = Offset(paddingStart + chartWidth, cOffset.y), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                            drawCircle(color = closestColor, radius = 4.dp.toPx(), center = cOffset)

                            val tooltipText = "$closestName\nX: ${cPoint.first}\nY: ${cPoint.second}"
                            val tr = textMeasurer.measure(tooltipText, style = TextStyle(color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            val tw = tr.size.width + 30f; val th = tr.size.height + 20f

                            var tLeft = cOffset.x + 15f; var tTop = cOffset.y - th - 15f
                            if (tLeft + tw > paddingStart + chartWidth) tLeft = cOffset.x - tw - 15f
                            if (tTop < paddingTop) tTop = cOffset.y + 15f

                            drawRoundRect(color = closestColor.copy(alpha = 0.2f), topLeft = Offset(tLeft, tTop), size = Size(tw, th), cornerRadius = CornerRadius(4.dp.toPx()))
                            drawRoundRect(color = closestColor, topLeft = Offset(tLeft, tTop), size = Size(tw, th), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
                            drawText(tr, topLeft = Offset(tLeft + 15f, tTop + 10f))
                        }
                    }
                }

                if (showMiniMap && (visibleSpectra.isNotEmpty() || hasLiveData)) {
                    val miniW = 160.dp.toPx(); val miniH = 100.dp.toPx()
                    val miniLeft = paddingStart + 16.dp.toPx(); val miniTop = paddingTop + 16.dp.toPx()

                    drawRoundRect(color = Color(0xFF1E2D4A).copy(alpha=0.85f), topLeft = Offset(miniLeft, miniTop), size = Size(miniW, miniH), cornerRadius = CornerRadius(4.dp.toPx()))
                    drawRoundRect(color = BorderDark, topLeft = Offset(miniLeft, miniTop), size = Size(miniW, miniH), style = Stroke(1f), cornerRadius = CornerRadius(4.dp.toPx()))

                    visibleSpectra.forEachIndexed { i, spec ->
                        val shift = i * offsetStep
                        val pPath = Path()
                        spec.displayData.forEachIndexed { ptIndex, pt ->
                            val x = miniLeft + ((pt.first - minX) / rangeX).toFloat() * miniW
                            val y = miniTop + miniH - ((pt.second + shift - minY) / (maxY - minY)).toFloat() * miniH
                            if (ptIndex == 0) pPath.moveTo(x, y) else pPath.lineTo(x, y)
                        }
                        drawPath(pPath, color = spec.color.copy(alpha = 0.6f), style = Stroke(1f))
                    }

                    val vx1 = miniLeft + ((viewMinX - minX) / rangeX).coerceIn(0.0, 1.0).toFloat() * miniW
                    val vx2 = miniLeft + ((viewMaxX - minX) / rangeX).coerceIn(0.0, 1.0).toFloat() * miniW
                    val vy1 = miniTop + miniH - ((viewMaxY - minY) / (maxY - minY)).coerceIn(0.0, 1.0).toFloat() * miniH
                    val vy2 = miniTop + miniH - ((viewMinY - minY) / (maxY - minY)).coerceIn(0.0, 1.0).toFloat() * miniH

                    drawRect(color = AccentCyan.copy(alpha = 0.2f), topLeft = Offset(vx1, vy1), size = Size(vx2 - vx1, vy2 - vy1))
                    drawRect(color = AccentCyan, topLeft = Offset(vx1, vy1), size = Size(vx2 - vx1, vy2 - vy1), style = Stroke(1f))
                }
            }
        }

        val yAxisText = if (lang == AppLanguage.Chinese) "强度" else "INTENSITY"
        val xAxisText = if (lang == AppLanguage.Chinese) "波数 [CM-1]" else "WAVENUMBER [CM-1]"
        val helperText = if (lang == AppLanguage.Chinese) "滚轮: 缩放X轴 | Ctrl+滚轮: 缩放Y轴 | 左键: X轴平移" else "Scroll: Zoom X | Ctrl+Scroll: Zoom Y | Left Click: Pan X"

        Text(yAxisText, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp))
        Text(xAxisText, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))

        val isPipelineActive = viewModel.pipelineSgSmooth || viewModel.pipelineSnv || viewModel.pipeline1stDeriv || viewModel.referenceSpectrumId != "NONE"
        if (isPipelineActive) {
            Text("PIPELINE ACTIVE", color = WarningOrange.copy(alpha = 0.3f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp)
                .background(PanelBg.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showPeaks = !showPeaks }) {
                Icon(if(showPeaks) Icons.Default.FilterCenterFocus else Icons.Default.FilterNone, contentDescription = null, tint = if(showPeaks) AccentCyan else TextMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("自动寻峰", color = if(showPeaks) AccentCyan else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showMiniMap = !showMiniMap }) {
                Icon(Icons.Default.Map, contentDescription = null, tint = if(showMiniMap) AccentCyan else TextMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("概览图", color = if(showMiniMap) AccentCyan else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("层叠", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = stackOffsetRatio, onValueChange = { stackOffsetRatio = it }, valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = BorderDark),
                    modifier = Modifier.width(70.dp).height(24.dp)
                )
            }

            if (scaleX > 1f || scaleY > 1f || offsetX != 0f || offsetY != 0f || stackOffsetRatio > 0f) {
                Box(modifier = Modifier.width(1.dp).height(12.dp).background(BorderDark))
                Icon(Icons.Default.Refresh, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp).clickable {
                    scaleX = 1f; scaleY = 1f; offsetX = 0f; offsetY = 0f; stackOffsetRatio = 0f
                })
            }
        }
    }
}

@Composable
private fun AcquisitionProgressBar(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage
    val animatedProgress by animateFloatAsState(targetValue = viewModel.progress, animationSpec = tween(durationMillis = 300, easing = LinearEasing))

    Row(
        modifier = Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val statusText = if (viewModel.isAcquiring) { if (lang == AppLanguage.Chinese) "采集中" else "ACQUIRING" } else { if (lang == AppLanguage.Chinese) "待机就绪" else "READY" }
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
                Icon(Icons.Default.Settings, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(if (viewModel.isBoardOpened) "READY" else "OFFLINE", color = if (viewModel.isBoardOpened) AccentCyan else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            ConfigItemRow(if (lang == AppLanguage.Chinese) "波段范围" else "WAVE RANGE", "${config.params.startWave} - ${config.params.stopWave}")
            ConfigItemRow(if (lang == AppLanguage.Chinese) "分辨率" else "RESOLUTION", resText)
            ConfigItemRow(if (lang == AppLanguage.Chinese) "硬件增益" else "GAIN", gainVal)
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