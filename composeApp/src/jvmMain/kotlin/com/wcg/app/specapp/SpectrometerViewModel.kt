package com.wcg.app.specapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

enum class ConnectionState { Disconnected, Connecting, Connected, Ready, Error }

enum class AppScreen(val title: String, val icon: String) {
    Analysis("Analysis", "📊"),
    Setup("Setup", "☷"),
    History("History", "↺"),
    Settings("Settings", "⚙")
}

class SpectrometerViewModel {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    var currentScreen by mutableStateOf(AppScreen.Analysis)
    var connectionState by mutableStateOf(ConnectionState.Connected)
    var isAcquiring by mutableStateOf(false)

    var spectrumData by mutableStateOf<List<Pair<Float, Float>>>(emptyList())
    var currentSweep by mutableStateOf(12)
    var totalSweeps by mutableStateOf(64)
    var progress by mutableStateOf(0.74f)
    var peakX by mutableStateOf("1245.32")
    var peakY by mutableStateOf("0.842")

    init {
        generateMockSpectrum()
    }

    fun startAcquisition() {
        if (isAcquiring) return
        isAcquiring = true
        scope.launch {
            currentSweep = 0
            progress = 0f
            while (currentSweep < totalSweeps && isAcquiring) {
                delay(100)
                currentSweep++
                progress = currentSweep.toFloat() / totalSweeps
                generateMockSpectrum()
            }
            isAcquiring = false
        }
    }

    fun stopAcquisition() {
        isAcquiring = false
    }

    private fun generateMockSpectrum() {
        val points = mutableListOf<Pair<Float, Float>>()
        val start = 4000f
        val end = 400f
        val step = (start - end) / 500f

        for (i in 0..500) {
            val x = start - i * step
            var y = Random.nextFloat() * 0.02f + 0.1f
            y += 0.4f * exp(-((x - 2200f) / 100f).pow(2f))
            y += 0.8f * exp(-((x - 1245f) / 80f).pow(2f))
            points.add(x to y)
        }
        spectrumData = points

        val maxPoint = points.maxByOrNull { it.second }
        if (maxPoint != null) {
            peakX = String.format("%.2f", maxPoint.first)
            peakY = String.format("%.3f", maxPoint.second)
        }
    }
}