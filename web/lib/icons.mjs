/**
 * Icon set — the web leg of the app's three-tier icon rule (CLAUDE.md § Iconos).
 *
 * Tier 1 (chrome) and tier 2 (UI) are drawn on the same 24 grid as Material Symbols Rounded, filled
 * and with rounded joins, so they read as siblings of what ships inside the app. They carry NO
 * colour: `currentColor` only, so the call site tints them — exactly like `tint` does in Compose.
 *
 * Tier 3 (illustration, vehicle glyphs, markers) is NOT here: those come from the app's own
 * `illus_*` VectorDrawables, converted by `tools/prepare-assets.py`, and bring their own colour.
 */

import {
  MARK_PATHS,
  WORD_PATHS,
  LOCKUP_W,
  LOCKUP_H,
  LOCKUP_MARK_TRANSFORM,
  LOCKUP_WORD_TRANSFORM,
  LOCKUP_DARK,
} from './mark.generated.mjs';

const glyphs = {
  // ── tier 1 · chrome ────────────────────────────────────────────────────────
  menu: 'M4 7.5h16M4 12h16M4 16.5h16',
  close: 'M5.6 5.6 18.4 18.4M18.4 5.6 5.6 18.4',
  chevronDown: 'm7 10 5 5 5-5',
  chevronRight: 'm10 7 5 5-5 5',
  arrowRight: 'M4 12h15m-5.5-5.5L19 12l-5.5 5.5',
  check: 'm5 12.5 4.5 4.5L19 7.5',
  language: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM3.6 9h16.8M3.6 15h16.8M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18',
};

