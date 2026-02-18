package io.apptolast.paparcar.domain.error

sealed class PaparcarError {

    sealed class Location : PaparcarError() {
        data object PermissionDenied : Location()
        data object ProviderDisabled : Location()
        data class Unknown(val message: String) : Location()
    }

    sealed class Network : PaparcarError() {
        data object NoConnection : Network()
        data object Timeout : Network()
        data class ServerError(val code: Int, val message: String) : Network()
        data class Unknown(val message: String) : Network()
    }

    sealed class Database : PaparcarError() {
        data object NotFound : Database()
        data class WriteError(val message: String) : Database()
        data class Unknown(val message: String) : Database()
    }

    sealed class Detection : PaparcarError() {
        data object ActivityRecognitionUnavailable : Detection()
        data object PermissionDenied : Detection()
    }
}
