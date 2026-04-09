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
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi
            )

            packageName = "SpectraX"
            packageVersion = "1.0.1"

            description = "Spec Application"
            vendor = "wcg"

            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.management"
            )

            windows {
                // ✔ EXE 图标
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))

                // ✔ 控制台关闭（桌面应用建议）
                console = false

                // ✔ 安装目录
                dirChooser = true

                // ✔ 快捷方式
                shortcut = true

                // ✔ 开机菜单
                menu = true

                // ✔ 升级支持
                upgradeUuid = "123e4567-e89b-12d3-a456-426614174000"
                perUserInstall = true
            }

            buildTypes.release.proguard {
                isEnabled.set(false)
            }
        }
    }
}
