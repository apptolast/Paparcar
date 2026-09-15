package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.SharedCarInvite

/**
 * The join code: six characters somebody has to read out loud and somebody else has to type.
 * [VEH-SHARED-001]
 *
 * The alphabet drops every character a human confuses when dictating — `O`/`0` and `I`/`1`/`L` —
 * leaving 31 symbols, so a code is 31⁶ ≈ 8.9·10⁸ possibilities. That number is not what protects
 * the car: the invite is **single use**, expires in [INVITE_TTL_MS], and the rules only let the
 * holder claim it once. Guessing is not a realistic attack against a code that dies in a day;
 * forgetting to expire one is.
 */
object SharedCarInviteCode {

    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val INVITE_TTL_MS = 24 * 60 * 60 * 1000L

    /**
     * Builds a code from a stream of bytes. Randomness is the CALLER's (platform secure random or a
     * test's fixed sequence) — this function stays pure so the format has a testable owner.
     */
    fun of(bytes: ByteArray): String {
        require(bytes.size >= LENGTH) { "need at least $LENGTH bytes to build a code" }
        return buildString {
            for (i in 0 until LENGTH) {
                append(ALPHABET[(bytes[i].toInt() and 0xFF) % ALPHABET.length])
            }
        }
    }

    /**
     * What the user typed, as the document id it has to match: trimmed, upper-cased, and with the
     * separators people add by habit removed ("ab3-k9p" and "AB3 K9P" are the same code).
     * Returns null when what is left cannot be a code — the caller says "that code does not look
     * right", it does not go hunting in Firestore.
     */
    fun normalize(raw: String): String? {
        val cleaned = raw.uppercase().filter { it in ALPHABET }
        return cleaned.takeIf { it.length == LENGTH }
    }
}

/** Why an invite cannot be used. `null` = it can. Order matters: expired before claimed, because
 *  "caducó" is the one the user can fix by asking for another. */
enum class SharedCarInviteRejection { EXPIRED, ALREADY_USED, WRONG_CAR }

/** The verdict on an invite, at the moment somebody tries to redeem it. */
fun rejectionFor(
    invite: SharedCarInvite,
    carId: String? = null,
    nowMs: Long,
): SharedCarInviteRejection? = when {
    invite.expiresAtMs <= nowMs -> SharedCarInviteRejection.EXPIRED
    invite.claimedByUid != null -> SharedCarInviteRejection.ALREADY_USED
    carId != null && invite.carId != carId -> SharedCarInviteRejection.WRONG_CAR
    else -> null
}
