package com.rndeveloper.paparcar.location

import android.content.Context
import android.os.BatteryManager
import com.rndeveloper.paparcar.domain.detection.ports.BatteryMeter

/**
 * [BATT-A-COST-IS-MEASURED-NOT-DECLARED-001] Reads the platform fuel gauge — both are property
 * lookups against BatteryService, cheap enough for a twice-per-job bracket.
 *
 * `BATTERY_PROPERTY_CHARGE_COUNTER` returns `Long.MIN_VALUE` when the property is unsupported
 * (and 0/absent readings exist on devices without a fuel gauge) — both are "no counter", mapped
 * to null so a missing instrument never masquerades as a measurement.
 */
class AndroidBatteryMeter(context: Context) : BatteryMeter {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    override fun chargeCounterMicroAh(): Long? =
        batteryManager
            ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Long.MIN_VALUE && it > 0 }

    override fun isCharging(): Boolean = batteryManager?.isCharging == true
}
