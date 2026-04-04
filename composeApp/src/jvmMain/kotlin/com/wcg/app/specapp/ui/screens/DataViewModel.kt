package com.wcg.app.specapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wcg.app.specapp.data.SpectrumRepository
import com.wcg.app.specapp.data.model.SpectrumData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DataViewModel(
    private val repository: SpectrumRepository
) : ViewModel() {

    private val _spectra = MutableStateFlow<List<SpectrumData>>(emptyList())
    val spectra: StateFlow<List<SpectrumData>> = _spectra

    private val _selectedSpectrum = MutableStateFlow<SpectrumData?>(null)
    val selectedSpectrum: StateFlow<SpectrumData?> = _selectedSpectrum

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _spectra.value = repository.loadAll()
        }
    }

    fun selectSpectrum(sp: SpectrumData) { _selectedSpectrum.value = sp }

    fun delete(sp: SpectrumData) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(sp.id)
            _spectra.value = _spectra.value.filter { it.id != sp.id }
            if (_selectedSpectrum.value?.id == sp.id) _selectedSpectrum.value = null
            _statusMessage.value = "已删除: ${sp.name}"
        }
    }

    fun exportToCsv(sp: SpectrumData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = repository.exportToCsv(sp)
                _statusMessage.value = "已导出: ${file.absolutePath}"
            } catch (e: Exception) {
                _statusMessage.value = "导出失败: ${e.message}"
            }
        }
    }

    /** Load a selected spectrum into the spectrum screen (returns it for caller to handle) */
    fun getSelected(): SpectrumData? = _selectedSpectrum.value
}
