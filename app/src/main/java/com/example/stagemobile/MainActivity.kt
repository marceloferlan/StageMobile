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
import com.example.stagemobile.ui.screens.*
import com.example.stagemobile.ui.theme.StageMobileTheme
import com.example.stagemobile.ui.components.SplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // We let the manifest theme (Theme.StageMobile.Splash) stay active
        // until the first Frame is drawn by Compose setContent.
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
                    color = Color(0xFF131313) // Exact same as splash_bg
                ) {
                    var showSplashScreen by remember { mutableStateOf(true) }
                    var currentScreen by remember { mutableStateOf("mixer") }

                    LaunchedEffect(Unit) {
                        delay(2500) // Show our branded splash for 2.5s
                        showSplashScreen = false
                    }

                    if (showSplashScreen) {
                        SplashScreen()
                    } else {
                        // Lazy initialization: ViewModel is only created AFTER splash ends
                        val viewModel: MixerViewModel = viewModel()
                        
                        when (currentScreen) {
                            "mixer" -> {
                                MixerScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { currentScreen = "settings" },
                                    onNavigateToSets = { currentScreen = "sets" },
                                    onNavigateToDrumpads = { currentScreen = "drumpads" },
                                    onNavigateToContinuousPads = { currentScreen = "continuous_pads" },
                                    onNavigateToDownloads = { currentScreen = "downloads" }
                                )
                            }
                            "settings" -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = "mixer" }
                                )
                            }
                            "sets" -> SetsScreen(onNavigateBack = { currentScreen = "mixer" })
                            "drumpads" -> DrumpadsScreen(onNavigateBack = { currentScreen = "mixer" })
                            "continuous_pads" -> ContinuousPadsScreen(onNavigateBack = { currentScreen = "mixer" })
                            "downloads" -> DownloadsScreen(onNavigateBack = { currentScreen = "mixer" })
                        }
                    }
                }
            }
        }
    }
}