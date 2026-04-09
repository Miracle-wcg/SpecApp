package com.wcg.app.specapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.wcg.app.specapp.quantitative.ui.QuantitativeScreen
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun App() {
    val viewModel = remember { SpectrometerViewModel() }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
            TopNavBar(viewModel)
            HorizontalDivider(color = BorderDark, thickness = 1.dp)

            Row(modifier = Modifier.weight(1f)) {
                Sidebar(
                    viewModel = viewModel,
                    modifier = Modifier.width(260.dp).fillMaxHeight()
                )
                VerticalDivider(color = BorderDark, thickness = 1.dp)

                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                    when (viewModel.currentScreen) {
                        AppScreen.Analysis -> AnalysisScreen(viewModel)
                        AppScreen.Setup -> SetupScreen(viewModel)
                        AppScreen.AutoScan -> AutoScanScreen(viewModel)
                        AppScreen.Quantitative -> QuantitativeScreen(viewModel)
                        AppScreen.Settings -> SettingsScreen(viewModel)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun TopNavBar(viewModel: SpectrometerViewModel) {
    val lang = viewModel.appLanguage

    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("SpectraX", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AccentCyan)
        Spacer(modifier = Modifier.width(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                if (lang == AppLanguage.Chinese) "仪器" else "Instruments",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.drawUnderline()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = {
                val chooser = JFileChooser(viewModel.config.savePath).apply {
                    dialogTitle = if (lang == AppLanguage.Chinese) "选择历史光谱数据文件" else "Choose Spectrum File"
                    fileFilter = FileNameExtensionFilter(if (lang == AppLanguage.Chinese) "光谱文件 (*.spc, *.txt)" else "Spectral Files (*.spc, *.txt)", "spc", "txt")
                    isAcceptAllFileFilterUsed = false
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    viewModel.importDataFile(chooser.selectedFile.absolutePath)
                    if (viewModel.currentScreen != AppScreen.Analysis) {
                        viewModel.currentScreen = AppScreen.Analysis
                    }
                }
            },
            modifier = Modifier.height(36.dp), shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite), border = BorderStroke(1.dp, BorderDark)
        ) {
            Text(if (lang == AppLanguage.Chinese) "📂 打开数据" else "📂 Open Data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(24.dp))

        Box(
            modifier = Modifier.size(32.dp).background(PanelBg, RoundedCornerShape(50))
                .border(1.dp, BorderDark, RoundedCornerShape(50)), contentAlignment = Alignment.Center
        ) {
            Text("👤", fontSize = 14.sp)
        }
    }
}

fun Modifier.drawUnderline(): Modifier = this.drawBehind {
    val strokeWidth = 2.dp.toPx()
    val y = size.height + 4.dp.toPx()
    drawLine(AccentCyan, Offset(0f, y), Offset(size.width, y), strokeWidth)
}

@Composable
fun Sidebar(viewModel: SpectrometerViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.appLanguage
    val currentScreen = viewModel.currentScreen

    Column(modifier = modifier.background(BgDark).padding(20.dp)) {
        Text(if (lang == AppLanguage.Chinese) "系统 ALPHA" else "SYSTEM ALPHA", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (lang == AppLanguage.Chinese) "仪器在线" else "Instrument Online", color = TextMuted, fontSize = 12.sp)

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
                    .clickable { viewModel.currentScreen = screen }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(screen.icon, color = if (isSelected) AccentCyan else TextMuted, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    screen.title(lang),
                    color = if (isSelected) TextWhite else TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}