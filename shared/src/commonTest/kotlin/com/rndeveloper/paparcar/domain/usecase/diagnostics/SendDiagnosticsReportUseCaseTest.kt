package com.rndeveloper.paparcar.domain.usecase.diagnostics

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
