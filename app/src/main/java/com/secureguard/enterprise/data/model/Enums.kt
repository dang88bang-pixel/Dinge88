package com.secureguard.enterprise.data.model

/** Lifecycle / online status of a protected asset. */
enum class AssetStatus {
    ONLINE,
    OFFLINE,
    MAINTENANCE,
    SEARCHING,
    UNKNOWN
}

/** Detection channel used by the self-learning agent. */
enum class DetectionSource {
    BLE,
    WIFI,
    LORA,
    TELEMETRY,
    OPTICAL,
    URBAN,
    CROWD,
    SATELLITE,
    /** Externe REST-APIs (WiGle.net, Google Geolocation, ...). */
    API,
    /** Echtzeit-Kommunikation über MQTT (Paho). */
    MQTT,
    /** Echtzeit-Updates über WebSocket (OkHttp). */
    WEBSOCKET,
    /** NFC-Tag / kontaktlose Anbindung. */
    NFC,
    UNKNOWN
}

/** Category of an alert raised by the system. */
enum class AlertType {
    SECURITY,
    CRITICAL,
    WARNING,
    INFO,
    GEOFENCE,
    LOW_BATTERY,
    MAINTENANCE
}

/** Severity of an alert. */
enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
