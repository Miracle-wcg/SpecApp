package com.wcg.app.specapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("仪器设置 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("/ Instrument Setup", color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text("配置光谱仪网络参数、系统标识及高级运行模式。所有更改将在应用后立即生效。", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { }, colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite), border = BorderStroke(1.dp, BorderDark)) {
                    Text("取消更改", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)) {
                    Text("保存配置", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // 左列
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupCard(title = "品  网络配置", subtitle = "NETWORK CONFIGURATION", actionText = "Test Connection") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DarkTextField("DEVICE IP ADDRESS", "192.168.1.105", Modifier.weight(1f))
                        DarkTextField("PORT", "8080", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DarkTextField("SUBNET MASK", "255.255.255.0", Modifier.weight(1f))
                        DarkTextField("GATEWAY", "192.168.1.1", Modifier.weight(1f))
                    }
                }
                SetupCard(title = "☷  高级参数", subtitle = "ADVANCED") {
                    SetupRowItem("Legacy Status", "针对旧版协议的数据兼容性偏移值 (0-255)") {
                        Box(modifier = Modifier.background(BgDark, RoundedCornerShape(4.dp)).border(1.dp, BorderDark, RoundedCornerShape(4.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("42", color = TextWhite)
                        }
                    }
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))
                    SetupRowItem("Inst. DSP Error Tracking", "实时监测数字信号处理器在扫频期间的校准误差") {
                        Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = TextWhite, checkedTrackColor = AccentCyan))
                    }
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))
                    SetupRowItem("Sweep Mode (扫频模式)", "定义光栅移动频率与传感器积分周期的耦合方式") {
                        DarkTextField("", "Standard Continuous", Modifier.width(200.dp))
                    }
                }
            }

            // 右列
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupCard(title = "◎  仪器身份", subtitle = "IDENTITY") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Model Number", color = TextMuted, fontSize = 12.sp); Text("SP-2000X Precision", color = TextWhite, fontSize = 13.sp) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Serial Number", color = TextMuted, fontSize = 12.sp); Text("#A99-4452-X", color = TextWhite, fontSize = 13.sp) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Firmware Version", color = TextMuted, fontSize = 12.sp); Text("v2.4.12-revB", color = TextWhite, fontSize = 13.sp) }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648), contentColor = TextWhite)) { Text("CHECK FOR UPDATES", fontWeight = FontWeight.Bold) }
                }
                SetupCard(title = "📁  存储偏好", subtitle = "FILE & STORAGE") {
                    Text("DEFAULT EXPORT PATH (默认导出路径)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        DarkTextField("", "C:/Users/Analyst/Documents/Spectra/", Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {}, shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3648)), modifier = Modifier.height(48.dp)) { Text("📁") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("DEFAULT DATA FORMAT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormatBox(".SPC", "High density binary format...", true, Modifier.weight(1f))
                        FormatBox(".TXT", "Human readable plain text...", false, Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🔧 系统维护 MAINTENANCE", color = TextWhite, fontWeight = FontWeight.Bold)
                Text("定期执行维护任务以确保光学路径精度。涉及硬件重启，请确认设备空闲。", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {}, border = BorderStroke(1.dp, BorderDark), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)) { Text("Recalibrate Instrument") }
                OutlinedButton(onClick = {}, border = BorderStroke(1.dp, BorderDark), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)) { Text("⬇ Download System Logs") }
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF451A1F), contentColor = DangerRed)) { Text("⟲ Factory Reset") }
            }
        }
    }
}