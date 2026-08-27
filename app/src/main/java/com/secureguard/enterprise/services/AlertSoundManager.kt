package com.secureguard.enterprise.services

import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarm-Töne pro Asset/Stufe. Nutzt [ToneGenerator] (keine Audio-Ressourcen
 * nötig); `laut` = durchdringender Dauer-Alarm, `leise` = kurzer Hinweis,
 * `aus` = stumm. Eigene Sounds können über Raw-Ressourcen ergänzt werden.
 */
@Singleton
class AlertSoundManager @Inject constructor() {

    private var activeTone: ToneGenerator? = null

    /** Handler für das Auto-Stop-Sicherheitsnetz von Dauer-Alarms. */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    fun play(soundName: String?, loop: Boolean = false) {
        stop()
        when (soundName?.lowercase()) {
            "laut", "alarm", "critical" -> startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, if (loop) -1 else 2000)
            "leise", "soft", "warning" -> startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            "aus", "off", "none", null, "" -> Unit
            else -> startTone(ToneGenerator.TONE_PROP_BEEP, 500)
        }
    }

    /** Spielt einen Alarm passend zur Schwere einer Meldung ab. */
    fun playForSeverity(severity: String?) {
        when (severity?.uppercase()) {
            "CRITICAL" -> play("laut", loop = true)
            "WARNING" -> play("leise")
            else -> play("leise")
        }
    }

    fun stop() {
        runCatching { activeTone?.stopTone() }
        activeTone?.release()
        activeTone = null
    }

    private fun startTone(tone: Int, durationMs: Int) {
        val generator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 80)
        } catch (e: Exception) {
            null
        } ?: return
        activeTone = generator
        runCatching { generator.startTone(tone, durationMs) }
        // Sicherheitsnetz (F-53): ein Loop-Alarm endet spätestens nach
        // [ALARM_AUTO_STOP_MS], auch ohne manuelles stop().
        if (durationMs < 0) {
            mainHandler.removeCallbacks(autoStop)
            mainHandler.postDelayed(autoStop, ALARM_AUTO_STOP_MS)
        }
    }

    companion object {
        /** Maximale Laufzeit eines Dauer-Alarms. */
        const val ALARM_AUTO_STOP_MS = 30_000L
    }
}
