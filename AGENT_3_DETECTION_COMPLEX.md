# 🤖 AGENT 3 – Detection-Services (LoRa, Urban, Crowd) + Backend-Crowd
## Scope: Komplexe Detection-Kanäle mit API-/Backend-Anbindung

> **Ziel:** DummyLoraClient ersetzen, UrbanService an echte APIs anbinden, CrowdService mit Backend-Endpoint verdrahten.  
> **Keine Änderungen an:** Presentation, ViewModels, BleService, WifiService, OpticalService, SatelliteService, TelemetryService  
> **Betroffene Dateien:** `services/LoraService.kt`, `services/UrbanService.kt`, `services/CrowdService.kt`, `backend/main.py` (nur Crowd-Endpoint)

---

## TASK 3.1 – LoraService: DummyLoraClient durch Helium-API ersetzen

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/LoraService.kt`

### Aktuelles Problem:
```kotlin
private val loraClient: LoraClient = DummyLoraClient()
// DummyLoraClient: 4 hartcodierte Berlin-Gateways + Random

suspend fun sendCommand(mac: String, command: String): Boolean {
    return inRange || Random.nextFloat() > 0.5f  // FAKE
}
```

### Zielcode:
```kotlin
package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.services.apis.HeliumNetworkApi
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttService: MqttService
) : DetectionCapable() {

    private val heliumApi: HeliumNetworkApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.helium.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HeliumNetworkApi::class.java)
    }

    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    suspend fun searchAsset(asset: Asset): Detection? {
        // Versuche Helium-Hotspots in der Nähe des Assets zu finden
        val lat = asset.latitude ?: return null
        val lon = asset.longitude ?: return null

        val hotspots = try {
            heliumApi.getHotspots(lat, lon, limit = 10).data
        } catch (e: Exception) {
            emptyList()
        }

        gateways = hotspots.map { hs ->
            Gateway(
                id = hs.address ?: hs.name ?: "unknown",
                rssi = -70,  // Helium API liefert keinen direkten RSSI für Asset-Sichtungen
                latitude = hs.lat,
                longitude = hs.lng,
                seenMacs = emptyList()  // Helium kennt keine MACs direkt
            )
        }

        // Wenn Hotspots in der Nähe sind → Detection erzeugen
        if (hotspots.isEmpty()) return null

        val nearest = hotspots.minByOrNull { hs ->
            val dLat = hs.lat - lat
            val dLon = hs.lng - lon
            dLat * dLat + dLon * dLon
        } ?: return null

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.LORA,
            nodeId = nearest.address ?: "helium-hotspot",
            rssi = -70,
            latitude = nearest.lat,
            longitude = nearest.lng,
            accuracyMeters = 200f,
            message = "Helium Hotspot: ${nearest.name ?: nearest.address}",
            timestamp = Date()
        ).also { emit(it) }
    }

    /**
     * Sendet einen Befehl über MQTT an das LoRa-Gateway.
     * Das Gateway (ESP32) leitet den Befehl per LoRa an das Asset weiter.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        return try {
            mqttService.sendCommand(mac, command)
            mqttService.isConnected
        } catch (e: Exception) {
            false
        }
    }

    suspend fun refreshGateways(): List<Gateway> {
        // Gateways werden bei searchAsset() aktualisiert
        return gateways
    }
}

// Gateway, LoraClient-Interface bleiben erhalten
data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val seenMacs: List<String>
)

// DummyLoraClient ENTFERNT – Helium-API wird direkt genutzt
```

### Änderungen:
- [ ] `DummyLoraClient`-Klasse **komplett löschen**
- [ ] `LoraClient`-Interface entfernen (nicht mehr nötig)
- [ ] `HeliumNetworkApi` direkt integrieren (Retrofit-Client)
- [ ] `sendCommand()` über `MqttService` (Gateway empfängt MQTT, leitet per LoRa weiter)
- [ ] `import kotlin.random.Random` entfernen
- [ ] `MqttService` als Constructor-Dependency

### Neue Dependency im Constructor:
```kotlin
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttService: MqttService
)
```

---

## TASK 3.2 – UrbanService: Echte API-Anbindung über ApiServiceManager

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/UrbanService.kt`

### Aktuelles Problem:
```kotlin
private val nodes = listOf(
    Triple("hub-hbf", 52.5255, 13.3695),  // Hartcodiert
    ...
)
if (Random.nextFloat() > 0.45f) return null  // Fake
```

### Zielcode:
```kotlin
package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Urban infrastructure channel.
 *
 * Queries OpenChargeMap (charging stations) and DHL (pack stations) near
 * the asset's last known position to check for urban-infrastructure sightings.
 * A detection is generated when infrastructure nodes are found near the asset,
 * indicating potential proximity through urban sensor coverage.
 */
