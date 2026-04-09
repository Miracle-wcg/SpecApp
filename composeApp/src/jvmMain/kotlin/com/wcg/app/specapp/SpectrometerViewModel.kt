package com.wcg.app.specapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spectrometer.config.SpectrometerProperties
import com.spectrometer.driver.AcquisitionDriverClient
import com.spectrometer.subsystem.MetadataParser
import com.spectrometer.subsystem.SpectrometerDriver
import com.spectrometer.subsystem.SpectrumStorage
import com.wcg.app.specapp.business.FileNameGenerator
import com.wcg.app.specapp.business.SpectrumDataProcessor
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File

enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }

enum class AppLanguage { English, Chinese }

enum class AutoScanMode { Continuous, Scheduled }

enum class AppScreen(val titleEn: String, val titleZh: String, val icon: String) {
    Analysis("Analysis", "采集分析", "📊"),
    Setup("Setup", "仪器设置", "☷"),
    AutoScan("Auto Scan", "自动采集", "⏳"),
    Quantitative("Quantitative","智能定量预测", "🔬"),
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

    // --- 核心 UI 状态 ---
    var appLanguage by mutableStateOf(AppLanguage.English)
    var currentScreen by mutableStateOf(AppScreen.Analysis)
    var connectionState by mutableStateOf(ConnectionState.Disconnected)
    var isAcquiring by mutableStateOf(false)

    var isTcpConnected by mutableStateOf(false)
    var isBoardOpened by mutableStateOf(false)
    var isConfigApplied by mutableStateOf(false)

    var uiMessage by mutableStateOf<String?>(null)

    // --- 硬件与身份信息 ---
    var boardName by mutableStateOf(config.boardName)
    var boardInfo by mutableStateOf<AcquisitionDriverClient.BoardInformation?>(null)
    var firmwareVersion by mutableStateOf("N/A")
    var instrumentType by mutableStateOf("N/A")

    var healthReport by mutableStateOf<Map<String, Any>?>(null)
    var systemMetadata by mutableStateOf<Map<String, String>?>(null)

    // --- 光谱实时数据状态 ---
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

