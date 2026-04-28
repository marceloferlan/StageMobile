package com.marceloferlan.stagemobile.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marceloferlan.stagemobile.domain.model.AudioStats
import com.marceloferlan.stagemobile.viewmodel.MixerViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun APMHudDialog(
    viewModel: MixerViewModel,
    onDismiss: () -> Unit
) {
    val stats by viewModel.audioStats.collectAsState()
    val cpu by viewModel.cpuUsagePercent.collectAsState()
    val context = LocalContext.current

    // History to export
    val statsHistory = remember { mutableStateListOf<String>() }
    
    LaunchedEffect(stats) {
        stats?.let {
            val ts = System.currentTimeMillis()
            val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ts))
            val line = "$format,$cpu,${it.avgCallbackUs},${it.maxCallbackUs},${it.underruns},${it.mutexMisses},${it.clips},${it.activeVoices}," +
                    "${it.avgPhaseFluidUs},${it.maxPhaseFluidUs}," +
                    "${it.avgPhaseDspChanUs},${it.maxPhaseDspChanUs}," +
                    "${it.avgPhaseDspMasterUs},${it.maxPhaseDspMasterUs}," +
                    "${it.avgPhaseMixUs},${it.maxPhaseMixUs}"
            statsHistory.add(line)
            if (statsHistory.size > 100) statsHistory.removeFirst()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APM HUD (Performance)", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Divider(color = Color(0xFF333333))

                // Stats Grid
                if (stats != null) {
                    val s = stats!!
                    StatRow("Underruns (XRuns)", "${s.underruns}", if (s.underruns > 0) Color.Red else Color.Green)
                    StatRow("Mutex Misses", "${s.mutexMisses}", if (s.mutexMisses > 0) Color(0xFFFFA000) else Color.White)
                    StatRow("Clipping", "${s.clips}", if (s.clips > 0) Color.Red else Color.White)
                    StatRow("Avg Callback", String.format(Locale.US, "%.1f µs", s.avgCallbackUs), Color.White)
                    StatRow("Max Callback", String.format(Locale.US, "%.1f µs", s.maxCallbackUs), if (s.maxCallbackUs > 3000) Color(0xFFFFA000) else Color.Green)
                    StatRow("Active Voices", "${s.activeVoices}", Color(0xFF64B5F6))
                    StatRow("CPU Load Delta", "$cpu %", Color.White)

                    Spacer(modifier = Modifier.height(4.dp))
                    Divider(color = Color(0xFF333333))
                    Text(
                        "Breakdown por Fase (avg / max µs)",
                        color = Color(0xFFBDBDBD),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    PhaseRow("Fluid Synth",  s.avgPhaseFluidUs,     s.maxPhaseFluidUs)
                    PhaseRow("DSP Canais",   s.avgPhaseDspChanUs,   s.maxPhaseDspChanUs)
                    PhaseRow("DSP Master",   s.avgPhaseDspMasterUs, s.maxPhaseDspMasterUs)
                    PhaseRow("Mix/Out",      s.avgPhaseMixUs,       s.maxPhaseMixUs)
                } else {
                    Text("Aguardando métricas do motor C++...", color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Button(
                    onClick = {
                        val header = "Timestamp,CPU,AvgCb(us),MaxCb(us),Underruns,MutexMiss,Clips,Voices," +
                                "AvgFluid,MaxFluid,AvgDspCh,MaxDspCh,AvgMaster,MaxMaster,AvgMix,MaxMix\n"
                        val csvData = header + statsHistory.joinToString("\n")
                        
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("APM CSV Export", csvData)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Métricas copiadas pro Clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export CSV", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar CSV (Clipboard)")
                }

                Button(
                    onClick = {
                        viewModel.resetApmCounters()
                        statsHistory.clear()
                        Toast.makeText(context, "Contadores zerados!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zerar Contadores")
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 14.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun PhaseRow(label: String, avgUs: Float, maxUs: Float) {
    val maxColor = when {
        maxUs >= 2000f -> Color(0xFFEF5350)  // vermelho: acima do budget (2666µs)
        maxUs >= 1000f -> Color(0xFFFFA000)  // amarelo: suspeito
        else -> Color(0xFF81C784)            // verde
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Row {
            Text(
                String.format(Locale.US, "%.1f", avgUs),
                color = Color.White, fontSize = 13.sp
            )
            Text(" / ", color = Color.Gray, fontSize = 13.sp)
            Text(
                String.format(Locale.US, "%.1f", maxUs),
                color = maxColor, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
        }
    }
}
