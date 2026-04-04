package com.wcg.app.specapp.data

import com.wcg.app.specapp.data.model.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object SettingsManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val settingsFile: File
        get() = File(System.getProperty("user.home")).resolve("SpecApp/settings.json")

    fun load(): AppSettings {
        return try {
            if (settingsFile.exists()) json.decodeFromString<AppSettings>(settingsFile.readText())
            else AppSettings()
        } catch (e: Exception) { System.err.println("Failed to load settings: ${e.message}"); AppSettings() }
    }

    fun save(settings: AppSettings) {
        settingsFile.parentFile?.mkdirs()
        settingsFile.writeText(json.encodeToString(settings))
    }
}
