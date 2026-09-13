package com.rndeveloper.paparcar.data.datasource.remote

/**
 * Which Firestore database of the `pap-26` project a build talks to. Debug builds (Android
 * `BuildConfig.DEBUG`, iOS Xcode Debug configuration) use the named `debug` database so field tests
 * and development never write into production data; release builds use `(default)`.
 *
 * Rules and indexes are deployed to BOTH databases from the same files — see `firebase.json`.
 * [FIRESTORE-DEBUG-BUILDS-USE-DEBUG-DATABASE-001]
 */
internal object FirestoreDatabase {
    /** Named database for debug builds. Must match the database id created in the console. */
    const val DEBUG_DATABASE_ID = "debug"

    /** `null` means the project's `(default)` database, which is what the SDK resolves it to. */
    fun idFor(isDebugBuild: Boolean): String? = if (isDebugBuild) DEBUG_DATABASE_ID else null
}
