/**
 * Static site generator for paparcar.com — no framework, no runtime, no dependencies.
 *
 * Why not Next/Astro: the site is a handful of pages of mostly-static product copy. A build that is
 * one Node file with zero `node_modules` deploys straight to the Firebase Hosting target that
 * already exists in `firebase.json`, ships no client framework, and cannot rot on a transitive
 * upgrade. Components are plain functions in `lib/` and `pages/`; the copy is JSON per locale.
 *
 *   node web/build.mjs          → writes hosting/public
 *   node web/build.mjs --check  → also fails on broken internal links / missing assets
 *
 * ⛔ Two files in the output are SOURCES, not build artefacts, and are never written here:
 *    `privacy-policy.html` (linked from inside the app, guarded by PrivacyPolicyParityGuardrailTest)
 *    and `delete-account.html`. The build only checks that they are still there.
 */
import { readFile, writeFile, mkdir, readdir, rm, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

import { LOCALES, ORIGIN, routes, COPY_TOKENS } from './lib/config.mjs';
import { asset, setAssetManifest } from './lib/assets.mjs';
import { restyleLegal } from './tools/restyle-legal.mjs';
import home from './pages/home.mjs';
import support from './pages/support.mjs';
import terms from './pages/terms.mjs';
import notFound from './pages/notfound.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..');
const OUT = join(ROOT, 'hosting', 'public');
const ASSETS_OUT = join(OUT, 'assets');

const PAGES = { home, support, terms };

/**
 * Text that this build writes goes out with LF, whatever the checkout has on disk.
 *
 * ⛔ Not cosmetics: `publishAssets()` names every asset after the sha256 of its bytes, so without
 * this the NAME of a text file depends on the line endings of whoever runs the build. Measured on
 * 30-09-2026 with the same `legal.css` in two places of this repo: 4895 bytes with CRLF in the main
 * worktree (`223eea16`) against 4755 with LF in the index and in any fresh worktree (`c62e5db9`) —
 * same CSS, two names. Building from the main worktree renamed an asset nobody had touched, twice
 * [WEB-A-BUILD-MUST-NOT-DEPEND-ON-THE-CHECKOUT-001].
 *
 * `.gitattributes` already declares `eol=lf`; this is the build honouring it.
 *
 * ⛔ By EXTENSION, never by sniffing: normalising a png, a webp or a woff2 corrupts it. Anything
 * not on this list is copied byte for byte.
 */
const TEXT_EXT = new Set(['css', 'js', 'mjs', 'svg', 'json', 'map', 'txt', 'webmanifest', 'xml', 'html']);
const isText = (name) => TEXT_EXT.has(name.split('.').pop().toLowerCase());
const toLf = (bytes) => Buffer.from(bytes.toString('utf8').replace(/\r\n/g, '\n'), 'utf8');

/** Files in `hosting/public` this build must never create, delete or overwrite. */
const HAND_WRITTEN = ['privacy-policy.html', 'delete-account.html', 'favicon.svg'];

const check = process.argv.includes('--check');
const problems = [];

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

/**
 * Resolve `{{token}}` in the copy against `COPY_TOKENS`.
 *
 * The copy never writes a plan limit as a numeral — see the note on `FREE_LIMITS`. An UNKNOWN token
 * is a hard error rather than a silent pass-through: a typo like `{{historydays}}` would otherwise
 * ship to production as literal braces in the middle of a sentence.
 */
function resolveTokens(value, where) {
  if (typeof value === 'string') {
    return value.replace(/\{\{(\w+)\}\}/g, (_, name) => {
      if (!(name in COPY_TOKENS)) {
        problems.push(`${where}: unknown copy token {{${name}}} — known: ${Object.keys(COPY_TOKENS).join(', ')}`);
        return `{{${name}}}`;
      }
      return COPY_TOKENS[name];
    });
  }
  if (Array.isArray(value)) return value.map((v, i) => resolveTokens(v, `${where}[${i}]`));
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolveTokens(v, `${where}.${k}`)]));
  }
  return value;
}

async function loadArt() {
  const dir = join(HERE, 'assets', 'img');
  const art = {};
  for (const file of await readdir(dir)) {
    if (file.startsWith('art-') && file.endsWith('.svg')) {
      art[file.replace(/\.svg$/, '')] = await readFile(join(dir, file), 'utf8');
    }
  }
  return art;
}

