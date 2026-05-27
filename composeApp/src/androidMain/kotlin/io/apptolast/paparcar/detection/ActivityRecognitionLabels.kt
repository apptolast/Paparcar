package io.apptolast.paparcar.detection

import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity

internal fun activityLabel(type: Int): String = when (type) {
    DetectedActivity.STILL -> "STILL"
    DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
    DetectedActivity.WALKING -> "WALKING"
    DetectedActivity.RUNNING -> "RUNNING"
    DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
    DetectedActivity.ON_FOOT -> "ON_FOOT"
    else -> "UNKNOWN($type)"
}

internal fun transitionLabel(type: Int): String = when (type) {
    ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
    ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
    else -> "UNKNOWN($type)"
}
