package com.rndeveloper.paparcar

actual val isDebugBuild: Boolean get() = AppBuildInfo.isDebug
actual val appVersion: String get() = AppBuildInfo.versionName
actual val platformName: String = "android"
actual val isBatteryOptimizationRelevant: Boolean = true
actual val isBtStrategyRelevant: Boolean = true
actual val isAudioRouteLinkRelevant: Boolean = false
actual val germanyFuelFeedKey: String get() = AppBuildInfo.germanyFuelFeedKey
