package com.wcg.app.specapp.quantitative.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.AppLanguage
import com.wcg.app.specapp.quantitative.model.PredictionResult
import com.wcg.app.specapp.quantitative.model.ProcessState
import com.wcg.app.specapp.quantitative.service.QuantitativeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class QuantitativeViewModel(private val getAppLanguage: () -> AppLanguage, private val showMessage: (String) -> Unit) {
    private val service = QuantitativeService(SpectrumStorage())
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    val sampleFiles = mutableStateListOf<File>()
    var refFile by mutableStateOf<File?>(null)

    var processState by mutableStateOf(ProcessState.IDLE)
    var progress by mutableStateOf(0f)
    val results = mutableStateListOf<PredictionResult>()

    var selectedResult by mutableStateOf<PredictionResult?>(null)

    fun startProcessing() {
        if (refFile == null) {
            showMessage(if (getAppLanguage() == AppLanguage.Chinese) "⚠️ 拒绝执行：请先绑定参比/白板光谱 (Ref)！" else "⚠️ Action Denied: Please bind Reference spectrum first!")
            return
        }

        if (sampleFiles.isEmpty()) {
            showMessage(if (getAppLanguage() == AppLanguage.Chinese) "⚠️ 拒绝执行：待处理样本队列为空，请先导入！" else "⚠️ Action Denied: Sample queue is empty, please import!")
            return
        }

        processState = ProcessState.PROCESSING
        results.clear()
        selectedResult = null
        progress = 0f

        viewModelScope.launch {
            var completed = 0
            val total = sampleFiles.size

            service.processBatch(sampleFiles, refFile!!, modelId = 1)
                .collect { result ->
                    results.add(result)
                    completed++
                    progress = completed.toFloat() / total

                    if (result.isSuccess && selectedResult == null) {
                        selectedResult = result
                    }
                }
            processState = ProcessState.COMPLETED
            showMessage(if (getAppLanguage() == AppLanguage.Chinese) "🎉 批量离线智能分析完成！" else "🎉 Batch analysis completed!")
        }
    }

    // 🌟 核心优化：只清除样本与结果列表，保留参比(Ref)文件不动
    fun clearSamples() {
        sampleFiles.clear()
        results.clear()
        // refFile = null  // <-- 注释/删除了这一行，保留绑定状态
        selectedResult = null
        processState = ProcessState.IDLE
        progress = 0f
    }
}