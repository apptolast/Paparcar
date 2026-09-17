/**
 * The site's component library. Every page is assembled from these; no page writes raw markup for
 * something another page also draws.
 */
import { icon, playGlyph, appleGlyph } from './icons.mjs';
import { PLAY_URL, SUPPORT_EMAIL } from './config.mjs';
import { asset } from './assets.mjs';

/** Escape for text nodes and attribute values. Content is authored in JSON, so nothing is trusted. */
export const esc = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** Join class names, dropping falsy ones. */
export const cx = (...parts) => parts.filter(Boolean).join(' ');

/** `reveal` marks a block for the scroll-in animation; without JS or with reduced motion it is
 *  simply visible, so it is never load-bearing. */
export const reveal = (delay = 0) => `data-reveal${delay ? ` style="--reveal-delay:${delay}ms"` : ''}`;

// ── type ──────────────────────────────────────────────────────────────────────

export const eyebrow = (text) => `<p class="eyebrow">${esc(text)}</p>`;

/** Section header: eyebrow + h2 + optional lead. `id` anchors the nav. */
export function sectionHead({ eyebrow: brow, title, lead, align = 'start' }) {
  return `<header class="section-head section-head--${align}" ${reveal()}>
    ${brow ? eyebrow(brow) : ''}
    <h2 class="section-title">${esc(title)}</h2>
    ${lead ? `<p class="section-lead">${esc(lead)}</p>` : ''}
  </header>`;
}

// ── actions ───────────────────────────────────────────────────────────────────

/**
 * The one canonical CTA. `variant` picks the weight, never a different shape — the app has a single
 * button silhouette and so does the site. [UI-BUTTON-ONE-CANONICAL-CTA-001]
 */
export function button({ href, label, variant = 'primary', icon: iconName, external = false, className = '' }) {
  const rel = external ? ' rel="noopener"' : '';
  const target = external ? ' target="_blank"' : '';
  return `<a class="${cx('btn', `btn--${variant}`, className)}" href="${esc(href)}"${target}${rel}>
    ${iconName ? icon(iconName, { size: 20, className: 'btn__icon' }) : ''}<span>${esc(label)}</span>
  </a>`;
}

/** Google Play button. The App Store twin is rendered disabled until there is a real listing:
 *  an inert button that says "coming soon" is honest; a live-looking one that 404s is not. */
export function storeButtons(t, { appleSoon = true } = {}) {
  const apple = appleSoon
    ? `<span class="store-btn store-btn--soon" aria-disabled="true">
        ${appleGlyph()}
        <span class="store-btn__text"><small>${esc(t.store.appleSoon)}</small><strong>${esc(t.store.appleName)}</strong></span>
      </span>`
    : '';
  return `<div class="store-row">
    <a class="store-btn" href="${PLAY_URL}" target="_blank" rel="noopener">
      ${playGlyph()}
      <span class="store-btn__text"><small>${esc(t.store.google)}</small><strong>${esc(t.store.googleName)}</strong></span>
    </a>
    ${apple}
  </div>`;
}

// ── product imagery ───────────────────────────────────────────────────────────

/** Intrinsic size of the widest variant, so the box is reserved before the image arrives. */
const SHOT_W = 1080;
const SHOT_H = 2280;

/**
 * A screenshot inside a phone shell. The capture is the locale's OWN one: a Spanish page showing an
 * English phone is the mistake [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001] was opened to fix.
 */
export function phone({ shot, lang, alt, size = 'md', priority = false, className = '' }) {
  const src = (w) => asset(`/assets/img/screen-${shot}-${lang}-${w}.webp`);
  const loading = priority ? '' : ' loading="lazy" decoding="async"';
  // `sizes` is what the browser multiplies by the device pixel ratio to pick a candidate. These
  // numbers track `--phone-w` in the stylesheet: a 300 CSS px phone on a 3x screen asks for ~900,
  // which is why the 1080 candidate has to exist — see the note on WIDTHS in prepare-assets.py.
  const sizes = size === 'lg' ? '(max-width: 720px) 74vw, 300px' : '(max-width: 720px) 60vw, 250px';
  return `<div class="${cx('phone', `phone--${size}`, className)}">
    <div class="phone__shell">
      <img class="phone__screen" src="${src(640)}"
           srcset="${src(320)} 320w, ${src(640)} 640w, ${src(1080)} 1080w"
           sizes="${sizes}" width="${SHOT_W}" height="${SHOT_H}" alt="${esc(alt)}"${loading}${priority ? ' fetchpriority="high"' : ''}>
    </div>
  </div>`;
}

/** One of the app's own illustrations (converted VectorDrawable), inlined by the build. */
export function art(svg, className = 'art') {
  return `<div class="${className}" aria-hidden="true">${svg}</div>`;
}

// ── surfaces ──────────────────────────────────────────────────────────────────

/** Icon tile + title + body, the web echo of `PapListItem` inside `PapOutlinedCard`. */
export function featureCard({ icon: iconName, title, body }, delay = 0) {
  return `<article class="card" ${reveal(delay)}>
    <span class="icon-tile">${icon(iconName, { size: 22 })}</span>
    <h3 class="card__title">${esc(title)}</h3>
    <p class="card__body">${esc(body)}</p>
  </article>`;
}

/** A row in the free/Plus comparison. */
export function tierColumn(col, checkLabel) {
  return `<article class="${cx('tier', col.featured && 'tier--featured')}" ${reveal(col.featured ? 80 : 0)}>
    <header class="tier__head">
      <h3 class="tier__name">${esc(col.name)}</h3>
      <p class="tier__tagline">${esc(col.tagline)}</p>
    </header>
    <ul class="tier__list">
      ${col.items
        .map(
          (item) => `<li class="tier__item">
        <span class="tier__check" aria-hidden="true">${icon('check', { size: 16 })}</span>
        <span>${esc(item)}</span>
      </li>`,
        )
        .join('')}
    </ul>
    <span class="sr-only">${esc(checkLabel)}</span>
  </article>`;
}

/** Native disclosure — keyboard and screen-reader behaviour for free, no JS needed. */
export function faqItem({ q, a }, index) {
  return `<details class="faq__item" ${reveal(Math.min(index, 4) * 40)}>
    <summary class="faq__q">
      <span>${esc(q)}</span>
      <span class="faq__marker" aria-hidden="true">${icon('chevronDown', { size: 20 })}</span>
    </summary>
    <div class="faq__a"><p>${esc(a)}</p></div>
  </details>`;
}

export { icon, SUPPORT_EMAIL };
