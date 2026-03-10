package com.example.stagemobile.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock

class SystemResourceMonitor(private val context: Context) {
    
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    
    // CPU Tracking state
    private var lastCpuTime = -1L
    private var lastUptime = -1L

    // Memory Tracking state (Hybrid PSS + Native Delta)
    private var lastPssMb = 0
    private var lastNativeHeapMb = 0L
    private var lastPssUpdateTime = 0L

    /**
     * Gets the Hybrid memory usage in MB. 
     * Combines PSS (Total accuracy) with Native Heap Deltas (Speed).
     * This ensures the RAM monitor reacts instantly to SF2 load/unload,
     * but still reflects the total footprint of the app.
     */
    fun getMemoryUsageMb(): Int {
        val now = SystemClock.elapsedRealtime()
        val currentNativeHeapMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)

        // Update the "Anchor PSS" every 30 seconds to avoid CPU overhead
        if (now - lastPssUpdateTime > 30000L || lastPssMb == 0) {
            val memInfo = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memInfo)
            lastPssMb = memInfo.totalPss / 1024
            lastNativeHeapMb = currentNativeHeapMb
            lastPssUpdateTime = now
        }

        // Calculate: Current PSS approx = Last PSS + (Change in Native Heap)
        val deltaNative = (currentNativeHeapMb - lastNativeHeapMb).toInt()
        return (lastPssMb + deltaNative).coerceAtLeast(1)
    }

    /**
     * Calculates the approximate CPU usage percentage since the last call to this function.
     */
    fun getCpuUsagePercent(): Float {
        val currentCpuTime = Process.getElapsedCpuTime() // Total CPU time the process used (ms)
        val currentUptime = SystemClock.elapsedRealtime() // Total wall-clock time passed (ms)

        if (lastCpuTime == -1L || lastUptime == -1L) {
            lastCpuTime = currentCpuTime
            lastUptime = currentUptime
            return 0f
        }

        val cpuTimeDelta = currentCpuTime - lastCpuTime
        val uptimeDelta = currentUptime - lastUptime

        lastCpuTime = currentCpuTime
        lastUptime = currentUptime

        if (uptimeDelta <= 0L) return 0f

        // Convert delta to percentage. (CPU time used / real time passed) * 100
        val percent = (cpuTimeDelta.toFloat() / uptimeDelta.toFloat()) * 100f
        
        // Coerce max to 100% just in case of anomaly spikes and round to 1 decimal place
        val coercedPercent = percent.coerceIn(0f, 100f)
        return kotlin.math.round(coercedPercent * 10f) / 10f
    }
}
