/** Support centre — the page the footer and the store listing point at. */
import { esc, reveal, button, icon } from '../lib/components.mjs';
import { page } from '../lib/layout.mjs';
import { SUPPORT_EMAIL, routes, ORIGIN } from '../lib/config.mjs';

export default function support({ t, lang, base }) {
  const r = routes(base);
  const subject = encodeURIComponent(t.lang === 'es' ? 'Soporte Paparcar' : 'Paparcar support');

  const body = `<article class="doc">
  <div class="shell shell--narrow">
    <header class="doc__head" ${reveal()}>
      <p class="eyebrow">${esc(t.legalNav.support)}</p>
      <h1 class="doc__title">${esc(t.support.h1)}</h1>
      <p class="doc__lead">${esc(t.support.lead)}</p>
      <div class="doc__actions">
        ${button({ href: `mailto:${SUPPORT_EMAIL}?subject=${subject}`, label: t.support.cta, icon: 'arrowRight' })}
      </div>
    </header>

    <div class="doc__blocks">
      ${t.support.blocks
        .map(
          (block, i) => `<section class="doc__block" ${reveal(i * 60)}>
        <h2 class="doc__h2">${esc(block.title)}</h2>
        <p>${esc(block.body)}</p>
      </section>`,
        )
        .join('')}
    </div>

    <p class="doc__foot" ${reveal()}>
      <a class="link link--quiet" href="${r.delete}">${esc(t.support.deleteLink)}${icon('chevronRight', { size: 16 })}</a>
    </p>
  </div>
</article>`;

  return page({
    t,
    lang,
    base,
    pageKey: 'support',
    title: t.support.title,
    description: t.support.description,
    body,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'ContactPage',
        name: t.support.h1,
        url: `${ORIGIN}${r.support}`,
        inLanguage: t.lang,
      },
    ],
  });
}
