import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
//    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm{
        withJava()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // --- 新增日志框架依赖 ---
            implementation("org.slf4j:slf4j-api:2.0.12")
            implementation("ch.qos.logback:logback-classic:1.5.3")
            // 添加 ONNX Runtime (支持跨平台 CPU 推理)
            implementation("com.microsoft.onnxruntime:onnxruntime:1.17.1")
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.wcg.app.specapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "SpectraX"
            packageVersion = "1.0.0"

            // 必须项：防止 Windows 打包时报 NullPointerException
            vendor = "WCG Instruments"
            description = "SpectraX Quantitative Analysis"

            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.management"
            )

            // ==========================================
            // 🌟 核心压缩配置：ProGuard 代码摇树与瘦身
            // ==========================================
            buildTypes.release.proguard {
                version.set("7.3.2")
                isEnabled.set(true)
                optimize.set(true)   // 剔除所有未使用的死代码（极大减小体积）
                obfuscate.set(false) // 保持 false 以防崩溃

                // 引入防崩溃保护规则
                configurationFiles.from(project.file("proguard-rules.pro"))
            }

            // ==========================================
            // 🌟 核心打包配置：将外置的 ONNX 模型一起打进安装包
            // ==========================================
            appResourcesRootDir.set(project.layout.projectDirectory.dir("app_resources"))

            windows {
                menuGroup = "SpectraX Analytics"
                shortcut = true
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                console = false // 隐藏运行时的黑色 CMD 窗口
                shortcut = true
                upgradeUuid = "123e4567-e89b-12d3-a456-426614174000"
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}
