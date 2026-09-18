package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ── Pap brand palette ─────────────────────────────────────────────────────────

// Primary accent
val PapGreen         = Color(0xFF25F48C)   // neon green — brand primary (dark theme)

// ── Ink ramp — near-black surfaces (dark theme primary surfaces) ─────────────
// Neutral-cool dark palette; green brand DNA lives in accent tokens, not surfaces.
val PapInkDeep       = Color(0xFF08090E)   // surfaceContainerLowest — behind the map
val PapInk           = Color(0xFF0D1117)   // surface — app base
val PapInkContainer  = Color(0xFF131A24)   // surfaceContainer — sheet, nav
val PapInkHigh       = Color(0xFF1A2232)   // surfaceContainerHigh — cards
val PapInkHighest    = Color(0xFF222C3E)   // surfaceContainerHighest — modals, popovers

// Forest greens — demoted from surface to interactive accents (containers, outlines)
val PapGreenMuted    = Color(0xFF133D28)   // primaryContainer — dark green accent
val PapGreenOutline  = Color(0xFF226D49)   // secondary-action border — visible but non-neon green

// On-dark text
val PapNeutralOutline      = Color(0xFF3B4A5E)   // neutral-cool outline for dark surfaces
val PapNeutralOutlineLight = Color(0xFF7A8FA0)   // neutral-cool outline for light surfaces

val PapOnDark        = Color(0xFFEBF2EF)   // primary text on dark surfaces
val PapOnDarkMuted   = Color(0xFF8EA0B4)   // secondary / disabled text — neutral cool

// Amber (secondary / warning)
val PapAmber         = Color(0xFFF4A825)
val PapAmberMuted    = Color(0xFF3D2A10)

// ── Semantic status tokens — dark theme ───────────────────────────────────────

// Spot reliability: HIGH → neon green (reuses PapGreen / PapGreenMuted)

// Spot reliability: MEDIUM → amber (reuses PapAmber / PapAmberMuted)

// Spot reliability: LOW / urgency
val PapRed           = Color(0xFFFF5252)   // urgent / low TTL / error
val PapRedMuted      = Color(0xFF3D1010)   // red container dark
val PapOnRed         = Color(0xFF1C0606)

// ⛔ Framework `tertiary` slot backing ONLY — the tertiary role is RETIRED [UI-COLOR-DOCTRINE-001 F6].
// These tokens exist solely so the MaterialTheme scheme slots hold sane values.
// Feature code must never read `colorScheme.tertiary*` — enforced by ColorGuardrailTest.
//
// `PapBlue` (#5B9EFF) and `PapBlueLight` (#0057CA) used to live here as separate names holding the
// EXACT values of `PapCarBlueDark` / `PapCarBlueLight` — the same duplicate-hex pattern §1 of
// COLOR-SYSTEM.md was written to kill, surviving under new names. The scheme now backs its blue
// slots with the car-blue tokens themselves, so there is one blue with one story.
// [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
val PapBlueMuted     = Color(0xFF0F1F3D)
val PapOnBlue        = Color(0xFF061021)

// ── Live — MOVEMENT ON THE MAP, fixed across themes. [UI-COLOR-DOCTRINE-001] ──────────────────
// One meaning, map-only: the trip trail, the trip-origin dot, the "reserved · someone en route"
// spot, and the follow-car FAB tint. Markers and polylines sit on street/satellite imagery, not on
// our surface, so this tone never theme-inverts. Replaces PapDriveBlue + SpotPalette.EnRouteBlue +
// LOC_HALO_BLUE. In the UI, movement is told by ANIMATION in the vehicle's identity colour — there
// is no "driving blue" accent anymore.
val PapLiveMap       = Color(0xFF2F6BFF)   // trail, origin dot, en-route pin, follow FAB — fixed

