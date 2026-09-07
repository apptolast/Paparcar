@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.user

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Records that the user closed the guided checklist — or, with `false`, that they want it back.
 * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
 *
 * Two stores, one verb, because they answer the same question at two lifetimes:
 *  - the DEVICE preference is what Home renders from, so it is written first and synchronously —
 *    the checklist has to disappear on the tap, not on a round-trip.
 *  - the ACCOUNT is what survives the device. Uninstalling, being restored from a Google backup or
 *    signing in on a new phone used to bring the tutorial back to someone who had already finished
 *    it, because "I closed this" was only ever written to a DataStore file.
 *
 * Existing as a use case rather than two lines in each ViewModel is the point: there are two
 * writers (Home closes it, Settings replays it) and two stores that must not drift, which is
 * exactly the shape that produces a bug when the second writer forgets the second store.
 */
class SetFirstStepsDismissedUseCase(
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    suspend operator fun invoke(dismissed: Boolean) {
        appPreferences.setFirstStepsDismissed(dismissed)

        val userId = authRepository.getCurrentSession()?.userId
        if (userId == null) {
            // No session to remember it on. Nothing is lost that was not already local-only, and
            // there is no screen showing a checklist to a signed-out user to complain to.
            PaparcarLogger.w(TAG, "no session — first-steps dismissal stays device-local")
            return
        }

        val dismissedAt = if (dismissed) Clock.System.now().toEpochMilliseconds() else null
        userProfileRepository.setFirstStepsDismissedAt(userId, dismissedAt)
            // Logged, never surfaced: the user's tap already did what they asked (the checklist is
            // gone on this phone) and "we could not write it to your profile" is app plumbing, not
            // something they can act on. The splash reconciles the miss on the next online start,
            // which is the actual remedy. [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
            .onFailure { e -> PaparcarLogger.w(TAG, "could not mirror dismissal to the account", e) }
    }

    private companion object {
        const val TAG = "SetFirstStepsDismissed"
    }
}
