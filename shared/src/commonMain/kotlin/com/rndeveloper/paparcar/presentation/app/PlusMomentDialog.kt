package com.rndeveloper.paparcar.presentation.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
import com.rndeveloper.paparcar.presentation.paywall.PLUS_BENEFITS
import com.rndeveloper.paparcar.presentation.paywall.PlusBenefitRow
import com.rndeveloper.paparcar.presentation.util.shortDateText
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapPremiumMark
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.plus_promo_ending_benefits
import paparcar.composeapp.generated.resources.plus_promo_ending_body
import paparcar.composeapp.generated.resources.plus_promo_ending_cta
import paparcar.composeapp.generated.resources.plus_promo_ending_see_plans
import paparcar.composeapp.generated.resources.plus_promo_ending_title
import paparcar.composeapp.generated.resources.plus_welcome_benefits
import paparcar.composeapp.generated.resources.plus_welcome_body
import paparcar.composeapp.generated.resources.plus_welcome_cta
import paparcar.composeapp.generated.resources.plus_welcome_title
import paparcar.composeapp.generated.resources.plus_welcome_until

/**
 * The two things the app says about a Plus nobody paid for. [MONEY-FOUNDERS-WELCOME-001]
 *
 * **A dialog, and not a bottom sheet.** On Home the sheet IS already a modal surface, and a second
 * modal on top of it is the one thing this project forbids outright — nobody can tell what closes
 * what, and the layer underneath goes inert. A dialog floats over whichever tab the user is on and
 * owns the screen alone.
 *
 * **It wears the metal — in the TILES.** The six benefit tiles are `PapPremiumSurface` at row size,
 * the same material as the paywall's, and on device that is where the finish actually reads. The
 * hero is [PapPremiumMark] because the brand mark beats a trophy (nothing was *achieved* here, the
 * same argument that removed the trophy from the paywall), but ⚠️ at the molde's 56 dp hero circle
 * the mark reads as FLAT BRAND GREEN: `PapPremiumMark` had its finish verified at 72 dp, and this
 * is less than half of that. Measured on the Pixel 8 emulator, 20-11. Enlarging it would mean
 * giving one dialog its own hero circle, which is a worse trade than a crest that reads as the
 * plain mark — so the claim is written down rather than quietly assumed.
 *
 * ⛔ The benefit list comes from `PLUS_BENEFITS`, the paywall's own. See `PlusBenefits.kt`: the two
 * readings of ONE list are the whole design — titles for the celebration, titles plus the free-plan
 * note for the warning.
 */
@Composable
fun PlusMomentDialog(
    moment: PlusMoment,
    onDismiss: () -> Unit,
    onSeePlans: () -> Unit,
) {
    when (moment) {
        is PlusMoment.FoundersWelcome -> FoundersWelcomeDialog(
            expiresAtMillis = moment.expiresAtMillis,
            onDismiss = onDismiss,
        )
        is PlusMoment.PromoEnding -> PromoEndingDialog(
            expiresAtMillis = moment.expiresAtMillis,
            onDismiss = onDismiss,
            onSeePlans = onSeePlans,
        )
    }
}

/**
 * «You are one of the first 2,000.»
 *
 * ⛔ **One action, and it is not the paywall.** The obvious second button — "see what Plus
 * includes" — would point a user we have just given Plus to at the screen that sells it, on their
 * first day. The list of what Plus includes is right there in the dialog, which is the honest way
 * to answer that wish; the door to the plans belongs to [PromoEndingDialog], where there is
 * actually a decision to make. A soft paywall means the gate opens it, not the gift.
 *
 * The date comes from the grant and is written only if the grant HAS one. ⛔ Deliberately no
 * "three months free" anywhere: the promo's length is whatever RevenueCat says it is, and a
 * hardcoded duration is a sentence that goes false the day the dashboard is edited.
 */
