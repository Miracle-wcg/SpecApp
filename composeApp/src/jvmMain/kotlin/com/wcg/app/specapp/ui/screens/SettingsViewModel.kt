package com.wcg.app.specapp.ui.screens

import androidx.lifecycle.ViewModel
import com.wcg.app.specapp.data.SettingsManager
import com.wcg.app.specapp.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel : ViewModel() {
    private val _settings = MutableStateFlow(SettingsManager.load())
    val settings: StateFlow<AppSettings> = _settings

    fun update(block: AppSettings.() -> AppSettings) {
        _settings.value = _settings.value.block()
        SettingsManager.save(_settings.value)
    }
}
