package com.wcg.app.specapp.viewmodel

import com.spectrometer.config.SpectrometerProperties
import com.wcg.app.specapp.quantitative.viewmodel.QuantitativeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory

class SpectrometerViewModel {
    private val log = LoggerFactory.getLogger(SpectrometerViewModel::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // ==========================================
    // 实例化独立子控制器 (Controllers)
    // ==========================================
    private val uiCtrl = UiController()
    private val setCtrl = SettingsController(uiCtrl)
    val hwCtrl = HardwareController(SpectrometerProperties(), uiCtrl, scope)
    private val wsCtrl = WorkspaceController(uiCtrl, scope)
    private val acqCtrl = AcquisitionController(hwCtrl, wsCtrl, setCtrl, uiCtrl, scope)

    val quantitativeViewModel = QuantitativeViewModel(getAppLanguage = { appLanguage }, showMessage = { uiMessage = it })

    // ==========================================
    // 属性委托与路由映射 (Facade Forwarding)
    // ==========================================

    // --- UiController ---
    var appLanguage get() = uiCtrl.appLanguage; set(v) { uiCtrl.appLanguage = v }
    var currentScreen get() = uiCtrl.currentScreen; set(v) { uiCtrl.currentScreen = v }
    var uiMessage get() = uiCtrl.uiMessage; set(v) { uiCtrl.uiMessage = v }
    var showDialog get() = uiCtrl.showDialog; set(v) { uiCtrl.showDialog = v }
    var dialogTitle get() = uiCtrl.dialogTitle; set(v) { uiCtrl.dialogTitle = v }
    var dialogMessage get() = uiCtrl.dialogMessage; set(v) { uiCtrl.dialogMessage = v }
    fun clearMessage() = uiCtrl.clearMsg()

    // --- HardwareController ---
    val config get() = hwCtrl.config
    var connectionState get() = hwCtrl.connectionState; set(v) { hwCtrl.connectionState = v }
    var isTcpConnected get() = hwCtrl.isTcpConnected; set(v) { hwCtrl.isTcpConnected = v }
    var isBoardOpened get() = hwCtrl.isBoardOpened; set(v) { hwCtrl.isBoardOpened = v }
    var isConfigApplied get() = hwCtrl.isConfigApplied; set(v) { hwCtrl.isConfigApplied = v }
    var boardName get() = hwCtrl.boardName; set(v) { hwCtrl.boardName = v }
    var firmwareVersion get() = hwCtrl.firmwareVersion; set(v) { hwCtrl.firmwareVersion = v }
    var healthReport get() = hwCtrl.healthReport; set(v) { hwCtrl.healthReport = v }
    fun connectTcp() = hwCtrl.connectTcp()
    fun openBoard() = hwCtrl.openBoard()
    fun applyParameters() = hwCtrl.applyParameters()
    fun checkHealth() = hwCtrl.checkHealth()
    fun disconnectHardware() = hwCtrl.disconnectHardware()
    fun markConfigDirty(p: String, v: String) { hwCtrl.isConfigApplied = false }
    fun logUserAction(act: String) { log.info("[USER] $act") }

    // --- WorkspaceController ---
    val loadedSpectra get() = wsCtrl.loadedSpectra
    val selectedForCompare get() = wsCtrl.selectedForCompare
    var spectrumData get() = wsCtrl.spectrumData; set(v) { wsCtrl.spectrumData = v }
    var peakX get() = wsCtrl.peakX; set(v) { wsCtrl.peakX = v }
    var peakY get() = wsCtrl.peakY; set(v) { wsCtrl.peakY = v }
    var pipelineSgSmooth get() = wsCtrl.pipelineSgSmooth; set(v) { wsCtrl.pipelineSgSmooth = v }
    var pipelineSnv get() = wsCtrl.pipelineSnv; set(v) { wsCtrl.pipelineSnv = v }
    var pipeline1stDeriv get() = wsCtrl.pipeline1stDeriv; set(v) { wsCtrl.pipeline1stDeriv = v }
    var referenceSpectrumId get() = wsCtrl.referenceSpectrumId; set(v) { wsCtrl.referenceSpectrumId = v }
    var refFilePathForComparison get() = wsCtrl.refFilePathForComparison; set(v) { wsCtrl.refFilePathForComparison = v }
    var targetFilePathForComparison get() = wsCtrl.targetFilePathForComparison; set(v) { wsCtrl.targetFilePathForComparison = v }
    var comparisonResult get() = wsCtrl.comparisonResult; set(v) { wsCtrl.comparisonResult = v }
    var isComparing get() = wsCtrl.isComparing; set(v) { wsCtrl.isComparing = v }
    fun importDataFile(path: String) = wsCtrl.importDataFiles(listOf(path))
    fun importDataFiles(paths: List<String>) = wsCtrl.importDataFiles(paths)

    fun clearAllSpectra() = wsCtrl.clearAllSpectra()

    // 转发流水线执行方法
    fun applyPipeline() = wsCtrl.applyPipeline()
    fun removeSpectrum(spec: AnalyzedSpectrum) = wsCtrl.removeSpectrum(spec)
    fun toggleSelectionForCompare(spec: AnalyzedSpectrum) = wsCtrl.toggleSelectionForCompare(spec)
    fun prepareComparison() { if(wsCtrl.prepareComparison()) currentScreen = AppScreen.Comparison }
    fun runDataComparison() = wsCtrl.runDataComparison()

    // --- AcquisitionController ---
    var isAcquiring get() = acqCtrl.isAcquiring; set(v) { acqCtrl.isAcquiring = v }
    var progress get() = acqCtrl.progress; set(v) { acqCtrl.progress = v }
    var currentSweep get() = acqCtrl.currentSweep; set(v) { acqCtrl.currentSweep = v }
    var totalSweeps get() = acqCtrl.totalSweeps; set(v) { acqCtrl.totalSweeps = v }
    var autoScanMode get() = acqCtrl.autoScanMode; set(v) { acqCtrl.autoScanMode = v }
    var autoScanCount get() = acqCtrl.autoScanCount; set(v) { acqCtrl.autoScanCount = v }
    var autoScanDurationMin get() = acqCtrl.autoScanDurationMin; set(v) { acqCtrl.autoScanDurationMin = v }
    var autoScanIntervalSec get() = acqCtrl.autoScanIntervalSec; set(v) { acqCtrl.autoScanIntervalSec = v }
    var isAutoSequenceRunning get() = acqCtrl.isAutoSequenceRunning; set(v) { acqCtrl.isAutoSequenceRunning = v }
    var autoSequenceCompletedCount get() = acqCtrl.autoSequenceCompletedCount; set(v) { acqCtrl.autoSequenceCompletedCount = v }
    fun startAcquisition() = acqCtrl.startAcquisition()
    fun stopAcquisition() = acqCtrl.stopAcquisition()
    fun startAutoSequence() = acqCtrl.startAutoSequence()
    fun stopAutoSequence() = acqCtrl.stopAutoSequence()

    // --- SettingsController ---
    var fileNameTemplate get() = setCtrl.fileNameTemplate; set(v) { setCtrl.fileNameTemplate = v }
    var operatorName get() = setCtrl.operatorName; set(v) { setCtrl.operatorName = v }
    var batchNumber get() = setCtrl.batchNumber; set(v) { setCtrl.batchNumber = v }
    var exportFormat get() = setCtrl.exportFormat; set(v) { setCtrl.exportFormat = v }
    var onnxModelDirectory get() = setCtrl.onnxModelDirectory; set(v) { setCtrl.onnxModelDirectory = v }
    fun saveNamingConfig() { uiCtrl.popup("Success", "Naming rules saved.") }
    fun restoreDefaultNaming() { setCtrl.fileNameTemplate = "[Operator]_[Batch]_[Timestamp]"; uiCtrl.popup("Success", "Restored to default.") }
    fun loadOnnxModels() = setCtrl.loadOnnxModels()
    fun resetOnnxDirectory() = setCtrl.resetOnnxDirectory()
    fun getGeneratedFileName(): String = setCtrl.getGeneratedFileName()
    fun setExportFormatOpt(fmt: String) = setCtrl.setExportFormatOpt(fmt)
    fun setExportPathOpt(path: String) { config.savePath = path; config.savePathWindows = path; uiCtrl.showMsg("Storage path updated") }
}