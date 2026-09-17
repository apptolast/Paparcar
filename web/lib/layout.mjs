/** The document shell: head, header, footer. Every page goes through `page()`. */
import { esc, cx, button, storeButtons, icon } from './components.mjs';
import { logoMark, playGlyph, appleGlyph } from './icons.mjs';
import { ORIGIN, PLAY_URL, SUPPORT_EMAIL, LOCALES, SECTIONS, routes } from './config.mjs';
import { asset } from './assets.mjs';

const YEAR = 2026;

/**
 * Absolute URL for a path that already starts with `/`.
 *
 * The path is used VERBATIM, trailing slash included: a locale home is `/en/` and must stay `/en/`
 * in the canonical, in every `hreflang` and in the sitemap. Normalising it in one of the three and
 * not the others is how a canonical ends up pointing at a URL the sitemap does not list.
 */
const abs = (path) => `${ORIGIN}${path}`;

/** The same page in the other locale, used for `hreflang` and the language switch. */
function alternates(pageKey) {
  return LOCALES.map((l) => ({ code: l.code, url: abs(routes(l.base)[pageKey]) }));
}

function head({ t, pageKey, title, description, canonical, jsonLd, ogImage, noindex }) {
  // A page Hosting serves for every miss has no canonical URL and no translated twin: it must not
  // be indexed, and pointing `hreflang` at it would advertise it as a real page.
  if (noindex) {
    return `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="robots" content="noindex, follow">
<meta name="theme-color" content="#0D1117">
<meta name="color-scheme" content="dark">
<link rel="icon" type="image/svg+xml" href="/favicon.svg">
<link rel="preload" href="${asset('/assets/font/plus-jakarta-sans.woff2')}" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="${asset('/assets/css/site.css')}">`;
  }
  const alts = alternates(pageKey);
  const links = alts
    .map((a) => `<link rel="alternate" hreflang="${a.code}" href="${a.url}">`)
    .join('\n');
  const structured = (jsonLd ?? [])
    .map((data) => `<script type="application/ld+json">${JSON.stringify(data)}</script>`)
    .join('\n');
  return `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}">
<link rel="canonical" href="${canonical}">
${links}
<link rel="alternate" hreflang="x-default" href="${alts[0].url}">
<meta name="theme-color" content="#0D1117">
<meta name="color-scheme" content="dark">
<link rel="icon" type="image/svg+xml" href="/favicon.svg">
<link rel="apple-touch-icon" href="${asset('/assets/img/apple-touch-icon.png')}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="Paparcar">
<meta property="og:locale" content="${t.locale}">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${canonical}">
<meta property="og:image" content="${ORIGIN}${asset(ogImage)}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="${esc(t.site.ogAlt)}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${esc(title)}">
<meta name="twitter:description" content="${esc(description)}">
<meta name="twitter:image" content="${ORIGIN}${asset(ogImage)}">
<link rel="preload" href="${asset('/assets/font/plus-jakarta-sans.woff2')}" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="${asset('/assets/css/site.css')}">
${structured}`;
}

/**
 * The language control shows BOTH languages with the current one marked, never just one.
 *
 * A single button reading "English" is ambiguous by construction: it can equally be read as "you
 * are reading English" or "switch to English", and the reader cannot tell which without clicking.
 * Two tags with the active one written — not merely implied — answers both questions at once, and
 * it is the same principle the app's colour doctrine states for state: it gets WRITTEN, not guessed.
 */
function languageSwitch(t, pageKey, current, { compact = false } = {}) {
  const urls = Object.fromEntries(alternates(pageKey).map((a) => [a.code, a.url.replace(ORIGIN, '') || '/']));

  const items = LOCALES.map((locale) => {
    const active = locale.code === current;
    // The active tag is not a link: it goes nowhere, and making it one gives the keyboard a stop
    // that does nothing.
    return active
      ? `<span class="langs__item langs__item--on" aria-current="true" lang="${locale.code}">
          ${esc(locale.short)}<span class="sr-only"> · ${esc(locale.label)}</span>
        </span>`
      : `<a class="langs__item" href="${urls[locale.code]}" hreflang="${locale.code}" lang="${locale.code}"
            aria-label="${esc(locale.label)}">${esc(locale.short)}</a>`;
  }).join('');

  return `<div class="${cx('langs', compact && 'langs--compact')}" role="group" aria-label="${esc(t.footer.langLabel)}">
    ${icon('language', { size: 16, className: 'langs__globe' })}${items}
  </div>`;
}

