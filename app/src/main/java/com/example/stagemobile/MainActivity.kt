package com.example.stagemobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stagemobile.viewmodel.MixerViewModel
import com.example.stagemobile.ui.screens.SettingsScreen
import com.example.stagemobile.ui.screens.MixerScreen
import com.example.stagemobile.ui.screens.SetsScreen
import com.example.stagemobile.ui.screens.DrumpadsScreen
import com.example.stagemobile.ui.screens.ContinuousPadsScreen
import com.example.stagemobile.ui.screens.DownloadsScreen
import com.example.stagemobile.ui.theme.StageMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system bars (Immersive Mode)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        setContent {
            StageMobileTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1A1A)
                ) {
                    val viewModel: MixerViewModel = viewModel()
                    var currentScreen by remember { mutableStateOf("mixer") }

                    if (currentScreen == "mixer") {
                        MixerScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { currentScreen = "settings" },
                            onNavigateToSets = { currentScreen = "sets" },
                            onNavigateToDrumpads = { currentScreen = "drumpads" },
                            onNavigateToContinuousPads = { currentScreen = "continuous_pads" },
                            onNavigateToDownloads = { currentScreen = "downloads" }
                        )
                    } else if (currentScreen == "settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = "mixer" }
                        )
                    } else if (currentScreen == "sets") {
                        SetsScreen(onNavigateBack = { currentScreen = "mixer" })
                    } else if (currentScreen == "drumpads") {
                        DrumpadsScreen(onNavigateBack = { currentScreen = "mixer" })
                    } else if (currentScreen == "continuous_pads") {
                        ContinuousPadsScreen(onNavigateBack = { currentScreen = "mixer" })
                    } else if (currentScreen == "downloads") {
                        DownloadsScreen(onNavigateBack = { currentScreen = "mixer" })
                    }
                }
            }
        }
    }
}