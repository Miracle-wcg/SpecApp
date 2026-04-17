package com.wcg.app.specapp.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.business.ComparisonResult
import com.wcg.app.specapp.business.DataComparisonService
import com.wcg.app.specapp.business.SpectrumDataProcessor
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class WorkspaceController(private val ui: UiController, private val scope: CoroutineScope) {
    val storage = SpectrumStorage()
    val loadedSpectra = mutableStateListOf<AnalyzedSpectrum>()
    val selectedForCompare = mutableStateListOf<AnalyzedSpectrum>()

    var spectrumData by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    var peakX by mutableStateOf("0.00")
    var peakY by mutableStateOf("0.000")

    var pipelineSgSmooth by mutableStateOf(false)
    var pipelineSnv by mutableStateOf(false)
    var pipeline1stDeriv by mutableStateOf(false)
    var referenceSpectrumId by mutableStateOf("NONE")

    var refFilePathForComparison by mutableStateOf("")
    var targetFilePathForComparison by mutableStateOf("")
    var comparisonResult by mutableStateOf<ComparisonResult?>(null)
    var isComparing by mutableStateOf(false)

    private val palette = listOf(Color(0xFF00E5FF), Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF00E676), Color(0xFF9C27B0), Color(0xFFFFEB3B))
    private var colorIdx = 0

    fun getNextColor(): Color {
        return palette[colorIdx++ % palette.size]
    }

    fun importDataFiles(filePaths: List<String>) {
        scope.launch(Dispatchers.IO) {
            var count = 0
            for (path in filePaths) {
                if (loadedSpectra.any { it.filePath == path }) continue
                try {
                    val rawData = storage.readFromFile(path)
                    SpectrumDataProcessor.process(rawData)?.let { processed ->
                        val spec = AnalyzedSpectrum(filePath = path, name = File(path).name, data = processed.points, color = getNextColor())
                        withContext(Dispatchers.Main) { loadedSpectra.add(spec); count++ }
                    }
                } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                applyPipeline()
                ui.showMsg(if(ui.appLanguage == AppLanguage.Chinese) "载入 $count 个文件" else "Loaded $count files")
            }
        }
    }

    fun applyPipeline() {
        scope.launch(Dispatchers.Default) {
            val refSpec = loadedSpectra.find { it.id == referenceSpectrumId }
            val processedMap = mutableMapOf<String, List<Pair<Double, Double>>>()

            for (spec in loadedSpectra) {
                var currentY = spec.data.map { it.second }.toDoubleArray()
                val currentX = spec.data.map { it.first }.toDoubleArray()

                if (pipelineSgSmooth) currentY = smoothSG(currentY)
                if (pipelineSnv) currentY = applySNV(currentY)
                if (pipeline1stDeriv) currentY = apply1stDerivative(currentX, currentY)
                processedMap[spec.id] = currentX.zip(currentY.toList())
            }

            val finalMap = mutableMapOf<String, List<Pair<Double, Double>>>()
            if (refSpec != null && processedMap.containsKey(refSpec.id)) {
                val refData = processedMap[refSpec.id]!!
                for (spec in loadedSpectra) {
                    if (spec.id == refSpec.id) finalMap[spec.id] = refData
                    else {
                        val diffData = processedMap[spec.id]!!.mapIndexed { i, p -> p.first to (p.second - (if(i < refData.size) refData[i].second else 0.0)) }
                        finalMap[spec.id] = diffData
                    }
                }
            } else finalMap.putAll(processedMap)

            withContext(Dispatchers.Main) { loadedSpectra.forEach { it.displayData = finalMap[it.id] ?: it.data } }
        }
    }

    fun runDataComparison() {
        isComparing = true; comparisonResult = null
        scope.launch {
            val result = DataComparisonService.evaluateAccuracy(refFilePathForComparison, targetFilePathForComparison)
            withContext(Dispatchers.Main) {
                comparisonResult = result; isComparing = false
                ui.showMsg(if (result.isSuccess) "验证完成" else "Error: ${result.message}")
            }
        }
    }

    fun removeSpectrum(spec: AnalyzedSpectrum) {
        loadedSpectra.remove(spec)
        selectedForCompare.remove(spec)
        if (referenceSpectrumId == spec.id) {
            referenceSpectrumId = "NONE"
            applyPipeline()
        }
    }

    // 🌟 核心新增：一键清空与重置状态
    fun clearAllSpectra() {
        loadedSpectra.clear()
        selectedForCompare.clear()
        referenceSpectrumId = "NONE"
        refFilePathForComparison = ""
        targetFilePathForComparison = ""
        comparisonResult = null
        applyPipeline()
        ui.showMsg(if(ui.appLanguage == AppLanguage.Chinese) "已清空所有数据轨道" else "All tracks cleared")
    }

    fun toggleSelectionForCompare(spec: AnalyzedSpectrum) {
        if (selectedForCompare.contains(spec)) selectedForCompare.remove(spec)
        else {
            if (selectedForCompare.size < 2) selectedForCompare.add(spec)
            else ui.showMsg(if(ui.appLanguage == AppLanguage.Chinese) "最多选择 2 个文件" else "Max 2 files")
        }
    }

    fun prepareComparison(): Boolean {
        if (selectedForCompare.size == 2) {
            refFilePathForComparison = selectedForCompare[0].filePath
            targetFilePathForComparison = selectedForCompare[1].filePath
            return true
        }
        return false
    }

    private fun smoothSG(y: DoubleArray): DoubleArray {
        val res = DoubleArray(y.size)
        for (i in y.indices) {
            var sum = 0.0; var count = 0
            for (j in max(0, i-2)..min(y.size-1, i+2)) { sum += y[j]; count++ }
            res[i] = sum / count
        }
        return res
    }

    private fun applySNV(y: DoubleArray): DoubleArray {
        val mean = y.average(); val std = sqrt(y.map { (it - mean) * (it - mean) }.average())
        return if (std == 0.0) y else y.map { (it - mean) / std }.toDoubleArray()
    }

    private fun apply1stDerivative(x: DoubleArray, y: DoubleArray): DoubleArray {
        val res = DoubleArray(y.size)
        for (i in 1 until y.size - 1) res[i] = (y[i+1] - y[i-1]) / (x[i+1] - x[i-1])
        res[0] = res[1]; res[y.size-1] = res[y.size-2]
        return res
    }
}