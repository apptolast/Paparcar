package com.rndeveloper.paparcar.review

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

internal class PlayParkingReview(
    private val activity: ComponentActivity,
    private val milestone: ParkingReviewMilestone,
) {
    private val manager by lazy { ReviewManagerFactory.create(activity.applicationContext) }
    private var inFlight = false

    fun onParkingSaved() {
        if (!milestone.recordSuccessfulParking() || inFlight || !canLaunch()) return

        inFlight = true
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                inFlight = false
                PaparcarLogger.w(TAG, "Play review request failed", request.exception)
                return@addOnCompleteListener
            }
            if (!canLaunch()) {
                inFlight = false
                return@addOnCompleteListener
            }

            manager.launchReviewFlow(activity, request.result).addOnCompleteListener { launch ->
                inFlight = false
                if (!launch.isSuccessful) {
                    PaparcarLogger.w(TAG, "Play review flow failed", launch.exception)
                }
            }
        }
    }

    private fun canLaunch(): Boolean =
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus()

    private companion object {
        const val TAG = "PlayParkingReview"
    }
}