@Singleton
class UrbanService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiServiceManager: ApiServiceManager
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        val lat = asset.latitude ?: return null
        val lon = asset.longitude ?: return null

        // 1. Ladesäulen in der Nähe prüfen
        val stations = apiServiceManager.searchViaOpenChargeMap(lat, lon)
        if (stations.isNotEmpty()) {
            val nearest = stations.first()
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.URBAN,
                nodeId = "charger-${nearest.id}",
                rssi = -75,
                latitude = nearest.latitude ?: lat,
                longitude = nearest.longitude ?: lon,
                accuracyMeters = 50f,
                message = "Ladesäule: ${nearest.operator ?: nearest.title ?: "Unbekannt"}",
                timestamp = Date()
            ).also { emit(it) }
        }

        // 2. DHL-Packstationen in der Nähe prüfen
        val packstations = apiServiceManager.searchViaDHL(lat, lon)
        if (packstations.isNotEmpty()) {
            val nearest = packstations.first()
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.URBAN,
                nodeId = "dhl-${nearest.id ?: "unknown"}",
                rssi = -80,
                latitude = nearest.latitude ?: lat,
                longitude = nearest.longitude ?: lon,
                accuracyMeters = 80f,
                message = "Packstation: ${nearest.name ?: nearest.id ?: "?"}",
                timestamp = Date()
            ).also { emit(it) }
        }

        // 3. CKAN Smart-City-Datensätze prüfen
        val datasets = apiServiceManager.searchViaCKAN(asset.mac)
        if (datasets.isNotEmpty()) {
            val ds = datasets.first()
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.URBAN,
                nodeId = "ckan-${ds.id ?: "unknown"}",
                rssi = 0,
                latitude = lat,
                longitude = lon,
                accuracyMeters = 100f,
                message = "Smart-City: ${ds.title ?: "Datensatz gefunden"}",
                timestamp = Date()
            ).also { emit(it) }
        }

        return null
    }
}
```

### Änderungen:
- [ ] Komplette Datei neu schreiben
- [ ] `ApiServiceManager` als Constructor-Dependency
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen
- [ ] Hartcodierte `nodes`-Liste entfernen
- [ ] Echte API-Abfragen: OpenChargeMap → DHL → CKAN (Priorität)
- [ ] Bei keinem Treffer: `null` zurückgeben

---

## TASK 3.3 – CrowdService: Backend-Crowdsource-Endpoint anbinden

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/CrowdService.kt`

### Aktuelles Problem:
```kotlin
if (Random.nextFloat() > 0.5f) return null  // Fake
return Detection(nodeId = "crowd-${Random.nextInt(1000, 9999)}", ...)  // Fake
```

### Zielcode:
```kotlin
package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crowdsource find-my-network channel.
 *
 * Queries the SecureGuard backend for crowd-reported sightings of an asset.
 * Only active when [Asset.externalAllowed] is true (GDPR compliance).
 * Returns null when no sightings are found or the backend is unreachable.
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val backendUrl: String
        get() {
            val ws = BuildConfig.WEBSOCKET_URL
            // WebSocket-URL in HTTP umwandeln: ws:// → http://, wss:// → https://
            return ws.replace("ws://", "http://").replace("wss://", "https://")
                .removeSuffix("/ws").trimEnd('/')
        }

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!asset.externalAllowed) return null
        if (backendUrl.isBlank()) return null

        return try {
            val url = "$backendUrl/api/crowd/search?mac=${asset.mac}"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val sightings = gson.fromJson(body, Array<CrowdSighting>::class.java)

            if (sightings.isEmpty()) return null

            val latest = sightings.first()
            Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.CROWD,
                nodeId = latest.reporterId ?: "crowd-unknown",
                rssi = latest.rssi ?: -85,
                latitude = latest.latitude,
                longitude = latest.longitude,
                accuracyMeters = 80f,
                message = "Crowd-Sichtung: ${latest.reporterId ?: "anonym"}",
                timestamp = Date()
            ).also { emit(it)
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class CrowdSighting(
    @SerializedName("mac") val mac: String? = null,
    @SerializedName("reporter_id") val reporterId: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)
```

### Änderungen:
- [ ] Komplette Datei neu schreiben
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen
- [ ] HTTP-GET an Backend `/api/crowd/search?mac={mac}`
- [ ] Backend-URL aus `BuildConfig.WEBSOCKET_URL` ableiten
- [ ] Bei Fehler/leer: `null` zurückgeben

---

## TASK 3.4 – Backend: Crowd-Endpoints hinzufügen

**Datei:** `backend/main.py`

### Neue Endpoints am Ende vor dem `if __name__`-Block hinzufügen:

