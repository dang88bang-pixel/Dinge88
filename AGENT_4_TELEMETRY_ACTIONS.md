# 🤖 AGENT 4 – Telemetrie + Aktionsketten + Registrierung
## Scope: TelemetryService, ActionsViewModel, AssetDetailViewModel, AgentService.performRegistration

> **Ziel:** Echte BLE-GATT-Telemetrie, Aktionskette über AgentService.sendAction() reparieren, performRegistration() implementieren.  
> **Keine Änderungen an:** Presentation-Screens, Detection-Services (BLE/WiFi/LoRa/etc.), Backend, Firmware  
> **Betroffene Dateien:** `services/TelemetryService.kt`, `services/AgentService.kt` (nur performRegistration), `presentation/ui/actions/ActionsViewModel.kt`, `presentation/ui/assets/AssetDetailViewModel.kt`

---

## TASK 4.1 – TelemetryService: Echte BLE-GATT-Anbindung

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/TelemetryService.kt`

### Aktuelles Problem:
```kotlin
// fetchTelemetry() generiert IMMER Fake-Daten:
batteryPercent = asset.batteryLevel ?: (60 + Random.nextInt(0, 40)),
fuelPercent = 45,
motorOk = true,
tiresOk = true,

// dispatchCommand() ist simuliert:
return Random.nextFloat() > 0.15f
```

### Zielcode:
```kotlin
package com.secureguard.enterprise.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads telemetry from assets over BLE/GATT and dispatches commands.
 *
 * Connects to the asset's GATT server, reads the telemetry characteristic
 * (JSON payload), and parses battery, fuel, motor status etc.
 * Returns null when the asset is not reachable via BLE.
 *
 * Service/Characteristic UUIDs match the ESP32 firmware:
 *   Service:        6BA1B218-15A8-461F-9FA8-5DC85327FD13
 *   Telemetry Char: 6BA1B218-15A8-461F-9FA8-5DC85327FD14
 *   Command Char:   6BA1B218-15A8-461F-9FA8-5DC85327FD15
 */
