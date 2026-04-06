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

    var spectrumData by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    var currentSweep by mutableStateOf(0)
    var totalSweeps by mutableStateOf(config.params.numScans)
    var progress by mutableStateOf(0f)
    var peakX by mutableStateOf("0.00")
    var peakY by mutableStateOf("0.000")

    var exportFormat by mutableStateOf("SPC")

    fun clearMessage() { uiMessage = null }

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
                        uiMessage = "✅ 硬件健康监控诊断已刷新"
                    }
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
                driver.configure(config.params.resolution, config.params.firstGain, config.params.secondGain, config.params.startWave, config.params.stopWave)
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
        // 【优化点 2】：开始采集的严密逻辑判断与精细提示
        if (isAcquiring) {
            uiMessage = "⚠️ 无法启动：当前正在进行采集任务，请勿重复操作"
            return
        }
        if (!isTcpConnected || !isBoardOpened) {
            uiMessage = "⚠️ 无法启动：设备未连接，请先前往【仪器设置】建立连接"
            return
        }
        if (!isConfigApplied) {
            uiMessage = "⚠️ 无法启动：参数尚未下发，请前往【仪器设置】下发光学参数"
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

                while (isAcquiring) {
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("无法获取状态信息")
                    val coaddState = parser.extractControlValue(statusBuf, 11).toInt()

                    withContext(Dispatchers.Main) {
                        currentSweep = minOf(currentSweep + 1, totalSweeps)
                        progress = currentSweep.toFloat() / totalSweeps
                    }

                    if (coaddState == 0) break
                    if (System.currentTimeMillis() - t0 > timeout) throw Exception("扫描超时")
                    delay(300)
                }

                if (!isAcquiring) return@launch

                val statusBuf = driver.fetchCurrentStatus()
                val nPts = parser.extractNpts(statusBuf)
                val rawData = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, nPts, config.autoCollect.timeoutMs)
                val metadata = parser.parseDynamicMetadata(statusBuf)

                val savedPath = try {
                    if (exportFormat == "SPC") {
                        storage.saveToSpc(rawData, metadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    } else {
                        storage.saveToTxt(rawData, metadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val xyData = try {
                    if (savedPath != null) {
                        storage.readFromFile(savedPath)
                    } else {
                        storage.getSpectrumDataArray(rawData, metadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

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
                        uiMessage = if (savedPath != null) "🎉 采集完成！数据已成功从 ($exportFormat) 文件读取并渲染" else "⚠️ 渲染成功，但文件持久化失败"
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
        if (!isAcquiring) return
        isAcquiring = false
        scope.launch(Dispatchers.IO) {
            try {
                driver.stopAcquisition()
                withContext(Dispatchers.Main) { uiMessage = "🛑 已向设备发送停止采集指令" }
            } catch (e: Exception) {}
        }
    }
}