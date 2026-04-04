package com.spectrometer.model

import java.time.LocalDateTime

data class SpectrumResult(
    val success: Boolean,
    val message: String? = null,
    val data: FloatArray? = null,
    val metadata: Map<String, String>? = null,
    val timestamp: LocalDateTime? = null,
    val savedFilePath: String? = null
) {
    // 供 Builder 模式兼容 Java 代码
    class Builder {
        private var success: Boolean = false
        private var message: String? = null
        private var data: FloatArray? = null
        private var metadata: Map<String, String>? = null
        private var timestamp: LocalDateTime? = null
        private var savedFilePath: String? = null

        fun success(success: Boolean) = apply { this.success = success }
        fun message(message: String?) = apply { this.message = message }
        fun data(data: FloatArray?) = apply { this.data = data }
        fun metadata(metadata: Map<String, String>?) = apply { this.metadata = metadata }
        fun timestamp(timestamp: LocalDateTime?) = apply { this.timestamp = timestamp }
        fun savedFilePath(savedFilePath: String?) = apply { this.savedFilePath = savedFilePath }
        fun build() = SpectrumResult(success, message, data, metadata, timestamp, savedFilePath)
    }
    companion object {
        @JvmStatic fun builder() = Builder()
    }
}