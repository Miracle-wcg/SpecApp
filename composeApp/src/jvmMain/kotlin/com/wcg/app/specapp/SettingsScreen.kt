package com.wcg.app.specapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    var password by remember { mutableStateOf("••••••••") }
    var sessionTimeout by remember { mutableStateOf("30 Minutes") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("系统设置 ", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("SETTINGS ", color = TextMuted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
            Text("SPECTRAL CORE CONTROL CENTER", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp, start = 8.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // 左列
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupCard(title = "", subtitle = "") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(80.dp).background(Color(0xFF86B3D1), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("👨‍💼", fontSize = 40.sp) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Operator", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("ACCOUNT UID: SPC-8842-X", color = TextMuted, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Badge("AUTHORIZED USER", Color(0xFF1E3A8A))
                                Badge("LEVEL 4 CLEARANCE", WarningOrange.copy(alpha = 0.2f), WarningOrange)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)) { Text("EDIT PROFILE", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            OutlinedButton(onClick = {}, border = BorderStroke(1.dp, BorderDark), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)) { Text("SWITCH USER", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }
                }
                SetupCard(title = "🔔  通知管理", subtitle = "NOTIFICATIONS") {
                    SetupRowItem("Scan Complete 扫描完成", "ALERT ON SPECTRUM FINISH") { Switch(checked = true, onCheckedChange = {}) }
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))
                    SetupRowItem("Error Warnings 错误警报", "CRITICAL HARDWARE ISSUES") { Switch(checked = true, onCheckedChange = {}) }
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))
                    SetupRowItem("System Updates 系统更新", "PATCH NOTIFICATIONS") { Switch(checked = false, onCheckedChange = {}) }
                }
            }

            // 中列
            Column(modifier = Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupCard(title = "🛡  安全与隐私", subtitle = "SECURITY", modifier = Modifier.weight(1f)) {
                    DarkTextField("UPDATE PASSWORD 密码", password, { password = it }, Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    DarkTextField("SESSION TIMEOUT (MIN)", sessionTimeout, { sessionTimeout = it }, Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = true, onCheckedChange = {}, colors = CheckboxDefaults.colors(checkedColor = AccentCyan))
                        Text("Data Encryption Enabled", color = TextWhite, fontSize = 12.sp)
                    }
                }
            }

            // 右列
            Column(modifier = Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SetupCard(title = "🖥  显示设置", subtitle = "DISPLAY") {
                    Text("INTERFACE LANGUAGE 语言", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, BorderDark, RoundedCornerShape(4.dp))) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF2B3648)), contentAlignment = Alignment.Center) { Text("ENGLISH", color = TextWhite, fontSize = 12.sp) }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("简体中文", color = TextMuted, fontSize = 12.sp) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    SetupRowItem("Theme Mode 主题", "") {
                        Box(modifier = Modifier.background(BgDark, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("🌙 ☀️", fontSize = 14.sp) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("UI DENSITY 密度", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Slider(value = 0.5f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("COMPACT", color = TextMuted, fontSize = 10.sp); Text("STANDARD", color = TextMuted, fontSize = 10.sp); Text("RELAXED", color = TextMuted, fontSize = 10.sp)
                    }
                }
                SetupCard(title = "ℹ  系统信息", subtitle = "SYSTEM", modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("CORE VERSION", color = TextMuted, fontSize = 12.sp); Text("v2.4.0-STABLE", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("LICENSE INFO", color = TextMuted, fontSize = 12.sp); Text("ENTERPRISE-882", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("HARDWARE LINK", color = TextMuted, fontSize = 12.sp); Text("ACTIVE", color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WarningOrange.copy(alpha=0.1f), contentColor = WarningOrange)) { Text("⟲ CHECK FOR UPDATES", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}