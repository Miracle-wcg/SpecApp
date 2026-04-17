package com.wcg.app.specapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spectrometer.subsystem.SpectrometerDriver
import com.wcg.app.specapp.business.SpectrumDataProcessor
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File

class AcquisitionController(
    private val hw: HardwareController,
    private val ws: WorkspaceController,
    private val settings: SettingsController,
    private val ui: UiController,
    private val scope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(AcquisitionController::class.java)

    var isAcquiring by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var currentSweep by mutableStateOf(0)
    var totalSweeps by mutableStateOf(hw.config.params.numScans)

    var autoScanMode by mutableStateOf(AutoScanMode.Continuous)
    var autoScanCount by mutableStateOf("10")
    var autoScanDurationMin by mutableStateOf("60")
    var autoScanIntervalSec by mutableStateOf("5")
    var isAutoSequenceRunning by mutableStateOf(false)
    var autoSequenceCompletedCount by mutableStateOf(0)

    fun startAcquisition() {
        // 🌟 恢复：如果条件不满足，直接拦截并弹窗提示，避免静默失败
        if (isAcquiring || !hw.driver.isConnected || !hw.isConfigApplied) {
            ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "无法启动：设备未就绪或参数未下发" else "Cannot start: Device not ready or config not applied")
            return
        }

        isAcquiring = true
        totalSweeps = hw.config.params.numScans
        currentSweep = 0
        progress = 0f
        ws.spectrumData = emptyList()
        ws.peakX = "0.00"
        ws.peakY = "0.000"

        scope.launch(Dispatchers.IO) {
            try {
                hw.driver.startCoaddition(hw.config.params.numScans, hw.config.params.numRuns)
                val t0 = System.currentTimeMillis()
                val timeout = hw.config.autoCollect.timeoutMs + (hw.config.params.numScans * 1500L)
                val estimatedTotalTimeMs = (hw.config.params.numScans * 1000L).coerceAtLeast(1000L)

                // 🌟 恢复：精准的内部监控轮询与数据提取
                while (isAcquiring) {
                    val statusBuf = hw.driver.fetchCurrentStatus() ?: throw Exception("Status buffer null")
                    val coaddState = hw.parser.extractControlValue(statusBuf, 11).toInt()
                    if (coaddState == 0) break

                    val elapsedMs = System.currentTimeMillis() - t0
                    val timeProgress = (elapsedMs.toFloat() / estimatedTotalTimeMs).coerceIn(0f, 0.99f)

                    try {
                        val nPts = hw.parser.extractNpts(statusBuf)
                        if (nPts > 0) {
                            val liveData = hw.driver.fetchRawData(SpectrometerDriver.SOURCE_CURRENT, nPts, 500)
                            val liveMeta = hw.parser.parseDynamicMetadata(statusBuf)
                            val liveXy = ws.storage.getSpectrumDataArray(
                                liveData, liveMeta, hw.config.laserFreq,
                                hw.config.params.startWave.toDouble(), hw.config.params.stopWave.toDouble()
                            )

                            SpectrumDataProcessor.process(liveXy)?.let { processed ->
                                withContext(Dispatchers.Main) {
                                    ws.spectrumData = processed.points
                                    ws.peakX = processed.peakX
                                    ws.peakY = processed.peakY
                                    progress = timeProgress
                                    currentSweep = (timeProgress * totalSweeps).toInt()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略中间抓取失败，不影响最终流程
                    }

                    if (System.currentTimeMillis() - t0 > timeout) {
                        throw Exception(if (ui.appLanguage == AppLanguage.Chinese) "扫描执行超时" else "Acquisition timeout limit reached")
                    }
                    delay(200)
                }

                if (!isAcquiring) return@launch

                withContext(Dispatchers.Main) {
                    ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "正在提取底层高精度数据并保存..." else "Extracting high-precision data...")
                }

                // 🌟 恢复：完整的 FIFO 读取与持久化落盘
                val finalStatusBuf = hw.driver.fetchCurrentStatus()
                val finalNpts = hw.parser.extractNpts(finalStatusBuf)
                val finalRawData = hw.driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, finalNpts, hw.config.autoCollect.timeoutMs)
                val finalMetadata = hw.parser.parseDynamicMetadata(finalStatusBuf)

                val actualFileName = settings.getGeneratedFileName()
                val exportDir = if (hw.config.savePath.endsWith(File.separator)) hw.config.savePath else hw.config.savePath + File.separator
                val absoluteSavePath = exportDir + actualFileName

                log.info("Saving spectrum data to path: {}", absoluteSavePath)

                // 核心持久化存盘分支
                val savedPath = try {
                    if (settings.exportFormat == "SPC") {
                        ws.storage.saveToSpc(finalRawData, finalMetadata, hw.config.laserFreq, hw.config.params.startWave.toDouble(), hw.config.params.stopWave.toDouble(), absoluteSavePath)
                    } else {
                        ws.storage.saveToTxt(finalRawData, finalMetadata, hw.config.laserFreq, hw.config.params.startWave.toDouble(), hw.config.params.stopWave.toDouble(), absoluteSavePath)
                    }
                } catch (e: Exception) {
                    log.error("Failed to save spectrum file.", e)
                    null
                }

                // 🌟 恢复：强制重新读取刚落盘的文件进行校验渲染，保证所见即所得
                val finalXyData = try {
                    if (savedPath != null) ws.storage.readFromFile(savedPath)
                    else ws.storage.getSpectrumDataArray(finalRawData, finalMetadata, hw.config.laserFreq, hw.config.params.startWave.toDouble(), hw.config.params.stopWave.toDouble())
                } catch (e: Exception) {
                    ws.storage.getSpectrumDataArray(finalRawData, finalMetadata, hw.config.laserFreq, hw.config.params.startWave.toDouble(), hw.config.params.stopWave.toDouble())
                }

                SpectrumDataProcessor.process(finalXyData)?.let { processed ->
                    withContext(Dispatchers.Main) {
                        ws.spectrumData = processed.points
                        ws.peakX = processed.peakX
                        ws.peakY = processed.peakY
                        currentSweep = totalSweeps
                        progress = 1f

                        ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "采集完成！保存至: $actualFileName" else "Scan completed! Saved: $actualFileName")

                        // 🌟 恢复：自动加入数据管道供后续追溯和分析
                        ws.loadedSpectra.add(AnalyzedSpectrum(
                            filePath = savedPath ?: "",
                            name = actualFileName,
                            data = processed.points,
                            color = ws.getNextColor()
                        ))

                        // 触发一次批处理算法联动
                        ws.applyPipeline()
                    }
                }

            } catch (e: Exception) {
                log.error("Acquisition pipeline error", e)
                withContext(Dispatchers.Main) {
                    ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "采集异常: ${e.message}" else "Acquisition error: ${e.message}")
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
                hw.driver.stopAcquisition()
                withContext(Dispatchers.Main) {
                    ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "已手动中断光谱采集" else "Acquisition aborted")
                }
            } catch (e: Exception) {
                log.error("Error aborting driver sequence.", e)
            }
        }
    }

    fun startAutoSequence() {
        if (isAutoSequenceRunning || isAcquiring || !hw.isConfigApplied) {
            ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "无法启动自动任务：设备未就绪" else "Cannot start auto task")
            return
        }

        val count = autoScanCount.toIntOrNull() ?: 0
        val durMs = (autoScanDurationMin.toLongOrNull() ?: 0) * 60000L
        val intMs = (autoScanIntervalSec.toLongOrNull() ?: 0) * 1000L

        isAutoSequenceRunning = true
        autoSequenceCompletedCount = 0

        scope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            while (isAutoSequenceRunning) {
                if (autoScanMode == AutoScanMode.Continuous && autoSequenceCompletedCount >= count) break
                if (autoScanMode == AutoScanMode.Scheduled && (System.currentTimeMillis() - t0) >= durMs) break

                withContext(Dispatchers.Main) { startAcquisition() }

                while (isAcquiring && isAutoSequenceRunning) delay(200)
                if (!isAutoSequenceRunning) break

                autoSequenceCompletedCount++
                delay(intMs)
            }
            isAutoSequenceRunning = false
            withContext(Dispatchers.Main) { ui.showMsg("自动序列执行结束") }
        }
    }

    fun stopAutoSequence() {
        isAutoSequenceRunning = false
        stopAcquisition()
    }
}