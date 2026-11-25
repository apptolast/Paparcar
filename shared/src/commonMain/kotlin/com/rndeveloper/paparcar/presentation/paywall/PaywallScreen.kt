package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.annualSavingsPercent
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.ui.components.PAPARCAR_LOGO_BESIDE_NAME_SIZE
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rndeveloper.paparcar.presentation.util.shortDateText
import com.rndeveloper.paparcar.presentation.util.storeRedeemCodeUrl
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTextRow
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPremiumButton
import com.rndeveloper.paparcar.ui.components.PapPremiumCrest
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
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
import paparcar.composeapp.generated.resources.paywall_benefits_title
import paparcar.composeapp.generated.resources.paywall_billing_note
import paparcar.composeapp.generated.resources.paywall_close_cd
import paparcar.composeapp.generated.resources.paywall_cta_buy
import paparcar.composeapp.generated.resources.paywall_cta_trial
import paparcar.composeapp.generated.resources.paywall_free_is_free
import paparcar.composeapp.generated.resources.paywall_plan_monthly
import paparcar.composeapp.generated.resources.paywall_plan_per_month
import paparcar.composeapp.generated.resources.paywall_plan_savings_badge
import paparcar.composeapp.generated.resources.paywall_plan_yearly
import paparcar.composeapp.generated.resources.paywall_promo_ends
import paparcar.composeapp.generated.resources.paywall_purchase_failed
import paparcar.composeapp.generated.resources.paywall_redeem_code
import paparcar.composeapp.generated.resources.paywall_restore
import paparcar.composeapp.generated.resources.paywall_restore_none
import paparcar.composeapp.generated.resources.paywall_store_unavailable
import paparcar.composeapp.generated.resources.paywall_tagline
import paparcar.composeapp.generated.resources.paywall_title
import paparcar.composeapp.generated.resources.paywall_reason_history
import paparcar.composeapp.generated.resources.paywall_reason_stats
import paparcar.composeapp.generated.resources.paywall_reason_vehicles
import paparcar.composeapp.generated.resources.paywall_reason_route_cloud
import paparcar.composeapp.generated.resources.paywall_reason_fuel_alerts
import paparcar.composeapp.generated.resources.paywall_reason_month_recap
import paparcar.composeapp.generated.resources.paywall_reason_shared_car
import paparcar.composeapp.generated.resources.paywall_reason_zone_alerts
import paparcar.composeapp.generated.resources.paywall_reason_zones

