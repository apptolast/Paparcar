package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] The four questions a vehicle type
 * answers, as one table.
 *
 * The point of the table is not the individual cells — those are one-line `when`s. It is that the
 * four columns **do not agree**, and that the reason they look like they do is an accident of
 * today's four constants:
 *
 *  - `isHumanPowered` and `parksInASpot` are exact complements here, and would stop being so the day
 *    a moped is added (motorised, and it does not take a car space).
 *  - `hasCarbody` and `slowTripContradictsProfile` are both true for `CAR` alone, for entirely
 *    unrelated reasons: one is about the registration form asking for a body shape, the other is the
 *    scooter mismatch guard — a `CAR` profile crawling for minutes is more likely a misfiled vehicle
 *    than a car in traffic. `MOTORCYCLE` answers `false` to the second because it already IS the
 *    small slow-capable vehicle. [BUG-SCOOTER-001]
 *
 * Collapsing any two of them into one property would pass every test in this file today and be
 * wrong on the next constant. The census below is what makes adding that constant a decision instead
 * of an inheritance. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class VehicleTypeQuestionsTest {

    /** humanPowered · parksInASpot · hasCarbody · slowTripContradictsProfile */
    private val expected = mapOf(
        VehicleType.CAR to Answers(humanPowered = false, parks = true, carbody = true, slowIsOdd = true),
        VehicleType.MOTORCYCLE to Answers(humanPowered = false, parks = true, carbody = false, slowIsOdd = false),
        VehicleType.SCOOTER to Answers(humanPowered = true, parks = false, carbody = false, slowIsOdd = false),
        VehicleType.BIKE to Answers(humanPowered = true, parks = false, carbody = false, slowIsOdd = false),
    )

    @Test
    fun should_answer_the_four_questions_as_declared_for_every_type() {
        val mismatches = VehicleType.entries.mapNotNull { type ->
            val want = expected.getValue(type)
            val got = Answers(
                humanPowered = type.isHumanPowered,
                parks = type.parksInASpot,
                carbody = type.hasCarbody,
                slowIsOdd = type.slowTripContradictsProfile,
            )
            if (got == want) null else "  - $type: expected $want, got $got"
        }
        assertTrue(mismatches.isEmpty(), "vehicle type answers drifted:\n${mismatches.joinToString("\n")}")
    }

    /**
     * The census — the reason this ticket exists. Before it, every one of these four answers was
     * spelled out at the site that needed it (`== SCOOTER || == BIKE` in the human-power veto, the
     * same pair as a `NON_PARKING_TYPES` set in the strategy resolver, `== CAR` five times across
     * registration), so a new constant became a car that parks, with a body, whose slow trips are
     * suspicious — without anyone deciding that.
     */
    @Test
    fun should_classify_every_vehicle_type_the_enum_declares() {
        val unclassified = VehicleType.entries.filterNot { it in expected }
        assertTrue(
            unclassified.isEmpty(),
            "[unclassified vehicle type] ${unclassified.joinToString()} — add its row here AND " +
                "answer the four `when`s in VehicleType. The compiler forces the four; this forces " +
                "somebody to look at what the answers mean together.",
        )
    }

    /**
     * A type that does not park is never asked to auto-confirm, so the mismatch guard has nothing to
     * contradict — the guard's suppression must not be the only thing standing between a bike and a
     * pin. This is the ORDER the two lanes rely on: `isHumanPowered` stops the session earlier.
     */
    @Test
    fun should_never_ask_a_human_powered_type_whether_a_slow_trip_contradicts_it() {
        VehicleType.entries.filter { it.isHumanPowered }.forEach { type ->
            assertEquals(
                false,
                type.slowTripContradictsProfile,
                "$type is muscle-powered — slowness is its normal, and it never reaches the guard",
            )
            assertEquals(false, type.parksInASpot, "$type is dismounted on the sidewalk")
        }
    }

    /** A body shape only makes sense for something that takes a car-sized space. */
    @Test
    fun should_only_expect_a_carbody_from_a_type_that_parks() {
        VehicleType.entries.filter { it.hasCarbody }.forEach { type ->
            assertTrue(type.parksInASpot, "$type has a body but reportedly takes no parking space")
            assertEquals(false, type.isHumanPowered, "$type has a body but reportedly has no engine")
        }
    }

    /**
     * The columns are not aliases of each other. Stated as a test so that collapsing two of them
     * into one property — which would be green everywhere else — has to happen against an explicit
     * claim about WHY they are separate.
     */
    @Test
    fun should_keep_the_four_questions_distinguishable() {
        val parksButNoBody = VehicleType.entries.filter { it.parksInASpot && !it.hasCarbody }
        assertTrue(
            parksButNoBody.isNotEmpty(),
            "no type parks without a carbody — 'takes a space' and 'has a body shape' would be " +
                "indistinguishable, and the next type to need them apart has nothing to point at",
        )
        val parksButSlowIsNormal = VehicleType.entries.filter { it.parksInASpot && !it.slowTripContradictsProfile }
        assertTrue(
            parksButSlowIsNormal.isNotEmpty(),
            "no type parks while being allowed to be slow — the mismatch guard would be a synonym " +
                "of 'parks', which is not what BUG-SCOOTER-001 measured",
        )
    }

    private data class Answers(
        val humanPowered: Boolean,
        val parks: Boolean,
        val carbody: Boolean,
        val slowIsOdd: Boolean,
    ) {
        override fun toString() =
            "(muscle=$humanPowered, parks=$parks, body=$carbody, slowIsOdd=$slowIsOdd)"
    }
}
