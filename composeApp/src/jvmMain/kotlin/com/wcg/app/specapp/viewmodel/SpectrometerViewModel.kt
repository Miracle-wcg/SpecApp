package com.wcg.app.specapp.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.spectrometer.config.SpectrometerProperties
import com.spectrometer.driver.AcquisitionDriverClient
import com.spectrometer.subsystem.MetadataParser
import com.spectrometer.subsystem.SpectrometerDriver
import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.business.ComparisonResult
import com.wcg.app.specapp.business.DataComparisonService
import com.wcg.app.specapp.business.FileNameGenerator
import com.wcg.app.specapp.business.OnnxResourceManager
import com.wcg.app.specapp.business.SpectrumDataProcessor
import com.wcg.app.specapp.quantitative.algorithm.ChemometricsEngine
import com.wcg.app.specapp.quantitative.viewmodel.QuantitativeViewModel
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File


enum class AppScreen(val titleEn: String, val titleZh: String, val icon: ImageVector) {
    Analysis("Analysis", "采集分析", Icons.Default.List),
    Setup("Setup", "仪器设置", Icons.Default.Build),
    AutoScan("Auto Scan", "自动采集", Icons.Default.Refresh),
    Quantitative("Quantitative", "智能分析", Icons.Default.Search),
    Comparison("Data Validation", "精度验证", Icons.Default.Check),
    Settings("Settings", "系统设置", Icons.Default.Settings);

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.Chinese) titleZh else titleEn
}

