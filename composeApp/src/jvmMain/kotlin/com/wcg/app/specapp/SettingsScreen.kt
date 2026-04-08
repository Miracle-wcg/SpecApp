package com.wcg.app.specapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("系统设置 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("/ System Preferences", color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // 左侧：仅保留运行状态与监控
            SystemStatusPanel(modifier = Modifier.weight(1f))

            // 右侧：新增的系统执行日志模块
            SystemLogPanel(modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun SystemStatusPanel(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("🖥️ 运行状态与监控", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SYSTEM STATUS", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 模拟系统全局状态
            ConfigRow("Application Version", "v1.0.4-Beta", true)
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow("JVM Environment", System.getProperty("java.version"))
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow("OS Architecture", System.getProperty("os.name") + " " + System.getProperty("os.arch"))
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow("Logback Engine", "Active", true)
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow("Local Storage Path", System.getProperty("user.dir"))

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A), RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("All Systems Operational", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Memory Usage: ~124 MB", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 14.sp)
        Text(value, color = if (isHighlight) AccentCyan else TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SystemLogPanel(modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val logLines = remember { mutableStateListOf<String>() }

    // 假设 logback.xml 将日志输出到了工作目录下的 logs/specapp.log
    // 这里使用 user.dir 来定位。如果你的 logback 配置了其他路径，请修改此处的 File 路径
    val logFile = File(System.getProperty("user.dir"), "logs/specapp.log")

    // 后台协程：类似于 tail -f 实时读取日志文件
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 如果文件不存在，先给个提示
            if (!logFile.exists()) {
                withContext(Dispatchers.Main) {
                    logLines.add("[SYSTEM] Waiting for log file at: ${logFile.absolutePath}")
                }
            }

            var lastPointer = 0L
            while (true) {
                if (logFile.exists()) {
                    try {
                        val fileLength = logFile.length()
                        if (fileLength > lastPointer) {
                            val raf = RandomAccessFile(logFile, "r")
                            raf.seek(lastPointer)
                            var line: String?
                            val newLines = mutableListOf<String>()

                            while (raf.readLine().also { line = it } != null) {
                                // 处理中文等 UTF-8 编码可能导致的乱码问题
                                val utf8Line = String(line!!.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                                newLines.add(utf8Line)
                            }
                            lastPointer = raf.filePointer
                            raf.close()

                            if (newLines.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    logLines.addAll(newLines)
                                    // 保持内存中最多显示最新的 1000 行
                                    if (logLines.size > 1000) {
                                        logLines.removeRange(0, logLines.size - 1000)
                                    }
                                }
                            }
                        } else if (fileLength < lastPointer) {
                            // 文件可能被日志轮转(log rotation)清空或重置了
                            lastPointer = 0L
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(1000) // 每秒轮询一次文件变化
            }
        }
    }

    // 自动滚动到最底部
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = BgDark),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("📄 实时执行日志", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(logFile.absolutePath, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                }

                // 清空控制台按钮 (仅清空 UI，不清空物理文件)
                Box(
                    modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp)).clickable { logLines.clear() }.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("CLEAR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 日志输出框
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0F172A)) // 更深的终端背景色
                    .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                if (logLines.isEmpty()) {
                    Text("No logs available...", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(logLines) { line ->
                            // 简单的按日志级别着色
                            val color = when {
                                line.contains("ERROR") || line.contains("Exception") -> DangerRed
                                line.contains("WARN") -> WarningOrange
                                line.contains("INFO") -> AccentCyan
                                else -> TextMuted
                            }
                            Text(
                                text = line,
                                color = color,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}