// ── The three greens ──────────────────────────────────────────────────────────────────────────
// Until [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001] the app had ONE green telling three unrelated
// stories: the brand, a vehicle the app is watching, and a stranger's free spot. On the map the
// last two were the same literal value (#009F5E) on the same layer at the same time — measured
// ΔE00 = 0.00. The doctrine called the sharing deliberate; being indistinguishable is not a
// decision, it is the absence of one.
//
// The three now sit on a HUE spectrum with the brand in the middle:
//
//     FRESH lime (h≈128)  ——  BRAND mint (h≈151)  ——  WATCH teal-emerald (h≈166)
//
// Separation is carried by hue, never by lightness or alpha. That is not a preference: v1 was
// revoked on device precisely because its most important distinction rode on the alpha of a 1dp
// border — an EMPHASIS axis. Two greens split only by lightness read as "the same colour, weaker",
// which is the same mistake wearing a different hat. Every pair holds ΔE00 ≥ 12 and ≥ 14° of hue in
// both themes, and each value meets the contrast floor of the job it actually does.

/** A vehicle the app is ACTIVELY WATCHING (Coordinator/assisted tier) — its name's glyph, badge,
 *  border and map marker. Deep teal-emerald: the instrument colour, "on duty", kin to the brand but
 *  never mistakable for it. Not cyan — v1's cyan was rejected on device as incomprehensible.
 *  5.97:1 on `PapInkHighest`; ΔE00 16.8 from `PapGreen`. */
val PapWatchGreen      = Color(0xFF0FBF9A)

/** Light-theme leg of [PapWatchGreen], and the FIXED tone of the vehicle marker on the map (markers
 *  float over street tiles, so they do not theme-invert).
 *
 *  Was #00543D and revoked on device with the rest of the light trio: at L\*=31 / C\*=30 it was the
 *  worst offender — dark AND dull, which is the "camouflage" the user saw. This one lives at L\*=50
 *  with half again the chroma, alongside the corporate green instead of underneath it. 4.47:1 on
 *  white (its marker ring), 3.92:1 on the scaffold; ΔE00 12.1 from the brand. */
val PapWatchGreenLight = Color(0xFF05876D)

/** Solid container for a card that IS an actively-watched vehicle — the live-session card of the
 *  history timeline. The green leg of `vehicleIdentityContainer`, which used to borrow the BRAND's
 *  `primaryContainer`: after the greens split that would have filled a watched car's card with the
 *  brand's green while its own dot and border read teal. 5.37:1 with [PapWatchGreen] on it. */
val PapWatchGreenMuted          = Color(0xFF0B3A31)

/** Light-theme leg of [PapWatchGreenMuted]. 13.23:1 with [PapOnWatchGreenContainerLight]. */
val PapWatchGreenContainerLight = Color(0xFFAAF0D8)

/** Content on [PapWatchGreenContainerLight]. */
val PapOnWatchGreenContainerLight = Color(0xFF00201A)

/** A community spot still FRESH — head of the freshness ramp (fresh → cooling → expiring), a scale
 *  the doctrine reserves exclusively for spot expiry. Lime side of green, so it never collides with
 *  the brand or with a watched car. A bright FILL that carries `PapInk` text: 12.41:1 against its
 *  own label, 9.20:1 against the card behind it. */
val PapSpotFresh       = Color(0xFF8FE83C)

/** The dark bed a fresh-spot TTL chip writes [PapSpotFresh] on — the lime mirror of
 *  `PapGreenMuted`, which is the brand's container and was standing in for this. 8.35:1. */
val PapSpotFreshMuted  = Color(0xFF0F3B08)

