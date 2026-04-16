package com.wcg.app.specapp.business

import com.spectrometer.subsystem.SpectrumStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 评估指标的数据模型
data class ComparisonResult(
    val isSuccess: Boolean,
    val message: String = "",
    val maxAbsError: Double = 0.0,
    val avgAbsError: Double = 0.0,
    val rmse: Double = 0.0,
    val maxRelErrorTop20: Double = 0.0,
    val pointCount: Int = 0
) {
    // 诊断逻辑：判定是否达到优秀(Excellent)或及格(Acceptable)标准
    fun maxAbsStatus() = evaluateDiagnostic(maxAbsError, 1E-5, 1E-4)
    fun avgAbsStatus() = evaluateDiagnostic(avgAbsError, 1E-6, 1E-5)
    fun rmseStatus() = evaluateDiagnostic(rmse, 1E-6, 1E-5)
    fun maxRelStatus() = evaluateDiagnostic(maxRelErrorTop20, 0.0005, 0.005)

    private fun evaluateDiagnostic(value: Double, excellentThreshold: Double, acceptableThreshold: Double): DiagnosticLevel {
        return when {
            value <= excellentThreshold -> DiagnosticLevel.EXCELLENT
            value <= acceptableThreshold -> DiagnosticLevel.ACCEPTABLE
            else -> DiagnosticLevel.WARNING
        }
    }
}

enum class DiagnosticLevel(val labelEn: String, val labelZh: String) {
    EXCELLENT("Excellent", "优 / Pass"),
    ACCEPTABLE("Acceptable", "良 / Accept"),
    WARNING("Warning", "差 / Warn")
}

object DataComparisonService {

    private val storage = SpectrumStorage()

    suspend fun evaluateAccuracy(refFilePath: String, targetFilePath: String): ComparisonResult = withContext(Dispatchers.IO) {
        try {
            // 复用已有底层读取模块提取数据
            val dataRef = storage.readFromFile(refFilePath)
                ?: return@withContext ComparisonResult(false, "无法读取参考文件: $refFilePath")
            val dataTgt = storage.readFromFile(targetFilePath)
                ?: return@withContext ComparisonResult(false, "无法读取目标文件: $targetFilePath")

            val refY = dataRef[1]
            val tgtY = dataTgt[1]

            if (refY.size != tgtY.size) {
                return@withContext ComparisonResult(false, "数据点数不匹配！基准: ${refY.size}, 目标: ${tgtY.size}")
            }

            val length = refY.size
            var maxAbsError = 0.0
            var sumAbsError = 0.0
            var sumSqError = 0.0

            // 1. 全局绝对误差统计
            for (i in 0 until length) {
                val y1 = refY[i]
                val y2 = tgtY[i]
                val absErr = abs(y1 - y2)

                if (absErr > maxAbsError) maxAbsError = absErr
                sumAbsError += absErr
                sumSqError += (absErr * absErr)
            }

            val avgAbsError = sumAbsError / length
            val rmse = sqrt(sumSqError / length)

            // 2. 高信号区相对误差评估 (过滤底部噪声)
            // 复制基准 Y 轴用于排序，获取 P80 阈值（前 20% 的信号强度下限）
            val sortedRefY = refY.map { abs(it) }.sorted()
            val p80Index = max(0, min((0.80 * length).toInt(), length - 1))
            val thresholdY = sortedRefY[p80Index]

            var maxRelError = 0.0
            for (i in 0 until length) {
                val y1 = refY[i]
                if (abs(y1) >= thresholdY) {
                    val y2 = tgtY[i]
                    val relErr = abs((y1 - y2) / y1)
                    if (relErr > maxRelError) maxRelError = relErr
                }
            }

            ComparisonResult(
                isSuccess = true,
                maxAbsError = maxAbsError,
                avgAbsError = avgAbsError,
                rmse = rmse,
                maxRelErrorTop20 = maxRelError,
                pointCount = length
            )
        } catch (e: Exception) {
            ComparisonResult(false, "评估异常: ${e.message}")
        }
    }
}