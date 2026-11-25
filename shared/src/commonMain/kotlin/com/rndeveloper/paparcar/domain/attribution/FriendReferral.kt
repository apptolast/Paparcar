package com.rndeveloper.paparcar.domain.attribution

/** Wire contract for the share link and the existing Play Install Referrer reader. */
internal object FriendReferral {
    const val SOURCE = "friend"
    const val MEDIUM = "referral"
    const val CODE_LENGTH = 32

    private const val INVITE_CODE = "invite_code"
    private val codePattern = Regex("[0-9a-f]{$CODE_LENGTH}")

    fun isTagged(params: Map<String, String>): Boolean =
        SOURCE.equals(params[UTM_SOURCE], ignoreCase = true) &&
            MEDIUM.equals(params[UTM_MEDIUM], ignoreCase = true)

    fun codeFrom(params: Map<String, String>): String? =
        params[INVITE_CODE]?.takeIf(codePattern::matches)

    fun referrer(code: String): String =
        "$UTM_SOURCE=$SOURCE&$UTM_MEDIUM=$MEDIUM&$INVITE_CODE=$code"
}
