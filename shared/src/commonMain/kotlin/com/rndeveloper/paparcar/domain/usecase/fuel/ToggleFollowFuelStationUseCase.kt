@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.repository.FuelWatchRepository
import kotlin.time.Clock

/** What the star did, so the caller can say it without re-reading the store. */
enum class FollowToggleOutcome { FOLLOWED, UNFOLLOWED }

/**
 * Follow or stop following a forecourt. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ **Free, and that is not an oversight.** Starring a station and reading its price is the app
 * answering a question you just asked — the free side of the line. What is Plus is the app watching
 * it for you while you are not looking. Gating the star would hide the gesture that teaches the
 * feature and leave the paywall selling something the user has never seen work.
 */
class ToggleFollowFuelStationUseCase(
    private val repository: FuelWatchRepository,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend operator fun invoke(
        stationKey: String,
        source: String,
        id: String,
        name: String?,
        latitude: Double,
        longitude: Double,
    ): Result<FollowToggleOutcome> = runCatching {
        val alreadyFollowed = repository.getFollowed().any { it.stationKey == stationKey }
        if (alreadyFollowed) {
            repository.unfollow(stationKey)
            FollowToggleOutcome.UNFOLLOWED
        } else {
            repository.follow(
                FollowedFuelStation(
                    stationKey = stationKey,
                    source = source,
                    id = id,
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    followedAtMs = clock(),
                ),
            )
            FollowToggleOutcome.FOLLOWED
        }
    }
}
