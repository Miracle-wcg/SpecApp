package com.wcg.app.specapp.business

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 🌟 [重构解耦] ONNX 本地资源管理器
 * 专门负责从 JAR 包内部动态释放和管理机器学习模型文件
 */
object OnnxResourceManager {
    private val log = LoggerFactory.getLogger(OnnxResourceManager::class.java)

    // 前缀，用于智能清理用户电脑上的旧模型
    val targetPrefixes = listOf(
        "ash_",
        "fixed_carbon_",
        "heat_value_",
        "moisture_",
        "sulfur_",
        "volatile_"
    )

    fun extractModelsToLocalDir(): String {
        // 1. 在用户电脑创建独立的模型存放目录
        val userHome = System.getProperty("user.home")
        val appDataDir = File(userHome, ".SpectraX/models")
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }

        try {
            // 3. 动态读取 JAR 包内 /models/ 目录下的所有文件列表
            val resourcePath = "/models"
            // 兼容不同的类加载器机制，尝试带 / 和不带 /
            val inputStream = this::class.java.getResourceAsStream(resourcePath)
                ?: this::class.java.getResourceAsStream("$resourcePath/")

            if (inputStream != null) {
                // 逐行读取目录中的文件名
                val availableFiles = inputStream.bufferedReader().readLines()
                inputStream.close()

                // 4. 筛选出符合前缀要求且以 .onnx 结尾的文件
                val matchedFiles = availableFiles.filter { fileName ->
                    fileName.endsWith(".onnx") && targetPrefixes.any { prefix -> fileName.startsWith(prefix) }
                }

//                if (matchedFiles.isEmpty()) {
//                    log.warn("⚠️ 在内部资源 $resourcePath 下未扫描到任何匹配的 ONNX 模型！")
//                }

                // 5. 遍历并释放匹配的文件
                for (fileName in matchedFiles) {
                    val targetFile = File(appDataDir, fileName)

                    // 如果这个新文件还不存在，说明需要释放 (首次安装 或 升级了新时间戳的模型)
                    if (!targetFile.exists()) {

                        // 🌟 高阶防坑优化：释放新模型前，先把本地旧时间戳的同类模型删掉！
                        // 防止本地同时存在 ash_v1.onnx 和 ash_v2.onnx 导致引擎加载混乱
                        val currentPrefix = targetPrefixes.first { fileName.startsWith(it) }
                        appDataDir.listFiles { _, name ->
                            name.startsWith(currentPrefix) && name.endsWith(".onnx")
                        }?.forEach { oldFile ->
                            oldFile.delete()
                            log.info("🗑️ 清理旧版本模型: ${oldFile.name}")
                        }

                        // 正式释放新模型到本地硬盘
                        val fileStream = this::class.java.getResourceAsStream("$resourcePath/$fileName")
                        if (fileStream != null) {
                            Files.copy(
                                fileStream,
                                targetFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                            fileStream.close()
                            log.info("✅ 成功释放自适应模型: $fileName")
                        }
                    }
                }
            } else {
                log.error("❌ 无法扫描资源目录: $resourcePath (目录可能在打包时被压缩工具剥离了索引)")
            }
        } catch (e: Exception) {
            log.error("❌ 动态释放 ONNX 模型过程中发生异常", e)
        }

        // 返回真实的释放路径供 ONNX 引擎读取
        return appDataDir.absolutePath
    }
}