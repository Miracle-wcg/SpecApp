package com.wcg.app.specapp.quantitative.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.*
import com.wcg.app.specapp.quantitative.model.ProcessState
import com.wcg.app.specapp.utils.NativeDialogUtils
import kotlin.math.abs

@Composable
fun QuantitativeScreen(appViewModel: SpectrometerViewModel) {
    val viewModel = appViewModel.quantitativeViewModel
    val lang = appViewModel.appLanguage

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

        // 👈 左侧列：环境配置与样本工作区
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (lang == AppLanguage.Chinese) "离线数据实验室 " else "Data Analysis Lab ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (lang == AppLanguage.Chinese) "/ 批量分析" else "/ Batch Analysis", color = AccentCyan, fontSize = 16.sp, modifier = Modifier.padding(bottom = 2.dp))
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderDark)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (lang == AppLanguage.Chinese) "🛠️ 绑定校准背景" else "🛠️ Bind Reference", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    FilePickerBox(
                        label = if (lang == AppLanguage.Chinese) "选择参比/白板光谱 (Ref)" else "Select Reference Spectrum",
                        selectedName = viewModel.refFile?.name, modifier = Modifier.fillMaxWidth()
                    ) {
                        val files = NativeDialogUtils.pickFiles(if (lang == AppLanguage.Chinese) "选择参比光谱" else "Select Ref Spectrum", false)
                        if (files.isNotEmpty()) viewModel.refFile = files.first()
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderDark)) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lang == AppLanguage.Chinese) "📋 待处理样本" else "📋 Sample Queue", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${viewModel.sampleFiles.size} ${if (lang == AppLanguage.Chinese) "个文件" else "files"}", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val files = NativeDialogUtils.pickFiles(if (lang == AppLanguage.Chinese) "批量导入光谱样本" else "Import Samples", true)
                                viewModel.sampleFiles.addAll(files)
                            },
                            modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648))
                        ) { Text(if (lang == AppLanguage.Chinese) "📂 导入 .spc" else "📂 Import", fontSize = 12.sp) }

                        Button(
                            onClick = { viewModel.clearAll() },
                            modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648), contentColor = DangerRed)
                        ) { Text(if (lang == AppLanguage.Chinese) "清空" else "Clear", fontSize = 12.sp) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(8.dp)) {
                        LazyColumn {
                            items(viewModel.sampleFiles) { file ->
                                Text("📄 ${file.name}", color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 进度条渲染
                    if (viewModel.processState == ProcessState.PROCESSING) {
                        LinearProgressIndicator(progress = { viewModel.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = AccentCyan, trackColor = BgDark)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 🌟 新增：直观的环境确实预警文字
                    if (viewModel.refFile == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️ ", fontSize = 12.sp)
                            Text(if (lang == AppLanguage.Chinese) "未绑定参比背景，分析引擎不可用" else "Reference missing, engine unavailable", color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (viewModel.sampleFiles.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️ ", fontSize = 12.sp)
                            Text(if (lang == AppLanguage.Chinese) "样本队列为空，请导入 .spc 文件" else "Queue is empty, import .spc files", color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 🌟 修改：放开按钮禁用限制（只在正在处理中时禁用），以便用户能点击并触发弹窗
                    Button(
                        onClick = { viewModel.startProcessing() },
                        enabled = viewModel.processState != ProcessState.PROCESSING,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = BgDark,
                            disabledContainerColor = Color(0xFF2B3648),
                            disabledContentColor = TextMuted
                        )
                    ) { Text(if (lang == AppLanguage.Chinese) "▶ 启动智能分析" else "▶ RUN ANALYSIS", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }

        // 👉 右侧列：图表与数据网格
        Column(modifier = Modifier.weight(2.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 3. 吸光度图表区
            Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(PanelBg).border(1.dp, BorderDark, RoundedCornerShape(8.dp))) {
                val data = viewModel.selectedResult?.absorbanceSpectrum?.points ?: emptyList()

                if (data.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (lang == AppLanguage.Chinese) "点击下方表格的成功行以预览其吸光度曲线" else "Select a successful row to preview absorbance spectrum", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    PrecisionAbsorbanceChart(data, lang, modifier = Modifier.fillMaxSize().padding(16.dp))
                    Text(if (lang == AppLanguage.Chinese) "吸光度光谱: ${viewModel.selectedResult?.file?.name}" else "Absorbance: ${viewModel.selectedResult?.file?.name}", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                }
            }

            // 4. 预测结果 6 大指标数据网格
            Box(modifier = Modifier.fillMaxWidth().weight(1.5f).clip(RoundedCornerShape(8.dp)).background(BgDark).border(1.dp, BorderDark, RoundedCornerShape(8.dp))) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A)).padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        GridHeader(if (lang == AppLanguage.Chinese) "样本名称" else "Sample", Modifier.weight(2f))
                        GridHeader(if (lang == AppLanguage.Chinese) "灰分(%)" else "Ash(%)", Modifier.weight(0.8f))
                        GridHeader(if (lang == AppLanguage.Chinese) "挥发分(%)" else "Volatile(%)", Modifier.weight(1f))
                        GridHeader(if (lang == AppLanguage.Chinese) "发热量(MJ/kg)" else "Heat(MJ/kg)", Modifier.weight(1.2f))
                        GridHeader(if (lang == AppLanguage.Chinese) "水分(%)" else "Moisture(%)", Modifier.weight(0.9f))
                        GridHeader(if (lang == AppLanguage.Chinese) "全硫(%)" else "Sulfur(%)", Modifier.weight(0.9f))
                        GridHeader(if (lang == AppLanguage.Chinese) "固定碳(%)" else "Carbon(%)", Modifier.weight(1f))
                        GridHeader(if (lang == AppLanguage.Chinese) "状态" else "Status", Modifier.weight(0.8f))
                    }
                    HorizontalDivider(color = BorderDark, thickness = 1.dp)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.results) { result ->
                            val isSelected = viewModel.selectedResult == result
                            Row(
                                modifier = Modifier.fillMaxWidth().background(if (isSelected) Color(0xFF2A364B) else Color.Transparent).clickable { viewModel.selectedResult = result }.padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(result.file.name, color = TextWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f).padding(end = 8.dp))

                                if (result.isSuccess) {
                                    val ashColor = if (result.ashContent > 15.0) DangerRed else TextWhite
                                    Text(String.format("%.2f", result.ashContent), color = ashColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                                    Text(String.format("%.2f", result.volatileMatter), color = TextWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text(String.format("%.2f", result.calorificValue), color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                                    Text(String.format("%.2f", result.moisture), color = TextWhite, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                    Text(String.format("%.2f", result.sulfur), color = TextWhite, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                    Text(String.format("%.2f", result.fixedCarbon), color = TextWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text("✅", color = Color(0xFF10B981), fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                } else {
                                    Text("-", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                    Text("-", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text("-", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                    Text("-", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                    Text("-", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                    Text("-", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text("❌", color = DangerRed, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                }
                            }
                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================
// 🌟 交互式高精度图表 (无修改)
// =====================================================
@Composable
private fun PrecisionAbsorbanceChart(data: List<Pair<Double, Double>>, lang: AppLanguage, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density

    var hoverX by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Move -> hoverX = event.changes.first().position.x
                        PointerEventType.Exit -> hoverX = null
                    }
                }
            }
        }
    ) {
        val paddingStart = 65f * density
        val paddingBottom = 35f * density
        val paddingTop = 15f * density
        val paddingEnd = 20f * density

        val chartWidth = size.width - paddingStart - paddingEnd
        val chartHeight = size.height - paddingTop - paddingBottom

        drawLine(color = BorderDark, start = Offset(paddingStart, paddingTop), end = Offset(paddingStart, paddingTop + chartHeight), strokeWidth = 2f)
        drawLine(color = BorderDark, start = Offset(paddingStart, paddingTop + chartHeight), end = Offset(paddingStart + chartWidth, paddingTop + chartHeight), strokeWidth = 2f)

        if (data.isNotEmpty()) {
            val minX = data.minOf { it.first }
            val maxX = data.maxOf { it.first }
            val minY = data.minOf { it.second }
            val maxY = data.maxOf { it.second }

            val rangeX = if (maxX == minX) 1.0 else maxX - minX
            val rangeY = if (maxY == minY) 1.0 else maxY - minY

            val adjustedMinY = minY - rangeY * 0.1
            val adjustedMaxY = maxY + rangeY * 0.1
            val renderRangeY = adjustedMaxY - adjustedMinY

            val ySteps = 4
            for (i in 0..ySteps) {
                val yRatio = i.toFloat() / ySteps
                val yPos = paddingTop + chartHeight - (yRatio * chartHeight)
                val yVal = adjustedMinY + yRatio * renderRangeY
                drawLine(color = BorderDark.copy(alpha = 0.4f), start = Offset(paddingStart, yPos), end = Offset(paddingStart + chartWidth, yPos), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                val textLayout = textMeasurer.measure(String.format("%.4f", yVal), style = TextStyle(color = TextMuted, fontSize = 10.sp))
                drawText(textLayout, topLeft = Offset(paddingStart - textLayout.size.width - 15f, yPos - textLayout.size.height / 2))
            }

            val xSteps = 6
            for (i in 0..xSteps) {
                val xRatio = i.toFloat() / xSteps
                val xPos = paddingStart + xRatio * chartWidth
                val xVal = maxX - xRatio * rangeX
                drawLine(color = BorderDark.copy(alpha = 0.4f), start = Offset(xPos, paddingTop), end = Offset(xPos, paddingTop + chartHeight), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                val textLayout = textMeasurer.measure(String.format("%.1f", xVal), style = TextStyle(color = TextMuted, fontSize = 10.sp))
                drawText(textLayout, topLeft = Offset(xPos - textLayout.size.width / 2, paddingTop + chartHeight + 10f))
            }

            val path = Path()
            var closestPoint: Pair<Offset, Pair<Double, Double>>? = null
            var minDistance = Float.MAX_VALUE

            data.forEachIndexed { index, point ->
                val px = paddingStart + ((maxX - point.first) / rangeX).toFloat() * chartWidth
                val py = paddingTop + chartHeight - ((point.second - adjustedMinY) / renderRangeY).toFloat() * chartHeight

                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)

                hoverX?.let { hx ->
                    val dist = abs(px - hx)
                    if (dist < minDistance && hx in paddingStart..(paddingStart + chartWidth)) {
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

                    val xStr = if (lang == AppLanguage.Chinese) "波数: " else "Wave: "
                    val yStr = if (lang == AppLanguage.Chinese) "吸光: " else "Abs: "
                    val tooltipText = "$xStr${String.format("%.1f", cPoint.first)}\n$yStr${String.format("%.4f", cPoint.second)}"

                    val tr = textMeasurer.measure(tooltipText, style = TextStyle(color = BgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    val tw = tr.size.width + 24f
                    val th = tr.size.height + 16f

                    var tLeft = cOffset.x + 12f
                    var tTop = cOffset.y - th - 12f
                    if (tLeft + tw > paddingStart + chartWidth) tLeft = cOffset.x - tw - 12f
                    if (tTop < paddingTop) tTop = cOffset.y + 12f

                    drawRoundRect(color = AccentCyan.copy(alpha = 0.9f), topLeft = Offset(tLeft, tTop), size = Size(tw, th), cornerRadius = CornerRadius(4.dp.toPx()))
                    drawText(tr, topLeft = Offset(tLeft + 12f, tTop + 8f))
                }
            }

            val yAxisLabel = textMeasurer.measure(if (lang == AppLanguage.Chinese) "吸光度 (AU)" else "Absorbance (AU)", style = TextStyle(color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            drawText(yAxisLabel, topLeft = Offset(10f, paddingTop - 15f))

            val xAxisLabel = textMeasurer.measure(if (lang == AppLanguage.Chinese) "波数 (cm⁻¹)" else "Wavenumber (cm⁻¹)", style = TextStyle(color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            drawText(xAxisLabel, topLeft = Offset(paddingStart + chartWidth - xAxisLabel.size.width, paddingTop + chartHeight + 25f))
        }
    }
}

@Composable
private fun GridHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun FilePickerBox(label: String, selectedName: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(4.dp)).background(BgDark).border(1.dp, if (selectedName != null) AccentCyan else BorderDark, RoundedCornerShape(4.dp)).clickable { onClick() }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = selectedName ?: "点击选择文件...", color = if (selectedName != null) TextWhite else TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}