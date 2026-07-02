package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val iconKey: String,
    val createdAt: Long,
    val radiusMeters: Float = 250f,
    val isPrivate: Boolean = false,
    /** Client epoch-ms of the last LOCAL mutation — drives Last-Write-Wins reconcile. [SYNC-RECONCILE-001] */
    val updatedAt: Long = 0,
    /** True while a local edit is not yet confirmed by Firestore; protected from inbound sync. [SYNC-RECONCILE-001] */
    val pendingSync: Boolean = false,
)
