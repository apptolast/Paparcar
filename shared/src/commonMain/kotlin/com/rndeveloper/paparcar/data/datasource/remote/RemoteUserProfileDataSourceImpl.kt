@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.withoutRouteGeometry
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toEntity
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

class RemoteUserProfileDataSourceImpl(
    private val firestore: FirebaseFirestore,
    // [PRIV-A-TRACE-IS-NOT-A-STAT-001] The ONE door every session push converges on (save worker,
    // pending drainer, iOS drainer, in-place mutations) -- so the local-first strip lives here and
    // no caller can forget it. Nullable so test doubles keep explicit behaviour.
    private val appPreferences: com.rndeveloper.paparcar.domain.preferences.AppPreferences? = null,
    // [VEH-SHARED-001] The group's copy hangs off the SAME door, and for the same reason: a second
    // write path is a second place to forget. Null = nobody shares anything (tests, and every
    // build before the mirror existed); the writer itself is a no-op for a car with no group.
    private val sharedCarMirror: com.rndeveloper.paparcar.domain.sharedcar.SharedCarMirror? = null,
) : RemoteUserProfileDataSource {

    private fun usersCollection() = firestore.collection(COLLECTION_USERS)
    private fun parkingHistoryCollection(userId: String) =
        usersCollection().document(userId).collection(COLLECTION_PARKING_HISTORY)

    private fun vehiclesCollection(userId: String) =
        usersCollection().document(userId).collection(COLLECTION_VEHICLES)

    override suspend fun getProfile(userId: String): UserProfileDto? =
        runCatching {
            val doc = usersCollection().document(userId).get()
            PaparcarLogger.i(AUTH_DIAG, "getProfile: users/{uid} exists=${doc.exists}")
            if (!doc.exists) return@runCatching null
            doc.toUserProfileDto()
        }
            // A null here reads as "new user" upstream, so a read or decode failure must say so.
            // [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
            .onFailure { e -> PaparcarLogger.e(AUTH_DIAG, "getProfile FAILED (read or decode)", e) }
            .getOrNull()

    override suspend fun createOrUpdateProfile(profile: UserProfileDto) {
        usersCollection().document(profile.userId).set(profile)
    }

    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) {
        usersCollection().document(userId).update(mapOf(FIELD_DEFAULT_VEHICLE_ID to vehicleId))
    }

    // Field-targeted like the one above, and for the same reason: a set(profile) here would push
    // the whole doc from whatever DTO the caller happens to hold.
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
    override suspend fun updateFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?) {
        usersCollection().document(userId).update(mapOf(FIELD_FIRST_STEPS_DISMISSED_AT to dismissedAtMs))
    }

    // Field-targeted for the same reason as the two above: a set(profile) would push the whole
    // doc from whatever DTO the caller happens to hold. [PROFILE-PHOTO-STUCK-AND-STALE-001]
    override suspend fun updateIdentityFields(
        userId: String,
        email: String?,
        displayName: String?,
        photoUrl: String?,
        updatedAt: Long,
    ) {
        usersCollection().document(userId).update(
            mapOf(
                FIELD_EMAIL to email,
                FIELD_DISPLAY_NAME to displayName,
                FIELD_PHOTO_URL to photoUrl,
                FIELD_UPDATED_AT to updatedAt,
            ),
        )
    }

    // Field-targeted like its three siblings above, and for the same reason. Four fields in ONE
    // update so a reader never sees a half-written snapshot (blockers from now, timestamp from
    // before). [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
    override suspend fun updateDetectionState(
        userId: String,
        autoDetectParking: Boolean,
        detectionBlockers: List<String>,
        batteryOptimizationExempt: Boolean,
        platform: String,
        appVersion: String,
        updatedAtMs: Long,
    ) {
        usersCollection().document(userId).update(
            mapOf(
                FIELD_AUTO_DETECT_PARKING to autoDetectParking,
                FIELD_DETECTION_BLOCKERS to detectionBlockers,
                FIELD_PLATFORM to platform,
                FIELD_APP_VERSION to appVersion,
                FIELD_BATTERY_OPTIMIZATION_EXEMPT to batteryOptimizationExempt,
                FIELD_DETECTION_STATE_UPDATED_AT to updatedAtMs,
            ),
        )
    }

    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) {
        // [PRIV-A-TRACE-IS-NOT-A-STAT-001] A trace is not a stat: without the user's cloud-routes
        // consent the FIGURES travel (distance, search time) and the GEOMETRY stays home. Applied
        // at this single door; the full-document set() also scrubs any previously-synced geometry
        // of this session on its next push, which is the retention promise keeping itself.
        val outgoing = if (appPreferences?.syncRouteGeometry == true) session else session.withoutRouteGeometry()
        parkingHistoryCollection(userId).document(session.id).set(outgoing)
        // ⛔ AFTER the personal write and never in its place: the mirror is a projection of a
        // record that already exists.
        //
        // It mirrors `session`, not `outgoing`, and that is deliberate: the strip above answers
        // "what does the USER sync of their own trace", and the group's copy answers a different
        // question with a different switch — `shareRoutesInGroup`, applied inside
        // `sharedCarMirrorPayload()`. Each door asks its own permission and neither uploads without
        // it. [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001]
        //
        // ⛔ This comment used to say the mirror "never carries geometry at all (its type has no
        // field for it)". That was true before F8 and false after it — `SharedCarSessionDto` has
        // carried `routePolyline` since. Passing `session` here is safe because of the gate, not
        // because of the type. [VEH-SHARED-001]
        sharedCarMirror?.onSessionSaved(session.toEntity().toDomain())
    }

    override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) {
        runCatching {
            parkingHistoryCollection(userId).document(sessionId).update(mapOf(FIELD_IS_ACTIVE to false))
        }.onFailure {
            // NOT_FOUND: the previous session was never synced to Firestore (offline when
            // parking was confirmed). The isActive flag is non-critical for a document that
            // doesn't exist remotely — no point reporting this as a non-fatal.
            PaparcarLogger.w(TAG, "clearParkingSessionActiveFlag skipped — session not in Firestore: $sessionId")
        }
        // The group is told the car left, but NOT where it went: the last known position stays put
        // with its hour. [VEH-SHARED-001]
        sharedCarMirror?.onSessionClosed(sessionId, endedAtMs = nowMs())
    }

    override suspend fun updateParkingSessionAddressAndPlace(
        userId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
    ) {
        val updates = buildMap {
            put(FIELD_ADDRESS, address)
            put(FIELD_PLACE_INFO, placeInfo)
        }
        parkingHistoryCollection(userId).document(sessionId).update(updates)
        sharedCarMirror?.onAddressResolved(
            sessionId = sessionId,
            address = address?.toDomain(),
            placeInfo = placeInfo?.toDomain(),
        )
    }

    override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> =
        parkingHistoryCollection(userId)
            .get()
            .documents
            .mapNotNull { it.toParkingHistoryDto() }

    // Filtered client-side rather than with a `where` clause: the collection is one user's own
    // history (already fetched whole by getParkingHistory) and a server-side filter would need an
    // index for a once-in-a-while delete. [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]
    override suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String) {
        parkingHistoryCollection(userId).get().documents
            .filter { runCatching { it.get<String?>(FIELD_VEHICLE_ID) }.getOrNull() == vehicleId }
            .forEach { it.reference.delete() }
    }

    override suspend fun getVehicles(userId: String): List<VehicleDto> =
        vehiclesCollection(userId).get().documents.mapNotNull { doc ->
            doc.toVehicleDto()
        }

    // Every vehicle write stamps updatedAt (client epoch ms) so the inbound sync's Last-Write-Wins
    // merge can tell when the server has caught up with a local pending edit. [SYNC-RECONCILE-001]
    override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) {
        vehiclesCollection(userId).document(vehicle.id).set(vehicle.copy(updatedAt = nowMs()))
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String) {
        vehiclesCollection(userId).document(vehicleId).delete()
    }

    override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) {
        vehiclesCollection(userId).document(vehicleId)
            .update(mapOf(FIELD_IS_ACTIVE to isActive, FIELD_UPDATED_AT to nowMs()))
    }

    override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) {
        vehiclesCollection(userId).document(vehicleId)
            .update(mapOf(FIELD_BLUETOOTH_DEVICE_ID to deviceAddress, FIELD_UPDATED_AT to nowMs()))
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    override suspend fun deleteUserData(userId: String) {
        parkingHistoryCollection(userId).get().documents.forEach { it.reference.delete() }
        vehiclesCollection(userId).get().documents.forEach { it.reference.delete() }
        usersCollection().document(userId).delete()
    }

    // ─── Deserialization ──────────────────────────────────────────────────────
    // [IMPORTANT] Use field-by-field get<T?>() to avoid 'Any' serialiser errors.

    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toUserProfileDto(): UserProfileDto? =
        runCatching {
            UserProfileDto(
                userId = get<String?>(FIELD_USER_ID) ?: return@runCatching null,
                email = get<String?>(FIELD_EMAIL),
                displayName = get<String?>(FIELD_DISPLAY_NAME),
                photoUrl = get<String?>(FIELD_PHOTO_URL),
                createdAt = getLongCompat(FIELD_CREATED_AT),
                updatedAt = getLongCompat(FIELD_UPDATED_AT),
                defaultVehicleId = runCatching { get<String?>(FIELD_DEFAULT_VEHICLE_ID) }.getOrNull(),
                // Absent on every doc written before the tutorial became an account fact → null.
                // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
                firstStepsDismissedAt = getLongOrNullCompat(FIELD_FIRST_STEPS_DISMISSED_AT),
                // Detection state — read back only so a full-document write can never drop it; the
                // app itself never consumes these. Absent before this ticket → null → unknown,
                // NOT "off". [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
                autoDetectParking = runCatching { get<Boolean?>(FIELD_AUTO_DETECT_PARKING) }.getOrNull(),
                detectionBlockers = runCatching { get<List<String>?>(FIELD_DETECTION_BLOCKERS) }.getOrNull(),
                batteryOptimizationExempt = runCatching { get<Boolean?>(FIELD_BATTERY_OPTIMIZATION_EXEMPT) }.getOrNull(),
                detectionStateUpdatedAt = getLongOrNullCompat(FIELD_DETECTION_STATE_UPDATED_AT),
                // Misma razón que los cuatro de arriba: se leen para que un `set(profile)` no los
                // borre, y nadie en la app los consume. [DIAG-A-ROW-MUST-SAY-WHICH-PLATFORM-WROTE-IT-001]
                platform = runCatching { get<String?>(FIELD_PLATFORM) }.getOrNull(),
                appVersion = runCatching { get<String?>(FIELD_APP_VERSION) }.getOrNull(),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toUserProfileDto failed — doc=$id", e)
            null
        }

    // Field-by-field read so every property in [VehicleDto] is covered. Adding a new
    // field to the DTO requires extending this block too — otherwise the value falls back
    // to the DTO default on read and the in-memory Vehicle silently loses it after sync.
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toVehicleDto(): VehicleDto? =
        runCatching {
            VehicleDto(
                id = id,
                userId = get<String?>(FIELD_USER_ID) ?: return@runCatching null,
                name = get<String?>(FIELD_NAME),
                brand = get<String?>(FIELD_BRAND),
                model = get<String?>(FIELD_MODEL),
                sizeCategory = get<String?>(FIELD_SIZE_CATEGORY).orEmpty(),
                carbodyType = get<String?>(FIELD_CARBODY_TYPE).orEmpty(),
                vehicleType = get<String?>(FIELD_VEHICLE_TYPE).orEmpty(),
                bluetoothDeviceId = get<String?>(FIELD_BLUETOOTH_DEVICE_ID),
                showBrandModelOnSpot = get<Boolean?>(FIELD_SHOW_BRAND_MODEL_ON_SPOT) ?: false,
                isActive = get<Boolean?>(FIELD_IS_ACTIVE) ?: false,
                color = get<String?>(FIELD_COLOR).orEmpty(),
                // Fuel block — defensive: absent on every doc written before VEH-FUEL-001.
                fuelType = runCatching { get<String?>(FIELD_FUEL_TYPE) }.getOrNull().orEmpty(),
                consumptionPer100 = runCatching { get<Double?>(FIELD_CONSUMPTION_PER_100)?.toFloat() }.getOrNull(),
                consumptionElectricPer100 = runCatching { get<Double?>(FIELD_CONSUMPTION_ELECTRIC_PER_100)?.toFloat() }.getOrNull(),
                heightMeters = runCatching { get<Double?>(FIELD_HEIGHT_METERS)?.toFloat() }.getOrNull(),
                // Price block — absent on every doc written before FUEL-PRICE-RESOLVER-001.
                fuelPricePerLiter = runCatching { get<Double?>(FIELD_FUEL_PRICE_PER_LITER) }.getOrNull(),
                energyPricePerKwh = runCatching { get<Double?>(FIELD_ENERGY_PRICE_PER_KWH) }.getOrNull(),
                priceCurrency = runCatching { get<String?>(FIELD_PRICE_CURRENCY) }.getOrNull(),
                energyLabel = runCatching { get<String?>(FIELD_ENERGY_LABEL) }.getOrNull().orEmpty(),
                // Shared-car link — absent on every doc written before VEH-SHARED-001, and absent
                // forever on a car nobody shares. Read back so a reinstall rejoins the group
                // instead of quietly becoming a lone car with the same MAC. [VEH-SHARED-001]
                sharedCarId = runCatching { get<String?>(FIELD_SHARED_CAR_ID) }.getOrNull(),
                ownerUid = runCatching { get<String?>(FIELD_OWNER_UID) }.getOrNull(),
                // Absent on every doc written before the switch existed → true, which is the
                // default the switch itself has. [VEH-SHARED-001]
                shareRoutesInGroup = runCatching { get<Boolean?>(FIELD_SHARE_ROUTES_IN_GROUP) }.getOrNull() ?: true,
                updatedAt = getLongCompat(FIELD_UPDATED_AT),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toVehicleDto failed — doc=$id", e)
            null
        }

    // Field-by-field read so every property in [ParkingHistoryDto] is covered. Same
    // parity rule as [toVehicleDto]: missing reads silently lose data after Firestore sync.
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toParkingHistoryDto(): ParkingHistoryDto? =
        runCatching {
            val lat = get<Double?>(FIELD_LATITUDE) ?: return@runCatching null
            val lon = get<Double?>(FIELD_LONGITUDE) ?: return@runCatching null
            ParkingHistoryDto(
                id = id,
                userId = get<String?>(FIELD_USER_ID).orEmpty(),
                vehicleId = runCatching { get<String?>(FIELD_VEHICLE_ID) }.getOrNull(),
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>(FIELD_ACCURACY)?.toFloat() ?: 0f,
                timestamp = getLongCompat(FIELD_TIMESTAMP),
                isActive = get<Boolean?>(FIELD_IS_ACTIVE) ?: false,
                geofenceId = get<String?>(FIELD_GEOFENCE_ID),
                address = runCatching { get<AddressDto?>(FIELD_ADDRESS) }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>(FIELD_PLACE_INFO) }.getOrNull(),
                detectionReliability = runCatching { get<Double?>(FIELD_DETECTION_RELIABILITY)?.toFloat() }.getOrNull(),
                sizeCategory = get<String?>(FIELD_SIZE_CATEGORY),
                carbodyType = get<String?>(FIELD_CARBODY_TYPE),
                spotType = runCatching { get<String?>(FIELD_SPOT_TYPE) }.getOrNull(),
                armEvidence = runCatching { get<String?>(FIELD_ARM_EVIDENCE) }.getOrNull(),
                decisionEvidence = runCatching { get<String?>(FIELD_DECISION_EVIDENCE) }.getOrNull(),
                detectionPath = runCatching { get<String?>(FIELD_DETECTION_PATH) }.getOrNull(),
                // [DET-DOUBT-REACHES-REMOTE-001] Absent on every doc written before this ticket →
                // null → an exact pin, which is exactly what those pins were.
                zoneRadiusMeters = runCatching { get<Float?>(FIELD_ZONE_RADIUS_METERS) }.getOrNull(),
                // The driven route (encoded polyline) — read back so a new device renders the trip.
                // Defensive: absent on legacy docs written before DET-ROUTE-TRACK-001. [DET-ROUTE-TRACK-001]
                routePolyline = runCatching { get<String?>(FIELD_ROUTE_POLYLINE) }.getOrNull(),
                // Whether that route is the final on-road line. Defensive: legacy docs default false
                // (raw/absent → history treats null polyline as "no route"). [DET-ROUTE-SNAP-STORE-001]
                routeSnapped = runCatching { get<Boolean?>(FIELD_ROUTE_SNAPPED) }.getOrNull() ?: false,
                // Provenance of road-inferred stretches + the user's verdict on them. Defensive:
                // absent on docs written before ROUTE-GAP-HONEST-001. [ROUTE-GAP-HONEST-001]
                routeInferredSpans = runCatching { get<String?>(FIELD_ROUTE_INFERRED_SPANS) }.getOrNull(),
                routeInferredResolution = runCatching { get<String?>(FIELD_ROUTE_INFERRED_RESOLUTION) }.getOrNull(),
                // Close provenance + route length. Defensive: absent on docs written before
                // VEH-STATS-SAY-SOMETHING-USEFUL-001 (distance self-heals in the inbound mapper).
                routeDistanceMeters = runCatching { get<Double?>(FIELD_ROUTE_DISTANCE_METERS)?.toFloat() }.getOrNull(),
                communitySpotId = runCatching { get<String?>(FIELD_COMMUNITY_SPOT_ID) }.getOrNull(),
                searchTimeMs = runCatching { get<Long?>(FIELD_SEARCH_TIME_MS) }.getOrNull(),
                endedAtMs = runCatching { get<Long?>(FIELD_ENDED_AT_MS) }.getOrNull()
                    ?: runCatching { get<Double?>(FIELD_ENDED_AT_MS)?.toLong() }.getOrNull(),
                publishedSpot = runCatching { get<Boolean?>(FIELD_PUBLISHED_SPOT) }.getOrNull() ?: false,
                retractedAtMs = runCatching { get<Long?>(FIELD_RETRACTED_AT_MS) }.getOrNull(),
                updatedAt = getLongCompat(FIELD_UPDATED_AT),
                platform = runCatching { get<String?>(FIELD_PLATFORM) }.getOrNull(),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toParkingHistoryDto failed — doc=$id", e)
            null
        }

    /** Reads a Long field tolerating Firestore's Double representation of integers. */
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.getLongCompat(field: String): Long =
        getLongOrNullCompat(field) ?: 0L

    /** Same tolerance, but keeping the ABSENT case distinguishable from a real 0 — for fields whose
     *  null carries meaning ("never happened") rather than a default. */
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.getLongOrNullCompat(field: String): Long? =
        runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull()

    // ─── Constants ────────────────────────────────────────────────────────────

    private companion object {
        const val TAG = "RemoteUserProfileDataSource"
        const val AUTH_DIAG = "AUTHDIAG/RemoteUserProfile"

        const val COLLECTION_USERS = "users"
        const val COLLECTION_PARKING_HISTORY = "parkingHistory"
        const val COLLECTION_VEHICLES = "vehicles"

        const val FIELD_USER_ID = "userId"
        const val FIELD_EMAIL = "email"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_DEFAULT_VEHICLE_ID = "defaultVehicleId"
        // The tutorial's one account-scoped fact. [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
        const val FIELD_FIRST_STEPS_DISMISSED_AT = "firstStepsDismissedAt"

        // Whether detection can run for this account. [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
        const val FIELD_AUTO_DETECT_PARKING = "autoDetectParking"
        const val FIELD_DETECTION_BLOCKERS = "detectionBlockers"
        const val FIELD_PLATFORM = "platform"
        const val FIELD_APP_VERSION = "appVersion"
        const val FIELD_BATTERY_OPTIMIZATION_EXEMPT = "batteryOptimizationExempt"
        const val FIELD_DETECTION_STATE_UPDATED_AT = "detectionStateUpdatedAt"

        const val FIELD_VEHICLE_ID = "vehicleId"
        const val FIELD_NAME = "name"
        const val FIELD_BRAND = "brand"
        const val FIELD_MODEL = "model"
        const val FIELD_SIZE_CATEGORY = "sizeCategory"
        const val FIELD_CARBODY_TYPE = "carbodyType"
        const val FIELD_VEHICLE_TYPE = "vehicleType"
        const val FIELD_COLOR = "color"
        const val FIELD_BLUETOOTH_DEVICE_ID = "bluetoothDeviceId"
        const val FIELD_SHOW_BRAND_MODEL_ON_SPOT = "showBrandModelOnSpot"
        // Fuel block. [VEH-FUEL-001]
        const val FIELD_FUEL_TYPE = "fuelType"
        const val FIELD_CONSUMPTION_PER_100 = "consumptionPer100"
        const val FIELD_CONSUMPTION_ELECTRIC_PER_100 = "consumptionElectricPer100"
        const val FIELD_HEIGHT_METERS = "heightMeters"
        const val FIELD_FUEL_PRICE_PER_LITER = "fuelPricePerLiter"
        const val FIELD_ENERGY_PRICE_PER_KWH = "energyPricePerKwh"
        const val FIELD_PRICE_CURRENCY = "priceCurrency"
        const val FIELD_ENERGY_LABEL = "energyLabel"
        // Shared-car link. [VEH-SHARED-001]
        const val FIELD_SHARED_CAR_ID = "sharedCarId"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_SHARE_ROUTES_IN_GROUP = "shareRoutesInGroup"
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_ACCURACY = "accuracy"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_IS_ACTIVE = "isActive"
        const val FIELD_GEOFENCE_ID = "geofenceId"
        const val FIELD_ADDRESS = "address"
        const val FIELD_PLACE_INFO = "placeInfo"
        const val FIELD_DETECTION_RELIABILITY = "detectionReliability"
        // Parking origin (auto / manual / home) — drives the history detail detection label. [HISTORY-DETAIL-001]
        const val FIELD_SPOT_TYPE = "spotType"
        // Pin provenance — the ARM trigger + the confirmation PATH that placed the parking.
        // [DET-PIN-PROVENANCE-001]
        const val FIELD_ARM_EVIDENCE = "armEvidence"
        /** [DET-A-WALK-IS-NOT-AN-EGRESS-001] Con qué números decidió la rama que pinó. */
        const val FIELD_DECISION_EVIDENCE = "decisionEvidence"
        const val FIELD_DETECTION_PATH = "detectionPath"
        const val FIELD_ZONE_RADIUS_METERS = "zoneRadiusMeters"
        const val FIELD_ROUTE_POLYLINE = "routePolyline"
        const val FIELD_ROUTE_SNAPPED = "routeSnapped"
        // Inferred-stretch provenance + user verdict. [ROUTE-GAP-HONEST-001]
        const val FIELD_ROUTE_INFERRED_SPANS = "routeInferredSpans"
        const val FIELD_ROUTE_INFERRED_RESOLUTION = "routeInferredResolution"
        // Close provenance + route length. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        const val FIELD_ROUTE_DISTANCE_METERS = "routeDistanceMeters"
        const val FIELD_COMMUNITY_SPOT_ID = "communitySpotId"
        const val FIELD_SEARCH_TIME_MS = "searchTimeMs"
        const val FIELD_ENDED_AT_MS = "endedAtMs"
        const val FIELD_PUBLISHED_SPOT = "publishedSpot"
        const val FIELD_RETRACTED_AT_MS = "retractedAtMs"
    }
}
