package io.apptolast.paparcar.domain.session

/**
 * Wipes the on-device cache that belongs to the currently authenticated user
 * (vehicles, zones, parking sessions, user profile) so a different user can
 * sign in without inheriting the previous user's data.
 *
 * Paparcar treats Room as the active user's cache only; Firestore is the
 * source of truth. The splash bootstrap repopulates the cache from Firestore
 * on the next sign-in, so callers can wipe unconditionally. [SESSION-ISOLATION-001]
 */
interface LocalSessionCache {
    suspend fun wipe()
}
