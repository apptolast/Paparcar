package com.rndeveloper.paparcar.detection.receiver

import android.content.Intent
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingConfirmationReceiverTest {
    @Test
    fun arrivalAnswerKeepsItsQuestionIdentityWhenForwarded() {
        val app = RuntimeEnvironment.getApplication()
        ParkingConfirmationReceiver().onReceive(app, Intent(ParkingConfirmationReceiver.ACTION_CONFIRMED).apply {
            putExtra(CoordinatorDetectionService.EXTRA_PROMPT_AT, 123L)
        })
        val delivered = shadowOf(app).nextStartedService
        assertEquals(CoordinatorDetectionService.ACTION_PARKING_CONFIRMED, delivered.action)
        assertEquals(123L, delivered.getLongExtra(CoordinatorDetectionService.EXTRA_PROMPT_AT, -1L))
    }
    @Test
    fun departureAnswerKeepsBothFenceAndQuestionIdentity() {
        val app = RuntimeEnvironment.getApplication()
        ParkingConfirmationReceiver().onReceive(app, Intent(CoordinatorDetectionService.ACTION_STILL_PARKED_HERE).apply {
            putExtra(CoordinatorDetectionService.EXTRA_GEOFENCE_ID, "car-fence")
            putExtra(CoordinatorDetectionService.EXTRA_PROMPT_AT, 456L)
        })
        val delivered = shadowOf(app).nextStartedService
        assertEquals(CoordinatorDetectionService.ACTION_STILL_PARKED_HERE, delivered.action)
        assertEquals("car-fence", delivered.getStringExtra(CoordinatorDetectionService.EXTRA_GEOFENCE_ID))
        assertEquals(456L, delivered.getLongExtra(CoordinatorDetectionService.EXTRA_PROMPT_AT, -1L))
    }
}
