package com.wcg.app.specapp.quantitative.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wcg.app.specapp.quantitative.model.ProcessState
import com.wcg.app.specapp.quantitative.viewmodel.QuantitativeViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun QuantitativeScreen(viewModel: QuantitativeViewModel = remember { QuantitativeViewModel() }) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部配置卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("测定环境配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilePickerButton("选择暗场背景 (Dark)", viewModel.darkFile.value?.name) {
                        viewModel.darkFile.value = it.firstOrNull()
                    }
                    FilePickerButton("选择参比背景 (Ref)", viewModel.refFile.value?.name) {
                        viewModel.refFile.value = it.firstOrNull()
                    }
                }
            }
        }

        // 2. 样本操作与状态栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    openFileDialog(multiple = true) { files -> viewModel.sampleFiles.addAll(files) }
                }) {
                    Text("导入测试样本 (${viewModel.sampleFiles.size})")
                }

                Button(
                    onClick = { viewModel.startProcessing() },
                    enabled = viewModel.sampleFiles.isNotEmpty() && viewModel.darkFile.value != null && viewModel.processState.value != ProcessState.PROCESSING,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)) // 翠绿色
                ) {
                    Text(if (viewModel.processState.value == ProcessState.PROCESSING) "计算中..." else "开始智能预测")
                }

                OutlinedButton(onClick = { viewModel.clearAll() }) {
                    Text("清空列表")
                }
            }
        }

        // 3. 结果数据表格 (Data Grid)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color.Gray, RoundedCornerShape(8.dp))) {
            Column {
                // 表头
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF2C2C2C)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("文件名", modifier = Modifier.weight(2f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("灰分 (%)", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "挥发分 (%)",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "发热量 (kcal/kg)",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text("状态", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Divider()
                // 数据列表
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.results) { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(result.file.name, modifier = Modifier.weight(2f))

                            if (result.isSuccess) {
                                // 模拟超限标红 (例如灰分 > 14% 标红)
                                val ashColor =
                                    if (result.ashContent > 14.0) Color.Red else MaterialTheme.colorScheme.onSurface
                                Text(
                                    String.format("%.2f", result.ashContent),
                                    modifier = Modifier.weight(1f),
                                    color = ashColor
                                )
                                Text(String.format("%.2f", result.volatileMatter), modifier = Modifier.weight(1f))
                                Text(String.format("%.0f", result.calorificValue), modifier = Modifier.weight(1f))
                                Text("✅ 成功", modifier = Modifier.weight(1f), color = Color(0xFF10B981))
                            } else {
                                Text("-", modifier = Modifier.weight(1f))
                                Text("-", modifier = Modifier.weight(1f))
                                Text("-", modifier = Modifier.weight(1f))
                                Text(
                                    "❌ 失败: ${result.errorMessage}",
                                    modifier = Modifier.weight(1f),
                                    color = Color.Red
                                )
                            }
                        }
                        Divider(color = Color.LightGray.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

// 辅助组件：文件选择按钮
@Composable
fun FilePickerButton(label: String, selectedName: String?, onFilesSelected: (List<File>) -> Unit) {
    OutlinedButton(onClick = { openFileDialog(multiple = false, onFilesSelected) }) {
        Text(selectedName ?: label)
    }
}

// 调用系统原生文件选择器 (支持多选)
fun openFileDialog(multiple: Boolean, onResult: (List<File>) -> Unit) {
    val dialog = FileDialog(null as Frame?, "选择光谱文件 (.spc)", FileDialog.LOAD)
    dialog.isMultipleMode = multiple
    dialog.file = "*.spc"
    dialog.isVisible = true
    val files = dialog.files.toList()
    if (files.isNotEmpty()) {
        onResult(files)
    }
}