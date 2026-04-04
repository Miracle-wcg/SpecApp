package com.wcg.app.specapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wcg.app.specapp.data.DeviceManager
import com.wcg.app.specapp.data.SpectrumRepository
import com.wcg.app.specapp.data.model.AppSettings
import com.wcg.app.specapp.data.model.DisplayMode
import com.wcg.app.specapp.data.model.ScanConfig
import com.wcg.app.specapp.data.model.SpectrumData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SpectrumViewModel(
    private val deviceManager: DeviceManager,
    private val repository: SpectrumRepository,
    settings: AppSettings
) : ViewModel() {

    // ── scan config ────────────────────────────────────────────────────────────
    private val _config = MutableStateFlow(
        ScanConfig(
            integrationTimeMs = settings.defaultIntegrationTimeMs,
            averaging = settings.defaultAveraging,
            wavelengthStart = settings.defaultWavelengthStart,
            wavelengthEnd = settings.defaultWavelengthEnd,
            smoothingPoints = settings.defaultSmoothingPoints
        )
    )
    val config: StateFlow<ScanConfig> = _config

    // ── spectrum state ─────────────────────────────────────────────────────────
    private val _currentSpectrum = MutableStateFlow<SpectrumData?>(null)
    val currentSpectrum: StateFlow<SpectrumData?> = _currentSpectrum

    private val _darkSpectrum = MutableStateFlow<SpectrumData?>(null)
    val darkSpectrum: StateFlow<SpectrumData?> = _darkSpectrum

    private val _referenceSpectrum = MutableStateFlow<SpectrumData?>(null)
    val referenceSpectrum: StateFlow<SpectrumData?> = _referenceSpectrum

    private val _overlaySpectra = MutableStateFlow<List<SpectrumData>>(emptyList())
    val overlaySpectra: StateFlow<List<SpectrumData>> = _overlaySpectra

    // ── acquisition state ──────────────────────────────────────────────────────
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _status = MutableStateFlow("就绪")
    val status: StateFlow<String> = _status

    private val _displayMode = MutableStateFlow(DisplayMode.RAW)
    val displayMode: StateFlow<DisplayMode> = _displayMode

    private val _showGrid = MutableStateFlow(true)
    val showGrid: StateFlow<Boolean> = _showGrid

    private val _showPeaks = MutableStateFlow(true)
    val showPeaks: StateFlow<Boolean> = _showPeaks

    private val _autoscaleY = MutableStateFlow(true)
    val autoscaleY: StateFlow<Boolean> = _autoscaleY

    private var continuousJob: Job? = null

    val connectionState = deviceManager.connectionState

    // ── actions ────────────────────────────────────────────────────────────────

    fun updateConfig(block: ScanConfig.() -> ScanConfig) {
        _config.value = _config.value.block()
    }

    /** Take a single spectrum */
    fun singleScan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            _status.value = "采集中..."
            val result = deviceManager.acquireSpectrum(_config.value)
            if (result != null) {
                _currentSpectrum.value = applyDisplayMode(result)
                _status.value = "采集完成 (${result.wavelengths.size} 点)"
            } else {
                _status.value = "采集失败 – 请先连接设备"
            }
            _isScanning.value = false
        }
    }

    /** Start continuous scanning */
    fun startContinuousScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        continuousJob = viewModelScope.launch {
            _status.value = "连续采集中..."
            while (true) {
                val result = deviceManager.acquireSpectrum(_config.value)
                if (result != null) _currentSpectrum.value = applyDisplayMode(result)
                delay(50L)
            }
        }
    }

    /** Stop continuous scanning */
    fun stopScan() {
        continuousJob?.cancel()
        continuousJob = null
        _isScanning.value = false
        _status.value = "已停止"
    }

    /** Capture dark background spectrum */
    fun captureDark() {
        viewModelScope.launch {
            _status.value = "采集暗背景..."
            val result = deviceManager.acquireSpectrum(_config.value)
            _darkSpectrum.value = result?.copy(name = "暗背景_${System.currentTimeMillis()}")
            _status.value = if (result != null) "暗背景已采集" else "暗背景采集失败"
        }
    }

    /** Capture reference spectrum for absorbance/transmittance */
    fun captureReference() {
        viewModelScope.launch {
            _status.value = "采集参考光谱..."
            val result = deviceManager.acquireSpectrum(_config.value)
            _referenceSpectrum.value = result?.copy(name = "参考_${System.currentTimeMillis()}")
            _status.value = if (result != null) "参考光谱已采集" else "参考光谱采集失败"
        }
    }

    fun clearDark() { _darkSpectrum.value = null }
    fun clearReference() { _referenceSpectrum.value = null }

    /** Save current spectrum to disk */
    fun saveCurrentSpectrum(): String? {
        val sp = _currentSpectrum.value ?: return null
        val file = repository.saveSpectrum(sp)
        _status.value = "已保存: ${file.name}"
        return file.absolutePath
    }

    fun addOverlay(sp: SpectrumData) {
        _overlaySpectra.value = (_overlaySpectra.value + sp).takeLast(4)
    }

    fun clearOverlays() { _overlaySpectra.value = emptyList() }

    fun setDisplayMode(mode: DisplayMode) { _displayMode.value = mode }
    fun toggleGrid() { _showGrid.value = !_showGrid.value }
    fun togglePeaks() { _showPeaks.value = !_showPeaks.value }
    fun toggleAutoscale() { _autoscaleY.value = !_autoscaleY.value }

    fun spectraToDisplay(): List<SpectrumData> {
        val current = _currentSpectrum.value ?: return emptyList()
        return listOf(current) + _overlaySpectra.value
    }

    // ── display mode transform ─────────────────────────────────────────────────

    private fun applyDisplayMode(raw: SpectrumData): SpectrumData {
        val ref = _referenceSpectrum.value ?: return raw
        if (_displayMode.value == DisplayMode.RAW) return raw
        val dark = _darkSpectrum.value
        val newIntensities = raw.wavelengths.mapIndexed { i, wl ->
            val rawI = raw.intensities[i] - (dark?.intensityAt(wl) ?: 0f)
            val refI = ref.intensityAt(wl) - (dark?.intensityAt(wl) ?: 0f)
            if (refI <= 0f) 0f
            else when (_displayMode.value) {
                DisplayMode.TRANSMITTANCE -> (rawI / refI * 100f).coerceIn(0f, 200f)
                DisplayMode.ABSORBANCE    -> {
                    val t = (rawI / refI).coerceIn(1e-6f, 1f)
                    -Math.log10(t.toDouble()).toFloat().coerceIn(0f, 5f)
                }
                else -> raw.intensities[i]
            }
        }
        return raw.copy(intensities = newIntensities)
    }

    override fun onCleared() {
        super.onCleared()
        continuousJob?.cancel()
    }
}
