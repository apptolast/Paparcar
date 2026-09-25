package com.rndeveloper.paparcar.ios.auth

import platform.Foundation.NSBundle
import platform.Foundation.NSDictionary
import platform.Foundation.dictionaryWithContentsOfFile

/**
 * Reads the Google iOS client id from the `GoogleService-Info.plist` bundled with the app — the same
 * gitignored file `FirebaseApp.configure()` already needs — so the id is never duplicated in code.
 * Null when the plist is missing (a fresh clone, CI): the Google button then simply does not show.
 * [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
 */
object GoogleServiceInfo {

    private const val PLIST_NAME = "GoogleService-Info"
    private const val PLIST_TYPE = "plist"
    private const val CLIENT_ID_KEY = "CLIENT_ID"

    fun clientId(): String? {
        val path = NSBundle.mainBundle.pathForResource(PLIST_NAME, PLIST_TYPE) ?: return null
        return NSDictionary.dictionaryWithContentsOfFile(path)?.get(CLIENT_ID_KEY) as? String
    }
}
