package com.secureguard.enterprise.data.database

import androidx.room.TypeConverter
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.DetectionSource
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromAssetStatus(value: AssetStatus?): String? = value?.name

    @TypeConverter
    fun toAssetStatus(value: String?): AssetStatus =
        runCatching { AssetStatus.valueOf(value ?: "") }.getOrDefault(AssetStatus.UNKNOWN)

    @TypeConverter
    fun fromDetectionSource(value: DetectionSource?): String? = value?.name

    @TypeConverter
    fun toDetectionSource(value: String?): DetectionSource =
        runCatching { DetectionSource.valueOf(value ?: "") }.getOrDefault(DetectionSource.UNKNOWN)

    @TypeConverter
    fun fromAlertType(value: AlertType?): String? = value?.name

    @TypeConverter
    fun toAlertType(value: String?): AlertType =
        runCatching { AlertType.valueOf(value ?: "") }.getOrDefault(AlertType.INFO)

    @TypeConverter
    fun fromAlertSeverity(value: AlertSeverity?): String? = value?.name

    @TypeConverter
    fun toAlertSeverity(value: String?): AlertSeverity =
        runCatching { AlertSeverity.valueOf(value ?: "") }.getOrDefault(AlertSeverity.INFO)
}