async function walk(dir, prefix = '') {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) out.push(...(await walk(join(dir, entry.name), rel)));
    else out.push(rel);
  }
  return out;
}

/**
 * Publish `web/assets/**` under content-hashed names and return the manifest.
 *
 * ⛔ Not a plain copy, and the reason is a bug that shipped: with stable filenames the stylesheet
 * has to carry a short `max-age`, and any window at all means a returning visitor can get NEW html
 * painted by OLD css. That is not a slightly stale page, it is a broken one — classes that did not
 * exist yet have no rules. Hashed names make the pairing impossible to get wrong and let the files
 * be cached for a year. See `lib/assets.mjs`.
 *
 * Order matters: the stylesheet references the font by URL, so everything that is referenced is
 * hashed BEFORE the files that reference it, and the CSS text is rewritten on the way through.
 */
async function publishAssets() {
  const from = join(HERE, 'assets');
  const files = await walk(from);
  const manifest = {};
  const isReferencing = (f) => f.endsWith('.css');

  for (const rel of [...files.filter((f) => !isReferencing(f)), ...files.filter(isReferencing)]) {
    let bytes = await readFile(join(from, rel));
    // ⛔ Antes de hashear, no despues: el hash ES el nombre.
    if (isText(rel)) bytes = toLf(bytes);
    if (isReferencing(rel)) {
      bytes = Buffer.from(
        bytes.toString('utf8').replace(/\/assets\/([\w./-]+)/g, (whole, path) =>
          manifest[path] ? `/assets/${manifest[path]}` : whole,
        ),
        'utf8',
      );
    }
    const hash = createHash('sha256').update(bytes).digest('hex').slice(0, 8);
    const hashed = rel.replace(/(\.[^./]+)$/, `.${hash}$1`);
    manifest[rel] = hashed;
    const dst = join(ASSETS_OUT, hashed);
    await mkdir(dirname(dst), { recursive: true });
    await writeFile(dst, bytes);
  }
  return manifest;
}

async function emit(path, contents) {
  // Las paginas salen de literales de plantilla en ficheros `.mjs`, asi que un `.mjs` con CRLF
  // meteria `\r` en el HTML por la misma puerta que lo metia en los assets.
  if (typeof contents === 'string') contents = contents.replace(/\r\n/g, '\n');
  const full = join(OUT, path);
  await mkdir(dirname(full), { recursive: true });
  await writeFile(full, contents, 'utf8');
  return full;
}

// ── link + asset checking ─────────────────────────────────────────────────────

