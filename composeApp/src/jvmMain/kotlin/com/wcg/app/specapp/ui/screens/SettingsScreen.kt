package com.wcg.app.specapp.ui.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.ui.theme.AccentCyan
import com.wcg.app.specapp.viewmodel.AppLanguage
import com.wcg.app.specapp.ui.theme.BgDark
import com.wcg.app.specapp.ui.theme.BorderDark
import com.wcg.app.specapp.ui.theme.DangerRed
import com.wcg.app.specapp.ui.theme.PanelBg
import com.wcg.app.specapp.viewmodel.SpectrometerViewModel
import com.wcg.app.specapp.ui.theme.TextMuted
import com.wcg.app.specapp.ui.theme.TextWhite
import com.wcg.app.specapp.ui.theme.WarningOrange
import com.wcg.app.specapp.quantitative.algorithm.ChemometricsEngine
import com.wcg.app.specapp.utils.NativeDialogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

@Composable
fun SettingsScreen(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (lang == AppLanguage.Chinese) "系统设置" else "System Settings", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // 左侧：包含 状态面板 与 新增的文件命名面板
            Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SystemStatusPanel(viewModel, modifier = Modifier.weight(1f))
                FileNamingPanel(viewModel, modifier = Modifier.weight(1.2f))
                // Python 引擎配置面板
                AlgorithmEnginePanel(viewModel)
            }
            // 右侧：日志终端
            SystemLogPanel(viewModel, modifier = Modifier.weight(1.5f))
        }
    }
}

