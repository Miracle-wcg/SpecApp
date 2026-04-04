package com.wcg.app.specapp.data.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

@Serializable
data class SpectrumData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val wavelengths: List<Float>,
    val intensities: List<Float>,
    val timestamp: Long = System.currentTimeMillis(),
    val config: ScanConfig = ScanConfig()
) {
    val size: Int get() = wavelengths.size

    val minWavelength: Float get() = wavelengths.minOrNull() ?: 0f
    val maxWavelength: Float get() = wavelengths.maxOrNull() ?: 0f
    val minIntensity: Float get() = intensities.minOrNull() ?: 0f
    val maxIntensity: Float get() = intensities.maxOrNull() ?: 0f

    val timestampFormatted: String get() {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return fmt.format(Date(timestamp))
    }

    fun intensityAt(wavelength: Float): Float {
        val idx = wavelengths.indexOfFirst { it >= wavelength }
        return if (idx >= 0) intensities[idx] else 0f
    }

    fun findPeaks(minHeight: Float = 0f, minDistance: Int = 5): List<Pair<Float, Float>> {
        if (intensities.size < 3) return emptyList()
        val peaks = mutableListOf<Pair<Float, Float>>()
        var lastPeakIndex = -minDistance - 1
        for (i in 1 until intensities.size - 1) {
            val v = intensities[i]
            if (v > minHeight && v > intensities[i - 1] && v > intensities[i + 1]) {
                if (i - lastPeakIndex >= minDistance) {
                    peaks.add(wavelengths[i] to v)
                    lastPeakIndex = i
                }
            }
        }
        return peaks
    }

    companion object {
        fun empty(name: String = ""): SpectrumData = SpectrumData(
            name = name,
            wavelengths = emptyList(),
            intensities = emptyList()
        )
    }
}
