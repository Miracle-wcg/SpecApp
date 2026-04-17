package com.wcg.app.specapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class UiController {
    var appLanguage by mutableStateOf(AppLanguage.Chinese)
    var currentScreen by mutableStateOf(AppScreen.Analysis)
    var uiMessage by mutableStateOf<String?>(null)

    var showDialog by mutableStateOf(false)
    var dialogTitle by mutableStateOf("")
    var dialogMessage by mutableStateOf("")

    fun showMsg(msg: String) { uiMessage = msg }
    fun clearMsg() { uiMessage = null }
    fun popup(title: String, msg: String) {
        dialogTitle = title
        dialogMessage = msg
        showDialog = true
    }
}