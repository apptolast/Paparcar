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
    data object AddVehicle : VehiclesIntent()
    data class SetHistoryFilter(val filter: HistoryFilter) : VehiclesIntent()

    /** Which series the activity chart draws. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    data class SetChartMetric(val metric: ChartMetric) : VehiclesIntent()
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
    data class ShowJoinDialog(val open: Boolean) : VehiclesIntent()
    data class JoinCodeChanged(val code: String) : VehiclesIntent()
    data object SubmitJoinCode : VehiclesIntent()
}
