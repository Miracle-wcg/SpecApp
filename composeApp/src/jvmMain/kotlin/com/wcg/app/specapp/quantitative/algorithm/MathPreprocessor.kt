package com.wcg.app.specapp.quantitative.algorithm

import kotlin.math.log10
import kotlin.math.sqrt

object MathPreprocessor {

    /**
     * 1. 基础吸光度转换与对齐 (已修复除0与越界)
     */
    fun calculateAbsorbance(sampleX: DoubleArray, sampleY: DoubleArray, refX: DoubleArray, refY: DoubleArray): DoubleArray {
        require(refX.isNotEmpty() && sampleX.isNotEmpty()) { "光谱数据为空" }
        val absorbanceArray = DoubleArray(refX.size)
        for (i in refX.indices) {
            val targetX = refX[i]
            val refIntensity = refY[i]
            val sampleIntensity = interpolate(targetX, sampleX, sampleY)
            val t = if (refIntensity > 1e-9) sampleIntensity / refIntensity else 1e-9
            val safeTransmittance = t.coerceIn(1e-5, 1.0)
            absorbanceArray[i] = -log10(safeTransmittance)
        }
        return absorbanceArray
    }

    /**
     * 2. 波段截取 (对应 Python 中的 4000 ~ 8000 cm-1)
     */
    fun sliceWavenumber(xArray: DoubleArray, yArray: DoubleArray, minWv: Double = 4000.0, maxWv: Double = 8000.0): DoubleArray {
        val slicedY = mutableListOf<Double>()
        for (i in xArray.indices) {
            if (xArray[i] in minWv..maxWv) {
                slicedY.add(yArray[i])
            }
        }
        return slicedY.toDoubleArray()
    }

    /**
     * 3. SNV (Standard Normal Variate) 标准正态变量变换
     */
    fun snv(data: DoubleArray): DoubleArray {
        if (data.isEmpty()) return data
        val mean = data.average()
        val std = sqrt(data.map { (it - mean) * (it - mean) }.average())
        val safeStd = if (std > 1e-9) std else 1.0
        return data.map { (it - mean) / safeStd }.toDoubleArray()
    }

    /**
     * 4. SG Smoothing (Savitzky-Golay 平滑, 5点二次/三次多项式)
     * 权重系数: [-3, 12, 17, 12, -3] / 35
     */
    fun sgSmooth5(data: DoubleArray): DoubleArray {
        val n = data.size
        if (n < 5) return data
        val result = DoubleArray(n)
        for (i in 2 until n - 2) {
            result[i] = (-3 * data[i - 2] + 12 * data[i - 1] + 17 * data[i] + 12 * data[i + 1] - 3 * data[i + 2]) / 35.0
        }
        // 边缘处理：保持原值
        result[0] = data[0]; result[1] = data[1]
        result[n - 2] = data[n - 2]; result[n - 1] = data[n - 1]
        return result
    }

    /**
     * 5. SG 1st Derivative (Savitzky-Golay 一阶导数, 5点二次多项式)
     * 权重系数: [-2, -1, 0, 1, 2] / 10
     */
    fun sgDerivative1(data: DoubleArray): DoubleArray {
        val n = data.size
        if (n < 5) return data
        val result = DoubleArray(n)
        for (i in 2 until n - 2) {
            result[i] = (-2 * data[i - 2] - 1 * data[i - 1] + 0 * data[i] + 1 * data[i + 1] + 2 * data[i + 2]) / 10.0
        }
        result[0] = result[2]; result[1] = result[2]
        result[n - 2] = result[n - 3]; result[n - 1] = result[n - 3]
        return result
    }

    /**
     * 6. 动态重采样 (防止 ONNX 报错：强制将数组拉伸/压缩至模型要求的 N_FEATURES)
     */
    fun resampleToExactFeatures(data: DoubleArray, targetSize: Int): DoubleArray {
        if (data.size == targetSize) return data
        val result = DoubleArray(targetSize)
        val ratio = (data.size - 1).toDouble() / (targetSize - 1)
        for (i in 0 until targetSize) {
            val originalPos = i * ratio
            val index0 = originalPos.toInt()
            val index1 = (index0 + 1).coerceAtMost(data.size - 1)
            val weight = originalPos - index0
            result[i] = data[index0] * (1 - weight) + data[index1] * weight
        }
        return result
    }

    private fun interpolate(targetX: Double, xArray: DoubleArray, yArray: DoubleArray): Double {
        val n = xArray.size
        if (n == 1) return yArray[0]
        val isAsc = xArray.first() < xArray.last()
        if (isAsc) {
            if (targetX <= xArray.first()) return yArray.first()
            if (targetX >= xArray.last()) return yArray.last()
        } else {
            if (targetX >= xArray.first()) return yArray.first()
            if (targetX <= xArray.last()) return yArray.last()
        }
        for (i in 0 until n - 1) {
            val x0 = xArray[i]
            val x1 = xArray[i + 1]
            if (if (isAsc) targetX in x0..x1 else targetX in x1..x0) {
                if (x0 == x1) return yArray[i]
                return yArray[i] + (yArray[i + 1] - yArray[i]) * (targetX - x0) / (x1 - x0)
            }
        }
        return yArray.last()
    }
}