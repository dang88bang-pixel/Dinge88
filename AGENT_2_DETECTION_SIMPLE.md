# 🤖 AGENT 2 – Detection-Services (BLE, WiFi, Optical, Satellite)
## Scope: Einfache Detection-Kanäle – Mock entfernen, echte Hardware-Anbindung

> **Ziel:** Alle 4 Services geben bei fehlendem echtem Treffer `null` zurück statt Fake-Daten. WiFi und Optical bekommen echte Implementierungen.  
> **Keine Änderungen an:** Presentation, ViewModels, AgentService, Backend, Firmware  
> **Betroffene Dateien:** ausschließlich `services/BleService.kt`, `services/WifiService.kt`, `services/OpticalService.kt`, `services/SatelliteService.kt`

---

## TASK 2.1 – BleService: Simulierten Fallback entfernen

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/BleService.kt`

### Aktuelles Problem (Zeilen ~60-75):
```kotlin
// Fallback / demo result.
delay(150)
val rssi = -35 - Random.nextInt(0, 45)
return Detection(
    assetMac = asset.mac,
    sourceType = DetectionSource.BLE,
    nodeId = "ble-sim",
    rssi = rssi,
    latitude = asset.latitude ?: 52.5200,
    longitude = asset.longitude ?: 13.4050,
    accuracyMeters = 5f,
    timestamp = Date()
).also { emit(it) }
```

### Zielcode – searchAsset():
```kotlin
@SuppressLint("MissingPermission")
suspend fun searchAsset(asset: Asset): Detection? {
    if (!hasScanPermission() || bluetoothAdapter == null) return null
    return runHardwareScan(asset)
}
```

### Änderungen:
- [ ] Simulierten Fallback-Block (delay + Random + fake Detection) **komplett löschen**
- [ ] `searchAsset()` gibt `null` zurück wenn `runHardwareScan()` null liefert
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen (nur falls nicht mehr benötigt)
- [ ] Kommentar „Fallback / demo result" entfernen

### runHardwareScan() bleibt unverändert (ist bereits echte BLE-Implementierung)

---

## TASK 2.2 – WifiService: Echten WiFi-Scan implementieren

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/WifiService.kt`

### Aktuelles Problem:
```kotlin
suspend fun searchAsset(asset: Asset): Detection? {
    if (hasLocationPermission()) {
        runCatching { wifiManager?.scanResults }
    }
    delay(180)
    val rssi = -50 - Random.nextInt(0, 30)
    return Detection(...)  // IMMER Fake
}
```

### Zielcode – Vollständiger Rewrite:
```kotlin
package com.secureguard.enterprise.services

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    @Suppress("DEPRECATION")
    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasLocationPermission()) return null
        val wm = wifiManager ?: return null

        // Aktuelle Scan-Ergebnisse prüfen (passiv, kein neuer Scan nötig)
        val existingHit = wm.scanResults.firstOrNull {
            it.BSSID.equals(asset.mac, ignoreCase = true)
        }
        if (existingHit != null) {
            return buildDetection(asset, existingHit.BSSID, existingHit.level)
        }

        // Aktiven Scan starten und auf Ergebnis warten
        val scanComplete = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                scanComplete.complete(Unit)
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)

        val started = runCatching { wm.startScan() }.getOrDefault(false)
        if (!started) {
            runCatching { context.unregisterReceiver(receiver) }
            return null
        }

        return try {
            withTimeoutOrNull(SCAN_TIMEOUT_MS) { scanComplete.await() }
            val hit = wm.scanResults.firstOrNull {
                it.BSSID.equals(asset.mac, ignoreCase = true)
            }
            hit?.let { buildDetection(asset, it.BSSID, it.level) }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun buildDetection(asset: Asset, bssid: String, rssi: Int): Detection {
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.WIFI,
            nodeId = "wifi-$bssid",
            rssi = rssi,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 20f,
            timestamp = Date()
        ).also { emit(it) }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val SCAN_TIMEOUT_MS = 5_000L
    }
}
```

