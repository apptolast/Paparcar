@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.preview

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.time.Clock

/**
 * Fake active parking session for UI development and Compose previews.
 * Position is on Calle de Alcalá, near Retiro, Madrid.
 */
val fakeParking: UserParking by lazy {
    UserParking(
        id = "fake-session-1",
        location = GpsPoint(
            latitude = 40.4170,
            longitude = -3.7040,
            accuracy = 9f,
            timestamp = Clock.System.now().toEpochMilliseconds() - 23 * 60_000, // 23 min ago
            speed = 0f,
        ),
        spotId = "fake-1",
        geofenceId = "geofence-fake-1",
        isActive = true,
    )
}
