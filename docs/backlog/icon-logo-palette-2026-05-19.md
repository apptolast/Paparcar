# Icon & Logo — palette refresh — 2026-05-19

Adapt the 2022 source artwork (`Documents/Paparcar/2022/Icon Logo 2022/`) to the
current Paparcar palette (`ui/theme/Color.kt`). The source artwork uses a legacy
corporate blue gradient (`#2196F3 → #3F51B5`) plus a white car glyph; the new
icon must use the brand neon-green family (`PapGreen` / `PapGreenDark`) with the
same white glyph.

## Status legend
✅ **Done** — merged to master
🔵 **Branch ready** — work complete, awaiting review/merge
⚪ **Pending** — not started
🟡 **Blocked** — waiting on user

---

## 1. `ICON-LOGO-PALETTE-001` — adaptive icon repaint — ✅ Done

**Scope:** Rebuild Android adaptive icon (`mipmap-anydpi-v26/ic_launcher*.xml`)
using vector drawables only, in the Paparcar neon-green palette.

**Deliverables (all shipped):**
- `drawable/ic_launcher_background.xml` — vertical linear gradient
  `PapInkContainer (#141918)` → `PapInk (#0B0F0E)` on a 108×108 viewport.
  Matches the dark-theme surface family (the "near-black background" used in
  the app under dark theme).
- `drawable/ic_launcher_foreground.xml` — pure `VectorDrawable` built from
  `paparcar_icon_fore.svg`. Car body as a single `PapGreen (#25F48C)`-filled
  path, two wheels as stroked elliptical paths (stroke `#25F48C`, width 6) —
  reproduces the brand "neon-green on near-black" pairing of the dark theme.
  Wrapped in an outer `<group>` with `scaleX/Y = 0.8` and pivot `(54, 54)` so
  the glyph sits comfortably inside Android's 72dp safe zone with breathing
  room (matches the proportion of the 2022 presentation artwork). Previously
  this file was a `layer-list` wrapping a raster PNG with 16dp inset; the
  inset is no longer needed.
- `mipmap-*/ic_launcher_foreground.png` — **deleted** at every density. The
  foreground is now vector-only; the PNG rasters were only consumed by the
  layer-list wrapper.
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` — unchanged;
  they keep referencing `@drawable/ic_launcher_background` /
  `@drawable/ic_launcher_foreground` (plus monochrome for Android 13+ themed
  icons).

**Verification:** `./gradlew :composeApp:processDebugResources` succeeds.
Compiled resources show both XML drawables packaged; no broken references.

---

## 2. `ICON-LOGO-PALETTE-002` — legacy mipmap PNGs — ✅ Done

**Scope:** Regenerate the pre-API-26 launcher fallbacks at every density:
- `mipmap-mdpi/ic_launcher.png` + `ic_launcher_round.png`
- `mipmap-hdpi/ic_launcher.png` + `ic_launcher_round.png`
- `mipmap-xhdpi/ic_launcher.png` + `ic_launcher_round.png`
- `mipmap-xxhdpi/ic_launcher.png` + `ic_launcher_round.png`
- `mipmap-xxxhdpi/ic_launcher.png` + `ic_launcher_round.png`

**Runtime impact:** Zero. `minSdk = 26` (see `gradle/libs.versions.toml`),
adaptive icons are supported on every device the app runs on, and the system
picks `mipmap-anydpi-v26/ic_launcher.xml` over the density-bucketed PNGs. The
legacy PNGs are currently stale (old artwork) but inert — they will only
matter if someone unpacks the APK or a tooling step reads them directly.

**How it was done:** Chrome headless (`--headless=new --screenshot`) was used as
an SVG rasterizer. A self-contained HTML file (`paparcar_icon_gen.html`) draws
the icon on a `<canvas>` using Canvas 2D API — SVG path data reproduced via
`Path2D`, ellipse wheels drawn with `ctx.ellipse()` and the original SVG
transforms replayed via `ctx.translate/rotate`. Chrome was invoked once per
size×variant with `--window-size=NxN --force-device-scale-factor=1`.

---

## 3. `ICON-LOGO-PALETTE-003` — Play Store hi-res icon — ⚪ Pending

**Scope:** Produce a 512×512 high-res launcher icon for Google Play Console
("Hi-res icon" upload). Source from the same SVG, exported as PNG with the
PapGreen → PapGreenDark gradient background and the white glyph.

**Why separate:** the hi-res icon is uploaded to Play Console, not packaged
inside the APK, so it's outside the source tree. Worth doing in the same
session as `ICON-LOGO-PALETTE-002` since both need the same SVG → PNG
rendering tool.

---

## 4. `ICON-LOGO-PALETTE-004` — light/dark verification — ⚪ Pending

**Scope:** Visual smoke test on a real device / emulator:
- Light theme launcher background → icon legible? (PapGreen on white)
- Dark theme launcher background → icon legible? (PapGreen on near-black)
- Themed icons on Android 13+ (monochrome layer reuses the same vector) →
  glyph reads correctly when the system tints to wallpaper accent?
- Adaptive shape variants (circle, squircle, rounded square, teardrop) →
  car glyph stays inside the safe zone on every Android launcher mask?

If any variant looks off, follow up in `ICON-LOGO-PALETTE-001` (vector tweak)
rather than re-rendering PNGs.
