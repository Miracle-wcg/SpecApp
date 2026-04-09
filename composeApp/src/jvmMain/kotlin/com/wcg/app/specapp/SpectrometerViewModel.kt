package com.wcg.app.specapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spectrometer.config.SpectrometerProperties
import com.spectrometer.driver.AcquisitionDriverClient
import com.spectrometer.subsystem.MetadataParser
import com.spectrometer.subsystem.SpectrometerDriver
import com.spectrometer.subsystem.SpectrumStorage
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }

enum class AppLanguage { English, Chinese }

enum class AutoScanMode { Continuous, Scheduled }

enum class AppScreen(val titleEn: String, val titleZh: String, val icon: String) {
    Analysis("Analysis", "采集分析", "📊"),
    Setup("Setup", "仪器设置", "☷"),
    AutoScan("Auto Scan", "自动采集", "⏳"),
    Settings("Settings", "系统设置", "⚙");

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

    // --- 自动化采集专属状态 ---
    var autoScanMode by mutableStateOf(AutoScanMode.Continuous)
    var autoScanCount by mutableStateOf("10")
    var autoScanDurationMin by mutableStateOf("60")
    var autoScanIntervalSec by mutableStateOf("5")
    var isAutoSequenceRunning by mutableStateOf(false)
    var autoSequenceCompletedCount by mutableStateOf(0)

    // --- 文件命名模板专属状态 ---
    var fileNameTemplate by mutableStateOf("[操作员]_[批次号]_[时间戳]")
    var operatorName by mutableStateOf("Admin")
    var batchNumber by mutableStateOf("B001")

    init {
        log.info("=== Spectrometer Engine Initialized ===")
    }

    fun clearMessage() { uiMessage = null }

    // --- 获取实时解析的动态文件名 ---
    fun getGeneratedFileName(): String {
        val now = Date()
        val sdfFull = SimpleDateFormat("yyyyMMdd_HHmmss")
        val sdfDate = SimpleDateFormat("yyyyMMdd")
        val sdfTime = SimpleDateFormat("HHmmss")

        var result = fileNameTemplate
        result = result.replace("[操作员]", operatorName).replace("[Operator]", operatorName)
        result = result.replace("[批次号]", batchNumber).replace("[Batch]", batchNumber)
        result = result.replace("[时间戳]", sdfFull.format(now)).replace("[Timestamp]", sdfFull.format(now))
        result = result.replace("[日期]", sdfDate.format(now)).replace("[Date]", sdfDate.format(now))
        result = result.replace("[时间]", sdfTime.format(now)).replace("[Time]", sdfTime.format(now))

        return "$result.${exportFormat.lowercase()}"
    }

    fun setExportFormatOpt(newFormat: String) {
        if (exportFormat != newFormat) {
            log.info("[USER ACTION] Data Export Format switched to: {}", newFormat)
            exportFormat = newFormat
            uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 导出格式已切换为 $newFormat" else "✅ Export format switched to $newFormat"
        }
    }

    fun setExportPathOpt(newPath: String) {
        log.info("[USER ACTION] Default Storage Path updated to: {}", newPath)
        config.savePath = newPath
        config.savePathWindows = newPath
        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 存储路径已更新" else "✅ Storage path updated"
    }

    fun markConfigDirty(paramName: String, value: String) {
        log.info("[USER ACTION] Configuration Changed -> {} : {}. Hardware resync required.", paramName, value)
        isConfigApplied = false
    }

    fun logUserAction(action: String) {
        log.info("[USER ACTION] {}", action)
    }

