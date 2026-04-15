package com.marceloferlan.stagemobile.utils

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
        // Reduzido para 500 + Inclusão do screenWidth (largura absoluta) >= 600
        // Como o app roda unicamente em paisagem, isso força a UI de tablet e o APM HUD a aparecerem.
        return context.resources.configuration.screenWidthDp >= 600 || context.resources.configuration.smallestScreenWidthDp >= 500
    }

    /**
     * Versão Composable para detecção reativa de Tablet.
     * Usa smallestScreenWidthDp para garantir que celulares em modo paisagem 
     * não sejam detectados erroneamente como tablets.
     */
    @Composable
    fun rememberIsTablet(): Boolean {
        val configuration = LocalConfiguration.current
        // Reduzido para 500 + Inclusão do screenWidth (largura absoluta) >= 600
        return configuration.screenWidthDp >= 600 || configuration.smallestScreenWidthDp >= 500
    }

    /**
     * Retorna a largura e altura físicas absolutas da tela em Dp.
     * Útil para modais imersivos que precisam calcular 90% da totalidade física
     * da tela, ignorando barras de status, notches ou margens da janela.
     */
    @Composable
    fun getPhysicalScreenSize(): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
        val context = LocalContext.current
        val displayMetrics = android.util.DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val density = context.resources.displayMetrics.density
        return Pair(
            androidx.compose.ui.unit.Dp(displayMetrics.widthPixels / density),
            androidx.compose.ui.unit.Dp(displayMetrics.heightPixels / density)
        )
    }
}
