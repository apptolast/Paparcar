/** The landing page. One section per question a first-time visitor asks. */
import {
  esc, cx, reveal, sectionHead, button, storeButtons, phone, art, featureCard, tierColumn, faqItem, icon,
} from '../lib/components.mjs';
import { asset } from '../lib/assets.mjs';
import { page } from '../lib/layout.mjs';
import { PLAY_URL, playUrl, SECTIONS, routes, ORIGIN } from '../lib/config.mjs';

// ── the map panel ─────────────────────────────────────────────────────────────
// Drawn, not screenshotted: a screenshot of the map would be a picture of Google's tiles, and what
// this section is about is the four LAYERS Paparcar puts on top of them. Colours are the app's
// own — spot green, car-blue for a watched vehicle, brand green for the tier-3 markers.

/** A freed street spot: a hollow pin (the gap) with the dot of whoever just left it. Never a "P" —
 *  the "P" belongs to the car park, which is somebody's installation. [COPY-SPOT-IS-NOT-A-PARKING-001] */
const SPOT_GLYPH =
  '<path class="mapmark__glyph" fill-rule="evenodd" transform="translate(-10 -11)" d="M10 1.6a6.4 6.4 0 0 0-6.4 6.4c0 4.4 5.3 10.2 5.8 10.7a.9.9 0 0 0 1.2 0c.5-.5 5.8-6.3 5.8-10.7A6.4 6.4 0 0 0 10 1.6ZM10 3.4a4.6 4.6 0 0 0-4.6 4.6c0 3 3.2 7.2 4.6 8.8 1.4-1.6 4.6-5.8 4.6-8.8A4.6 4.6 0 0 0 10 3.4ZM10 5.7a2.3 2.3 0 1 1 0 4.6 2.3 2.3 0 0 1 0-4.6Z"/>';

function mapPanel(t) {
  const marker = (x, y, kind, glyph, delay) => `
    <g class="mapmark mapmark--${kind}" style="--mark-delay:${delay}ms" transform="translate(${x} ${y})">
      <circle class="mapmark__halo" r="26"/>
      <circle class="mapmark__disc" r="17"/>
      ${glyph}
    </g>`;

  return `<div class="mapcanvas" role="img" aria-label="${esc(t.map.title)}">
    <svg viewBox="0 0 640 420" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      <defs>
        <clipPath id="mapclip"><rect width="640" height="420" rx="0"/></clipPath>
      </defs>
      <g clip-path="url(#mapclip)">
        <rect width="640" height="420" fill="#16203A"/>
        <g class="mapcanvas__blocks" fill="#1B2745">
          <rect x="-20" y="-20" width="200" height="150" transform="rotate(-18 80 55)"/>
          <rect x="250" y="40" width="230" height="130" transform="rotate(-18 365 105)"/>
          <rect x="60" y="220" width="210" height="170" transform="rotate(-18 165 305)"/>
          <rect x="400" y="250" width="260" height="190" transform="rotate(-18 530 345)"/>
        </g>
        <g class="mapcanvas__streets" stroke="#26365C" fill="none" stroke-linecap="round">
          <path stroke-width="22" d="M-40 150 L700 -90"/>
          <path stroke-width="16" d="M-40 330 L700 90"/>
          <path stroke-width="26" d="M120 -40 L330 470"/>
          <path stroke-width="14" d="M420 -40 L620 470"/>
          <path stroke-width="10" d="M-40 460 L700 220"/>
        </g>
        <g class="mapcanvas__park" fill="#1E3B33">
          <path d="M470 20 L560 -10 L600 60 L510 92 Z"/>
        </g>
      </g>
    </svg>

    <svg class="mapcanvas__marks" viewBox="0 0 640 420" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      ${marker(178, 148, 'car', '<path class="mapmark__glyph" transform="translate(-11 -11) scale(0.92)" d="M4.6 6.4A2 2 0 0 1 6.5 5h11a2 2 0 0 1 1.9 1.4l1.2 3.5c.6.3 1 .9 1 1.6v4.6c0 .6-.5 1.1-1.1 1.1h-.5v.9a1.2 1.2 0 0 1-2.4 0v-.9H6.4v.9a1.2 1.2 0 0 1-2.4 0v-.9h-.5c-.6 0-1.1-.5-1.1-1.1v-4.6c0-.7.4-1.3 1-1.6Zm1.8.7-.9 2.6h13l-.9-2.6a.5.5 0 0 0-.5-.4H6.9a.5.5 0 0 0-.5.4Zm-.8 4.8a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2Zm9 0a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2Z"/>', 0)}
      ${marker(392, 108, 'spot', SPOT_GLYPH, 240)}
      ${marker(262, 292, 'spot', SPOT_GLYPH, 480)}
      ${marker(486, 214, 'parking', '<path class="mapmark__glyph" transform="translate(-10 -10)" d="M6.2 3.4h4.6a3.05 3.05 0 0 1 0 6.1H8.2v4.9a1 1 0 0 1-2 0V4.4c0-.55.45-1 1-1Zm2 2v2.1h2.6a1.05 1.05 0 0 0 0-2.1Z"/>', 620)}
      ${marker(152, 330, 'fuel', '<path class="mapmark__glyph" transform="translate(-9 -11) scale(0.85)" d="M4.8 3A1.8 1.8 0 0 1 6.6 1.2h5.8A1.8 1.8 0 0 1 14.2 3v17.2h1.3a.9.9 0 0 1 0 1.8H3.5a.9.9 0 0 1 0-1.8h1.3Zm2.2 1.6v4.2h5V4.6Z"/>', 860)}
    </svg>

    <div class="mapcanvas__legend">
      ${t.map.layers
        .map(
          (layer) => `<span class="legend legend--${layer.key}"><i aria-hidden="true"></i>${esc(layer.title)}</span>`,
        )
        .join('')}
    </div>
  </div>`;
}

