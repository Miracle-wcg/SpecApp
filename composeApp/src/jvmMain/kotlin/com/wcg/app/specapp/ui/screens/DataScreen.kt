package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.data.model.SpectrumData
import com.wcg.app.specapp.ui.components.SpectrumChart

@Composable
fun DataScreen(vm: DataViewModel, onLoadToMain: (SpectrumData) -> Unit) {
    val spectra by vm.spectra.collectAsState()
    val selected by vm.selectedSpectrum.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<SpectrumData?>(null) }

    // delete confirmation dialog
    showDeleteDialog?.let { sp ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${sp.name}」吗？") },
            confirmButton = {
                TextButton(onClick = { vm.delete(sp); showDeleteDialog = null }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── left: list ──────────────────────────────────────────────────────────
        Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已保存光谱 (${spectra.size})", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = vm::loadAll) { Text("刷新") }
            }
            HorizontalDivider()

            if (spectra.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无已保存的光谱", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(spectra, key = { it.id }) { sp ->
                        SpectrumListItem(
                            spectrum = sp,
                            isSelected = selected?.id == sp.id,
                            onClick = { vm.selectSpectrum(sp) },
                            onDelete = { showDeleteDialog = sp },
                            onExport = { vm.exportToCsv(sp) },
                            onLoad = { onLoadToMain(sp) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        VerticalDivider()

        // ── right: preview + actions ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            if (selected != null) {
                val sp = selected!!
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(sp.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "时间: ${sp.timestampFormatted}  |  " +
                            "点数: ${sp.size}  |  " +
                            "范围: ${"%.0f".format(sp.minWavelength)}–${"%.0f".format(sp.maxWavelength)} nm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.exportToCsv(sp) }) {
                            Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出 CSV")
                        }
                        Button(onClick = { onLoadToMain(sp) }) {
                            Icon(Icons.Filled.BarChart, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("加载到主界面")
                        }
                    }
                }

                // spectrum preview
                SpectrumChart(
                    spectra = listOf(sp),
                    showGrid = true,
                    showPeaks = true,
                    autoscaleY = true,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                // config details
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        LabelValue("积分时间", "${sp.config.integrationTimeMs} ms")
                        LabelValue("平均次数", "${sp.config.averaging}")
                        LabelValue("平滑", if (sp.config.smoothingPoints > 0) "${sp.config.smoothingPoints}点" else "无")
                        LabelValue("最大强度", "${"%.0f".format(sp.maxIntensity)}")
                        LabelValue("峰值波长", sp.findPeaks(sp.maxIntensity * 0.5f)
                            .maxByOrNull { it.second }?.let { "${"%.1f".format(it.first)} nm" } ?: "—")
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("选择左侧光谱以预览", color = MaterialTheme.colorScheme.outline)
                }
            }

            // status message
            if (statusMessage.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SpectrumListItem(
    spectrum: SpectrumData,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onLoad: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(bg).clickable(onClick = onClick).padding(8.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(spectrum.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                spectrum.timestampFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onLoad, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.BarChart, "加载", Modifier.size(16.dp))
        }
        IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Download, "导出", Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, "删除", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
