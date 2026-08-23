package com.secureguard.enterprise.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads telemetry (battery, fuel, motor health, location, ...) from the asset
 * over a real BLE / GATT connection and dispatches commands back to it.
 *
 * GATT profile (identisch zur ESP32-Gateway-Firmware
 * `firmware/secureguard_esp32/secureguard_esp32.ino`):
 *  - Service:        6BA1B218-15A8-461F-9FA8-5DC85327FD13
 *  - Characteristic: 6BA1B218-15A8-461F-9FA8-5DC85327FD14 (READ|WRITE|NOTIFY)
 *  - Telemetrie-JSON: {"type":"telemetry","battery":..,"fuel":..,"motorOk":..,
 *                      "tiresOk":..,"hours":..,"km":..,"lat":..,"lng":..}
 *
 * Ohne Berechtigung, ohne erreichbares Gerät oder ohne Antwort wird `null`
 * geliefert — keine simulierten Werte.
 */
@Singleton
class TelemetryService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val gson = Gson()
    private val latest = mutableMapOf<String, Telemetry>()
    private val mutex = Mutex()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    suspend fun searchAsset(asset: Asset): Detection? {
        val telemetry = fetchTelemetry(asset) ?: return null
        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.TELEMETRY,
            nodeId = "gatt-${asset.mac.uppercase()}",
            rssi = telemetryRssi(asset.mac),
            latitude = telemetry.latitude ?: asset.latitude,
            longitude = telemetry.longitude ?: asset.longitude,
            accuracyMeters = 8f,
            message = "GATT-Telemetrie gelesen (Batterie ${telemetry.batteryPercent ?: "?"}%)",
            timestamp = telemetry.timestamp
        )
        emit(detection)
        return detection
    }

    suspend fun getLatestTelemetry(mac: String): Telemetry? = mutex.withLock {
        latest[mac.uppercase()]
    }

    /**
     * Echter GATT-Read: verbindet mit dem Asset, liest die
     * Telemetrie-Charakteristik und parst das JSON. Ergebnis wird gecacht.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchTelemetry(asset: Asset): Telemetry? {
        if (!hasConnectPermission()) return null
        val adapter = bluetoothAdapter?.takeIf { it.isEnabled } ?: return null

        return withContext(Dispatchers.IO) {
            val read = GattConnector.readTelemetry(adapter, asset.mac)
            if (read != null) {
                val telemetry = read.second
                mutex.withLock { latest[asset.mac.uppercase()] = telemetry }
                rssiCache[asset.mac.uppercase()] = read.first
                telemetry
            } else null
        }
    }

    /**
     * Sends a command string to the asset via a real GATT write on the
     * command characteristic. Returns whether the device acknowledged it.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        if (!hasConnectPermission()) return false
        val adapter = bluetoothAdapter?.takeIf { it.isEnabled } ?: return false
        return withContext(Dispatchers.IO) {
            GattConnector.writeCommand(adapter, mac, command)
        }
    }

    /** Letzter echter RSSI-Wert aus dem GATT-Read (readRemoteRssi). */
    private fun telemetryRssi(mac: String): Int =
        rssiCache[mac.uppercase()] ?: -100

    private val rssiCache = mutableMapOf<String, Int>()

    private fun hasConnectPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, needed) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Clears the in-memory cache (e.g. on logout). */
    suspend fun clear() = mutex.withLock {
        latest.clear()
        rssiCache.clear()
    }

    // ============ GATT-CONNECTOR ============

    /**
     * Statelose GATT-Operationen (verbinden → lesen/schreiben → trennen).
     * Jede Operation schließt die Verbindung im finally-Block.
     */
    private object GattConnector {

        const val CONNECT_TIMEOUT_MS = 10_000L
        val SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dc85327fd13")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dc85327fd14")
        private val CCC_DESCRIPTOR_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Verbindet, liest die Telemetrie-Charakteristik → (rssi, telemetry). */
        @Synchronized
        @SuppressLint("MissingPermission")
        fun readTelemetry(
            adapter: BluetoothAdapter,
            mac: String
        ): Pair<Int, Telemetry>? {
            val device = runCatching { adapter.getRemoteDevice(mac) }.getOrNull() ?: return null
            val gatt = runCatching { device.connectGatt(null, false, callback) }.getOrNull()
                ?: return null
            try {
                val payload = runCatching {
                    withTimeoutOrNull(CONNECT_TIMEOUT_MS) { readDeferred.await() }
                }.getOrNull() ?: return null
                val rssi = runCatching {
                    withTimeoutOrNull(2_000L) { rssiDeferred.await() }
                }.getOrNull() ?: -100
                val telemetry = parseTelemetry(mac.uppercase(), payload) ?: return null
                return rssi to telemetry
            } finally {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
                reset()
            }
        }

        /** Verbindet und schreibt den Befehl auf die Charakteristik. */
        @Synchronized
        @SuppressLint("MissingPermission")
        fun writeCommand(adapter: BluetoothAdapter, mac: String, command: String): Boolean {
            val device = runCatching { adapter.getRemoteDevice(mac) }.getOrNull() ?: return false
            val gatt = runCatching { device.connectGatt(null, false, callback) }.getOrNull()
                ?: return false
            try {
                val charReady = runCatching {
                    withTimeoutOrNull(CONNECT_TIMEOUT_MS) { readyDeferred.await() }
                }.getOrNull() ?: return false
                writeResult.reset()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeCharacteristic(
                        charReady,
                        command.toByteArray(Charsets.UTF_8),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    if (result != android.bluetooth.BluetoothStatusCodes.SUCCESS) return false
                } else {
                    @Suppress("DEPRECATION")
                    charReady.value = command.toByteArray(Charsets.UTF_8)
                    @Suppress("DEPRECATION")
                    if (!gatt.writeCharacteristic(charReady)) return false
                }
                return withTimeoutOrNull(5_000L) { writeResult.await() } ?: false
            } finally {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
                reset()
            }
        }

        // ---- Callback-Verdrahtung (ein Deferred pro Verbindung) ----

        private var readDeferred = CompletableDeferred<String>()
        private var readyDeferred = CompletableDeferred<BluetoothGattCharacteristic>()
        private var writeResult = CompletableDeferred<Boolean>()
        private var rssiDeferred = CompletableDeferred<Int?>()

        private fun reset() {
            readDeferred = CompletableDeferred()
            readyDeferred = CompletableDeferred()
            writeResult = CompletableDeferred()
            rssiDeferred = CompletableDeferred()
        }

        private val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (!readDeferred.isCompleted) readDeferred.completeExceptionally(
                            IllegalStateException("getrennt")
                        )
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val characteristic = gatt
                    .getService(SERVICE_UUID)
                    ?.getCharacteristic(CHARACTERISTIC_UUID)

                if (characteristic == null) {
                    if (!readDeferred.isCompleted) {
                        readDeferred.completeExceptionally(
                            IllegalStateException("SecureGuard-GATT-Profil nicht gefunden")
                        )
                    }
                    return
                }
                if (!readyDeferred.isCompleted) readyDeferred.complete(characteristic)

                // CCC-Deskriptor aktivieren, damit Notifications/Reads funktionieren.
                runCatching {
                    val ccc = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
                    if (ccc != null) {
                        @Suppress("DEPRECATION")
                        gatt.setCharacteristicNotification(characteristic, true)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            ccc.value =
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(ccc)
                        } else {
                            gatt.writeDescriptor(
                                ccc,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                        }
                    }
                }
                gatt.readRemoteRssi()
                @Suppress("DEPRECATION")
                gatt.readCharacteristic(characteristic)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: return
                if (!readDeferred.isCompleted) {
                    readDeferred.complete(String(data, Charsets.UTF_8))
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (!readDeferred.isCompleted) {
                    readComplete(String(value, Charsets.UTF_8))
                }
            }

            private fun readComplete(payload: String) {
                if (!readDeferred.isCompleted) readDeferred.complete(payload)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (!writeResult.isCompleted) {
                    writeResult.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (!writeResult.isCompleted) {
                    writeResult.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (!rssiDeferred.isCompleted) {
                    rssiDeferred.complete(if (status == BluetoothGatt.GATT_SUCCESS) rssi else null)
                }
            }
        }

        /** Parst das Telemetrie-JSON des Assets (Firmware-Vertrag). */
        fun parseTelemetry(mac: String, payload: String): Telemetry? {
            return runCatching {
                val json = gson.fromJson(payload, JsonObject::class.java)
                Telemetry(
                    mac = mac,
                    batteryPercent = json.intOrNull("battery"),
                    fuelPercent = json.intOrNull("fuel"),
                    motorOk = json.boolOrNull("motorOk") ?: true,
                    tiresOk = json.boolOrNull("tiresOk") ?: true,
                    operatingHours = json.doubleOrNull("hours"),
                    kilometers = json.doubleOrNull("km"),
                    latitude = json.doubleOrNull("lat"),
                    longitude = json.doubleOrNull("lng"),
                    timestamp = Date()
                )
            }.getOrNull()
        }

        private fun JsonObject.intOrNull(key: String): Int? =
            get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asInt

        private fun JsonObject.doubleOrNull(key: String): Double? =
            get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asDouble

        private fun JsonObject.boolOrNull(key: String): Boolean? =
            get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asBoolean
    }
}