// ── The spot ramp in the LIGHT theme ─────────────────────────────────────────────────────────
// The ramp is EXCLUSIVE to spot expiry, but its light legs used to borrow the theme's own amber and
// red — the tokens that also mean "warning" and "error". Those are dark by necessity, which is why
// the spots read muddy as a SET, not just the green. The ramp now owns its three light values, and
// they are the map's: one ramp, the same three tones in the sheet and on the tiles.
//
// ⚠️ Contrast, stated plainly: as TEXT on a white card these measure 2.34 / 2.85 / 4.50 : 1, below
// the 4.5 floor for the first two. That is a DELIBERATE, user-made trade-off taken on device
// (29-08) after seeing both: the AA-compliant version was judged too dark for what these are —
// a freshness signal, not prose. The way to have both is to render the reliability label as a
// FILLED badge (vivid fill + ink text, 8.07:1) instead of coloured text — a change to how the
// label is BUILT, not to these values. Left as a follow-up, not done silently. It would give
// `SpotStateColors` back the `on` leg that was dropped once nothing painted a fill with text on it.
// [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001] [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]

/** Light-theme leg of [PapSpotFresh] — reliability label, peek eyebrow, accent rows, meter. */
val PapSpotFreshLight  = Color(0xFF5FBF1F)

/** Cooling spot, light theme. Was `PapAmberLight`, which is the theme's WARNING amber. */
val PapSpotCoolingLight = Color(0xFFE08200)

/** Expiring spot, light theme. Was `PapRedLight`, which is the theme's ERROR red. */
val PapSpotExpiringLight = Color(0xFFE0322F)

/**
 * The fresh tier as TEXT on a white card — the "FIABLE" label, the peek eyebrow. Small text, so the
 * floor is 4.5:1 and this measures 4.53:1. [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
 */
val PapSpotFreshDeep   = Color(0xFF398701)

/**
 * The fresh tier as the PUCK — the round spot marker on the map and its twin in the sheet row.
 *
 * Brighter than [PapSpotFreshDeep] because the two jobs stopped sharing a floor: the puck carries a
 * large bold "P", a graphical glyph whose floor is 3:1, not the 4.5:1 small text needs. At 3.03:1
 * this clears it with more room than the amber puck next to it has ever had (#E08200 = 2.85:1) —
 * and it fixes an inconsistency the ramp was already carrying, where the amber and red pucks were
 * vivid and only the green one sat dark.
 */
val PapSpotFreshPuck   = Color(0xFF4FA80A)

// ── Premium sheen — the metallic FINISH of the brand green, for Paparcar Plus ─────────────────
// [UI-COLOR-PLUS-WEARS-THE-BRAND-WITH-A-FINISH-001]
//
// Plus is not a fourth identity: it is the BRAND, dressed up. A flat fourth green would have to
// hold ≥14° of hue from the other three, and there is no room left on the green spectrum between
// fresh (h≈128), brand (h≈151) and watch (h≈166) — a token squeezed in there would be exactly the
// "same colour, weaker" mistake the three-greens doctrine was written to kill.
//
// So premium is carried by FINISH, not by hue: these are the shadow, bed and specular stops of a
// metallic sweep at the brand's own hue. A gradient is what reads as metal; a single flat tone
// never does. Only `PapPremiumSurface` builds the brush — nothing else names these.
// A gradient stop is not a content colour: text on this bed keeps using onSurface/onPrimary.
//
// ⛔ THREE device revocations live in this block, and the third is the one worth reading, because
// the first two were the same mistake mirrored:
//   1. Shipped at h≈161.5° → read as the WATCHED-VEHICLE teal. Hue fixed.
//   2. Right hue, bed at L*≈10-18% → read as dark forest. At that luminosity hue is not perceived.
//   3. Right hue, bed at L*≈76 and C*≈71 — the brand NEON filling a whole card — → «demasiado
//      cantoso… apenas se le nota el brillo metálico».
//
// 🔑 The third revocation is TWO complaints with ONE cause, and it is the lesson this block exists
// for. `PapGreen` is calibrated as an ACCENT: a button, a glyph, a 2 dp border. The chroma that
// reads as energetic across 200 px² reads as garish across a 20 000 px² card — AREA AMPLIFIES
// CHROMA, and the colour doctrine had no rule about that (it governs hue, and the hue was perfect:
// 0.2° of drift). And because that bed already sat at the top of the lightness range, there was no
// headroom left ABOVE it for a highlight — you cannot have a specular reflection on a surface that
// is already at its maximum. Loud and flat were the same defect.
//
// So the fix is two-axis, at an unchanged hue: drop the chroma (71 → 55) so the bed reads as
// BRUSHED brand green rather than neon, and drop the bed so the sweep spans 28 L* instead of 18,
// leaving room for a narrow specular band.
//
// ⚠️ The dark ink is what makes that possible, so do not "fix" it back to light text: with dark
// content, a brighter band can only IMPROVE contrast. Flip to white ink and every highlight becomes
// a threat to legibility — which is the trap the dark-bed version (2) fell into.
// [UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001]

