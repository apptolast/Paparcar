package com.rndeveloper.paparcar.domain.service

/**
 * Platform door for pinning the parked-car widget from inside the app. [WIDGET-ACTIONS-002]
 *
 * Android implements it with `AppWidgetManager.requestPinAppWidget` (the system sheet the launcher
 * shows); launchers without pin support answer [isPinSupported] false and the Settings row simply
 * does not appear. No iOS implementation — the binding is optional there.
 */
interface WidgetPinRequester {
    val isPinSupported: Boolean
    fun requestPin()
}
