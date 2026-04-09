package com.wcg.app.specapp.quantitative.service

import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.business.SpectrumDataProcessor
import com.wcg.app.specapp.quantitative.algorithm.ChemometricsEngine
import com.wcg.app.specapp.quantitative.algorithm.MathPreprocessor
import com.wcg.app.specapp.quantitative.model.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class QuantitativeService(private val storage: SpectrumStorage) {

    fun processBatch(samples: List<File>, refFile: File, modelId: Int): Flow<PredictionResult> = flow {
        try {
            val refData = storage.readFromFile(refFile.absolutePath) ?: throw Exception("无法读取参比背景")
            val refX = refData[0]
            val refY = refData[1]

            samples.forEach { sampleFile ->
                try {
                    val sampleData = storage.readFromFile(sampleFile.absolutePath) ?: throw Exception("样本损坏")
                    val sampleX = sampleData[0]
                    val sampleY = sampleData[1]

                    // 1. 全波段吸光度计算 (用于图表展示)
                    val absorbanceY = MathPreprocessor.calculateAbsorbance(sampleX, sampleY, refX, refY)
                    val processedSpectrum = SpectrumDataProcessor.process(arrayOf(refX, absorbanceY))

                    // 2. 截取有效波段 (对齐 Python: 4000 ~ 8000 cm-1)
                    val slicedAbsorbance = MathPreprocessor.sliceWavenumber(refX, absorbanceY, 4000.0, 8000.0)

                    // 3. 构建两种化学计量学预处理管线
                    // 管线 A: SNV + SG (Ash, Moisture, Carbon, Sulfur, Heat)
                    val pipelineSnvSg = MathPreprocessor.snv(MathPreprocessor.sgSmooth5(slicedAbsorbance))

                    // 管线 B: SG + D1 (Volatile 挥发分专属)
                    val pipelineSgD1 = MathPreprocessor.sgDerivative1(MathPreprocessor.sgSmooth5(slicedAbsorbance))

                    // 4. 送入 ONNX 引擎
                    val results = ChemometricsEngine.predict(pipelineSnvSg, pipelineSgD1)

                    emit(
                        PredictionResult(
                            file = sampleFile,
                            ashContent = results[0],
                            volatileMatter = results[1],
                            calorificValue = results[2],
                            moisture = results[3],
                            sulfur = results[4],
                            fixedCarbon = results[5],
                            isSuccess = true,
                            absorbanceSpectrum = processedSpectrum
                        )
                    )
                } catch (e: Exception) {
                    emit(PredictionResult(file = sampleFile, isSuccess = false, errorMessage = e.message))
                }
            }
        } catch (e: Exception) {
            samples.forEach { emit(PredictionResult(it, isSuccess = false, errorMessage = "执行引擎错误: ${e.message}")) }
        }
    }.flowOn(Dispatchers.IO)
}