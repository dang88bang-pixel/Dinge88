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
 * Reads telemetry (battery, fuel, motor health, location, ...) from the asset
 * over BLE / GATT and dispatches commands back to it.
 *
 * Connects to the asset's GATT server, reads the telemetry characteristic
 * (JSON payload), and parses the result. Returns null when the asset is
 * not reachable via BLE – no simulated data is ever generated.
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
     * Expected format:
     * {"battery":85,"fuel":45,"motor":true,"tires":true,"hours":123.4,"km":567.8,"lat":52.52,"lon":13.40}
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

    /** Sends a command string to the asset. Returns whether delivery succeeded. */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        return dispatchCommand(mac, command)
    }

    /**
     * Sends a command to the asset via BLE GATT write on the command characteristic.
     * Returns true if the write was acknowledged, false otherwise.
     */
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

    /** Clears the in-memory cache (e.g. on logout). */
    suspend fun clear() = mutex.withLock { latest.clear() }

    companion object {
        private const val GATT_TIMEOUT_MS = 8_000L
        private val SERVICE_UUID: UUID = UUID.fromString("6BA1B218-15A8-461F-9FA8-5DC85327FD13")
        private val TELEMETRY_UUID: UUID = UUID.fromString("6BA1B218-15A8-461F-9FA8-5DC85327FD14")
        private val COMMAND_UUID: UUID = UUID.fromString("6BA1B218-15A8-461F-9FA8-5DC85327FD15")
    }
}
