package com.secureguard.enterprise.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geteilter Speicher für das zuletzt gescannte QR-/Barcode-Ergebnis.
 *
 * Wird vom ScanScreen befüllt und vom "Asset hinzufügen"-Formular gelesen,
 * damit die gescannte MAC/ID automatisch übernommen wird.
 */
@Singleton
class ScanResultStore @Inject constructor() {

    private val _lastScannedValue = MutableStateFlow<String?>(null)
    val lastScannedValue: StateFlow<String?> = _lastScannedValue.asStateFlow()

    fun setScannedValue(value: String) {
        _lastScannedValue.value = value.trim()
    }

    fun consume(): String? {
        val value = _lastScannedValue.value
        _lastScannedValue.value = null
        return value
    }

    fun clear() {
        _lastScannedValue.value = null
    }
}
