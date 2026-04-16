package com.wcg.app.specapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import com.wcg.app.specapp.ui.theme.*
import com.wcg.app.specapp.utils.NativeDialogUtils
import com.wcg.app.specapp.viewmodel.AppLanguage
import com.wcg.app.specapp.viewmodel.SpectrometerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

@Composable
fun SettingsScreen(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    // 外层使用 Box，方便绝对定位底部弹出框
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (lang == AppLanguage.Chinese) "系统设置" else "System Settings", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // 左侧：包含 状态面板、文件命名面板、引擎配置面板 (支持全局滑动)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SystemStatusPanel(viewModel)
                    FileNamingPanel(viewModel)
                    AlgorithmEnginePanel(viewModel)

                    // 底部留白
                    Spacer(modifier = Modifier.height(16.dp))
                }
                // 右侧：日志终端 (固定高度)
                SystemLogPanel(viewModel, modifier = Modifier.weight(1.5f))
            }
        }

        // 🌟 终极优化：无遮罩的底部滑出式提示框
        AnimatedVisibility(
            visible = viewModel.showDialog,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp) // 悬浮于底部上方
        ) {
            Card(
                modifier = Modifier.widthIn(min = 320.dp, max = 450.dp),
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderDark),
                elevation = CardDefaults.cardElevation(16.dp) // 增加阴影，突出层级
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(viewModel.dialogTitle, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(viewModel.dialogMessage, color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 靠右对齐的操作按钮
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentCyan)
                                .clickable { viewModel.showDialog = false }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(if (lang == AppLanguage.Chinese) "确定" else "OK", color = BgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemStatusPanel(viewModel: SpectrometerViewModel) {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
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

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A), RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(16.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileNamingPanel(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (lang == AppLanguage.Chinese) "📁 文件命名模板" else "📁 File Naming", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(if (lang == AppLanguage.Chinese) "重置" else "Reset", Color(0xFF2B3648)) { viewModel.restoreDefaultNaming() }
                    ActionButton(if (lang == AppLanguage.Chinese) "保存" else "Save", AccentCyan, BgDark) { viewModel.saveNamingConfig() }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsDarkTextField(
                    label = if (lang == AppLanguage.Chinese) "操作员 / Operator" else "Operator",
                    value = viewModel.operatorName,
                    placeholder = if (lang == AppLanguage.Chinese) "请输入名称..." else "Enter name...",
                    onValueChange = { viewModel.operatorName = it },
                    modifier = Modifier.weight(1f)
                )
                SettingsDarkTextField(
                    label = if (lang == AppLanguage.Chinese) "批次号 / Batch No." else "Batch No.",
                    value = viewModel.batchNumber,
                    placeholder = if (lang == AppLanguage.Chinese) "请输入批次..." else "Enter batch...",
                    onValueChange = { viewModel.batchNumber = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            SettingsDarkTextField(
                label = if (lang == AppLanguage.Chinese) "命名规则 / Generation Rule" else "Naming Rule",
                value = viewModel.fileNameTemplate,
                placeholder = if (lang == AppLanguage.Chinese) "点击下方标签插入变量" else "Click tags to insert",
                onValueChange = { viewModel.fileNameTemplate = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tags = if (lang == AppLanguage.Chinese)
                    listOf("[操作员]", "[批次号]", "[时间戳]", "[日期]")
                else listOf("[Operator]", "[Batch]", "[Timestamp]", "[Date]")
                tags.forEach { tag -> TagChip(tag) { viewModel.fileNameTemplate += tag } }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2D4A), RoundedCornerShape(8.dp)).padding(16.dp)) {
                Column {
                    Text(if (lang == AppLanguage.Chinese) "生成预览 / Real-time Preview:" else "Preview:", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(viewModel.getGeneratedFileName(), color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AlgorithmEnginePanel(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (lang == AppLanguage.Chinese) "🧠 ONNX 智能推理引擎" else "🧠 ONNX Engine", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                ActionButton(if (lang == AppLanguage.Chinese) "加载" else "Load", AccentCyan, BgDark) { viewModel.loadOnnxModels() }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (lang == AppLanguage.Chinese) "ONNX 模型库目录 (包含多个 .onnx)" else "ONNX Models Directory",
                color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsDarkTextField(
                    label = "",
                    value = viewModel.onnxModelDirectory,
                    placeholder = "",
                    onValueChange = { viewModel.onnxModelDirectory = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 📂 按钮
                Button(
                    onClick = {
                        val path = NativeDialogUtils.pickDirectory(if (lang == AppLanguage.Chinese) "选择 ONNX 模型所在文件夹" else "Choose ONNX Directory", viewModel.onnxModelDirectory)
                        if (path != null) viewModel.onnxModelDirectory = path
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)),
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("📂", fontSize = 16.sp) }

                Spacer(modifier = Modifier.width(8.dp))
                // ⟲ 按钮
                Button(
                    onClick = { viewModel.resetOnnxDirectory() },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)),
                    modifier = Modifier.size(48.dp),
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
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFF0F172A)).border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(12.dp)
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

// -----------------------------------------------------
// 基础通用 UI 组件
// -----------------------------------------------------

@Composable
fun SettingsDarkTextField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = TextMuted.copy(alpha = 0.4f), fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderDark,
                focusedBorderColor = AccentCyan,
                unfocusedContainerColor = BgDark,
                focusedContainerColor = BgDark,
                unfocusedTextColor = TextWhite,
                focusedTextColor = TextWhite
            ),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
fun ActionButton(text: String, bgColor: Color, textColor: Color = TextWhite, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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