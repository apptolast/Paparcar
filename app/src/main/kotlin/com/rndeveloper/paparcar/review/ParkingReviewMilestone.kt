package com.rndeveloper.paparcar.review

import android.content.Context

/** Counts only successful, eligible manual saves. Store quota is owned by Google Play. */
internal class ParkingReviewMilestone(
    initialCount: Int,
    private val persistCount: (Int) -> Unit,
) {
    private val hadSuccessInEarlierProcess = initialCount > 0
    private var count = initialCount

    fun recordSuccessfulParking(): Boolean {
        if (count < REQUIRED_PARKINGS) {
            count++
            persistCount(count)
        }
        return hadSuccessInEarlierProcess && count >= REQUIRED_PARKINGS
    }

    companion object {
        private const val REQUIRED_PARKINGS = 3
        private const val PREFERENCES_NAME = "parking_review"
        private const val SUCCESS_COUNT = "success_count"

        fun fromPreferences(context: Context): ParkingReviewMilestone {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            return ParkingReviewMilestone(preferences.getInt(SUCCESS_COUNT, 0)) { count ->
                preferences.edit().putInt(SUCCESS_COUNT, count).apply()
            }
        }
    }
}
