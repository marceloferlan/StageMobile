package com.marceloferlan.stagemobile

import android.app.Application
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class StageMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.e("FirebaseDebug", ">>> INICIANDO StageMobileApp <<<")
        try {
            FirebaseApp.initializeApp(this)
            Log.i("FirebaseDebug", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("FirebaseDebug", "Failed to initialize Firebase: ${e.message}")
        }
    }
}
