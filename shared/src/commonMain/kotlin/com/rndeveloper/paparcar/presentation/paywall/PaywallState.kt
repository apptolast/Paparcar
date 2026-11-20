package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan

@Immutable
data class PaywallState(
    /**
     * Live entitlement: a completed purchase flips this through the repository stream, and the
     * screen reacts by dismissing itself — whichever flow granted it (this purchase, a restore,
     * another device). [MONEY-001]
     */
    val isPlus: Boolean = false,
    /**
     * The plans the store can sell right now, with live localised prices. Empty means there is
     * nothing to sell — no key, no products registered, or the store could not be reached — and
     * the screen then shows the comparison with no price cards. It never invents a price.
     */
    val offers: List<PlusOffer> = emptyList(),
    /**
     * Which plan the buy button will purchase. Defaults to yearly once the offers land: it is
     * the better deal for the user and the one the savings badge points at, so preselecting
     * anything else would be recommending against our own badge.
     */
    val selectedPlan: PlusPlan = PlusPlan.YEARLY,
    /** A purchase is in flight — the store sheet is up or the receipt is being verified. */
    val purchasing: Boolean = false,
    /** Set when a purchase failed for a reason worth showing; cleared on the next attempt. */
    val purchaseFailed: Boolean = false,
    /** A restore is in flight. */
    val restoring: Boolean = false,
    /**
     * The restore finished and this store account owns nothing. Not an error — the honest answer
     * to "I already paid" when they did not, or paid on another Google account — so it gets a
     * plain sentence rather than an error colour. A successful restore needs no flag: the
     * entitlement stream emits and the screen dismisses itself.
     */
    val restoreFoundNothing: Boolean = false,
    /**
     * When a Plus that NOBODY PAID FOR runs out — null unless this user is on the promo with an
     * end date. [MONEY-FOUNDERS-WELCOME-001]
     *
     * It exists because [isPlus] alone was making this screen unreachable for the one user with a
     * real decision to make: see [dismissesItself].
     */
    val plusPromoEndsAtMillis: Long? = null,
) {
    /** Nothing to sell → no plan cards, no buy button, and an honest line instead. */
    val canPurchase: Boolean get() = offers.isNotEmpty()

    /**
     * Whether this screen has nothing left to do and should leave. [MONEY-FOUNDERS-WELCOME-001]
     *
     * ⛔ **This used to be [isPlus] alone, and that was a hole.** "A paywall shown to a Plus user
     * has nothing left to sell" is true of a subscription that RENEWS — and false of a Plus that
     * was given away and expires. For that user what is left to sell is precisely the
     * continuation, and with the old rule the screen slammed shut the instant it opened: the
     * end-of-promo reminder would have been a door with a wall behind it.
     *
     * It still closes the moment the grant becomes a purchase, because the purchase arrives
     * through the same stream and clears [plusPromoEndsAtMillis] with it.
     */
    val dismissesItself: Boolean get() = isPlus && plusPromoEndsAtMillis == null

    val selectedOffer: PlusOffer? get() = offers.firstOrNull { it.plan == selectedPlan }
}
