package com.wcg.app.specapp

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import kotlin.system.exitProcess // 🌟 引入强制退出底层进程的函数

fun main() = application {
    // 设置应用启动时的默认窗口大小
    val windowState = rememberWindowState(size = DpSize(1440.dp, 900.dp))

    Window(
        onCloseRequest = {
            // 🌟 1. 优雅通知 Compose 框架停止 UI 渲染循环
            exitApplication()

            // 🌟 2. 核弹级清理：强行杀掉当前 JVM 进程！
            // 彻底切断底层 ONNX C++ 引擎、TCP/UDP 硬件守护协程以及日志文件锁的占用，
            // 确保 Windows MSI 在覆盖安装或卸载时绝对不会出现“文件被占用”的流氓弹窗。
            exitProcess(0)
        },
        title = "SpectraX",
        state = windowState,
        // 读取 resources 目录下的 logo.png 作为窗口图标
        icon = painterResource("logo.png")
    ) {
        // 设置操作系统级别的窗口最小尺寸限制，防止用户把窗口缩得太小导致 UI 挤压变形
        window.minimumSize = Dimension(1280, 800)

        // 启动主应用 UI
        App()
    }
}