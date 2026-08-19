package com.secureguard.enterprise.services

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Führt BLE-GATT-Operationen aus: Verbinden → Services durchsuchen → Operation
 * (Schreiben eines Befehls / Lesen der Telemetrie) → trennen.
 */
@Singleton
class BleCommandConnector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Schreibsignale der laufenden GATT-Sitzung. */
    private class GattSignals {
        val ready = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
        val services = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
        val write = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
        val read = MutableSharedFlow<ByteArray?>(replay = 1, extraBufferCapacity = 1)
    }

    /**
     * @return true, wenn der Befehl erfolgreich geschrieben wurde.
     */
    suspend fun execute(device: BluetoothDevice, command: String): Boolean {
        return connectAndDiscover(device) { signals, gatt ->
            val characteristic = firstCharacteristic(gatt, WRITE_PROPERTIES)
                ?: return@connectAndDiscover false

            val isWriteNoResponse =
                (characteristic.properties and
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            characteristic.writeType = if (isWriteNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.value = command.toByteArray(Charsets.UTF_8)

            @Suppress("DEPRECATION")
            val writeStarted = gatt.writeCharacteristic(characteristic)
            if (!writeStarted) return@connectAndDiscover false

            if (isWriteNoResponse) {
                true
            } else {
                withTimeoutOrNull(WRITE_TIMEOUT_MS) { signals.write.first() } ?: false
            }
        } ?: false
    }

    /**
     * Liest die Telemetrie-Charakteristik eines Geräts und parst sie.
     *
     * Erwartetes Format der Charakteristik (UTF-8 JSON):
     * ```
     * { "battery": 78, "fuel": 45, "engineOk": true,
     *   "distanceKm": 234.5, "operatingHours": 12.4,
     *   "latitude": 51.2, "longitude": 6.8 }
     * ```
     */
    suspend fun readTelemetry(device: BluetoothDevice): Telemetry? {
        return connectAndDiscover(device) { signals, gatt ->
            val characteristic = firstCharacteristic(gatt, BluetoothGattCharacteristic.PROPERTY_READ)
                ?: return@connectAndDiscover null

            @Suppress("DEPRECATION")
            val readStarted = gatt.readCharacteristic(characteristic)
            if (!readStarted) return@connectAndDiscover null

            val bytes = withTimeoutOrNull(READ_TIMEOUT_MS) { signals.read.first() } ?: return@connectAndDiscover null
            parseTelemetry(bytes)
        }
    }

    private suspend fun <T> connectAndDiscover(
        device: BluetoothDevice,
        block: suspend (GattSignals, BluetoothGatt) -> T?
    ): T? {
        val signals = GattSignals()
        var gatt: BluetoothGatt? = null
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                gatt = g
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> signals.ready.tryEmit(true)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        signals.write.tryEmit(false)
                        signals.read.tryEmit(null)
                    }
                    else -> signals.ready.tryEmit(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                gatt = g
                signals.services.tryEmit(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                signals.write.tryEmit(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    signals.read.tryEmit(characteristic.value)
                } else {
                    signals.read.tryEmit(null)
                }
            }
        }

        return try {
            val gattRef = device.connectGatt(context, false, callback)
            if (gattRef == null) return null
            gatt = gattRef

            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { signals.ready.first() } ?: false
            if (!connected) return null

            gattRef.discoverServices()
            val servicesOk = withTimeoutOrNull(SERVICE_TIMEOUT_MS) { signals.services.first() } ?: false
            if (!servicesOk) return null

            block(signals, gattRef)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
        }
    }

    private fun firstCharacteristic(
        gatt: BluetoothGatt,
        properties: Int
    ): BluetoothGattCharacteristic? {
        return gatt.services
            ?.asSequence()
            ?.flatMap { it.characteristics.asSequence() }
            ?.firstOrNull { ch -> (ch.properties and properties) != 0 }
    }

    private fun parseTelemetry(bytes: ByteArray): Telemetry? {
        return try {
            val text = bytes.toString(Charsets.UTF_8)
            val obj = JSONObject(text)
            Telemetry(
                latitude = optDouble(obj, "latitude"),
                longitude = optDouble(obj, "longitude"),
                battery = if (obj.has("battery")) obj.optInt("battery") else null,
                fuel = if (obj.has("fuel")) obj.optInt("fuel") else null,
                engineOk = obj.optBoolean("engineOk", true),
                distanceKm = optDouble(obj, "distanceKm"),
                operatingHours = optDouble(obj, "operatingHours")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun optDouble(obj: JSONObject, key: String): Double? {
        return if (obj.has(key) && !obj.isNull(key)) {
            runCatching { obj.getDouble(key) }.getOrNull()
        } else {
            null
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val SERVICE_TIMEOUT_MS = 8_000L
        private const val WRITE_TIMEOUT_MS = 5_000L
        private const val READ_TIMEOUT_MS = 5_000L

        private const val WRITE_PROPERTIES =
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    }
}
