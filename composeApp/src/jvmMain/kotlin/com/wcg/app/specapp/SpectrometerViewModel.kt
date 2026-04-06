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
        if (isAcquiring || !driver.isConnected || !isConfigApplied) {
            uiMessage = "⚠️ 无法启动：设备未就绪或未下发参数"
            return
        }

        isAcquiring = true
        val numScans = config.params.numScans
        totalSweeps = numScans
        currentSweep = 0
        progress = 0f

        spectrumData = emptyList()
        peakX = "0.00"
        peakY = "0.000"

        scope.launch(Dispatchers.IO) {
            try {
                var accumulatedRawData: FloatArray? = null
                var finalMetadata: Map<String, String> = emptyMap()
                var actualScanCount = 0

                // 【核心重构】：执行软件级真实多次扫描与均值算法 (Software Co-addition)
                for (scanIndex in 1..numScans) {
                    if (!isAcquiring) break

                    // 1. 抓取硬件连续流 (SOURCE_CURRENT) 的当前最新单帧光谱数据
                    val statusBuf = driver.fetchCurrentStatus() ?: throw Exception("无法获取硬件状态")
                    val nPts = parser.extractNpts(statusBuf)
                    if (nPts <= 0) throw Exception("数据点数异常: $nPts")

                    val rawData = driver.fetchRawData(SpectrometerDriver.SOURCE_CURRENT, nPts, config.autoCollect.timeoutMs)
                    finalMetadata = parser.parseDynamicMetadata(statusBuf)

                    // 2. 将单帧数据累加到数组缓冲池中
                    if (accumulatedRawData == null || accumulatedRawData.size != nPts) {
                        accumulatedRawData = FloatArray(nPts)
                    }
                    for (i in 0 until nPts) {
                        accumulatedRawData[i] += rawData[i]
                    }
                    actualScanCount++

                    // 3. 实时渲染当前【未平均】的单帧光谱，让用户看到真实的扫描噪声与跳动动画
                    val liveXyData = storage.getSpectrumDataArray(rawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
                    if (liveXyData != null && liveXyData.size == 2 && liveXyData[0].isNotEmpty()) {
                        val points = liveXyData[0].zip(liveXyData[1]).toList()
                        val maxPoint = points.maxByOrNull { it.second }

                        withContext(Dispatchers.Main) {
                            spectrumData = points
                            if (maxPoint != null) {
                                peakX = String.format("%.2f", maxPoint.first)
                                peakY = String.format("%.4f", maxPoint.second)
                            }
                            currentSweep = scanIndex
                            progress = scanIndex.toFloat() / numScans
                        }
                    }

                    // 模拟硬件单次干涉仪扫描耗时，控制进度条和渲染频率
                    delay(300)
                }

                if (!isAcquiring || accumulatedRawData == null || actualScanCount == 0) return@launch

                withContext(Dispatchers.Main) { uiMessage = "⏳ 正在计算 $actualScanCount 次扫描的平滑均值并持久化..." }

                // 4. 计算所有采集帧的数学平均值 (Average)，消除随机噪声提升信噪比
                for (i in accumulatedRawData.indices) {
                    accumulatedRawData[i] /= actualScanCount.toFloat()
                }

                // 5. 将平均化后的高 SNR 数据导出到物理文件
                val savedPath = try {
                    if (exportFormat == "SPC") {
                        storage.saveToSpc(accumulatedRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    } else {
                        storage.saveToTxt(accumulatedRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble(), config.savePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                // 6. 从物理文件中反向读取解析，实现“所见即所得”，绘制最终的平滑曲线
                val finalXyData = try {
                    if (savedPath != null) {
                        storage.readFromFile(savedPath)
                    } else {
                        storage.getSpectrumDataArray(accumulatedRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
                    }
                } catch (e: Exception) {
                    storage.getSpectrumDataArray(accumulatedRawData, finalMetadata, config.laserFreq, config.params.startWave.toDouble(), config.params.stopWave.toDouble())
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
                        progress = 1f
                        uiMessage = "🎉 $actualScanCount 次扫描并求均值完成！数据已导出至: $savedPath"
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
                // 不再依赖黑盒子的停止指令，只要我们将 isAcquiring 设为 false，上方的 For 循环就会立即安全中断
                driver.stopAcquisition()
                withContext(Dispatchers.Main) { uiMessage = "🛑 已手动中断光谱采集序列" }
            } catch (e: Exception) {}
        }
    }
}