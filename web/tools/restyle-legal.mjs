/**
 * Dress the two hand-written legal pages in the site's system — without touching their content.
 *
 * `privacy-policy.html` and `delete-account.html` are NOT build output. The first is opened from
 * inside the app, is the document Play's Data Safety form has to agree with, and is read by
 * `PrivacyPolicyParityGuardrailTest`, which compares the skeleton of its `<section id="en">` and
 * `<section id="es">` halves — heading numbers, bullets per heading, table rows. Regenerating them
 * from a template would put that document at the mercy of a build script.
 *
 * So this replaces only the CHROME around the content, between explicit markers:
 *
 *   · `<head>`                 → site head (font, stylesheets, description, Open Graph, canonical)
 *   · `<header class="brand">` → the site header, with a way back to paparcar.com
 *   · the `<h1>` + lang nav    → added once, then kept in sync
 *   · `<footer>`               → the site footer strip
 *
 * The `<section>` halves, the `<hr class="split">` between them and every word inside are passed
 * through byte for byte. Re-running is safe: the generated chrome sits between `pap:shell` markers.
 *
 *   node web/tools/restyle-legal.mjs [--check]
 */
import { readFile, writeFile } from 'node:fs/promises';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { ORIGIN, SUPPORT_EMAIL, PLAY_URL } from '../lib/config.mjs';
import { logoLockup } from '../lib/icons.mjs';
import { asset, setAssetManifest } from '../lib/assets.mjs';
import { iconLinks } from '../lib/layout.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const PUBLIC = join(ROOT, 'hosting', 'public');

const PAGES = [
  {
    file: 'privacy-policy.html',
    path: '/privacy-policy',
    title: 'Privacy Policy · Política de Privacidad — paparcar',
    description:
      'How paparcar handles your data: what it collects, why it needs background location, who it is shared with and how to delete your account. English and Spanish.',
    h1: 'Privacy Policy',
    h1es: 'Política de Privacidad',
    kicker: 'Legal',
  },
  {
    file: 'delete-account.html',
    path: '/delete-account',
    title: 'Delete your account · Eliminar tu cuenta — paparcar',
    description:
      'How to delete your paparcar account and its data, with the app installed or without it. English and Spanish.',
    h1: 'Delete your account',
    h1es: 'Eliminar tu cuenta',
    kicker: 'Legal',
  },
];

const START = '<!-- pap:shell-start -->';
const END = '<!-- pap:shell-end -->';
const FOOT_START = '<!-- pap:foot-start -->';
const FOOT_END = '<!-- pap:foot-end -->';

function head(page) {
  return `
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${page.title}</title>
<meta name="description" content="${page.description}">
<link rel="canonical" href="${ORIGIN}${page.path}">
<meta name="theme-color" content="#0D1117">
<meta name="color-scheme" content="dark">
${iconLinks()}
<meta property="og:type" content="article">
<meta property="og:site_name" content="paparcar">
<meta property="og:title" content="${page.title}">
<meta property="og:description" content="${page.description}">
<meta property="og:url" content="${ORIGIN}${page.path}">
<meta property="og:image" content="${ORIGIN}${asset('/assets/img/og-card.jpg')}">
<meta name="twitter:card" content="summary_large_image">
<meta name="robots" content="index, follow">
<link rel="preload" href="${asset('/assets/font/plus-jakarta-sans.woff2')}" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="${asset('/assets/css/site.css')}">
<link rel="stylesheet" href="${asset('/assets/css/legal.css')}">
`;
}

function shell(page) {
  return `${START}
<a class="skip-link" href="#doc">Skip to content · Saltar al contenido</a>
<header class="site-header">
  <div class="shell site-header__inner">
    <a class="brand" href="/" aria-label="paparcar">
      ${logoLockup({ width: 136 })}
    </a>
    <div class="site-header__actions">
      <a class="backlink" href="/">← paparcar.com</a>
    </div>
  </div>
</header>

<main class="legal-main" id="doc">
<p class="eyebrow">${page.kicker}</p>
<h1>${page.h1} <span style="color:var(--muted)">·</span> <span lang="es">${page.h1es}</span></h1>
${END}`;
}

