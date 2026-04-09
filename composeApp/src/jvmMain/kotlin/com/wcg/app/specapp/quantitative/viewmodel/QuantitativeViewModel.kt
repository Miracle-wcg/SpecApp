package com.wcg.app.specapp.quantitative.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.quantitative.model.PredictionResult
import com.wcg.app.specapp.quantitative.model.ProcessState
import com.wcg.app.specapp.quantitative.service.QuantitativeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class QuantitativeViewModel {
    // 这里需注入您实际的 Storage
    private val service = QuantitativeService(SpectrumStorage())
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // UI 状态
    val sampleFiles = mutableStateListOf<File>()
    val darkFile = mutableStateOf<File?>(null)
    val refFile = mutableStateOf<File?>(null)

    val processState = mutableStateOf(ProcessState.IDLE)
    val results = mutableStateListOf<PredictionResult>()

    fun startProcessing() {
        if (sampleFiles.isEmpty() || darkFile.value == null || refFile.value == null) return

        processState.value = ProcessState.PROCESSING
        results.clear()

        viewModelScope.launch {
            service.processBatch(sampleFiles, darkFile.value!!, refFile.value!!, modelId = 1)
                .collect { result ->
                    results.add(result)
                }
            processState.value = ProcessState.COMPLETED
        }
    }

    fun clearAll() {
        sampleFiles.clear()
        results.clear()
        processState.value = ProcessState.IDLE
    }
}