    // ==========================================
    // 🌟 解耦层调用：文件生成与数据处理
    // ==========================================
    fun getGeneratedFileName(): String {
        return FileNameGenerator.generate(fileNameTemplate, operatorName, batchNumber, exportFormat)
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

    fun logUserAction(action: String) { log.info("[USER ACTION] {}", action) }

    fun importDataFile(filePath: String) {
        log.info("[USER ACTION] Loading historical spectrum data from: {}", filePath)
        scope.launch(Dispatchers.IO) {
            try {
                val rawData = storage.readFromFile(filePath)

                // 使用解耦的处理引擎处理数据
                val processed = SpectrumDataProcessor.process(rawData)

                withContext(Dispatchers.Main) {
                    if (processed != null) {
                        spectrumData = processed.points
                        peakX = processed.peakX
                        peakY = processed.peakY
                        progress = 1f
                        val fileName = filePath.substringAfterLast(File.separator)
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 成功载入文件: $fileName" else "✅ Successfully loaded file: $fileName"
                        log.info("Data loaded successfully. Points rendered: {}", processed.points.size)
                    } else {
                        log.warn("Target file is empty or unsupported format.")
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "⚠️ 文件格式不支持或内容为空" else "⚠️ Unsupported or empty file"
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to parse historical file.", e)
                withContext(Dispatchers.Main) {
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 文件读取失败: ${e.message}" else "❌ Failed to read file: ${e.message}"
                }
            }
        }
    }

    // ==========================================
    // 硬件连接与生命周期控制
    // ==========================================
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 硬件健康监控及系统扩展状态已刷新" else "✅ Hardware health and system status refreshed"
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
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ TCP 已连接，自动识别板卡: $detectedName" else "✅ TCP Connected, auto-detected board: $detectedName"
                        log.info("Discovered Board Name: [{}]", detectedName)
                    } else {
                        uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ TCP 基础连接已建立，但未探测到默认板卡名称" else "✅ TCP connected, but default board name not detected"
                        log.warn("Board name auto-discovery yielded no results.")
                    }
                }
            } else {
                log.error("TCP Connection refused by target device.")
                withContext(Dispatchers.Main) {
                    isTcpConnected = false
                    connectionState = ConnectionState.Error
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ TCP 连接失败，请检查 IP 和端口" else "❌ TCP connection failed, check IP and port"
                }
            }
        }
    }

    fun openBoard() {
        if (!isTcpConnected) return
        log.info("[USER ACTION] Initializing UDP Board Link -> Board Name: {}, UDP Port: {}", config.boardName, config.udpPort)
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 板卡打开成功，已获取硬件信息" else "✅ Board opened successfully, hardware info retrieved"
                    log.info("Hardware identified. Type: {}, Firmware: {}", instrumentType, firmwareVersion)
                }
                checkHealth()
            } else {
                log.error("Failed to initialize UDP Board Link.")
                withContext(Dispatchers.Main) {
                    isBoardOpened = false
                    connectionState = ConnectionState.Error
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 板卡打开失败，请检查 UDP 端口及板卡名称" else "❌ Board open failed, check UDP port and board name"
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "✅ 光学及扫描参数已成功下发至硬件" else "✅ Optics parameters synchronized to hardware"
                    log.info("Optics parameters synchronized successfully. Ready for acquisition.")
                }
            } catch (e: Exception) {
                log.error("Failed to synchronize optics parameters.", e)
                withContext(Dispatchers.Main) {
                    isConfigApplied = false
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "❌ 参数下发失败: ${e.message}" else "❌ Failed to apply parameters: ${e.message}"
                }
            }
        }
    }

    // ==========================================
    // 采集流程控制与自动化调度
    // ==========================================
    fun startAcquisition() {
        if (isAcquiring || !driver.isConnected || !isConfigApplied) {
            uiMessage = if (appLanguage == AppLanguage.Chinese) "⚠️ 无法启动：设备未就绪或未下发参数" else "⚠️ Cannot start: Device not ready or config not applied"
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

                // 1. 实时预览循环
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

                            // 依赖处理引擎刷新 UI
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
                    } catch (e: Exception) {}

                    if (System.currentTimeMillis() - t0 > timeout) {
                        throw Exception(if (appLanguage == AppLanguage.Chinese) "扫描执行超时" else "Acquisition timeout limit reached")
                    }
                    delay(200)
                }

                if (!isAcquiring) return@launch

                withContext(Dispatchers.Main) {
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "⏳ 正在提取硬件底层高精度均值数据..." else "⏳ Extracting high-precision co-added data..."
                }

                // 2. 最终结果提取与落盘
                val finalStatusBuf = driver.fetchCurrentStatus()
                val finalNpts = parser.extractNpts(finalStatusBuf)
                val finalRawData = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, finalNpts, config.autoCollect.timeoutMs)
                val finalMetadata = parser.parseDynamicMetadata(finalStatusBuf)

                // 🌟 使用动态命名生成器组合绝对路径
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

                // 🌟 依赖处理引擎完成终极数据展示
                SpectrumDataProcessor.process(finalXyData)?.let { processed ->
                    withContext(Dispatchers.Main) {
                        spectrumData = processed.points
                        peakX = processed.peakX
                        peakY = processed.peakY
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "🛑 已手动中断光谱采集" else "🛑 Spectrometer acquisition manually aborted"
                }
            } catch (e: Exception) {
                log.error("Error aborting driver sequence.", e)
            }
        }
    }

    fun startAutoSequence() {
        if (isAutoSequenceRunning || isAcquiring || !isConfigApplied) {
            uiMessage = if (appLanguage == AppLanguage.Chinese) "⚠️ 无法启动：设备未就绪、参数未下发或任务运行中" else "⚠️ Cannot start: Device not ready or task running"
            return
        }

        val count = autoScanCount.toIntOrNull() ?: 0
        val durationMs = (autoScanDurationMin.toLongOrNull() ?: 0) * 60 * 1000L
        val intervalMs = (autoScanIntervalSec.toLongOrNull() ?: 0) * 1000L

        isAutoSequenceRunning = true
        autoSequenceCompletedCount = 0
        log.info("[AUTO SCAN] Started. Mode: {}, Count: {}, Duration: {}ms, Interval: {}ms", autoScanMode, count, durationMs, intervalMs)

        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                while (isAutoSequenceRunning) {
                    // 1. 检查退出条件
                    if (autoScanMode == AutoScanMode.Continuous && autoSequenceCompletedCount >= count) break
                    if (autoScanMode == AutoScanMode.Scheduled && (System.currentTimeMillis() - startTime) >= durationMs) break

                    // 2. 触发单次采集（内部已包含落盘机制）
                    withContext(Dispatchers.Main) { startAcquisition() }

                    // 3. 挂起等待单次采集结束
                    while (isAcquiring && isAutoSequenceRunning) { delay(200) }

                    if (!isAutoSequenceRunning) break
                    autoSequenceCompletedCount++

                    // 4. 等待设定的间隔时间
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
                    uiMessage = if (appLanguage == AppLanguage.Chinese) "🎉 自动采集任务已结束 (共 $autoSequenceCompletedCount 次)" else "🎉 Auto sequence finished ($autoSequenceCompletedCount scans)"
                }
            }
        }
    }

    fun stopAutoSequence() {
        if (!isAutoSequenceRunning) return
        log.info("[USER ACTION] Aborting auto sequence.")
        isAutoSequenceRunning = false
        stopAcquisition() // 同步中断底层硬件采集
        uiMessage = if (appLanguage == AppLanguage.Chinese) "🛑 自动采集已被人工中止" else "🛑 Auto sequence aborted by user"
    }
}