package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ZoneDto(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val iconKey: String = "",
    val createdAt: Long = 0L,
    val radiusMeters: Float = 250f,
    val isPrivate: Boolean = false,
    /** Client epoch-ms stamp of the last write, set by the remote data source. [SYNC-RECONCILE-001] */
    val updatedAt: Long = 0,
)
