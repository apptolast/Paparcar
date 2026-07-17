package io.apptolast.paparcar.data.datasource.remote.dto

import io.apptolast.paparcar.domain.diagnostics.UiLocationSample

/**
 * Flat wire form of a [UiLocationSample], stored one doc per sample at
 * `diagnostics/{userId}/uiLocation/{autoId}`. There is no session/header doc — each sample is
 * self-describing (it carries [deviceModel]/[appVersion]) because the map-location stream has no
 * session lifecycle to hang a header off. [UI-LOC-FOREGROUND-001]
 */
@kotlinx.serialization.Serializable
data class UiLocationSampleDto(
    val kind: String,
    val timestampMs: Long,
    val foreground: Boolean,
    val priority: String,
    val accuracy: Float? = null,
    val sinceLastFixMs: Long? = null,
    val speed: Float? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    // Device identity, so a sample says which phone produced it (Oppo + Redmi share one account).
    val deviceModel: String? = null,
    val appVersion: String? = null,
)

fun UiLocationSample.toDto(deviceModel: String, appVersion: String): UiLocationSampleDto =
    UiLocationSampleDto(
        kind = kind.name,
        timestampMs = timestampMs,
        foreground = foreground,
        priority = priority,
        accuracy = accuracy,
        sinceLastFixMs = sinceLastFixMs,
        speed = speed,
        lat = latitude,
        lon = longitude,
        deviceModel = deviceModel,
        appVersion = appVersion,
    )
