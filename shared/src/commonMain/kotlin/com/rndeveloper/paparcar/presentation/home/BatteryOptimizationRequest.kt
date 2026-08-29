package com.rndeveloper.paparcar.presentation.home

/**
 * Launch the OS "ignore battery optimizations" request for this app — the durable lever that keeps the
 * departure watch alive on Doze-aggressive OEMs. Fired by the honest watch badge's fragile / interrupted
 * CTA. Android → the system exemption dialog; iOS → no-op (not applicable). Uses the app context so it
 * can be called from a Compose effect without an Activity handle.
 * [DET-WATCH-HONEST-001] [DET-BATTERY-EXEMPTION-NUDGE-001]
 */
expect fun requestIgnoreBatteryOptimizations()
