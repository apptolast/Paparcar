package com.rndeveloper.paparcar.ui.components

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkedVehicleSummary
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The map's tap routing, tested where it can actually be called.
 *
 * The ids are NOT written by hand here: every one of them is produced by the same builder the render
 * path uses, so a builder that changes its shape tomorrow drags this test along with it instead of
 * leaving it asserting a string nobody emits any more.
 *
 * The rule the suite exists for: `my_car_asking_lt` used to start with `my_car`, so the open
 * question's marker was routed to the parked-car branch, found no session, and did nothing — and no
 * test could see it, because the `when` lived inside a `@Composable` lambda.
 * [UI-THE-ASK-MARKER-TAP-NEVER-REACHES-ITS-HANDLER-001]
 */
class MapMarkerIdsTest {

    // ── The corpus: every contentId shape PaparcarMapView can emit ──────────

    private data class EmittedId(val id: String, val expected: MarkerTapTarget)

    private val themes = listOf("lt", "dk")

    private fun summary(
        vehicleId: String,
        approximate: Boolean,
        bluetooth: Boolean,
        active: Boolean,
        color: VehicleColor?,
    ) = ParkedVehicleSummary(
        sessionId = "session_$vehicleId",
        vehicleId = vehicleId,
        displayName = "Car $vehicleId",
        location = GpsPoint(40.4168, -3.7038, accuracy = 8f, timestamp = 1L, speed = 0f),
        sizeCategory = VehicleSize.MEDIUM_SUV,
        carbodyType = CarbodyType.SUV_MEDIUM,
        stableRank = 0,
        isBluetoothPaired = bluetooth,
        color = color,
        isActive = active,
        zoneRadiusMeters = if (approximate) 154f else null,
    )

    private val summaries = listOf(
        summary("abcdef123456", approximate = false, bluetooth = true, active = true, color = null),
        summary("999888777666", approximate = true, bluetooth = false, active = false, color = VehicleColor.BLACK),
    )

    /** Badge markers — the normal parked-car surface, one per parked vehicle. */
    private val badgeIds: List<EmittedId> = summaries.flatMap { v ->
        themes.flatMap { theme ->
            listOf(
                vehicleBadgeContentId(v, selected = true, themeKey = theme),
                vehicleBadgeContentId(v, selected = false, dim = true, themeKey = theme),
                vehicleBadgeContentId(v, selected = false, dim = false, themeKey = theme),
            )
        }
    }.map { EmittedId(it, MarkerTapTarget.ParkedVehicle) }

    /** Legacy teardrop fallback — the Historial surface, which has no summary to build a badge from. */
    private val fallbackIds: List<EmittedId> =
        listOf(MARKER_MY_CAR, MARKER_MY_CAR_DIM, MARKER_MY_CAR_SELECTED).flatMap { base ->
            themes.flatMap { theme ->
                listOf(
                    fallbackParkingContentId(base, approximate = false, themeKey = theme),
                    fallbackParkingContentId(base, approximate = true, themeKey = theme),
                )
            }
        }.map { EmittedId(it, MarkerTapTarget.ParkedVehicle) }

    private val askIds: List<EmittedId> =
        themes.map { EmittedId(parkingAskContentId(it), MarkerTapTarget.ParkingAsk) }

    private val zoneIds: List<EmittedId> = listOf("z1", "home-zone").flatMap { zoneId ->
        listOf(true, false).flatMap { isPrivate ->
            listOf(true, false).map { dim -> zoneContentId(zoneId, isPrivate, dim) }
        }
    }.map { EmittedId(it, MarkerTapTarget.Zone) }

    private val freeSpotIds: List<EmittedId> = buildList {
        SpotFreshness.entries.forEach { tier ->
            listOf(true to false, false to true, false to false).forEach { (selected, dim) ->
                add(freeSpotContentId(tier, selected, dim))
                add(freeSpotContentId(tier, selected, dim, manual = true))
            }
        }
        // En-route counts get their own bucket keys, tier-independent. Stops at the bucket ceiling:
        // beyond it every count collapses onto the same id, which the distinctness check would read
        // as two builders colliding.
        (1..10).forEach { count ->
            add(freeSpotContentId(SpotFreshness.FRESH, selected = false, dim = false, enRouteCount = count))
        }
    }.map { EmittedId(it, MarkerTapTarget.FreeSpot) }

