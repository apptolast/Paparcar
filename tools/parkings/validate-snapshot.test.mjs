import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';
import { validateSnapshot } from './validate-snapshot.mjs';

async function fixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'parking-validation-'));
  const dir = join(root, 'parkings/v1/test');
  await mkdir(dir, { recursive: true });
  const entry = { id: 'osm/node/1', lat: 40.4168, lon: -3.7038, kind: 'SURFACE', fee: 'YES' };
  const manifest = { version: 'test', generatedAtMs: 1, cells: 1, parkings: 1, serve: true,
    counts: { '1499_-105': 1 } };
  const json = (path, value) => writeFile(path, JSON.stringify(value));
  await json(join(root, 'parkings/v1/latest.json'), manifest);
  await json(join(dir, 'index.json'), manifest);
  await json(join(dir, '1499_-105.json'), { cell: '1499_-105', version: 'test', parkings: [entry] });
  await writeFile(join(dir, 'parkings.ndjson.gz'), gzipSync(JSON.stringify(entry)+'\n'));
  try { await run(root, dir, json); } finally { await rm(root, { recursive: true }); }
}

test('should_acceptAConsistentArtifact', () => fixture(async root => {
  assert.equal((await validateSnapshot(root)).parkings, 1);
}));
test('should_rejectAPartialCountryPublication', () => fixture(async root => {
  await assert.rejects(validateSnapshot(root, true), /no coverage/);
}));
test('should_rejectAMissingListedCell', () => fixture(async (root, dir) => {
  await rm(join(dir, '1499_-105.json'));
  await assert.rejects(validateSnapshot(root), /ENOENT/);
}));
test('should_rejectABulkThatDoesNotMatchTheServedCells', () => fixture(async (root, dir) => {
  await writeFile(join(dir, 'parkings.ndjson.gz'), gzipSync('{"id":"osm/node/2"}\n'));
  await assert.rejects(validateSnapshot(root), /bulk mismatch/);
}));
test('should_rejectLossOfThePreviouslyPublishedBase', () => fixture(async (root, dir) => {
  const baseline = join(dir, 'previous.gz');
  await writeFile(baseline, gzipSync('{"id":"osm/node/1"}\n{"id":"osm/node/2"}\n'));
  await assert.rejects(validateSnapshot(root, false, baseline), /more than 2%/);
}));
test('should_acceptAnAdditiveExpansion', () => fixture(async (root, dir) => {
  const baseline = join(dir, 'previous.gz');
  await writeFile(baseline, gzipSync('{"id":"osm/node/1"}\n'));
  assert.deepEqual((await validateSnapshot(root, false, baseline)).preserved, { present: 1, previous: 1 });
}));
test('should_rejectAReplacedCellVersion', () => fixture(async (root, dir, json) => {
  await json(join(dir, '1499_-105.json'), { cell: '1499_-105', version: 'old', parkings: [] });
  await assert.rejects(validateSnapshot(root), /wrong cell\/version/);
}));
