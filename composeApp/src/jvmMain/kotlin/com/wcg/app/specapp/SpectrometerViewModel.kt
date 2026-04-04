package com.wcg.app.specapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trionesdev.oca.core.shared.spectrometer.config.SpectrometerProperties
import com.trionesdev.oca.core.shared.spectrometer.driver.AcquisitionDriverClient
import com.trionesdev.oca.core.shared.spectrometer.subsystem.MetadataParser
import com.trionesdev.oca.core.shared.spectrometer.subsystem.SpectrometerDriver
import com.trionesdev.oca.core.shared.spectrometer.subsystem.SpectrumStorage
import kotlinx.coroutines.*

enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }

enum class AppScreen(val title: String, val icon: String) {
    Analysis("Analysis", "📊"),
    Setup("Setup", "☷"),
    History("History", "↺"),
    Settings("Settings", "⚙")
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

    // 三阶段连接状态
    var isTcpConnected by mutableStateOf(false)
    var isBoardOpened by mutableStateOf(false)

    // 硬件反馈信息
    var boardInfo by mutableStateOf<AcquisitionDriverClient.BoardInformation?>(null)
    var firmwareVersion by mutableStateOf("N/A")
    var instrumentType by mutableStateOf("N/A")

    // 数据与进度状态
    var spectrumData by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    var currentSweep by mutableStateOf(0)
    var totalSweeps by mutableStateOf(config.params.numScans)
    var progress by mutableStateOf(0f)
    var peakX by mutableStateOf("0.00")
    var peakY by mutableStateOf("0.000")

    // 文件导出格式状态 (默认 SPC)
    var exportFormat by mutableStateOf("SPC")

    // 步骤 1：连接 TCP
    fun connectTcp() {
        scope.launch(Dispatchers.IO) {
            connectionState = ConnectionState.Connecting
            if (driver.connectTcp()) {
                isTcpConnected = true
                connectionState = ConnectionState.Connected
            } else {
                isTcpConnected = false
                connectionState = ConnectionState.Error
            }
        }
    }

    // 步骤 2：打开板卡
    fun openBoard() {
        if (!isTcpConnected) return
        scope.launch(Dispatchers.IO) {
            connectionState = ConnectionState.Connecting
            if (driver.openBoard()) {
                val info = driver.boardInfo
                boardInfo = info
                firmwareVersion = "v${info.acquisitionDriverVersion}"
                instrumentType = when(info.instrumentType.toInt()) {
                    0 -> "MID-IR"
                    1 -> "NIR"
                    else -> "Unknown (${info.instrumentType})"
                }

                // 初始化元数据字典
                driver.fetchStatusDefinition()?.let { parser.initTable(it) }
                driver.fetchControlStatus()?.let { parser.initControlTable(it) }

                isBoardOpened = true
                connectionState = ConnectionState.Ready
            } else {
                isBoardOpened = false
                connectionState = ConnectionState.Error
            }
        }
    }

    // 步骤 3：下发参数配置
    fun applyParameters() {
        if (!isBoardOpened) return
        scope.launch(Dispatchers.IO) {
            try {
                driver.configure(config.params.resolution, config.params.firstGain, config.params.secondGain, config.params.startWave, config.params.stopWave)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startAcquisition() {
        if (isAcquiring || !driver.isConnected) return
        isAcquiring = true
        totalSweeps = config.params.numScans
        currentSweep = 0
        progress = 0f

        scope.launch(Dispatchers.IO) {
            try {
                driver.startCoaddition(config.params.numScans, config.params.numRuns)
                val t0 = System.currentTimeMillis()
                val timeout = config.autoCollect.timeoutMs + (config.params.numScans * 1500L)

                while (isAcquiring) {
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("无法获取状态信息")
                    val coaddState = parser.extractControlValue(statusBuf, 11).toInt()

                    currentSweep = minOf(currentSweep + 1, totalSweeps)
                    progress = currentSweep.toFloat() / totalSweeps

                    if (coaddState == 0) break
                    if (System.currentTimeMillis() - t0 > timeout) throw Exception("扫描超时")
                    delay(300)
                }

                if (!isAcquiring) return@launch

                val statusBuf = driver.fetchCurrentStatus()
                val nPts = parser.extractNpts(statusBuf)
                val rawData = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, nPts, config.autoCollect.timeoutMs)
                val metadata = parser.parseDynamicMetadata(statusBuf)

                val xyData = storage.getSpectrumDataArray(rawData, metadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())

                if (xyData[0].isNotEmpty()) {
                    val points = xyData[0].zip(xyData[1]).toList()
                    val maxPoint = points.maxByOrNull { it.second }

                    withContext(Dispatchers.Main) {
                        spectrumData = points
                        if (maxPoint != null) {
                            peakX = String.format("%.2f", maxPoint.first)
                            peakY = String.format("%.3f", maxPoint.second)
                        }
                        progress = 1f
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isAcquiring = false
            }
        }
    }

    fun stopAcquisition() {
        isAcquiring = false
        scope.launch(Dispatchers.IO) {
            try { driver.stopAcquisition() } catch (e: Exception) {}
        }
    }
}