/**
 * Paparcar Plus paywall. [MONEY-001] [UI-PAYWALL-THE-PITCH-IS-OURS-THE-PRICE-IS-THE-STORES-001]
 *
 * **Paparcar draws its own paywall.** This screen used to hand the whole surface to the remote
 * RevenueCat template and keep a static frame as the no-store fallback. It no longer does, and
 * the reasoning is worth keeping, because the obvious default is the other way round:
 *
 *  · The remote template cannot render THIS. The metal — the crest, the benefit tiles, the buy
 *    button — is the app's own material, and the dashboard's template system has no way to reach
 *    it. Every other Plus touchpoint in the app is metallic; the one screen that has to close the
 *    sale would have been the only place that did not look like Paparcar.
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
    val state by viewModel.state.collectAsStateWithLifecycle()

    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaywallEffect.Dismiss -> onClose()
                // El canje ocurre FUERA. Al volver, la suscripción entra por el mismo sitio que
                // cualquier compra —el `CustomerInfo` que empuja el SDK— y el paywall se cierra
                // solo, igual que si hubiera pagado aquí.
                // [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001]
                is PaywallEffect.OpenUrl -> uriHandler.openUri(effect.url)
            }
        }
    }
    // However Plus arrived (this purchase, a restore, another device), a paywall with nothing left
    // to sell leaves on its own. ⛔ `dismissesItself` and not `isPlus`: a Plus that was GIVEN and
    // expires still has the continuation to sell, and that user is the only one on this screen with
    // a real decision to make. [MONEY-FOUNDERS-WELCOME-001]
    LaunchedEffect(state.dismissesItself) {
        if (state.dismissesItself) onClose()
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
            // squeezed the pitch. The button carries the price in its own label ("Get Plus for
            // 13,99 €"), so nothing is being decided blind: what has to be permanently visible is
            // what you are about to pay, and that travels with the button.
            Column(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    // 🔑 The slack goes in ONE place, and it is between the pitch and the price.
                    // [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
                    //
                    // A paywall is short — a brand block, a list, two plans, a pinned button — so
                    // on a tall phone the content simply ended and left a dead field of surface
                    // above the footer, which reads as a screen that failed to load.
                    // `heightIn(min = maxHeight)` + `SpaceBetween` over exactly TWO children hands
                    // that leftover to the single gap between them. On a screen with no leftover
                    // there is nothing to distribute and the column just scrolls: one rule, both
                    // cases, no branch.
                    //
                    // ⛔ Two children and not three. Distributing across all three blocks was the
                    // first cut, and it pushed the section header a third of a screen away from
                    // the crest — the brand block reads as the TOP of the screen, so it must stay
                    // attached to it. What may float is the price, and floating it down towards
                    // the button it belongs to is the arrangement saying something true.
                    //
                    // ⚠️ Each block carries its OWN minimum gap ([SECTION_GAP_DP] as bottom
                    // padding): `SpaceBetween` only distributes the surplus, so with the gaps left
                    // to it alone a screen with no surplus would have its sections touching.
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(horizontal = SCREEN_H_PADDING_DP.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.padding(bottom = SECTION_GAP_DP.dp)) {
                            Spacer(Modifier.height(CLOSE_ROW_HEIGHT_DP.dp))
                            PaywallHeader()
                            ReasonStrip(reason)
                            Spacer(Modifier.height(SECTION_GAP_DP.dp))
                            PapSectionHeader(stringResource(Res.string.paywall_benefits_title))
                            Spacer(Modifier.height(BENEFITS_TITLE_GAP_DP.dp))
                            PlusBenefits()
                            Spacer(Modifier.height(FREE_LINE_GAP_DP.dp))
                            FreeIsFreeLine()
                        }

                        // No store, no plans — and then the pitch is the only block, which
                        // `SpaceBetween` leaves at the top exactly as it should.
                        if (state.canPurchase) {
                            Column(modifier = Modifier.padding(bottom = SECTION_GAP_DP.dp)) {
                                PlanPicker(state = state, onIntent = onIntent)
                            }
                        }
                    }
                }

                PaywallFooter(state = state, onIntent = onIntent)
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
private fun PaywallFooter(state: PaywallState, onIntent: (PaywallIntent) -> Unit) {
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
                    //
                    // ⛔ The MOTIVE that brought the user here is NOT repeated down here any more.
                    // It used to live only in this branch, which meant a working store showed it
                    // never; now [ReasonStrip] says it at the top of every entrance, store or no
                    // store, and saying it twice on the same screen would be the other failure.
                    Text(
                        text = stringResource(Res.string.paywall_store_unavailable),
                        style = PaparcarType.current.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The opening of the screen — the crest, and beside it the name and the promise.
 * [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
 *
 * ⚠️ This used to be centred, stacked and 96 dp tall, argued as ceremony: "a paywall opens by
 * asking for money, and ceremony is space and symmetry". Two things were wrong with that.
 *
 *  · **It cost about 260 dp** before the pitch started — a third of a 360×800 phone spent on the
 *    introduction. Space is only ceremony while there is space to spare.
 *  · **The screen lays every other child out full-width from the same left edge** — the section
 *    header, every benefit row, the plan rows, the CTA. A centred header floats on an axis of its
 *    own above all of them. This is the identical argument that put the auth header on the left
 *    edge of its own fields [UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001], and the paywall had no
 *    reason to be the exception.
 *
 * So it is now literally the login's header composition: mark on the left at
 * [PAPARCAR_LOGO_BESIDE_NAME_SIZE], name and tagline stacked to its right, everything on the same
 * left edge. The title moves from `heroTitle` to `screenTitle` for the same reason — the ROLE
 * follows the composition, and this composition already had its role chosen on the auth screen.
 *
 * The crest is a small [PapPremiumSurface], so the metal is introduced at the top of the screen
 * and then paid off by the benefit tiles and the buy button below — one material, stated
 * throughout.
 */
