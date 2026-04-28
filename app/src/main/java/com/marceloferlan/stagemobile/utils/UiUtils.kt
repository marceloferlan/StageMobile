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
        // Usa APENAS smallestScreenWidthDp (menor dimensão física da tela).
        // Isso garante que phones em landscape (ex: S24 Ultra, smallestWidth ≈ 411dp)
        // NÃO sejam detectados como tablet. Apenas dispositivos com largura mínima
        // >= 600dp são tablets (padrão Android sw600dp qualifier).
        // S24 Ultra: ~452dp → phone. Tab S9 FE: ~1067dp → tablet.
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }

    /**
     * Versão Composable para detecção reativa de Tablet.
     * Usa smallestScreenWidthDp para distinguir phone de tablet
     * independente da orientação (landscape/portrait).
     */
    @Composable
    fun rememberIsTablet(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.smallestScreenWidthDp >= 600
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
