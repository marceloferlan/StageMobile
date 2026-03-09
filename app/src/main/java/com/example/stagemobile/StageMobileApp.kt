package com.example.stagemobile

import android.app.Application
import android.util.Log

class StageMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.e("StageMobileApp", "=== APP PROCESS STARTED (Application.onCreate) ===")
    }
}
