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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted, potentially re-init audio devices here if needed
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
        // Legacy storage if needed
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        // Audio
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Allow content to extend into the cutout area (notch) for edge-to-edge landscape
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            // Hide system bars (Immersive Mode)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Hide both Status Bar and Navigation Bar
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            checkAndRequestPermissions()
        } catch (e: Exception) {
        }


        setContent {
            StageMobileTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF131313) // Exact same as splash_bg
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    // Eager initialization: ViewModel starts loading immediately during Splash
                    val viewModel: MixerViewModel = viewModel()
                    val isReady by viewModel.isReady.collectAsState()
                    var showSplashScreen by remember { mutableStateOf(true) }
                    var currentScreen by remember { mutableStateOf("mixer") }

                    LaunchedEffect(Unit) {
                        viewModel.initMidi(context)
                    }

                    LaunchedEffect(isReady) {
                        if (isReady) {
                            delay(2000) // Assure branding visibility (total ~2-3s)
                            showSplashScreen = false
                        }
                    }

                    if (showSplashScreen) {
                        SplashScreen()
                    } else {
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
                                SystemGlobalSettings(
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