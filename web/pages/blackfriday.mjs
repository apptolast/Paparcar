/**
 * The Black Friday "Founders" campaign landing page — 3 months of Plus, free, for the first
 * 2,000 NEW accounts. Every figure here is a token resolved from `lib/config.mjs`
 * (`FOUNDERS_PROMO`), never a numeral typed by hand — see the note on that constant for where the
 * cap, the duration and the deadline are enforced server-side.
 *
 * No illustration: the product's own Founders-gift artwork (`FoundersGiftIllustration` in
 * `ui/illustrations/PaparcarIllustrations.kt`) is drawn on a Compose `Canvas`, not a
 * VectorDrawable, so it cannot go through the same `illus_* → SVG` pipeline the app's other
 * illustrations use (`tools/prepare-assets.py`). Rebuilding that 3D geometry by hand for the web
 * would be a second, independent drawing of it — exactly the kind of copy the brand rules forbid.
 * This page is typographic instead, built from the same sections (`hero`, `proof`, `steps`,
 * `tier`, `faq`, `cta`) every other page already uses.
 */
import {
  esc, reveal, sectionHead, button, icon, faqItem, tierColumn,
} from '../lib/components.mjs';
import { page } from '../lib/layout.mjs';
import { playUrl } from '../lib/config.mjs';

function hero(t) {
  const h = t.blackfriday.hero;
  return `<section class="hero hero--centered" id="top">
  <div class="hero__glow" aria-hidden="true"></div>
  <div class="shell hero__inner">
    <div class="hero__copy">
      <p class="eyebrow" ${reveal()}>${esc(h.eyebrow)}</p>
      <h1 class="hero__title" ${reveal(60)}>
        <span class="hero__title-line">${esc(h.title[0])}</span>
        <span class="hero__title-line hero__title-line--accent">${esc(h.title[1])}</span>
      </h1>
      <p class="hero__lead" ${reveal(120)}>${esc(h.lead)}</p>
      <div class="hero__actions" ${reveal(180)}>
        ${button({ href: playUrl('blackfriday', 'hero'), label: h.cta, external: true, icon: 'arrowRight' })}
        ${button({ href: '#how', label: h.ctaSecondary, variant: 'ghost' })}
      </div>
      <p class="hero__meta" ${reveal(220)}>${esc(h.meta)}</p>
    </div>
  </div>
</section>`;
}

function proof(t) {
  return `<section class="proof" aria-label="${esc(t.blackfriday.hero.eyebrow)}">
  <div class="shell proof__grid proof__grid--3">
    ${t.blackfriday.proof
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

function how(t) {
  const h = t.blackfriday.how;
  return `<section class="section" id="how">
  <div class="shell">
    ${sectionHead({ eyebrow: h.eyebrow, title: h.title, lead: h.lead })}
    <ol class="steps">
      ${h.steps
        .map(
          (step, i) => `<li class="step" ${reveal(i * 90)}>
        <p class="step__n">${esc(step.n)}</p>
        <h3 class="step__title">${esc(step.title)}</h3>
        <p class="step__body">${esc(step.body)}</p>
      </li>`,
        )
        .join('')}
    </ol>
  </div>
</section>`;
}

function includes(t) {
  const inc = t.blackfriday.includes;
  return `<section class="section section--alt" id="includes">
  <div class="shell">
    ${sectionHead({ eyebrow: inc.eyebrow, title: inc.title, lead: inc.lead, align: 'center' })}
    <div class="tiers tiers--single">
      ${tierColumn({ ...inc.tier, featured: true }, inc.tier.name)}
    </div>
  </div>
</section>`;
}

function faq(t) {
  const f = t.blackfriday.faq;
  return `<section class="section" id="faq">
  <div class="shell shell--narrow">
    ${sectionHead({ eyebrow: f.eyebrow, title: f.title, align: 'center' })}
    <div class="faq">${f.items.map(faqItem).join('')}</div>
    <p class="fineprint" ${reveal()}>${esc(t.blackfriday.legal)}</p>
  </div>
</section>`;
}

function finalCta(t) {
  const c = t.blackfriday.finalCta;
  return `<section class="cta">
  <div class="cta__glow" aria-hidden="true"></div>
  <div class="shell cta__inner cta__inner--centered">
    <div class="cta__copy">
      <h2 class="cta__title" ${reveal()}>
        <span>${esc(c.title[0])}</span>
        <span class="cta__title--accent">${esc(c.title[1])}</span>
      </h2>
      <p class="cta__lead" ${reveal(80)}>${esc(c.lead)}</p>
      <div class="hero__actions hero__actions--center" ${reveal(140)}>
        ${button({ href: playUrl('blackfriday', 'close'), label: c.cta, external: true, icon: 'arrowRight' })}
      </div>
      <p class="cta__meta" ${reveal(180)}>${esc(c.meta)}</p>
    </div>
  </div>
</section>`;
}

export default function blackfriday({ t, lang, base }) {
  const b = t.blackfriday;
  const body = [hero(t), proof(t), how(t), includes(t), faq(t), finalCta(t)].join('\n');

  return page({
    t,
    lang,
    base,
    pageKey: 'blackfriday',
    title: b.title,
    description: b.description,
    body,
    bodyClass: 'page--blackfriday',
  });
}
