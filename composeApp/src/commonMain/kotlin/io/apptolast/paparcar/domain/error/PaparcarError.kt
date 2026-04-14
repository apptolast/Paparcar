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
    }

    sealed class Parking : PaparcarError() {
        /** Session could not be persisted to the local database. */
        data object SaveFailed : Parking()
    }
}
