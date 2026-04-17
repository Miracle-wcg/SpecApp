package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {
        // 头部标题栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (lang == AppLanguage.Chinese) "高精度验证分析系统" else "High-Precision Validation System",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite
            )
        }

        Text(
            text = if (lang == AppLanguage.Chinese) "执行 X 轴物理标定校验与 Kahan 高精度浮点误差计算" else "X-axis calibration check & Kahan high-precision floating-point calculation.",
            fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(top = 8.dp, start = 40.dp, bottom = 32.dp)
        )

        // 1. 文件选择区域 - 优化按钮亮度
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FileSelectorCard(
                label = if (lang == AppLanguage.Chinese) "基准光谱 (ABB Reference)" else "Reference (ABB)",
                path = viewModel.refFilePathForComparison,
                onSelect = { viewModel.refFilePathForComparison = it },
                lang = lang,
                modifier = Modifier.weight(1f)
            )
            FileSelectorCard(
                label = if (lang == AppLanguage.Chinese) "待测光谱 (DEV Target)" else "Target (DEV)",
                path = viewModel.targetFilePathForComparison,
                onSelect = { viewModel.targetFilePathForComparison = it },
                lang = lang,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 验证按钮
        Button(
            onClick = { viewModel.runDataComparison() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (lang == AppLanguage.Chinese) "执行高精度验证系统比对" else "Run High-Precision System Comparison",
                fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. 结果展示 - 增强布局鲁棒性
        viewModel.comparisonResult?.let { result ->
            if (result.isSuccess) {
                ResultContent(result, lang)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, ErrorRed, RoundedCornerShape(8.dp)).padding(16.dp)
                ) {
                    Text("❌ ${result.message}", color = ErrorRed, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ResultContent(res: ComparisonResult, lang: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // 校验通过状态
        Row(modifier = Modifier.fillMaxWidth().background(SuccessGreen.copy(0.1f), RoundedCornerShape(6.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if(lang == AppLanguage.Chinese) "X 轴对齐校验通过。有效对比点数: ${res.pointCount}" else "X-axis aligned. Valid data points: ${res.pointCount}",
                color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
        }

        // 分组 1
        MetricGroup(if(lang == AppLanguage.Chinese) "【1】 整体平均误差与漂移范围" else "【1】 Global Offset & Drift", listOf(
            MetricItem(if(lang == AppLanguage.Chinese) "MBE (系统性漂移)" else "MBE (Systematic Bias)", formatV(res.mbe), "≈ 0.0", evalAbs(res.mbe, 1e-6, 1e-4)),
            MetricItem(if(lang == AppLanguage.Chinese) "MAE (平均误差)" else "MAE (Mean Abs Error)", formatV(res.mae), "< 1.05e-04", evalAbs(res.mae, 1e-5, 1.05e-4)),
            MetricItem(if(lang == AppLanguage.Chinese) "RMSE (均方根误差)" else "RMSE", formatV(res.rmse), "< 1.05e-04", evalAbs(res.rmse, 1e-5, 1.05e-4))
        ), lang)

        // 分组 2
        MetricGroup(if(lang == AppLanguage.Chinese) "【2】 最大误差范围与波段位置排查" else "【2】 Max Error & Peak Position", listOf(
            MetricItem(if(lang == AppLanguage.Chinese) "MaxAE (最大绝对误差)" else "MaxAE (Max Abs Error)", formatV(res.maxAe), "< 3.16e-04", evalAbs(res.maxAe, 1e-4, 3.16e-4)),
            SubItem(if(lang == AppLanguage.Chinese) "最大偏差发生位置" else "Peak Error Position", "X = ${String.format("%.2f", res.maxAePos)} cm⁻¹"),
            SubItem(if(lang == AppLanguage.Chinese) "最大正偏离(突起)" else "Max Positive Dev", "${formatV(res.maxPosDev)} (X=${String.format("%.2f", res.maxPosDevPos)})"),
            SubItem(if(lang == AppLanguage.Chinese) "最大负偏离(凹陷)" else "Max Negative Dev", "${formatV(res.maxNegDev)} (X=${String.format("%.2f", res.maxNegDevPos)})")
        ), lang)

        // 分组 3
        MetricGroup(if(lang == AppLanguage.Chinese) "【3】 相对误差与空间相似度" else "【3】 Relative Error & Similarity", listOf(
            MetricItem(if(lang == AppLanguage.Chinese) "R² (决定系数)" else "R-Squared", String.format("%.6f", res.rSquared), "> 0.9999", evalInverse(1 - res.rSquared, 1e-5, 1e-4)),
            MetricItem(if(lang == AppLanguage.Chinese) "MAPE (平均相对百分比)" else "MAPE", String.format("%.4f %%", res.mape), "< 1.00 %", evalAbs(res.mape, 1.0, 5.0)),
            MetricItem(if(lang == AppLanguage.Chinese) "Cosine (空间相似度)" else "Cosine Similarity", String.format("%.6f %%", res.cosineSimilarity), "> 99.99 %", evalInverse(100 - res.cosineSimilarity, 0.001, 0.01))
        ), lang)

        // 分组 4
        MetricGroup(if(lang == AppLanguage.Chinese) "【4】 误差水位线分布 (临界值)" else "【4】 Percentile Thresholds", listOf(
            SubItem(if(lang == AppLanguage.Chinese) "[中位代表] 50% 数据点绝对误差小于" else "[Median] 50% data error <", formatV(res.p50)),
            SubItem(if(lang == AppLanguage.Chinese) "[绝大数界] 90% 数据点绝对误差小于" else "[Boundary] 90% data error <", formatV(res.p90)),
            SubItem(if(lang == AppLanguage.Chinese) "[极值边界] 99% 数据点绝对误差小于" else "[Extreme] 99% data error <", formatV(res.p99))
        ), lang)
    }
}

@Composable
private fun MetricGroup(title: String, items: List<Any>, lang: AppLanguage) {
    Column(modifier = Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(6.dp)).border(1.dp, BorderDark, RoundedCornerShape(6.dp))) {
        Box(modifier = Modifier.fillMaxWidth().background(BorderDark.copy(0.5f)).padding(vertical = 10.dp, horizontal = 16.dp)) {
            Text(title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(16.dp)) {
            items.forEach { item ->
                when (item) {
                    is MetricItem -> MetricRow(item, lang)
                    is SubItem -> SubRow(item)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(item: MetricItem, lang: AppLanguage) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // 提升权重至 2.2f，防止中英文长文本换行
        Text(item.label, modifier = Modifier.weight(2.2f), color = TextMuted, fontSize = 13.sp)
        Text(item.value, modifier = Modifier.weight(1.3f), color = AccentCyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            text = if(lang == AppLanguage.Chinese) "| 界限: ${item.target}" else "| Limit: ${item.target}",
            modifier = Modifier.weight(1.2f), color = TextMuted, fontSize = 12.sp
        )

        val statusColor = when(item.status) {
            DiagnosticLevel.EXCELLENT -> SuccessGreen
            DiagnosticLevel.ACCEPTABLE -> AccentCyan
            DiagnosticLevel.WARNING -> ErrorRed
        }
        val statusText = if(lang == AppLanguage.Chinese) "[${item.status.labelZh}]" else "[${item.status.labelEn}]"

        Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun SubRow(item: SubItem) {
    // 移除 maxLines，允许长文本自动换行或完全展开
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(start = 12.dp)) {
        Text("↳ ${item.label} : ", color = TextMuted, fontSize = 13.sp)
        Text(item.value, color = TextWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FileSelectorCard(label: String, path: String, onSelect: (String) -> Unit, lang: AppLanguage, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp), fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).height(42.dp).background(BgDark, RoundedCornerShape(4.dp)).border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (path.isEmpty()) (if(lang == AppLanguage.Chinese) "未选择..." else "Unselected...") else path.substringAfterLast("\\").substringAfterLast("/"),
                    color = if (path.isEmpty()) TextMuted else AccentCyan,
                    fontSize = 13.sp, maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 优化浏览按钮：显式设置 contentColor 为白色，增加边框对比
            OutlinedButton(
                onClick = {
                    val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("SPC Spectrum", "spc") }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) onSelect(chooser.selectedFile.absolutePath)
                },
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                modifier = Modifier.height(42.dp)
            ) {
                Text(if(lang == AppLanguage.Chinese) "浏览" else "Browse", fontSize = 13.sp)
            }
        }
    }
}

private fun formatV(v: Double) = if (java.lang.Math.abs(v) < 1e-15) "0.000000e+00" else String.format("%.6e", v)
private fun evalAbs(v: Double, exc: Double, acc: Double) = if (java.lang.Math.abs(v) <= exc) DiagnosticLevel.EXCELLENT else if (java.lang.Math.abs(v) <= acc) DiagnosticLevel.ACCEPTABLE else DiagnosticLevel.WARNING
private fun evalInverse(v: Double, exc: Double, acc: Double) = evalAbs(v, exc, acc)

data class MetricItem(val label: String, val value: String, val target: String, val status: DiagnosticLevel)
data class SubItem(val label: String, val value: String)