package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.annualSavingsPercent
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.presentation.util.collectAsStateLifecycleAware
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPremiumButton
import com.rndeveloper.paparcar.ui.components.PapPremiumMark
import com.rndeveloper.paparcar.ui.components.PapPremiumSurface
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.premiumInk
import com.rndeveloper.paparcar.ui.theme.outlineSubtle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paywall_billing_note
import paparcar.composeapp.generated.resources.paywall_close_cd
import paparcar.composeapp.generated.resources.paywall_compare_title
import paparcar.composeapp.generated.resources.paywall_cta_buy
import paparcar.composeapp.generated.resources.paywall_cta_trial
import paparcar.composeapp.generated.resources.paywall_free_alerts
import paparcar.composeapp.generated.resources.paywall_free_history
import paparcar.composeapp.generated.resources.paywall_free_support
import paparcar.composeapp.generated.resources.paywall_plan_monthly
import paparcar.composeapp.generated.resources.paywall_plan_per_month
import paparcar.composeapp.generated.resources.paywall_plan_savings_badge
import paparcar.composeapp.generated.resources.paywall_plan_yearly
import paparcar.composeapp.generated.resources.paywall_plus_alerts
import paparcar.composeapp.generated.resources.paywall_plus_history
import paparcar.composeapp.generated.resources.paywall_plus_support
import paparcar.composeapp.generated.resources.paywall_purchase_failed
import paparcar.composeapp.generated.resources.paywall_restore
import paparcar.composeapp.generated.resources.paywall_restore_none
import paparcar.composeapp.generated.resources.paywall_store_unavailable
import paparcar.composeapp.generated.resources.paywall_tagline
import paparcar.composeapp.generated.resources.paywall_tier_free
import paparcar.composeapp.generated.resources.paywall_tier_plus
import paparcar.composeapp.generated.resources.paywall_title
import paparcar.composeapp.generated.resources.paywall_free_vehicles
import paparcar.composeapp.generated.resources.paywall_free_widget
import paparcar.composeapp.generated.resources.paywall_free_zones
import paparcar.composeapp.generated.resources.paywall_plus_everything
import paparcar.composeapp.generated.resources.paywall_plus_vehicles
import paparcar.composeapp.generated.resources.paywall_plus_zones
import paparcar.composeapp.generated.resources.paywall_reason_history
import paparcar.composeapp.generated.resources.paywall_reason_stats
import paparcar.composeapp.generated.resources.paywall_reason_vehicles
import paparcar.composeapp.generated.resources.paywall_reason_zone_alerts
import paparcar.composeapp.generated.resources.paywall_reason_zones

/**
 * Paparcar Plus paywall. [MONEY-001] [UI-PAYWALL-THE-PITCH-IS-OURS-THE-PRICE-IS-THE-STORES-001]
 *
 * **Paparcar draws its own paywall.** This screen used to hand the whole surface to the remote
 * RevenueCat template and keep a static frame as the no-store fallback. It no longer does, and
 * the reasoning is worth keeping, because the obvious default is the other way round:
 *
 *  · The remote template cannot render THIS. Free-versus-Plus as two facing cards, one of them
 *    wearing `PapPremiumSurface`, is the app's own material — the dashboard's template system has
 *    no way to reach it. Every other Plus touchpoint in the app is metallic; the one screen that
 *    has to close the sale would have been the only place that did not look like Paparcar.
 *  · The template was never configured anyway, so what a paying user would actually have seen is
 *    the SDK's generic fallback. The choice was not "our design vs a designed template", it was
 *    "our design vs a placeholder".
 *
 * What we give up is real and worth stating: layout changes now need a release, where the
 * dashboard could have shipped them remotely, and restoring purchases is on us.
 *
 * ⛔ What did NOT change is who owns the money. Prices, currency, the trial and the payment sheet
 * are still entirely the store's — read live per screen open, never hardcoded, never cached across
 * sessions. The rule "RevenueCat is the only source of truth for the subscription" is about state
 * and price, not about who draws the pixels.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    /** WHICH limit (or entrance) opened this paywall — see [PaywallReason]. [FREE-LIMITS-001] */
    reason: PaywallReason = PaywallReason.GENERAL,
    viewModel: PaywallViewModel = koinViewModel { parametersOf(reason) },
) {
    val state by viewModel.state.collectAsStateLifecycleAware()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaywallEffect.Dismiss -> onClose()
            }
        }
    }
    // However Plus arrived (this purchase, a restore, another device), a paywall shown to a
    // Plus user has nothing left to sell — it leaves on its own.
    LaunchedEffect(state.isPlus) {
        if (state.isPlus) onClose()
    }

    PaywallContent(state = state, onIntent = viewModel::handleIntent, reason = reason)
}

