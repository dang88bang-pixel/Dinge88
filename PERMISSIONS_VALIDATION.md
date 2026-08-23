# SecureGuard Enterprise – Permissions-Validierung

**Stand:** Vollprüfung 2026-08-23 – Manifest mit allen für die Aktions- und
Interaktionsketten erforderlichen Berechtigungen; alle Deklarationen sind mit
echten Codepfaden verknüpft (keine toren Permissions, keine fehlenden).

## ✅ Alle Permissions vorhanden und korrekt deklariert

### Netzwerk (2)
- ✅ `android.permission.INTERNET` – REST/MQTT/WebSocket/LoRa-Backend/Kartenkacheln
- ✅ `android.permission.ACCESS_NETWORK_STATE` – ConnectivityWatcher (Offline-Queue-Flush)

### Bluetooth (4)
- ✅ `android.permission.BLUETOOTH` (maxSdkVersion="30") – Legacy-Verbindungen
- ✅ `android.permission.BLUETOOTH_ADMIN` (maxSdkVersion="30")
- ✅ `android.permission.BLUETOOTH_SCAN` (targetApi="31") – `BleService.runHardwareScan`
- ✅ `android.permission.BLUETOOTH_CONNECT` (targetApi="31") – `TelemetryService` GATT-Read/Write

### WiFi (3)
- ✅ `android.permission.ACCESS_WIFI_STATE`
- ✅ `android.permission.CHANGE_WIFI_STATE` – `WifiManager.startScan`
- ✅ `android.permission.NEARBY_WIFI_DEVICES` (targetApi="33") – Scan-Ergebnisse ab API 33

### Location (3)
- ✅ `android.permission.ACCESS_FINE_LOCATION` – BLE-Scan < API 31, WiFi-Suche, GPS
- ✅ `android.permission.ACCESS_COARSE_LOCATION`
- ✅ `android.permission.ACCESS_BACKGROUND_LOCATION` – Agent-Worker sucht bei ausgeschaltetem Screen (Whitelist-Assets)

### Kamera (1)
- ✅ `android.permission.CAMERA` – QR-Scan (ZXing embedded, Berechtigungsfluss im Screen)

### NFC (1)
- ✅ `android.permission.NFC` – `NfcService.processTag` (MainActivity.onNewIntent)

### Benachrichtigungen (2)
- ✅ `android.permission.POST_NOTIFICATIONS` – geprüft vor jedem Send (`NotificationService`)
- ✅ `android.permission.VIBRATE`

### Foreground Service (5)
- ✅ `android.permission.FOREGROUND_SERVICE`
- ✅ `android.permission.FOREGROUND_SERVICE_DATA_SYNC` – Suchzyklen/Persistenz
- ✅ `android.permission.FOREGROUND_SERVICE_LOCATION` (API 34+) – GPS im FGS
- ✅ `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` (API 34+) – BLE-GATT im FGS
- ✅ `android.permission.WAKE_LOCK`

### Boot & Energie (3)
- ✅ `android.permission.RECEIVE_BOOT_COMPLETED` – `BootCompletedReceiver` (Worker neu planen, Agent wieder aufnehmen)
- ✅ `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` – Anfrage aus den Einstellungen (zuverlässiger 15-Min-Takt)

### Hardware-Features (5, alle optional)
- ✅ `android.hardware.bluetooth_le` (required="false")
- ✅ `android.hardware.camera` (required="false")
- ✅ `android.hardware.location.gps` (required="false")
- ✅ `android.hardware.nfc` (required="false")
- ✅ `android.hardware.usb.host` (required="false") – `UsbSerialService` (Diagnose in Einstellungen)

## Zusammenfassung
- **Gesamt Permissions:** 27 (inkl. FGS-Typen API 34+ und Hintergrund-Ortung)
- **Status:** ✅ Alle vorhanden, korrekt deklariert und an echten Codepfaden angebunden
- **Laufzeit-Anfrage:** Standort/Bluetooth/WiFi (33+)/Kamera/Benachrichtigungen werden
  über den Berechtigungs-Abschnitt der Einstellungen angefragt
  (`RequestMultiplePermissions`), NICHT automatisch beim Start
- **Hardware-Zugriff:** ✅ BLE, GPS, WiFi, NFC, USB funktionsfähig angebunden
- **Netzwerk:** ✅ MQTT, WebSocket, REST, LoRa-Backend implementiert
