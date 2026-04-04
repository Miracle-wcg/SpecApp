package com.wcg.app.specapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.data.DeviceManager
import com.wcg.app.specapp.data.model.DeviceInfo

@Composable
fun DeviceScreen(vm: DeviceViewModel) {
    val ports by vm.ports.collectAsState()
    val selectedPort by vm.selectedPort.collectAsState()
    val connectionState by vm.connectionState.collectAsState()
    val log by vm.log.collectAsState()
    val baudRate by vm.baudRate.collectAsState()

    val isConnected = connectionState == DeviceManager.ConnectionState.CONNECTED
    val isConnecting = connectionState == DeviceManager.ConnectionState.CONNECTING

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── left: port list ─────────────────────────────────────────────────────
        Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("可用端口", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = vm::refreshPorts) { Text("刷新") }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(ports) { port ->
                    PortItem(
                        port = port,
                        isSelected = selectedPort?.portName == port.portName,
                        onClick = { vm.selectPort(port) }
                    )
                }
                if (ports.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("未找到串口设备", color = MaterialTheme.colorScheme.outline) }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Baud rate
            var baudText by remember(baudRate) { mutableStateOf(baudRate.toString()) }
            OutlinedTextField(
                value = baudText,
                onValueChange = {
                    baudText = it
                    it.toIntOrNull()?.let { v -> vm.setBaudRate(v) }
                },
                label = { Text("波特率", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // connect / disconnect
            if (isConnected) {
                Button(
                    onClick = vm::disconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("断开连接") }
            } else {
                Button(
                    onClick = vm::connect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedPort != null && !isConnecting
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isConnecting) "连接中..." else "连接")
                }
            }
        }

        // ── right: info + log ───────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // connection status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val (dotColor, stateText) = when (connectionState) {
                        DeviceManager.ConnectionState.CONNECTED   -> Color(0xFF4CAF50) to "已连接"
                        DeviceManager.ConnectionState.CONNECTING  -> Color(0xFFFFC107) to "连接中"
                        DeviceManager.ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) to "未连接"
                    }
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Column {
                        Text(stateText, style = MaterialTheme.typography.titleMedium)
                        selectedPort?.let {
                            Text(it.displayName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // connection log
            Text("连接日志", style = MaterialTheme.typography.titleSmall)
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1E1E1E)
            ) {
                val logState = rememberLazyListState()
                LaunchedEffect(log.size) {
                    if (log.isNotEmpty()) logState.animateScrollToItem(log.size - 1)
                }
                LazyColumn(
                    state = logState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    reverseLayout = false
                ) {
                    items(log) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = Color(0xFF80FF80), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortItem(port: DeviceInfo, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (port.portName == "SIMULATOR") Icons.Filled.Science else Icons.Filled.Usb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(port.portName, style = MaterialTheme.typography.bodyMedium)
            if (port.description.isNotBlank()) {
                Text(port.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