// Filled tier-2 glyphs: one path each, drawn on the 24 grid.
const filled = {
  pin: 'M12 2.5a6.8 6.8 0 0 0-6.8 6.8c0 4.7 5.6 10.9 6.1 11.4a1 1 0 0 0 1.4 0c.5-.5 6.1-6.7 6.1-11.4A6.8 6.8 0 0 0 12 2.5Zm0 9.4a2.6 2.6 0 1 1 0-5.2 2.6 2.6 0 0 1 0 5.2Z',
  auto: 'M13.6 2.2a.7.7 0 0 1 .65.95L12.3 9.1h4.35a.7.7 0 0 1 .55 1.14l-8.1 10.3a.7.7 0 0 1-1.24-.6l1.68-6.44H5.3a.7.7 0 0 1-.55-1.13l8.3-10.6a.7.7 0 0 1 .55-.27Z',
  // ⛔ A community SPOT is not a car park, and it must not wear the car park's "P"
  // [COPY-SPOT-IS-NOT-A-PARKING-001]. A car park is an installation someone runs; a spot is a gap
  // in the street that a driver just left. So: a HOLLOW pin — an empty space — with the dot of the
  // driver who freed it. `parking` keeps the P, on a square, because that is the road sign.
  spot: {
    rule: 'evenodd',
    d:
      'M12 2.4a7.2 7.2 0 0 0-7.2 7.2c0 5 6 11.5 6.5 12a1 1 0 0 0 1.4 0c.5-.5 6.5-7 6.5-12A7.2 7.2 0 0 0 12 2.4Z' +
      'M12 4.4a5.2 5.2 0 0 0-5.2 5.2c0 3.4 3.6 8.2 5.2 10 1.6-1.8 5.2-6.6 5.2-10A5.2 5.2 0 0 0 12 4.4Z' +
      'M12 6.9a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Z',
  },
  parking: 'M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Zm3.3 3.6a.9.9 0 0 0-.9.9v8.9a1 1 0 0 0 2 0v-2.6h2.2a3.6 3.6 0 0 0 0-7.2Zm1.1 2h2.2a1.6 1.6 0 0 1 0 3.2h-2.2Z',
  fuel: 'M4.8 3A1.8 1.8 0 0 1 6.6 1.2h5.8A1.8 1.8 0 0 1 14.2 3v17.2h1.3a.9.9 0 0 1 0 1.8H3.5a.9.9 0 0 1 0-1.8h1.3Zm2.2 1.6v4.2h5V4.6Zm10.1.1a.9.9 0 0 1 1.3 0l1.8 1.9c.5.5.8 1.2.8 1.9v7.9a1.9 1.9 0 0 1-3.8 0v-4.2h-1.4a.9.9 0 0 1 0-1.8h1.4V8.6l-2.1-2.2a.9.9 0 0 1 0-1.3Z',
  car: 'M5.6 5.4A2.4 2.4 0 0 1 7.9 3.8h8.2a2.4 2.4 0 0 1 2.3 1.6l1.4 4.1a2 2 0 0 1 1.2 1.8v5.4a1.5 1.5 0 0 1-1.5 1.5h-.6v1a1.4 1.4 0 0 1-2.8 0v-1H7.9v1a1.4 1.4 0 0 1-2.8 0v-1h-.6A1.5 1.5 0 0 1 3 16.7v-5.4c0-.8.5-1.5 1.2-1.8Zm2.1.8-1 3h10.6l-1-3a.6.6 0 0 0-.6-.4H8.3a.6.6 0 0 0-.6.4Zm-1 5.6a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Zm10.6 0a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Z',
  location: 'M12 1.4a.9.9 0 0 1 .9.9v1.35a8.1 8.1 0 0 1 7.05 7.05h1.35a.9.9 0 0 1 0 1.8h-1.35a8.1 8.1 0 0 1-7.05 7.05v1.35a.9.9 0 0 1-1.8 0v-1.35A8.1 8.1 0 0 1 4.05 13.1H2.7a.9.9 0 0 1 0-1.8h1.35A8.1 8.1 0 0 1 11.1 4.25V2.3a.9.9 0 0 1 .9-.9Zm0 4.7a6.1 6.1 0 1 0 0 12.2 6.1 6.1 0 0 0 0-12.2Zm0 3.2a2.9 2.9 0 1 1 0 5.8 2.9 2.9 0 0 1 0-5.8Z',
  steps: 'M13.4 1.9a2.1 2.1 0 1 1 0 4.2 2.1 2.1 0 0 1 0-4.2ZM11.6 7.1c1.4-.5 2.9.1 3.5 1.4l1 2.1c.3.6.8 1 1.4 1.2l1.6.5a1 1 0 0 1-.6 1.9l-1.6-.5a4.4 4.4 0 0 1-2.4-1.9l-.5 2.6 2.2 2.1c.4.4.6.9.6 1.4l.3 3.1a1.1 1.1 0 0 1-2.2.2l-.3-2.8-2.8-2.7-1 3.6-2.7 3.2a1.1 1.1 0 0 1-1.7-1.4l2.4-2.9L10.4 12l-1.5.9-.9 2.2a1 1 0 0 1-1.9-.8l1-2.5c.2-.4.5-.8.9-1Z',
  lock: 'M12 1.8A4.7 4.7 0 0 0 7.3 6.5v2.2h-.5A2.3 2.3 0 0 0 4.5 11v8a2.3 2.3 0 0 0 2.3 2.3h10.4A2.3 2.3 0 0 0 19.5 19v-8a2.3 2.3 0 0 0-2.3-2.3h-.5V6.5A4.7 4.7 0 0 0 12 1.8Zm0 2a2.7 2.7 0 0 1 2.7 2.7v2.2H9.3V6.5A2.7 2.7 0 0 1 12 3.8Zm0 9.4a1.9 1.9 0 0 1 1 3.5v1.6a1 1 0 0 1-2 0v-1.6a1.9 1.9 0 0 1 1-3.5Z',
  shield: 'M11.6 1.7a1 1 0 0 1 .8 0l7.2 3a1 1 0 0 1 .6.9v5.1c0 4.9-3.1 9.3-7.8 10.8a1 1 0 0 1-.8 0C7 20 3.8 15.6 3.8 10.7V5.6a1 1 0 0 1 .6-.9Zm4.3 6.5a1 1 0 0 0-1.4 0l-3.8 3.8-1.2-1.2a1 1 0 0 0-1.4 1.4l1.9 1.9a1 1 0 0 0 1.4 0l4.5-4.5a1 1 0 0 0 0-1.4Z',
  account: 'M12 2.2a9.8 9.8 0 1 0 0 19.6 9.8 9.8 0 0 0 0-19.6Zm0 3.4a3.3 3.3 0 1 1 0 6.6 3.3 3.3 0 0 1 0-6.6Zm0 14.4a7.8 7.8 0 0 1-5.3-2.1c.6-1.9 2.8-3.1 5.3-3.1s4.7 1.2 5.3 3.1a7.8 7.8 0 0 1-5.3 2.1Z',
  bluetooth: 'M11.3 2.1a1 1 0 0 1 1.1-.2l5 3a1 1 0 0 1 .1 1.7L13.2 10l4.3 3.4a1 1 0 0 1-.1 1.7l-5 3a1 1 0 0 1-1.5-.9v-5.1l-3.3 2.6a1 1 0 0 1-1.2-1.6L10 10 6.4 7.1a1 1 0 1 1 1.2-1.6L11 8.1V3a1 1 0 0 1 .3-.9ZM13 4.8v3.1l2.4-1.6Zm0 10.2 2.4-1.5L13 12Z',
  map: 'M8.7 2.2a1 1 0 0 1 .6 0l5.4 1.9 4.4-1.8a1.2 1.2 0 0 1 1.7 1.1v13.3c0 .5-.3 1-.8 1.1l-5 2a1 1 0 0 1-.7 0L9 17.9l-4.4 1.8a1.2 1.2 0 0 1-1.7-1.1V5.3c0-.5.3-1 .8-1.1Zm.3 2.3v11.3l6 2.1V6.6Z',
  bell: 'M12 1.8c-3.5 0-6.3 2.8-6.3 6.3v3.4l-1.5 2.8a1.4 1.4 0 0 0 1.2 2.1h13.2a1.4 1.4 0 0 0 1.2-2.1l-1.5-2.8V8.1c0-3.5-2.8-6.3-6.3-6.3Zm0 20.4a3 3 0 0 0 2.9-2.3H9.1a3 3 0 0 0 2.9 2.3Z',
  clock: 'M12 2.2a9.8 9.8 0 1 0 0 19.6 9.8 9.8 0 0 0 0-19.6Zm0 3.9a1 1 0 0 1 1 1v5.1l3.2 1.9a1 1 0 0 1-1 1.7l-3.7-2.2a1 1 0 0 1-.5-.9V7.1a1 1 0 0 1 1-1Z',
  infinity: 'M6.6 6.6a5.4 5.4 0 0 1 4 1.8l1.4 1.5 1.4-1.5a5.4 5.4 0 1 1 0 7.2L12 14.1l-1.4 1.5a5.4 5.4 0 1 1-4-9Zm0 2.4a3 3 0 1 0 0 6c.9 0 1.6-.4 2.2-1l1.6-2-1.6-2a3 3 0 0 0-2.2-1Zm10.8 0c-.9 0-1.6.4-2.2 1l-1.6 2 1.6 2c.6.6 1.3 1 2.2 1a3 3 0 0 0 0-6Z',
};

