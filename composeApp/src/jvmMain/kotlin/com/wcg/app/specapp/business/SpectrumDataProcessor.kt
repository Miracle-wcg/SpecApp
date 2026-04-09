package com.wcg.app.specapp.business

data class ProcessedSpectrum(
    val points: List<Pair<Double, Double>>,
    val peakX: String,
    val peakY: String
)

object SpectrumDataProcessor {
    /**
     * 将底层的 DoubleArray 转换为 UI 层需要的 List 结构，并自动计算峰值
     */
    fun process(xyData: Array<DoubleArray>?): ProcessedSpectrum? {
        if (xyData == null || xyData.size != 2 || xyData[0].isEmpty()) return null

        val xArray = xyData[0]
        val yArray = xyData[1]

        val points = xArray.zip(yArray).toList()
        val maxPoint = points.maxByOrNull { it.second }

        return ProcessedSpectrum(
            points = points,
            peakX = maxPoint?.let { String.format("%.2f", it.first) } ?: "0.00",
            peakY = maxPoint?.let { String.format("%.4f", it.second) } ?: "0.000"
        )
    }
}