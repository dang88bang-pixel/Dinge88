package com.secureguard.enterprise.services

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import com.secureguard.enterprise.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarm-Töne pro Asset/Stufe. Nutzt die Audio-Ressourcen
 * [R.raw.alarm_loud] (durchdringender Dauer-Alarm) und [R.raw.alarm_soft]
 * (kurzer Hinweis) über [MediaPlayer]; falls die Ressourcen nicht
 * ladbar sind, wird auf [ToneGenerator] zurückgefallen.
 */
@Singleton
class AlertSoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var media: MediaPlayer? = null
    private var activeTone: ToneGenerator? = null

    fun play(soundName: String?, loop: Boolean = false) {
        stop()
        when (soundName?.lowercase()) {
            "laut", "alarm", "critical" -> playRaw(R.raw.alarm_loud, loop)
            "leise", "soft", "warning" -> playRaw(R.raw.alarm_soft, false)
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
        media?.let { runCatching { it.release() } }
        media = null
        runCatching { activeTone?.stopTone() }
        activeTone?.release()
        activeTone = null
    }

    private fun playRaw(resId: Int, loop: Boolean) {
        try {
            media = MediaPlayer.create(context, resId)?.apply {
                isLooping = loop
                setOnCompletionListener {
                    if (!isLooping) {
                        runCatching { release() }
                    }
                }
                start()
            }
        } catch (e: Exception) {
            media = null
            // Fallback: Systemtöne
            if (resId == R.raw.alarm_loud) {
                startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, if (loop) -1 else 2000)
            } else {
                startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            }
        }
    }

    private fun startTone(tone: Int, durationMs: Int) {
        val generator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 80)
        } catch (e: Exception) {
            null
        } ?: return
        activeTone = generator
        runCatching { generator.startTone(tone, durationMs) }
    }
}
