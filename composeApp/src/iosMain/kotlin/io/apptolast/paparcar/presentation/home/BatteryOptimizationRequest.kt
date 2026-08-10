package io.apptolast.paparcar.presentation.home

// iOS has no equivalent of Android's battery-optimization whitelist exposed to third-party apps;
// the honest watch badge's CTA is a no-op here (the badge itself only appears on the aggressive-OEM
// Android path). [DET-WATCH-HONEST-001]
actual fun requestIgnoreBatteryOptimizations() = Unit
