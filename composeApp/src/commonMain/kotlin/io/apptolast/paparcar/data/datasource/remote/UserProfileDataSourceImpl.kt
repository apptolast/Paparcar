package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.domain.util.PaparcarLogger

class UserProfileDataSourceImpl(
    private val firestore: FirebaseFirestore,
) : UserProfileDataSource {

    private fun usersCollection() = firestore.collection(COLLECTION_USERS)
    private fun parkingHistoryCollection(userId: String) =
        usersCollection().document(userId).collection(COLLECTION_PARKING_HISTORY)

//FIXME: esto podria ser un flow con snapshot que este escuchando cambios de perfil
    override suspend fun getProfile(userId: String): UserProfileDto? =
        runCatching {
            val doc = usersCollection().document(userId).get()
            if (!doc.exists) return@runCatching null
            doc.toUserProfileDto()
        }.getOrNull()

    override suspend fun createOrUpdateProfile(profile: UserProfileDto) {
        usersCollection().document(profile.userId).set(profile)
    }

    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) {
        parkingHistoryCollection(userId).document(session.id).set(session)
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

    override suspend fun deleteUserData(userId: String) {
        parkingHistoryCollection(userId).get().documents.forEach { it.reference.delete() }
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
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>(FIELD_ACCURACY)?.toFloat() ?: 0f,
                timestamp = getLongCompat(FIELD_TIMESTAMP),
                isActive = get<Boolean?>(FIELD_IS_ACTIVE) ?: false,
                spotId = get<String?>(FIELD_SPOT_ID),
                geofenceId = get<String?>(FIELD_GEOFENCE_ID),
                address = runCatching { get<AddressDto?>(FIELD_ADDRESS) }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>(FIELD_PLACE_INFO) }.getOrNull(),
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
        const val TAG = "UserProfileDataSource"

        const val COLLECTION_USERS = "users"
        const val COLLECTION_PARKING_HISTORY = "parkingHistory"

        const val FIELD_USER_ID = "userId"
        const val FIELD_EMAIL = "email"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"

        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_ACCURACY = "accuracy"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_IS_ACTIVE = "isActive"
        const val FIELD_SPOT_ID = "spotId"
        const val FIELD_GEOFENCE_ID = "geofenceId"
        const val FIELD_ADDRESS = "address"
        const val FIELD_PLACE_INFO = "placeInfo"
    }
}
