package com.secureguard.enterprise.presentation.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    val assets: StateFlow<List<Asset>> = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastUpdate = MutableStateFlow("--:--")
    val lastUpdate: StateFlow<String> = _lastUpdate.asStateFlow()

    private val _zoom = MutableStateFlow(15.0)
    val zoom: StateFlow<Double> = _zoom.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _lastUpdate.value =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            _isLoading.value = false
        }
    }

    fun zoomIn() { _zoom.value = (_zoom.value + 1f).coerceAtMost(19.0) }
    fun zoomOut() { _zoom.value = (_zoom.value - 1f).coerceAtLeast(3.0) }
}
