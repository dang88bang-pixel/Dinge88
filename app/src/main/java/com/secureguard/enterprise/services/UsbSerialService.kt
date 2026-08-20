package com.secureguard.enterprise.services

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB/Serial-Anbindung (kabelgebunden) über usb-serial-for-android.
 * Erkennt angeschlossene USB-Seriell-Adapter (z. B. FTDI, CP210x, CH34x)
 * und liest/z. B. Telemetrie von angeschlossener Hardware.
 *
 * Hinweis: Für den Zugriff muss die USB-Berechtigung erteilt sein
 * (`UsbManager.requestPermission` aus einer Activity).
 */
@Singleton
class UsbSerialService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** Alle gefundenen USB-Serial-Geräte. */
    fun availableDrivers(): List<UsbSerialDriver> =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

    fun hasPermission(driver: UsbSerialDriver): Boolean =
        usbManager.hasPermission(driver.device)

    /**
     * Liest eine Zeile (bis `\n`) vom ersten verfügbaren Port.
     * @param timeoutMs Wartezeit pro Leseversuch
     */
    suspend fun readLine(timeoutMs: Int = 1000): String? = withContext(Dispatchers.IO) {
        val driver = availableDrivers().firstOrNull() ?: return@withContext null
        if (!usbManager.hasPermission(driver.device)) return@withContext null

        val port = driver.ports.firstOrNull() ?: return@withContext null
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        try {
            connection = usbManager.openDevice(driver.device) ?: return@withContext null
            // open() wirft in usb-serial-for-android 3.5.x eine Exception bei Fehlern
            // (Rückgabetyp Unit statt Boolean).
            try {
                port.open(connection)
            } catch (e: Exception) {
                return@withContext null
            }
            port.setParameters(
                115200,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            val buffer = ByteArray(256)
            val read = port.read(buffer, timeoutMs)
            val line = String(buffer, 0, read.coerceAtLeast(0), Charsets.UTF_8).trim()
            line.ifBlank { null }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { port.close() }
            runCatching { connection?.close() }
        }
    }
}
