import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as path from 'node:path';
import * as url from 'node:url';
import * as crypto from 'node:crypto';
import { SourceTextModule, SyntheticModule, createContext } from 'node:vm';

const script = new URL('./store_listing.mjs', import.meta.url);
const source = readFileSync(script, 'utf8');
const languages = ['en-US', 'es-ES', 'it-IT', 'pt-PT', 'fr-FR', 'de-DE', 'nl-NL', 'pl-PL', 'ro'];
const png = Buffer.alloc(24);
Buffer.from('89504e470d0a1a0a', 'hex').copy(png);
png.writeUInt32BE(1080, 16);
png.writeUInt32BE(1920, 20);
const hash = (name, data) => crypto.createHash(name).update(data).digest('hex');
const original = () => ({
  listings: languages.map(language => ({ language, title: 'Paparcar', shortDescription: 'Parking', fullDescription: 'Old copy' })),
  tracks: [{ track: 'production', releases: [{ status: 'completed', versionCodes: ['16'] }] }],
  images: Object.fromEntries(languages.map(l => [l, [{ sha1: 'old-image' }]])),
});

function sandbox() {
  const files = new Map(), edits = new Map(), calls = [], logs = [];
  let live = original(), next = 0;
  const options = { denyValidation: false, badDigest: false };
  const fs = {
    mkdirSync() {},
    existsSync: p => files.has(p),
    writeFileSync: (p, data) => files.set(p, data),
    readFileSync(p) {
      if (files.has(p)) return files.get(p);
      if (p.endsWith('.png')) return Buffer.concat([png, Buffer.from(p)]);
      if (p.endsWith('.txt')) return '--- 1. NAME ---\nPaparcar\n--- 2. SHORT ---\nParking\n--- 3. FULL ---\nNew copy\n';
      throw Error('Unexpected file read: ' + p);
    },
  };
  async function fetch(address, request) {
    const pathname = new URL(address).pathname;
    const parts = pathname.split('/');
    const id = parts[parts.indexOf('edits') + 1];
    const state = edits.get(id);
    calls.push({ method: request.method, pathname });
    const respond = (data, status = 200) => ({ ok: status < 400, status, text: async () => JSON.stringify(data) });
    if (pathname.endsWith('/edits') && request.method === 'POST') {
      const editId = 'edit-' + ++next;
      edits.set(editId, structuredClone(live));
      return respond({ id: editId });
    }
    if (pathname.endsWith(':validate')) {
      return options.denyValidation ? respond({ error: { message: 'The caller does not have permission' } }, 403) : respond({});
    }
    if (pathname.endsWith(':commit')) {
      live = structuredClone(edits.get(id.replace(':commit', '')));
      return respond({ id });
    }
    if (pathname.endsWith('/tracks')) return respond({ tracks: state.tracks });
    if (pathname.endsWith('/listings')) return respond({ listings: state.listings });
    if (pathname.endsWith('/phoneScreenshots')) {
      const language = parts.at(-2);
      if (request.method === 'GET') return respond({ images: state.images[language] });
      if (request.method === 'DELETE') { state.images[language] = []; return respond({}); }
      const image = { sha1: hash('sha1', request.body), sha256: options.badDigest ? 'wrong' : hash('sha256', request.body) };
      if (options.badDigest) image.sha1 = 'wrong';
      state.images[language].push(image);
      return respond({ image });
    }
    if (request.method === 'PUT') {
      const listing = JSON.parse(request.body);
      state.listings = state.listings.map(x => x.language === listing.language ? listing : x);
      return respond(listing);
    }
    if (request.method === 'DELETE') { edits.delete(id); return respond({}); }
    throw Error('Unexpected request: ' + request.method + ' ' + pathname);
  }
  async function run(mode) {
    const process = { argv: ['node', script.pathname, mode], env: { PATH: '' }, exitCode: 0 };
    const context = createContext({ process, Buffer, URL, AbortSignal, fetch,
      console: { log: message => logs.push(message), error: message => logs.push(message) } });
    const imports = {
      'node:fs': fs, 'node:path': path, 'node:url': url, 'node:crypto': crypto,
      'node:child_process': { spawnSync: () => ({ status: 0, stdout: 'fake-token' }) },
    };
    const module = new SourceTextModule(source, { context, identifier: script.href,
      initializeImportMeta: meta => { meta.url = script.href; } });
    await module.link(specifier => {
      const values = imports[specifier];
      return new SyntheticModule(Object.keys(values), function () {
        for (const [key, value] of Object.entries(values)) this.setExport(key, value);
      }, { context });
    });
    await module.evaluate();
    return process.exitCode;
  }
  return { files, edits, calls, logs, options, run, get live() { return live; } };
}

test('inspect records a baseline without changing or committing the listing', async () => {
  const s = sandbox();
  assert.equal(await s.run('--inspect'), 0);
  assert.equal(s.edits.size, 0);
  assert.equal(s.live.listings[0].fullDescription, 'Old copy');
  assert.equal(s.calls.filter(x => x.method === 'PUT').length, 0);
  assert.equal(s.calls.filter(x => x.pathname.endsWith(':commit')).length, 0);
});

test('applies 72 images and nine descriptions atomically and rereads the committed state', async () => {
  const s = sandbox();
  assert.equal(await s.run('--inspect'), 0);
  assert.equal(await s.run('--apply'), 0, s.logs.join('\n'));
  assert.equal(s.calls.filter(x => x.method === 'POST' && x.pathname.endsWith('/phoneScreenshots')).length, 72);
  assert.equal(s.calls.filter(x => x.pathname.endsWith(':commit')).length, 1);
  assert.ok(s.live.listings.every(x => x.fullDescription === 'New copy' && x.title === 'Paparcar'));
  assert.ok(Object.values(s.live.images).every(images => images.length === 8));
  assert.ok([...s.files.keys()].some(p => p.endsWith('after.json')));
});

test('a denied validation aborts before uploading and discards the temporary edit', async () => {
  const s = sandbox();
  await s.run('--inspect');
  s.options.denyValidation = true;
  assert.equal(await s.run('--apply'), 1);
  assert.equal(s.edits.size, 0);
  assert.equal(s.calls.filter(x => x.method === 'POST' && x.pathname.endsWith('/phoneScreenshots')).length, 0);
  assert.equal(s.calls.filter(x => x.pathname.endsWith(':commit')).length, 0);
  assert.equal(s.live.listings[0].fullDescription, 'Old copy');
});

test('an image digest mismatch prevents the commit and leaves the original listing intact', async () => {
  const s = sandbox();
  await s.run('--inspect');
  s.options.badDigest = true;
  assert.equal(await s.run('--apply'), 1);
  assert.equal(s.edits.size, 0);
  assert.equal(s.calls.filter(x => x.pathname.endsWith(':commit')).length, 0);
  assert.equal(s.live.listings[0].fullDescription, 'Old copy');
});
