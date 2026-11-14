package com.qiongge.callassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
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
            AssistantTheme {
                AssistantApp(viewModel = viewModel)
            }
        }
    }
}
