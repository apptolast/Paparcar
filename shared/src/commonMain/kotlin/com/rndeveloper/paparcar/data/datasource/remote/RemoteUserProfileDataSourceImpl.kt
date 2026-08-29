@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

class RemoteUserProfileDataSourceImpl(
    private val firestore: FirebaseFirestore,
) : RemoteUserProfileDataSource {

    private fun usersCollection() = firestore.collection(COLLECTION_USERS)
    private fun parkingHistoryCollection(userId: String) =
        usersCollection().document(userId).collection(COLLECTION_PARKING_HISTORY)

    private fun vehiclesCollection(userId: String) =
        usersCollection().document(userId).collection(COLLECTION_VEHICLES)

    override suspend fun getProfile(userId: String): UserProfileDto? =
        runCatching {
            val doc = usersCollection().document(userId).get()
            if (!doc.exists) return@runCatching null
            doc.toUserProfileDto()
        }.getOrNull()

    override suspend fun createOrUpdateProfile(profile: UserProfileDto) {
        usersCollection().document(profile.userId).set(profile)
    }

    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) {
        usersCollection().document(userId).update(mapOf(FIELD_DEFAULT_VEHICLE_ID to vehicleId))
    }

    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) {
        parkingHistoryCollection(userId).document(session.id).set(session)
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
    }

    override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> =
        parkingHistoryCollection(userId)
            .get()
            .documents
            .mapNotNull { it.toParkingHistoryDto() }

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
                spotId = get<String?>(FIELD_SPOT_ID),
                geofenceId = get<String?>(FIELD_GEOFENCE_ID),
                address = runCatching { get<AddressDto?>(FIELD_ADDRESS) }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>(FIELD_PLACE_INFO) }.getOrNull(),
                detectionReliability = runCatching { get<Double?>(FIELD_DETECTION_RELIABILITY)?.toFloat() }.getOrNull(),
                sizeCategory = get<String?>(FIELD_SIZE_CATEGORY),
                carbodyType = get<String?>(FIELD_CARBODY_TYPE),
                spotType = runCatching { get<String?>(FIELD_SPOT_TYPE) }.getOrNull(),
                armEvidence = runCatching { get<String?>(FIELD_ARM_EVIDENCE) }.getOrNull(),
                detectionPath = runCatching { get<String?>(FIELD_DETECTION_PATH) }.getOrNull(),
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
                endedAtMs = runCatching { get<Long?>(FIELD_ENDED_AT_MS) }.getOrNull()
                    ?: runCatching { get<Double?>(FIELD_ENDED_AT_MS)?.toLong() }.getOrNull(),
                publishedSpot = runCatching { get<Boolean?>(FIELD_PUBLISHED_SPOT) }.getOrNull() ?: false,
                updatedAt = getLongCompat(FIELD_UPDATED_AT),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toParkingHistoryDto failed — doc=$id", e)
            null
        }

    /** Reads a Long field tolerating Firestore's Double representation of integers. */
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.getLongCompat(field: String): Long =
        runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull()
            ?: 0L

    // ─── Constants ────────────────────────────────────────────────────────────

    private companion object {
        const val TAG = "RemoteUserProfileDataSource"

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
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_ACCURACY = "accuracy"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_IS_ACTIVE = "isActive"
        const val FIELD_SPOT_ID = "spotId"
        const val FIELD_GEOFENCE_ID = "geofenceId"
        const val FIELD_ADDRESS = "address"
        const val FIELD_PLACE_INFO = "placeInfo"
        const val FIELD_DETECTION_RELIABILITY = "detectionReliability"
        // Parking origin (auto / manual / home) — drives the history detail detection label. [HISTORY-DETAIL-001]
        const val FIELD_SPOT_TYPE = "spotType"
        // Pin provenance — the ARM trigger + the confirmation PATH that placed the parking.
        // [DET-PIN-PROVENANCE-001]
        const val FIELD_ARM_EVIDENCE = "armEvidence"
        const val FIELD_DETECTION_PATH = "detectionPath"
        const val FIELD_ROUTE_POLYLINE = "routePolyline"
        const val FIELD_ROUTE_SNAPPED = "routeSnapped"
        // Inferred-stretch provenance + user verdict. [ROUTE-GAP-HONEST-001]
        const val FIELD_ROUTE_INFERRED_SPANS = "routeInferredSpans"
        const val FIELD_ROUTE_INFERRED_RESOLUTION = "routeInferredResolution"
        // Close provenance + route length. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        const val FIELD_ROUTE_DISTANCE_METERS = "routeDistanceMeters"
        const val FIELD_ENDED_AT_MS = "endedAtMs"
        const val FIELD_PUBLISHED_SPOT = "publishedSpot"
    }
}
