package com.rndeveloper.paparcar.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rndeveloper.paparcar.domain.attribution.FriendReferral
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.settings_invite_message
import java.net.URLEncoder
import java.security.MessageDigest

@Composable
internal actual fun rememberInviteFriendAction(userId: String?): (() -> Unit)? {
    if (userId.isNullOrBlank()) return null

    val context = LocalContext.current
    val link = remember(userId) { friendReferralPlayLink(userId) }
    val message = stringResource(Res.string.settings_invite_message, link)
    return {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, message)
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (error: ActivityNotFoundException) {
            PaparcarLogger.w(TAG, "Android share sheet unavailable", error)
        }
    }
}

internal fun friendReferralPlayLink(userId: String): String {
    val code = MessageDigest.getInstance("SHA-256")
        .digest("$INVITATION_CODE_NAMESPACE$userId".encodeToByteArray())
        .toHexString()
        .take(FriendReferral.CODE_LENGTH)
    val referrer = URLEncoder.encode(FriendReferral.referrer(code), Charsets.UTF_8.name())
    return "$PLAY_LISTING&referrer=$referrer"
}

private const val TAG = "InviteFriend"
private const val INVITATION_CODE_NAMESPACE = "paparcar:friend-invite:v1:"
private const val PLAY_LISTING = "https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar"
