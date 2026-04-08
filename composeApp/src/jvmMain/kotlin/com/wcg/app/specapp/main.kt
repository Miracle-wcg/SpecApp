package com.wcg.app.specapp

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    // 设置应用启动时的默认窗口大小
    val windowState = rememberWindowState(size = DpSize(1440.dp, 900.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "SpectraX",
        state = windowState,
        // 新增：读取 resources 目录下的 logo.png 作为窗口图标
        icon = painterResource("logo.png")
    ) {
        // 设置操作系统级别的窗口最小尺寸限制，防止 UI 挤压变形
        window.minimumSize = Dimension(1280, 800)

        // 启动主应用 UI
        App()
    }
}