function foot(page) {
  return `${FOOT_START}
<footer>
  <p>© 2026 paparcar ·
    <a href="/">paparcar.com</a> ·
    <a href="${page.file === 'privacy-policy.html' ? '/delete-account' : '/privacy-policy'}">${
      page.file === 'privacy-policy.html' ? 'Delete your account' : 'Privacy Policy'
    }</a> ·
    <a href="/support">Support</a> ·
    <a href="mailto:${SUPPORT_EMAIL}">${SUPPORT_EMAIL}</a> ·
    <a href="${PLAY_URL}" rel="noopener">Google Play</a>
  </p>
</footer>
</main>
${FOOT_END}`;
}

/** Replace between markers, or install them the first time over the legacy chrome. */
function swap(html, startMark, endMark, replacement, legacy) {
  const marked = new RegExp(`${startMark}[\\s\\S]*?${endMark}`);
  if (marked.test(html)) return html.replace(marked, replacement);
  if (!legacy.test(html)) throw new Error(`could not find the block to replace: ${legacy}`);
  return html.replace(legacy, replacement);
}

/**
 * Re-dress both legal pages. Called by `web/build.mjs` as a build STEP — the pages link the same
 * content-hashed stylesheet as everything else, so leaving this as a command somebody has to
 * remember would mean shipping two pages pointing at a stylesheet that no longer exists.
 */
export async function restyleLegal({ check = false, verbose = true } = {}) {
  let changed = 0;
  
  for (const page of PAGES) {
    const file = join(PUBLIC, page.file);
    const original = await readFile(file, 'utf8');
  
    // The content halves must survive untouched — captured before and compared after.
    const sections = original.match(/<section id="(?:en|es)"[\s\S]*?<\/section>/g);
    if (!sections || sections.length !== 2) throw new Error(`${page.file}: expected two <section> halves`);
  
    let html = original;
    html = html.replace(/<head>[\s\S]*?<\/head>/, `<head>${head(page)}</head>`);
    html = swap(html, START, END, shell(page), /<header class="brand">[\s\S]*?<\/header>\s*<main>/);
    html = swap(html, FOOT_START, FOOT_END, foot(page), /<footer>[\s\S]*?<\/footer>\s*<\/main>/);
  
    // ⛔ The table of recipients lives INSIDE `<section id="en">`. Wrapping it in a scroll box was
    // the first thing tried here and it is exactly what this script must not do: the halves are the
    // document, and the guard compares them. Its phone behaviour is a CSS problem, and `legal.css`
    // solves it there.
  
    const after = html.match(/<section id="(?:en|es)"[\s\S]*?<\/section>/g) ?? [];
    const untouched = after.length === 2 && after.every((s, i) => s === sections[i]);
    if (!untouched) throw new Error(`${page.file}: the content halves changed — refusing to write`);
  
    if (html === original) {
      if (verbose) console.log(`  ${page.file}  unchanged`);
    } else if (check) {
      if (verbose) console.log(`  ${page.file}  WOULD CHANGE`);
      changed++;
    } else {
      await writeFile(file, html, 'utf8');
      if (verbose) console.log(`  ${page.file}  restyled`);
      changed++;
    }
  }
  
  
  if (verbose) console.log(`${changed} page(s) ${check ? 'pending' : 'written'}; content halves verified identical`);
  return changed;
}

// CLI: standalone runs need the manifest the build wrote.
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  setAssetManifest(JSON.parse(await readFile(join(PUBLIC, 'assets', 'manifest.json'), 'utf8')));
  await restyleLegal({ check: process.argv.includes('--check') });
}
