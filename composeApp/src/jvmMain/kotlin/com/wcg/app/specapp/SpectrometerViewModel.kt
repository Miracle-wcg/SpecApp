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

    fun clearMessage() {
        uiMessage = null
    }

    fun disconnectHardware() {
        scope.launch(Dispatchers.IO) {
            try {
                driver.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // 确保所有 UI 状态重置都在主线程执行
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
        if (!isBoardOpened) {
            uiMessage = "⚠️ 无法获取健康状态：板卡未打开"
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val hDef = driver.fetchHealthStatusDefinition()
                val hStat = driver.fetchHealthStatus()
                if (hDef != null && hStat != null) {
                    val report = parser.parseHealthMonitoring(hDef, hStat)
                    // 强制切回主线程更新 UI
                    withContext(Dispatchers.Main) {
                        healthReport = null // 先清空，强迫 Compose 销毁旧 UI
                        @Suppress("UNCHECKED_CAST")
                        healthReport = (report as Map<String, Any>).toMap() // 创建全新对象触发重绘
                        uiMessage = "✅ 硬件健康监控诊断已刷新"
                    }
                } else {
                    withContext(Dispatchers.Main) { uiMessage = "⚠️ 硬件未返回健康监控数据" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { uiMessage = "❌ 健康状态获取失败: ${e.message}" }
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
        if (!isTcpConnected) {
            uiMessage = "⚠️ 请先建立 TCP 连接"
            return
        }
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connectionState = ConnectionState.Connecting }
            if (driver.openBoard()) {
                val info = driver.boardInfo

                driver.fetchStatusDefinition()?.let { parser.initTable(it) }
                driver.fetchControlStatus()?.let { parser.initControlTable(it) }

                // 强制切回主线程绑定硬件反馈数据
                withContext(Dispatchers.Main) {
                    boardInfo = info
                    firmwareVersion = "v${info?.acquisitionDriverVersion ?: "N/A"}"
                    instrumentType = when (info?.instrumentType?.toInt()) {
                        0 -> "MID-IR"
                        1 -> "NIR"
                        else -> "Unknown (${info?.instrumentType})"
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
        if (!isBoardOpened) {
            uiMessage = "⚠️ 请先打开板卡并获取硬件身份"
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                driver.configure(config.params.resolution, config.params.firstGain, config.params.secondGain, config.params.startWave, config.params.stopWave)
                withContext(Dispatchers.Main) {
                    isConfigApplied = true
                    uiMessage = "✅ 光学及扫描参数已成功下发至硬件"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isConfigApplied = false
                    uiMessage = "❌ 参数下发失败: ${e.message}"
                }
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

                while (isAcquiring) {
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("无法获取状态信息")
                    val coaddState = parser.extractControlValue(statusBuf, 11).toInt()

                    // 切回主线程平滑刷新进度条
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
                    "文件保存失败"
                }

                val xyData = storage.getSpectrumDataArray(rawData, metadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())

                if (xyData[0].isNotEmpty()) {
                    val points = xyData[0].zip(xyData[1]).toList()
                    val maxPoint = points.maxByOrNull { it.second }

                    // 主线程渲染图表数据
                    withContext(Dispatchers.Main) {
                        spectrumData = points
                        if (maxPoint != null) {
                            peakX = String.format("%.2f", maxPoint.first)
                            peakY = String.format("%.3f", maxPoint.second)
                        }
                        progress = 1f
                        uiMessage = "🎉 采集完成！数据已导出至: $savedPath"
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
                withContext(Dispatchers.Main) { uiMessage = "🛑 已发送停止采集指令" }
            } catch (e: Exception) {
            }
        }
    }
}