package com.rndeveloper.paparcar.presentation.home.model

/**
 * Home has NO position at all — the fact the map's pill states, and the one the camera falls back on
 * when it decides to fly to the parked car instead of waiting for a fix that will never land.
 * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
 *
 * Two roads to the same absence, and the app only knows each one from a different place:
 *  - **the CORE permission is not granted** — known on its own, with or without a registered car;
 *  - **location services are off** while the permission IS granted — a fact only detection readiness
 *    reports, via [warnsLocationMissing] (`BlockedCore`).
 *
 * ⛔ **It must not require a car.** The pill used to read `warnsLocationMissing` alone, and that
 * predicate only exists with a registered vehicle and detection on — so the user who had just
 * refused the permission and had no car yet got a Home that said NOTHING about why their position
 * was missing. That is the person the sentence is for.
 *
 * ⛔ And it is ONE definition. `HomeScreen` carried a second copy of these same two terms for the
 * camera decision; two spellings of "there is no location" is how a map ends up flying to the car
 * while the pill stays quiet, or the other way round.
 *
 * The amber row in the list keeps reading [warnsLocationMissing] and NOT this: that row speaks for
 * the WATCH being blocked, which is a different subject and genuinely needs a car to be about.
 */
fun homeHasNoLocation(hasCorePermissions: Boolean, detectionUiState: DetectionUiState): Boolean =
    !hasCorePermissions || detectionUiState.warnsLocationMissing

/**
 * The first-entry location ask is about to be made, or is on screen right now with no answer read
 * yet — so Home must NOT state the absence. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
 *
 * [homeHasNoLocation] is the FACT; this is whether Home is allowed to SAY it. They are separate
 * because the two consumers want different things: the pill and the amber row are sentences and must
 * wait for the answer, while the camera ACTS on the fact — flying to the parked car behind the
 * dialog is the best version of the context that dialog is opened over, and making it wait would
 * leave Home at (0, 0) behind the question. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
 *
 * ⛔ **Both terms are load-bearing**, and each covers a window the other misses:
 *  - [locationEverAsked] is stamped BEFORE the dialog launches (`askFineLocationOnFirstEntry`), so
 *    on its own it is already `true` for the whole time the dialog is up — it only covers the beat
 *    between Home's first frame and the `SetMapForeground(true)` that fires the ask;
 *  - [locationAnswerInFlight] covers exactly that dialog window, and nothing before it.
 *
 * ⛔ And [hasCorePermissions] has to come first: a user who granted the permission and then turned
 * location services OFF was never asked anything, so without this term their pill would go quiet
 * forever over a question that is never coming.
 *
 * A refusal already has its own moment — `warningOpenCue` opens the sheet onto the warning once
 * the answer is read [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]. This one only holds the
 * sentence until that moment arrives.
 */
fun homeAwaitsLocationAnswer(
    hasCorePermissions: Boolean,
    locationEverAsked: Boolean,
    locationAnswerInFlight: Boolean,
): Boolean = !hasCorePermissions && (!locationEverAsked || locationAnswerInFlight)
