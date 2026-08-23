package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lädt bzw. entfernt **explizit** angeforderte Demo-Daten (Einstellungen →
 * Demo-Modus). Der automatische Seed beim App-Start wurde entfernt: Ohne
 * Demo-Modus startet die App mit einer leeren, echten Datenbank.
 */
@Singleton
class DemoDataManager @Inject constructor(
    private val database: SecureGuardDatabase
) {

    /** `true`, wenn die Demo-Assets aktuell in der DB liegen. */
    suspend fun isDemoDataLoaded(): Boolean =
        database.assetDao().count() > 0 && database.assetDao()
            .getByMac(DEMO_MAC_PREFIX + "01") != null

    /** Schreibt die 5 Demo-Assets (idempotent). */
    suspend fun seed(): Unit = withContext(Dispatchers.IO) {
        val now = Date()
        val demo = listOf(
            Asset(
                id = "asset-001", name = "E-Scooter Roller #1", shortName = "Roller #1",
                mac = DEMO_MAC_PREFIX + "01", status = AssetStatus.ONLINE,
                rssi = -45, batteryLevel = 78,
                latitude = 52.5200, longitude = 13.4050,
                lastSeen = now, externalAllowed = true
            ),
            Asset(
                id = "asset-002", name = "E-Bike Fahrrad #2", shortName = "Fahrrad #2",
                mac = DEMO_MAC_PREFIX + "02", status = AssetStatus.MAINTENANCE,
                rssi = -60, batteryLevel = 54,
                latitude = 52.4980, longitude = 13.4040,
                lastSeen = now, maintenanceDue = true
            ),
            Asset(
                id = "asset-003", name = "Schlüsselfinder #3", shortName = "Schlüssel #3",
                mac = DEMO_MAC_PREFIX + "03", status = AssetStatus.OFFLINE,
                rssi = -90, batteryLevel = 12,
                lastSeen = Date(now.time - 2 * 60 * 60 * 1000L)
            ),
            Asset(
                id = "asset-004", name = "Tablet Wache #4", shortName = "Tablet #4",
                mac = DEMO_MAC_PREFIX + "04", status = AssetStatus.ONLINE,
                rssi = -55, batteryLevel = 92,
                latitude = 52.5219, longitude = 13.4132, lastSeen = now
            ),
            Asset(
                id = "asset-005", name = "Smartphone #5", shortName = "Smartphone #5",
                mac = DEMO_MAC_PREFIX + "05", status = AssetStatus.ONLINE,
                rssi = -50, batteryLevel = 64,
                latitude = 52.5380, longitude = 13.4200, lastSeen = now
            )
        )
        demo.forEach { database.assetDao().upsert(it) }
    }

    /** Entfernt die Demo-Assets und zugehörige Daten wieder. */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        (1..5).map { DEMO_MAC_PREFIX + "%02d".format(it) }.forEach { mac ->
            database.assetDao().getByMac(mac)?.let { asset ->
                database.assetDao().deleteById(asset.id)
            }
        }
        database.alertDao().insert(
            Alert(
                assetId = "system",
                type = AlertType.INFO,
                severity = AlertSeverity.INFO,
                message = "Demo-Daten entfernt – App arbeitet jetzt nur noch mit echten Daten.",
                timestamp = Date()
            )
        )
    }

    companion object {
        const val DEMO_MAC_PREFIX = "AA:BB:CC:DD:EE:"
    }
}
