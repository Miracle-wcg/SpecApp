package com.wcg.app.specapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wcg.app.specapp.data.DeviceManager
import com.wcg.app.specapp.data.model.AppSettings
import com.wcg.app.specapp.data.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceViewModel(
    private val deviceManager: DeviceManager,
    private val settings: AppSettings
) : ViewModel() {

    private val _ports = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val ports: StateFlow<List<DeviceInfo>> = _ports

    private val _selectedPort = MutableStateFlow<DeviceInfo?>(null)
    val selectedPort: StateFlow<DeviceInfo?> = _selectedPort

    private val _baudRate = MutableStateFlow(settings.defaultBaudRate)
    val baudRate: StateFlow<Int> = _baudRate

    val connectionState = deviceManager.connectionState
    val log = deviceManager.log

    init { refreshPorts() }

    fun refreshPorts() {
        _ports.value = deviceManager.listPorts()
        if (_selectedPort.value == null) _selectedPort.value = _ports.value.firstOrNull()
    }

    fun selectPort(port: DeviceInfo) { _selectedPort.value = port }
    fun setBaudRate(baud: Int) { _baudRate.value = baud }

    fun connect() {
        val port = _selectedPort.value ?: return
        viewModelScope.launch {
            deviceManager.connect(port.portName, _baudRate.value)
        }
    }

    fun disconnect() { deviceManager.disconnect() }
}
