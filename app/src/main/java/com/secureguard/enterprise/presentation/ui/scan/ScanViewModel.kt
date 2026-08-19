package com.secureguard.enterprise.presentation.ui.scan

import androidx.lifecycle.ViewModel
import com.secureguard.enterprise.data.repository.ScanResultStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    val scanResultStore: ScanResultStore
) : ViewModel()
