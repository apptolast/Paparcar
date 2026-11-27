import { readFile, readdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { gunzipSync } from 'node:zlib';
import { isDeepStrictEqual } from 'node:util';
import { pathToFileURL } from 'node:url';

export const CHECK_POINTS = [
  ['Madrid', 40.4168, -3.7038], ['Barcelona', 41.3874, 2.1686],
  ['Cuenca', 40.0704, -2.1374], ['Las Palmas', 28.1235, -15.4363],
  ['Palma', 39.5696, 2.6502], ['Ceuta', 35.8894, -5.3213], ['Melilla', 35.2923, -2.9381],
  ['Paris', 48.8566, 2.3522], ['Lyon', 45.764, 4.8357],
  ['Toulouse', 43.6047, 1.4442], ['Ajaccio', 41.9192, 8.7386],
  ['Bourges', 47.081, 2.3988], ['Sancerre', 47.331, 2.838], ['Perpignan', 42.6887, 2.8948],
];

function requireThat(condition, message) {
  if (!condition) throw new Error(message);
}

function near(entry, lat, lon) {
  const rad = Math.PI / 180;
  const a = Math.sin((entry.lat-lat)*rad/2)**2 +
    Math.cos(lat*rad)*Math.cos(entry.lat*rad)*Math.sin((entry.lon-lon)*rad/2)**2;
  return 6371000*2*Math.atan2(Math.sqrt(a), Math.sqrt(1-a)) <= 3000;
}

/** No Firebase credentials, no writes: validate the complete publishable artifact. */
export async function validateSnapshot(publicDir, requireRegions = false, baselinePath) {
  const base = join(publicDir, 'parkings/v1');
  const pointer = JSON.parse(await readFile(join(base, 'latest.json'), 'utf8'));
  requireThat(/^[A-Za-z0-9_-]+$/.test(pointer.version), 'unsafe version');
  requireThat(pointer.serve === true, 'publication must explicitly enable serve');
  const dir = join(base, pointer.version);
  const index = JSON.parse(await readFile(join(dir, 'index.json'), 'utf8'));
  for (const field of ['version', 'generatedAtMs', 'cells', 'parkings', 'serve']) {
    requireThat(index[field] === pointer[field], `pointer/index mismatch: ${field}`);
  }
  const counts = Object.entries(index.counts);
  requireThat(counts.length === index.cells && counts.length > 0, 'invalid cell count');
  const entries = new Map();
  for (const [id, count] of counts) {
    requireThat(/^-?\d+_-?\d+$/.test(id) && Number.isInteger(count) && count > 0, 'invalid index cell');
    const cell = JSON.parse(await readFile(join(dir, id+'.json'), 'utf8'));
    requireThat(cell.version === index.version && cell.cell === id, `wrong cell/version: ${id}`);
    requireThat(cell.parkings.length === count, `wrong count: ${id}`);
    for (const entry of cell.parkings) {
      requireThat(/^osm\/(node|way|relation)\/\d+$/.test(entry.id), 'invalid OSM id');
      requireThat(Number.isFinite(entry.lat) && Math.abs(entry.lat) <= 90 &&
        Number.isFinite(entry.lon) && Math.abs(entry.lon) <= 180, 'invalid coordinates');
      requireThat(!entries.has(entry.id), `duplicate: ${entry.id}`);
      entries.set(entry.id, entry);
    }
  }
  requireThat(entries.size === index.parkings, 'invalid total');
  const files = (await readdir(dir)).filter(name => /^-?\d+_-?\d+\.json$/.test(name));
  requireThat(files.length === index.cells, 'unlisted cells in version directory');
  const bulk = gunzipSync(await readFile(join(dir, 'parkings.ndjson.gz'))).toString('utf8')
    .trim().split('\n').map(line => JSON.parse(line));
  requireThat(bulk.length === entries.size, 'bulk count mismatch');
  const seen = new Set();
  for (const entry of bulk) {
    requireThat(!seen.has(entry.id) && isDeepStrictEqual(entries.get(entry.id), entry),
      `bulk mismatch: ${entry.id}`);
    seen.add(entry.id);
  }
  const samples = Object.fromEntries(CHECK_POINTS.map(([name, lat, lon]) =>
    [name, bulk.filter(entry => near(entry, lat, lon)).length]));
  if (requireRegions) {
    for (const [name, count] of Object.entries(samples)) requireThat(count > 0, `no coverage: ${name}`);
    // Conservative floor below the measured ES base alone; samples independently require FR.
    requireThat(entries.size >= 65000, 'national base unexpectedly small');
  }
  let preserved;
  if (baselinePath) {
    const previous = gunzipSync(await readFile(baselinePath)).toString('utf8').trim().split('\n')
      .map(line => JSON.parse(line).id);
    preserved = previous.filter(id => entries.has(id)).length;
    requireThat(preserved / previous.length >= 0.98, 'more than 2% of the published base disappeared');
    preserved = { present: preserved, previous: previous.length };
  }
  return { version: index.version, cells: index.cells, parkings: index.parkings, samples, preserved };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.argv[2] === '--download-baseline') {
    const get = async path => {
      const response = await fetch('https://paparcar.com/parkings/v1/'+path,
        { signal: AbortSignal.timeout(60_000) });
      requireThat(response.ok, `baseline HTTP ${response.status}`);
      return response;
    };
    const pointer = await (await get('latest.json')).json();
    requireThat(/^[A-Za-z0-9_-]+$/.test(pointer.version), 'unsafe baseline version');
    const response = await get(pointer.version+'/parkings.ndjson.gz');
    const bytes = Buffer.from(await response.arrayBuffer());
    const rows = gunzipSync(bytes).toString('utf8').trim().split('\n');
    requireThat(rows.length === pointer.parkings, 'baseline count mismatch');
    await writeFile(process.argv[3], bytes);
    console.log(JSON.stringify({ baseline: pointer.version, parkings: rows.length }));
    process.exit(0);
  }
  const [publicDir, ...options] = process.argv.slice(2);
  if (!publicDir) throw new Error('usage: validate-snapshot.mjs <public-dir> [--require-es-fr] [--baseline=<gz>]');
  console.log(JSON.stringify(await validateSnapshot(publicDir, options.includes('--require-es-fr'),
    options.find(arg => arg.startsWith('--baseline='))?.slice('--baseline='.length)), null, 2));
}
