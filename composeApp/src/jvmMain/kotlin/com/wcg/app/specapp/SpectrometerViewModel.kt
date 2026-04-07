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
import java.io.File

enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }

enum class AppScreen(val title: String, val icon: String) {
    Analysis("采集分析", "📊"),
    Setup("仪器设置", "☷"),
    Settings("系统设置", "⚙")
}

class SpectrometerViewModel {
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

    fun clearMessage() { uiMessage = null }

    fun importDataFile(filePath: String) {
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
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { uiMessage = "❌ 文件读取失败: ${e.message}" }
            }
        }
    }

    fun disconnectHardware() {
        scope.launch(Dispatchers.IO) {
            try {
                driver.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
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
                    withContext(Dispatchers.Main) {
                        systemMetadata = meta
                    }
                }

                withContext(Dispatchers.Main) {
                    uiMessage = "✅ 硬件健康监控及系统扩展状态已刷新"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun connectTcp() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connectionState = ConnectionState.Connecting }
            val success = driver.connectTcp()
            withContext(Dispatchers.Main) {
                if (success) {
                    isTcpConnected = true
                    connectionState = ConnectionState.Connected
                    uiMessage = "✅ TCP 基础连接已成功建立"
                } else {
                    isTcpConnected = false
                    connectionState = ConnectionState.Error
                    uiMessage = "❌ TCP 连接失败，请检查 IP 和端口"
                }
            }
        }
    }

    fun openBoard() {
        if (!isTcpConnected) return
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
                    uiMessage = "✅ 板卡打开成功，已获取硬件信息"
                }
                checkHealth()
            } else {
                withContext(Dispatchers.Main) {
                    isBoardOpened = false
                    connectionState = ConnectionState.Error
                    uiMessage = "❌ 板卡打开失败，请检查 UDP 端口"
                }
            }
        }
    }

    fun applyParameters() {
        if (!isBoardOpened) return
        scope.launch(Dispatchers.IO) {
            try {
                // 【优化点 1】：Second Gain 始终传 0
                config.params.secondGain = 0
                driver.configure(config.params.resolution, config.params.firstGain, 0, config.params.startWave, config.params.stopWave)
                withContext(Dispatchers.Main) {
                    isConfigApplied = true
                    uiMessage = "✅ 光学及扫描参数已成功下发至硬件"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isConfigApplied = false; uiMessage = "❌ 参数下发失败: ${e.message}" }
            }
        }
    }

    fun startAcquisition() {
        if (isAcquiring || !driver.isConnected || !isConfigApplied) {
            uiMessage = "⚠️ 无法启动：设备未就绪或未下发参数"
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

                var simulatedSweep = 0

                while (isAcquiring) {
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("无法获取状态信息")
                    val coaddState = parser.extractControlValue(statusBuf, 11).toInt()

                    if (coaddState == 0) break

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
                                    simulatedSweep = minOf(simulatedSweep + 1, totalSweeps - 1)
                                    currentSweep = simulatedSweep
                                    progress = simulatedSweep.toFloat() / totalSweeps
                                }
                            }
                        }
                    } catch (e: Exception) {}

                    if (System.currentTimeMillis() - t0 > timeout) throw Exception("扫描执行超时")
                    delay(300)
                }

                if (!isAcquiring) return@launch

                withContext(Dispatchers.Main) { uiMessage = "⏳ 正在提取硬件底层最终的高精度均值及全量状态..." }

                val finalStatusBuf = driver.fetchCurrentStatus()
                val finalNpts = parser.extractNpts(finalStatusBuf)
                val finalRawData = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, finalNpts, config.autoCollect.timeoutMs)
                val finalMetadata = parser.parseDynamicMetadata(finalStatusBuf)

                val savedPath = try {
                    if (exportFormat == "SPC") {
                        storage.saveToSpc(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    } else {
                        storage.saveToTxt(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val finalXyData = try {
                    if (savedPath != null) {
                        storage.readFromFile(savedPath)
                    } else {
                        storage.getSpectrumDataArray(finalRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
                    }
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
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { uiMessage = "❌ 采集异常: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isAcquiring = false }
            }
        }
    }

    fun stopAcquisition() {
        isAcquiring = false
        scope.launch(Dispatchers.IO) {
            try {
                driver.stopAcquisition()
                withContext(Dispatchers.Main) { uiMessage = "🛑 已手动中断光谱采集序列" }
            } catch (e: Exception) {}
        }
    }
}