package com.secureguard.enterprise.presentation.ui.settings

import androidx.lifecycle.ViewModel
import com.secureguard.enterprise.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val repository: SettingsRepository
) : ViewModel() {
    // StateFlows werden direkt über das Repository bereitgestellt und
    // im UI über `collectAsState` gelesen.
}