@Singleton
class TelemetryService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val latest = mutableMapOf<String, Telemetry>()
    private val mutex = Mutex()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    suspend fun searchAsset(asset: Asset): Detection? {
        val telemetry = fetchTelemetry(asset) ?: return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.TELEMETRY,
            nodeId = "telemetry-gatt",
            rssi = -50,
            latitude = telemetry.latitude,
            longitude = telemetry.longitude,
            accuracyMeters = 8f,
            timestamp = telemetry.timestamp
        ).also { emit(it) }
    }

    suspend fun getLatestTelemetry(mac: String): Telemetry? = mutex.withLock {
        latest[mac.uppercase()]
    }

    /**
     * Connects to the asset via BLE GATT and reads the telemetry characteristic.
     * Returns null if the device is not reachable or the read fails.
     */
    @SuppressLint("MissingPermission")
    private suspend fun fetchTelemetry(asset: Asset): Telemetry? {
        val adapter = bluetoothAdapter ?: return null
        val device = try {
            adapter.getRemoteDevice(asset.mac)
        } catch (e: Exception) {
            return null
        }

        val readDeferred = CompletableDeferred<String?>()
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!readDeferred.isCompleted) readDeferred.complete(null)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    readDeferred.complete(null)
                    return
                }
                val service = g.getService(SERVICE_UUID) ?: run {
                    readDeferred.complete(null)
                    return
                }
                val characteristic = service.getCharacteristic(TELEMETRY_UUID) ?: run {
                    readDeferred.complete(null)
                    return
                }
                g.readCharacteristic(characteristic)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val value = characteristic.value?.let { String(it) }
                    readDeferred.complete(value)
                } else {
                    readDeferred.complete(null)
                }
            }
        }

        gatt = try {
            device.connectGatt(context, false, callback)
        } catch (e: Exception) {
            return null
        }

        return try {
            val raw = withTimeoutOrNull(GATT_TIMEOUT_MS) { readDeferred.await() }
            if (raw.isNullOrBlank()) return null

            val telemetry = parseTelemetryJson(asset.mac, raw)
            if (telemetry != null) {
                mutex.withLock { latest[asset.mac.uppercase()] = telemetry }
            }
            telemetry
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    /**
     * Parses a JSON telemetry payload from the GATT characteristic.
     * Expected format: {"battery":85,"fuel":45,"motor":true,"tires":true,"hours":123.4,"km":567.8,"lat":52.52,"lon":13.40}
     */
    private fun parseTelemetryJson(mac: String, json: String): Telemetry? {
        return try {
            val obj = JSONObject(json)
            Telemetry(
                mac = mac,
                batteryPercent = obj.optInt("battery", -1).takeIf { it >= 0 },
                fuelPercent = obj.optInt("fuel", -1).takeIf { it >= 0 },
                motorOk = obj.optBoolean("motor", true),
                tiresOk = obj.optBoolean("tires", true),
                operatingHours = obj.optDouble("hours").takeIf { !it.isNaN() },
                kilometers = obj.optDouble("km").takeIf { !it.isNaN() },
                latitude = obj.optDouble("lat").takeIf { !it.isNaN() },
                longitude = obj.optDouble("lon").takeIf { !it.isNaN() },
                timestamp = Date()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sends a command to the asset via BLE GATT write.
     * Returns true if the write was acknowledged, false otherwise.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendCommand(mac: String, command: String): Boolean {
        return dispatchCommand(mac, command)
    }

    @SuppressLint("MissingPermission")
    protected open suspend fun dispatchCommand(mac: String, command: String): Boolean {
        val adapter = bluetoothAdapter ?: return false
        val device = try {
            adapter.getRemoteDevice(mac)
        } catch (e: Exception) {
            return false
        }

        val writeDeferred = CompletableDeferred<Boolean>()
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!writeDeferred.isCompleted) writeDeferred.complete(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    writeDeferred.complete(false)
                    return
                }
                val service = g.getService(SERVICE_UUID) ?: run {
                    writeDeferred.complete(false)
                    return
                }
                val characteristic = service.getCharacteristic(COMMAND_UUID) ?: run {
                    writeDeferred.complete(false)
                    return
                }
                @Suppress("DEPRECATION")
                characteristic.value = command.toByteArray(Charsets.UTF_8)
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writeDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        gatt = try {
            device.connectGatt(context, false, callback)
        } catch (e: Exception) {
            return false
        }

        return try {
            withTimeoutOrNull(GATT_TIMEOUT_MS) { writeDeferred.await() } ?: false
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    suspend fun clear() = mutex.withLock { latest.clear() }

    companion object {
        private const val GATT_TIMEOUT_MS = 8_000L
        private val SERVICE_UUID: UUID = UUID.fromString("6BA1B218-15A8-461F-9FA8-5DC85327FD13")
        private val TELEMETRY_UUID: UUID = UUID.fromString("6BA1B218-15A8-461F-9FA8-5DC85327FD14")
        private val COMMAND_UUID: UUID = UUID.fromString("6BA1B218-15A8-461F-9FA8-5DC85327FD15")
    }
}
```

### Änderungen:
- [ ] Komplette Datei neu schreiben (obenstehenden Code verwenden)
- [ ] `import kotlin.random.Random` entfernen
- [ ] `import kotlinx.coroutines.delay` entfernen
- [ ] `fetchTelemetry()`: Echte BLE-GATT-Verbindung + Characteristic-Read
- [ ] `dispatchCommand()`: Echte BLE-GATT-Write auf Command-Characteristic
- [ ] JSON-Parser für Telemetrie-Payload
- [ ] UUIDs aus ESP32-Firmware verwenden
- [ ] `COMMAND_UUID` zusätzlich definieren (neu: `...FD15`)

---

## TASK 4.2 – ActionsViewModel: AgentService.sendAction() nutzen

**Datei:** `presentation/ui/actions/ActionsViewModel.kt`

### Aktuelles Problem:
```kotlin
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val telemetryService: TelemetryService  // NUTZT NUR SIMULIERTEN SERVICE
) : ViewModel() {

    fun executeAction(actionType: ActionType) {
        val success = telemetryService.sendCommand(asset.mac, actionType.wireCommand)
        // ...
    }
}
```

### Zielcode – executeAction():
```kotlin
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService  // <-- AgentService statt TelemetryService
) : ViewModel() {

    // ... (assets, selectedAsset, commandLog, isExecuting, menuExpanded bleiben unverändert)

    fun executeAction(actionType: ActionType) {
        viewModelScope.launch {
            val asset = selectedAsset.value ?: return@launch
            _isExecuting.value = true
            val success = agentService.sendAction(asset, actionType.wireCommand)
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val mark = if (success) "✓" else "✗"
            _commandLog.value = _commandLog.value +
                "$ts → ${actionType.label} an ${asset.shortName} $mark"
            _isExecuting.value = false
        }
    }
}
```

### Änderungen:
- [ ] Constructor: `telemetryService: TelemetryService` → `agentService: AgentService`
- [ ] Import: `AgentService` hinzufügen, `TelemetryService` entfernen
- [ ] `executeAction()`: `telemetryService.sendCommand()` → `agentService.sendAction()`

### Aktionskette VORHER:
```
UI → ActionsViewModel → TelemetryService.sendCommand() → Random > 0.15 → Fake-Ergebnis
```

### Aktionskette NACHHER:
```
UI → ActionsViewModel → AgentService.sendAction() → MQTT + WebSocket + BLE/GATT + Offline-Queue
```

---

## TASK 4.3 – AssetDetailViewModel: AgentService.sendAction() nutzen

**Datei:** `presentation/ui/assets/AssetDetailViewModel.kt`

### Aktuelles Problem:
```kotlin
fun executeAction(actionType: ActionType) {
    val success = telemetryService.sendCommand(asset.mac, actionType.wireCommand)
    // ...
}
```

### Zielcode – executeAction():
```kotlin
fun executeAction(actionType: ActionType) {
    viewModelScope.launch {
        _actionResult.value = ActionResult.Processing
        val asset = _assetState.value ?: return@launch

        val success = agentService.sendAction(asset, actionType.wireCommand)
        val result = if (success) {
            ActionResult(true, "${actionType.label} ausgeführt")
        } else {
            ActionResult(false, "Zustellung fehlgeschlagen (Offline-Queue)")
        }
        _actionResult.value = result

        repository.raiseAlert(
            assetId = asset.id,
            type = if (success) AlertType.INFO else AlertType.WARNING,
            severity = if (success) AlertSeverity.INFO else AlertSeverity.WARNING,
            message = "${actionType.label}: ${result.message}"
        )
        notificationService.sendActionNotification(asset, actionType, success)
    }
}
```

### Änderungen:
- [ ] `telemetryService.sendCommand()` → `agentService.sendAction()` in `executeAction()`
- [ ] `AgentService` ist bereits als Dependency injiziert (nur den Aufruf ändern)
- [ ] Import `AgentService` sicherstellen (bereits vorhanden)
- [ ] `AlertType.SECURITY` → `AlertType.INFO` bei Erfolg (kein Sicherheits-Alarm für normale Aktionen)

---

## TASK 4.4 – AgentService.performRegistration(): Echte HTTP-Implementierung

**Datei:** `services/AgentService.kt`

### Aktuelles Problem:
```kotlin
private suspend fun performRegistration(
    serviceName: String, url: String, data: Map<String, String>, email: String
): Boolean {
    // TODO: echte Registrierung (HTTP-POST mit email im Payload) –
    // bewusst skizziert, um keine unautorisierten Aufrufe auszulösen.
    return false
}
```

### Zielcode:
```kotlin
private suspend fun performRegistration(
    serviceName: String,
    url: String,
    data: Map<String, String>,
    email: String
): Boolean {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val payload = JSONObject(data.toMutableMap().apply { put("email", email) })
        val body = okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("application/json"),
            payload.toString()
        )
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val success = response.isSuccessful

        auditLogService.log(
            action = "REGISTER_HTTP",
            details = "$serviceName: HTTP ${response.code()} (${if (success) "OK" else "FEHLER"})"
        )

        response.close()
        success
    } catch (e: Exception) {
        auditLogService.log(
            action = "REGISTER_HTTP_ERROR",
            details = "$serviceName: ${e.message}"
        )
        false
    }
}
```

### Änderungen:
- [ ] TODO-Kommentar entfernen
- [ ] `return false` durch echte HTTP-POST-Implementierung ersetzen
- [ ] OkHttp-Client erstellen (oder bestehenden nutzen)
- [ ] JSON-Payload mit `data` + `email`
- [ ] Response-Code prüfen (2xx = Erfolg)
- [ ] AuditLog-Einträge für Erfolg/Misserfolg
- [ ] `import okhttp3.OkHttpClient` + `import okhttp3.RequestBody` + `import okhttp3.MediaType`
- [ ] `import org.json.JSONObject`
- [ ] `import java.util.concurrent.TimeUnit`

---

## PRÜFUNG & TEST

### Nach allen Tasks:
```bash
# 1. Keine Random-Imports in TelemetryService
grep -n "import kotlin.random.Random" app/src/main/java/com/secureguard/enterprise/services/TelemetryService.kt
# Erwartet: 0 Treffer

