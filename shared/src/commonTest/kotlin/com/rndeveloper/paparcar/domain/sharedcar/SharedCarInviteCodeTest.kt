package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.SharedCarInvite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The join code and its verdicts. [VEH-SHARED-001] */
class SharedCarInviteCodeTest {

    @Test
    fun should_buildSixCharactersOfTheAlphabet_when_givenBytes() {
        val code = SharedCarInviteCode.of(ByteArray(8) { it.toByte() })

        assertEquals(SharedCarInviteCode.LENGTH, code.length)
        assertTrue(code.all { it in SharedCarInviteCode.ALPHABET })
    }

    @Test
    fun should_excludeTheCharactersPeopleConfuse_when_dictatingACode() {
        // O/0 and I/1/L are the pairs that turn "read it out loud" into three attempts.
        listOf('O', '0', 'I', '1', 'L').forEach { ambiguous ->
            assertTrue(ambiguous !in SharedCarInviteCode.ALPHABET, "$ambiguous must not be in the alphabet")
        }
    }

    @Test
    fun should_acceptWhatPeopleActuallyType_when_normalising() {
        // Lower case, a dash somebody added, and a space in the middle: same code.
        assertEquals("AB3K9P", SharedCarInviteCode.normalize("ab3-k9p"))
        assertEquals("AB3K9P", SharedCarInviteCode.normalize(" AB3 K9P "))
    }

    @Test
    fun should_returnNull_when_whatWasTypedCannotBeACode() {
        assertNull(SharedCarInviteCode.normalize("AB3K9"))
        assertNull(SharedCarInviteCode.normalize("AB3K9PQ"))
        // O and I are not in the alphabet, so a code full of them has nothing left after cleaning.
        assertNull(SharedCarInviteCode.normalize("OIOIOI"))
    }

    @Test
    fun should_rejectAnInvite_when_itHasExpired() {
        val invite = invite(expiresAtMs = 1_000L)

        assertEquals(SharedCarInviteRejection.EXPIRED, rejectionFor(invite, nowMs = 1_000L))
        assertEquals(SharedCarInviteRejection.EXPIRED, rejectionFor(invite, nowMs = 1_001L))
    }

    @Test
    fun should_rejectAnInvite_when_somebodyElseAlreadyUsedIt() {
        val invite = invite(expiresAtMs = 10_000L, claimedByUid = "nacho")

        assertEquals(SharedCarInviteRejection.ALREADY_USED, rejectionFor(invite, nowMs = 1_000L))
    }

    @Test
    fun should_preferExpiredOverUsed_when_anInviteIsBoth() {
        // "It expired, ask for another" is the one the user can act on; "already used" on a dead
        // code would send them looking for a culprit that does not matter.
        val invite = invite(expiresAtMs = 1_000L, claimedByUid = "nacho")

        assertEquals(SharedCarInviteRejection.EXPIRED, rejectionFor(invite, nowMs = 2_000L))
    }

    @Test
    fun should_acceptAnInvite_when_itIsLiveAndUnclaimed() {
        assertNull(rejectionFor(invite(expiresAtMs = 10_000L), nowMs = 9_999L))
    }

    // [SEC-A-REPLACED-INVITE-STILL-OPENS-THE-DOOR-001]
    @Test
    fun should_rejectAnInvite_when_theOwnerHasIssuedANewOne() {
        val invite = invite(expiresAtMs = 10_000L)

        assertEquals(
            SharedCarInviteRejection.SUPERSEDED,
            rejectionFor(invite, activeCode = "OTHER1", nowMs = 1_000L),
        )
    }

    @Test
    fun should_acceptAnInvite_when_itIsStillTheActiveCode() {
        val invite = invite(expiresAtMs = 10_000L)

        assertNull(rejectionFor(invite, activeCode = invite.code, nowMs = 1_000L))
    }

    @Test
    fun should_notRejectAsSuperseded_when_theActiveCodeIsUnknown() {
        // No car to compare against (deleted, or the caller never asked) — the orphan check is a
        // defense in depth, not a requirement to know the car's state.
        assertNull(rejectionFor(invite(expiresAtMs = 10_000L), activeCode = null, nowMs = 1_000L))
    }

    @Test
    fun should_preferExpiredOverSuperseded_when_anInviteIsBoth() {
        val invite = invite(expiresAtMs = 1_000L)

        assertEquals(
            SharedCarInviteRejection.EXPIRED,
            rejectionFor(invite, activeCode = "OTHER1", nowMs = 2_000L),
        )
    }

    private fun invite(expiresAtMs: Long, claimedByUid: String? = null) = SharedCarInvite(
        code = "AB3K9P",
        carId = "car-1",
        ownerUid = "marta",
        createdAtMs = 0L,
        expiresAtMs = expiresAtMs,
        claimedByUid = claimedByUid,
    )
}
