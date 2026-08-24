package io.apptolast.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-SESSION-BIRTH-001] The session-birth filter, now that it is one function instead of five
 * hand-written comparisons.
 *
 * The boundary and the two nulls are what these tests exist for: they are the three things a caller
 * used to decide for itself, differently, in four different files.
 */
class EvidenceAdmissibilityTest {

    @Test
    fun should_admit_the_evidence_when_it_happened_after_the_session_began() {
        assertTrue(isAdmissibleEvidence(evidenceAtMs = 1_000L, sessionStartMs = 900L))
    }

    @Test
    fun should_reject_the_evidence_when_it_predates_the_session() {
        // The 2026-07-08 18:52 field case in one line: a 17-min-old re-delivered ENTER.
        assertFalse(isAdmissibleEvidence(evidenceAtMs = 900L, sessionStartMs = 1_000L))
    }

    /**
     * The boundary is INCLUSIVE, and it has to be: the arming evidence of a session frequently
     * carries the session's own start instant, and an exclusive bound would throw away the very
     * signal that opened it.
     */
    @Test
    fun should_admit_the_evidence_when_it_lands_exactly_at_the_session_start() {
        assertTrue(isAdmissibleEvidence(evidenceAtMs = 1_000L, sessionStartMs = 1_000L))
    }

    /**
     * Fail CLOSED on absent evidence. Nothing to admit means nothing may be licensed — the
     * asymmetric-failure principle: a false negative costs a prompt, a false positive costs a
     * phantom spot.
     */
    @Test
    fun should_reject_when_there_is_no_evidence_at_all() {
        assertFalse(isAdmissibleEvidence(evidenceAtMs = null, sessionStartMs = 1_000L))
        assertFalse(isAdmissibleEvidence(evidenceAtMs = null, sessionStartMs = null))
    }

    /**
     * Fail OPEN on an unknown session birth — the OPPOSITE default, deliberately. Not knowing when
     * the session began is not grounds to throw away a real signal; the caller's own guards still
     * have to clear it. `VerifyDepartureEvidenceUseCase` is the caller that depends on this, and it
     * is the reason the shared function takes a nullable session start at all.
     */
    @Test
    fun should_admit_when_the_session_birth_is_unknown() {
        assertTrue(isAdmissibleEvidence(evidenceAtMs = 900L, sessionStartMs = null))
        assertTrue(isAdmissibleEvidence(evidenceAtMs = 0L, sessionStartMs = null))
    }
}