```python
# ============ CROWD SOURCE ============

@app.post("/api/crowd/report")
async def report_crowd_sighting(sighting: dict):
    """Anonyme Crowd-Sichtung melden (MAC + Position + RSSI)."""
    mac = sighting.get("mac", "").upper()
    if not mac:
        return {"status": "error", "message": "mac required"}

    conn = get_db()
    conn.execute(
        "INSERT INTO crowd_sightings (mac, reporter_id, rssi, latitude, longitude, timestamp) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (
            mac,
            sighting.get("reporter_id", "anonymous"),
            sighting.get("rssi", 0),
            sighting.get("latitude"),
            sighting.get("longitude"),
            datetime.now(),
        ),
    )
    conn.commit()
    conn.close()
    return {"status": "ok"}


@app.get("/api/crowd/search")
async def search_crowd_sightings(mac: str, hours: int = 24):
    """Letzte Crowd-Sichtungen für eine MAC-Adresse abrufen."""
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM crowd_sightings WHERE mac = ? "
        "AND timestamp > datetime('now', ? || ' hours') "
        "ORDER BY timestamp DESC LIMIT 10",
        (mac.upper(), -hours),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]
```

### DB-Schema erweitern (in `init_db()`):

```python
CREATE TABLE IF NOT EXISTS crowd_sightings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mac TEXT,
    reporter_id TEXT,
    rssi INTEGER,
    latitude REAL,
    longitude REAL,
    timestamp TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_crowd_mac ON crowd_sightings(mac);
```

### Änderungen:
- [ ] `crowd_sightings`-Tabelle in `init_db()` hinzufügen
- [ ] `POST /api/crowd/report` Endpoint hinzufügen
- [ ] `GET /api/crowd/search` Endpoint hinzufügen
- [ ] Index auf `mac`-Spalte für schnelle Abfragen

---

## PRÜFUNG & TEST

### Nach allen Tasks:
```bash
# 1. Keine Random-Imports in den 3 Service-Dateien
grep -n "import kotlin.random.Random" \
  app/src/main/java/com/secureguard/enterprise/services/LoraService.kt \
  app/src/main/java/com/secureguard/enterprise/services/UrbanService.kt \
  app/src/main/java/com/secureguard/enterprise/services/CrowdService.kt
# Erwartet: 0 Treffer

# 2. Kein DummyLoraClient mehr
grep -n "DummyLoraClient" app/src/main/java/com/secureguard/enterprise/services/LoraService.kt
# Erwartet: 0 Treffer

# 3. Keine hartcodierten Berliner Koordinaten
grep -n "52\.52\|13\.40\|52\.49\|52\.53" \
  app/src/main/java/com/secureguard/enterprise/services/LoraService.kt \
  app/src/main/java/com/secureguard/enterprise/services/UrbanService.kt \
  app/src/main/java/com/secureguard/enterprise/services/CrowdService.kt
# Erwartet: 0 Treffer

# 4. Backend hat crowd-Endpoints
grep -n "crowd" backend/main.py
# Erwartet: crowd_sightings, /api/crowd/report, /api/crowd/search

# 5. LoraService nutzt MqttService
grep -n "mqttService" app/src/main/java/com/secureguard/enterprise/services/LoraService.kt
# Erwartet: mindestens 1 Treffer

# 6. UrbanService nutzt ApiServiceManager
grep -n "apiServiceManager" app/src/main/java/com/secureguard/enterprise/services/UrbanService.kt
# Erwartet: mindestens 3 Treffer (OpenChargeMap, DHL, CKAN)

# 7. CrowdService nutzt HTTP-Client
grep -n "httpClient\|backendUrl" app/src/main/java/com/secureguard/enterprise/services/CrowdService.kt
# Erwartet: mindestens 2 Treffer
```

### Backend-Test:
```bash
cd /home/user/Dinge88/backend
pip install -r requirements.txt 2>/dev/null
python -c "from main import app; print('Backend imports OK')"
```

---

## ABNAHMEKRITERIEN

- [ ] `LoraService` nutzt Helium-API für Gateway-Discovery (kein DummyLoraClient)
- [ ] `LoraService.sendCommand()` sendet über MQTT (kein Random)
- [ ] `UrbanService` fragt OpenChargeMap + DHL + CKAN ab (keine hartcodierten Knoten)
- [ ] `UrbanService` gibt `null` bei keinem API-Treffer
- [ ] `CrowdService` fragt Backend `/api/crowd/search` ab (kein Random)
- [ ] `CrowdService` gibt `null` bei Backend-Fehler oder leerer Antwort
- [ ] Backend hat `crowd_sightings`-Tabelle + 2 Endpoints
- [ ] Keine `import kotlin.random.Random` in den 3 Service-Dateien
- [ ] Keine Dummy/Mock/Placeholder-Klassen mehr
