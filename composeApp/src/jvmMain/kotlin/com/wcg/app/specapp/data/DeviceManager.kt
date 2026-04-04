package com.wcg.app.specapp.data

import com.fazecast.jSerialComm.SerialPort
import com.wcg.app.specapp.data.model.DeviceInfo
import com.wcg.app.specapp.data.model.ScanConfig
import com.wcg.app.specapp.data.model.SpectrumData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manages communication with a spectrometer device over a serial port.
 * When no real device is available, falls back to the built-in simulator.
 */
class DeviceManager {

    private var serialPort: SerialPort? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    /** Whether the current "connection" is a simulated device. */
    var isSimulated: Boolean = false
        private set

    // ─────────────────────────── device discovery ────────────────────────────

    fun listPorts(): List<DeviceInfo> {
        val realPorts = SerialPort.getCommPorts().map { port ->
            DeviceInfo(
                portName = port.systemPortName,
                description = port.portDescription ?: ""
            )
        }
        return realPorts + DeviceInfo(portName = "SIMULATOR", description = "内置光谱仪模拟器")
    }

    // ──────────────────────────── connect / disconnect ────────────────────────

    suspend fun connect(portName: String, baudRate: Int = 115200): Boolean = withContext(Dispatchers.IO) {
        appendLog("正在连接 $portName ...")
        _connectionState.value = ConnectionState.CONNECTING

        if (portName == "SIMULATOR") {
            isSimulated = true
            _connectionState.value = ConnectionState.CONNECTED
            appendLog("已连接到模拟器")
            return@withContext true
        }

        val port = SerialPort.getCommPort(portName)
        port.baudRate = baudRate
        port.numDataBits = 8
        port.numStopBits = SerialPort.ONE_STOP_BIT
        port.parity = SerialPort.NO_PARITY

        return@withContext if (port.openPort()) {
            serialPort = port
            isSimulated = false
            _connectionState.value = ConnectionState.CONNECTED
            appendLog("已连接到 $portName @ ${baudRate}bps")
            true
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
            appendLog("连接失败: $portName")
            false
        }
    }

    fun disconnect() {
        serialPort?.closePort()
        serialPort = null
        isSimulated = false
        _connectionState.value = ConnectionState.DISCONNECTED
        appendLog("已断开连接")
    }

    // ──────────────────────────── spectrum acquisition ────────────────────────

    /**
     * Acquire a single spectrum from the connected device (or simulator).
     */
    suspend fun acquireSpectrum(config: ScanConfig): SpectrumData? = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.CONNECTED) return@withContext null
        return@withContext if (isSimulated) simulateSpectrum(config) else readFromDevice(config)
    }

    // ─────────────────────────── simulated device ────────────────────────────

    private suspend fun simulateSpectrum(config: ScanConfig): SpectrumData {
        // Simulate acquisition delay (integration time)
        delay(config.integrationTimeMs.toLong().coerceIn(10L, 500L))

        val n = 512
        val wavelengths = List(n) { i ->
            config.wavelengthStart + i.toFloat() / (n - 1) * (config.wavelengthEnd - config.wavelengthStart)
        }

        // Simulate a realistic-looking spectrum: a broad background + several Gaussian peaks + noise
        val rawIntensities = wavelengths.map { wl ->
            val background = 200f + 0.3f * (wl - config.wavelengthStart)
            val peak1 = 45000f * gaussian(wl, 486f, 8f)       // Hβ
            val peak2 = 60000f * gaussian(wl, 589f, 5f)       // Na D
            val peak3 = 30000f * gaussian(wl, 656f, 10f)      // Hα
            val peak4 = 15000f * gaussian(wl, 760f, 6f)       // O2
            val noise = Random.nextFloat() * 50f - 25f
            (background + peak1 + peak2 + peak3 + peak4 + noise).coerceAtLeast(0f)
        }

        // Apply averaging (already averaged for sim – just adjust scale slightly)
        val scaled = rawIntensities.map { it * (1f + (Random.nextFloat() - 0.5f) * 0.01f) }

        // Apply smoothing
        val smoothed = if (config.smoothingPoints > 0) smooth(scaled, config.smoothingPoints) else scaled

        return SpectrumData(
            name = "光谱_${System.currentTimeMillis()}",
            wavelengths = wavelengths,
            intensities = smoothed,
            config = config
        )
    }

    // ──────────────────────────── real device I/O ─────────────────────────────

    private suspend fun readFromDevice(config: ScanConfig): SpectrumData? {
        val port = serialPort ?: return null
        return try {
            // Send scan command; real protocol depends on hardware
            val cmd = buildScanCommand(config)
            port.writeBytes(cmd, cmd.size.toLong())
            delay(config.integrationTimeMs.toLong() + 200L)

            val buf = ByteArray(65536)
            val read = port.readBytes(buf, buf.size.toLong())
            if (read <= 0) return null

            parseDeviceResponse(buf.copyOf(read), config)
        } catch (e: Exception) {
            appendLog("采集错误: ${e.message}")
            null
        }
    }

    private fun buildScanCommand(config: ScanConfig): ByteArray {
        // Generic command format; replace with actual device protocol
        return "SCAN ${config.integrationTimeMs} ${config.averaging}\n".toByteArray(Charsets.US_ASCII)
    }

    private fun parseDeviceResponse(data: ByteArray, config: ScanConfig): SpectrumData? {
        return try {
            val text = String(data, Charsets.US_ASCII)
            val lines = text.lines().filter { it.isNotBlank() }
            val wavelengths = mutableListOf<Float>()
            val intensities = mutableListOf<Float>()
            for (line in lines) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    wavelengths.add(parts[0].toFloat())
                    intensities.add(parts[1].toFloat())
                }
            }
            if (wavelengths.isEmpty()) null
            else SpectrumData(
                name = "光谱_${System.currentTimeMillis()}",
                wavelengths = wavelengths,
                intensities = intensities,
                config = config
            )
        } catch (e: Exception) {
            appendLog("数据解析错误: ${e.message}")
            null
        }
    }

    // ──────────────────────────── utilities ───────────────────────────────────

    private fun gaussian(x: Float, center: Float, sigma: Float): Float {
        val diff = (x - center) / sigma
        return exp(-0.5f * diff * diff)
    }

    private fun smooth(data: List<Float>, points: Int): List<Float> {
        if (points <= 0) return data
        val half = points / 2
        return data.mapIndexed { i, _ ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(data.size - 1)
            data.subList(from, to + 1).average().toFloat()
        }
    }

    private fun appendLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
        _log.value = (_log.value + "[$ts] $msg").takeLast(200)
    }

    // ──────────────────────────── connection state ─────────────────────────────

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }
}
