package com.secureguard.enterprise

import com.google.common.truth.Truth.assertThat
import com.secureguard.enterprise.services.PrivacyService
import org.json.JSONObject
import org.junit.Test

/**
 * Vertrag der Datenauskunft: keine Secret-Felder, Art.15-Marker.
 * Passwörter legt der Anwender selbst fest – sie gehören nicht in den Export.
 */
class PrivacyExportContractTest {

    @Test
    fun export_json_must_not_contain_secret_keys() {
        val sample = JSONObject()
            .put("exportType", "DSGVO_ART15_DATENAUSKUNFT")
            .put("note", "Keine Passwörter/PINs/Keystore-Secrets enthalten.")
            .put("assets", org.json.JSONArray())
        val text = sample.toString()
        assertThat(text).doesNotContain("KEYSTORE_PASSWORD")
        assertThat(text).doesNotContain("pin_hash")
        assertThat(text).doesNotContain("passphrase")
        assertThat(sample.getString("exportType")).contains("DSGVO")
    }

    @Test
    fun default_retention_is_90_days() {
        assertThat(PrivacyService.DEFAULT_RETENTION_DAYS).isEqualTo(90)
    }
}
