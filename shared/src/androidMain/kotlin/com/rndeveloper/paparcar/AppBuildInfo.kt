package com.rndeveloper.paparcar

/**
 * Build facts that only the application module can know once the project is split into
 * `:app` + `:shared` — `BuildConfig` is generated in `:app`, and shared code cannot import it.
 * Both Application classes ([PaparcarApp], `MockPaparcarApp`) MUST populate this in
 * `onCreate()` before anything else runs; every Android component (activity, service,
 * receiver, worker) is created after `Application.onCreate`, so readers never see defaults.
 */
object AppBuildInfo {
    var isDebug: Boolean = false
    var versionName: String = ""
}
