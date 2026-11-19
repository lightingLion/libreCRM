package com.qiongge.callassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiongge.callassistant.data.preferences.ThemeMode
import com.qiongge.callassistant.ui.AssistantApp
import com.qiongge.callassistant.ui.MainViewModel
import com.qiongge.callassistant.ui.theme.AssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AssistantApplication
        val viewModel = ViewModelProvider(
            this,
            app.appContainer.mainViewModelFactory,
        )[MainViewModel::class.java]

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val mode by remember { derivedStateOf { state.themeMode } }
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AssistantTheme(darkTheme = dark) {
                val bars = MaterialTheme.colorScheme.surfaceContainer.toArgb()
                LaunchedEffect(dark, bars) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(transparent, transparent) { dark },
                        navigationBarStyle = SystemBarStyle.auto(bars, bars) { dark },
                    )
                }
                AssistantApp(viewModel = viewModel)
            }
        }
    }
}
