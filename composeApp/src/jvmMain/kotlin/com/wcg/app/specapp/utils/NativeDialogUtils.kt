package com.wcg.app.specapp.utils

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * 全局现代原生对话框工具类
 * 封装了系统级的文件与文件夹选择器，自动注入当前操作系统的原生 UI 风格。
 */
object NativeDialogUtils {
    init {
        try {
            // 强行注入系统原生 UI 风格，告别远古 Java Metal 风格
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 文件选择：调用操作系统最底层的原生弹窗 (Mac/Win 体验最佳)
     * @param filter 默认过滤 .spc 文件，也可传入 *.csv 等
     */
    fun pickFiles(title: String, multiple: Boolean = false, filter: String = "*.spc"): List<File> {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isMultipleMode = multiple
        dialog.file = filter
        dialog.isVisible = true
        return dialog.files.toList()
    }

    /**
     * 文件夹选择：使用注入了原生样式的 JFileChooser
     * (解决 Windows 无法用 FileDialog 选文件夹的痛点)
     */
    fun pickDirectory(title: String, defaultPath: String): String? {
        val chooser = JFileChooser(defaultPath).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else null
    }
}