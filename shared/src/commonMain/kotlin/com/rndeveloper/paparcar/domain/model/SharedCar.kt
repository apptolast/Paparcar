package com.rndeveloper.paparcar.domain.model

/**
 * A car two to five people drive, seen from the group. [VEH-SHARED-001]
 *
 * ⛔ **It is a MIRROR, not a move.** The personal parking session
 * (`users/{uid}/parkingHistory/{id}`) stays the source of truth and keeps its route; this is the
 * projection the group is allowed to see. Nothing about a car that is not shared changes, and the
 * privacy of a personal trace costs nothing to keep because the mirror simply has nowhere to put
 * it — see [SharedCarSession]. Model decided 2026-09-08 (option B, "proyección").
 *
 * [carId] IS the owner's vehicle id: sharing re-keys nothing.
 *
 * @property btMacHash Group-scoped comparison token for the paired BT address, never the address
 *   itself. Lets a member's phone answer "is the device I just paired the same car?" without the
 *   MAC ever leaving the owner's device. See `sharedCarBtToken`.
 * @property sharingEnabled False pauses the mirror without dissolving the group (what an owner's
 *   Plus downgrade will flip once the RevenueCat webhook exists — stage 3). Reading never stops:
 *   losing Plus narrows what is NEW, it never deletes.
 * @property state Where the car is RIGHT NOW, whoever parked it — the reason the whole group,
 *   owner included, reads this document: if a member parked, the owner's own phone detected
 *   nothing and his personal history has nothing to say.
 */
data class SharedCar(
    val carId: String,
    val ownerUid: String,
    val ownerName: String? = null,
    /** Brand + model (or the user's name for it), snapshotted when the group was created. */
    val displayName: String,
    val btMacHash: String? = null,
    val sizeCategory: VehicleSize? = null,
    val carbodyType: CarbodyType? = null,
    val color: VehicleColor? = null,
    /**
     * What the car burns and how much, so the group's figures come out the SAME for everyone.
     * [VEH-SHARED-001]
     *
     * The car's spec travels; the PRICE does not. What a litre costs is each person's own setting
     * (or their country's average), so two members can honestly read different euros over the same
     * kilometres — the kilometres are the car's, the money is theirs.
     */
    val fuelType: FuelType? = null,
    val consumptionPer100: Float? = null,
    val consumptionElectricPer100: Float? = null,
    val sharingEnabled: Boolean = true,
    val createdAtMs: Long = 0L,
    /** The join code the owner has out right now, with its deadline. Null = none live. Only the
     *  owner's screen shows it; a member reading it changes nothing (they are already in). */
    val activeInviteCode: String? = null,
    val activeInviteExpiresAtMs: Long? = null,
    val state: SharedCarState? = null,
)

/**
 * The car's last known whereabouts, written by whoever parked it.
 *
 * ⛔ [isParked] false does NOT blank [location]: the car keeps its last known position with its
 * hour attached ("hace 3 h"). Asymmetric failure applies here too — a stale position the reader
 * can judge beats a car that vanishes off the map.
 */
data class SharedCarState(
    val sessionId: String,
    val location: GpsPoint,
    val parkedAtMs: Long,
    val authorUid: String,
    val authorName: String? = null,
    val address: AddressInfo? = null,
    val placeInfo: PlaceInfo? = null,
    val isParked: Boolean = true,
    val updatedAtMs: Long = 0L,
)

/**
 * One parking of the shared car, as the GROUP sees it. [VEH-SHARED-001]
 *
 * A shared car is measured exactly like any other — same recorder, same lanes — and what it
 * measured belongs to the group (user, 15-09). So the figures travel unconditionally and the
 * GEOMETRY travels when its author allows it:
 *
 * - **Always**: who, where, when, the address, and the figures ([routeDistanceMeters],
 *   [searchTimeMs], [publishedSpot]) that the car's statistics are made of.
 * - **The drawn route, always stored**: [routePolyline] and its provenance of shape travel too, and
 *   whether the group SEES them is decided on reading, from the author's current
 *   [SharedCarMember.sharesRoutes]. Storing it is what lets somebody turn the switch back on and get
 *   their old trips back; a per-session stamp would have frozen the answer for ever.
 * - **Never**: `armEvidence`, `detectionPath`, `tripMaxSpeedMps`, the plate. Detection mechanics and
 *   on-device data — no field for them here, and that part of the whitelist stays a TYPE.
 */
data class SharedCarSession(
    val sessionId: String,
    val authorUid: String,
    val authorName: String? = null,
    val location: GpsPoint,
    val parkedAtMs: Long,
    val endedAtMs: Long? = null,
    val isActive: Boolean = true,
    val address: AddressInfo? = null,
    val placeInfo: PlaceInfo? = null,
    /** Measured length of the drive that ended here. A FIGURE, so it always travels. */
    val routeDistanceMeters: Float? = null,
    /** Time this trip spent looking for a spot. Also a figure. */
    val searchTimeMs: Long? = null,
    /** Whether leaving this parking freed a spot for the community. */
    val publishedSpot: Boolean = false,
    /** The drawn route. Stored for every trip; SHOWN according to the author's switch. */
    val routePolyline: String? = null,
    val routeSnapped: Boolean = false,
    val routeInferredSpans: String? = null,
    val updatedAtMs: Long = 0L,
)

/** Who is in the group. One document per person, written by that person — see the ticket for why
 *  membership is a subcollection and not an array. */
data class SharedCarMember(
    val uid: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val joinedAtMs: Long = 0L,
    val role: SharedCarRole = SharedCarRole.MEMBER,
    /**
     * Whether this person's routes are SHOWN to the group. [VEH-SHARED-001]
     *
     * The one place the answer lives, and it is read live: a member who turns it back on gets every
     * one of their trips drawn again, because the geometry never stopped being recorded. That is
     * what a frozen per-session flag could not do.
     */
    val sharesRoutes: Boolean = true,
)

enum class SharedCarRole { OWNER, MEMBER }

/**
 * A one-shot, expiring ticket to join a shared car. The CODE is the secret: it is typed by hand
 * (deep links fail), so it is short, unambiguous and short-lived.
 */
data class SharedCarInvite(
    val code: String,
    val carId: String,
    val ownerUid: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val claimedByUid: String? = null,
)
