package com.wcg.app.specapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val defaultIntegrationTimeMs: Int = 100,
    val defaultAveraging: Int = 1,
    val defaultWavelengthStart: Float = 200f,
    val defaultWavelengthEnd: Float = 1100f,
    val defaultSmoothingPoints: Int = 0,
    val defaultBaudRate: Int = 115200,
    val showGrid: Boolean = true,
    val showPeakLabels: Boolean = true,
    val autoscaleY: Boolean = true,
    val darkMode: Boolean = false,
    val exportDirectory: String = System.getProperty("user.home") ?: "",
    val dataDirectory: String = System.getProperty("user.home") ?: ""
)
