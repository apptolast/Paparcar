package io.apptolast.paparcar.detection

import android.content.Context
import io.apptolast.paparcar.domain.service.DepartureEventBus

/**
 * [DepartureEventBus] backed by a @Volatile field with a SharedPreferences mirror.
 *
 * The hot path (reads from the departure worker / verifier) stays in memory; the prefs
 * mirror only exists so the timestamp SURVIVES process death — an OEM kill between
 * IN_VEHICLE_ENTER and the geofence-exit used to wipe the evidence and silently degrade
 * departure detection to speed-only (fail-negative). The 30-min `vehicleEnterWindowMs`
 * bounds any staleness a resurrected value could introduce. [DET-SOLID-001]
 */
class DepartureEventBusImpl(context: Context) : DepartureEventBus {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var _lastVehicleEnteredAt: Long? =
        prefs.getLong(KEY_LAST_VEHICLE_ENTERED_AT, NO_VALUE).takeIf { it != NO_VALUE }

    override val lastVehicleEnteredAt: Long?
        get() = _lastVehicleEnteredAt

    override fun onVehicleEntered(timestampMs: Long) {
        _lastVehicleEnteredAt = timestampMs
        prefs.edit().putLong(KEY_LAST_VEHICLE_ENTERED_AT, timestampMs).apply()
    }

    override fun reset() {
        _lastVehicleEnteredAt = null
        prefs.edit().remove(KEY_LAST_VEHICLE_ENTERED_AT).apply()
    }

    private companion object {
        const val PREFS_NAME = "departure_event_bus"
        const val KEY_LAST_VEHICLE_ENTERED_AT = "last_vehicle_entered_at"
        const val NO_VALUE = Long.MIN_VALUE
    }
}
