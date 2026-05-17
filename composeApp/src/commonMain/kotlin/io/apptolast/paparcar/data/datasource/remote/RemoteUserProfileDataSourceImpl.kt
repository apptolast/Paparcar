package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.data.datasource.remote.dto.VehicleDto
import io.apptolast.paparcar.domain.util.PaparcarLogger

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

    override suspend fun updateParkingSessionActiveFlag(userId: String, sessionId: String, isActive: Boolean) {
        parkingHistoryCollection(userId).document(sessionId).update(mapOf(FIELD_IS_ACTIVE to isActive))
    }

    override suspend fun updateParkingSessionLocation(
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
        runCatching {
            parkingHistoryCollection(userId)
                .get()
                .documents
                .mapNotNull { it.toParkingHistoryDto() }
        }.getOrElse { emptyList() }

    override suspend fun getVehicles(userId: String): List<VehicleDto> =
        runCatching {
            vehiclesCollection(userId).get().documents.map { it.data<VehicleDto>() }
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "getVehicles failed — userId=$userId", e)
            emptyList()
        }

    override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) {
        vehiclesCollection(userId).document(vehicle.id).set(vehicle)
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String) {
        vehiclesCollection(userId).document(vehicleId).delete()
    }

    override suspend fun updateVehicleDefaultFlag(userId: String, vehicleId: String, isDefault: Boolean) {
        vehiclesCollection(userId).document(vehicleId).update(mapOf(FIELD_IS_DEFAULT to isDefault))
    }

    override suspend fun deleteUserData(userId: String) {
        parkingHistoryCollection(userId).get().documents.forEach { it.reference.delete() }
        vehiclesCollection(userId).get().documents.forEach { it.reference.delete() }
        usersCollection().document(userId).delete()
    }

    // ─── Deserialization ──────────────────────────────────────────────────────

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

    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toParkingHistoryDto(): ParkingHistoryDto? =
        runCatching {
            val lat = get<Double?>(FIELD_LATITUDE) ?: return@runCatching null
            val lon = get<Double?>(FIELD_LONGITUDE) ?: return@runCatching null
            ParkingHistoryDto(
                id = id,
                userId = get<String?>(FIELD_USER_ID) ?: "",
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

        const val FIELD_IS_DEFAULT = "isDefault"
    }
}
