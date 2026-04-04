package com.wcg.app.specapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val viewModel = remember { SpectrometerViewModel() }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
            // 顶部导航栏
            TopNavBar()
            HorizontalDivider(color = BorderDark, thickness = 1.dp)

            Row(modifier = Modifier.weight(1f)) {
                // 左侧导航菜单
                Sidebar(
                    currentScreen = viewModel.currentScreen,
                    onScreenSelected = { viewModel.currentScreen = it },
                    modifier = Modifier.width(240.dp).fillMaxHeight()
                )
                VerticalDivider(color = BorderDark, thickness = 1.dp)

                // 右侧主工作区路由分发
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                    when (viewModel.currentScreen) {
                        AppScreen.Analysis -> AnalysisScreen(viewModel)
                        AppScreen.Setup -> SetupScreen()
                        AppScreen.Settings -> SettingsScreen()
                        AppScreen.History -> Text("History Screen (WIP)", color = TextWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun TopNavBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("SpectraPro Precision", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AccentCyan)
        Spacer(modifier = Modifier.width(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Connectivity", color = TextMuted, fontSize = 14.sp)
            Text(
                "Instruments",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.drawUnderline()
            )
            Text("Diagnostics", color = TextMuted, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End) {
            Text("DEVICE IP: 192.168.1.102", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(modifier = Modifier.size(6.dp).background(AccentCyan, RoundedCornerShape(50)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stable (Port: 5025)", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(24.dp))
        Text("🔔", color = TextMuted)
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier.size(32.dp).background(PanelBg, RoundedCornerShape(50))
                .border(1.dp, BorderDark, RoundedCornerShape(50)), contentAlignment = Alignment.Center
        ) {
            Text("👤", fontSize = 14.sp)
        }
    }
}

// Navbar 的下划线特效
fun Modifier.drawUnderline(): Modifier = this.drawBehind {
    val strokeWidth = 2.dp.toPx()
    val y = size.height + 4.dp.toPx()
    drawLine(AccentCyan, Offset(0f, y), Offset(size.width, y), strokeWidth)
}

@Composable
fun Sidebar(currentScreen: AppScreen, onScreenSelected: (AppScreen) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(BgDark).padding(20.dp)) {
        Text("SYSTEM ALPHA", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Instrument Online", color = TextMuted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(32.dp))

        AppScreen.values().forEach { screen ->
            val isSelected = currentScreen == screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color(0xFF1E2D4A) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) AccentCyan.copy(alpha = 0.3f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onScreenSelected(screen) }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(screen.icon, color = if (isSelected) AccentCyan else TextMuted, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    screen.title,
                    color = if (isSelected) TextWhite else TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
        Text("?  Support", color = TextWhite, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        Text("ℹ  Help", color = TextWhite, fontSize = 14.sp)
    }
}