# 2. ActionsViewModel nutzt AgentService
grep -n "agentService\|telemetryService" app/src/main/java/com/secureguard/enterprise/presentation/ui/actions/ActionsViewModel.kt
# Erwartet: agentService (nicht telemetryService)

# 3. AssetDetailViewModel nutzt AgentService in executeAction
grep -A5 "fun executeAction" app/src/main/java/com/secureguard/enterprise/presentation/ui/assets/AssetDetailViewModel.kt | grep "agentService"
# Erwartet: mindestens 1 Treffer

# 4. performRegistration hat keinen TODO mehr
grep -n "TODO" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt | grep -i "registrierung"
# Erwartet: 0 Treffer

# 5. TelemetryService hat GATT-UUIDs
grep -n "UUID.fromString" app/src/main/java/com/secureguard/enterprise/services/TelemetryService.kt
# Erwartet: 3 Treffer (SERVICE, TELEMETRY, COMMAND)

# 6. TelemetryService nutzt BluetoothGatt
grep -n "BluetoothGatt\|connectGatt\|discoverServices" app/src/main/java/com/secureguard/enterprise/services/TelemetryService.kt
# Erwartet: mindestens 5 Treffer

# 7. performRegistration nutzt OkHttp
grep -n "OkHttpClient\|newCall\|response.isSuccessful" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: mindestens 3 Treffer
```

---

## ABNAHMEKRITERIEN

- [ ] `TelemetryService.fetchTelemetry()` verbindet per BLE-GATT und liest Telemetrie-Characteristic
- [ ] `TelemetryService.fetchTelemetry()` gibt `null` bei fehlender Verbindung (keine Fake-Daten)
- [ ] `TelemetryService.dispatchCommand()` schreibt Command auf GATT-Characteristic
- [ ] `TelemetryService.dispatchCommand()` gibt `false` bei fehlgeschlagenem Write
- [ ] JSON-Parser extrahiert battery, fuel, motor, tires, hours, km aus GATT-Payload
- [ ] `ActionsViewModel.executeAction()` nutzt `agentService.sendAction()` (MQTT + WS + BLE + Queue)
- [ ] `AssetDetailViewModel.executeAction()` nutzt `agentService.sendAction()`
- [ ] `AgentService.performRegistration()` sendet echten HTTP-POST an Registrierungs-URL
- [ ] `performRegistration()` loggt Ergebnis ins AuditLog
- [ ] Keine `import kotlin.random.Random` in TelemetryService
- [ ] Keine TODO-Kommentare in `performRegistration()`
