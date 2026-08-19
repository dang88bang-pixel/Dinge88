package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LoRa / LoRaWAN – Langstreckenkommunikation (generisch, ohne Meshtastic).
 *
 * Platzhalter für eine echte LoRa-Integration (z. B. Helium, TTN, eigene
 * Gateways). Der Client kann hier später über einen LoRaWAN-Backend-Endpunkt
 * (REST/MQTT) angebunden werden.
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        // TODO: LoRaWAN-Backend abfragen (TTN/Helium/LNS), Asset-ID zuordnen,
        // Detektion mit Position und RSSI zurückgeben.
        return null
    }
}
