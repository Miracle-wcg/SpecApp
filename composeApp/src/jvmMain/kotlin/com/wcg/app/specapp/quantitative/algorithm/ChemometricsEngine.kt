package com.wcg.app.specapp.quantitative.algorithm

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.FloatBuffer

object ChemometricsEngine {
    private val log = LoggerFactory.getLogger(ChemometricsEngine::class.java)

    private val env = OrtEnvironment.getEnvironment()

    private var sessionAsh: OrtSession? = null
    private var sessionVolatile: OrtSession? = null
    private var sessionCalorific: OrtSession? = null
    private var sessionMoisture: OrtSession? = null
    private var sessionSulfur: OrtSession? = null
    private var sessionFixedCarbon: OrtSession? = null

    fun loadModels(modelDirectory: String) {
        try {
            val dir = File(modelDirectory)
            require(dir.exists() && dir.isDirectory) { "模型目录不存在" }

            sessionAsh?.close(); sessionVolatile?.close(); sessionCalorific?.close()
            sessionMoisture?.close(); sessionSulfur?.close(); sessionFixedCarbon?.close()

            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            dir.listFiles { _, name -> name.endsWith(".onnx") }?.forEach { file ->
                val path = file.absolutePath
                when {
                    path.contains("ash") -> sessionAsh = env.createSession(path, opts)
                    path.contains("volatile") -> sessionVolatile = env.createSession(path, opts)
                    path.contains("heat_value") -> sessionCalorific = env.createSession(path, opts)
                    path.contains("moisture") -> sessionMoisture = env.createSession(path, opts)
                    path.contains("sulfur") -> sessionSulfur = env.createSession(path, opts)
                    path.contains("fixed_carbon") -> sessionFixedCarbon = env.createSession(path, opts)
                }
            }
            log.info("ONNX Native Engines loaded successfully.")
        } catch (e: Exception) {
            log.error("Failed to load ONNX models", e)
            throw Exception("模型加载失败: ${e.message}")
        }
    }

    /**
     * 接收两种预处理数据，分发给不同的指标模型
     */
    fun predict(snvSgData: DoubleArray, sgD1Data: DoubleArray): DoubleArray {
        val ash = runInferenceSafe(sessionAsh, snvSgData)
        val vol = runInferenceSafe(sessionVolatile, sgD1Data) // 🌟 挥发分使用 SG+D1
        val cal = runInferenceSafe(sessionCalorific, snvSgData)
        val moi = runInferenceSafe(sessionMoisture, snvSgData)
        val sul = runInferenceSafe(sessionSulfur, snvSgData)
        val fc  = runInferenceSafe(sessionFixedCarbon, snvSgData)

        return doubleArrayOf(ash, vol, cal, moi, sul, fc)
    }

    /**
     * 动态尺寸感知与安全推理
     */
    private fun runInferenceSafe(session: OrtSession?, rawData: DoubleArray): Double {
        if (session == null) return 0.0

        // 🌟 动态获取当前 ONNX 模型编译时需要的特征点数 (例如 Python 中的 1500)
        val inputInfo = session.inputInfo.values.first().info as TensorInfo
        val expectedFeatures = inputInfo.shape[1].toInt()

        // 🌟 自动补齐重采样：如果截取后的光谱不足 1500 点，自动线性拉伸，防止维度崩溃！
        val alignedData = MathPreprocessor.resampleToExactFeatures(rawData, expectedFeatures)

        val floatData = FloatArray(alignedData.size) { alignedData[it].toFloat() }
        val shape = longArrayOf(1, alignedData.size.toLong())
        var tensor: OnnxTensor? = null
        var result: OrtSession.Result? = null

        try {
            tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), shape)
            result = session.run(mapOf(session.inputNames.iterator().next() to tensor))

            val output = result[0].value as Array<*>
            return when (val first = output[0]) {
                is FloatArray -> first[0].toDouble()
                is Float -> first.toDouble()
                else -> 0.0
            }
        } finally {
            result?.close()
            tensor?.close()
        }
    }
}