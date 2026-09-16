package com.rndeveloper.paparcar.domain.error

sealed class PaparcarError : Exception() {

    sealed class Location : PaparcarError() {
        data object PermissionDenied : Location()
        data object ProviderDisabled : Location()
        /** The address search (geocoder) failed — distinct from a successful lookup with zero
         *  results, so the UI can tell "nothing found" from "the search itself broke". [UX-PARK-FLOW-001 H3] */
        data object SearchFailed : Location()
        data class Unknown(override val message: String) : Location()
    }

    sealed class Network : PaparcarError() {
        data object NoConnection : Network()
        data object Timeout : Network()
        data class ServerError(val code: Int, override val message: String) : Network()
        data class Unknown(override val message: String) : Network()
    }

    sealed class Database : PaparcarError() {
        data object NotFound : Database()
        data class WriteError(override val message: String) : Database()
        data class Unknown(override val message: String) : Database()
    }

    sealed class Detection : PaparcarError() {
        data object ActivityRecognitionUnavailable : Detection()
        data object PermissionDenied : Detection()

        /** The user asked to reactivate the departure watch and the platform refused to bring it
         *  back (nothing to watch any more, or the foreground start was blocked). An explicit tap
         *  must never look like a dead button. [DET-WATCH-REACTIVATE-001] */
        data object WatchResumeFailed : Detection()
    }

    sealed class Auth : PaparcarError() {
        data object ProfileSyncFailed : Auth()
        /** The operation requires an authenticated user but no active session was found. */
        data object NotAuthenticated : Auth()
        data object DeleteFailed : Auth()
    }

    sealed class Parking : PaparcarError() {
        /** Session could not be persisted to the local database. */
        data object SaveFailed : Parking()
        /** The user has no resolvable default vehicle. Saving a parking with vehicleId=null would
         *  produce a row unreachable in the per-vehicle history UI; better to fail loud. [AUTH-001] */
        data object NoDefaultVehicle : Parking()
        /** Auto-confirm rejected by the repark-plausibility guard: an active session for the same
         *  vehicle exists nearby-and-recent, and the confirming detection session never observed
         *  driving. Relocating the parked car on that evidence is more likely a pedestrian false
         *  positive than a real repark — the caller should degrade to a user prompt. [DET-SOLID-001] */
        data object ImplausibleRepark : Parking()
        /** The tapped session id resolved to no active session (already released, or synced away).
         *  Releasing is a no-op instead of falling back to whichever session ranks first, which
         *  could free the wrong car's spot. [VEH-ACTIVE-FENCE-001] */
        data object ReleaseFailed : Parking()
    }

    /** The community half — a PLAZA, not your parking. Kept apart from [Parking] on purpose: the two
     *  words name two different things and the vocabulary already split them.
     *  [COPY-SPOT-IS-NOT-A-PARKING-001] */
    sealed class Spot : PaparcarError() {
        /** The pin sat further from the measured fix than a reporter can vouch for. The peek disables
         *  its confirm and says so, so this only lands when the fix moved between the camera settling
         *  and the press. [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001] */
        data object TooFarToReport : Spot()
    }

    sealed class Vehicle : PaparcarError() {
        data object SaveFailed : Vehicle()
        data object DeleteFailed : Vehicle()

        /** The vehicle is parked right now, and deleting it would erase the parking in progress
         *  along with its history. Distinct from [DeleteFailed] because nothing broke: the user is
         *  told to close the parking first, not that the delete failed.
         *  [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001] */
        data object DeleteBlockedByActiveParking : Vehicle()
    }

    /**
     * A shared car refuses an operation. [VEH-SHARED-001]
     *
     * Every case here is a thing the USER can act on, which is why they are separate types and not
     * one `SharedCarFailed`: the copy for each one names its own remedy ("ask for another code",
     * "someone has to leave first"). Nothing broke in any of them — the screen says what happened
     * and what to do, never "error".
     */
    sealed class SharedCar : PaparcarError() {
        /** Six characters that match no live invitation — mistyped, or the owner revoked it. */
        data object InviteNotFound : SharedCar()
        /** The code was real and its 24 h are up. */
        data object InviteExpired : SharedCar()
        /** Single use, and somebody already used it. */
        data object InviteAlreadyUsed : SharedCar()
        /** The owner plus four is the whole group; somebody has to leave first. */
        data object GroupFull : SharedCar()

        /** Ya tiene el máximo de coches propios compartidos. No es un paywall: ya es Plus.
         *  [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] */
        data object TooManySharedCars : SharedCar()

        /** Ya está en el máximo de grupos ajenos. Tampoco es un paywall: entrar siempre fue gratis. */
        data object TooManyJoinedCars : SharedCar()
        /** Already in this group — joining twice is a no-op, not a failure to hide. */
        data object AlreadyJoined : SharedCar()
        /** Only the owner opens, closes or changes the group. */
        data object NotTheOwner : SharedCar()
        /** The mirror could not be written or read. The one case here that IS a breakage. */
        data object SyncFailed : SharedCar()
    }
}
