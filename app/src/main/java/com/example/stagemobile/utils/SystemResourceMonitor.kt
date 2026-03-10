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

    /**
     * Gets the current Native Heap memory allocated in MegaBytes (MB).
     * This provides real-time feedback for SF2 loading/unloading.
     */
    fun getMemoryUsageMb(): Int {
        val nativeHeapBytes = android.os.Debug.getNativeHeapAllocatedSize()
        return (nativeHeapBytes / (1024 * 1024)).toInt()
    }

    /**
     * Calculates the approximate CPU usage percentage since the last call to this function.
     * This uses the elapsed CPU time of the process divided by the elapsed device realtime.
     * Needs to be called periodically (e.g., every 1s) to return an accurate delta.
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

        if (uptimeDelta <= 0) return 0f

        // Convert delta to percentage. (CPU time used / real time passed) * 100
        val percent = (cpuTimeDelta.toFloat() / uptimeDelta.toFloat()) * 100f
        
        // Coerce max to 100% just in case of anomaly spikes and round to 1 decimal place
        val coercedPercent = percent.coerceIn(0f, 100f)
        return kotlin.math.round(coercedPercent * 10f) / 10f
    }
}