// ── the automation journey ────────────────────────────────────────────────────

function journey(t) {
  const n = t.automation.steps.length;
  // The four moments in order: you drive · you park · it is saved · the spot you leave goes on.
  const icons = ['car', 'pin', 'check', 'spot'];
  return `<div class="journey" ${reveal()}>
    <svg class="journey__line" viewBox="0 0 1000 12" preserveAspectRatio="none" aria-hidden="true" focusable="false">
      <path class="journey__track" d="M6 6H994"/>
      <path class="journey__dash" d="M6 6H994"/>
    </svg>
    <ol class="journey__steps">
      ${t.automation.steps
        .map(
          (step, i) => `<li class="journey__step" style="--step-delay:${i * 140}ms">
        <span class="journey__dot">${icon(icons[i % icons.length], { size: 20 })}</span>
        <h3 class="journey__title">${esc(step.title)}</h3>
        <p class="journey__body">${esc(step.body)}</p>
      </li>`,
        )
        .join('')}
    </ol>
    <p class="sr-only">${esc(t.automation.steps.map((s) => `${s.title}: ${s.body}`).join('. '))} (${n})</p>
  </div>`;
}

// ── sections ──────────────────────────────────────────────────────────────────

function hero(t, lang) {
  return `<section class="hero" id="top">
  <div class="hero__glow" aria-hidden="true"></div>
  <div class="shell hero__inner">
    <div class="hero__copy">
      <p class="eyebrow" ${reveal()}>${esc(t.hero.eyebrow)}</p>
      <h1 class="hero__title" ${reveal(60)}>
        <span class="hero__title-line">${esc(t.hero.title[0])}</span>
        <span class="hero__title-line hero__title-line--accent">${esc(t.hero.title[1])}</span>
      </h1>
      <p class="hero__lead" ${reveal(120)}>${esc(t.hero.lead)}</p>
      <div class="hero__actions" ${reveal(180)}>
        ${button({ href: playUrl('home', 'hero'), label: t.hero.cta, external: true, icon: 'arrowRight' })}
        ${button({ href: `#${SECTIONS.how}`, label: t.hero.ctaSecondary, variant: 'ghost' })}
      </div>
      <p class="hero__meta" ${reveal(220)}>${esc(t.hero.meta)}</p>
    </div>

    <div class="hero__visual" ${reveal(120)}>
      <div class="hero__phones">
        ${phone({ shot: 2, lang, alt: t.hero.shotAltSecondary, size: 'md', className: 'hero__phone hero__phone--back' })}
        ${phone({ shot: 1, lang, alt: t.hero.shotAlt, size: 'lg', priority: true, className: 'hero__phone hero__phone--front' })}
      </div>
      <!-- The phone already shows the address and the elapsed time; a floating card repeating them
           reads as a rendering duplicate. This badge says the one thing the screenshot cannot:
           that nobody touched the phone for it to get there. -->
      <p class="hero__badge">
        <span class="hero__badge-dot" aria-hidden="true"></span>
        ${esc(t.hero.badge.eyebrow)}
      </p>
    </div>
  </div>
</section>`;
}

function proof(t) {
  return `<section class="proof" aria-label="${esc(t.hero.eyebrow)}">
  <div class="shell proof__grid">
    ${t.proof
      .map(
        (p, i) => `<div class="proof__item" ${reveal(i * 60)}>
      <p class="proof__value">${esc(p.value)}</p>
      <p class="proof__label">${esc(p.label)}</p>
    </div>`,
      )
      .join('')}
  </div>
</section>`;
}

function how(t, lang, artSvg) {
  return `<section class="section section--how" id="${SECTIONS.how}">
  <div class="shell">
    ${sectionHead({ eyebrow: t.how.eyebrow, title: t.how.title, lead: t.how.lead })}
    <ol class="steps">
      ${t.how.steps
        .map(
          (step, i) => `<li class="step" ${reveal(i * 90)}>
        <div class="step__art">${art(artSvg[step.art], 'art art--step')}</div>
        <p class="step__n">${esc(step.n)}</p>
        <h3 class="step__title">${esc(step.title)}</h3>
        <p class="step__body">${esc(step.body)}</p>
      </li>`,
        )
        .join('')}
    </ol>
    <p class="keyidea" ${reveal()}>${esc(t.how.keyIdea)}</p>
  </div>
</section>`;
}

function features(t) {
  return `<section class="section section--alt" id="${SECTIONS.features}">
  <div class="shell">
    ${sectionHead({ eyebrow: t.features.eyebrow, title: t.features.title, lead: t.features.lead })}
    <div class="cards">
      ${t.features.cards.map((card, i) => featureCard(card, (i % 3) * 70)).join('')}
    </div>
  </div>
</section>`;
}

function map(t, lang) {
  return `<section class="section section--map" id="${SECTIONS.map}">
  <div class="shell">
    ${sectionHead({ eyebrow: t.map.eyebrow, title: t.map.title, lead: t.map.lead })}
    <div class="map__layout">
      <div class="map__canvas-wrap" ${reveal()}>${mapPanel(t)}</div>
      <ul class="map__layers">
        ${t.map.layers
          .map(
            (layer, i) => `<li class="map__layer map__layer--${layer.key}" ${reveal(i * 70)}>
          <span class="map__layer-dot" aria-hidden="true"></span>
          <div>
            <h3 class="map__layer-title">${esc(layer.title)}</h3>
            <p class="map__layer-body">${esc(layer.body)}</p>
          </div>
        </li>`,
          )
          .join('')}
      </ul>
    </div>
    <div class="map__shots">
      ${t.map.shots
        .map((s, i) => `<div ${reveal(i * 80)}>${phone({ shot: s.n, lang, alt: s.alt })}</div>`)
        .join('')}
    </div>
  </div>
</section>`;
}

function automation(t) {
  return `<section class="section section--automation" id="${SECTIONS.automation}">
  <div class="shell">
    ${sectionHead({ eyebrow: t.automation.eyebrow, title: t.automation.title, lead: t.automation.lead, align: 'center' })}
    ${journey(t)}
    <aside class="note" ${reveal()}>
      <span class="icon-tile icon-tile--quiet">${icon('shield', { size: 22 })}</span>
      <div>
        <h3 class="note__title">${esc(t.automation.note.title)}</h3>
        <p class="note__body">${esc(t.automation.note.body)}</p>
      </div>
    </aside>
  </div>
</section>`;
}

function plus(t) {
  return `<section class="section section--plus" id="${SECTIONS.plus}">
  <div class="shell">
    ${sectionHead({ eyebrow: t.plus.eyebrow, title: t.plus.title, lead: t.plus.lead, align: 'center' })}
    <div class="tiers">
      ${t.plus.columns.map((col) => tierColumn(col, col.name)).join('')}
    </div>
    <div class="plus__foot" ${reveal()}>
      <p class="plus__note">${esc(t.plus.footnote)}</p>
      ${button({ href: playUrl('home', 'plus'), label: t.plus.cta, external: true, icon: 'arrowRight' })}
    </div>
  </div>
</section>`;
}

function trust(t, base, artSvg) {
  const r = routes(base);
  return `<section class="section section--alt" id="${SECTIONS.privacy}">
  <div class="shell trust">
    <div class="trust__intro">
      ${sectionHead({ eyebrow: t.trust.eyebrow, title: t.trust.title, lead: t.trust.lead })}
      <div class="trust__art" ${reveal(80)}>${art(artSvg['art-privacy'], 'art art--trust')}</div>
      <div class="trust__links" ${reveal(120)}>
        <a class="link" href="${r.privacy}">${esc(t.trust.links.privacy)}${icon('chevronRight', { size: 16 })}</a>
        <a class="link link--quiet" href="${r.delete}">${esc(t.trust.links.delete)}</a>
      </div>
    </div>
    <ul class="trust__list">
      ${t.trust.points
        .map(
          (point, i) => `<li class="trust__item" ${reveal(i * 60)}>
        <span class="icon-tile icon-tile--quiet">${icon(point.icon, { size: 20 })}</span>
        <div>
          <h3 class="trust__title">${esc(point.title)}</h3>
          <p class="trust__body">${esc(point.body)}</p>
        </div>
      </li>`,
        )
        .join('')}
    </ul>
  </div>
</section>`;
}

function faq(t) {
  return `<section class="section" id="${SECTIONS.faq}">
  <div class="shell shell--narrow">
    ${sectionHead({ eyebrow: t.faq.eyebrow, title: t.faq.title, align: 'center' })}
    <div class="faq">${t.faq.items.map(faqItem).join('')}</div>
  </div>
</section>`;
}

function finalCta(t, lang) {
  return `<section class="cta">
  <div class="cta__glow" aria-hidden="true"></div>
  <div class="shell cta__inner">
    <div class="cta__copy">
      <h2 class="cta__title" ${reveal()}>
        <span>${esc(t.finalCta.title[0])}</span>
        <span class="cta__title--accent">${esc(t.finalCta.title[1])}</span>
      </h2>
      <p class="cta__lead" ${reveal(80)}>${esc(t.finalCta.lead)}</p>
      <div ${reveal(140)}>${storeButtons(t, { href: playUrl('home', 'store') })}</div>
      <p class="cta__meta" ${reveal(180)}>${esc(t.finalCta.meta)}</p>
    </div>
    <div class="cta__visual" ${reveal(120)}>
      ${phone({ shot: 1, lang, alt: t.finalCta.shotAlt, size: 'md' })}
    </div>
  </div>
</section>`;
}

// ── structured data ───────────────────────────────────────────────────────────

function jsonLd(t, base) {
  const r = routes(base);
  return [
    {
      '@context': 'https://schema.org',
      '@type': 'SoftwareApplication',
      name: 'Paparcar',
      applicationCategory: 'TravelApplication',
      operatingSystem: 'Android 8.0+',
      url: `${ORIGIN}${r.home}`,
      description: t.site.description,
      // ⛔ El ÚNICO enlace a Play del sitio sin `referrer`, y a propósito: esto no es un botón,
      // es el CANÓNICO que leen los buscadores. Etiquetarlo mete UTMs en un resultado de búsqueda
      // y en cualquier sitio que reutilice el dato. El guard lo exime por NOMBRE, nunca por fichero.
      // [MKT-A-SITE-CTA-MUST-SAY-WHERE-ITS-INSTALL-CAME-FROM-001]
      installUrl: PLAY_URL,
      image: `${ORIGIN}${asset('/assets/img/og-card.jpg')}`,
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'EUR' },
      inLanguage: t.lang,
    },
    {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: t.faq.items.map((item) => ({
        '@type': 'Question',
        name: item.q,
        acceptedAnswer: { '@type': 'Answer', text: item.a },
      })),
    },
  ];
}

export default function home({ t, lang, base, artSvg }) {
  const body = [
    hero(t, lang),
    proof(t),
    how(t, lang, artSvg),
    features(t),
    map(t, lang),
    automation(t),
    plus(t),
    trust(t, base, artSvg),
    faq(t),
    finalCta(t, lang),
  ].join('\n');

  return page({
    t,
    lang,
    base,
    pageKey: 'home',
    title: t.site.title,
    description: t.site.description,
    jsonLd: jsonLd(t, base),
    body,
    bodyClass: 'page--home',
  });
}
