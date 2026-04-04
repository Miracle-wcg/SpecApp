package com.wcg.app.specapp.data

import com.wcg.app.specapp.data.model.AppSettings
import com.wcg.app.specapp.data.model.SpectrumData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Handles saving, loading and exporting spectrum data.
 */
class SpectrumRepository(private val settings: AppSettings) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val dataDir: File
        get() = File(settings.dataDirectory).resolve("SpecApp/data").also { it.mkdirs() }

    // ─────────────────────────── save / load ─────────────────────────────────

    fun saveSpectrum(spectrum: SpectrumData): File {
        val file = dataDir.resolve("${spectrum.id}.json")
        file.writeText(json.encodeToString(spectrum))
        return file
    }

    fun loadAll(): List<SpectrumData> {
        return dataDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                try { json.decodeFromString<SpectrumData>(f.readText()) }
                catch (e: Exception) { System.err.println("Failed to load spectrum ${f.name}: ${e.message}"); null }
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun loadById(id: String): SpectrumData? {
        val file = dataDir.resolve("$id.json")
        return if (file.exists()) {
            try { json.decodeFromString<SpectrumData>(file.readText()) }
            catch (e: Exception) { System.err.println("Failed to load spectrum $id: ${e.message}"); null }
        } else null
    }

    fun deleteById(id: String): Boolean {
        return dataDir.resolve("$id.json").delete()
    }

    // ─────────────────────────── CSV export ──────────────────────────────────

    fun exportToCsv(spectrum: SpectrumData, targetDir: File = File(settings.exportDirectory)): File {
        targetDir.mkdirs()
        val safe = spectrum.name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date(spectrum.timestamp))
        val file = targetDir.resolve("${safe}_$ts.csv")
        val sb = StringBuilder()
        sb.appendLine("# SpecApp 光谱数据导出")
        sb.appendLine("# 名称: ${spectrum.name}")
        sb.appendLine("# 时间: ${spectrum.timestampFormatted}")
        sb.appendLine("# 积分时间(ms): ${spectrum.config.integrationTimeMs}")
        sb.appendLine("# 平均次数: ${spectrum.config.averaging}")
        sb.appendLine("波长(nm),强度")
        for (i in spectrum.wavelengths.indices) {
            sb.appendLine("${spectrum.wavelengths[i]},${spectrum.intensities[i]}")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }
}
