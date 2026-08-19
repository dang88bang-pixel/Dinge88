package com.secureguard.enterprise.services

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Führt einen BLE-GATT-Befehl aus: Verbinden → Services durchsuchen →
 * in die erste beschreibbare Charakteristik schreiben → trennen.
 */
@Singleton
class BleCommandConnector @Inject constructor(
    private val context: Context
) {
    /**
     * @return true, wenn der Befehl erfolgreich geschrieben wurde.
     */
    suspend fun execute(device: BluetoothDevice, command: String): Boolean {
        // replay=1, damit ein Callback, der vor dem ersten Collector eintrifft,
        // nicht verloren geht.
        val readyFlow = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
        val servicesFlow = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
        val writeFlow = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)

        var gatt: BluetoothGatt? = null
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                gatt = g
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> readyFlow.tryEmit(true)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        // Falls noch auf etwas gewartet wird, mit false beenden.
                        writeFlow.tryEmit(false)
                    }
                    else -> readyFlow.tryEmit(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                gatt = g
                servicesFlow.tryEmit(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writeFlow.tryEmit(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        return try {
            val gattRef = device.connectGatt(context, false, callback)
            if (gattRef == null) return false
            gatt = gattRef

            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { readyFlow.first() } ?: false
            if (!connected) return false

            gattRef.discoverServices()
            val servicesOk = withTimeoutOrNull(SERVICE_TIMEOUT_MS) { servicesFlow.first() } ?: false
            if (!servicesOk) return false

            val characteristic = gattRef.services
                ?.asSequence()
                ?.flatMap { it.characteristics.asSequence() }
                ?.firstOrNull { ch ->
                    (ch.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                }
                ?: return false

            val isWriteNoResponse =
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            characteristic.writeType = if (isWriteNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.value = command.toByteArray(Charsets.UTF_8)

            // Alte Signatur wird für minSdk 26 benötigt (ab API 33 deprecated).
            @Suppress("DEPRECATION")
            val writeStarted = gattRef.writeCharacteristic(characteristic) == true
            if (!writeStarted) return false

            if (isWriteNoResponse) {
                true
            } else {
                withTimeoutOrNull(WRITE_TIMEOUT_MS) { writeFlow.first() } ?: false
            }
        } catch (e: Exception) {
            false
        } finally {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val SERVICE_TIMEOUT_MS = 8_000L
        private const val WRITE_TIMEOUT_MS = 5_000L
    }
}
