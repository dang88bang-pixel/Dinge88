package com.secureguard.enterprise.services

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-Karte: konfiguriert osmdroid so, dass Kartenkacheln im
 * app-eigenen Verzeichnis gecacht werden und die Karte ohne
 * Datenverbindung funktioniert (Cache + optionale MBTiles/zip-Archive).
 */
@Singleton
class OfflineMapService @Inject constructor() {

    /**
     * Basis-Konfiguration: Cache-Pfad im app-externen Bereich.
     * Muss VOR dem ersten MapView-Aufruf ausgeführt werden.
     */
    fun configure(context: Context) {
        val basePath = File(context.getExternalFilesDir(null), "osmdroid").apply { mkdirs() }
        Configuration.getInstance().apply {
            setUserAgentValue(context.packageName)
            osmdroidBasePath = basePath
            osmdroidTileCache = File(basePath, "tiles").apply { mkdirs() }
            // Offline zuerst: Kacheln nur aus dem Cache, wenn gewünscht.
            isMapViewHardwareAccelerated = true
        }
    }

    /**
     * Schaltet die Karte in den Offline-Modus (nur gecachte Kacheln).
     * @param offline false = Online-Kacheln zusätzlich erlaubt
     */
    fun setOfflineMode(mapView: MapView, offline: Boolean) {
        mapView.setUseDataConnection(!offline)
    }

    /**
     * Hängt ein Offline-Archiv (MBTiles oder OSM-zip) an die Karte an.
     *
     * osmdroid erkennt Archive im konfigurierten Basis-Pfad automatisch –
     * die Datei wird also nur dorthin kopiert und die Karte in den
     * Offline-Modus geschaltet.
     * @param archiveFile z. B. `berlin.mbtiles` oder `berlin.zip` (OSMAnd-Format)
     */
    fun attachOfflineArchive(mapView: MapView, archiveFile: File): Boolean {
        if (!archiveFile.exists()) return false
        return runCatching {
            val target = File(Configuration.getInstance().osmdroidBasePath, archiveFile.name)
            archiveFile.copyTo(target, overwrite = true)
            mapView.setUseDataConnection(false)
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.invalidate()
            true
        }.getOrDefault(false)
    }

    /**
     * F-61j: Lädt eine Region VORAB in den osmdroid-Kachel-Cache. Der
     * Offline-Modus war bisher auf den zufälligen Cache angewiesen – mit
     * gezieltem Pre-Load ist er planbar (z. B. Einsatzgebiet vor Abfahrt).
     *
     * Netzwerklastig und blockierend: NICHT im Main-Thread aufrufen
     * (UI kapselt das in viewModelScope(Dispatchers.IO)).
     * @param radiusKm Radius um [center] in Kilometern
     * @return true, wenn der Download ohne Fehler durchlief
     */
    fun preloadRegion(
        mapView: MapView,
        center: GeoPoint,
        radiusKm: Double = 5.0
    ): Boolean = runCatching {
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude)).coerceAtLeast(0.1))
        val box = BoundingBox(
            center.latitude + latDelta, center.longitude + lonDelta,
            center.latitude - latDelta, center.longitude - lonDelta
        )
        val manager = org.osmdroid.tileprovider.cachemanager.CacheManager(mapView, mapView.zoomLevelDouble.toInt().coerceIn(10, 17), 17)
        manager.downloadAreaNoUI(mapView.context, box)
        true
    }.getOrDefault(false)

    /**
     * Liefert die Download-URL für eine Region (Geofabrik-Exporte).
     * Der eigentliche Download läuft über einen DownloadManager/WorkManager;
     * die extrahierten Kacheln landen im osmdroid-Cache.
     */
    fun downloadRegionUrl(region: String = "germany/berlin"): String =
        "https://download.geofabrik.de/europe/$region-latest.osm.pbf"
}
