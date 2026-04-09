package com.wcg.app.specapp.quantitative.model

import com.wcg.app.specapp.business.ProcessedSpectrum
import java.io.File

// 预测结果实体类 (扩充至 6 种煤质指标)
data class PredictionResult(
    val file: File,
    val ashContent: Double = 0.0,       // 灰分 (%)
    val volatileMatter: Double = 0.0,   // 挥发分 (%)
    val calorificValue: Double = 0.0,   // 发热量 (MJ/kg)
    val moisture: Double = 0.0,         // 水分 (%)
    val sulfur: Double = 0.0,           // 全硫 (%)
    val fixedCarbon: Double = 0.0,      // 固定碳 (%)

    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val absorbanceSpectrum: ProcessedSpectrum? = null // 用于在图表中回显绘制
)

// UI 整体处理状态
enum class ProcessState {
    IDLE, PROCESSING, COMPLETED, ERROR
}