### Änderungen:
- [ ] Komplette Datei neu schreiben (obenstehenden Code verwenden)
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen
- [ ] BroadcastReceiver für `SCAN_RESULTS_AVAILABLE_ACTION` registrieren
- [ ] `wifiManager.startScan()` aufrufen
- [ ] Scan-Ergebnisse nach Asset-MAC filtern (`BSSID`)
- [ ] Bei keinem Treffer: `null` zurückgeben

---

## TASK 2.3 – OpticalService: ZXing-basierte Erkennung

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/OpticalService.kt`

### Aktuelles Problem:
```kotlin
if (Random.nextFloat() > 0.55f) return null
return Detection(nodeId = "cam-${Random.nextInt(1, 16)}", ...)  // Fake
```

### Zielcode:
```kotlin
package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optical recognition channel.
 *
 * Matches assets by comparing a scanned QR/barcode value against known
 * asset MACs in the local database. The actual camera scan is triggered
 * by the user via ScanQrScreen; this service checks whether a given
 * asset has a pending optical match (e.g. from a recently scanned code
 * stored in SharedPreferences or passed via a shared state).
 *
 * Without an explicit scan result this channel returns null – no
 * simulated data is ever generated.
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SecureGuardDatabase
) : DetectionCapable() {

    /** Last scanned optical code (set by ScanQrScreen or NFC). */
    @Volatile
    var lastScannedCode: String? = null

    suspend fun searchAsset(asset: Asset): Detection? {
        val code = lastScannedCode ?: return null

        // Match: scanned code equals the asset's MAC or ID
        val matches = code.equals(asset.mac, ignoreCase = true) ||
            code.equals(asset.id, ignoreCase = true) ||
            code.equals(asset.vin, ignoreCase = true)

        if (!matches) return null

        // Consume the scan (one-shot)
        lastScannedCode = null

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = "optical-qr",
            rssi = 0,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 2f,
            message = "Optisch erkannt: $code",
            timestamp = Date()
        ).also { emit(it) }
    }

    /** Setzt den letzten gescannten Code (von ScanQrScreen aufgerufen). */
    fun setScannedCode(code: String) {
        lastScannedCode = code
    }
}
```

### Änderungen:
- [ ] Komplette Datei neu schreiben
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen
- [ ] `SecureGuardDatabase` als Dependency injizieren
- [ ] `lastScannedCode`-Property für Scan-Ergebnisse von `ScanQrScreen`
- [ ] Match gegen MAC, ID und VIN des Assets
- [ ] Bei keinem Match: `null` zurückgeben

---

## TASK 2.4 – SatelliteService: Simulierten Fallback entfernen

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/SatelliteService.kt`

### Aktuelles Problem (Fallback-Block):
```kotlin
// Kein Fix verfügbar – grobe Schätzung (letzte bekannte Position).
delay(500)
if (Random.nextFloat() > 0.4f) return null
return Detection(
    assetMac = asset.mac,
    sourceType = DetectionSource.SATELLITE,
    nodeId = "sat-fix",
    rssi = -100,
    latitude = asset.latitude ?: (52.5200 + Random.nextDouble(-0.08, 0.08)),
    longitude = asset.longitude ?: (13.4050 + Random.nextDouble(-0.08, 0.08)),
    accuracyMeters = 150f,
    timestamp = Date()
).also { emit(it) }
```

### Zielcode – searchAsset():
```kotlin
suspend fun searchAsset(asset: Asset): Detection? {
    val location = currentLocation()
    if (location != null) {
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.SATELLITE,
            nodeId = "gps-fix",
            rssi = -100,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.coerceAtLeast(1f),
            message = "GPS-Position (${location.provider ?: "gnss"})",
            timestamp = Date()
        ).also { emit(it) }
    }

    // Kein GPS-Fix: letzte bekannte Asset-Position als grober Fallback
    if (asset.latitude != null && asset.longitude != null) {
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.SATELLITE,
            nodeId = "last-known",
            rssi = -100,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 500f,
            message = "Letzte bekannte Position (kein GPS-Fix)",
            timestamp = Date()
        ).also { emit(it) }
    }

    return null
}
```

