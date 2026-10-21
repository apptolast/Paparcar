/**
 * The page Hosting serves for every miss.
 *
 * It is ONE page for the whole site — Firebase serves `/404.html` whatever the path, so it cannot
 * know the reader's language. It says its piece in Spanish and then in English, and hands back the
 * two ways out that always exist: the home page and the app.
 */
import { esc, reveal, button } from '../lib/components.mjs';
import { page } from '../lib/layout.mjs';
import { PLAY_URL } from '../lib/config.mjs';

export default function notFound({ t, lang, base }) {
  const body = `<section class="notfound">
  <div class="shell shell--narrow notfound__inner" ${reveal()}>
    <p class="notfound__code" aria-hidden="true">404</p>
    <h1 class="notfound__title">Esta página no está donde la dejaste.</h1>
    <p class="notfound__lead">
      El enlace no lleva a ninguna parte. Tu coche, en cambio, Paparcar sí sabe dónde está.
    </p>
    <p class="notfound__lead" lang="en">
      This link does not lead anywhere. Your car, on the other hand, Paparcar does know how to find.
    </p>
    <div class="notfound__actions">
      ${button({ href: '/', label: 'Ir al inicio · Go home', icon: 'arrowRight' })}
      ${button({ href: PLAY_URL, label: 'Google Play', variant: 'ghost', external: true })}
    </div>
    <p class="notfound__links">
      <a class="link link--quiet" href="/support">Soporte · Support</a>
      <a class="link link--quiet" href="/privacy-policy">Privacidad · Privacy</a>
    </p>
  </div>
</section>`;

  return page({
    t,
    lang,
    base,
    pageKey: 'notFound',
    title: '404 — Paparcar',
    description: '',
    noindex: true,
    body,
    bodyClass: 'page--notfound',
  });
}
