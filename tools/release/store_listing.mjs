#!/usr/bin/env node
/**
 * Update only phone screenshots and full descriptions in the existing nine Play listings.
 * --inspect saves a baseline and discards its temporary edit.
 * --apply stages, verifies and validates all changes before a single commit.
 * Requires explicit user authorization for --apply; it never changes binaries or tracks.
 *
 * Auth reuses play_api.Play without printing or persisting the token/credential.
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const BASE = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.rndeveloper.paparcar';
const UPLOAD = BASE.replace('/androidpublisher/v3/', '/upload/androidpublisher/v3/');
const LISTING = resolve(ROOT, 'docs/release/play-listing');
const OUT = resolve(ROOT, 'build/store-publish');
const LANGS = ['en-US', 'es-ES', 'it-IT', 'pt-PT', 'fr-FR', 'de-DE', 'nl-NL', 'pl-PL', 'ro'];
const LIMITS = { title: 30, shortDescription: 80, fullDescription: 4000 };
const mode = process.argv[2];
if (!['--inspect', '--check-permissions', '--apply'].includes(mode)) {
  throw Error('Use --inspect, --check-permissions or --apply [credential-path]');
}
const credential = resolve(process.argv[3] || resolve(ROOT, 'google-application-credentials-play.json'));
const auth = spawnSync('python', ['-B', '-c',
  "import sys; sys.path.insert(0, 'tools/release'); import play_api; print(play_api.Play(sys.argv[1]).token())",
  credential], { cwd: ROOT, encoding: 'utf8', env: {
    ...process.env, PATH: 'C:/Program Files/Git/usr/bin;' + process.env.PATH,
  } });
if (auth.status !== 0) throw Error('Authentication failed through the existing Play client.');
const token = auth.stdout.trim();
if (!token || /\s/.test(token)) throw Error('Invalid auth response.');
const sha256 = data => createHash('sha256').update(data).digest('hex');
const sha1 = data => createHash('sha1').update(data).digest('hex');
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const json = path => JSON.parse(readFileSync(path, 'utf8'));
const save = (name, data) => writeFileSync(resolve(OUT, name), JSON.stringify(data, null, 2) + '\n');
mkdirSync(OUT, { recursive: true });

async function request(method, path, body, media = false) {
  const response = await fetch((media ? UPLOAD : BASE) + path, {
    method,
    headers: { Authorization: 'Bearer ' + token,
      ...(body ? { 'Content-Type': media ? 'image/png' : 'application/json' } : {}) },
    body: body ? (media ? body : JSON.stringify(body)) : undefined,
    signal: AbortSignal.timeout(120000),
  });
  const raw = await response.text();
  let data;
  try { data = raw ? JSON.parse(raw) : {}; } catch { throw Error('Invalid Play response: HTTP ' + response.status); }
  if (!response.ok) throw Error(method + ' ' + path + ': HTTP ' + response.status + ' ' +
    (data.error?.message || 'Play rejected the request.'));
  return data;
}
const editPath = id => '/edits/' + id;
const imagePath = (id, lang) => editPath(id) + '/listings/' + lang + '/phoneScreenshots';
async function snapshot(id) {
  const listings = (await request('GET', editPath(id) + '/listings')).listings || [];
  const tracks = (await request('GET', editPath(id) + '/tracks')).tracks || [];
  if (!same(listings.map(x => x.language).sort(), [...LANGS].sort())) throw Error('Unexpected listing locales.');
  const production = tracks.find(x => x.track === 'production');
  if (!production?.releases?.some(x => x.status === 'completed' && x.versionCodes?.includes('16'))) {
    throw Error('Production baseline has changed; review published features before updating copy.');
  }
  const images = {};
  for (const lang of LANGS) images[lang] = (await request('GET', imagePath(id, lang))).images || [];
  return { listings: listings.sort((a, b) => a.language.localeCompare(b.language)),
    tracks: tracks.sort((a, b) => a.track.localeCompare(b.track)), images };
}
function fingerprint(snapshot) {
  return sha256(JSON.stringify({ ...snapshot,
    images: Object.fromEntries(LANGS.map(l => [l, snapshot.images[l].map(x => x.sha256 || x.sha1)])),
  }));
}
function planned() {
  return LANGS.map(language => {
    const source = readFileSync(resolve(LISTING, 'paste/play-' + language + '.txt'), 'utf8');
    const sections = source.split(/^--- \d+\.[^\n]*---\s*$/m);
    if (sections.length !== 4) throw Error('Invalid paste source: ' + language);
    const [title, shortDescription, fullDescription] = sections.slice(1).map(x => x.trim());
    const listing = { language, title, shortDescription, fullDescription };
    for (const [field, limit] of Object.entries(LIMITS)) {
      if (!listing[field] || [...listing[field]].length > limit) throw Error(language + ': invalid ' + field);
    }
    const folder = language.split('-')[0];
    const images = Array.from({ length: 8 }, (_, i) => {
      const path = resolve(LISTING, 'assets/screenshots/' + folder + '/' + folder + '-play-shot-' + (i + 1) + '.png');
      const data = readFileSync(path);
      if (data.toString('hex', 0, 8) !== '89504e470d0a1a0a' ||
          data.readUInt32BE(16) !== 1080 || data.readUInt32BE(20) !== 1920) throw Error('Invalid screenshot: ' + path);
      return { path, sha256: sha256(data), sha1: sha1(data), bytes: data.length };
    });
    return { listing, images };
  });
}
function assertImages(got, want, lang) {
  if (got.length !== 8 || !got.every((x, i) =>
    x.sha256 === want[i].sha256 || x.sha1 === want[i].sha1)) {
    throw Error('Screenshot count, order or digest mismatch: ' + lang);
  }
}
function verify(snapshot, plans, before) {
  if (!same(snapshot.tracks, before.tracks)) throw Error('Tracks changed during listing edit.');
  for (const p of plans) {
    const language = p.listing.language;
    const expected = before.listings.find(x => x.language === language);
    const got = snapshot.listings.find(x => x.language === language);
    if (!same(got, { ...expected, fullDescription: p.listing.fullDescription })) {
      throw Error('Unexpected listing fields: ' + language);
    }
    assertImages(snapshot.images[language], p.images, language);
  }
}
async function main() {
  const plans = planned();
  const baselinePath = resolve(OUT, 'before.json');
  if (mode === '--inspect' && existsSync(baselinePath)) throw Error('Baseline already exists; preserve it and review before re-inspecting.');
  const id = (await request('POST', '/edits')).id;
  let commitAttempted = false;
  let committed = false;
  try {
    const before = await snapshot(id);
    if (mode === '--inspect') {
      save('before.json', before);
      save('plan.json', plans);
      for (const p of plans) {
        const old = before.listings.find(x => x.language === p.listing.language);
        console.log(p.listing.language + ': ' + before.images[p.listing.language].length +
          ' -> 8 phone screenshots; full description ' + [...p.listing.fullDescription].length + '/4000; ' +
          (old.fullDescription === p.listing.fullDescription ? 'text unchanged' : 'text differs'));
      }
      console.log('Baseline and plan saved locally; temporary edit discarded.');
      return;
    }
    // Exercise validation before uploading 72 images: staging alone does not prove save permission.
    const probe = plans.find(p => p.listing.language === 'es-ES');
    const original = before.listings.find(x => x.language === 'es-ES');
    await request('PUT', editPath(id) + '/listings/es-ES',
      { ...original, fullDescription: probe.listing.fullDescription });
    await request('POST', editPath(id) + ':validate');
    console.log('Store listing validation permission confirmed.');
    if (mode === '--check-permissions') return;
    if (!same(plans, json(resolve(OUT, 'plan.json')))) throw Error('Local assets changed after inspection.');
    if (fingerprint(before) !== fingerprint(json(baselinePath))) throw Error('Remote state changed after inspection.');
    for (const p of plans) {
      const lang = p.listing.language;
      const old = before.listings.find(x => x.language === lang);
      if (p.listing.title !== old.title || p.listing.shortDescription !== old.shortDescription) {
        throw Error('This operation is limited to full descriptions and phone screenshots: ' + lang);
      }
      await request('PUT', editPath(id) + '/listings/' + lang,
        { ...old, fullDescription: p.listing.fullDescription });
      await request('DELETE', imagePath(id, lang));
      for (const item of p.images) {
        const data = readFileSync(item.path);
        if (sha256(data) !== item.sha256) throw Error('Local image changed during upload.');
        const { image: uploaded } = await request('POST', imagePath(id, lang) + '?uploadType=media', data, true);
        if (!uploaded || (uploaded.sha256 !== item.sha256 && uploaded.sha1 !== item.sha1)) throw Error('Upload digest mismatch: ' + lang);
      }
      console.log(lang + ': description and 8 screenshots staged.');
    }
    verify(await snapshot(id), plans, before);
    await request('POST', editPath(id) + ':validate');
    save('validated.json', { at: new Date().toISOString(), locales: LANGS, count: 72, fingerprint: fingerprint(before) });
    commitAttempted = true;
    const commit = await request('POST', editPath(id) + ':commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW');
    committed = true;
    save('commit.json', { at: new Date().toISOString(), response: commit, count: 72, locales: LANGS });
    console.log('Play accepted the listing commit. Verifying in a new edit...');
    const checkId = (await request('POST', '/edits')).id;
    try {
      const after = await snapshot(checkId);
      verify(after, plans, before);
      save('after.json', after);
      console.log('VERIFIED: 72 screenshots in order and full descriptions in 9 locales; tracks unchanged.');
    } finally { await request('DELETE', editPath(checkId)); }
  } catch (error) {
    save('failure.json', { at: new Date().toISOString(), commitAttempted, committed, message: error.message });
    throw error;
  } finally {
    if (!commitAttempted) await request('DELETE', editPath(id));
  }
}
await main().catch(error => { console.error(error.message); process.exitCode = 1; });
