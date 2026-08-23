package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schlanker HTTP-Client für die konfigurierbaren Remote-Kanäle
 * (LoRaWAN-Backend, Optik-Inferenz, Urban-Infrastruktur, Crowd-Proxy).
 *
 * Alle Aufrufe sind fehlertolerant: bei Netzwerk-/Server-Fehlern wird `null`
 * zurückgegeben – der Kanal bleibt damit inaktiv, es werden **keine** Fake-
 * Daten erzeugt (Simulation nur im expliziten Demo-Modus, siehe
 * [RuntimeSettings.demoMode]).
 */
@Singleton
class RemoteEndpointClient @Inject constructor() {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * GET `url` (optional mit `Authorization: Bearer <apiKey>`) und Parsen der
     * Antwort als JSON (Objekt oder Array). `null` bei Fehler oder
     * Nicht-JSON-Antwort.
     */
    suspend fun getJson(url: String, apiKey: String? = null): JsonElement? =
        execute(Request.Builder().url(url).apply {
            apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }.build())

    /**
     * POST `body` (JSON) an `url` und Parsen der Antwort als JSON-Objekt.
     * `null` bei Fehler oder Nicht-2xx-Status.
     */
    suspend fun postJson(url: String, body: JsonObject, apiKey: String? = null): JsonObject? =
        execute(
            Request.Builder()
                .url(url)
                .apply {
                    apiKey?.takeIf { it.isNotBlank() }?.let {
                        header("Authorization", "Bearer $it")
                    }
                }
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()
        )

    /**
     * POST `body` (JSON) an `url`; liefert nur, ob die Anfrage mit HTTP 2xx
     * beantwortet wurde (z. B. für LoRa-Downlinks / Befehle).
     */
    suspend fun postExpectOk(url: String, body: JsonObject, apiKey: String? = null): Boolean =
        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .apply {
                        apiKey?.takeIf { it.isNotBlank() }?.let {
                            header("Authorization", "Bearer $it")
                        }
                    }
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()
            ).execute().use { response -> response.isSuccessful }
        }.getOrDefault(false)

    private suspend fun execute(request: Request): JsonElement? = runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body?.string() ?: return@runCatching null
            gson.fromJson(body, JsonElement::class.java)
        }
    }.getOrNull()
}
