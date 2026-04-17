package com.wcg.app.specapp.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.*

class AnalyzedSpectrum(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val name: String,
    val data: List<Pair<Double, Double>>,
    val color: Color,
    initialVisible: Boolean = true
) {
    var isVisible by mutableStateOf(initialVisible)
    var displayData by mutableStateOf(data)
}

enum class AppScreen(val titleEn: String, val titleZh: String, val icon: ImageVector) {
    Analysis("Analysis", "采集分析", Icons.Default.List),
    Setup("Setup", "仪器设置", Icons.Default.Build),
    AutoScan("Auto Scan", "自动采集", Icons.Default.Refresh),
    Quantitative("Quantitative", "智能分析", Icons.Default.Search),
    Comparison("Data Validation", "精度验证", Icons.Default.Check),
    Settings("Settings", "系统设置", Icons.Default.Settings);

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.Chinese) titleZh else titleEn
}

enum class AutoScanMode { Continuous, Scheduled }
enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }