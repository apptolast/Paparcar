package com.rndeveloper.paparcar.domain.usecase.notification

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] **What street a "did you park?" question is allowed
 * to name — or nothing at all.**
 *
 * One rule, one place, because three call sites post that question (the confidence-scored prompt and
 * the executor's two degrade paths) and a street that differs between them would be three different
 * questions about one stop.
 *
 * ## Why it must run BEFORE the notification is posted
 *
 * The obvious alternative — post now, re-post with the street when the geocoder answers — reposts
 * through `showParkingConfirmation`, which rewrites `PendingPromptWindow.shownAtMs` unconditionally.
 * That silently restarts the user's `confirmationResponseTimeoutMs` window and, with it, the
 * unattended-save deadline. A cosmetic upgrade would have moved a safety deadline. So the street is
 * resolved first, on a budget short enough that the ask is not perceptibly delayed
 * ([BUDGET_MS] — the geocoder's own Phase-1 deadline is more than twice that), and a slow answer
 * simply does not make it into this question.
 *
 * ## Why an APPROXIMATE address is refused outright
 *
 * When the platform geocoder fails, `AddressAndPlaceRepository` borrows the nearest cached cell's
 * street and flags it `approximate` [GEO-CACHE-ANSWERS-NEARBY-001]. In a list of nearby places that
 * is a helpful "near X". In a question that decides where a pin goes it is a road the user is not
 * on, printed with a house number — the most confident-looking way this feature could lie. No
 * street is an honest answer; a borrowed one is not.
 *
 * The POI name is deliberately not used either, even though "did you park at Mercadona?" reads
 * better: the POI is the repository's Phase 2 (a network lookup an order of magnitude slower than
 * this budget), so it would appear only when a previous visit happened to cache it — the same stop
 * would be worded differently on different days.
 */
class ResolveAskedStreetUseCase(
    private val getAddressAndPlace: GetAddressAndPlaceUseCase,
) {
    suspend operator fun invoke(at: GpsPoint?): String? {
        if (at == null) return null
        val answer = withTimeoutOrNull(BUDGET_MS) {
            getAddressAndPlace(at.latitude, at.longitude).firstOrNull()
        }
        if (answer == null) {
            PaparcarLogger.d(DIAG, "  ⌂ no street for the question — geocoder did not answer inside ${BUDGET_MS}ms")
            return null
        }
        if (answer.approximate) {
            PaparcarLogger.d(DIAG, "  ⌂ street REFUSED — borrowed from a neighbouring cell, not this spot")
            return null
        }
        return answer.address.street?.takeIf { it.isNotBlank() }
            .also { PaparcarLogger.d(DIAG, "  ⌂ question street = ${it ?: "none"}") }
    }

    private companion object {
        const val DIAG = "PARKDIAG/Street"

        /** Short on purpose: this runs between the decision to ask and the ask appearing. */
        const val BUDGET_MS = 2_000L
    }
}