    fun importDataFile(filePath: String) {
        log.info("[USER ACTION] Loading historical spectrum data from: {}", filePath)
        scope.launch(Dispatchers.IO) {
            try {
                val xyData = storage.readFromFile(filePath)
                if (xyData != null && xyData.size == 2 && xyData[0].isNotEmpty()) {
                    val xArray = xyData[0]
                    val yArray = xyData[1]
                    val points = xArray.zip(yArray).toList()
                    val maxPoint = points.maxByOrNull { it.second }

                    withContext(Dispatchers.Main) {
                        spectrumData = points
                        if (maxPoint != null) {
                            peakX = String.format("%.2f", maxPoint.first)
                            peakY = String.format("%.4f", maxPoint.second)
                        }
                        progress = 1f
                        val fileName = filePath.substringAfterLast(File.separator)
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 成功载入文件: $fileName" else "✅ Successfully loaded file: $fileName"
                        log.info("Data loaded successfully. Points rendered: {}", points.size)
                    }
                } else {
                    log.warn("Target file is empty or unsupported format.")
                }
            } catch (e: Exception) {
                log.error("Failed to parse historical file.", e)
                withContext(Dispatchers.Main) {
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 文件读取失败: ${e.message}" else "❌ Failed to read file: ${e.message}"
                }
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "🔌 设备已安全断开连接" else "🔌 Device safely disconnected"
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 硬件健康监控已刷新" else "✅ Hardware health refreshed"
                }
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
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ TCP 已连接，自动识别板卡: $detectedName" else "✅ TCP Connected, auto-detected board: $detectedName"
                    } else {
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ TCP 基础连接已建立" else "✅ TCP connected"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    isTcpConnected = false
                    connectionState = ConnectionState.Error
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ TCP 连接失败，请检查 IP 和端口" else "❌ TCP connection failed"
                }
            }
        }
    }

    fun openBoard() {
        if (!isTcpConnected) return
        log.info("[USER ACTION] Initializing UDP Board Link -> Board Name: {}", config.boardName)
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connectionState = ConnectionState.Connecting }
            if (driver.openBoard()) {
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 板卡打开成功，已获取硬件信息" else "✅ Board opened successfully"
                }
                checkHealth()
            } else {
                withContext(Dispatchers.Main) {
                    isBoardOpened = false
                    connectionState = ConnectionState.Error
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 板卡打开失败" else "❌ Board open failed"
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
                driver.configure(config.params.resolution, config.params.firstGain, 0, config.params.startWave, config.params.stopWave)
                withContext(Dispatchers.Main) {
                    isConfigApplied = true
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 光学参数已成功下发" else "✅ Optics parameters synchronized"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConfigApplied = false
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 参数下发失败: ${e.message}" else "❌ Failed to apply parameters: ${e.message}"
                }
            }
        }
    }

    fun startAcquisition() {
        if (isAcquiring || !driver.isConnected || !isConfigApplied) {
            uiMessage = if (appLanguage == AppLanguage.Chinese) "⚠️ 无法启动：设备未就绪或未下发参数" else "⚠️ Cannot start: Device not ready"
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
                            val liveXy = storage.getSpectrumDataArray(liveData, liveMeta, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())

                            if (liveXy != null && liveXy.size == 2 && liveXy[0].isNotEmpty()) {
                                val points = liveXy[0].zip(liveXy[1]).toList()
                                val maxPoint = points.maxByOrNull { it.second }

                                withContext(Dispatchers.Main) {
                                    spectrumData = points
                                    if (maxPoint != null) {
                                        peakX = String.format("%.2f", maxPoint.first)
                                        peakY = String.format("%.4f", maxPoint.second)
                                    }
                                    progress = timeProgress
                                    currentSweep = (timeProgress * totalSweeps).toInt()
                                }
                            }
                        }
                    } catch (e: Exception) {}

                    if (System.currentTimeMillis() - t0 > timeout) {
                        throw Exception(if (appLanguage == AppLanguage.Chinese) "扫描执行超时" else "Acquisition timeout")
                    }
                    delay(200)
                }

                if (!isAcquiring) return@launch

                withContext(Dispatchers.Main) {
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "⏳ 正在提取数据并落盘..." else "⏳ Extracting and saving data..."
                }

                val finalStatusBuf = driver.fetchCurrentStatus()
                val finalNpts = parser.extractNpts(finalStatusBuf)
                val finalRawData = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, finalNpts, config.autoCollect.timeoutMs)
                val finalMetadata = parser.parseDynamicMetadata(finalStatusBuf)

                // 🌟 使用动态命名模板组合绝对路径
                val actualFileName = getGeneratedFileName()
                val exportDir = if (config.savePath.endsWith(File.separator)) config.savePath else config.savePath + File.separator
                val absoluteSavePath = exportDir + actualFileName

                log.info("Saving spectrum data to custom path: {}", absoluteSavePath)

                val savedPath = try {
                    if (exportFormat == "SPC") storage.saveToSpc(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), absoluteSavePath)
                    else storage.saveToTxt(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), absoluteSavePath)
                } catch (e: Exception) { null }

                val finalXyData = try {
                    if (savedPath != null) storage.readFromFile(savedPath)
                    else storage.getSpectrumDataArray(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
                } catch (e: Exception) {
                    storage.getSpectrumDataArray(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
                }

                if (finalXyData != null && finalXyData.size == 2 && finalXyData[0].isNotEmpty()) {
                    val points = finalXyData[0].zip(finalXyData[1]).toList()
                    val maxPoint = points.maxByOrNull { it.second }

                    withContext(Dispatchers.Main) {
                        spectrumData = points
                        if (maxPoint != null) {
                            peakX = String.format("%.2f", maxPoint.first)
                            peakY = String.format("%.4f", maxPoint.second)
                        }
                        currentSweep = totalSweeps
                        progress = 1f
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "🎉 采集完成！保存至: $actualFileName" else "🎉 Scan completed! Saved: $actualFileName"
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 采集异常: ${e.message}" else "❌ Acquisition error: ${e.message}"
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "🛑 已中止采集" else "🛑 Acquisition aborted"
                }
            } catch (e: Exception) {
                log.error("Error aborting sequence.", e)
            }
        }
    }

    fun startAutoSequence() {
        if (isAutoSequenceRunning || isAcquiring || !isConfigApplied) return
        val count = autoScanCount.toIntOrNull() ?: 0
        val durationMs = (autoScanDurationMin.toLongOrNull() ?: 0) * 60 * 1000L
        val intervalMs = (autoScanIntervalSec.toLongOrNull() ?: 0) * 1000L

        isAutoSequenceRunning = true
        autoSequenceCompletedCount = 0

        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                while (isAutoSequenceRunning) {
                    if (autoScanMode == AutoScanMode.Continuous && autoSequenceCompletedCount >= count) break
                    if (autoScanMode == AutoScanMode.Scheduled && (System.currentTimeMillis() - startTime) >= durationMs) break

                    withContext(Dispatchers.Main) { startAcquisition() }

                    while (isAcquiring && isAutoSequenceRunning) { delay(200) }
                    if (!isAutoSequenceRunning) break
                    autoSequenceCompletedCount++

                    var delayed = 0L
                    while (delayed < intervalMs && isAutoSequenceRunning) {
                        delay(100)
                        delayed += 100
                    }
                }
            } catch (e: Exception) {
                log.error("Error in auto loop", e)
            } finally {
                isAutoSequenceRunning = false
                withContext(Dispatchers.Main) {
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "🎉 自动采集结束 (共 $autoSequenceCompletedCount 次)" else "🎉 Auto sequence finished ($autoSequenceCompletedCount scans)"
                }
            }
        }
    }

    fun stopAutoSequence() {
        if (!isAutoSequenceRunning) return
        isAutoSequenceRunning = false
        stopAcquisition()
        uiMessage = if (appLanguage == AppLanguage.Chinese) "🛑 自动采集已被中止" else "🛑 Auto sequence aborted"
    }
}