@Composable
private fun SystemStatusPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    var memoryUsageMB by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            val usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory()
            memoryUsageMB = usedMemoryBytes / (1024 * 1024)
            delay(2000)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (lang == AppLanguage.Chinese) "🖥️ 运行状态与监控" else "🖥️ System Status", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (lang == AppLanguage.Chinese) "系统语言" else "Language", color = TextMuted, fontSize = 14.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.appLanguage = AppLanguage.English }) {
                        RadioButton(
                            selected = lang == AppLanguage.English,
                            onClick = { viewModel.appLanguage = AppLanguage.English },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan, unselectedColor = TextMuted)
                        )
                        Text("English", color = if (lang == AppLanguage.English) TextWhite else TextMuted, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.appLanguage = AppLanguage.Chinese }) {
                        RadioButton(
                            selected = lang == AppLanguage.Chinese,
                            onClick = { viewModel.appLanguage = AppLanguage.Chinese },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan, unselectedColor = TextMuted)
                        )
                        Text("中文", color = if (lang == AppLanguage.Chinese) TextWhite else TextMuted, fontSize = 14.sp)
                    }
                }
            }
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

            ConfigRow(if (lang == AppLanguage.Chinese) "应用版本" else "App Version", "v1.0.1", true)
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow(if (lang == AppLanguage.Chinese) "JVM 环境" else "JVM Env", System.getProperty("java.version"))
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow(if (lang == AppLanguage.Chinese) "系统架构" else "OS Arch", System.getProperty("os.name") + " " + System.getProperty("os.arch"))
            HorizontalDivider(color = BorderDark, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ConfigRow(if (lang == AppLanguage.Chinese) "本地存储路径" else "Local Storage", System.getProperty("user.dir"))

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A), RoundedCornerShape(8.dp)).border(1.dp,
                    BorderDark, RoundedCornerShape(8.dp)).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (lang == AppLanguage.Chinese) "所有系统运行正常" else "All Systems Operational", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(if (lang == AppLanguage.Chinese) "内存使用: ~${memoryUsageMB} MB" else "Memory Usage: ~${memoryUsageMB} MB", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

// -----------------------------------------------------
// 新增：文件命名模板配置卡片
// -----------------------------------------------------
@Composable
private fun FileNamingPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (lang == AppLanguage.Chinese) "📁 文件命名模板" else "📁 File Naming Template", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsDarkTextField(if (lang == AppLanguage.Chinese) "操作员 / Operator" else "Operator", viewModel.operatorName, { viewModel.operatorName = it }, Modifier.weight(1f))
                SettingsDarkTextField(if (lang == AppLanguage.Chinese) "批次号 / Batch No." else "Batch No.", viewModel.batchNumber, { viewModel.batchNumber = it }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))

            SettingsDarkTextField(if (lang == AppLanguage.Chinese) "命名规则 / Generation Rule" else "Naming Rule", viewModel.fileNameTemplate, { viewModel.fileNameTemplate = it }, Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(if (lang == AppLanguage.Chinese) "[操作员]" else "[Operator]") { viewModel.fileNameTemplate += if (lang == AppLanguage.Chinese) "[操作员]" else "[Operator]" }
                TagChip(if (lang == AppLanguage.Chinese) "[批次号]" else "[Batch]") { viewModel.fileNameTemplate += if (lang == AppLanguage.Chinese) "[批次号]" else "[Batch]" }
                TagChip(if (lang == AppLanguage.Chinese) "[时间戳]" else "[Timestamp]") { viewModel.fileNameTemplate += if (lang == AppLanguage.Chinese) "[时间戳]" else "[Timestamp]" }
                TagChip(if (lang == AppLanguage.Chinese) "[日期]" else "[Date]") { viewModel.fileNameTemplate += if (lang == AppLanguage.Chinese) "[日期]" else "[Date]" }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column {
                    Text(if (lang == AppLanguage.Chinese) "生成预览 / Real-time Preview:" else "Real-time Preview:", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(viewModel.getGeneratedFileName(), color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2B3648))
            .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text, color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsDarkTextField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderDark,
                focusedBorderColor = AccentCyan,
                unfocusedTextColor = TextWhite,
                focusedTextColor = TextWhite,
                unfocusedContainerColor = BgDark,
                focusedContainerColor = BgDark
            ),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

// -----------------------------------------------------

@Composable
private fun ConfigRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            color = if (isHighlight) AccentCyan else TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun SystemLogPanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    val listState = rememberLazyListState()
    val logLines = remember { mutableStateListOf<String>() }
    val logFile = File(System.getProperty("user.dir"), "logs/specapp.log")

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!logFile.exists()) {
                withContext(Dispatchers.Main) { logLines.add("[SYSTEM] Waiting for log file at: ${logFile.absolutePath}") }
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
                                val utf8Line = String(line!!.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                                newLines.add(utf8Line)
                            }
                            lastPointer = raf.filePointer
                            raf.close()

                            if (newLines.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    logLines.addAll(newLines)
                                    if (logLines.size > 1000) logLines.removeRange(0, logLines.size - 1000)
                                }
                            }
                        } else if (fileLength < lastPointer) {
                            lastPointer = 0L
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                delay(1000)
            }
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) listState.animateScrollToItem(logLines.size - 1)
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
                    Text(if (lang == AppLanguage.Chinese) "📄 实时执行日志" else "📄 Real-time Logs", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = logFile.absolutePath,
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp).widthIn(max = 200.dp)
                    )
                }

                Box(
                    modifier = Modifier.border(1.dp, BorderDark, RoundedCornerShape(4.dp)).clickable { logLines.clear() }.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (lang == AppLanguage.Chinese) "清空" else "CLEAR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFF0F172A)).border(1.dp,
                    BorderDark, RoundedCornerShape(4.dp)).padding(12.dp)
            ) {
                if (logLines.isEmpty()) {
                    Text(if (lang == AppLanguage.Chinese) "暂无日志..." else "No logs available...", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(logLines) { line ->
                            val color = when {
                                line.contains("ERROR") || line.contains("Exception") -> DangerRed
                                line.contains("WARN") -> WarningOrange
                                line.contains("INFO") -> AccentCyan
                                else -> TextMuted
                            }
                            Text(text = line, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlgorithmEnginePanel(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(if (lang == AppLanguage.Chinese) "🧠 ONNX 智能推理引擎" else "🧠 ONNX Native Engine", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccentCyan).clickable {
                    try {
                        ChemometricsEngine.loadModels(viewModel.onnxModelDirectory)
                        viewModel.uiMessage = if (lang == AppLanguage.Chinese) "✅ ONNX 模型组已成功加载至内存" else "✅ ONNX models loaded into memory"
                    } catch(e: Exception) {
                        viewModel.uiMessage = "❌ 加载失败: ${e.message}"
                    }
                }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (lang == AppLanguage.Chinese) "应用并加载" else "Load Models", color = BgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 🌟 优化点：将 Label 单独提出来，不参与横向排列的对齐干扰
            Text(
                text = if (lang == AppLanguage.Chinese) "ONNX 模型库目录 (包含多个 .onnx)" else "ONNX Models Directory",
                color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 🌟 将输入框本体与两个按钮放入同一个严格对齐的 Row 中
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = viewModel.onnxModelDirectory,
                    onValueChange = { viewModel.onnxModelDirectory = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f), // 严格限制高度 48dp
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = BorderDark,
                        focusedBorderColor = AccentCyan,
                        unfocusedTextColor = TextWhite,
                        focusedTextColor = TextWhite,
                        unfocusedContainerColor = BgDark,
                        focusedContainerColor = BgDark
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val path = NativeDialogUtils.pickDirectory(if (lang == AppLanguage.Chinese) "选择 ONNX 模型所在文件夹" else "Choose ONNX Directory", viewModel.onnxModelDirectory)
                        if (path != null) viewModel.onnxModelDirectory = path
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)),
                    modifier = Modifier.size(48.dp), // 严格尺寸 48x48
                    contentPadding = PaddingValues(0.dp)
                ) { Text("📂", fontSize = 16.sp) }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.resetOnnxDirectory() },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)),
                    modifier = Modifier.size(48.dp), // 严格尺寸 48x48
                    contentPadding = PaddingValues(0.dp)
                ) { Text("⟲", fontSize = 18.sp) }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (lang == AppLanguage.Chinese) "⚠️ 提示：系统启动时会自动扫描项目默认 models 目录。如需更改，请指定新目录并点击【应用并加载】。"
                else "⚠️ Note: Scans default models dir on startup. To change, select new dir and click Apply.",
                color = WarningOrange, fontSize = 10.sp, lineHeight = 16.sp
            )
        }
    }
}