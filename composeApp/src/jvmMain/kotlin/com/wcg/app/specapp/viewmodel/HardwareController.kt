package com.wcg.app.specapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spectrometer.config.SpectrometerProperties
import com.spectrometer.driver.AcquisitionDriverClient
import com.spectrometer.subsystem.MetadataParser
import com.spectrometer.subsystem.SpectrometerDriver
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

class HardwareController(
    val config: SpectrometerProperties,
    private val ui: UiController,
    private val scope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(HardwareController::class.java)
    val driver = SpectrometerDriver(config)
    val parser = MetadataParser()

    var connectionState by mutableStateOf(ConnectionState.Disconnected)
    var isTcpConnected by mutableStateOf(false)
    var isBoardOpened by mutableStateOf(false)
    var isConfigApplied by mutableStateOf(false)

    var boardName by mutableStateOf(config.boardName)
    var boardInfo by mutableStateOf<AcquisitionDriverClient.BoardInformation?>(null)
    var firmwareVersion by mutableStateOf("N/A")
    var instrumentType by mutableStateOf("N/A")
    var healthReport by mutableStateOf<Map<String, Any>?>(null)
    var systemMetadata by mutableStateOf<Map<String, String>?>(null)

    fun connectTcp() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connectionState = ConnectionState.Connecting }
            if (driver.connectTcp()) {
                val detectedName = driver.autoDetectBoardName()
                withContext(Dispatchers.Main) {
                    isTcpConnected = true; connectionState = ConnectionState.Connected
                    if (detectedName.isNotEmpty()) {
                        boardName = detectedName; config.boardName = detectedName
                        ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "TCP 已连接，板卡: $detectedName" else "TCP Connected: $detectedName")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    isTcpConnected = false; connectionState = ConnectionState.Error
                    ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "TCP 连接失败" else "TCP Connection Failed")
                }
            }
        }
    }

    fun openBoard() {
        if (!isTcpConnected) return
        scope.launch(Dispatchers.IO) {
            if (driver.openBoard()) {
                val info = driver.boardInfo
                driver.fetchStatusDefinition()?.let { parser.initTable(it) }
                driver.fetchControlStatus()?.let { parser.initControlTable(it) }
                withContext(Dispatchers.Main) {
                    boardInfo = info
                    firmwareVersion = "v${info?.acquisitionDriverVersion ?: "N/A"}"
                    instrumentType = when (info?.instrumentType?.toInt()) { 0 -> "MID-IR"; 1 -> "NIR"; else -> "Unknown" }
                    isBoardOpened = true; connectionState = ConnectionState.Ready
                    ui.showMsg(if (ui.appLanguage == AppLanguage.Chinese) "板卡打开成功，硬件已就绪" else "Board Ready")
                }
                checkHealth()
            }
        }
    }

    fun checkHealth() {
        if (!isBoardOpened) return
        scope.launch(Dispatchers.IO) {
            val hDef = driver.fetchHealthStatusDefinition()
            val hStat = driver.fetchHealthStatus()
            if (hDef != null && hStat != null) {
                val report = parser.parseHealthMonitoring(hDef, hStat)
                withContext(Dispatchers.Main) { @Suppress("UNCHECKED_CAST") healthReport = report as Map<String, Any> }
            }
        }
    }

    fun applyParameters() {
        if (!isBoardOpened) return
        scope.launch(Dispatchers.IO) {
            try {
                config.params.secondGain = 0
                driver.configure(config.params.resolution, config.params.firstGain, 0, config.params.startWave, config.params.stopWave)
                withContext(Dispatchers.Main) { isConfigApplied = true; ui.showMsg("参数下发成功") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isConfigApplied = false; ui.showMsg("参数下发失败") }
            }
        }
    }

    fun disconnectHardware() {
        scope.launch(Dispatchers.IO) {
            driver.disconnect()
            withContext(Dispatchers.Main) {
                isTcpConnected = false; isBoardOpened = false; isConfigApplied = false
                connectionState = ConnectionState.Disconnected; boardInfo = null
                healthReport = null; ui.showMsg("设备已断开连接")
            }
        }
    }
}