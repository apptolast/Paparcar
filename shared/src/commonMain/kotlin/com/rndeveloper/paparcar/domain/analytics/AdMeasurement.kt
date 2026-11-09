package com.rndeveloper.paparcar.domain.analytics

/**
 * What the user said about advertising measurement on THIS install.
 * [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]
 *
 * ⛔ Three states, not a Boolean, on purpose: "never asked" and "said no" are different facts. A
 * Boolean defaulting to false would either ask again forever (treating a no as never-asked) or
 * never ask at all (treating never-asked as a no) — and the second one is how a consent prompt
 * silently stops existing.
 *
 * ⛔ And it is its OWN consent, separate from accepting the privacy policy. Reading the device's
 * advertising ID to measure ads is the mobile equivalent of a marketing cookie: it needs a
 * specific, optional yes that can be refused as easily as given, and that the app works the same
 * without. Bundling it with account creation (the 1.5.0 design) made it neither specific nor
 * free, and left everyone who signed in with Google outside it for good.
 *
 * Per install, like the cookies it stands in for: it is not wiped on sign-out.
 */
enum class AdMeasurementConsent { UNASKED, GRANTED, DENIED }

/**
 * The advertising-measurement lane of this platform, seen from the domain.
 *
 * Android binds the Meta implementation; iOS and the mock flavor have no lane, and get
 * [NoAdMeasurementLane] — so nothing is asked on a platform that would send nothing.
 */
interface AdMeasurementLane {
    /** There is an ad network in this build AND it has its keys. False ⇒ never ask, never show the row. */
    val isAvailable: Boolean

    /** Switch the lane on or off to match [consent]. Never throws. */
    fun onConsentChanged(consent: AdMeasurementConsent)
}

/** The lane of a build without an ad network. */
object NoAdMeasurementLane : AdMeasurementLane {
    override val isAvailable: Boolean = false
    override fun onConsentChanged(consent: AdMeasurementConsent) = Unit
}

/** The prompt is owed only while the lane exists and the user has not answered yet. */
fun shouldAskAdMeasurementConsent(lane: AdMeasurementLane, consent: AdMeasurementConsent): Boolean =
    lane.isAvailable && consent == AdMeasurementConsent.UNASKED
