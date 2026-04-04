package com.wcg.app.specapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wcg.app.specapp.data.DeviceManager
import com.wcg.app.specapp.data.SettingsManager
import com.wcg.app.specapp.data.SpectrumRepository
import com.wcg.app.specapp.ui.screens.*

private enum class AppScreen(val label: String) {
    SPECTRUM("光谱"),
    DEVICE("设备"),
    DATA("数据"),
    SETTINGS("设置")
}

@Composable
@Preview
fun App() {
    // shared singletons
    val settings = remember { SettingsManager.load() }
    val deviceManager = remember { DeviceManager() }
    val repository = remember { SpectrumRepository(settings) }

    // view models
    val spectrumVm = remember { SpectrumViewModel(deviceManager, repository, settings) }
    val deviceVm = remember { DeviceViewModel(deviceManager, settings) }
    val dataVm = remember { DataViewModel(repository) }
    val settingsVm = remember { SettingsViewModel() }

    var selectedScreen by remember { mutableStateOf(AppScreen.SPECTRUM) }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── navigation rail ─────────────────────────────────────────────
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(8.dp))
                    AppScreen.entries.forEach { screen ->
                        NavigationRailItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        AppScreen.SPECTRUM -> Icons.Filled.BarChart
                                        AppScreen.DEVICE   -> Icons.Filled.Usb
                                        AppScreen.DATA     -> Icons.Filled.FolderOpen
                                        AppScreen.SETTINGS -> Icons.Filled.Settings
                                    },
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }

                VerticalDivider()

                // ── content ─────────────────────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedScreen) {
                        AppScreen.SPECTRUM -> SpectrumScreen(spectrumVm)
                        AppScreen.DEVICE   -> DeviceScreen(deviceVm)
                        AppScreen.DATA     -> DataScreen(dataVm) { sp ->
                            spectrumVm.addOverlay(sp)
                            selectedScreen = AppScreen.SPECTRUM
                        }
                        AppScreen.SETTINGS -> SettingsScreen(settingsVm)
                    }
                }
            }
        }
    }
}
