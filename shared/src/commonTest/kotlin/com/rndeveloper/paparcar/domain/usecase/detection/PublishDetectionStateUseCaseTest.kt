package com.rndeveloper.paparcar.domain.usecase.detection

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.detection.DetectionStateLabel
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityReport
import com.rndeveloper.paparcar.domain.model.DetectionTier
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.FakeOemBackgroundReliabilityManager
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
 *
 * The contract under test is a COST contract as much as a correctness one: `permissionState` is
 * refreshed on every resume, so a publisher that wrote on observation would spend one Firestore
 * write per app opening per user. Most of what follows counts writes.
 */
class PublishDetectionStateUseCaseTest {

    private val permissions = FakePermissionManager()
    private val preferences = FakeAppPreferences()
    private val profiles = FakeUserProfileRepository()
    private val analytics = FakeAnalyticsTracker()
    private val auth = FakeAuthRepository(
        initialSession = UserSession(userId = "uid-1", email = null, displayName = null, photoUrl = null),
    )
    private val vehicles = FakeVehicleRepository()

    private fun useCase(
        auth: FakeAuthRepository = this.auth,
        aggressiveOem: Boolean = false,
    ) = PublishDetectionStateUseCase(
        permissionManager = permissions,
        appPreferences = preferences,
        observeDetectionReliability = ObserveDetectionReliabilityUseCase(
            vehicleRepository = vehicles,
            permissionManager = permissions,
            oemBackgroundReliabilityManager = FakeOemBackgroundReliabilityManager(
                requiresAutostartWhitelist = aggressiveOem,
            ),
            strategyResolver = ParkingStrategyResolver(vehicles, FakeBluetoothScanner()),
            evaluateDetectionReliability = EvaluateDetectionReliabilityUseCase(
                capabilities = DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true),
            ),
        ),
        userProfileRepository = profiles,
        authRepository = auth,
        analyticsTracker = analytics,
        platform = "android",
        appVersionName = "1.2.0",
    )

    private fun report(level: DetectionReliabilityLevel = DetectionReliabilityLevel.GOOD) =
        DetectionReliabilityReport(level = level, tier = DetectionTier.ASSISTED, issues = emptyList())

    private fun granted(batteryExempt: Boolean = true) =
        FakePermissionManager.allGranted().copy(isBatteryOptimizationExempt = batteryExempt)

    // ── The write budget ────────────────────────────────────────────────────────────────────────

    @Test
    fun should_writeOnce_when_theSameStateIsObservedRepeatedly() = runTest {
        val sut = useCase()
        permissions.emit(granted())
        val (snapshot, reliability) = sut.observe().first()

        repeat(10) { sut.publish(snapshot, reliability) }

        assertEquals(
            1,
            profiles.detectionStateWrites.size,
            "ten resumes of an unchanged state must cost exactly one Firestore write",
        )
    }

    @Test
    fun should_writeAgain_when_aPermissionIsRevoked() = runTest {
        val sut = useCase()
        permissions.emit(granted())
        val before = sut.observe().first()
        sut.publish(before.first, before.second)

        permissions.emit(granted().copy(hasBackgroundLocationPermission = false))
        val after = sut.observe().first()
        sut.publish(after.first, after.second)

        assertEquals(2, profiles.detectionStateWrites.size)
        assertEquals(
            listOf(RequiredPermission.BACKGROUND_LOCATION.name),
            profiles.detectionStateWrites.last().blockers,
        )
    }

    /** The intent must survive a revocation untouched — forgetting a deliberate OFF because a
     *  permission lapsed would re-arm detection behind the user's back.
     *  [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] */
    @Test
    fun should_reportIntentAndBlockersApart_when_theUserWantsItButCannotHaveIt() = runTest {
        val sut = useCase()
        permissions.emit(AppPermissionState(hasLocationPermission = true))

        val (snapshot, reliability) = sut.observe().first()
        sut.publish(snapshot, reliability)

        val write = profiles.detectionStateWrites.single()
        assertTrue(write.autoDetectParking, "the stored intent defaults to true and must travel as true")
        assertTrue(write.blockers.isNotEmpty(), "and the blockers must travel beside it, not instead of it")
    }

    /** A failed write must NOT bank the fingerprint, or a state that never arrived would be treated
     *  as published and the next attempt would never come. */
    @Test
    fun should_retryNextTime_when_theWriteFailed() = runTest {
        val sut = useCase()
        permissions.emit(granted())
        profiles.detectionStateResult = Result.failure(IllegalStateException("offline"))

        val (snapshot, reliability) = sut.observe().first()
        sut.publish(snapshot, reliability)
        assertNull(preferences.lastPublishedDetectionState, "a failed write must bank nothing")

        profiles.detectionStateResult = Result.success(Unit)
        sut.publish(snapshot, reliability)
        assertEquals(2, profiles.detectionStateWrites.size, "the unchanged state must be retried")
    }

    @Test
    fun should_writeNothing_when_nobodyIsSignedIn() = runTest {
        val sut = useCase(auth = FakeAuthRepository(initialSession = null))
        permissions.emit(granted())

        val (snapshot, reliability) = sut.observe().first()
        sut.publish(snapshot, reliability)

        assertTrue(profiles.detectionStateWrites.isEmpty())
        assertNull(
            preferences.lastPublishedDetectionState,
            "stamping the fingerprint signed-out would make the next sign-in believe it had reported",
        )
    }

    // ── Analytics: the free sink, so no dedupe ──────────────────────────────────────────────────

    @Test
    fun should_setThePropertyEveryTime_when_theStateIsUnchanged() = runTest {
        val sut = useCase()
        permissions.emit(granted())
        val (snapshot, reliability) = sut.observe().first()

        repeat(3) { sut.publish(snapshot, reliability) }

        // Three emissions × two properties. The user property costs no network call of its own, and
        // dropping it on the deduped path would bias the population counts it exists to produce.
        assertEquals(6, analytics.properties.size)
        assertEquals(1, profiles.detectionStateWrites.size)
    }

    @Test
    fun should_reportBlocked_when_wantedButAPermissionIsMissing() = runTest {
        val sut = useCase()
        permissions.emit(AppPermissionState(hasLocationPermission = true))

        val (snapshot, reliability) = sut.observe().first()
        sut.publish(snapshot, reliability)

        val state = analytics.properties.filterIsInstance<AnalyticsUserProperty.DetectionState>().single()
        assertEquals(DetectionStateLabel.BLOCKED_PERMISSIONS, state.label)
    }

    @Test
    fun should_reportOffByUser_when_theMasterSwitchIsOff() = runTest {
        val sut = useCase()
        permissions.emit(granted())
        preferences.setAutoDetectParking(false)

        val (snapshot, reliability) = sut.observe().first()
        sut.publish(snapshot, reliability)

        val state = analytics.properties.filterIsInstance<AnalyticsUserProperty.DetectionState>().single()
        assertEquals(DetectionStateLabel.OFF_BY_USER, state.label)
        assertEquals(false, profiles.detectionStateWrites.single().autoDetectParking)
    }

    /** The reliability axis reaches analytics and must NOT reach Firestore — it is not a stored
     *  field, so a write on its account would rewrite three identical values. */
    @Test
    fun should_reportReducedWithoutWriting_when_onlyTheEnvironmentIsHostile() = runTest {
        val sut = useCase(aggressiveOem = true)
        permissions.emit(granted(batteryExempt = false))

        val (snapshot, reliability) = sut.observe().first()
        assertEquals(DetectionReliabilityLevel.REDUCED, reliability.level)
        sut.publish(snapshot, reliability)
        val writesAfterFirst = profiles.detectionStateWrites.size

        // Same permissions, same switch, same battery flag → nothing stored has changed.
        sut.publish(snapshot, report(DetectionReliabilityLevel.GOOD))

        assertEquals(writesAfterFirst, profiles.detectionStateWrites.size)
        assertEquals(
            DetectionStateLabel.REDUCED_RELIABILITY,
            analytics.properties.filterIsInstance<AnalyticsUserProperty.DetectionState>().first().label,
        )
    }
}
