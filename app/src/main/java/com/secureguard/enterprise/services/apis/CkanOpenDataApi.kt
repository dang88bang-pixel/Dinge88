package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CKAN Open Data API – Smart-City-Datensätze.
 *
 * Basis-URL: reales Open-Data-Portal `https://www.govdata.de/ckan/`
 * (offizielles deutsches Datenportal, über `CKAN_BASE_URL` überschreibbar),
 * Pfad `api/3/action/package_search`. Kostenlos ohne Key; Antwort:
 * `{ "success": true, "result": { "count": n, "results": [...] } }`.
 */
interface CkanOpenDataApi {

    @GET("api/3/action/package_search")
    suspend fun searchDatasets(
        @Query("q") query: String,
        @Query("rows") rows: Int = 10
    ): CkanResponse
}

data class CkanResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "result") val result: CkanResult = CkanResult()
)

data class CkanResult(
    @Json(name = "count") val count: Int = 0,
    @Json(name = "results") val results: List<CkanDataset> = emptyList()
)

data class CkanDataset(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "organization") val organization: CkanOrganization? = null,
    @Json(name = "resources") val resources: List<CkanResource> = emptyList()
)

data class CkanOrganization(
    @Json(name = "title") val title: String? = null
)

data class CkanResource(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "format") val format: String? = null
)
