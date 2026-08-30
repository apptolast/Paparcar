package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The physical rules a spot must satisfy per carbody — read by `SpotFit` and by the carbody info
 * card, and until now by no test at all, despite the function's own KDoc promising it was written
 * *"in pure Kotlin so it can be reused on both platforms and exercised by unit tests"*.
 *
 * The interesting content of `getParkingRules` is not the three width constants; it is the
 * PRECEDENCE of the advisory, which is a four-branch `when` whose order encodes two decisions a
 * reader would not guess:
 *
 *  - **a large SUV is warned about columns, not overhang.** It shares `LARGE_SEDAN` with the sedan
 *    and the estate, so length would be the obvious advisory — but the branch that names those two
 *    carbodies explicitly runs first, and `SUV_LARGE` is not in it, so it falls through to width.
 *  - **height outranks width.** A commercial van and a pickup are the two widest bodies in the
 *    catalogue *and* the tallest, and they are told about the clearance bar, because that is the one
 *    that stops you from entering the garage at all.
 *
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class VehicleParkingRulesTest {

    /**
     * The whole table in one place, so the two precedence facts above are visible as a column
     * rather than as prose. Adding a carbody without a row here fails the census below.
     */
    private val expected = mapOf(
        CarbodyType.HATCHBACK_SMALL to Expectation(2.20, false, ParkingAlertKey.STANDARD),
        CarbodyType.SUV_SMALL to Expectation(2.20, false, ParkingAlertKey.STANDARD),
        CarbodyType.HATCHBACK_MEDIUM to Expectation(2.20, false, ParkingAlertKey.STANDARD),
        CarbodyType.SUV_MEDIUM to Expectation(2.40, false, ParkingAlertKey.WIDE_CAR),
        CarbodyType.SEDAN to Expectation(2.20, false, ParkingAlertKey.LONG_CAR),
        CarbodyType.FAMILY_LONG to Expectation(2.20, false, ParkingAlertKey.LONG_CAR),
        CarbodyType.SUV_LARGE to Expectation(2.40, false, ParkingAlertKey.WIDE_CAR),
        CarbodyType.VAN_LIGHT to Expectation(2.20, true, ParkingAlertKey.HIGH_CEILING),
        CarbodyType.VAN_COMMERCIAL to Expectation(2.50, true, ParkingAlertKey.HIGH_CEILING),
        CarbodyType.PICKUP to Expectation(2.50, true, ParkingAlertKey.HIGH_CEILING),
    )

    @Test
    fun should_resolve_the_declared_rules_for_every_carbody() {
        val mismatches = CarbodyType.entries.mapNotNull { body ->
            val want = expected.getValue(body)
            val got = body.getParkingRules()
            val actual = Expectation(got.minPlazaWidthMeters, got.requiresHighCeiling, got.alertKey)
            if (actual == want) null else "  - $body: expected $want, got $actual"
        }
        assertTrue(mismatches.isEmpty(), "carbody rules drifted:\n${mismatches.joinToString("\n")}")
    }

    /**
     * The census. A new carbody type silently inherits `else -> MIN_WIDTH_STANDARD_METERS` and, if
     * it is not tall, `STANDARD` — so the dangerous failure here is not a wrong row, it is a body
     * that nobody classified reading as "fits almost anywhere".
     */
    @Test
    fun should_classify_every_carbody_the_catalogue_declares() {
        val unclassified = CarbodyType.entries.filterNot { it in expected }
        assertTrue(
            unclassified.isEmpty(),
            "[unclassified carbody] ${unclassified.joinToString()} — a new body falls through to " +
                "the standard width and the STANDARD advisory by default, which reads as 'fits " +
                "almost anywhere'. Decide its width, height and advisory here and in getParkingRules.",
        )
    }

    /**
     * Height outranks width. Both of these are 2.50 m wide — the widest in the catalogue — and both
     * are told about the clearance bar rather than about columns.
     */
    @Test
    fun should_warn_about_the_clearance_bar_rather_than_the_columns_when_a_body_is_both_tall_and_wide() {
        listOf(CarbodyType.VAN_COMMERCIAL, CarbodyType.PICKUP).forEach { body ->
            val rules = body.getParkingRules()
            assertTrue(rules.requiresHighCeiling, "$body does not fit under a standard garage")
            assertEquals(2.50, rules.minPlazaWidthMeters, "$body is as wide as the catalogue gets")
            assertEquals(
                ParkingAlertKey.HIGH_CEILING,
                rules.alertKey,
                "$body is wide AND tall — the bar you cannot pass wins over the column you can avoid",
            )
        }
    }

    /**
     * Length outranks width, and only for the two bodies named by length. The large SUV shares
     * their size class and is deliberately NOT warned about overhang.
     */
    @Test
    fun should_warn_a_large_suv_about_width_even_though_it_shares_its_size_class_with_the_long_cars() {
        assertEquals(VehicleSize.LARGE_SEDAN, CarbodyType.SUV_LARGE.sizeCategory)
        assertEquals(VehicleSize.LARGE_SEDAN, CarbodyType.SEDAN.sizeCategory)

        assertEquals(ParkingAlertKey.LONG_CAR, CarbodyType.SEDAN.getParkingRules().alertKey)
        assertEquals(ParkingAlertKey.WIDE_CAR, CarbodyType.SUV_LARGE.getParkingRules().alertKey)
    }

    /** The high-ceiling flag tracks the size axis, not the body: it is true for exactly the vans. */
    @Test
    fun should_require_a_high_ceiling_for_exactly_the_van_sized_bodies() {
        CarbodyType.entries.forEach { body ->
            assertEquals(
                body.sizeCategory == VehicleSize.VAN_HIGH,
                body.getParkingRules().requiresHighCeiling,
                "$body (${body.sizeCategory}) disagrees with its size class about garage clearance",
            )
        }
    }

    /** A standard advisory means no caveat on any axis — worth pinning, since it is the default. */
    @Test
    fun should_reserve_the_standard_advisory_for_bodies_with_no_caveat_on_either_axis() {
        CarbodyType.entries
            .filter { it.getParkingRules().alertKey == ParkingAlertKey.STANDARD }
            .forEach { body ->
                val rules = body.getParkingRules()
                assertFalse(rules.requiresHighCeiling, "$body is tall — that is not 'standard'")
                assertEquals(2.20, rules.minPlazaWidthMeters, "$body is wide — that is not 'standard'")
            }
    }

    private data class Expectation(
        val minWidth: Double,
        val highCeiling: Boolean,
        val alert: ParkingAlertKey,
    ) {
        override fun toString() = "(${minWidth}m, ceiling=$highCeiling, $alert)"
    }
}
