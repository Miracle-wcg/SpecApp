package com.wcg.app.specapp.quantitative.algorithm

object ChemometricsEngine {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("SpectraModelEngine")
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            println("警告: JNI DLL 加载失败，将使用 Mock 数据进行 UI 测试。")
        }
    }

    private external fun nativePredict(absorbanceData: DoubleArray, modelId: Int): DoubleArray?

    fun predict(absorbance: DoubleArray, modelId: Int): DoubleArray {
        if (!isNativeLoaded) {
            // Mock 模式：如果没有加载 C++ 库，随机生成合理的煤炭数据用于测试 UI
            Thread.sleep(100) // 模拟计算耗时
            return doubleArrayOf(
                10.0 + Math.random() * 5,  // 灰分 10~15%
                20.0 + Math.random() * 10, // 挥发分 20~30%
                5000.0 + Math.random() * 1000 // 发热量 5000~6000
            )
        }
        return nativePredict(absorbance, modelId) ?: throw Exception("Native 模型返回空数据")
    }
}