/** Deep stop of the Plus metal — L*62.0 · C*55.0 · 0.0° from the brand. The DARKEST the sweep
 *  goes: the binding constraint for the ink, which clears 4.5:1 here with room (6.38:1). */
val PapPremiumDeep   = Color(0xFF39B66E)

/** Bed of the Plus metal — L*70.9 · C*58.8 · 0.0° from the brand. The tone that covers most of a
 *  card, and therefore the one that decides whether Plus reads calm or loud. Sits between the
 *  version revoked as garish (L*76 · C*71) and the muted one that followed (L*60 · C*50). */
val PapPremiumBed    = Color(0xFF44C479)

/** Lit stop of the Plus metal — L*86.1 · C*46.2 · 0.1° from the brand.
 *
 *  🔑 **Lighter AND less saturated than the bed** (+15 L*, −13 chroma), which is the whole trick:
 *  a reflection tends toward the colour of the LIGHT, so a desaturating peak reads as light
 *  landing on green, while a more-saturated one just reads as a greener green. An earlier cut had
 *  the sheen MORE saturated than the bed and the surface looked like a gradient.
 *
 *  ⛔ Restraint lives HERE, in the lightness range, and nowhere else. This paragraph used to say
 *  the finish was "ONE highlight, not the three bands plus brushed grain that were revoked" — and
 *  the surface that ships has had the bands AND the grain since `3ec0088a`. What was revoked on
 *  device was spreading them over 25 L*, not having them: the same structure compressed into this
 *  range reads as a finish. Believing the old sentence would mean undoing the fix.
 *  What carries the metal is the SHAPE of these bands — see `metallicSweep`.
 *  [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001] */
val PapPremiumSheen  = Color(0xFF64D48D)

// ── Car blue — the BLUETOOTH-watched vehicle's identity colour. ───────────────────────────────
// A vehicle's colour is its WATCH METHOD (green = active detection, blue = BT, grey = off) and it
// never changes with state; [papCarBlue] is the blue leg, resolved via
// `VehicleIdentity.vehicleIdentityColor`. Values keep the old "BT blue" the user already reads as
// their BT-tracked car. [UI-COLOR-DOCTRINE-001]
val PapCarBlueDark   = Color(0xFF5B9EFF)   // BT identity on dark surfaces — 7.0:1 on PapInk
val PapCarBlueLight  = Color(0xFF0057CA)   // BT identity on light surfaces — 6.5:1 on white

// ── Car-park blue — a THIRD PARTY'S facility, not a vehicle and not a free spot. ───────────────
// The map's blues used to be about movement: [papCarBlue] is a vehicle's WATCH METHOD and
// [PapLiveMap] is movement that is not yours. A public car park is neither — it is a PLACE you
// drive into, run by someone else, and the P on blue is the sign every driver already reads on the
// street. It gets its own token instead of borrowing the BT blue because a colour here means one
// thing: a car park wearing the BT blue would make the same hue say "this car is watched by
// Bluetooth" and "this is a car park" on the same screen. [UI-COLOR-DOCTRINE-001]
//
// ⚠️ Blue is CROWDED, and these values are the closest call in the palette: on dark surfaces this
// sits 87/765 from PapCarBlueDark and 94/765 from PapLiveMap. What separates them in practice is
// what wears them — a filled puck (car park), a vehicle glyph (BT) and a trail (live) — not the
// hue alone. If the device test says they read as one blue, the fallback is a deep navy fixed in
// both themes. [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001]
val PapCarParkBlueDark  = Color(0xFF3E8FD4)   // car-park fill on dark surfaces — white P at 3.45:1
val PapCarParkBlueLight = Color(0xFF00468F)   // car-park fill on light surfaces — white P at 9.24:1

