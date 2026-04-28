package com.marceloferlan.stagemobile.viewmodel

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.marceloferlan.stagemobile.data.FeedbackRepository
import com.marceloferlan.stagemobile.data.SettingsRepository
import com.marceloferlan.stagemobile.domain.model.FeedbackReport
import com.marceloferlan.stagemobile.domain.model.TelemetryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class FeedbackViewModel(
    private val repository: FeedbackRepository = FeedbackRepository(),
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _submitResult = MutableStateFlow<Result<Unit>?>(null)
    val submitResult: StateFlow<Result<Unit>?> = _submitResult

    fun clearResult() {
        _submitResult.value = null
    }

    fun sendFeedback(
        context: Context,
        type: String,
        featureTag: String,
        details: String,
        phone: String,
        connectedMidiDevices: List<String>
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            
            // Build Telemetry
            val isBug = type.contains("Bug", ignoreCase = true) || type.contains("Falha", ignoreCase = true)
            val logs = if (isBug) captureLogcat() else ""
            
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }

            val telemetry = TelemetryData(
                appVersion = versionName,
                androidVersion = Build.VERSION.RELEASE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                freeMemoryInMb = getFreeMemoryMb(),
                audioDriverMode = if (settingsRepository.audioDriverMode == 1) "Driver USB Melhorado" else "Driver Nativo",
                connectedMidiDevices = connectedMidiDevices,
                logcatSnippet = logs
            )

            val user = FirebaseAuth.getInstance().currentUser
            val report = FeedbackReport(
                userId = user?.uid ?: "anonymous",
                userName = user?.displayName ?: "No Name",
                userEmail = user?.email ?: "No Email",
                userPhone = phone,
                type = type,
                featureTag = featureTag,
                details = details,
                telemetry = telemetry
            )

            _submitResult.value = repository.submitFeedback(report)
            _isSubmitting.value = false
        }
    }

    private fun getFreeMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
    }

    private suspend fun captureLogcat(): String = withContext(Dispatchers.IO) {
        try {
            // Busca apenas os processos do próprio app, últimas 150 linhas
            val process = Runtime.getRuntime().exec("logcat -d -v time -t 150")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                log.append(line).append("\n")
            }
            log.toString()
        } catch (e: Exception) {
            "Erro ao capturar logs: ${e.message}"
        }
    }
}
