package com.wcg.app.specapp.quantitative.algorithm

import kotlin.math.log10

object MathPreprocessor {
    /**
     * 将原始光谱转化为吸光度: A = -log10( (Sample - Dark) / (Reference - Dark) )
     */
    fun calculateAbsorbance(sample: DoubleArray, dark: DoubleArray, reference: DoubleArray): DoubleArray {
        require(sample.size == dark.size && sample.size == reference.size) {
            "数据对齐失败：Sample, Dark, Reference 数组长度不一致"
        }
        val absorbanceArray = DoubleArray(sample.size)
        for (i in sample.indices) {
            val numerator = sample[i] - dark[i]
            val denominator = reference[i] - dark[i]
            // Epsilon 保护，防止除 0
            val t = if (denominator > 1e-9) numerator / denominator else 1e-9
            val safeTransmittance = t.coerceIn(1e-5, 1.0)
            absorbanceArray[i] = -log10(safeTransmittance)
        }
        return absorbanceArray
    }
}