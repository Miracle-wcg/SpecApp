package com.spectrometer.config

class SpectrometerProperties {
    var serverIp: String = "192.168.1.105"
    var tcpPort: Int = 8080
    var udpPort: Int = 5025
    var boardName: String = "SP-2000X"
    var laserFreq: Double = 15798.0
    var savePath: String = "C:/SpectraData/Exports/"
    var savePathWindows: String = "C:/SpectraData/Exports/"

    var params = Params()
    var autoCollect = AutoCollect()

    class Params {
        var resolution: Short = 4
        var firstGain: Short = 1
        var secondGain: Short = 1
        var startWave: Float = 400f
        var stopWave: Float = 4000f
        var numScans: Int = 16
        var numRuns: Int = 1
    }

    class AutoCollect {
        var timeoutMs: Long = 10000L
    }
}