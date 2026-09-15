package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of `sharedCars/{carId}` — the head of the mirror, not its history. [VEH-SHARED-001]
 *
 * Offline-first for the same reason every other table is: the question this feature answers
 * ("where is the car?") is asked in the street, often with one bar of signal. Without the cache the
 * screen would be empty exactly when it matters, which reads as "the app lost the car" rather than
 * "the network is slow". The row carries [updatedAtMs] so the screen can always say WHEN this was
 * true.
 *
 * ⚠️ Read-only cache: nothing local is authoritative here. The mirror is written straight to
 * Firestore by whoever parks and pulled back by the snapshot listener, so there is no `pendingSync`
 * column and no outbox — a failed mirror write is corrected by the next push of the same session,
 * never by replaying local state.
 */
@Entity(tableName = "shared_cars")
data class SharedCarEntity(
    @PrimaryKey val carId: String,
    val ownerUid: String,
    val ownerName: String? = null,
    val displayName: String,
    val btMacHash: String? = null,
    /** [com.rndeveloper.paparcar.domain.model.VehicleSize] enum name. */
    val sizeCategory: String? = null,
    /** [com.rndeveloper.paparcar.domain.model.CarbodyType] enum name. */
    val carbodyType: String? = null,
    /** [com.rndeveloper.paparcar.domain.model.VehicleColor] enum name. */
    val color: String? = null,
    val sharingEnabled: Boolean = true,
    val createdAtMs: Long = 0L,
    /** [com.rndeveloper.paparcar.domain.model.FuelType] enum name — the car's spec, for the
     *  group's figures. [VEH-SHARED-001] */
    val fuelType: String? = null,
    val consumptionPer100: Float? = null,
    val consumptionElectricPer100: Float? = null,
    val activeInviteCode: String? = null,
    val activeInviteExpiresAtMs: Long? = null,
    // ── state{} flattened: one row per car, and a nullable embedded object would need the same
    // null-marker columns anyway. stateSessionId == null ⇒ the car has never been parked with the
    // group watching.
    val stateSessionId: String? = null,
    val stateLatitude: Double? = null,
    val stateLongitude: Double? = null,
    val stateAccuracy: Float? = null,
    val stateParkedAtMs: Long? = null,
    val stateAuthorUid: String? = null,
    val stateAuthorName: String? = null,
    val stateStreet: String? = null,
    val stateCity: String? = null,
    val stateRegion: String? = null,
    val stateCountry: String? = null,
    val stateCountryCode: String? = null,
    val stateProvince: String? = null,
    val statePostalCode: String? = null,
    val statePlaceName: String? = null,
    val statePlaceCategory: String? = null,
    val stateIsParked: Boolean = false,
    val stateUpdatedAtMs: Long = 0L,
)
