package com.knackered.obd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knackered.obd.ui.AppScreen
import com.knackered.obd.ui.HostViewModel
import com.knackered.obd.ui.settings.SettingsScreen
import com.knackered.obd.ui.settings.SettingsViewModel
import com.knackered.obd.ui.terminal.TerminalScreen
import com.knackered.obd.ui.terminal.TerminalViewModel
import com.knackered.obd.ui.theme.KnackeredTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KnackeredApp
        setContent {
            KnackeredTheme {
                val host: HostViewModel = viewModel()
                val screen by host.screen.collectAsStateWithLifecycle()
                when (screen) {
                    AppScreen.TERMINAL -> {
                        val viewModel: TerminalViewModel = viewModel(factory = TerminalViewModel.factory(app.standIn))
                        TerminalScreen(viewModel, onOpenSettings = host::openSettings)
                    }
                    AppScreen.SETTINGS -> {
                        val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app.profiles))
                        SettingsScreen(viewModel, onBack = host::closeSettings)
                    }
                }
            }
        }
    }
}
