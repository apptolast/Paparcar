@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * The MANUAL "avisar plaza libre" report policy, extracted from
 * HomeViewModel.confirmReportSpot. [HOME-ATOMIZE-001 F4]
 *
 * Carbody hygiene [F1-bis]: the manual flow picks a size but has no carbody
 * picker, so the active vehicle's carbody is used as fallback — the public Spot
 * then carries it and the "Left by …" subline renders for other users. Size is
 * taken verbatim: null means the user explicitly selected "Indefinido", so it
 * is NOT inferred from the vehicle.
 *
 * Deliberately a wrapper around [ReportSpotReleasedUseCase] rather than a
 * fallback inside it: the released-spot path is shared with the detection
 * chain (departure workers, release-parking), where the departing vehicle is
 * the SESSION's vehicle, not necessarily the active one.
 */
class ReportManualSpotUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        sizeCategory: VehicleSize?,
        /** Address/POI the caller already geocoded for these coords (pin centre). [SPOT-PREFETCH-001] */
        prefetched: AddressAndPlace?,
    ): Result<Unit> = runCatching {
        val activeCarbody = vehicleRepository.observeVehicles().first()
            .firstOrNull { it.isActive }?.carbodyType
        reportSpotReleased(
            lat = lat,
            lon = lon,
            spotId = "$MANUAL_SPOT_ID_PREFIX${Clock.System.now().toEpochMilliseconds()}",
            spotType = SpotType.MANUAL_REPORT,
            confidence = MANUAL_REPORT_CONFIDENCE,
            sizeCategory = sizeCategory,
            carbodyType = activeCarbody,
            prefetched = prefetched,
        )
    }

    private companion object {
        const val MANUAL_SPOT_ID_PREFIX = "manual_"
        // An eyewitness report is full-confidence by definition.
        const val MANUAL_REPORT_CONFIDENCE = 1f
    }
}
