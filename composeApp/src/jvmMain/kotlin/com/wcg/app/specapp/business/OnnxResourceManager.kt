package com.wcg.app.specapp.business

import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 🌟 [重构解耦] ONNX 本地资源管理器 (全自动自适应版)
 * 专门负责从 JAR 包内部动态释放和管理机器学习模型文件
 */
object OnnxResourceManager {
    private val log = LoggerFactory.getLogger(OnnxResourceManager::class.java)

    // 核心前缀白名单：只要以这些前缀开头且是 .onnx 文件，就会被自动识别、提取和管理
    private val targetPrefixes = listOf(
        "ash_",
        "fixed_carbon_",
        "heat_value_",
        "moisture_",
        "sulfur_",
        "volatile_"
    )

    /**
     * 🌟 黑科技：智能扫描内部资源目录，获取所有匹配的模型文件名
     * 完美兼容 IDE 开发环境与 JAR 包运行环境
     */
    private fun scanBundledModels(): List<String> {
        val matchedNames = mutableSetOf<String>()
        val resourcePath = "/models"

        try {
            val url = this::class.java.getResource(resourcePath)
            if (url != null) {
                if (url.protocol == "file") {
                    // [开发环境] 此时 /models 是个真实的文件夹，直接读取文件系统
                    File(url.toURI()).listFiles()?.forEach { matchedNames.add(it.name) }
                } else if (url.protocol == "jar") {
                    // [生产环境] 此时 /models 被封死在 JAR 里，通过连接器暴力解析 JAR 内部结构
                    val jarConn = url.openConnection() as JarURLConnection
                    jarConn.jarFile.use { jar ->
                        jar.entries().asSequence().forEach { entry ->
                            val name = entry.name
                            // 筛选出位于 models/ 目录下且不是文件夹的条目
                            if (name.startsWith("models/") && !entry.isDirectory) {
                                matchedNames.add(name.substringAfterLast("/"))
                            }
                        }
                    }
                }
            } else {
                // [兜底方案] 尝试直接通过流读取目录清单 (部分底层类加载器支持)
                val stream = this::class.java.getResourceAsStream(resourcePath)
                if (stream != null) {
                    stream.bufferedReader().readLines().forEach { matchedNames.add(it) }
                    stream.close()
                }
            }
        } catch (e: Exception) {
            log.error("❌ 扫描内置模型列表时发生异常", e)
        }

        // 🌟 严格过滤：必须是 .onnx 结尾，且前缀在我们的白名单内
        val finalModels = matchedNames.filter { fileName ->
            fileName.endsWith(".onnx") && targetPrefixes.any { fileName.startsWith(it) }
        }

        log.info("🔍 动态扫描到符合条件的内置模型: $finalModels")
        return finalModels
    }

    fun extractModelsToLocalDir(): String {
        // 优先使用标准 AppData 目录，彻底解决权限问题
        val baseDir = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val appDataDir = File(baseDir, ".SpectraX${File.separator}models")

        if (!appDataDir.exists()) {
            val created = appDataDir.mkdirs()
            if (!created) {
                log.error("❌ 无法创建本地存储目录: ${appDataDir.absolutePath}")
            }
        }

        try {
            // 🌟 动态获取需要释放的模型列表，彻底告别写死文件名
            val bundledModels = scanBundledModels()

            if (bundledModels.isEmpty()) {
                log.warn("⚠️ 未扫描到任何符合前缀的内置 ONNX 模型，请检查 resources/models 目录")
                return appDataDir.absolutePath
            }

            for (fileName in bundledModels) {
                val targetFile = File(appDataDir, fileName)

                // 如果本地不存在该确切的新文件，说明需要释放
                if (!targetFile.exists()) {

                    // 🌟 智能清理：通过前缀匹配，清理掉该指标的历史版本模型
                    val currentPrefix = targetPrefixes.firstOrNull { fileName.startsWith(it) }
                    if (currentPrefix != null) {
                        appDataDir.listFiles { _, name ->
                            name.startsWith(currentPrefix) && name.endsWith(".onnx")
                        }?.forEach { oldFile ->
                            oldFile.delete()
                            log.info("🗑️ 清理旧版本模型: ${oldFile.name}")
                        }
                    }

                    // 读取内部资源流并释放到本地硬盘
                    val fileStream: InputStream? =
                        this::class.java.getResourceAsStream("/models/$fileName")
                            ?: Thread.currentThread().contextClassLoader.getResourceAsStream("models/$fileName")

                    if (fileStream != null) {
                        Files.copy(
                            fileStream,
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        fileStream.close()
                        log.info("✅ 成功释放内置模型: $fileName")
                    } else {
                        log.error("❌ JAR包内缺失模型提取流: /models/$fileName")
                    }
                }
            }
        } catch (e: Exception) {
            log.error("❌ 动态释放 ONNX 模型时发生全局异常", e)
        }

        return appDataDir.absolutePath
    }
}