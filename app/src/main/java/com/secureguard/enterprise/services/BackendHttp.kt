package com.secureguard.enterprise.services

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

/**
 * Echter HTTP-Client für das Pilot-Backend ([BackendUrl.BASE]).
 *
 * Alle Backend-kanäle der App (LoRa, Optik, Crowd, Urban) stellen damit
 * echte HTTP-Anfragen; JSON-Antworten werden mit Gson geparst. Fehler,
 * Timeouts oder ein nicht erreichbares Backend liefern `null`/`false` –
 * es werden NIEMALS simulierte oder erfundene Ergebnisse erzeugt.
 */
object BackendHttp {

    private const val TAG = "BackendHttp"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * GET-Anfrage gegen den Backend-Pfad; liefert die geparste JSON-Antwort
     * (Objekt oder Array) oder `null` bei jedem Fehler.
     */
    suspend fun getJson(path: String, params: Map<String, String> = emptyMap()): JsonElement? =
        withContext(Dispatchers.IO) {
            val base = okhttp3.HttpUrl.get(BackendUrl.BASE) ?: return@withContext null
            val url = runCatching {
                base.newBuilder()
                    .pathSegments(0, *path.trim('/').split('/').filter { it.isNotEmpty() }.toTypedArray())
                    .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()
            }.getOrNull() ?: return@withContext null

            val request = Request.Builder().url(url).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GET $path → HTTP ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string() ?: return@withContext null
                    if (body.isBlank()) return@withContext null
                    runCatching { JsonParser.parseString(body) }.getOrElse {
                        Log.w(TAG, "GET $path → JSON-Parsing fehlgeschlagen: ${it.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GET $path → ${e.message}")
                null
            }
        }

    /**
     * POST-Anfrage mit JSON-Body; liefert `true` bei HTTP-2xx.
     */
    suspend fun postJson(path: String, body: JsonObject): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BackendUrl.url(path))
                .post(
                    RequestBody.create(
                        MediaType.get("application/json"),
                        body.toString()
                    )
                )
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "POST $path → HTTP ${response.code}")
                    }
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "POST $path → ${e.message}")
                false
            }
        }
}
