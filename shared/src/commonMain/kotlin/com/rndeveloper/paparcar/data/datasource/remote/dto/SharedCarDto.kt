package com.rndeveloper.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

/**
 * `sharedCars/{carId}` — the group's document. [VEH-SHARED-001]
 *
 * ⛔ No `bluetoothDeviceId` here: what travels is [btMacHash], a group-scoped comparison token.
 * ⛔ No plate, no route, no detection provenance anywhere in this file — see [SharedCarSessionDto].
 */
@Serializable
data class SharedCarDto(
    val carId: String = "",
    val ownerUid: String = "",
    val ownerName: String? = null,
    val displayName: String = "",
    val btMacHash: String? = null,
    val sizeCategory: String? = null,
    val carbodyType: String? = null,
    val color: String? = null,
    val sharingEnabled: Boolean = true,
    val createdAtMs: Long = 0L,
    /** The car's spec, so the group's figures come out the same for everyone. The PRICE is not
     *  here on purpose: what a litre costs is each reader's own. [VEH-SHARED-001] */
    val fuelType: String? = null,
    val consumptionPer100: Float? = null,
    val consumptionElectricPer100: Float? = null,
    /**
     * The outstanding join code, or null when there is none live. It lives HERE, on the car, and
     * not only as a `sharedCarInvites/{code}` document, because an invite is readable one code at a
     * time by whoever holds it — the rules forbid listing them, on purpose. Without this field the
     * owner could neither re-read the code they generated yesterday nor revoke it when dissolving
     * the group, and an orphan invite is the one piece of this feature that must never outlive its
     * car.
     */
    val inviteCode: String? = null,
    val inviteExpiresAtMs: Long? = null,
)

/**
 * `sharedCars/{carId}/state/current` — where the car is right now, whoever parked it.
 *
 * A DOCUMENT of its own and not a nested field of [SharedCarDto], because the two have different
 * writers: the car doc belongs to the owner and barely changes, while this one is rewritten by
 * whichever member last parked. As a separate doc each write is a plain full `set()` of a small
 * payload — the same shape every other write in this codebase has — and a member never needs
 * permission to touch the owner's metadata just to say where the car is.
 */
@Serializable
data class SharedCarStateDto(
    val sessionId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val parkedAtMs: Long = 0L,
    val authorUid: String = "",
    val authorName: String? = null,
    val address: AddressDto? = null,
    val placeInfo: PlaceInfoDto? = null,
    /** False = the car has left; the position STAYS as the last known one, with its hour. */
    val isParked: Boolean = true,
    /**
     * Quién se ha llevado el coche, cuando no es quien lo aparcó.
     * [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
     *
     * ⛔ Existe porque [authorUid] responde a otra pregunta —quién lo DEJÓ ahí— y la salida puede
     * ser de otra persona. Sin este par, el aviso de «se lo han llevado» nombraría a quien aparcó y
     * se lo mandaría a quien está conduciendo.
     *
     * ⛔ No se cachea en Room a propósito: el único que lo lee es el reparto del servidor, y añadir
     * dos columnas obligaría a una migración para un dato que ninguna pantalla pide. Si algún día
     * la ficha del coche quiere decir «lo lleva Marta», ESE será el ticket que pague la migración.
     */
    val movedByUid: String? = null,
    val movedByName: String? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * `sharedCars/{carId}/sessions/{sessionId}` — one parking, as the group sees it.
 *
 * The facts, the FIGURES and the GEOMETRY all travel — the car's statistics belong to everyone who
 * drives it, and the route has to be stored for the author to be able to show it again later.
 * Whether it is SHOWN is decided on reading, from `members/{uid}.sharesRoutes`. What has no field
 * here at all — and therefore cannot travel by accident — is the detection mechanics:
 * `armEvidence`, `detectionPath`, `tripMaxSpeedMps`, the plate. [VEH-SHARED-001]
 */
@Serializable
data class SharedCarSessionDto(
    val sessionId: String = "",
    val authorUid: String = "",
    val authorName: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val parkedAtMs: Long = 0L,
    val endedAtMs: Long? = null,
    val isActive: Boolean = true,
    val address: AddressDto? = null,
    val placeInfo: PlaceInfoDto? = null,
    /** Figures — always. They are what the car's statistics are made of. */
    val routeDistanceMeters: Float? = null,
    val searchTimeMs: Long? = null,
    val publishedSpot: Boolean = false,
    /** Geometry — stored for every trip; shown according to its author's switch. */
    val routePolyline: String? = null,
    val routeSnapped: Boolean = false,
    val routeInferredSpans: String? = null,
    val updatedAtMs: Long = 0L,
)

/** `sharedCars/{carId}/members/{uid}` — written by that member, nobody else. */
@Serializable
data class SharedCarMemberDto(
    val uid: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val joinedAtMs: Long = 0L,
    val role: String = "MEMBER",
    /** Whether this person's routes are visible to the group. [VEH-SHARED-001] */
    val sharesRoutes: Boolean = true,
    /** The devices to tell when somebody parks this car. [SHARED-A-PARKING-TELLS-THE-GROUP-001] */
    val tokens: List<String> = emptyList(),
    /** The invite this membership was born from — the rules read it to verify the claim. */
    val inviteCode: String? = null,
)

/** `sharedCarInvites/{code}` — the code IS the document id, and the secret. */
@Serializable
data class SharedCarInviteDto(
    val code: String = "",
    val carId: String = "",
    val ownerUid: String = "",
    val carName: String = "",
    val createdAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val claimedByUid: String? = null,
)