// ── Light theme counterparts ──────────────────────────────────────────────────

// ── Azure ramp — blue-navy surfaces for light theme (H≈217°, mirrors PapInk DNA) ─
// Same hue family as the dark PapInk ramp but at the opposite luminosity pole,
// so light and dark themes share the same cool blue character at both extremes.
val PapAzureLowest   = Color(0xFFFFFFFF)   // surfaceContainerLowest
val PapAzureLow      = Color(0xFFF4F6FC)   // surfaceContainerLow
val PapAzure         = Color(0xFFECF0F9)   // surfaceContainer — sheet, nav

// Teal-shifted emerald ramp — same hue DNA as PapGreen (#25F48C ≈ H150°).
// onPrimary = Color.White in light theme so filled surfaces (buttons, chips, icon circles) use
// white content on the green fill.
//
// ⚠️ **This is the corporate green and it does NOT move.** It was briefly changed to #237A46 to
// clear WCAG AA as text (3.01:1 against the light scaffold, where normal text wants 4.5:1) and the
// user revoked it ON DEVICE (29-08): the darker green read as camouflage and the logo's identity was
// gone. Measured, that verdict is exact — #237A46 sits 12.5 L* points below this one, and the whole
// light trio built around it landed 15-25 points below where the dark trio lives.
//
// The contrast finding stands and is a KNOWN, ACCEPTED trade-off, not an oversight: as a fill
// carrying white content, and as a glyph/border (graphical objects, floor 3.0), this token is fine.
// Where it is small green TEXT on a light surface it is below AA, exactly as it has shipped for
// months. Revisit by giving TEXT its own darker derivative — never by dulling the brand.
// [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
val PapGreenLight            = Color(0xFF009F5E)

/** Secondary-action border in light theme. Deliberately the SAME value as [PapGreenLight] and
 *  declared as an alias rather than a second literal: "the brand green read as a border" is the
 *  same story, not a new one. Two names holding one hex by accident is the bug; by declaration it
 *  is a system. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001] */
val PapGreenOutlineLight     = PapGreenLight
val PapGreenContainerLight   = Color(0xFFA8F5D0)
val PapOnGreenContainerLight = Color(0xFF002819)
val PapSurfaceLight          = Color(0xFFF0F4FB)  // page background — blue-tinted (H217°)
val PapCardLight             = Color(0xFFFFFFFF)  // card / sheet surface — white
val PapOnSurfaceLight        = Color(0xFF0E1A2E)  // primary text — deep navy
val PapVariantLight          = Color(0xFFD8E0EE)  // surfaceVariant — blue-grey
val PapOnVariantLight        = Color(0xFF374460)  // onSurfaceVariant — blue-dark
val PapOutlineVariantLight   = Color(0xFFBDC8DF)  // subtle dividers — blue-grey
val PapInverseSurfaceLight   = Color(0xFF0D1B33)  // dark navy for Snackbar/Toast
val PapInverseOnSurfaceLight = Color(0xFFE8EDF8)  // text on inverse surface
val PapAmberLight            = Color(0xFFB56000)
val PapAmberContainerLight   = Color(0xFFFFDDB3)
// Content ON the light amber container. Was #3D2A10 — the exact value of `PapAmberMuted`, which is
// the DARK theme's amber container FILL: a fill and a content colour, two different stories, holding
// one hex by coincidence. Caught by `ColorGuardrailTest`, not by eye. #402400 reads as the same
// brown and measures better against its own bed: 11.05:1 on PapAmberContainerLight (was 10.57:1).
// [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
val PapOnAmberContainerLight = Color(0xFF402400)