function header(t, pageKey, lang, base) {
  const home = routes(base).home;
  const navItems = [
    { key: 'how', label: t.nav.how },
    { key: 'features', label: t.nav.features },
    { key: 'plus', label: t.nav.plus },
    { key: 'faq', label: t.nav.faq },
  ];
  // On a legal or support page the in-page anchors do not exist, so they point back at the home
  // page's anchors instead of at nothing.
  const prefix = pageKey === 'home' ? '' : home;
  const links = navItems
    .map((item) => `<li><a class="nav__link" href="${prefix}#${SECTIONS[item.key]}">${esc(item.label)}</a></li>`)
    .join('');

  return `<header class="site-header" data-header>
  <div class="shell site-header__inner">
    <a class="brand" href="${home}" aria-label="Paparcar">
      ${logoMark({ size: 36 })}
      <span class="brand__name">Paparcar</span>
    </a>

    <nav class="nav" aria-label="${esc(t.nav.home)}">
      <ul class="nav__list">${links}</ul>
    </nav>

    <div class="site-header__actions">
      ${languageSwitch(t, pageKey, lang)}
      ${button({ href: PLAY_URL, label: t.nav.cta, variant: 'primary', external: true, className: 'btn--sm site-header__cta' })}
      <button class="burger" type="button" data-menu-toggle aria-expanded="false" aria-controls="site-menu"
              aria-label="${esc(t.nav.menuOpen)}" data-label-open="${esc(t.nav.menuOpen)}" data-label-close="${esc(t.nav.menuClose)}">
        <span class="burger__bars" aria-hidden="true"><i></i><i></i><i></i></span>
      </button>
    </div>
  </div>

  <div class="menu" id="site-menu" data-menu hidden>
    <nav class="shell menu__inner" aria-label="${esc(t.nav.home)}">
      <ul class="menu__list">${navItems
        .map((item) => `<li><a class="menu__link" href="${prefix}#${SECTIONS[item.key]}">${esc(item.label)}</a></li>`)
        .join('')}</ul>
      <div class="menu__actions">
        ${button({ href: PLAY_URL, label: t.nav.cta, variant: 'primary', external: true })}
        ${languageSwitch(t, pageKey, lang, { compact: true })}
      </div>
    </nav>
  </div>
</header>`;
}

function footer(t, lang, base, pageKey) {
  const r = routes(base);
  // A `#section` link in the footer means "that section of the home page", which is only a
  // same-page jump when you are already on it.
  const resolve = (href) => (href.startsWith('#') ? (pageKey === 'home' ? href : `${r.home}${href}`) : href);
  const columns = t.footer.columns
    .map(
      (col) => `<div class="footer__col">
      <h2 class="footer__title">${esc(col.title)}</h2>
      <ul class="footer__list">
        ${col.links
          .map(
            (l) =>
              `<li><a class="${cx('footer__link', l.quiet && 'footer__link--quiet')}" href="${esc(
                resolve(l.href),
              )}">${esc(l.label)}</a></li>`,
          )
          .join('')}
      </ul>
    </div>`,
    )
    .join('');

  return `<footer class="site-footer">
  <div class="shell">
    <div class="footer__grid">
      <div class="footer__brand">
        <a class="brand" href="${r.home}" aria-label="Paparcar">
          ${logoMark({ size: 40 })}
          <span class="brand__name">Paparcar</span>
        </a>
        <p class="footer__tagline">${esc(t.footer.tagline)}</p>
      </div>
      ${columns}
      <div class="footer__col footer__col--store">
        <h2 class="footer__title">${esc(t.footer.downloadTitle)}</h2>
        <a class="store-btn store-btn--sm" href="${PLAY_URL}" target="_blank" rel="noopener">
          ${playGlyph(20)}
          <span class="store-btn__text"><small>${esc(t.store.google)}</small><strong>${esc(t.store.googleName)}</strong></span>
        </a>
        <p class="footer__soon">${appleGlyph(14)}<span>${esc(t.footer.storeSoon)}</span></p>
      </div>
    </div>
    <div class="footer__bottom">
      <p class="footer__rights">© ${YEAR} ${esc(t.footer.rights)}</p>
      <div class="footer__bottom-links">
        <a class="footer__link" href="mailto:${SUPPORT_EMAIL}">${SUPPORT_EMAIL}</a>
        ${languageSwitch(t, 'home', lang, { compact: true })}
      </div>
    </div>
  </div>
</footer>`;
}

/** Assemble a full document. */
export function page({ t, lang, base, pageKey, title, description, body, jsonLd = [], ogImage = '/assets/img/og-card.jpg', bodyClass = '', noindex = false }) {
  const canonical = abs(routes(base)[pageKey]);
  return `<!doctype html>
<html lang="${t.lang}" dir="${t.dir}">
<head>
${head({ t, pageKey, title, description, canonical, jsonLd, ogImage, noindex })}
</head>
<body class="${cx('page', bodyClass)}">
<a class="skip-link" href="#main">${esc(t.site.skipToContent)}</a>
${header(t, pageKey, lang, base)}
<main id="main">
${body}
</main>
${footer(t, lang, base, pageKey)}
<script src="${asset('/assets/js/site.js')}" defer></script>
</body>
</html>`;
}

export { abs, storeButtons };
