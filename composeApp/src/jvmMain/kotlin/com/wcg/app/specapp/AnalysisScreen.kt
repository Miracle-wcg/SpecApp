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
            DataExportPanel(modifier = Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun HeaderAndActions(viewModel: SpectrometerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("光谱控制中心 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Spectral Analysis",
                    color = AccentCyan,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                "当前序列: SCAN_PROC_2024_08_12_001",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { viewModel.stopAcquisition() },
                modifier = Modifier.height(44.dp), shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = TextWhite
                ),
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

@Composable
private fun ChartSection(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(PanelBg)
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
    ) {
        Text(
            "ABSORBANCE [AU]",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-30).dp).rotate(-90f)
        )

        val data = viewModel.spectrumData
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 60.dp, bottom = 60.dp, top = 40.dp, end = 40.dp)) {
            val width = size.width
            val height = size.height

            // 绘制网格
            val gridLines = 6
            for (i in 0..gridLines) {
                val y = i * (height / gridLines)
                drawLine(
                    color = BorderDark,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                val x = i * (width / gridLines)
                drawLine(
                    color = BorderDark,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }

            // 动态解析真实数据的边界
            if (data.isNotEmpty()) {
                val minX = data.minOf { it.first }
                val maxX = data.maxOf { it.first }
                val minY = data.minOf { it.second }
                val maxY = data.maxOf { it.second } * 1.1 // 顶部留白 10%

                val path = Path()
                data.forEachIndexed { index, point ->
                    // 波数通常是高到低(从右向左)，这里做标准映射
                    val px = ((maxX - point.first) / (maxX - minX)).toFloat() * width
                    val py = height - ((point.second - minY) / (maxY - minY)).toFloat() * height

                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path = path, color = AccentCyan, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Text(
            "WAVENUMBER [CM-1]",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
        // 峰值悬浮框
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                .background(Color(0xFF2A364B).copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(12.dp)
        ) {
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

        // 进度条
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, end = 40.dp, bottom = 40.dp)
                .fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("扫描进度 SCAN PROGRESS", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${(viewModel.progress * 100).toInt()}%",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 修复截图中提示的弃用警告，将 progress 包装为 Lambda
            LinearProgressIndicator(
                progress = { viewModel.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                color = AccentCyan,
                trackColor = BorderDark
            )
        }
    }
}

@Composable
private fun InstrumentConfigPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📡", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仪器配置 ", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Instrument Configuration", color = TextWhite, fontSize = 16.sp)
                }
                Box(
                    modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("SYSTEM READY", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            val isAcquiring = viewModel.isAcquiring
            Row(modifier = Modifier.weight(1f)) {
                ConfigCol(
                    "OBJECTID",
                    "SPEC-2024-AL-004",
                    "LEGACY STATUS",
                    "Active / Standard",
                    "分辨率 INST. RESOLUTION",
                    "4.0 cm-1",
                    Modifier.weight(1f)
                )
                ConfigCol(
                    "操作员 OPERATOR",
                    "Admin-042",
                    "运行状态 INST. STATUS",
                    if (isAcquiring) "采集进行中 Acquiring" else "待机 Standby",
                    "趋势时间 INST. TIME TND",
                    "14:02:33",
                    Modifier.weight(1f),
                    isAcquiring
                )
                ConfigCol(
                    "部门 DEPARTMENT",
                    "核心分析实验室 Core-Lab",
                    "SWEEP COUNT (SWEEP CO)",
                    "${viewModel.currentSweep} / ${viewModel.totalSweeps}",
                    "数据点数 INST. NPTS",
                    "16,384",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DataExportPanel(modifier: Modifier = Modifier) {
    // 引入状态管理
    var savePath by remember { mutableStateOf("C:/SpectraData/Exports/2024-08-") }

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("➔", color = TextWhite, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("数据导出 ", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Data Export", color = TextWhite, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.weight(1f))

            // 修复崩溃点：显式命名参数，避免位置映射错误导致类型转换异常
            DarkTextField(
                label = "保存路径 SAVE PATH",
                value = savePath,
                onValueChange = { savePath = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("导出格式 EXPORT FORMAT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormatButton("TXT", "文本格式", true, Modifier.weight(1f))
                FormatButton("SPC", "专业格式", false, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = TextWhite)
            ) {
                Text("⬇ 立即执行导出 Export Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConfigCol(
    l1: String,
    v1: String,
    l2: String,
    v2: String,
    l3: String,
    v3: String,
    modifier: Modifier,
    isHighlightV2: Boolean = false
) {
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
            if (highlight) {
                Box(
                    modifier = Modifier.size(6.dp).background(WarningOrange, RoundedCornerShape(50))
                ); Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                value,
                color = if (highlight) WarningOrange else TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FormatButton(title: String, subtitle: String, isSelected: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF1E2D4A) else BgDark)
            .border(1.dp, if (isSelected) AccentCyan else BorderDark, RoundedCornerShape(4.dp)).clickable { },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(subtitle, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}