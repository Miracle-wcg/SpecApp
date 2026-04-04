package com.wcg.app.specapp.data.model

data class DeviceInfo(
    val portName: String,
    val description: String,
    val isConnected: Boolean = false
) {
    val displayName: String get() = if (description.isNotBlank()) "$portName ($description)" else portName
}