class SpectrometerViewModel {
    private val log = LoggerFactory.getLogger(SpectrometerViewModel::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    val config = SpectrometerProperties()
    private val driver = SpectrometerDriver(config)
    private val parser = MetadataParser()
    private val storage = SpectrumStorage()

    var appLanguage by mutableStateOf(AppLanguage.English)
    var currentScreen by mutableStateOf(AppScreen.Analysis)
    var connectionState by mutableStateOf(ConnectionState.Disconnected)
    var isAcquiring by mutableStateOf(false)

    var isTcpConnected by mutableStateOf(false)
    var isBoardOpened by mutableStateOf(false)
    var isConfigApplied by mutableStateOf(false)
    var uiMessage by mutableStateOf<String?>(null)

    var boardName by mutableStateOf(config.boardName)
    var boardInfo by mutableStateOf<AcquisitionDriverClient.BoardInformation?>(null)
    var firmwareVersion by mutableStateOf("N/A")
    var instrumentType by mutableStateOf("N/A")

    var healthReport by mutableStateOf<Map<String, Any>?>(null)
    var systemMetadata by mutableStateOf<Map<String, String>?>(null)

    var spectrumData by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    var currentSweep by mutableStateOf(0)
    var totalSweeps by mutableStateOf(config.params.numScans)
    var progress by mutableStateOf(0f)
    var peakX by mutableStateOf("0.00")
    var peakY by mutableStateOf("0.000")

    var exportFormat by mutableStateOf("SPC")

    var autoScanMode by mutableStateOf(AutoScanMode.Continuous)
    var autoScanCount by mutableStateOf("10")
    var autoScanDurationMin by mutableStateOf("60")
    var autoScanIntervalSec by mutableStateOf("5")
    var isAutoSequenceRunning by mutableStateOf(false)
    var autoSequenceCompletedCount by mutableStateOf(0)

    var fileNameTemplate by mutableStateOf(
        if (appLanguage == AppLanguage.Chinese) "[操作员]_[批次号]_[时间戳]" else "[Operator]_[Batch]_[Timestamp]"
    )
    var operatorName by mutableStateOf("Admin")
    var batchNumber by mutableStateOf("B001")

    var showDialog by mutableStateOf(false)
    var dialogTitle by mutableStateOf("")
    var dialogMessage by mutableStateOf("")

    fun saveNamingConfig() {
        dialogTitle = if (appLanguage == AppLanguage.Chinese) "系统提示" else "System Notification"
        dialogMessage = if (appLanguage == AppLanguage.Chinese) "命名规则已保存，将在下次采集时生效" else "Naming rules saved for next acquisition"
        showDialog = true
        log.info("File naming config saved.")
    }

    fun restoreDefaultNaming() {
        fileNameTemplate = if (appLanguage == AppLanguage.Chinese) "[操作员]_[批次号]_[时间戳]" else "[Operator]_[Batch]_[Timestamp]"
        operatorName = "Admin"
        batchNumber = "B001"
        dialogTitle = if (appLanguage == AppLanguage.Chinese) "系统提示" else "System Notification"
        dialogMessage = if (appLanguage == AppLanguage.Chinese) "命名模板已恢复默认设置" else "Naming template restored to default"
        showDialog = true
    }

    fun loadOnnxModels() {
        try {
            ChemometricsEngine.loadModels(onnxModelDirectory)
            dialogTitle = if (appLanguage == AppLanguage.Chinese) "系统提示" else "System Notification"
            dialogMessage = if (appLanguage == AppLanguage.Chinese) "ONNX 模型组已成功加载至内存" else "ONNX models loaded successfully"
            showDialog = true
        } catch (e: Exception) {
            dialogTitle = if (appLanguage == AppLanguage.Chinese) "加载失败" else "Load Failed"
            dialogMessage = "Exception: ${e.message}"
            showDialog = true
        }
    }

    fun resetOnnxDirectory() {
        onnxModelDirectory = defaultOnnxDirectory
        try {
            ChemometricsEngine.loadModels(onnxModelDirectory)
            dialogTitle = if (appLanguage == AppLanguage.Chinese) "系统提示" else "System Notification"
            dialogMessage = if (appLanguage == AppLanguage.Chinese) "已重置为项目默认模型并完成加载" else "Reset to default models and loaded"
            showDialog = true
        } catch (e: Exception) {
            dialogTitle = if (appLanguage == AppLanguage.Chinese) "系统错误" else "System Error"
            dialogMessage = if (appLanguage == AppLanguage.Chinese) "默认目录模型丢失，请检查资源文件" else "Default models not found"
            showDialog = true
        }
    }

    val quantitativeViewModel = QuantitativeViewModel(
        getAppLanguage = { appLanguage },
        showMessage = { msg -> uiMessage = msg }
    )

    val defaultOnnxDirectory = OnnxResourceManager.extractModelsToLocalDir()
    var onnxModelDirectory by mutableStateOf(defaultOnnxDirectory)

    init {
        log.info("=== Spectrometer Engine Initialized ===")
        try {
            val dir = File(onnxModelDirectory)
            if (dir.exists() && dir.isDirectory) {
                ChemometricsEngine.loadModels(onnxModelDirectory)
                log.info("Default ONNX models successfully auto-loaded from: $onnxModelDirectory")
            } else {
                log.warn("Default models directory not found. Please specify custom path in Settings.")
            }
        } catch (e: Exception) {
            log.error("Failed to auto-load default ONNX models", e)
        }
    }

    var refFilePathForComparison by mutableStateOf("")
    var targetFilePathForComparison by mutableStateOf("")
    var comparisonResult by mutableStateOf<ComparisonResult?>(null)
    var isComparing by mutableStateOf(false)

    fun runDataComparison() {
        if (refFilePathForComparison.isEmpty() || targetFilePathForComparison.isEmpty()) {
            uiMessage = if (appLanguage == AppLanguage.Chinese) "请先选择两个需要对比的光谱文件" else "Please select both files to compare"
            return
        }

        isComparing = true
        comparisonResult = null
        uiMessage = if (appLanguage == AppLanguage.Chinese) "正在执行高精度比对..." else "Running high-precision comparison..."

        scope.launch {
            val result = DataComparisonService.evaluateAccuracy(refFilePathForComparison, targetFilePathForComparison)
            withContext(Dispatchers.Main) {
                comparisonResult = result
                isComparing = false
                uiMessage = if (result.isSuccess) {
                    if (appLanguage == AppLanguage.Chinese) "精度验证分析完成" else "Validation complete"
                } else {
                    "Error: ${result.message}"
                }
            }
        }
    }

    fun clearMessage() {
        uiMessage = null
    }

    fun getGeneratedFileName(): String {
        return FileNameGenerator.generate(fileNameTemplate, operatorName, batchNumber, exportFormat)
    }

    fun setExportFormatOpt(newFormat: String) {
        if (exportFormat != newFormat) {
            exportFormat = newFormat
            uiMessage =
                if (appLanguage == AppLanguage.Chinese) "导出格式已切换为 $newFormat" else "Export format switched to $newFormat"
        }
    }

    fun setExportPathOpt(newPath: String) {
        config.savePath = newPath; config.savePathWindows = newPath
        uiMessage = if (appLanguage == AppLanguage.Chinese) "存储路径已更新" else "Storage path updated"
    }

    fun markConfigDirty(paramName: String, value: String) {
        isConfigApplied = false
    }

    fun logUserAction(action: String) {
        log.info("[USER ACTION] {}", action)
    }

    fun importDataFile(filePath: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val rawData = storage.readFromFile(filePath)
                val processed = SpectrumDataProcessor.process(rawData)
                withContext(Dispatchers.Main) {
                    if (processed != null) {
                        spectrumData = processed.points
                        peakX = processed.peakX
                        peakY = processed.peakY
                        progress = 1f
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "成功载入数据文件" else "Data file loaded"
                    } else {
                        uiMessage = "文件格式不支持或数据为空"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { uiMessage = "读取失败: ${e.message}" }
            }
        }
    }

    fun disconnectHardware() {
        log.info("[USER ACTION] Triggered manual hardware disconnection.")
        scope.launch(Dispatchers.IO) {
            try {
                driver.disconnect()
                log.info("All network sockets and driver states released.")
            } catch (e: Exception) {
                log.error("Exception during hardware disconnection", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isTcpConnected = false
                    isBoardOpened = false
                    isConfigApplied = false
                    connectionState = ConnectionState.Disconnected
                    boardInfo = null
                    firmwareVersion = "N/A"
                    instrumentType = "N/A"
                    healthReport = null
                    systemMetadata = null
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "设备已安全断开连接" else "Device safely disconnected"
                }
            }
        }
    }

    fun checkHealth() {
        if (!isBoardOpened) return
        log.info("[USER ACTION] Requesting hardware diagnostics & health report...")
        scope.launch(Dispatchers.IO) {
            try {
                val hDef = driver.fetchHealthStatusDefinition()
                val hStat = driver.fetchHealthStatus()
                if (hDef != null && hStat != null) {
                    val report = parser.parseHealthMonitoring(hDef, hStat)
                    withContext(Dispatchers.Main) {
                        healthReport = null
                        @Suppress("UNCHECKED_CAST")
                        healthReport = (report as Map<String, Any>).toMap()
                    }
                }
                val cStat = driver.fetchCurrentStatus()
                if (cStat != null) {
                    val meta = parser.parseDynamicMetadata(cStat)
                    withContext(Dispatchers.Main) { systemMetadata = meta }
                }
                withContext(Dispatchers.Main) {
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "硬件健康监控及系统状态已刷新" else "Hardware health and system status refreshed"
                }
                log.info("Health diagnostics updated successfully.")
            } catch (e: Exception) {
                log.error("Error fetching health telemetry", e)
            }
        }
    }

    fun connectTcp() {
        log.info("[USER ACTION] Connecting to Spectrometer Server -> IP: {}, Port: {}", config.serverIp, config.tcpPort)
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connectionState = ConnectionState.Connecting }
            if (driver.connectTcp()) {
                log.info("TCP Handshake successful. Probing for physical board name...")
                val detectedName = driver.autoDetectBoardName()

                withContext(Dispatchers.Main) {
                    isTcpConnected = true
                    connectionState = ConnectionState.Connected
                    if (detectedName.isNotEmpty()) {
                        boardName = detectedName
                        config.boardName = detectedName
                        uiMessage =
                            if (appLanguage == AppLanguage.Chinese) "TCP 已连接，自动识别板卡: $detectedName" else "TCP Connected, auto-detected board: $detectedName"
                        log.info("Discovered Board Name: [{}]", detectedName)
                    } else {
                        uiMessage =
                            if (appLanguage == AppLanguage.Chinese) "TCP 基础连接已建立，未探测到默认板卡名称" else "TCP connected, but default board name not detected"
                        log.warn("Board name auto-discovery yielded no results.")
                    }
                }
            } else {
                log.error("TCP Connection refused by target device.")
                withContext(Dispatchers.Main) {
                    isTcpConnected = false
                    connectionState = ConnectionState.Error
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "TCP 连接失败，请检查 IP 和端口" else "TCP connection failed, check IP and port"
                }
            }
        }
    }

    fun openBoard() {
        if (!isTcpConnected) return
        log.info(
            "[USER ACTION] Initializing UDP Board Link -> Board Name: {}, UDP Port: {}",
            config.boardName,
            config.udpPort
        )
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connectionState = ConnectionState.Connecting }
            if (driver.openBoard()) {
                log.info("UDP Link established. Fetching memory mapping tables...")
                val info = driver.boardInfo
                driver.fetchStatusDefinition()?.let { parser.initTable(it) }
                driver.fetchControlStatus()?.let { parser.initControlTable(it) }

                withContext(Dispatchers.Main) {
                    boardInfo = info
                    firmwareVersion = "v${info?.acquisitionDriverVersion ?: "N/A"}"
                    instrumentType = when (info?.instrumentType?.toInt()) {
                        0 -> "MID-IR"; 1 -> "NIR"; else -> "Unknown (${info?.instrumentType})"
                    }
                    isBoardOpened = true
                    connectionState = ConnectionState.Ready
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "板卡打开成功，已获取硬件信息" else "Board opened successfully, hardware info retrieved"
                    log.info("Hardware identified. Type: {}, Firmware: {}", instrumentType, firmwareVersion)
                }
                checkHealth()
            } else {
                log.error("Failed to initialize UDP Board Link.")
                withContext(Dispatchers.Main) {
                    isBoardOpened = false
                    connectionState = ConnectionState.Error
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "板卡打开失败，请检查 UDP 端口及名称" else "Board open failed, check UDP port and board name"
                }
            }
        }
    }

    fun applyParameters() {
        if (!isBoardOpened) return
        log.info("[USER ACTION] Synchronizing Optics Parameters to Hardware:")
        scope.launch(Dispatchers.IO) {
            try {
                config.params.secondGain = 0
                driver.configure(
                    config.params.resolution,
                    config.params.firstGain,
                    0,
                    config.params.startWave,
                    config.params.stopWave
                )
                withContext(Dispatchers.Main) {
                    isConfigApplied = true
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "光学及扫描参数已成功下发至硬件" else "Optics parameters synchronized to hardware"
                    log.info("Optics parameters synchronized successfully. Ready for acquisition.")
                }
            } catch (e: Exception) {
                log.error("Failed to synchronize optics parameters.", e)
                withContext(Dispatchers.Main) {
                    isConfigApplied = false
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "参数下发失败: ${e.message}" else "Failed to apply parameters: ${e.message}"
                }
            }
        }
    }

    fun startAcquisition() {
        if (isAcquiring || !driver.isConnected || !isConfigApplied) {
            uiMessage =
                if (appLanguage == AppLanguage.Chinese) "无法启动：设备未就绪或未下发参数" else "Cannot start: Device not ready or config not applied"
            return
        }

        isAcquiring = true
        totalSweeps = config.params.numScans
        currentSweep = 0
        progress = 0f
        spectrumData = emptyList()
        peakX = "0.00"
        peakY = "0.000"

        scope.launch(Dispatchers.IO) {
            try {
                driver.startCoaddition(config.params.numScans, config.params.numRuns)
                val t0 = System.currentTimeMillis()
                val timeout = config.autoCollect.timeoutMs + (config.params.numScans * 1500L)
                val estimatedTotalTimeMs = (config.params.numScans * 1000L).coerceAtLeast(1000L)

                while (isAcquiring) {
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("Status buffer null")
                    val coaddState = parser.extractControlValue(statusBuf, 11).toInt()
                    if (coaddState == 0) break

                    val elapsedMs = System.currentTimeMillis() - t0
                    val timeProgress = (elapsedMs.toFloat() / estimatedTotalTimeMs).coerceIn(0f, 0.99f)

                    try {
                        val nPts = parser.extractNpts(statusBuf)
                        if (nPts > 0) {
                            val liveData = driver.fetchRawData(SpectrometerDriver.SOURCE_CURRENT, nPts, 500)
                            val liveMeta = parser.parseDynamicMetadata(statusBuf)
                            val liveXy = storage.getSpectrumDataArray(
                                liveData,
                                liveMeta,
                                config.laserFreq,
                                config.params.startWave.toDouble(),
                                config.params.stopWave.toDouble()
                            )

                            SpectrumDataProcessor.process(liveXy)?.let { processed ->
                                withContext(Dispatchers.Main) {
                                    spectrumData = processed.points
                                    peakX = processed.peakX
                                    peakY = processed.peakY
                                    progress = timeProgress
                                    currentSweep = (timeProgress * totalSweeps).toInt()
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }

                    if (System.currentTimeMillis() - t0 > timeout) {
                        throw Exception(if (appLanguage == AppLanguage.Chinese) "扫描执行超时" else "Acquisition timeout limit reached")
                    }
                    delay(200)
                }

                if (!isAcquiring) return@launch

                withContext(Dispatchers.Main) {
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "正在提取硬件底层高精度均值数据..." else "Extracting high-precision co-added data..."
                }

                val finalStatusBuf = driver.fetchCurrentStatus()
                val finalNpts = parser.extractNpts(finalStatusBuf)
                val finalRawData =
                    driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, finalNpts, config.autoCollect.timeoutMs)
                val finalMetadata = parser.parseDynamicMetadata(finalStatusBuf)

                val actualFileName = getGeneratedFileName()
                val exportDir =
                    if (config.savePath.endsWith(File.separator)) config.savePath else config.savePath + File.separator
                val absoluteSavePath = exportDir + actualFileName

                log.info("Saving spectrum data to custom path: {}", absoluteSavePath)

                val savedPath = try {
                    if (exportFormat == "SPC") storage.saveToSpc(
                        finalRawData,
                        finalMetadata,
                        config.laserFreq,
                        config.params.startWave.toDouble(),
                        config.params.stopWave.toDouble(),
                        absoluteSavePath
                    )
                    else storage.saveToTxt(
                        finalRawData,
                        finalMetadata,
                        config.laserFreq,
                        config.params.startWave.toDouble(),
                        config.params.stopWave.toDouble(),
                        absoluteSavePath
                    )
                } catch (e: Exception) {
                    null
                }

                val finalXyData = try {
                    if (savedPath != null) storage.readFromFile(savedPath)
                    else storage.getSpectrumDataArray(
                        finalRawData,
                        finalMetadata,
                        config.laserFreq,
                        config.params.startWave.toDouble(),
                        config.params.stopWave.toDouble()
                    )
                } catch (e: Exception) {
                    storage.getSpectrumDataArray(
                        finalRawData,
                        finalMetadata,
                        config.laserFreq,
                        config.params.startWave.toDouble(),
                        config.params.stopWave.toDouble()
                    )
                }

                SpectrumDataProcessor.process(finalXyData)?.let { processed ->
                    withContext(Dispatchers.Main) {
                        spectrumData = processed.points
                        peakX = processed.peakX
                        peakY = processed.peakY
                        currentSweep = totalSweeps
                        progress = 1f
                        uiMessage =
                            if (appLanguage == AppLanguage.Chinese) "采集完成！保存至: $actualFileName" else "Scan completed! Saved: $actualFileName"
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "采集异常: ${e.message}" else "Acquisition error: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) { isAcquiring = false }
            }
        }
    }

    fun stopAcquisition() {
        if (!isAcquiring) return
        isAcquiring = false
        scope.launch(Dispatchers.IO) {
            try {
                driver.stopAcquisition()
                withContext(Dispatchers.Main) {
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "已手动中断光谱采集" else "Spectrometer acquisition manually aborted"
                }
            } catch (e: Exception) {
                log.error("Error aborting driver sequence.", e)
            }
        }
    }

    fun startAutoSequence() {
        if (isAutoSequenceRunning || isAcquiring || !isConfigApplied) {
            uiMessage =
                if (appLanguage == AppLanguage.Chinese) "无法启动：设备未就绪、参数未下发或任务运行中" else "Cannot start: Device not ready or task running"
            return
        }

        val count = autoScanCount.toIntOrNull() ?: 0
        val durationMs = (autoScanDurationMin.toLongOrNull() ?: 0) * 60 * 1000L
        val intervalMs = (autoScanIntervalSec.toLongOrNull() ?: 0) * 1000L

        isAutoSequenceRunning = true
        autoSequenceCompletedCount = 0
        log.info(
            "[AUTO SCAN] Started. Mode: {}, Count: {}, Duration: {}ms, Interval: {}ms",
            autoScanMode,
            count,
            durationMs,
            intervalMs
        )

        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                while (isAutoSequenceRunning) {
                    if (autoScanMode == AutoScanMode.Continuous && autoSequenceCompletedCount >= count) break
                    if (autoScanMode == AutoScanMode.Scheduled && (System.currentTimeMillis() - startTime) >= durationMs) break

                    withContext(Dispatchers.Main) { startAcquisition() }

                    while (isAcquiring && isAutoSequenceRunning) {
                        delay(200)
                    }

                    if (!isAutoSequenceRunning) break
                    autoSequenceCompletedCount++

                    var delayed = 0L
                    while (delayed < intervalMs && isAutoSequenceRunning) {
                        delay(100)
                        delayed += 100
                    }
                }
            } catch (e: Exception) {
                log.error("Error in auto sequence loop.", e)
            } finally {
                isAutoSequenceRunning = false
                withContext(Dispatchers.Main) {
                    uiMessage =
                        if (appLanguage == AppLanguage.Chinese) "自动采集任务已结束 (共 $autoSequenceCompletedCount 次)" else "Auto sequence finished ($autoSequenceCompletedCount scans)"
                }
            }
        }
    }

    fun stopAutoSequence() {
        if (!isAutoSequenceRunning) return
        log.info("[USER ACTION] Aborting auto sequence.")
        isAutoSequenceRunning = false
        stopAcquisition()
        uiMessage =
            if (appLanguage == AppLanguage.Chinese) "自动采集已被人工中止" else "Auto sequence aborted by user"
    }
}