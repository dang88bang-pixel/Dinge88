package com.secureguard.enterprise.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.secureguard.enterprise.data.model.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Echte BLE-GATT-Verbindung zu einem Asset (z. B. ESP32-Gateway mit der
 * SecureGuard-Firmware). Liest die Telemetrie-Characteristic (JSON-Payload)
 * und schreibt Befehle in dieselbe Characteristic (Write mit Response).
 *
 * Service/Characteristic-UUIDs entsprechen der Firmware
 * (`firmware/secureguard_esp32/secureguard_esp32.ino`):
 * - Service:        `6BA1B218-15A8-461F-9FA8-5DC85327FD13`
 * - Characteristic: `6BA1B218-15A8-461F-9FA8-5DC85327FD14` (READ | WRITE | NOTIFY)
 *
 * Telemetrie-Vertrag (Firmware): `{"type":"telemetry","battery":85,"rssi":-45,"timestamp":"..."}`
 * Erweiterte Felder (optional): `fuel`, `motorOk`, `tiresOk`, `hours`, `km`, `lat`, `lng`.
 */
@Singleton
class BleCommandConnector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()

    private val adapter: android.bluetooth.BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Beschreibt, ob ein GATT-Verbindungsaufbau überhaupt möglich ist. */
    fun canConnect(): Boolean =
        adapter != null && ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT
            else Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Verbindet mit dem Gerät, liest die Telemetrie-Characteristic und baut
     * die Verbindung wieder ab. Liefert `null`, wenn das Gerät nicht erreicht
     * werden kann (kein Fake-Fallback).
     */
    suspend fun readTelemetry(mac: String): Telemetry? {
        val payload = withGatt(mac) { gatt, characteristic, done ->
            if (!gatt.readCharacteristic(characteristic)) {
                done.complete(null)
            }
        } ?: return null
        return parseTelemetry(payload, mac)
    }

    /**
     * Schreibt einen Befehl (z. B. `ALARM`, `LIGHT`) in die Characteristic
     * (WRITE_TYPE_DEFAULT, d. h. mit Bestätigung). Liefert `true` nur bei
     * bestätigtem Write.
     */
    suspend fun writeCommand(mac: String, command: String): Boolean {
        val written = withGatt(mac) { gatt, characteristic, done ->
            val ok: Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        command.toByteArray(Charsets.UTF_8),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    characteristic.setValue(command.toByteArray(Charsets.UTF_8))
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            if (!ok) done.complete(null)
        }
        return written != null
    }

    // ============ Interner Verbindungs-Handler ============

    /**
     * Öffnet eine GATT-Verbindung, führt [operation] aus und wartet auf deren
     * Abschluss (Characteristik-Read/-Write). Das Ergebnis ist der Payload
     * (String) bzw. ein leerer Marker bei Write-Erfolg; `null` = Fehlschlag.
     */
    @SuppressLint("MissingPermission")
    private suspend fun withGatt(
        mac: String,
        operation: (BluetoothGatt, BluetoothGattCharacteristic, CompletableDeferred<String?>) -> Unit
    ): String? {
        if (!canConnect()) return null
        val device: BluetoothDevice = adapter?.getRemoteDevice(mac) ?: return null
        val done = CompletableDeferred<String?>()

        return withContext(Dispatchers.IO) {
            var gattRef: BluetoothGatt? = null
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                        BluetoothProfile.STATE_DISCONNECTED ->
                            // Verbindung weg, bevor die Operation abgeschlossen wurde.
                            if (!done.isCompleted) done.complete(null)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (!done.isCompleted) done.complete(null)
                        return
                    }
                    val service = gatt.getService(SERVICE_UUID) ?: run {
                        if (!done.isCompleted) done.complete(null)
                        return
                    }
                    val characteristic =
                        service.getCharacteristic(CHARACTERISTIC_UUID) ?: run {
                            if (!done.isCompleted) done.complete(null)
                            return
                        }
                    operation(gatt, characteristic, done)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    val value = characteristic.value ?: ByteArray(0)
                    if (!done.isCompleted) {
                        done.complete(
                            if (status == BluetoothGatt.GATT_SUCCESS) String(value, Charsets.UTF_8) else null
                        )
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: android.bluetooth.BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    if (!done.isCompleted) {
                        done.complete(
                            if (status == BluetoothGatt.GATT_SUCCESS) String(value, Charsets.UTF_8) else null
                        )
                    }
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (!done.isCompleted) {
                        done.complete(if (status == BluetoothGatt.GATT_SUCCESS) "" else null)
                    }
                }
            }

            try {
                gattRef = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, callback)
                } ?: return@withContext null

                // Verbinden + Services entdecken + Operation + Antwort
                val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS + OP_TIMEOUT_MS) {
                    done.await()
                }
                result
            } catch (e: Exception) {
                null
            } finally {
                runCatching { gattRef?.disconnect() }
                runCatching { gattRef?.close() }
            }
        }
    }

    /** Wandelt den JSON-Payload der Characteristic in ein [Telemetry] um. */
    internal fun parseTelemetry(payload: String, mac: String): Telemetry? {
        if (payload.isBlank()) return null
        val json = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
            ?: return Telemetry(mac = mac) // Nicht-JSON: trotzdem gültige „online"-Meldung
        return Telemetry(
            mac = mac,
            batteryPercent = json.get("battery")?.asInt
                ?: json.get("batteryPercent")?.asInt,
            fuelPercent = json.get("fuel")?.asInt
                ?: json.get("fuelPercent")?.asInt,
            motorOk = json.get("motorOk")?.asBoolean ?: true,
            tiresOk = json.get("tiresOk")?.asBoolean ?: true,
            operatingHours = json.get("hours")?.asDouble
                ?: json.get("operatingHours")?.asDouble,
            kilometers = json.get("km")?.asDouble
                ?: json.get("kilometers")?.asDouble,
            latitude = json.get("lat")?.asDouble ?: json.get("latitude")?.asDouble,
            longitude = json.get("lng")?.asDouble ?: json.get("longitude")?.asDouble,
            timestamp = Date()
        )
    }

    companion object {
        /** Service-UUID der SecureGuard-Firmware (BLE-Telemetrie). */
        val SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dc85327fd13")

        /** Characteristic-UUID (READ | WRITE | NOTIFY). */
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dc85327fd14")

        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val OP_TIMEOUT_MS = 5_000L

        /** Optional: Hilfs-Encoder, falls Befehle Base64-kodiert werden sollen. */
        fun encodeCommand(command: String): String =
            Base64.encodeToString(command.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