// Light semantic counterparts
val PapRedLight       = Color(0xFFBA1A1A)
val PapBlueContainerLight = Color(0xFFD8E2FF)

/** Theme-aware secondary-action border green — outlined buttons, the sheet edit icon-button.
 *  Same luminance probe as `SpotStateColors.stateColors()`. [UI-SHEET-001] */
val greenOutline: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) PapGreenOutline
        else PapGreenOutlineLight

/**
 * Theme-aware **Bluetooth identity** accent — the colour of a BT-watched vehicle's name, glyph,
 * badge and border. Never for community spots, never for a state, never for anything that is not
 * the user's own BT-watched vehicle. Resolve through `vehicleIdentityColor`, not directly.
 * [UI-COLOR-DOCTRINE-001]
 */
val papCarBlue: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) PapCarBlueDark
        else PapCarBlueLight

/**
 * Theme-aware **active-detection identity** accent — the colour of a vehicle the app is watching via
 * the Coordinator tier: its glyph, badge, border and marker. The green leg of
 * `vehicleIdentityColor`, and no longer `colorScheme.primary`: a watched car and a CTA used to be
 * the same pixel, so "the app is watching this car" and "press me" were indistinguishable.
 * Resolve through `vehicleIdentityColor`, not directly. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
 */
val papWatchGreen: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) PapWatchGreen
        else PapWatchGreenLight

/** Theme-aware fill for a FRESH community spot — the head of the freshness ramp. Never for a
 *  vehicle, never for the brand. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001] */
val papSpotFresh: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) PapSpotFresh
        else PapSpotFreshLight

// ── Text legs — the same story, dark enough to be READ ───────────────────────────────────────
// A colour that fills a button and a colour that spells a word have different jobs and different
// floors: a graphical object needs 3:1, small text needs 4.5:1. Light-theme greens vivid enough to
// look right as a fill land at 2.3-3.0:1 as text — legible indoors with good eyesight, washed out
// in sunlight or with reduced vision. Splitting the story in two is what lets the fill stay vivid
// AND the letters stay readable, instead of trading one for the other.
// Dark theme needs no split: a vivid colour on a near-black bed already clears 4.5:1.
// [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]

/** Brand green as TEXT on a light surface — links, figures, labels. 5.32:1 on white, 4.67:1 on the
 *  scaffold. `PapGreenLight` (#009F5E, 3.01:1) stays the FILL: buttons, glyphs, markers, the logo. */
val PapGreenTextLight        = Color(0xFF237A46)

// The ramp's other two text legs are `PapAmberLight` and `PapRedLight` — the theme's own warning
// and error tones, reused deliberately and NOT copied into new tokens. As a FILL the ramp must not
// borrow them (that is what made spots read muddy), but as TEXT every one of these has to be dark
// to be read, and at that darkness the ramp and the theme's warning/error genuinely converge.
// Minting near-identical tokens to pretend otherwise would be the duplicate-hex disease with better
// manners. Only the fresh tier needs its own ([PapSpotFreshDeep]): the theme has no green to reuse.

// ── The age pill, light theme ────────────────────────────────────────────────────────────────
// `SpotAgeIndicator` is a TONAL pill: a soft bed of the tier with the tier written on it. It only
// ever had a dark bed, so in the light theme it rendered as a near-black lozenge on a white sheet —
// it was never theme-aware, from before the colour refactor. These are its light legs.
// [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]

/** Pale lime bed of the fresh age pill. */
val PapSpotFreshContainerLight   = Color(0xFFDEF5C7)

/** Content on [PapSpotFreshContainerLight] — 5.38:1. Deeper than the text leg because a tinted bed
 *  eats contrast that white does not. */
