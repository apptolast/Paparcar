package com.rndeveloper.paparcar.data.datasource.remote

import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarInviteDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarMemberDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarSessionDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarStateDto
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * `sharedCars/{carId}` and `sharedCarInvites/{code}`. [VEH-SHARED-001]
 *
 * ⛔ Every observe here can legitimately start FAILING mid-flight: the owner can dissolve the group
 * or show a member the door while their phone is watching, and Firestore answers a revoked listener
 * with PERMISSION_DENIED. That is not an error to surface — it is the answer "you are not in this
 * group any more", and it arrives as `null` / empty so the repository can clean up the linked row.
 */
class RemoteSharedCarDataSourceImpl(
    private val firestore: FirebaseFirestore,
) : RemoteSharedCarDataSource {

    private fun cars() = firestore.collection(COLLECTION_SHARED_CARS)
    private fun car(carId: String) = cars().document(carId)
    private fun members(carId: String) = car(carId).collection(COLLECTION_MEMBERS)
    private fun sessions(carId: String) = car(carId).collection(COLLECTION_SESSIONS)
    private fun state(carId: String) = car(carId).collection(COLLECTION_STATE).document(DOC_CURRENT)
    private fun invites() = firestore.collection(COLLECTION_INVITES)

    override fun observeCar(carId: String): Flow<SharedCarDto?> =
        car(carId).snapshots
            .map { snapshot -> if (snapshot.exists) snapshot.toSharedCarDto() else null }
            .catchAsGone("observeCar", carId, null)

    override fun observeState(carId: String): Flow<SharedCarStateDto?> =
        state(carId).snapshots
            .map { snapshot -> if (snapshot.exists) snapshot.toSharedCarStateDto() else null }
            .catchAsGone("observeState", carId, null)

    override fun observeMembers(carId: String): Flow<List<SharedCarMemberDto>> =
        members(carId).snapshots
            .map { snapshot -> snapshot.documents.mapNotNull { it.toSharedCarMemberDto() } }
            .catchAsGone("observeMembers", carId, emptyList())

    override fun observeSessions(carId: String): Flow<List<SharedCarSessionDto>> =
        sessions(carId).snapshots
            .map { snapshot -> snapshot.documents.mapNotNull { it.toSharedCarSessionDto() } }
            .catchAsGone("observeSessions", carId, emptyList())

    override suspend fun getCar(carId: String): SharedCarDto? =
        runCatching { car(carId).get().takeIf { it.exists }?.toSharedCarDto() }.getOrNull()

    override suspend fun getMembers(carId: String): List<SharedCarMemberDto> =
        runCatching { members(carId).get().documents.mapNotNull { it.toSharedCarMemberDto() } }
            .getOrElse { emptyList() }

    override suspend fun createCar(car: SharedCarDto, owner: SharedCarMemberDto) {
        // The owner's membership travels with the creation: a group whose own owner is not a member
        // would be a group nobody can read, because reading is "am I in members?".
        car(car.carId).set(car)
        members(car.carId).document(owner.uid).set(owner)
    }

    override suspend fun deleteCar(carId: String) {
        // Subcollections first: an orphaned `members` doc under a deleted car would keep granting
        // reads on paths the parent no longer governs.
        runCatching { sessions(carId).get().documents.forEach { it.reference.delete() } }
        // ⛔ `state` ANTES que `members`: la cabecera es lo ÚNICO que lleva la última posición del
        // coche, y su regla se apoya en el coche y en la pertenencia. Borrar antes a los miembros y
        // luego intentar la cabecera es quedarse sin la llave con la puerta todavía abierta.
        //
        // ⛔ Y su fallo se REGISTRA: si esto no borra, la posición del coche sobrevive a la
        // disolución del grupo y nadie se entera. Un `runCatching` mudo es como estuvieron
        // huérfanas hasta que una revisión las encontró.
        // [PRIV-A-DISSOLVED-GROUP-LEAVES-ITS-HEAD-001]
        runCatching { state(carId).delete() }
            .onFailure { PaparcarLogger.e(TAG, "no se pudo borrar la cabecera de $carId — queda huérfana", it) }
        runCatching { members(carId).get().documents.forEach { it.reference.delete() } }
        deleteInvitesFor(carId)
        car(carId).delete()
    }

    override suspend fun getState(carId: String): SharedCarStateDto? =
        runCatching { state(carId).get().takeIf { it.exists }?.toSharedCarStateDto() }.getOrNull()

    override suspend fun putState(carId: String, state: SharedCarStateDto) {
        state(carId).set(state)
    }

    override suspend fun setStateParked(carId: String, isParked: Boolean, updatedAtMs: Long) {
        runCatching {
            state(carId).update(
                mapOf(
                    FIELD_IS_PARKED to isParked,
                    FIELD_UPDATED_AT_MS to updatedAtMs,
                    // ⛔ Y se limpia quién se lo llevó la vez anterior. Un `movedBy` viejo colgando
                    // de un aparcamiento nuevo haría que la siguiente salida nombrase a quien
                    // condujo hace dos viajes. [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
                    FIELD_MOVED_BY_UID to null,
                    FIELD_MOVED_BY_NAME to null,
                ),
            )
        }.onFailure {
            PaparcarLogger.w(TAG, "setStateParked skipped — no state document yet for $carId")
        }
    }

    override suspend fun setStateTakenBy(carId: String, uid: String, name: String?, updatedAtMs: Long) {
        runCatching {
            state(carId).update(
                mapOf(
                    FIELD_IS_PARKED to false,
                    FIELD_MOVED_BY_UID to uid,
                    FIELD_MOVED_BY_NAME to name,
                    FIELD_UPDATED_AT_MS to updatedAtMs,
                ),
            )
        }.onFailure {
            PaparcarLogger.w(TAG, "setStateTakenBy skipped — no state document yet for $carId")
        }
    }

    override suspend fun putSession(carId: String, session: SharedCarSessionDto) {
        sessions(carId).document(session.sessionId).set(session)
    }

    override suspend fun closeSession(carId: String, sessionId: String, endedAtMs: Long, updatedAtMs: Long) {
        runCatching {
            sessions(carId).document(sessionId).update(
                mapOf(
                    FIELD_IS_ACTIVE to false,
                    FIELD_ENDED_AT_MS to endedAtMs,
                    FIELD_UPDATED_AT_MS to updatedAtMs,
                ),
            )
        }.onFailure {
            // NOT_FOUND: the parking was never mirrored (offline at park time, or the car was
            // shared after it). Nothing to close, and nothing worth a crash report.
            PaparcarLogger.w(TAG, "closeSession skipped — mirror session absent: $sessionId")
        }
    }

    override suspend fun updateSessionAddress(
        carId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
        updatedAtMs: Long,
    ) {
        runCatching {
            sessions(carId).document(sessionId).update(
                mapOf(
                    FIELD_ADDRESS to address,
                    FIELD_PLACE_INFO to placeInfo,
                    FIELD_UPDATED_AT_MS to updatedAtMs,
                ),
            )
        }.onFailure {
            PaparcarLogger.w(TAG, "updateSessionAddress skipped — mirror session absent: $sessionId")
        }
    }

    override suspend fun putMember(carId: String, member: SharedCarMemberDto) {
        members(carId).document(member.uid).set(member)
    }

    override suspend fun setMemberTokens(carId: String, uid: String, tokens: List<String>) {
        // Field-targeted, como el flag de rutas: el documento de miembro lo mantiene su dueño y un
        // `set()` aquí lo reescribiría entero desde lo que este móvil tuviera a mano.
        runCatching { members(carId).document(uid).update(mapOf(FIELD_TOKENS to tokens)) }
            .onFailure { PaparcarLogger.w(TAG, "setMemberTokens skipped — not a member of $carId") }
    }

    override suspend fun setMemberSharesRoutes(carId: String, uid: String, sharesRoutes: Boolean) {
        // Field-targeted: the flag changes from a switch, and a set() here would rewrite a
        // membership document from whatever the screen happened to be holding.
        runCatching { members(carId).document(uid).update(mapOf(FIELD_SHARES_ROUTES to sharesRoutes)) }
            .onFailure { PaparcarLogger.w(TAG, "setMemberSharesRoutes skipped — not a member of $carId") }
    }

    override suspend fun clearMemberFromState(carId: String, uid: String) {
        val current = getState(carId) ?: return
        // Field-targeted, y solo los campos que son SUYOS: un `set()` aquí reescribiría la posición
        // del coche con lo que este móvil tuviera en la mano, que es de todo el grupo.
        val fields = buildMap<String, Any?> {
            if (current.authorUid == uid) put(FIELD_AUTHOR_NAME, null)
            if (current.movedByUid == uid) {
                put(FIELD_MOVED_BY_UID, null)
                put(FIELD_MOVED_BY_NAME, null)
            }
        }
        // Ni un `updatedAtMs`: el coche no se ha movido. Firestore empuja el cambio a los que
        // escuchan igual, así que sellarlo solo mentiría sobre cuándo cambió el estado del coche.
        if (fields.isEmpty()) return
        runCatching { state(carId).update(fields) }
            .onFailure { PaparcarLogger.w(TAG, "clearMemberFromState skipped on $carId — ${it.message}") }
    }

    override suspend fun deleteSessionsOf(carId: String, uid: String) {
        // Read-then-delete over the whole collection instead of a `where` query: the group's
        // history is capped and small, and the read is the same one `observeSessions` already
        // does. What must not happen is deleting somebody else's row, so the author is checked
        // here and again by the rules. [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]
        runCatching {
            sessions(carId).get().documents
                .filter { runCatching { it.get<String?>(FIELD_AUTHOR_UID) }.getOrNull() == uid }
                .forEach { it.reference.delete() }
        }.onFailure { PaparcarLogger.w(TAG, "deleteSessionsOf skipped on $carId — ${it.message}") }
    }

    override suspend fun deleteMember(carId: String, uid: String) {
        members(carId).document(uid).delete()
    }

    override suspend fun replaceInvite(carId: String, invite: SharedCarInviteDto) {
        firestore.runTransaction {
            val carSnapshot = get(car(carId))
            val previousCode = carSnapshot.takeIf { it.exists }?.get<String?>(FIELD_INVITE_CODE)
            if (previousCode != null && previousCode != invite.code) {
                delete(invites().document(previousCode))
            }
            set(invites().document(invite.code), invite)
            update(car(carId), mapOf(FIELD_INVITE_CODE to invite.code, FIELD_INVITE_EXPIRES_AT_MS to invite.expiresAtMs))
        }
    }

    override suspend fun getInvite(code: String): SharedCarInviteDto? =
        runCatching { invites().document(code).get().takeIf { it.exists }?.toSharedCarInviteDto() }
            .getOrNull()

    override suspend fun claimInvite(code: String, uid: String) {
        invites().document(code).update(mapOf(FIELD_CLAIMED_BY_UID to uid))
    }

    override suspend fun deleteInvitesFor(carId: String) {
        // An invite is readable one code at a time by whoever holds the code — the rules forbid
        // listing the collection on purpose. So the outstanding code is tracked where it CAN be
        // found again: on the car document that spawned it. One live code per car, always the
        // last one, and dissolving the group takes it with it.
        val code = getCar(carId)?.inviteCode ?: return
        runCatching { invites().document(code).delete() }
            .onFailure { PaparcarLogger.w(TAG, "deleteInvitesFor: no se pudo borrar el invite $code de $carId", it) }
        runCatching { car(carId).update(mapOf(FIELD_INVITE_CODE to null, FIELD_INVITE_EXPIRES_AT_MS to null)) }
            .onFailure { PaparcarLogger.w(TAG, "deleteInvitesFor: no se pudo limpiar el puntero de $carId", it) }
    }

    // ─── Deserialization ──────────────────────────────────────────────────────
    // Field-by-field get<T?>() like every other manual deserializer in this layer, and guarded by
    // [FirestoreDeserializerParityTest] — a field added to a DTO without a read here would fall
    // back to its default in silence. [feedback_dto_field_parity]

    private fun DocumentSnapshot.toSharedCarDto(): SharedCarDto? =
        runCatching {
            SharedCarDto(
                carId = get<String?>(FIELD_CAR_ID) ?: id,
                ownerUid = get<String?>(FIELD_OWNER_UID) ?: return@runCatching null,
                ownerName = get<String?>(FIELD_OWNER_NAME),
                displayName = get<String?>(FIELD_DISPLAY_NAME).orEmpty(),
                btMacHash = get<String?>(FIELD_BT_MAC_HASH),
                sizeCategory = get<String?>(FIELD_SIZE_CATEGORY),
                carbodyType = get<String?>(FIELD_CARBODY_TYPE),
                color = get<String?>(FIELD_COLOR),
                sharingEnabled = get<Boolean?>(FIELD_SHARING_ENABLED) ?: true,
                createdAtMs = getLongCompat(FIELD_CREATED_AT_MS),
                fuelType = get<String?>(FIELD_FUEL_TYPE),
                consumptionPer100 = get<Double?>(FIELD_CONSUMPTION_PER_100)?.toFloat(),
                consumptionElectricPer100 = get<Double?>(FIELD_CONSUMPTION_ELECTRIC_PER_100)?.toFloat(),
                inviteCode = get<String?>(FIELD_INVITE_CODE),
                inviteExpiresAtMs = getLongOrNullCompat(FIELD_INVITE_EXPIRES_AT_MS),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSharedCarDto failed — doc=$id", e)
            null
        }

    private fun DocumentSnapshot.toSharedCarStateDto(): SharedCarStateDto? =
        runCatching {
            val lat = get<Double?>(FIELD_LATITUDE) ?: return@runCatching null
            val lon = get<Double?>(FIELD_LONGITUDE) ?: return@runCatching null
            SharedCarStateDto(
                sessionId = get<String?>(FIELD_SESSION_ID).orEmpty(),
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>(FIELD_ACCURACY)?.toFloat() ?: 0f,
                parkedAtMs = getLongCompat(FIELD_PARKED_AT_MS),
                authorUid = get<String?>(FIELD_AUTHOR_UID).orEmpty(),
                authorName = get<String?>(FIELD_AUTHOR_NAME),
                address = runCatching { get<AddressDto?>(FIELD_ADDRESS) }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>(FIELD_PLACE_INFO) }.getOrNull(),
                isParked = get<Boolean?>(FIELD_IS_PARKED) ?: false,
                movedByUid = get<String?>(FIELD_MOVED_BY_UID),
                movedByName = get<String?>(FIELD_MOVED_BY_NAME),
                updatedAtMs = getLongCompat(FIELD_UPDATED_AT_MS),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSharedCarStateDto failed — doc=$id", e)
            null
        }

    private fun DocumentSnapshot.toSharedCarSessionDto(): SharedCarSessionDto? =
        runCatching {
            val lat = get<Double?>(FIELD_LATITUDE) ?: return@runCatching null
            val lon = get<Double?>(FIELD_LONGITUDE) ?: return@runCatching null
            SharedCarSessionDto(
                sessionId = get<String?>(FIELD_SESSION_ID) ?: id,
                authorUid = get<String?>(FIELD_AUTHOR_UID).orEmpty(),
                authorName = get<String?>(FIELD_AUTHOR_NAME),
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>(FIELD_ACCURACY)?.toFloat() ?: 0f,
                parkedAtMs = getLongCompat(FIELD_PARKED_AT_MS),
                endedAtMs = getLongOrNullCompat(FIELD_ENDED_AT_MS),
                isActive = get<Boolean?>(FIELD_IS_ACTIVE) ?: false,
                address = runCatching { get<AddressDto?>(FIELD_ADDRESS) }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>(FIELD_PLACE_INFO) }.getOrNull(),
                routeDistanceMeters = runCatching { get<Double?>(FIELD_ROUTE_DISTANCE_METERS)?.toFloat() }.getOrNull(),
                searchTimeMs = getLongOrNullCompat(FIELD_SEARCH_TIME_MS),
                publishedSpot = get<Boolean?>(FIELD_PUBLISHED_SPOT) ?: false,
                routePolyline = runCatching { get<String?>(FIELD_ROUTE_POLYLINE) }.getOrNull(),
                routeSnapped = get<Boolean?>(FIELD_ROUTE_SNAPPED) ?: false,
                routeInferredSpans = runCatching { get<String?>(FIELD_ROUTE_INFERRED_SPANS) }.getOrNull(),
                updatedAtMs = getLongCompat(FIELD_UPDATED_AT_MS),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSharedCarSessionDto failed — doc=$id", e)
            null
        }

    private fun DocumentSnapshot.toSharedCarMemberDto(): SharedCarMemberDto? =
        runCatching {
            SharedCarMemberDto(
                uid = get<String?>(FIELD_UID) ?: id,
                displayName = get<String?>(FIELD_DISPLAY_NAME),
                photoUrl = get<String?>(FIELD_PHOTO_URL),
                joinedAtMs = getLongCompat(FIELD_JOINED_AT_MS),
                role = get<String?>(FIELD_ROLE).orEmpty().ifEmpty { ROLE_MEMBER },
                sharesRoutes = get<Boolean?>(FIELD_SHARES_ROUTES) ?: true,
                tokens = runCatching { get<List<String>?>(FIELD_TOKENS) }.getOrNull().orEmpty(),
                inviteCode = get<String?>(FIELD_INVITE_CODE),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSharedCarMemberDto failed — doc=$id", e)
            null
        }

    private fun DocumentSnapshot.toSharedCarInviteDto(): SharedCarInviteDto? =
        runCatching {
            SharedCarInviteDto(
                code = get<String?>(FIELD_CODE) ?: id,
                carId = get<String?>(FIELD_CAR_ID) ?: return@runCatching null,
                ownerUid = get<String?>(FIELD_OWNER_UID).orEmpty(),
                carName = get<String?>(FIELD_CAR_NAME).orEmpty(),
                createdAtMs = getLongCompat(FIELD_CREATED_AT_MS),
                expiresAtMs = getLongCompat(FIELD_EXPIRES_AT_MS),
                claimedByUid = get<String?>(FIELD_CLAIMED_BY_UID),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSharedCarInviteDto failed — doc=$id", e)
            null
        }

    /** Reads a Long tolerating Firestore's Double representation of integers. */
    private fun DocumentSnapshot.getLongCompat(field: String): Long =
        getLongOrNullCompat(field) ?: 0L

    private fun DocumentSnapshot.getLongOrNullCompat(field: String): Long? =
        runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull()

    /** A revoked listener is an ANSWER, not a crash: the group is gone for this user. */
    private fun <T> Flow<T>.catchAsGone(what: String, carId: String, fallback: T): Flow<T> =
        catch { e ->
            PaparcarLogger.w(TAG, "$what($carId) stopped — treating the group as gone", e)
            emit(fallback)
        }

    private companion object {
        const val TAG = "SharedCarRemote"

        const val COLLECTION_SHARED_CARS = "sharedCars"
        const val COLLECTION_MEMBERS = "members"
        const val COLLECTION_SESSIONS = "sessions"
        const val COLLECTION_STATE = "state"
        const val COLLECTION_INVITES = "sharedCarInvites"
        const val DOC_CURRENT = "current"
        const val ROLE_MEMBER = "MEMBER"

        const val FIELD_CAR_ID = "carId"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_OWNER_NAME = "ownerName"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_BT_MAC_HASH = "btMacHash"
        const val FIELD_SIZE_CATEGORY = "sizeCategory"
        const val FIELD_CARBODY_TYPE = "carbodyType"
        const val FIELD_COLOR = "color"
        const val FIELD_SHARING_ENABLED = "sharingEnabled"
        const val FIELD_CREATED_AT_MS = "createdAtMs"
        const val FIELD_FUEL_TYPE = "fuelType"
        const val FIELD_CONSUMPTION_PER_100 = "consumptionPer100"
        const val FIELD_CONSUMPTION_ELECTRIC_PER_100 = "consumptionElectricPer100"
        const val FIELD_ROUTE_DISTANCE_METERS = "routeDistanceMeters"
        const val FIELD_SEARCH_TIME_MS = "searchTimeMs"
        const val FIELD_PUBLISHED_SPOT = "publishedSpot"
        const val FIELD_ROUTE_POLYLINE = "routePolyline"
        const val FIELD_ROUTE_SNAPPED = "routeSnapped"
        const val FIELD_ROUTE_INFERRED_SPANS = "routeInferredSpans"
        const val FIELD_SHARES_ROUTES = "sharesRoutes"
        const val FIELD_TOKENS = "tokens"
        const val FIELD_INVITE_EXPIRES_AT_MS = "inviteExpiresAtMs"

        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_ACCURACY = "accuracy"
        const val FIELD_PARKED_AT_MS = "parkedAtMs"
        const val FIELD_AUTHOR_UID = "authorUid"
        const val FIELD_AUTHOR_NAME = "authorName"
        const val FIELD_ADDRESS = "address"
        const val FIELD_PLACE_INFO = "placeInfo"
        const val FIELD_IS_PARKED = "isParked"
        const val FIELD_MOVED_BY_UID = "movedByUid"
        const val FIELD_MOVED_BY_NAME = "movedByName"
        const val FIELD_IS_ACTIVE = "isActive"
        const val FIELD_ENDED_AT_MS = "endedAtMs"
        const val FIELD_UPDATED_AT_MS = "updatedAtMs"

        const val FIELD_UID = "uid"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_JOINED_AT_MS = "joinedAtMs"
        const val FIELD_ROLE = "role"
        const val FIELD_INVITE_CODE = "inviteCode"

        const val FIELD_CODE = "code"
        const val FIELD_CAR_NAME = "carName"
        const val FIELD_EXPIRES_AT_MS = "expiresAtMs"
        const val FIELD_CLAIMED_BY_UID = "claimedByUid"
    }
}
