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
}
