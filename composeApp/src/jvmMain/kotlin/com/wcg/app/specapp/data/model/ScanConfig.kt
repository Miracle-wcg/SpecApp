package com.wcg.app.specapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanConfig(
    val integrationTimeMs: Int = 100,
    val averaging: Int = 1,
    val wavelengthStart: Float = 200f,
    val wavelengthEnd: Float = 1100f,
    val smoothingPoints: Int = 0,
    val darkSubtraction: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.RAW
)

@Serializable
enum class DisplayMode(val label: String) {
    RAW("原始强度"),
    ABSORBANCE("吸光度"),
    TRANSMITTANCE("透射率")
}
