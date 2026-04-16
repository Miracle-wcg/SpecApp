package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.business.ComparisonResult
import com.wcg.app.specapp.business.DiagnosticLevel
import com.wcg.app.specapp.ui.theme.*
import com.wcg.app.specapp.viewmodel.AppLanguage
import com.wcg.app.specapp.viewmodel.SpectrometerViewModel
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DataComparisonScreen(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (lang == AppLanguage.Chinese) "原厂精度交叉验证" else "Data Accuracy Validation",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite
        )
        Text(
            if (lang == AppLanguage.Chinese) "针对底层 C++ 驱动与 App 导出的 SPC/TXT 数据进行字节级无损精度比对。"
            else "Perform byte-level lossless accuracy comparison between ABB C++ Driver and App exported data.",
            fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 文件选择面板
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            FileSelectorCard(
                title = if (lang == AppLanguage.Chinese) "📄 基准文件 (ABB)" else "📄 Reference (ABB)",
                filePath = viewModel.refFilePathForComparison,
                onFileSelected = { viewModel.refFilePathForComparison = it },
                modifier = Modifier.weight(1f)
            )
            FileSelectorCard(
                title = if (lang == AppLanguage.Chinese) "📄 待测文件 (App)" else "📄 Target (App)",
                filePath = viewModel.targetFilePathForComparison,
                onFileSelected = { viewModel.targetFilePathForComparison = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.runDataComparison() },
            enabled = !viewModel.isComparing && viewModel.refFilePathForComparison.isNotEmpty() && viewModel.targetFilePathForComparison.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(48.dp).fillMaxWidth()
        ) {
            Text(
                if (viewModel.isComparing) (if (lang == AppLanguage.Chinese) "正在比对中..." else "Comparing...")
                else (if (lang == AppLanguage.Chinese) "⚡ 开始精度验证" else "⚡ Run Validation"),
                fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 结果展示区域
        viewModel.comparisonResult?.let { result ->
            if (result.isSuccess) {
                ResultPanel(result, lang)
            } else {
                Text(result.message, color = ErrorRed, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FileSelectorCard(title: String, filePath: String, onFileSelected: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.background(PanelBg, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (filePath.isEmpty()) "请选择 .spc 或 .txt 文件..." else filePath.substringAfterLast("\\").substringAfterLast("/"),
                color = if (filePath.isEmpty()) TextMuted else AccentCyan,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    val chooser = JFileChooser().apply {
                        fileFilter = FileNameExtensionFilter("Spectrum Files (*.spc, *.txt)", "spc", "txt")
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        onFileSelected(chooser.selectedFile.absolutePath)
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("浏览", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ResultPanel(result: ComparisonResult, lang: AppLanguage) {
    Column(
        modifier = Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(20.dp)
    ) {
        Text(
            text = if (lang == AppLanguage.Chinese) "验证报告 (数据点: ${result.pointCount})" else "Validation Report (Points: ${result.pointCount})",
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 🌟 1. 全局绝对误差分析区
        Text(
            text = if (lang == AppLanguage.Chinese) "全局绝对误差分析:" else "Global Absolute Error Analysis:",
            color = AccentCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        DiagnosticRow("Max Abs Error (最大绝对误差)", formatValue(result.maxAbsError), "< 1.0E-4", result.maxAbsStatus(), lang)
        DiagnosticRow("Avg Abs Error (平均绝对误差)", formatValue(result.avgAbsError), "< 1.0E-5", result.avgAbsStatus(), lang)
        DiagnosticRow("RMSE (均方根误差)", formatValue(result.rmse), "< 1.0E-5", result.rmseStatus(), lang)

        Spacer(modifier = Modifier.height(20.dp))

        // 🌟 2. 核心高频信号区相对误差评估区
        Text(
            text = if (lang == AppLanguage.Chinese) "核心高频信号区相对误差评估 (P80 阈值以上):" else "Core Signal Relative Error (P80+ Threshold):",
            color = AccentCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        DiagnosticRow("Max Rel Error P80", String.format("%.6f%%", result.maxRelErrorTop20 * 100), "< 0.5%", result.maxRelStatus(), lang)
    }
}

// 🌟 智能数值格式化：如果是完美对齐的极小值/零，直接显示 0.000000；否则用科学计数法展示
private fun formatValue(value: Double): String {
    return if (value == 0.0 || value < 1e-12) {
        "0.000000"
    } else {
        String.format("%.6E", value)
    }
}
@Composable
private fun DiagnosticRow(name: String, value: String, target: String, status: DiagnosticLevel, lang: AppLanguage) {
    val statusColor = when (status) {
        DiagnosticLevel.EXCELLENT -> SuccessGreen
        DiagnosticLevel.ACCEPTABLE -> AccentCyan
        DiagnosticLevel.WARNING -> ErrorRed
    }

    val statusLabel = if (lang == AppLanguage.Chinese) status.labelZh else status.labelEn

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
        Text(value, color = TextWhite, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
        Text("标准: $target", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(100.dp))
    }
}