### Änderungen:
- [ ] Gesamten Fallback-Block mit `Random.nextDouble()` entfernen
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen (falls nicht mehr benötigt)
- [ ] Fallback: Letzte bekannte Asset-Position (aus DB) statt Random-Koordinaten
- [ ] Bei gar keiner Position: `null` zurückgeben

---

## PRÜFUNG & TEST

### Nach allen Tasks:
```bash
# 1. Keine Random-Imports mehr in den 4 Dateien
grep -n "import kotlin.random.Random" \
  app/src/main/java/com/secureguard/enterprise/services/BleService.kt \
  app/src/main/java/com/secureguard/enterprise/services/WifiService.kt \
  app/src/main/java/com/secureguard/enterprise/services/OpticalService.kt \
  app/src/main/java/com/secureguard/enterprise/services/SatelliteService.kt
# Erwartet: 0 Treffer

# 2. Keine "sim"-NodeIDs mehr
grep -n "ble-sim\|wifi-ap\|cam-\|sat-fix" \
  app/src/main/java/com/secureguard/enterprise/services/BleService.kt \
  app/src/main/java/com/secureguard/enterprise/services/WifiService.kt \
  app/src/main/java/com/secureguard/enterprise/services/OpticalService.kt \
  app/src/main/java/com/secureguard/enterprise/services/SatelliteService.kt
# Erwartet: 0 Treffer

# 3. Alle searchAsset() können null zurückgeben
grep -c "return null" \
  app/src/main/java/com/secureguard/enterprise/services/BleService.kt \
  app/src/main/java/com/secureguard/enterprise/services/WifiService.kt \
  app/src/main/java/com/secureguard/enterprise/services/OpticalService.kt \
  app/src/main/java/com/secureguard/enterprise/services/SatelliteService.kt
# Erwartet: mindestens 1 "return null" pro Datei

# 4. Keine Fake-Koordinaten (52.5200/13.4050 als Fallback)
grep -n "52\.5200\|13\.4050" \
  app/src/main/java/com/secureguard/enterprise/services/BleService.kt \
  app/src/main/java/com/secureguard/enterprise/services/WifiService.kt \
  app/src/main/java/com/secureguard/enterprise/services/OpticalService.kt
# Erwartet: 0 Treffer (SatelliteService darf asset.latitude nutzen)

# 5. Syntax-Check
grep -c "class.*Service" \
  app/src/main/java/com/secureguard/enterprise/services/BleService.kt \
  app/src/main/java/com/secureguard/enterprise/services/WifiService.kt \
  app/src/main/java/com/secureguard/enterprise/services/OpticalService.kt \
  app/src/main/java/com/secureguard/enterprise/services/SatelliteService.kt
```

### Integrationstest (nach Agent 1 + Agent 2):
```bash
# Build-Test
cd /home/user/Dinge88 && ./gradlew :app:assembleDebug 2>&1 | tail -30
```

---

## ABNAHMEKRITERIEN

- [ ] `BleService.searchAsset()` gibt `null` bei keinem BLE-Hit (keine Fake-Detection)
- [ ] `WifiService.searchAsset()` nutzt `WifiManager.startScan()` + `getScanResults()` + BSSID-Match
- [ ] `WifiService.searchAsset()` gibt `null` wenn Asset-MAC nicht in WiFi-Scan-Ergebnissen
- [ ] `OpticalService.searchAsset()` matcht `lastScannedCode` gegen Asset-MAC/ID/VIN
- [ ] `OpticalService.searchAsset()` gibt `null` wenn kein Code gescannt wurde
- [ ] `SatelliteService.searchAsset()` nutzt echte GPS-Position oder letzte bekannte Asset-Position
- [ ] `SatelliteService.searchAsset()` gibt `null` wenn weder GPS noch letzte Position verfügbar
- [ ] Keine `import kotlin.random.Random` in den 4 Dateien
- [ ] Keine hartcodierten Berlin-Koordinaten als Fallback
- [ ] Alle 4 Services implementieren `DetectionCapable` und emitten echte Detections
