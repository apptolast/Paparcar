package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.repository.FuelWatchRepository
import kotlinx.coroutines.flow.Flow

/**
 * The stations this user follows, live. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ **Not gated by Plus**, and the map surfaces that read it must not gate it either: a star drawn
 * filled is the app answering what you already told it. What Plus buys is the app WATCHING that
 * star while you are not looking — which is why the gate lives in [RunFuelPriceWatchUseCase] and
 * nowhere else.
 */
class ObserveFollowedFuelStationsUseCase(
    private val repository: FuelWatchRepository,
) {
    operator fun invoke(): Flow<List<FollowedFuelStation>> = repository.observeFollowed()
}
