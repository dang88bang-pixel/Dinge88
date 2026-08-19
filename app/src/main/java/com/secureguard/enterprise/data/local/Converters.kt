package com.secureguard.enterprise.data.local

import androidx.room.TypeConverter
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.DetectionSource
import java.util.Date

/** Room type converters for enums and [Date]. */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromAssetStatus(status: AssetStatus): String = status.name

    @TypeConverter
    fun toAssetStatus(value: String): AssetStatus =
        runCatching { AssetStatus.valueOf(value) }.getOrDefault(AssetStatus.UNKNOWN)

    @TypeConverter
    fun fromDetectionSource(source: DetectionSource): String = source.name

    @TypeConverter
    fun toDetectionSource(value: String): DetectionSource =
        runCatching { DetectionSource.valueOf(value) }.getOrDefault(DetectionSource.UNKNOWN)

    @TypeConverter
    fun fromAlertType(type: AlertType): String = type.name

    @TypeConverter
    fun toAlertType(value: String): AlertType =
        runCatching { AlertType.valueOf(value) }.getOrDefault(AlertType.INFO)

    @TypeConverter
    fun fromAlertSeverity(severity: AlertSeverity): String = severity.name

    @TypeConverter
    fun toAlertSeverity(value: String): AlertSeverity =
        runCatching { AlertSeverity.valueOf(value) }.getOrDefault(AlertSeverity.INFO)
}
