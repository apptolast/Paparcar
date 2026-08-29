package com.rndeveloper.paparcar

actual val isDebugBuild: Boolean get() = AppBuildInfo.isDebug
actual val appVersion: String get() = AppBuildInfo.versionName
actual val isBatteryOptimizationRelevant: Boolean = true
