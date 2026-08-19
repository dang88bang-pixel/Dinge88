package com.secureguard.enterprise

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class SecureGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // osmdroid verlangt einen User-Agent (Pflicht), sonst wirft er eine Exception.
        Configuration.getInstance().userAgentValue =
            "SecureGuard-Enterprise/${BuildConfig.VERSION_NAME}"
    }
}
