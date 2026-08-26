package com.secureguard.enterprise.services

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export/Import: CSV-Exporte (Assets, Detektionen, Alarme) und
 * PDF-Berichte; optionale AES/GCM-Verschlüsselung der CSV-Dateien über
 * [EncryptionService].
 */
@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SecureGuardRepository,
    private val encryptionService: EncryptionService
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)

    // ============ CSV ============

    suspend fun exportAssetsCsv(): File {
        val assets = repository.getAllAssets().first()
        val file = newFile("assets_export")
        file.writeText(buildString {
            appendLine("ID;Name;MAC;Status;Latitude;Longitude;RSSI;LastSeen")
            assets.forEach { a ->
                appendLine(
                    listOf(
                        a.id, a.name, a.mac, a.status.name,
                        a.latitude?.toString() ?: "", a.longitude?.toString() ?: "",
                        a.rssi.toString(),
                        a.lastSeen?.let { dateFormat.format(it) } ?: ""
                    ).joinToString(";")
                )
            }
        })
        return file
    }

    suspend fun exportDetectionsCsv(): File {
        val detections = repository.getAllDetections().first()
        val file = newFile("detections_export")
        file.writeText(buildString {
            appendLine("Timestamp;AssetMAC;Source;RSSI;Latitude;Longitude")
            detections.forEach { d ->
                appendLine(
                    listOf(
                        dateFormat.format(d.timestamp), d.assetMac, d.sourceType.name,
                        d.rssi.toString(), d.latitude?.toString() ?: "",
                        d.longitude?.toString() ?: ""
                    ).joinToString(";")
                )
            }
        })
        return file
    }

    suspend fun exportAlertsCsv(): File {
        val alerts = repository.getAlerts().first()
        val file = newFile("alerts_export")
        file.writeText(buildString {
            appendLine("Timestamp;AssetID;Type;Severity;Message;Acknowledged")
            alerts.forEach { a ->
                appendLine(
                    listOf(
                        dateFormat.format(a.timestamp),
                        a.assetId,
                        a.type.name,
                        a.severity.name,
                        a.message.replace(';', ','),
                        a.acknowledged.toString()
                    ).joinToString(";")
                )
            }
        })
        return file
    }

    /** CSV-Export mit AES/GCM-Verschlüsselung (Datei endet auf .enc). */
    suspend fun exportAssetsCsvEncrypted(): File {
        val plain = exportAssetsCsv()
        val encrypted = encryptionService.encrypt(plain.readBytes())
        val file = File(plain.parentFile, "${plain.name}.enc")
        // Format: [1 byte ivLen][iv][ciphertext]
        file.outputStream().use { out ->
            require(encrypted.iv.size in 1..255) { "IV-Länge ungültig" }
            out.write(encrypted.iv.size)
            out.write(encrypted.iv)
            out.write(encrypted.data)
        }
        plain.delete()
        return file
    }

    /** Entschlüsselt eine zuvor mit [exportAssetsCsvEncrypted] erzeugte Datei. */
    fun decryptExport(encFile: File): ByteArray {
        val raw = encFile.readBytes()
        require(raw.isNotEmpty()) { "Leere Datei" }
        val ivLen = raw[0].toInt() and 0xFF
        require(raw.size > 1 + ivLen) { "Datei zu kurz" }
        val iv = raw.copyOfRange(1, 1 + ivLen)
        val cipher = raw.copyOfRange(1 + ivLen, raw.size)
        return encryptionService.decrypt(cipher, iv)
    }

    // ============ PDF ============

    /** PDF-Bericht mit aktuellen Assets + Detektionen aus der DB. */
    suspend fun exportPdfReport(
        assets: List<Asset> = emptyList(),
        detections: List<Detection> = emptyList()
    ): File {
        val assetList = assets.ifEmpty { repository.getAllAssets().first() }
        val detectionList = detections.ifEmpty {
            repository.getAllDetections().first().takeLast(100)
        }

        val file = newFile("secureguard_report", ".pdf")
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val title = Paint().apply {
            color = Color.DKGRAY
            textSize = 20f
            isFakeBoldText = true
        }
        val header = Paint().apply {
            color = Color.GRAY
            textSize = 11f
            isFakeBoldText = true
        }
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }

        fun newPageIfNeeded(y: Float): Float {
            if (y < 800f) return y
            document.finishPage(page)
            page = document.startPage(pageInfo)
            canvas = page.canvas
            return 50f
        }

        canvas.drawText("SecureGuard Enterprise – Bericht", 40f, 50f, title)
        canvas.drawText("Erstellt: ${dateFormat.format(Date())}", 40f, 75f, body)

        var y = 120f
        canvas.drawText("Assets (${assetList.size})", 40f, y, header)
        y += 20f
        assetList.forEach { a ->
            y = newPageIfNeeded(y)
            val last = detectionList.lastOrNull { it.assetMac.equals(a.mac, ignoreCase = true) }
            val line = "${a.shortName} · ${a.status.name} · RSSI ${a.rssi}" +
                (last?.let { " · zuletzt: ${dateFormat.format(it.timestamp)}" } ?: "")
            canvas.drawText(line.take(90), 40f, y, body)
            y += 16f
        }

        y += 10f
        y = newPageIfNeeded(y)
        canvas.drawText("Letzte Detektionen (${detectionList.size})", 40f, y, header)
        y += 20f
        detectionList.takeLast(50).forEach { d ->
            y = newPageIfNeeded(y)
            canvas.drawText(
                "${dateFormat.format(d.timestamp)} · ${d.assetMac} · ${d.sourceType.name} · RSSI ${d.rssi}".take(95),
                40f, y, body
            )
            y += 14f
        }

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    // ============ INTERN ============

    private fun newFile(prefix: String, extension: String = ".csv"): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}$extension")
    }
}
