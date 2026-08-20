package com.secureguard.enterprise.services

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NFC-Hardware-Integration (plattformseitig, kompatibel zu PN532-Lesern,
 * die als NFC-Adapter des Geräts erscheinen).
 *
 * Verarbeitet NDEF-Tags, die eine Asset-MAC tragen, und erzeugt daraus eine
 * [Detection] (Quelle [DetectionSource.NFC]). Aufruf aus der Activity:
 * `onNewIntent` → [NfcService.processTag].
 */
@Singleton
class NfcService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val adapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(context)
    }

    fun isAvailable(): Boolean = adapter != null

    /** Liest die Asset-ID (MAC) aus einem NDEF-Tag. */
    fun readTagId(intent: Intent): String? {
        val tag = getTag(intent) ?: return null
        val ndef = Ndef.get(tag) ?: return null
        return runCatching {
            ndef.connect()
            val message: NdefMessage = ndef.ndefMessage ?: return null
            ndef.close()
            val record = message.records.firstOrNull() ?: return null
            // NDEF-Text-Record: Status-Byte + Sprache + Text
            val payload = record.payload
            val text = if (payload.isNotEmpty() && payload[0] == 0x02.toByte()) {
                String(payload, 3, payload.size - 3, Charsets.UTF_8)
            } else {
                String(payload, Charsets.UTF_8)
            }
            text.trim().ifBlank { null }
        }.getOrNull()
    }

    /** Erzeugt aus einer gelesenen Tag-ID eine Detektion (sofern MAC-ähnlich). */
    fun processTag(intent: Intent): Detection? {
        val id = readTagId(intent) ?: return null
        val mac = id.replace("-", ":").uppercase()
        if (!mac.matches(MAC_PATTERN)) return null
        val detection = Detection(
            assetMac = mac,
            sourceType = DetectionSource.NFC,
            nodeId = "nfc-tag",
            rssi = 0,
            message = "NFC-Tag gelesen",
            timestamp = Date()
        )
        emit(detection)
        return detection
    }

    @Suppress("DEPRECATION")
    private fun getTag(intent: Intent): Tag? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    companion object {
        private val MAC_PATTERN = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    }
}
