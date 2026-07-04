package io.apptolast.paparcar.domain.error

sealed class PaparcarError : Exception() {

    sealed class Location : PaparcarError() {
        data object PermissionDenied : Location()
        data object ProviderDisabled : Location()
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
    }

    sealed class Vehicle : PaparcarError() {
        data object SaveFailed : Vehicle()
        data object DeleteFailed : Vehicle()
    }
}
