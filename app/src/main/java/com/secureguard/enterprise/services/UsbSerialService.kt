package com.secureguard.enterprise.services

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Ereignis der USB-/Seriell-Bridge (automatische Port-Ansicht). */
sealed interface UsbSerialEvent {
    /** Ergebnis einer USB-Berechtigungsanfrage (Systemdialog). */
    data class PermissionResult(val deviceName: String?, val granted: Boolean) : UsbSerialEvent

    /** Ein USB-Seriell-Adapter wurde angesteckt. */
    data class DeviceAttached(val deviceName: String) : UsbSerialEvent
}

/**
 * USB/Serial-Anbindung (kabelgebunden) über usb-serial-for-android.
 * Erkennt angeschlossene USB-Seriell-Adapter (z. B. FTDI, CP210x, CH34x)
 * und liest/z. B. Telemetrie von angeschlossener Hardware.
 *
 * Automatische Port-Ansicht: [events] meldet Anstecken und
 * Berechtigungsergebnisse, damit offene Ansichten ihre Port-Liste ohne
 * manuelles Scannen aktualisieren können. [requestPermissionIfMissing]
 * fragt fehlende Berechtigungen automatisch (mit Cooldown) an.
 *
 * Hinweis: Für den Zugriff muss die USB-Berechtigung erteilt sein
 * (`UsbManager.requestPermission` aus einer Activity).
 */
@Singleton
class UsbSerialService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Broadcast-Action für das USB-Permission-Ergebnis. */
        const val ACTION_USB_PERMISSION = "com.secureguard.enterprise.USB_PERMISSION"

        /** Mindestabstand zwischen zwei automatischen Berechtigungsanfragen. */
        private const val PERMISSION_REQUEST_COOLDOWN_MS = 15_000L
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _events = MutableSharedFlow<UsbSerialEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UsbSerialEvent> = _events.asSharedFlow()

    private val lastPermissionRequestAt = ConcurrentHashMap<Int, Long>()

    /** Meldet ein Berechtigungsergebnis an offene Ansichten. */
    fun notifyPermissionResult(device: UsbDevice?, granted: Boolean) {
        _events.tryEmit(
            UsbSerialEvent.PermissionResult(
                deviceName = device?.deviceName,
                granted = granted
            )
        )
    }

    /** Meldet einen angesteckten Adapter an offene Ansichten. */
    fun notifyDeviceAttached(deviceName: String) {
        _events.tryEmit(UsbSerialEvent.DeviceAttached(deviceName))
    }

    /** Alle gefundenen USB-Serial-Geräte. */
    fun availableDrivers(): List<UsbSerialDriver> =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

    fun hasPermission(driver: UsbSerialDriver): Boolean =
        usbManager.hasPermission(driver.device)

    /**
     * Fordert die USB-Berechtigung automatisch an, falls sie fehlt und nicht
     * gerade erst (Cooldown) angefragt wurde – kein doppelter Dialog nach
     * Ablehnung, aber automatische Anfrage beim ersten Öffnen/Anstecken.
     * Das Ergebnis kommt als Broadcast mit [ACTION_USB_PERMISSION] zurück
     * (Empfänger: MainActivity).
     */
    fun requestPermissionIfMissing(driver: UsbSerialDriver): Boolean {
        if (usbManager.hasPermission(driver.device)) return false
        val now = System.currentTimeMillis()
        val last = lastPermissionRequestAt[driver.device.deviceId]
        if (last != null && now - last < PERMISSION_REQUEST_COOLDOWN_MS) return false
        lastPermissionRequestAt[driver.device.deviceId] = now
        requestPermission(driver)
        return true
    }

    /**
     * Fordert die USB-Berechtigung für ein Gerät an. Das Ergebnis kommt als
     * Broadcast mit [ACTION_USB_PERMISSION] zurück (Empfänger: MainActivity).
     * Der Aufruf muss aus einer Activity heraus geschehen (PendingIntent).
     */
    fun requestPermission(driver: UsbSerialDriver) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        // FLAG_MUTABLE erst ab API 31 setzen (Parsen der Extras durch das System
        // erfordert mutable); davor ist mutabel das Standardverhalten.
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(driver.device, pendingIntent)
    }

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