@Composable
fun PaywallContent(
    state: PaywallState,
    onIntent: (PaywallIntent) -> Unit,
    modifier: Modifier = Modifier,
    reason: PaywallReason = PaywallReason.GENERAL,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // Everything the user READS scrolls — including the plans. Only the ACTION is pinned.
            //
            // The plans started out pinned too, on the argument that a price which scrolls away
            // makes people decide from memory. On a phone that footer ate half the screen and
            // squeezed the comparison, which is the actual pitch. The button carries the price in
            // its own label ("Get Plus for 13,99 €"), so nothing is being decided blind: what has
            // to be permanently visible is what you are about to pay, and that travels with the
            // button.
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SCREEN_H_PADDING_DP.dp),
                ) {
                    Spacer(Modifier.height(CLOSE_ROW_HEIGHT_DP.dp))
                    PaywallHeader()

                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                    PapSectionHeader(stringResource(Res.string.paywall_compare_title))
                    Spacer(Modifier.height(COMPARE_TITLE_GAP_DP.dp))
                    TierComparison()

                    if (state.canPurchase) {
                        Spacer(Modifier.height(SECTION_GAP_DP.dp))
                        PlanPicker(state = state, onIntent = onIntent)
                    }
                    Spacer(Modifier.height(SECTION_GAP_DP.dp))
                }

                PaywallFooter(state = state, onIntent = onIntent, reason = reason)
            }

            IconButton(
                onClick = { onIntent(PaywallIntent.Close) },
                modifier = Modifier.align(Alignment.TopEnd).padding(CLOSE_PADDING_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.paywall_close_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The pinned action: the buy button, what it costs, and the way back in.
 *
 * It carries its own surface colour and a hairline on top rather than floating transparently,
 * because content scrolling underneath a transparent footer reads as a rendering glitch. The
 * divider is what tells the eye "this part does not move".
 */
@Composable
private fun PaywallFooter(state: PaywallState, onIntent: (PaywallIntent) -> Unit, reason: PaywallReason) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            PapDivider()
            Column(
                modifier = Modifier.padding(
                    horizontal = SCREEN_H_PADDING_DP.dp,
                    vertical = FOOTER_V_PADDING_DP.dp,
                ),
            ) {
                if (state.canPurchase) {
                    PurchaseAction(state = state, onIntent = onIntent)
                } else {
                    // No store, no products, or the offerings call failed. One honest line, and
                    // deliberately no price anywhere on the screen — a paywall that invents a
                    // number to look complete is worse than one that admits it cannot sell today.
                    Text(
                        text = stringResource(Res.string.paywall_store_unavailable),
                        style = PaparcarType.current.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // [FREE-LIMITS-001] The MOTIVE that brought the user here still gets said,
                    // even when nothing can be sold — a gate that opens onto a dead end without
                    // explanation reads as a bug. GENERAL entrances add nothing.
                    reasonLineRes(reason)?.let { res ->
                        Spacer(Modifier.height(NOTE_GAP_DP.dp))
                        Text(
                            text = stringResource(res),
                            style = PaparcarType.current.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The opening of the screen — a centred crest, the name, the promise.
 *
 * Centred and given room on purpose. A paywall opens by asking for money, and a left-aligned
 * title tucked under the close button reads like a settings page making a request; the same words
 * centred under a crest read like an offer. Ceremony is most of what "premium" means in a layout:
 * it is space and symmetry, not more ornament.
 *
 * The crest is a small [PapPremiumSurface], so the metal is introduced at the top of the screen
 * and then paid off by the Plus card below — one material, stated twice.
 */
@Composable
private fun PaywallHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The BRAND cast in metal, not a medal in a box. A trophy glyph said "achievement" and
        // nothing is unlocked by subscribing; and dropping the tile lets the mark itself be the
        // metallic object, instead of a plain glyph sitting on one.
        PapPremiumMark()
        Spacer(Modifier.height(CREST_GAP_DP.dp))
        Text(
            text = stringResource(Res.string.paywall_title),
            style = PaparcarType.current.heroTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TITLE_GAP_DP.dp))
        Text(
            text = stringResource(Res.string.paywall_tagline),
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Free vs Plus — two facing cards
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The comparison, as two cards side by side: what you have on the left, what you could have on
 * the right, wearing the metal.
 *
 * Chosen over a shared-label table because this is a SALES screen, not a spec sheet. A table is
 * more scannable, but it reduces the premium surface to a thin column, and the finish only sells
 * when the Plus tier is a whole OBJECT you could point at.
 *
 * ⚠️ The price of that choice is width: on a 360 dp phone each card is about 150 dp, so every
 * line inside has to survive being wrapped across nine locales. That is why the values are terse
 * fragments ("The last 30 days") instead of the sentences the old stacked layout could afford —
 * the layout constrains the copy, so the copy was rewritten rather than left to overflow.
 *
 * `IntrinsicSize.Min` + `fillMaxHeight` keeps both cards the same height whichever locale wraps
 * more, so the two never sit at different depths.
 */
@Composable
private fun TierComparison() {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(TIER_GAP_DP.dp),
    ) {
        PapOutlinedCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = PapShapes.cardLarge,
        ) {
            TierCardBody(
                title = stringResource(Res.string.paywall_tier_free),
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                lines = FREE_LINES,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        PapPremiumSurface(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // On the metallic bed the ink inverts, exactly as on the Settings row and the
            // history footer — `PapPremiumSurface` provides `onPrimary` to everything inside.
            TierCardBody(
                title = stringResource(Res.string.paywall_tier_plus),
                titleColor = premiumInk,
                lines = PLUS_LINES,
                tint = premiumInk,
                textColor = premiumInk,
            )
        }
    }
}

@Composable
private fun TierCardBody(
    title: String,
    titleColor: Color,
    lines: List<TierLine>,
    tint: Color,
    textColor: Color,
) {
    Column(modifier = Modifier.padding(TIER_PADDING_DP.dp)) {
        Text(text = title, style = PaparcarType.current.cardTitle, color = titleColor)
        Spacer(Modifier.height(TIER_TITLE_GAP_DP.dp))
        lines.forEach { line ->
            Row(
                modifier = Modifier.padding(vertical = TIER_LINE_PAD_DP.dp),
                horizontalArrangement = Arrangement.spacedBy(TIER_ICON_GAP_DP.dp),
                // Top, so the icon marks the first line of a value that wraps to two or three.
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = line.icon,
                    contentDescription = null,
                    tint = tint.copy(alpha = PapAlpha.body),
                    modifier = Modifier
                        .padding(top = TIER_ICON_NUDGE_DP.dp)
                        .size(TIER_ICON_DP.dp),
                )
                Text(
                    text = stringResource(line.text),
                    style = PaparcarType.current.caption,
                    color = textColor,
                )
            }
        }
    }
}

/** One value inside a tier card. The icon is what pairs a Free line with its Plus counterpart:
 *  with no shared label column, the glyph is the only thing saying "these two are the same row". */
private class TierLine(val icon: ImageVector, val text: StringResource)

private val FREE_LINES = listOf(
    TierLine(Icons.Rounded.History, Res.string.paywall_free_history),
    TierLine(Icons.Rounded.DirectionsCar, Res.string.paywall_free_vehicles),
    TierLine(Icons.Rounded.Map, Res.string.paywall_free_zones),
    // [WIDGET-ACTIONS-002] A free value the pitch was not naming — the widget is FREE by doctrine
    // (the present is never hidden) and the table says so.
    TierLine(Icons.Rounded.Widgets, Res.string.paywall_free_widget),
    TierLine(Icons.Rounded.NotificationsActive, Res.string.paywall_free_alerts),
    TierLine(Icons.Rounded.Favorite, Res.string.paywall_free_support),
)

private val PLUS_LINES = listOf(
    // Plus opens by SAYING it contains Free — nobody should have to infer it. [WIDGET-ACTIONS-002]
    TierLine(Icons.Rounded.Check, Res.string.paywall_plus_everything),
    TierLine(Icons.Rounded.History, Res.string.paywall_plus_history),
    TierLine(Icons.Rounded.DirectionsCar, Res.string.paywall_plus_vehicles),
    TierLine(Icons.Rounded.Map, Res.string.paywall_plus_zones),
    TierLine(Icons.Rounded.NotificationsActive, Res.string.paywall_plus_alerts),
    TierLine(Icons.Rounded.Favorite, Res.string.paywall_plus_support),
)

/** The one-line motive for a gated entrance; GENERAL entrances (the Settings row) have none. */
private fun reasonLineRes(reason: PaywallReason): StringResource? = when (reason) {
    PaywallReason.HISTORY -> Res.string.paywall_reason_history
    PaywallReason.VEHICLES -> Res.string.paywall_reason_vehicles
    PaywallReason.ZONES -> Res.string.paywall_reason_zones
    PaywallReason.STATS -> Res.string.paywall_reason_stats
    PaywallReason.ZONE_ALERTS -> Res.string.paywall_reason_zone_alerts
    PaywallReason.GENERAL -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// Plans — monthly and yearly, priced by the store
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.PlanPicker(state: PaywallState, onIntent: (PaywallIntent) -> Unit) {
    // Stacked full-width rather than side by side, unlike the tier cards above: a plan row carries
    // a period, a price, a per-month equivalent and a savings badge, and four things do not fit in
    // half a phone. The two layouts differ because what they hold differs, not by accident.
    val savings = annualSavingsPercent(state.offers)
    state.offers.forEach { offer ->
        PlanRow(
            offer = offer,
            selected = offer.plan == state.selectedPlan,
            savingsPercent = savings.takeIf { offer.plan == PlusPlan.YEARLY },
            onClick = { onIntent(PaywallIntent.SelectPlan(offer.plan)) },
        )
        Spacer(Modifier.height(PLAN_GAP_DP.dp))
    }
    // The billing terms sit with the PLANS, not with the button. They explain what choosing a
    // period commits you to — how it renews and how to stop it — so this is where they answer a
    // question the reader is already asking. Keeping them here also buys back the height the
    // pinned footer was spending on a sentence nobody reads twice.
    Text(
        text = stringResource(Res.string.paywall_billing_note),
        style = PaparcarType.current.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The pinned half: the button, and the way back in for someone who already paid. */
@Composable
private fun ColumnScope.PurchaseAction(state: PaywallState, onIntent: (PaywallIntent) -> Unit) {
    val selected = state.selectedOffer
    if (selected != null) {
        val trialDays = selected.freeTrialDays
        // The buy button wears the METAL, not the flat brand fill. This is the one action in the
        // app that is the product, so it is made of the product. [PapPremiumButton]
        PapPremiumButton(
            // The button says what actually happens next. With a trial it promises the trial, not
            // the charge; without one it names the price, so nobody taps "Continue" to find out —
            // and carrying the price HERE is what let the plan rows go back to scrolling.
            label = if (trialDays != null) {
                stringResource(Res.string.paywall_cta_trial, trialDays)
            } else {
                stringResource(Res.string.paywall_cta_buy, selected.price)
            },
            onClick = { onIntent(PaywallIntent.Purchase) },
            isLoading = state.purchasing,
            enabled = !state.purchasing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.purchaseFailed) {
        Spacer(Modifier.height(NOTE_GAP_DP.dp))
        Text(
            // A cancelled store sheet lands here too, so the wording blames nobody and answers
            // the only question that matters after a failed purchase: was I charged?
            text = stringResource(Res.string.paywall_purchase_failed),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // "I already paid." A subscription belongs to the Google account, not to the install, so a
    // reinstall or a new phone can leave a paying user staring at a paywall. Without this their
    // only visible way out is paying twice — and Play's subscription policy requires it.
    // [MONEY-001]
    Spacer(Modifier.height(NOTE_GAP_DP.dp))
    TextButton(
        onClick = { onIntent(PaywallIntent.Restore) },
        enabled = !state.restoring && !state.purchasing,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(
            text = stringResource(Res.string.paywall_restore),
            style = PaparcarType.current.cta,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (state.restoreFoundNothing) {
        Text(
            // Deliberately NOT the error colour: owning nothing is a fact, not a fault, and the
            // sentence names the thing the user can actually check — which Google account.
            text = stringResource(Res.string.paywall_restore_none),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanRow(
    offer: PlusOffer,
    selected: Boolean,
    savingsPercent: Int?,
    onClick: () -> Unit,
) {
    PapOutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        // Selection is a BORDER and a check, never a heavier font: the type doctrine keeps weight
        // as a property of the role, and the colour doctrine keeps state out of the text colour.
        border = if (selected) {
            BorderStroke(PapBorders.medium, MaterialTheme.colorScheme.primary)
        } else {
            outlineSubtle
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PLAN_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PLAN_GAP_DP.dp),
        ) {
            Box(
                modifier = Modifier.size(CHECK_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(CHECK_DP.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (offer.plan == PlusPlan.YEARLY) {
                            Res.string.paywall_plan_yearly
                        } else {
                            Res.string.paywall_plan_monthly
                        },
                    ),
                    style = PaparcarType.current.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                offer.pricePerMonth?.let { perMonth ->
                    Text(
                        text = stringResource(Res.string.paywall_plan_per_month, perMonth),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            savingsPercent?.let { percent ->
                // A filled chip, not bare green text: a discount is a STICKER on the offer, and
                // coloured text beside a price reads as part of the price.
                Surface(
                    shape = PapShapes.chip,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = premiumInk,
                    modifier = Modifier.padding(end = BADGE_GAP_DP.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_plan_savings_badge, percent),
                        style = PaparcarType.current.badge,
                        modifier = Modifier.padding(
                            horizontal = BADGE_H_PAD_DP.dp,
                            vertical = BADGE_V_PAD_DP.dp,
                        ),
                    )
                }
            }
            Text(
                text = offer.price,
                style = PaparcarType.current.rowName,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private const val SCREEN_H_PADDING_DP = 20

/** Room above the crest. Generous on purpose — the header is the ceremony, and ceremony is
 *  mostly space. */
private const val CLOSE_ROW_HEIGHT_DP = 64
private const val CLOSE_PADDING_DP = 4
/** Breathing room around the pinned footer. */
private const val FOOTER_V_PADDING_DP = 14
private const val CREST_GAP_DP = 16
private const val TITLE_GAP_DP = 8
private const val SECTION_GAP_DP = 28
private const val COMPARE_TITLE_GAP_DP = 10
private const val NOTE_GAP_DP = 12
private const val CHECK_DP = 20

private const val TIER_GAP_DP = 12
private const val TIER_PADDING_DP = 14
private const val TIER_TITLE_GAP_DP = 10
private const val TIER_LINE_PAD_DP = 5
private const val TIER_ICON_GAP_DP = 8
private const val TIER_ICON_DP = 16

/** Drops the row icon onto the first text line's optical centre instead of its top edge. */
private const val TIER_ICON_NUDGE_DP = 2

private const val PLAN_PADDING_DP = 16
private const val PLAN_GAP_DP = 10
private const val BADGE_GAP_DP = 8
private const val BADGE_H_PAD_DP = 8
private const val BADGE_V_PAD_DP = 3