    /** Read-only scenery: nothing to open, on purpose. */
    private val inertIds: List<EmittedId> = buildList {
        add(MARKER_CLUSTER)
        add(MARKER_CLUSTER_DIM)
        add(MARKER_DEPARTURE)
        add(MARKER_ARRIVAL)
        add(MARKER_USER_DOT)
        add(locationActiveContentId(CarbodyType.SEDAN, VehicleColor.BLACK))
        add(locationActiveContentId(null, null))
    }.map { EmittedId(it, MarkerTapTarget.Inert) }

    private val corpus: List<EmittedId> =
        badgeIds + fallbackIds + askIds + zoneIds + freeSpotIds + inertIds

    // ── The population witness ──────────────────────────────────────────────
    // A suite that asserts a prohibition over an empty corpus is green for the wrong reason. These
    // floors sit at roughly half of what the builders produce today, so ordinary growth never trips
    // them but a corpus that quietly stops being built does.
    // [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]

    @Test
    fun should_exerciseEveryMarkerFamily_when_buildingTheCorpus() {
        assertTrue(corpus.size >= 35, "corpus collapsed to ${corpus.size} ids — nothing was checked")
        assertEquals(
            corpus.map { it.id }.distinct().size,
            corpus.size,
            "two builders emitted the SAME id — one of them is shadowing the other's bitmap",
        )
        val byTarget = corpus.groupBy { it.expected }
        listOf(
            MarkerTapTarget.ParkedVehicle to 10,
            MarkerTapTarget.ParkingAsk to 2,
            MarkerTapTarget.Zone to 4,
            MarkerTapTarget.FreeSpot to 12,
            MarkerTapTarget.Inert to 4,
        ).forEach { (target, floor) ->
            val found = byTarget[target].orEmpty().size
            assertTrue(found >= floor, "only $found ids witness $target — expected at least $floor")
        }
    }

    // ── Rule 1: every emitted id resolves to its own family ─────────────────

    @Test
    fun should_routeEveryEmittedId_to_itsOwnTarget() {
        corpus.forEach { (id, expected) ->
            assertEquals(expected, resolveMarkerTapTarget(id), "wrong destination for '$id'")
        }
    }

    @Test
    fun should_openTheQuestion_when_theAskMarkerIsTapped() {
        // The regression itself, spelled out: this is the tap that did nothing for two days.
        themes.forEach { theme ->
            assertEquals(
                MarkerTapTarget.ParkingAsk,
                resolveMarkerTapTarget(parkingAskContentId(theme)),
                "the open question's marker routed away from its own handler",
            )
        }
    }

    // ── Rule 2: the families are disjoint, so rule 1 cannot depend on branch order ──

    @Test
    fun should_matchNoOtherFamilysPrefix_when_anyIdIsEmitted() {
        // The prefixes `resolveMarkerTapTarget` tests, grouped by what they mean. An id that starts
        // with a prefix from a family that is not its own is a latent bug: it stays correct only for
        // as long as nobody reorders the branches. This is the assertion that goes red if
        // `parking_ask` is ever moved back under `my_car`.
        val prefixesByTarget: Map<MarkerTapTarget, List<String>> = mapOf(
            MarkerTapTarget.ParkedVehicle to listOf(MARKER_VEHICLE_BADGE_PREFIX, MARKER_MY_CAR),
            MarkerTapTarget.ParkingAsk to listOf(MARKER_PARKING_ASK),
            MarkerTapTarget.Zone to listOf(MARKER_ZONE_PREFIX),
            MarkerTapTarget.FreeSpot to listOf(MARKER_FREE_SPOT_PREFIX),
        )
        corpus.forEach { (id, expected) ->
            prefixesByTarget.forEach { (target, prefixes) ->
                if (target == expected) return@forEach
                prefixes.forEach { prefix ->
                    assertTrue(
                        !id.startsWith(prefix),
                        "'$id' belongs to $expected but also matches $target's prefix '$prefix' — " +
                            "the routing only works while the branches stay in this exact order",
                    )
                }
            }
        }
    }

    @Test
    fun should_beInert_when_theIdIsUnknownOrAbsent() {
        assertEquals(MarkerTapTarget.Inert, resolveMarkerTapTarget(null))
        assertEquals(MarkerTapTarget.Inert, resolveMarkerTapTarget(""))
        assertEquals(MarkerTapTarget.Inert, resolveMarkerTapTarget("something_nobody_emits"))
    }
}
