package com.example.stagemobile.utils

import android.app.Activity
import android.content.Context
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Utilitário global para detecção de características do dispositivo e interface.
 * Centraliza a inteligência de layout adaptativo do Stage Mobile usando WindowSizeClasses. 🎨📱
 */
object UiUtils {
    /**
     * Retorna true se o dispositivo for um Tablet (largura mínima >= 600dp).
     * Útil para contextos não-composables (como ViewModels).
     */
    fun isTablet(context: Context): Boolean {
        // Mantemos a lógica baseada em configuração para contextos fora do Compose
        // que não possuem acesso fácil à Activity ou ao WindowSizeClass reativo.
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }

    /**
     * Versão Composable para detecção reativa de Tablet.
     * Usa smallestScreenWidthDp para garantir que celulares em modo paisagem 
     * não sejam detectados erroneamente como tablets.
     */
    @Composable
    fun rememberIsTablet(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.smallestScreenWidthDp >= 600
    }
}
