@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.zone

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Creates a new [Zone] at the supplied coordinates and persists it.
 * The id and createdAt are generated here; the use case is responsible
 * for resolving the active userId via [AuthRepository].
 *
 * Returns [PaparcarError.Auth.NotAuthenticated] if there is no current
 * session — the host UI should treat that as an unexpected state since
 * zones are only reachable from authenticated screens.
 */
class SaveZoneUseCase(
    private val repository: ZoneRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        lat: Double,
        lon: Double,
        iconKey: String = ZoneIcon.DEFAULT,
        radiusMeters: Float = Zone.DEFAULT_RADIUS_METERS,
        isPrivate: Boolean = false,
    ): Result<Zone> {
        val userId = authRepository.getCurrentSession()?.userId
            ?: return Result.failure(PaparcarError.Auth.NotAuthenticated)
        val zone = Zone(
            id = Uuid.random().toString(),
            userId = userId,
            name = name.trim(),
            lat = lat,
            lon = lon,
            iconKey = iconKey,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            radiusMeters = radiusMeters.coerceIn(Zone.MIN_RADIUS_METERS, Zone.MAX_RADIUS_METERS),
            isPrivate = isPrivate,
        )
        repository.saveZone(zone).getOrElse { return Result.failure(it) }
        return Result.success(zone)
    }
}
