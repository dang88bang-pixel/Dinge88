package com.secureguard.enterprise.services

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.core.content.IntentCompat
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
        val message: NdefMessage = readNdefMessage(intent) ?: return null
        return runCatching {
            val record = message.records.firstOrNull() ?: return null
            // RTD-Text: Byte 0 = Status (Bit 7: UTF-16, Bits 5..0: Sprachcode-Länge)
            val payload = record.payload
            if (payload.isEmpty()) return null
            val status = payload[0].toInt() and 0xFF
            val languageLength = status and 0x3F
            val isUtf16 = (status and 0x80) != 0
            val textStart = 1 + languageLength
            val text = if (textStart >= payload.size) {
                ""
            } else if (isUtf16) {
                String(payload, textStart, payload.size - textStart, Charsets.UTF_16)
            } else {
                String(payload, textStart, payload.size - textStart, Charsets.UTF_8)
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

    /**
     * NDEF-Nachricht ohne Tag-I/O auf dem Main-Thread:
     * 1. `EXTRA_NDEF_MESSAGES` – vom System bereits geparst (ACTION_NDEF_DISCOVERED),
     * 2. `Ndef.cachedNdefMessage` – beim Erkennen gelesen, kein `connect()` nötig,
     * 3. Fallback: kurzer `connect()`/`close()` (TECH_/TAG_DISCOVERED ohne Cache).
     */
    private fun readNdefMessage(intent: Intent): NdefMessage? {
        IntentCompat.getParcelableArrayExtra(
            intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java
        )?.filterIsInstance<NdefMessage>()?.firstOrNull()?.let { return it }

        val tag = getTag(intent) ?: return null
        val ndef = Ndef.get(tag) ?: return null
        ndef.cachedNdefMessage?.let { return it }
        return runCatching {
            ndef.connect()
            try {
                ndef.ndefMessage
            } finally {
                runCatching { ndef.close() }
            }
        }.getOrNull()
    }

    private fun getTag(intent: Intent): Tag? =
        IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)

    companion object {
        private val MAC_PATTERN = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    }
}