val PapOnSpotFreshContainerLight = Color(0xFF2E6E01)

/** Pale bed of the expiring age pill; its content is `PapRedLight` — 5.00:1. */
val PapSpotExpiringContainerLight = Color(0xFFFFDAD6)

// The cooling pill reuses `PapAmberContainerLight` + `PapOnAmberContainerLight` (11.05:1). A pale
// amber bed IS a pale amber bed: minting a near-identical token to keep the story names apart would
// be the duplicate-hex disease with better manners, which this system exists to prevent.

/**
 * The three tones of the Plus metal. [UI-PLUS-THE-METAL-IS-AN-OBJECT-NOT-A-BACKGROUND-001]
 *
 * ⛔ **The SAME in both themes, on purpose** — this used to have light legs and they were a
 * mistake, revoked on device with «en tema claro chirría bastante». Two things were wrong:
 *
 *  · **A theme leg exists so a colour stays legible against the SURFACE behind it. This surface
 *    is opaque and carries its own ink, so it has nothing to stay legible against.** It is an
 *    OBJECT sitting on the page, not a background. A brushed-metal card does not change alloy
 *    when you turn the lights on.
 *  · The light legs sat at L*25-49 on a page at L*98 — a 59 L* drop. In the dark theme the metal
 *    sits ABOVE the page and reads as a lit object; in the light theme it sat far below and read
 *    as a hole in shadow. Metal is bright because it REFLECTS; a dark slab is not a finish.
 *
 * 🔑 The reason the light legs went dark in the first place is worth keeping: the surface took its
 * content colour from `colorScheme.onPrimary`, which is WHITE in the light theme, and white ink
 * forces a dark bed. The bed was bent to fit BORROWED ink. Hence [premiumInk] below — the ink is a
 * property of this surface, not of the theme's primary.
 *
 * The sheen is 0.1° from the LOGO's green (`#25F48C`), which is the brand green the user actually
 * sees — the logo does not theme-invert either.
 *
 * This says WHICH greens the finish is made of. It deliberately does NOT say where the highlight
 * falls or how narrow it is: that is the shape of the reflection, it belongs to the surface, and
 * `PapPremiumSurface` is the only thing that turns these into a brush.
 * [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001] [UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001]
 */
@Immutable
data class PremiumSweep(val shadow: Color, val bed: Color, val sheen: Color)

val premiumSweep: PremiumSweep
    @Composable get() = PremiumSweep(PapPremiumDeep, PapPremiumBed, PapPremiumSheen)

/**
 * The ink that rides ON the Plus metal — dark in both themes, because the metal is the same in
 * both. [UI-PLUS-THE-METAL-IS-AN-OBJECT-NOT-A-BACKGROUND-001]
 *
 * ⛔ Not `colorScheme.onPrimary`. That is the ink for the THEME's primary fill, and the two only
 * coincided in the dark theme by luck: in the light theme `onPrimary` is white, and every call
 * site reading it was writing white text on what should be a bright green surface. Clears 6.40:1
 * on the darkest stop of the sweep.
 */
val premiumInk: Color
    @Composable get() = PapInk

/**
 * The polished rim of a Plus surface — the thin line that gives the metal an edge.
 *
 * White on a dark page: the lit corner of brushed metal. On a LIGHT page a white rim is invisible
 * and leaves the card unbounded against the paper, so there the rim is the sweep's own shadow
 * tone instead — the same physical idea (the edge turning away from the light), pointed the other
 * way. A graphical object (3:1 floor), never text.
 * [UI-PLUS-THE-METAL-IS-AN-OBJECT-NOT-A-BACKGROUND-001]
 */
val premiumEdge: Color
    @Composable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE) {
            Color.White
        } else {
            PapPremiumDeep
        }

/** Luminance below which a surface counts as "dark" for picking a theme variant. */
internal const val SURFACE_DARK_LUMINANCE = 0.5f
