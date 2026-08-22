package com.secureguard.enterprise.services

import com.secureguard.enterprise.BuildConfig

/**
 * Basis-URL des SecureGuard-Pilot-Backends (FastAPI, siehe `backend/main.py`
 * und `docker-compose.yml`).
 *
 * Wird über `BACKEND_URL` in gradle.properties / local.properties gesetzt
 * (siehe `local.properties.example`). Ohne Eintrag gilt die lokale
 * Standard-URL für Emulator/Docker-Compose-Betrieb (10.0.2.2 = Host-Rechner
 * im Android-Emulator; auf einem echten Gerät die LAN-IP des Backends).
 */
object BackendUrl {

    val BASE: String = BuildConfig.BACKEND_URL.ifBlank { "http://10.0.2.2:8000" }

    fun url(path: String): String =
        trimEnd('/') + if (path.startsWith("/")) path else "/$path"

    private val String.trimEnd(): String = this.trimEnd('/')
}