@Composable
private fun FoundersWelcomeDialog(expiresAtMillis: Long?, onDismiss: () -> Unit) {
    PapAlertDialog(
        onDismiss = onDismiss,
        // Semantic fallback only: `heroContent` is what gets drawn. Required by the molde.
        icon = Icons.Rounded.WorkspacePremium,
        title = stringResource(Res.string.plus_welcome_title),
        body = stringResource(Res.string.plus_welcome_body),
        primaryLabel = stringResource(Res.string.plus_welcome_cta),
        onPrimary = onDismiss,
        heroContent = { PapPremiumMark(size = HERO_MARK_DP) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PapGap.block),
            ) {
                expiresAtMillis?.let { UntilLine(stringResource(Res.string.plus_welcome_until, shortDateText(it))) }
                PapSectionHeader(stringResource(Res.string.plus_welcome_benefits))
                // Titles only: there is nothing to compare against when you have all of it, and
                // "Free: the last 7 days" under a celebration would be answering a question
                // nobody asked yet.
                Column(modifier = Modifier.fillMaxWidth()) {
                    PLUS_BENEFITS.forEach { PlusBenefitRow(it) }
                }
            }
        },
    )
}

/**
 * «It ends on the 15th.» The quiet knock, a week out.
 *
 * Three rules it is held to, all of them the monetisation doctrine's:
 *  · **"See plans" is the PRIMARY action and "Not now" the outlined one below it** (user's call,
 *    20-11; it shipped the other way round for one build). The reminder's whole point is that
 *    there is still something you can DO about the date, and burying the only action that does it
 *    under the dismissal makes the dialog a dead end — the same defect as a paywall that closes
 *    itself, one screen earlier. Soft gating is intact: "Not now" is a full-width button of the
 *    same size, not a link, not greyed out, not a second layer.
 *  · **Its header is "What changes"**, and that wording is a correction made from a screenshot.
 *    It said *"What the free plan gives you"* — over rows whose BOLD line is the Plus promise
 *    ("Unlimited zones") and whose quiet second line is the free limit ("Free: 2 zones"). Anyone
 *    scanning the bold text read a header that said the opposite of its own list. "What changes"
 *    is true of both halves of every row, and it still is not "what you lose".
 *  · **Nothing is deleted and the copy says so**, because that is the project's actual downgrade
 *    rule, stated by the gates themselves: history narrows its READ window, zones and cars above
 *    the free limit are conserved, and only what is NEW gets gated.
 */
@Composable
private fun PromoEndingDialog(
    expiresAtMillis: Long,
    onDismiss: () -> Unit,
    onSeePlans: () -> Unit,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.WorkspacePremium,
        title = stringResource(Res.string.plus_promo_ending_title),
        body = stringResource(Res.string.plus_promo_ending_body, shortDateText(expiresAtMillis)),
        primaryLabel = stringResource(Res.string.plus_promo_ending_see_plans),
        onPrimary = onSeePlans,
        secondaryLabel = stringResource(Res.string.plus_promo_ending_cta),
        onSecondary = onDismiss,
        heroContent = { PapPremiumMark(size = HERO_MARK_DP) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PapGap.block),
            ) {
                PapSectionHeader(stringResource(Res.string.plus_promo_ending_benefits))
                Column(modifier = Modifier.fillMaxWidth()) {
                    PLUS_BENEFITS.forEach { PlusBenefitRow(it, showNote = true) }
                }
            }
        },
    )
}

/**
 * The crest inside the dialog's 56 dp hero circle.
 *
 * Smaller than the molde's own 28 dp glyph would suggest at first glance, and the reason is that
 * the mark is FULL BLEED — it touches all four edges of its box, where a Material glyph keeps
 * about 8 % of padding inside its own. At 30 dp the two read as the same optical size.
 *
 * ⚠️ And at this size the premium FINISH does not read — see the class KDoc. The size is chosen for
 * the optical match with every other dialog in the app, not for the metal.
 */
private val HERO_MARK_DP = 30.dp

/** The date, centred under the body it belongs to — one line, the brand's own colour, because it
 *  is the fact the reader came for. */
@Composable
private fun UntilLine(text: String) {
    Text(
        text = text,
        style = PaparcarType.current.label,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
