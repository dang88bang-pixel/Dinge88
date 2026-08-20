package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.model.Detection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Base class for services that can emit [Detection]s in real time (e.g. while
 * a passive BLE/LoRa scan is running). The agent collects these flows so that
 * sightings discovered outside of an explicit search still update the database.
 */
abstract class DetectionCapable {

    protected val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 256)
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    protected fun emit(detection: Detection) {
        _detections.tryEmit(detection)
    }
}
