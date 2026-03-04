@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.preview

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.time.Clock

/**
 * Fake spot list for UI development and Compose previews.
 * Coordinates are clustered around Retiro, Madrid.
 */
val fakeSpots: List<Spot> by lazy {
    val now = Clock.System.now().toEpochMilliseconds()
    listOf(
        Spot(
            id = "fake-1",
            location = GpsPoint(
                latitude = 40.4180,
                longitude = -3.7020,
                accuracy = 8f,
                timestamp = now - 3 * 60_000,   // 3 min ago
                speed = 0f,
            ),
            reportedBy = "Carlos M.",
            isActive = true,
        ),
        Spot(
            id = "fake-2",
            location = GpsPoint(
                latitude = 40.4155,
                longitude = -3.7055,
                accuracy = 12f,
                timestamp = now - 11 * 60_000,  // 11 min ago
                speed = 0f,
            ),
            reportedBy = "Ana R.",
            isActive = true,
        ),
        Spot(
            id = "fake-3",
            location = GpsPoint(
                latitude = 40.4175,
                longitude = -3.7010,
                accuracy = 6f,
                timestamp = now - 28 * 60_000,  // 28 min ago
                speed = 0f,
            ),
            reportedBy = "Pedro L.",
            isActive = false,
        ),
        Spot(
            id = "fake-4",
            location = GpsPoint(
                latitude = 40.4162,
                longitude = -3.7025,
                accuracy = 10f,
                timestamp = now - 2 * 60_000,   // 2 min ago
                speed = 0f,
            ),
            reportedBy = "Lucía F.",
            isActive = true,
        ),
        Spot(
            id = "fake-5",
            location = GpsPoint(
                latitude = 40.4190,
                longitude = -3.7045,
                accuracy = 15f,
                timestamp = now - 45 * 60_000,  // 45 min ago
                speed = 0f,
            ),
            reportedBy = "Miguel T.",
            isActive = false,
        ),
    )
}
