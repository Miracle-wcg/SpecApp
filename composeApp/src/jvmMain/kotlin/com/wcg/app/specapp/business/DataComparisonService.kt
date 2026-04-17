package com.wcg.app.specapp.business

import com.spectrometer.subsystem.SpectrumStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

data class ComparisonResult(
    val isSuccess: Boolean,
    val message: String = "",
    val pointCount: Int = 0,

    // 【1】 整体平均误差与漂移 (系统性状态)
    val mbe: Double = 0.0,
    val mae: Double = 0.0,
    val rmse: Double = 0.0,

    // 【2】 最大误差与位置排查
    val maxAe: Double = 0.0,
    val maxAePos: Double = 0.0,
    val maxPosDev: Double = 0.0,
    val maxPosDevPos: Double = 0.0,
    val maxNegDev: Double = 0.0,
    val maxNegDevPos: Double = 0.0,

    // 【3】 统计学一致性与相似度 (化学计量学黄金指标)
    val pearsonR: Double = 0.0,     // 拟合优度
    val rSquared: Double = 0.0,     // 决定系数
    val mape: Double = 0.0,
    val cosineSimilarity: Double = 0.0,

    // 【4】 误差水位线
    val p50: Double = 0.0,
    val p90: Double = 0.0,
    val p99: Double = 0.0
)

object DataComparisonService {
    private val storage = SpectrumStorage()

    suspend fun evaluateAccuracy(refPath: String, tgtPath: String): ComparisonResult = withContext(Dispatchers.IO) {
        try {
            val dataRef = storage.readFromFile(refPath) ?: return@withContext ComparisonResult(false, "读取基准文件失败")
            val dataTgt = storage.readFromFile(tgtPath) ?: return@withContext ComparisonResult(false, "读取待测文件失败")

            val xRef = dataRef[0]; val yRef = dataRef[1]
            val xTgt = dataTgt[0]; val yTgt = dataTgt[1]

            val n = yRef.size
            if (n != yTgt.size) return@withContext ComparisonResult(false, "数据点数不匹配: 标曲=$n, 待测=${yTgt.size}")

            // 🌟 【核心屏障】：X 轴物理标定绝对对齐校验
            // 如果 X 轴波数不同，Y 轴直接相减是没有物理意义的（会混入导数误差）
            for (i in 0 until n) {
                if (abs(xRef[i] - xTgt[i]) > 1e-4) {
                    return@withContext ComparisonResult(false, "X轴未对齐熔断！位于点索引 [$i]: 标曲=${xRef[i]}, 待测=${xTgt[i]}")
                }
            }

            val diffs = DoubleArray(n) { yTgt[it] - yRef[it] }
            val absDiffs = DoubleArray(n) { abs(diffs[it]) }

            // 1. Kahan Summation 高精度累加计算 RMSE、MAE 和 MBE
            // 防止几万个 10^-6 级别的数据在普通 Double 连加时丢失尾数精度
            val mbe = kahanSum(diffs) / n
            val mae = kahanSum(absDiffs) / n
            val sumSqErr = kahanSum(DoubleArray(n) { diffs[it] * diffs[it] })
            val rmse = sqrt(sumSqErr / n)

            // 2. 极值溯源
            var maxAe = 0.0; var maxAePos = 0.0
            var maxPos = -Double.MAX_VALUE; var maxPosPos = 0.0
            var maxNeg = Double.MAX_VALUE; var maxNegPos = 0.0

            for (i in 0 until n) {
                if (absDiffs[i] > maxAe) { maxAe = absDiffs[i]; maxAePos = xRef[i] }
                if (diffs[i] > maxPos) { maxPos = diffs[i]; maxPosPos = xRef[i] }
                if (diffs[i] < maxNeg) { maxNeg = diffs[i]; maxNegPos = xRef[i] }
            }

            // 3. 化学计量学指标 (Pearson R & R-Squared)
            val meanRef = kahanSum(yRef) / n
            val meanTgt = kahanSum(yTgt) / n

            var sumRefTgtDev = 0.0
            var sumSqRefDev = 0.0
            var sumSqTgtDev = 0.0

            var sumMape = 0.0
            var dotProduct = 0.0; var normRef = 0.0; var normTgt = 0.0

            for (i in 0 until n) {
                val refDev = yRef[i] - meanRef
                val tgtDev = yTgt[i] - meanTgt
                sumRefTgtDev += refDev * tgtDev
                sumSqRefDev += refDev * refDev
                sumSqTgtDev += tgtDev * tgtDev

                // 相似度与MAPE
                sumMape += absDiffs[i] / (abs(yRef[i]) + 1e-10) // 加上极小值防止除以0
                dotProduct += yRef[i] * yTgt[i]
                normRef += yRef[i] * yRef[i]
                normTgt += yTgt[i] * yTgt[i]
            }

            // Pearson 相关系数 r
            val pearsonR = sumRefTgtDev / sqrt(sumSqRefDev * sumSqTgtDev)
            // 决定系数 R^2 = 1 - (SS_res / SS_tot)
            val rSquared = 1.0 - (sumSqErr / sumSqRefDev)

            val mape = (sumMape / n) * 100
            val cosine = (dotProduct / (sqrt(normRef) * sqrt(normTgt))) * 100

            // 4. 水位线分布 (高精度快速排序)
            val sortedAbs = absDiffs.sorted()

            ComparisonResult(
                isSuccess = true, pointCount = n,
                mbe = mbe, mae = mae, rmse = rmse,
                maxAe = maxAe, maxAePos = maxAePos,
                maxPosDev = maxPos, maxPosDevPos = maxPosPos,
                maxNegDev = maxNeg, maxNegDevPos = maxNegPos,
                pearsonR = pearsonR, rSquared = rSquared,
                mape = mape, cosineSimilarity = cosine,
                p50 = sortedAbs[(n * 0.50).toInt()],
                p90 = sortedAbs[(n * 0.90).toInt()],
                p99 = sortedAbs[(n * 0.99).toInt()]
            )
        } catch (e: Exception) { ComparisonResult(false, e.message ?: "未知异常") }
    }

    /**
     * Kahan 求和算法：专为解决海量极小浮点数相加时的舍入误差问题
     */
    private fun kahanSum(array: DoubleArray): Double {
        var sum = 0.0
        var c = 0.0 // 补偿项（存放丢失的低位精度）
        for (i in array.indices) {
            val y = array[i] - c
            val t = sum + y
            c = (t - sum) - y
            sum = t
        }
        return sum
    }
}

enum class DiagnosticLevel(val labelEn: String, val labelZh: String) {
    EXCELLENT("Excellent", "优 / Pass"),
    ACCEPTABLE("Acceptable", "良 / Accept"),
    WARNING("Warning", "差 / Warn")
}