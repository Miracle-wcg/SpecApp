package com.spectrometer.config

class SpectrometerProperties {
    var serverIp: String = "10.127.127.1"
    var tcpPort: Int = 5002
    var udpPort: Int = 20000
    var boardName: String = "MAC_00_02_2C_08_1F_82"
    var laserFreq: Double = 15798.0
    var savePath: String = "D:/SpectraData/Exports/"
    var savePathWindows: String = "D:/SpectraData/Exports/"

    var params = Params()
    var autoCollect = AutoCollect()

    class Params {
        var resolution: Short = 4
        var firstGain: Short = 7
        var secondGain: Short = 1
        var startWave: Float = 4000f
        var stopWave: Float = 8500f
        var numScans: Int = 16
        var numRuns: Int = 1
    }

    class AutoCollect {
        var timeoutMs: Long = 10000L
    }
}