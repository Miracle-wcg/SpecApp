package com.spectrometer.config

class SpectrometerProperties {
    var serverIp: String = "127.0.0.1"
    var tcpPort: Int = 9000
    var udpPort: Int = 5025
    var boardName: String = "MAC_00_02_2C_08_1F_82"
    var laserFreq: Double = 15798.0
    var savePath: String = "D:/SpectraData/Exports/"
    var savePathWindows: String = "D:/SpectraData/Exports/"

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