const STROKE_ONLY = new Set(Object.keys(glyphs));

/** One 24-grid icon. `title` omitted ⇒ decorative, hidden from assistive tech. */
export function icon(name, { className = '', title = '', size = 24 } = {}) {
  const raw = STROKE_ONLY.has(name) ? glyphs[name] : filled[name];
  if (!raw) throw new Error(`unknown icon: ${name}`);
  const d = typeof raw === 'string' ? raw : raw.d;
  const rule = typeof raw === 'string' ? '' : ` fill-rule="${raw.rule}"`;
  const paint = STROKE_ONLY.has(name)
    ? 'fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"'
    : `fill="currentColor"${rule}`;
  const a11y = title ? `role="img" aria-label="${title}"` : 'aria-hidden="true" focusable="false"';
  const cls = className ? ` class="${className}"` : '';
  return `<svg${cls} width="${size}" height="${size}" viewBox="0 0 24 24" ${paint} ${a11y}><path d="${d}"/></svg>`;
}

/**
 * The brand IMAGOTYPE: the mark *and its name*, as one drawing.
 *
 * This is what goes wherever the site says who it is — the header, the footer, both legal pages
 * and the social card. It replaces a pair: `logoMark()` plus a `<span class="brand__name">` with
 * the word set in Jakarta 800 beside it.
 *
 * ⛔ **The disc is gone, and so are both of its exceptions.** The site wore one
 * [WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001] and so did the Play banner
 * [PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001], and both rested on ONE measured fact: the mark at
 * `F_EMBLEM` came out ~46 px next to an 82 px wordmark and **floated**. The disc propped up a
 * lockup nobody had designed. The imagotype arrives with that lockup measured — 1:5.8 between
 * symbol and word, 14 u of channel — so the prop has nothing left to hold up and is deleted from
 * the repo [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001]. One rule, no exceptions to remember:
 * *mark alone → the isotype, no disc. Mark saying its name → the imagotype.*
 *
 * ⛔ **The word is eight OUTLINES, not text**, and that is the point of using it here rather than
 * keeping a `<span>`: the logo now looks the same before the webfont loads, inside the headless
 * Chrome that renders the Play banner, and inside the Pillow script that composes the social card
 * — none of which can be trusted to have Jakarta. It also means the name cannot drift
 * typographically from one surface to the next, which it already had in three ways.
 *
 * ⛔ Nothing here is drawn by hand: both groups' transforms, the viewBox and the two fills come
 * from `mark.generated.mjs`, written by `docs/branding/master/export_surfaces.py` from the
 * parametric master — the SAME `lockup_parts()` that composes the banner and the app's
 * VectorDrawable, so the three cannot drift.
 *
 * ⚠️ `width` is the WIDTH, not a square side: the lockup is 5.484:1 and the height follows from
 * the ratio. Passing a square size here is how a logo gets squashed, so there is no `size`.
 */
