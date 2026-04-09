package com.wcg.app.specapp.quantitative.service

import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.quantitative.algorithm.ChemometricsEngine
import com.wcg.app.specapp.quantitative.algorithm.MathPreprocessor
import com.wcg.app.specapp.quantitative.model.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class QuantitativeService(private val storage: SpectrumStorage) {

    fun processBatch(
        samples: List<File>,
        darkFile: File,
        refFile: File,
        modelId: Int
    ): Flow<PredictionResult> = flow {
        try {
            // 1. 加载背景数据
            val darkY = storage.readSpc(darkFile.absolutePath).yData
            val refY = storage.readSpc(refFile.absolutePath).yData

            // 2. 遍历处理样品
            samples.forEach { sampleFile ->
                try {
                    val sampleY = storage.readSpc(sampleFile.absolutePath).yData
                    // 数学预处理
                    val absorbance = MathPreprocessor.calculateAbsorbance(sampleY, darkY, refY)
                    // JNI 预测
                    val results = ChemometricsEngine.predict(absorbance, modelId)

                    emit(
                        PredictionResult(
                            file = sampleFile,
                            ashContent = results[0],
                            volatileMatter = results[1],
                            calorificValue = results[2],
                            isSuccess = true
                        )
                    )
                } catch (e: Exception) {
                    emit(PredictionResult(file = sampleFile, isSuccess = false, errorMessage = e.message))
                }
            }
        } catch (e: Exception) {
            // 背景加载失败等全局错误
            samples.forEach {
                emit(PredictionResult(it, isSuccess = false, errorMessage = "初始化失败: ${e.message}"))
            }
        }
    }.flowOn(Dispatchers.IO)
}