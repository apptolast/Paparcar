package com.rndeveloper.paparcar.domain.usecase.diagnostics

import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.diagnostics.UnknownDeviceInfoProvider
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDiagnosticsReportUploader
import com.rndeveloper.paparcar.fakes.FakeLocalDiagnosticsLog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SendDiagnosticsReportUseCaseTest {

    private val session = FakeAuthRepository.authenticatedSession()

    private fun buildDeps(
        withSession: Boolean = true,
        logSnapshot: ByteArray? = byteArrayOf(1, 2, 3),
        withLocalLog: Boolean = true,
    ) = object {
        val auth = FakeAuthRepository(initialSession = if (withSession) session else null)
        val uploader = FakeDiagnosticsReportUploader()
        val localLog = FakeLocalDiagnosticsLog(logSnapshot)
        val useCase get() = SendDiagnosticsReportUseCase(
            authRepository = auth,
            uploader = uploader,
            localLog = if (withLocalLog) localLog else null,
            deviceInfo = UnknownDeviceInfoProvider,
        )
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should_returnSuccess_when_uploadSucceeds`() = runTest {
        val d = buildDeps()
        assertTrue(d.useCase().isSuccess)
    }

    @Test
    fun `should_shipTheDeviceLog_when_reportIsSent`() = runTest {
        val d = buildDeps()
        d.useCase()
        assertEquals(1, d.uploader.uploadCallCount)
        assertContentEquals(byteArrayOf(1, 2, 3), d.uploader.lastReport?.logGzip)
    }

    @Test
    fun `should_stampTheSessionUser_when_reportIsSent`() = runTest {
        val d = buildDeps()
        d.useCase()
        assertEquals(session.userId, d.uploader.lastReport?.userId)
    }

    // ── The user's own words [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] ───

    @Test
    fun `should_shipTheUserDescription_when_reportIsSent`() = runTest {
        val d = buildDeps()
        d.useCase("The pin landed on the previous street")
        assertEquals("The pin landed on the previous street", d.uploader.lastReport?.message)
    }

    @Test
    fun `should_trimTheDescription_when_itHasSurroundingWhitespace`() = runTest {
        val d = buildDeps()
        d.useCase("  the pin was wrong \n")
        assertEquals("the pin was wrong", d.uploader.lastReport?.message)
    }

    // The dialog already caps typing; this proves the ceiling is the DOMAIN's, so a report built
    // by any other path still cannot ship an unbounded string into a header doc.
    @Test
    fun `should_capTheDescription_when_itExceedsTheCeiling`() = runTest {
        val d = buildDeps()
        d.useCase("x".repeat(DiagnosticsReport.MAX_MESSAGE_CHARS + 250))
        assertEquals(DiagnosticsReport.MAX_MESSAGE_CHARS, d.uploader.lastReport?.message?.length)
    }

    @Test
    fun `should_keepTheWholeDescription_when_itSitsExactlyOnTheCeiling`() = runTest {
        val d = buildDeps()
        val exact = "y".repeat(DiagnosticsReport.MAX_MESSAGE_CHARS)
        d.useCase(exact)
        assertEquals(exact, d.uploader.lastReport?.message)
    }

    // Evidence without words is still the part that cannot be recovered later, so a blank
    // description must never block the upload.
    @Test
    fun `should_stillUploadTheLog_when_descriptionIsBlank`() = runTest {
        val d = buildDeps()
        assertTrue(d.useCase("   ").isSuccess)
        assertEquals(1, d.uploader.uploadCallCount)
        assertEquals("", d.uploader.lastReport?.message)
        assertContentEquals(byteArrayOf(1, 2, 3), d.uploader.lastReport?.logGzip)
    }

    // ── No local log — the complaint itself is still signal ────────────────────

    @Test
    fun `should_stillUpload_when_deviceHasNoLogYet`() = runTest {
        val d = buildDeps(logSnapshot = null)
        assertTrue(d.useCase().isSuccess)
        assertEquals(1, d.uploader.uploadCallCount)
        assertNull(d.uploader.lastReport?.logGzip)
    }

    @Test
    fun `should_stillUpload_when_platformHasNoLocalLogPort`() = runTest {
        val d = buildDeps(withLocalLog = false)
        assertTrue(d.useCase().isSuccess)
        assertEquals(1, d.uploader.uploadCallCount)
        assertNull(d.uploader.lastReport?.logGzip)
    }

    // ── No active session ─────────────────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_noActiveSession`() = runTest {
        val d = buildDeps(withSession = false)
        assertTrue(d.useCase().isFailure)
    }

    @Test
    fun `should_notReadTheLogNorUpload_when_noActiveSession`() = runTest {
        val d = buildDeps(withSession = false)
        d.useCase()
        assertEquals(0, d.localLog.snapshotCallCount)
        assertEquals(0, d.uploader.uploadCallCount)
    }

    // ── Upload failure ────────────────────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_uploadFails`() = runTest {
        val d = buildDeps()
        d.uploader.uploadResult = Result.failure(Exception("network error"))
        assertTrue(d.useCase().isFailure)
    }
}