export function logoLockup({ width = 150, className = 'logo-lockup', title = 'Paparcar' } = {}) {
  const mark = MARK_PATHS.map((d) => `<path d="${d}"/>`).join('\n');
  const word = WORD_PATHS.map((d) => `<path d="${d}"/>`).join('\n');
  const a11y = title ? `role="img" aria-label="${title}"` : 'aria-hidden="true" focusable="false"';
  return `<svg class="${className}" width="${width}" height="${(width * LOCKUP_H) / LOCKUP_W}" viewBox="0 0 ${LOCKUP_W} ${LOCKUP_H}" ${a11y}>
<g transform="${LOCKUP_MARK_TRANSFORM}" fill="${LOCKUP_DARK.mark}">
${mark}
</g>
<g transform="${LOCKUP_WORD_TRANSFORM}" fill="${LOCKUP_DARK.word}">
${word}
</g>
</svg>`;
}

/** Google Play badge, drawn rather than shipped as an image so it stays crisp and themeable. */
export function playGlyph(size = 22) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
<path fill="#00D0FF" d="M3.6 2.2a1.6 1.6 0 0 0-.5 1.2v17.2c0 .5.2.9.5 1.2l9-9.8Z"/>
<path fill="#00F076" d="M3.6 2.2a1.5 1.5 0 0 1 1.6-.1l11.1 6.3-3.7 3.6Z"/>
<path fill="#FFD400" d="m16.3 8.4 3.5 2c1 .6 1 1.9 0 2.5l-3.5 2-3.7-3.6Z"/>
<path fill="#FF3A44" d="m12.6 12 3.7 3.6-11.1 6.3a1.5 1.5 0 0 1-1.6-.1Z"/>
</svg>`;
}

/** Apple mark for the "coming soon" store button — flat, single colour, never tinted brand. */
export function appleGlyph(size = 22) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
<path d="M16.4 12.7c0-2.3 1.9-3.4 2-3.5-1.1-1.6-2.8-1.8-3.4-1.9-1.4-.1-2.8.9-3.5.9-.7 0-1.9-.8-3.1-.8-1.6 0-3.1.9-3.9 2.4-1.7 2.9-.4 7.2 1.2 9.6.8 1.2 1.7 2.5 3 2.4 1.2 0 1.6-.8 3.1-.8 1.4 0 1.8.8 3.1.7 1.3 0 2.1-1.2 2.9-2.3.9-1.3 1.3-2.6 1.3-2.7 0 0-2.5-1-2.7-3.9ZM14.2 5.3c.6-.8 1.1-1.9 1-3-1 0-2.2.6-2.9 1.5-.6.7-1.2 1.9-1 3 1.1.1 2.2-.6 2.9-1.5Z"/>
</svg>`;
}
