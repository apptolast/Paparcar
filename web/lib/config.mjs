/**
 * The facts the site is allowed to state about the product, in one place.
 *
 * Everything here is checked against the repository, not assumed:
 *   · `PLAY_URL`        — `applicationId` in `app/build.gradle.kts`
 *   · `SUPPORT_EMAIL`   — the address the app itself gives in `settings_send_diagnostics_no_reply`
 *   · `PRIVACY_PATH`    — `SettingsViewModel.PRIVACY_POLICY_URL`, so the app's own link keeps working
 *   · `MIN_ANDROID`     — `minSdk = 26`
 *   · `APP_VERSION`     — `versionName` of the shipped build
 *
 * ⛔ No invented URL and no invented figure. If a fact is not verifiable in the repo, the copy says
 * less instead of guessing.
 */

export const ORIGIN = 'https://paparcar.com';
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.rndeveloper.paparcar';
export const SUPPORT_EMAIL = 'support@paparcar.com';
export const PRIVACY_PATH = '/privacy-policy';
export const DELETE_PATH = '/delete-account';
export const MIN_ANDROID = '8.0';
export const APP_VERSION = '1.7.0';
/**
 * What the free plan includes, mirrored from the gates in `domain/entitlement/`
 * (`FREE_HISTORY_WINDOW_DAYS`, `FREE_OWN_VEHICLE_LIMIT`, `FREE_ZONE_LIMIT`).
 *
 * ⛔ The copy does NOT write these numbers: it writes `{{historyDays}}`, `{{freeVehicles}}` and
 * `{{freeZones}}`, and the build substitutes them. That is deliberate. A number typed into the
 * plan table and again into the FAQ, twice per language, is four places to forget when the plan
 * changes — and nothing would go red, the page would simply promise the old plan on the URL the
 * Play listing links to. With a token there is one place, and `WebSiteParityGuardrailTest` compares
 * that one place against the Kotlin constants.
 */
export const FREE_LIMITS = {
  historyDays: 7,
  freeVehicles: 1,
  freeZones: 2,
};

/**
 * Cifras que Plus ENSEÑA, y que no son límites del plan free — por eso no viven en `FREE_LIMITS`,
 * cuyo nombre promete otra cosa: esos son los topes que la app impone sin pagar.
 *
 * `fuelHistoryDays` es la ventana de la serie de precios de una gasolinera que sigues. Misma regla
 * que los límites: **el copy no escribe la cifra**, la pide por marcador, y
 * `WebSiteParityGuardrailTest` la compara contra `FuelWatchRepository.RETENTION_DAYS`.
 * [WEB-A-PLUS-BULLET-MUST-SELL-THE-MEMORY-TOO-001]
 */
export const PLUS_FIGURES = {
  fuelHistoryDays: 30,
};

/**
 * The Black Friday "Founders" gift — 3 months of Plus for the first 2,000 new accounts.
 *
 * Mirrors the three literals `functions/src/foundersPromo.ts` enforces server-side, so they are
 * not invented for the page: `decideReservation()` there rejects any config where
 * `promo.durationMonths !== 3` or `promo.cap > 2_000`, and `FOUNDERS_CLOSE_MILLIS =
 * Date.UTC(2026, 10, 30, 23)` is the same instant as `ENDS_AT_UTC` below. [MONEY-FOUNDERS-PLUS-PROMO-001]
 * [MONEY-FOUNDERS-PROMO-REQUIRES-A-NEW-ACCOUNT-001]
 *
 * ⛔ The deadline is stated in Europe/Madrid local time, not UTC: `23:00 UTC` is `00:00 CET` on the
 * 1st of December, and that is the instant a reader actually cares about. Formatting it with `Intl`
 * from the one UTC instant — instead of writing "1 de diciembre" by hand — is what keeps the two
 * locale strings from drifting from each other or from the real cutoff.
 */
const FOUNDERS_ENDS_AT_UTC = new Date(Date.UTC(2026, 10, 30, 23));
const foundersDeadline = (locale) =>
  new Intl.DateTimeFormat(locale, {
    timeZone: 'Europe/Madrid',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  }).format(FOUNDERS_ENDS_AT_UTC);

export const FOUNDERS_PROMO = {
  cap: 2000,
  durationMonths: 3,
  endsAtUtc: FOUNDERS_ENDS_AT_UTC.toISOString(),
};

/** The tokens the copy may use, and what each resolves to. */
export const COPY_TOKENS = {
  ...Object.fromEntries(Object.entries(FREE_LIMITS).map(([k, v]) => [k, String(v)])),
  ...Object.fromEntries(Object.entries(PLUS_FIGURES).map(([k, v]) => [k, String(v)])),
  minAndroid: MIN_ANDROID,
  foundersCap: String(FOUNDERS_PROMO.cap),
  foundersMonths: String(FOUNDERS_PROMO.durationMonths),
  // Two tokens, not one: the deadline reads differently per language, and COPY_TOKENS is not
  // keyed by locale — each content file picks the one that matches it.
  foundersDeadlineEs: foundersDeadline('es-ES'),
  foundersDeadlineEn: foundersDeadline('en-GB'),
};

/**
 * Locales the site is built in. The first is the default and lives at the root.
 *
 * `label` is the language's ENDONYM — its name in itself, which is what a reader looking for their
 * own language scans for, and which therefore never gets translated. `short` is the two-letter tag
 * the switch shows. Both live here rather than in the copy files: they are the same string in every
 * locale, and duplicating them per locale is how one of them ends up saying "Spanish".
 */
export const LOCALES = [
  { code: 'es', base: '', label: 'Español', short: 'ES' },
  { code: 'en', base: '/en', label: 'English', short: 'EN' },
];

/** Section ids — shared by both locales so one anchor works everywhere. Code stays English. */
export const SECTIONS = {
  how: 'how',
  features: 'features',
  map: 'map',
  automation: 'automation',
  plus: 'plus',
  privacy: 'privacy',
  faq: 'faq',
};

/** Page routes per locale. `key` matches the page module. */
export function routes(base) {
  return {
    home: `${base}/`,
    support: `${base}/support`,
    terms: `${base}/terms`,
    blackfriday: `${base}/blackfriday`,
    privacy: PRIVACY_PATH,
    delete: DELETE_PATH,
    // Hosting serves this for every miss, whatever the path, so there is exactly one of it and it
    // lives at the root — no locale prefix.
    notFound: '/404',
  };
}
