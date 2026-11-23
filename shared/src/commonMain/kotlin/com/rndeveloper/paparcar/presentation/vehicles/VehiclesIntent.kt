package com.rndeveloper.paparcar.presentation.vehicles

sealed class VehiclesIntent {
    data class SetActiveVehicle(val vehicleId: String) : VehiclesIntent()
    data class BluetoothVehicleConnected(val vehicleId: String?) : VehiclesIntent()
    data class SelectVehicle(val index: Int) : VehiclesIntent()

    /**
     * Open the pager ON a given car, asked for from OUTSIDE this screen — today, "Ver vehículo" in
     * Home's parking modal. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
     *
     * By ID and not by index, because whoever asks does not know this screen's ordering; and it
     * survives arriving before the vehicle list does — it waits as a pending request instead of
     * being dropped on a race.
     */
    data class FocusVehicle(val vehicleId: String) : VehiclesIntent()
    data class EditVehicle(val vehicleId: String) : VehiclesIntent()

    /** [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Vía A (explicit): read the live audio route and,
     *  if the car's Bluetooth is connected, tie its MAC to this vehicle. iOS-only surface. */
    data class LinkCarAudio(val vehicleId: String) : VehiclesIntent()
    data object AddVehicle : VehiclesIntent()

    /** El «+»: preguntar antes de navegar, porque «otro coche en la app» son dos cosas — registrar
     *  uno mío, o entrar en uno que ya me comparten. `false` cierra.
     *  [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001] */
    data class ShowAddChoice(val open: Boolean) : VehiclesIntent()

    /** Pedir confirmacion antes de salir del grupo o disolverlo; null cierra.
     *  [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001] */
    data class ConfirmGroupExit(val prompt: GroupExitPrompt?) : VehiclesIntent()
    data class SetStatsPeriod(val filter: StatsPeriod) : VehiclesIntent()

    /** Which series the activity chart draws. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    data class SetChartMetric(val metric: ChartMetric) : VehiclesIntent()

    /** «Sí» / «No» a «¿Repostaste aquí?» desde una fila del historial — la misma pregunta que Home
     *  ofrece, sobre el mismo registro. [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] */
    data class AnswerRefuelStop(val sessionId: String, val refuelled: Boolean) : VehiclesIntent()
    data class ViewOnMap(val lat: Double, val lon: Double, val sessionId: String = "") : VehiclesIntent()

    /**
     * Ver en el mapa un aparcamiento que sigue VIVO — y eso es Home, no el detalle del historial.
     * [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
     *
     * Regla editorial del proyecto: *si pasa AHORA → Home*. Un coche que está aparcado ahora mismo
     * se mira donde se puede actuar sobre él (irse, navegar, corregir el pin); el detalle del
     * historial es de solo lectura y existe para las sesiones CERRADAS, que en Home ya no tienen pin.
     */
    data class ViewLiveParkingOnMap(val sessionId: String) : VehiclesIntent()

    /** The "Ver todo con Plus" footer under a gated history. [MONEY-001] */
    data object OpenPlusPaywall : VehiclesIntent()

    /** The Plus row under the figures card. Distinct from [OpenPlusPaywall] so the funnel can tell
     *  which of the two surfaces on this page sold the upgrade. [STATS-UI-001] */
    data object OpenStatsPaywall : VehiclesIntent()

    /** Plus tapping "ver desglose completo" — the panel opens on THIS car. [STATS-PANEL-001] */
    data class OpenStatsPanel(val vehicleId: String) : VehiclesIntent()

    /** The CTA of the paused-detection notice over the figures. ONE action whatever is in the way:
     *  bank the intent and open the flow that grants what is missing. [DET-TOGGLE-001]
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    data object EnableAutoDetection : VehiclesIntent()

    // ── Coche compartido [VEH-SHARED-001] ────────────────────────────────────
    // Acciones sobre un coche, no navegación: el grupo vive en la página del coche.

    /** Abrir el grupo de este coche. Gate blando: free aterriza en el paywall. */
    data class ShareCar(val vehicleId: String) : VehiclesIntent()
    /** El diálogo que explica qué verá el grupo, antes de abrirlo. */
    data class ConfirmShareCar(val vehicleId: String?) : VehiclesIntent()
    /** Un código nuevo de un solo uso; el anterior muere. */
    data class CreateInvite(val carId: String) : VehiclesIntent()
    /** El dueño disuelve el grupo. El coche, su historial y su emparejamiento se quedan. */
    data class StopSharing(val carId: String) : VehiclesIntent()
    /** El miembro se sale. Su fila se desvincula, no se borra. */
    data class LeaveSharedCar(val carId: String) : VehiclesIntent()
    data class RemoveMember(val carId: String, val uid: String) : VehiclesIntent()
    /** Que mis rutas en este coche se vean, o no. Las cifras viajan igual. */
    data class SetShareRoutes(val carId: String, val share: Boolean) : VehiclesIntent()
    /** La hoja de miembros, o null para cerrarla. */
    data class ShowMembers(val carId: String?) : VehiclesIntent()
    /** Historial y cifras del coche a la vista: del grupo, o solo míos. */
    data class SetSharedHistoryScope(val mineOnly: Boolean) : VehiclesIntent()

    /** Abrir/cerrar el diálogo de unirse. */
    /** Conservarlo: pasa a ser un coche suyo más y ocupa su plaza.
     *  [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] */
    data object KeepDissolvedCar : VehiclesIntent()
    /** Borrarlo, con los viajes que hizo con él — que son suyos, y por eso se le dice cuántos. */
    data object DeleteDissolvedCar : VehiclesIntent()

    /** La respuesta a la pregunta de rutas, mientras el diálogo está abierto.
     *  [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001] */
    data class SetConfirmShareRoutes(val share: Boolean) : VehiclesIntent()
    data class SetJoinShareRoutes(val share: Boolean) : VehiclesIntent()

    data class ShowJoinDialog(val open: Boolean) : VehiclesIntent()
    data class JoinCodeChanged(val code: String) : VehiclesIntent()
    data object SubmitJoinCode : VehiclesIntent()
}

/**
 * La salida de un grupo, esperando un si. [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
 *
 * Lleva [isOwner] y no lo re-deduce del estado: cual de las dos frases se lee —«todo el grupo deja
 * de ver» o «dejas de ver»— es la mitad de lo que se esta confirmando.
 */
data class GroupExitPrompt(val carId: String, val isOwner: Boolean)
