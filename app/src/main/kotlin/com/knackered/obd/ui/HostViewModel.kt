package com.knackered.obd.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppScreen {
    TERMINAL,
    SETTINGS,
}

class HostViewModel : ViewModel() {
    private val _screen = MutableStateFlow(AppScreen.TERMINAL)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    fun openSettings() {
        _screen.value = AppScreen.SETTINGS
    }

    fun closeSettings() {
        _screen.value = AppScreen.TERMINAL
    }
}
