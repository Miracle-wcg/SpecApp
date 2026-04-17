package com.wcg.app.specapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wcg.app.specapp.business.FileNameGenerator
import com.wcg.app.specapp.business.OnnxResourceManager
import com.wcg.app.specapp.quantitative.algorithm.ChemometricsEngine
import java.io.File

class SettingsController(private val ui: UiController) {
    var fileNameTemplate by mutableStateOf("[操作员]_[批次号]_[时间戳]")
    var operatorName by mutableStateOf("Admin")
    var batchNumber by mutableStateOf("B001")
    var exportFormat by mutableStateOf("SPC")

    val defaultOnnxDirectory = OnnxResourceManager.extractModelsToLocalDir()
    var onnxModelDirectory by mutableStateOf(defaultOnnxDirectory)

    init {
        try {
            if (File(onnxModelDirectory).let { it.exists() && it.isDirectory }) {
                ChemometricsEngine.loadModels(onnxModelDirectory)
            }
        } catch (e: Exception) {
            // Log intentionally omitted for simplicity in this layer
        }
    }

    fun getGeneratedFileName(): String =
        FileNameGenerator.generate(fileNameTemplate, operatorName, batchNumber, exportFormat)

    fun setExportFormatOpt(newFormat: String) {
        exportFormat = newFormat
        ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "导出格式已切换为 $newFormat" else "Export format: $newFormat")
    }

    fun loadOnnxModels() {
        ChemometricsEngine.loadModels(onnxModelDirectory)
        ui.popup("Success", "Models loaded successfully.")
    }

    fun resetOnnxDirectory() {
        onnxModelDirectory = defaultOnnxDirectory
        loadOnnxModels()
    }
}