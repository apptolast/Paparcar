package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [06 §3-b] The person/car envelope, now that the four copies share one formula. What these tests
 * pin is each TERM of it — because each term is there for a field incident, and a term is exactly
 * what a later simplification drops.
 */
class PedestrianReachTest {

    private fun at(lat: Double, accuracy: Float = 0f) =
        GpsPoint(latitude = lat, longitude = 0.0, accuracy = accuracy, timestamp = 0L, speed = 0f)

    /** ~111 m per 0.001° of latitude — enough precision for the thresholds under test. */
    private fun latFor(meters: Double) = meters / 111_320.0

    @Test
    fun should_accuse_a_vehicle_when_the_distance_beats_what_the_steps_could_walk() {
        // 200 m covered on 10 steps: no walker did that.
        assertTrue(
            outrunsPedestrianReach(
                base = at(0.0), fix = at(latFor(200.0)),
                steps = 10, strideMeters = 1f, floorMeters = 18f,
            ),
        )
    }

    @Test
    fun should_stay_silent_when_the_steps_cover_the_distance() {
        // 60 m on 80 steps — an ordinary walk away from the car.
        assertFalse(
            outrunsPedestrianReach(
                base = at(0.0), fix = at(latFor(60.0)),
                steps = 80, strideMeters = 1f, floorMeters = 18f,
            ),
        )
    }

    /**
     * The pro-person bias is deliberate: calling a walker a car costs a phantom spot, the reverse
     * only costs a question. Right at the reach the answer must be "not proven" — the comparison is
     * strictly greater-than.
     */
    @Test
    fun should_not_accuse_when_the_distance_sits_exactly_at_the_reach() {
        assertFalse(
            outrunsPedestrianReach(
                base = at(0.0), fix = at(latFor(28.0)),
                steps = 10, strideMeters = 1f, floorMeters = 18f,
            ),
        )
    }

    /**
     * A degraded fix inflates the reach through its OWN accuracy, so the answer fails conservative
     * instead of accusing a car on GPS noise. Same geometry, only the uncertainty changes.
     */
    @Test
    fun should_fail_conservative_when_the_fix_accuracy_is_poor() {
        val far = at(latFor(200.0), accuracy = 0f)
        val farButVague = at(latFor(200.0), accuracy = 300f)
        assertTrue(outrunsPedestrianReach(at(0.0), far, 10, 1f, 18f))
        assertFalse(
            outrunsPedestrianReach(at(0.0), farButVague, 10, 1f, 18f),
            "a 300 m accuracy envelope must swallow a 200 m displacement, not accuse a car with it",
        )
    }

    /**
     * A Doppler blip while standing AT the anchor can never qualify: with no step credit the
     * question becomes "has the position measurably left the envelope at all?", and a fix that
     * never escapes its own accuracy cannot have.
     */
    @Test
    fun should_never_accuse_a_still_phone_when_no_steps_are_credited() {
        assertFalse(
            outrunsPedestrianReach(
                base = at(0.0, accuracy = 25f), fix = at(latFor(30.0), accuracy = 25f),
                steps = 0, strideMeters = 1f, floorMeters = 18f,
            ),
        )
    }

    /**
     * The floor is the term that separates the four uses, and the reason they cannot be unified
     * into one parameterless helper. A real egress under-logs steps and loses GPS (field Calle
     * Gavia: 68 m walked on 8 logged steps), so the copies that must not strand a real park run a
     * far more generous floor than the ones policing a single fix. Same base, same fix, same steps
     * — opposite verdicts, purely on the floor.
     */
    @Test
    fun should_flip_on_the_floor_alone_when_everything_else_is_equal() {
        val base = at(0.0)
        val fix = at(latFor(100.0))
        assertTrue(
            outrunsPedestrianReach(base, fix, steps = 8, strideMeters = 1f, floorMeters = 18f),
            "the tight per-fix floor must call 100 m on 8 steps a vehicle",
        )
        assertFalse(
            outrunsPedestrianReach(base, fix, steps = 8, strideMeters = 1f, floorMeters = 150f),
            "the generous egress floor must let the same displacement pass as a real walk",
        )
    }
}