/** Every internal href must resolve to something the deploy will actually serve. */
function checkLinks(html, page, written) {
  const hrefs = [...html.matchAll(/(?:href|src)="([^"]+)"/g)].map((m) => m[1]);
  for (const href of hrefs) {
    if (/^(https?:|mailto:|tel:|data:|#)/.test(href)) continue;
    const [path, hash] = href.split('#');
    if (!path) continue;
    const candidates = [
      path.replace(/^\//, ''),
      `${path.replace(/^\//, '')}.html`,
      `${path.replace(/^\//, '').replace(/\/$/, '')}/index.html`,
      path === '/' ? 'index.html' : null,
    ].filter(Boolean);
    const ok = candidates.some((c) => written.has(c) || existsSync(join(OUT, c)));
    if (!ok) problems.push(`${page}: dead link → ${href}${hash ? ` (#${hash})` : ''}`);
  }
}

/**
 * An anchor that points at an id no section carries is a silently broken menu.
 * Only SAME-page anchors (`href="#x"`) are checkable here: `href="/#x"` is a cross-page jump whose
 * target lives in another document, and flagging those would just be noise.
 */
function checkAnchors(html, page) {
  const ids = new Set([...html.matchAll(/ id="([^"]+)"/g)].map((m) => m[1]));
  for (const m of html.matchAll(/href="#([^"]+)"/g)) {
    const target = m[1];
    if (!ids.has(target)) problems.push(`${page}: anchor #${target} has no target`);
  }
}

// ── build ─────────────────────────────────────────────────────────────────────

async function build() {
  const artSvg = await loadArt();
  const written = new Set();
  const urls = [];

  // Assets go first: they are wiped before publishing so a previous hash never lingers in the
  // deploy, and nothing can render until the manifest exists.
  await rm(ASSETS_OUT, { recursive: true, force: true });
  const assets = await publishAssets();
  setAssetManifest(assets);
  await emit('assets/manifest.json', `${JSON.stringify(assets, null, 2)}
`);

  // The web app manifest. It goes out HERE, before a single page is built, because the link
  // checker asks the DISK whether `/site.webmanifest` exists — emitting it after the pages
  // makes every page report a dead link to it.
  // Its icons carry the content hash `publishAssets()` just gave them.
  // ⛔ No `display` and no `start_url`: this declares an IDENTITY (name, colours, icons) so an
  // Android home-screen shortcut has something to show. Adding either would make the site
  // installable as an app, which is a product decision nobody has taken.
  await emit('site.webmanifest', JSON.stringify({
    name: 'Paparcar',
    short_name: 'Paparcar',
    theme_color: '#0D1117',
    background_color: '#0D1117',
    icons: [192, 512].map((px) => ({
      src: asset(`/assets/img/icon-${px}.png`),
      sizes: `${px}x${px}`,
      type: 'image/png',
    })),
  }, null, 2) + '\n');

  for (const { code, base } of LOCALES) {
    const t = resolveTokens(await readJson(join(HERE, 'content', `${code}.json`)), `content/${code}.json`);
    const r = routes(base);

    for (const [key, render] of Object.entries(PAGES)) {
      const html = render({ t, lang: code, base, artSvg });
      const path = key === 'home' ? `${base}/index.html`.replace(/^\//, '') : `${base}/${key}.html`.replace(/^\//, '');
      await emit(path, html);
      written.add(path);
      urls.push({ loc: `${ORIGIN}${r[key]}`, lang: code, key });
      if (check) {
        checkLinks(html, path, written);
        checkAnchors(html, path);
      }
    }
  }

  // sitemap — one entry per page with its alternate, which is what search engines read for i18n.
  const byKey = new Map();
  for (const u of urls) byKey.set(`${u.key}:${u.lang}`, u.loc);
  const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${urls
  .map(
    (u) => `  <url>
    <loc>${u.loc}</loc>
${LOCALES.map((l) => `    <xhtml:link rel="alternate" hreflang="${l.code}" href="${byKey.get(`${u.key}:${l.code}`)}"/>`).join('\n')}
    <xhtml:link rel="alternate" hreflang="x-default" href="${byKey.get(`${u.key}:${LOCALES[0].code}`)}"/>
    <priority>${u.key === 'home' ? '1.0' : '0.6'}</priority>
  </url>`,
  )
  .join('\n')}
  <url><loc>${ORIGIN}/privacy-policy</loc><priority>0.4</priority></url>
  <url><loc>${ORIGIN}/delete-account</loc><priority>0.3</priority></url>
</urlset>
`;
  await emit('sitemap.xml', sitemap);
  await emit('robots.txt', `User-agent: *\nAllow: /\n\nSitemap: ${ORIGIN}/sitemap.xml\n`);

  // One 404 for the whole site — Hosting serves `/404.html` for every miss regardless of path, so
  // it cannot be per-locale. Built from the default locale's strings and kept out of the sitemap.
  const t404 = await readJson(join(HERE, 'content', `${LOCALES[0].code}.json`));
  const notFoundHtml = notFound({ t: t404, lang: LOCALES[0].code, base: LOCALES[0].base, artSvg });
  await emit('404.html', notFoundHtml);
  written.add('404.html');
  if (check) checkLinks(notFoundHtml, '404.html', written);

  for (const file of HAND_WRITTEN) {
    if (!existsSync(join(OUT, file))) problems.push(`missing hand-written page: ${file}`);
  }

  // The two hand-written legal pages link the same hashed stylesheet, so re-dressing them is a STEP
  // of this build, not a command somebody has to remember after it. It only ever touches their
  // chrome and refuses to write if their content halves are not byte-identical.
  await restyleLegal({ verbose: false });

  // Report
  const sizes = await Promise.all(
    [...written].map(async (p) => [p, (await stat(join(OUT, p))).size]),
  );
  for (const [p, size] of sizes.sort()) console.log(`  ${p.padEnd(22)} ${(size / 1024).toFixed(1)} KB`);
  console.log(`\n${written.size} pages + sitemap + robots → ${relative(ROOT, OUT)}`);

  if (problems.length) {
    console.error(`\n${problems.length} problem(s):`);
    for (const p of problems) console.error(`  ✗ ${p}`);
    process.exitCode = 1;
  } else if (check) {
    console.log('links, anchors and hand-written pages: ok');
  }
}

await build();
