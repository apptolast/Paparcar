package com.rndeveloper.paparcar.location

import android.content.Context
import android.os.BatteryManager
import com.rndeveloper.paparcar.domain.location.ChargingStateSource

/**
 * [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001] Reads the platform's cached charging state —
 * `BatteryManager.isCharging` is a property lookup against BatteryService, no receiver, no I/O —
 * cheap enough for the sampling policy to consult on every fix tick.
 */
class AndroidChargingStateSource(context: Context) : ChargingStateSource {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    override fun isCharging(): Boolean = batteryManager?.isCharging == true
}
