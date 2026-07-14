package io.apptolast.paparcar.presentation.home.sections.sheet

/**
 * The single UI-action channel of the bottom sheet. [HOME-ATOMIZE-001 F3]
 *
 * Everything the sheet needs that is NOT a plain ViewModel intent — local UI
 * orchestration (sheet motion, dialogs, list expansion), camera moves and
 * navigation — flows through `onAction: (HomeSheetAction) -> Unit`, translated
 * in ONE place (HomeSheetSection in HomeScreen.kt). Actions that are 1:1 with a
 * [io.apptolast.paparcar.presentation.home.HomeIntent] are NOT mirrored here:
 * the sheet emits those directly via its `onIntent` channel.
 */
internal sealed interface HomeSheetAction {
    /** Tap on the peek header — toggle between peek and the adjacent snap. */
    data object ToggleSheet : HomeSheetAction

    /** "Show list" toggle on the selected-spot peek. */
    data object ToggleSpotList : HomeSheetAction

    /** "Me voy" on the parking peek — open the release dialog (publish / delete-only). */
    data object RequestRelease : HomeSheetAction

    /** "Report a free spot" CTA — enter Reporting mode centred on the current camera. */
    data object RequestReportMode : HomeSheetAction

    /** Fly the map camera to a coordinate (row tap, parking row tap…). */
    data class MoveCamera(val lat: Double, val lon: Double) : HomeSheetAction

    /** Launch the platform's external navigation (Google/Apple Maps). */
    data class NavigateExternal(val lat: Double, val lon: Double, val walking: Boolean) : HomeSheetAction

    /** CORE blocker CTA / detection surface — open the permission flow focused on core. */
    data object OpenCorePermissions : HomeSheetAction

    /** Detection surface — navigate to vehicle registration. */
    data object AddVehicle : HomeSheetAction
}
