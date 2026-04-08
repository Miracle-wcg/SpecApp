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

enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }

enum class AppScreen(val title: String, val icon: String) {
    Analysis("采集分析", "📊"),
    Setup("仪器设置", "☷"),
    Settings("系统设置", "⚙")
}

class SpectrometerViewModel {
    private val log = LoggerFactory.getLogger(SpectrometerViewModel::class.java)

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    val config = SpectrometerProperties()
    private val driver = SpectrometerDriver(config)
    private val parser = MetadataParser()
    private val storage = SpectrumStorage()

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

    init {
        log.info("=== Spectrometer Engine Initialized ===")
    }

    fun clearMessage() { uiMessage = null }

    // ==========================================
    // UI 专属日志与状态管理方法
    // ==========================================
    fun setExportFormatOpt(newFormat: String) {
        if (exportFormat != newFormat) {
            log.info("[USER ACTION] Data Export Format switched to: {}", newFormat)
            exportFormat = newFormat
            uiMessage = "✅ 导出格式已切换为 $newFormat"
        }
    }

    fun setExportPathOpt(newPath: String) {
        log.info("[USER ACTION] Default Storage Path updated to: {}", newPath)
        config.savePath = newPath
        config.savePathWindows = newPath
        uiMessage = "✅ 存储路径已更新"
    }

    fun markConfigDirty(paramName: String, value: String) {
        log.info("[USER ACTION] Configuration Changed -> {} : {}. Hardware resync required.", paramName, value)
        isConfigApplied = false
    }

    fun logUserAction(action: String) {
        log.info("[USER ACTION] {}", action)
    }

    // ==========================================
    // 核心硬件交互逻辑 (含全量日志快照)
    // ==========================================
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
                        uiMessage = "✅ 成功载入文件: ${filePath.substringAfterLast(File.separator)}"
                        log.info("Data loaded successfully. Points rendered: {}", points.size)
                    }
                } else {
                    log.warn("Target file is empty or unsupported format.")
                }
            } catch (e: Exception) {
                log.error("Failed to parse historical file.", e)
                withContext(Dispatchers.Main) { uiMessage = "❌ 文件读取失败: ${e.message}" }
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
                    uiMessage = "🔌 设备已安全断开连接"
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
                withContext(Dispatchers.Main) { uiMessage = "✅ 硬件健康监控及系统扩展状态已刷新" }
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
                        uiMessage = "✅ TCP 已连接，自动识别板卡: $detectedName"
                        log.info("Discovered Board Name: [{}]", detectedName)
                    } else {
                        uiMessage = "✅ TCP 基础连接已建立，但未探测到默认板卡名称"
                        log.warn("Board name auto-discovery yielded no results.")
                    }
                }
            } else {
                log.error("TCP Connection refused by target device.")
                withContext(Dispatchers.Main) {
                    isTcpConnected = false
                    connectionState = ConnectionState.Error
                    uiMessage = "❌ TCP 连接失败，请检查 IP 和端口"
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
                    uiMessage = "✅ 板卡打开成功，已获取硬件信息"
                    log.info("Hardware identified. Type: {}, Firmware: {}", instrumentType, firmwareVersion)
                }
                checkHealth()
            } else {
                log.error("Failed to initialize UDP Board Link.")
                withContext(Dispatchers.Main) {
                    isBoardOpened = false
                    connectionState = ConnectionState.Error
                    uiMessage = "❌ 板卡打开失败，请检查 UDP 端口及板卡名称"
                }
            }
        }
    }

    fun applyParameters() {
        if (!isBoardOpened) return
        log.info("[USER ACTION] Synchronizing Optics Parameters to Hardware:")
        log.info("  -> Wave Range : {} to {} cm-1", config.params.startWave, config.params.stopWave)
        log.info("  -> Resolution : {}", config.params.resolution)
        log.info("  -> Total Gain : {}", config.params.firstGain)
        log.info("  -> Num Scans  : {} (Runs: {})", config.params.numScans, config.params.numRuns)
        log.info("  -> Laser Freq : {} Hz", config.laserFreq)

        scope.launch(Dispatchers.IO) {
            try {
                config.params.secondGain = 0
                driver.configure(config.params.resolution, config.params.firstGain, 0, config.params.startWave, config.params.stopWave)
                withContext(Dispatchers.Main) {
                    isConfigApplied = true
                    uiMessage = "✅ 光学及扫描参数已成功下发至硬件"
                    log.info("Optics parameters synchronized successfully. Ready for acquisition.")
                }
            } catch (e: Exception) {
                log.error("Failed to synchronize optics parameters.", e)
                withContext(Dispatchers.Main) { isConfigApplied = false; uiMessage = "❌ 参数下发失败: ${e.message}" }
            }
        }
    }

    fun startAcquisition() {
        if (isAcquiring || !driver.isConnected || !isConfigApplied) {
            log.warn("Acquisition rejected. State checks failed. isAcq={}, isConn={}, isConfOk={}", isAcquiring, driver.isConnected, isConfigApplied)
            uiMessage = "⚠️ 无法启动：设备未就绪或未下发参数"
            return
        }

        log.info("[USER ACTION] START ACQUISITION Triggered. Target Scans: {}", config.params.numScans)
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

                log.debug("Entering realtime visualizer polling loop...")
                while (isAcquiring) {
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("无法获取状态信息")
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
                        log.error("Acquisition timeout limit reached ({} ms).", timeout)
                        throw Exception("扫描执行超时")
                    }
                    delay(200)
                }

                if (!isAcquiring) {
                    log.warn("Acquisition sequence was aborted by user.")
                    return@launch
                }

                log.info("Hardware Co-addition complete. Extracting high-precision FIFO data & metadata...")
                withContext(Dispatchers.Main) { uiMessage = "⏳ 正在提取硬件底层最终的高精度均值及全量状态..." }

                val finalStatusBuf = driver.fetchCurrentStatus()
                val finalNpts = parser.extractNpts(finalStatusBuf)
                val finalRawData = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, finalNpts, config.autoCollect.timeoutMs)
                val finalMetadata = parser.parseDynamicMetadata(finalStatusBuf)

                log.info("Committing spectral data to disk using format [{}]...", exportFormat)
                val savedPath = try {
                    if (exportFormat == "SPC") storage.saveToSpc(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    else storage.saveToTxt(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                } catch (e: Exception) {
                    log.error("Failed to commit data to disk.", e)
                    null
                }

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
                        uiMessage = "🎉 $totalSweeps 次硬件级扫描并求均值完成！全量状态数据已导出至: $savedPath"
                        log.info("Acquisition lifecycle fully completed and verified. Saved to: {}", savedPath)
                    }
                }

            } catch (e: Exception) {
                log.error("Critical error halted acquisition pipeline.", e)
                withContext(Dispatchers.Main) { uiMessage = "❌ 采集异常: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isAcquiring = false }
            }
        }
    }

    fun stopAcquisition() {
        if (!isAcquiring) return
        log.info("[USER ACTION] STOP ACQUISITION Triggered. Aborting hardware loop.")
        isAcquiring = false
        scope.launch(Dispatchers.IO) {
            try {
                driver.stopAcquisition()
                withContext(Dispatchers.Main) { uiMessage = "🛑 已手动中断光谱采集序列" }
            } catch (e: Exception) {
                log.error("Error aborting driver sequence.", e)
            }
        }
    }
}