package com.wcg.app.specapp.quantitative.model

import java.io.File

// 预测结果实体类
data class PredictionResult(
    val file: File,
    val ashContent: Double = 0.0,       // 灰分
    val volatileMatter: Double = 0.0,   // 挥发分
    val calorificValue: Double = 0.0,   // 发热量
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

// UI 整体处理状态
enum class ProcessState {
    IDLE, PROCESSING, COMPLETED, ERROR
}