@Composable
private fun PaywallHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CREST_TEXT_GAP_DP.dp),
    ) {
        // The BRAND in metal, not a medal in a box: a trophy glyph said "achievement" and nothing
        // is unlocked by subscribing.
        //
        // ⛔ A DISC of metal with the mark cut out of it, and not the mark cast in metal
        // ([PapPremiumMark]) — decisión del user, 20-11, mirando la pantalla. Lo que gana es área:
        // el barrido necesita superficie que cruzar, y a 72 dp el glifo suelto la tiene justa.
        // Lo que cuesta está escrito en el KDoc de [PapPremiumCrest]: reinstala un disco bajo la
        // marca, que la doctrina de iconos daba por muerto. [MONEY-FOUNDERS-WELCOME-001]
        PapPremiumCrest(size = PAPARCAR_LOGO_BESIDE_NAME_SIZE)
        // `weight(1f)` and not intrinsic width: the tagline is a full sentence and it WILL wrap in
        // the longer locales. Wrapping is fine; being pushed off the edge is not.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.paywall_title),
                style = PaparcarType.current.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(TITLE_GAP_DP.dp))
            // The tagline says the AXIS, not two features. It used to name the history and the
            // zone alerts, which is the job of the list twenty dp below it — so the one line with
            // room to state what separates free from paid was spending itself on a preview of the
            // next paragraph. Free is asking; Plus is being watched over and remembered for.
            Text(
                text = stringResource(Res.string.paywall_tagline),
                style = PaparcarType.current.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Why you are here — the gate that opened this screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The one-line motive for a gated entrance, stated where the reader is already looking.
 * [FREE-LIMITS-001] [UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001]
 *
 * ⛔ This used to be drawn ONLY in the footer's no-store branch — so with a store that works,
 * which is every shipping build, the motive was computed, carried through two constructors and
 * then thrown away. Someone who taps "add a second car", hits the free limit and lands here read
 * a generic pitch and had to work out for themselves what any of it had to do with their car. The
 * eight sentences already existed, translated, in all nine locales; nothing was missing except
 * the decision to show them.
 *
 * It sits directly under the promise because that is the reading order of the screen: what this
 * is, why you are seeing it, what it adds, what it costs.
 *
 * The icon is the icon of the THING you hit, so the strip points back at the gate rather than
 * decorating itself with a padlock. Icon and sentence travel in ONE object for the reason
 * `TierRow` existed: two parallel `when`s over the same enum drift the moment a reason is added.
 */
@Composable
private fun ReasonStrip(reason: PaywallReason) {
    val line = reasonLine(reason) ?: return
    Spacer(Modifier.height(REASON_GAP_DP.dp))
    PapOutlinedCard(modifier = Modifier.fillMaxWidth(), shape = PapShapes.row) {
        PapIconTextRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = REASON_H_PAD_DP.dp, vertical = REASON_V_PAD_DP.dp),
            gap = REASON_ICON_GAP_DP.dp,
            multilineIconTopPadding = ICON_NUDGE_DP.dp,
            icon = {
                Icon(
                    imageVector = line.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(REASON_ICON_DP.dp),
                )
            },
        ) {
            Text(
                text = stringResource(line.text),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The gate that opened the paywall, as the reader sees it: a glyph and a sentence. */
private class ReasonLine(val icon: ImageVector, val text: StringResource)

/** [PaywallReason.GENERAL] is the Settings row — an entrance, not a gate, so it has no motive to
 *  name and gets no strip. */
private fun reasonLine(reason: PaywallReason): ReasonLine? = when (reason) {
    PaywallReason.HISTORY -> ReasonLine(PaparcarIcons.History, Res.string.paywall_reason_history)
    PaywallReason.VEHICLES -> ReasonLine(Icons.Rounded.DirectionsCar, Res.string.paywall_reason_vehicles)
    PaywallReason.ZONES -> ReasonLine(Icons.Rounded.Map, Res.string.paywall_reason_zones)
    PaywallReason.STATS -> ReasonLine(PaparcarIcons.Figures, Res.string.paywall_reason_stats)
    PaywallReason.ZONE_ALERTS -> ReasonLine(Icons.Rounded.NotificationsActive, Res.string.paywall_reason_zone_alerts)
    PaywallReason.FUEL_ALERTS -> ReasonLine(Icons.Rounded.NotificationsActive, Res.string.paywall_reason_fuel_alerts)
    PaywallReason.MONTH_RECAP -> ReasonLine(Icons.Rounded.EventAvailable, Res.string.paywall_reason_month_recap)
    PaywallReason.ROUTE_CLOUD -> ReasonLine(Icons.Rounded.Route, Res.string.paywall_reason_route_cloud)
    PaywallReason.SHARED_CAR -> ReasonLine(PaparcarIcons.SharedCar, Res.string.paywall_reason_shared_car)
    PaywallReason.GENERAL -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// What Plus adds — one list, and the free limit rides its own line
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The pitch: six things Plus adds, each carrying the limit it is measured against.
 * [UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001]
 *
 * ⛔ **This replaces the two facing tier cards, and the reason is width.** The old comparison
 * split the screen down the middle and wrote PROSE in the halves: on a 360 dp phone that is about
 * 150 dp per cell, nine rows deep, in `caption`. The previous KDoc admitted what that costs —
 * *"the layout constrains the copy, so the copy is written to fit"* — which is the maquette
 * overruling the message on the one screen whose only job is the message.
 *
 * 🔑 The insight is that **the anchor was never the column, it was having the reference beside
 * the value.** "Your full history / Free: the last 7 days" anchors exactly as well as two columns
 * did and costs half the width. What the column bought on top of that was the ability to print
 * "Not included" in a cell — a cell whose whole content is that it has no content.
 *
 * Three rows left with the column and did not come back:
 *  · `nearby` said Free "Car parks and fuel prices" / Plus "The same: looking is always free" — a
 *    row in a table titled *what changes* spending half the screen's width to say nothing changes.
 *    It is doctrine and it is true (SEEING is never gated), so it survives as [FreeIsFreeLine],
 *    one sentence under the list.
 *  · `support` said "Free for everyone" / "You pay for the servers" — not a product delta but an
 *    honest note about where the money goes, so it moved into the billing line, which is where
 *    this screen talks about money.
 *  · `widget` folded into the cars row: what Plus adds to the widget IS the other cars.
 *
 * ⚠️ **The metal is now six tiles instead of one slab**, and that is a deliberate reversal of
 * [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001], which argued the finish only sells while
 * Plus is a whole OBJECT you could point at. There are still objects to point at — the crest, six
 * tiles and the buy button — and the material is now stated the length of the screen instead of
 * in one half-width card. If it reads thin on device, the fix is a larger metallic CTA, not the
 * column back.
 *
 * ⛔ Title and note live in ONE object, exactly as the free/plus pair did before them: two
 * parallel lists re-pair themselves in silence the first time a row is added to one of them.
 */
@Composable
internal fun PlusBenefits() {
    Column(modifier = Modifier.fillMaxWidth()) {
        PLUS_BENEFITS.forEach { benefit -> PlusBenefitRow(benefit) }
    }
}

/**
 * The line that keeps the free plan honest, at the moment the app is asking for money.
 * [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] [UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001]
 *
 * Free spots, public car parks and fuel prices — with price, hours and how to get there — are free
 * and complete, and doctrine says SEEING is never gated. A paywall that stays quiet about that
 * invites the reader to assume the opposite, which is why this sentence exists at all.
 *
 * It was a row of the comparison table and it is a sentence now, because as a row it had to be
 * answered by a Plus cell, and the only honest answer was "the same".
 */
@Composable
private fun FreeIsFreeLine() {
    PapIconTextRow(
        modifier = Modifier.fillMaxWidth(),
        gap = REASON_ICON_GAP_DP.dp,
        multilineIconTopPadding = ICON_NUDGE_DP.dp,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(REASON_ICON_DP.dp),
            )
        },
    ) {
        Text(
            text = stringResource(Res.string.paywall_free_is_free),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plans — monthly and yearly, priced by the store
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.PlanPicker(state: PaywallState, onIntent: (PaywallIntent) -> Unit) {
    // Stacked full-width, like everything else on the screen now: a plan row carries a period, a
    // price, a per-month equivalent and a savings badge, and four things do not fit in half a
    // phone. That was always true — it is the reason this block never went side by side.
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
    // ⛔ Lo que esta pantalla NO puede hacer es gritar «Consigue Plus por 13,99 €» a quien ya lo
    // tiene de regalo sin nombrar que se le acaba: una pantalla que ignora lo que el usuario ya
    // tiene es la que enseña a desconfiar de la siguiente. Va con los PLANES porque es lo que
    // cualifica la decisión de elegir uno. [MONEY-FOUNDERS-WELCOME-001]
    state.plusPromoEndsAtMillis?.let { endsAt ->
        Text(
            text = stringResource(Res.string.paywall_promo_ends, shortDateText(endsAt)),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(PLAN_GAP_DP.dp))
    }
    // The billing terms sit with the PLANS, not with the button. They explain what choosing a
    // period commits you to — how it renews, how to stop it, and (since the `support` row folded
    // in here) where the money goes — so this is where they answer questions the reader is
    // already asking. Keeping them here also buys back the height the pinned footer was spending
    // on a sentence nobody reads twice.
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
        // A store round-trip with no visible progress reads as "the tap did nothing", and the
        // answer to it arrives as a line of text below — so the wait has to be shown HERE, on the
        // button that started it, exactly like the buy button above.
        // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
        if (state.restoring) {
            CircularProgressIndicator(
                modifier = Modifier.size(RESTORE_SPINNER_DP.dp),
                strokeWidth = RESTORE_SPINNER_STROKE_DP.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = stringResource(Res.string.paywall_restore),
                style = PaparcarType.current.cta,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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

    // «Tengo un código». Va PEGADO a restaurar y no al lado del botón de comprar, porque es de la
    // misma familia: las dos son «ya tengo derecho a esto, dámelo», no «cóbrame».
    //
    // ⛔ Sólo si esta plataforma tiene puerta de canje ([storeRedeemCodeUrl]). En iOS un código de
    // oferta se canjea con una hoja de StoreKit, no con una URL, y mandar a un iPhone a la tienda
    // de Android sería peor que no ofrecerlo.
    // [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001]
    if (storeRedeemCodeUrl() != null) {
        TextButton(
            onClick = { onIntent(PaywallIntent.RedeemCode) },
            enabled = !state.restoring && !state.purchasing,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(Res.string.paywall_redeem_code),
                style = PaparcarType.current.cta,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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

/**
 * Room above the header: EXACTLY the close button's footprint — a 48 dp `IconButton` plus its
 * [CLOSE_PADDING_DP]. Not a round number and not ceremony; it is the one height at which a
 * left-aligned header cannot collide with the button floating at the top-end, whatever a locale
 * does to the title. The alternative — reserving end padding on the text column — is the same
 * guarantee written in a way that breaks in whichever language wraps widest.
 * [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
 */
private const val CLOSE_ROW_HEIGHT_DP = 52
private const val CLOSE_PADDING_DP = 4
/** Breathing room around the pinned footer. */
private const val FOOTER_V_PADDING_DP = 14
/** Mark-to-text gap, the login header's own. */
private const val CREST_TEXT_GAP_DP = 14
private const val TITLE_GAP_DP = 4
private const val SECTION_GAP_DP = 28
private const val BENEFITS_TITLE_GAP_DP = 10
private const val NOTE_GAP_DP = 12
private const val CHECK_DP = 20

/** Sized to the `cta` line it replaces, so the restore row doesn't change height mid-wait. */
private const val RESTORE_SPINNER_DP = 18
private const val RESTORE_SPINNER_STROKE_DP = 2

/** Closer to the header than a section gap: the strip explains the block directly above it. */
private const val REASON_GAP_DP = 16
private const val REASON_H_PAD_DP = 12
private const val REASON_V_PAD_DP = 10
private const val REASON_ICON_GAP_DP = 10
private const val REASON_ICON_DP = 16


/** Sits under the list as its closing note, so it keeps the list's own rhythm rather than a
 *  section's. */
private const val FREE_LINE_GAP_DP = 16

/** Optical inset for the leading glyph when the caption wraps. */
private const val ICON_NUDGE_DP = 2

private const val PLAN_PADDING_DP = 16
private const val PLAN_GAP_DP = 10
private const val BADGE_GAP_DP = 8
private const val BADGE_H_PAD_DP = 8
private const val BADGE_V_PAD_DP = 3
