import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ParkingSnapshotReader } from './parkingSnapshotReader';

const entry = { id: 'osm/way/1', lat: 48.85, lon: 2.35, kind: 'SURFACE', fee: 'UNKNOWN' };
const manifest = (version: string) => ({ version, cells: 1, parkings: 1, generatedAtMs: 1 });
const index = (version: string) => ({ ...manifest(version), counts: { '1812_57': 1 } });
const cell = (version: string) => ({ version, cell: '1812_57', parkings: [entry] });

function reader(answer: (path: string) => unknown) {
  const paths: string[] = [];
  const get = (async (url: string | URL | Request) => {
    const path = String(url).split('/parkings/v1/')[1]!;
    paths.push(path);
    const value = answer(path);
    return value === undefined ? new Response('', { status: 404 }) : Response.json(value);
  }) as typeof fetch;
  return { source: new ParkingSnapshotReader('https://example.test', get), paths };
}

test('should_readOnlyListedCells_when_theBlockIncludesEmptyLand', async () => {
  const { source, paths } = reader(path => ({
    'latest.json': manifest('new'), 'new/index.json': index('new'), 'new/1812_57.json': cell('new'),
  })[path]);
  const result = await source.readBlock(['1812_57', '1812_58'], 1);
  assert.equal(result.cells.length, 1);
  assert.ok(!paths.includes('new/1812_58.json'));
});

test('should_fetchTheLargeIndexOnce_when_coldRequestsArriveTogether', async () => {
  const { source, paths } = reader(path => ({
    'latest.json': manifest('new'), 'new/index.json': index('new'), 'new/1812_57.json': cell('new'),
  })[path]);
  await Promise.all(Array.from({ length: 80 }, () => source.readBlock(['1812_57'], 1)));
  assert.equal(paths.filter(path => path === 'new/index.json').length, 1);
});

test('should_fail_when_aListedCellIsMissingInsteadOfCallingItEmpty', async () => {
  const { source } = reader(path => ({
    'latest.json': manifest('new'), 'new/index.json': index('new'),
  })[path]);
  await assert.rejects(source.readBlock(['1812_57'], 1), /HTTP 404/);
});

test('should_retryTheWholeBlock_when_aDeployReplacesTheOldVersion', async () => {
  let reads = 0;
  const { source } = reader(path => path === 'latest.json'
    ? manifest(++reads === 1 ? 'old' : 'new')
    : ({ 'new/index.json': index('new'), 'new/1812_57.json': cell('new') })[path]);
  const result = await source.readBlock(['1812_57'], 1);
  assert.equal(result.manifest.version, 'new');
  assert.equal(result.cells[0]!.version, 'new');
});

test('should_notMixCachedOldCells_when_aWarmInstanceSeesADeploy', async () => {
  let deployed = false;
  const fullIndex = (version: string) => ({
    ...manifest(version), cells: 2, parkings: 2, counts: { '1812_57': 1, '1812_58': 1 },
  });
  const { source } = reader(path => {
    if (path === 'latest.json') return { ...manifest(deployed ? 'new' : 'old'), cells: 2, parkings: 2 };
    if (path === 'old/index.json') return fullIndex('old');
    if (path === 'old/1812_57.json' && !deployed) return cell('old');
    if (path === 'new/index.json') return fullIndex('new');
    if (path === 'new/1812_57.json') return cell('new');
    if (path === 'new/1812_58.json') return { ...cell('new'), cell: '1812_58' };
    return undefined;
  });
  await source.readBlock(['1812_57'], 1);
  deployed = true;
  const result = await source.readBlock(['1812_57', '1812_58'], 2);
  assert.equal(result.cells.length, 2);
  assert.ok(result.cells.every(file => file.version === 'new'));
});

test('should_rejectAMismatchedCell_when_theServerReturnsTheWrongVersion', async () => {
  const { source } = reader(path => ({
    'latest.json': manifest('new'), 'new/index.json': index('new'), 'new/1812_57.json': cell('old'),
  })[path]);
  await assert.rejects(source.readBlock(['1812_57'], 1), /invalid snapshot cell/);
});

test('should_notNeedAnIndex_when_theKillSwitchIsOff', async () => {
  const { source, paths } = reader(() => ({ ...manifest('new'), serve: false }));
  assert.deepEqual((await source.readBlock(['1812_57'], 1)).cells, []);
  assert.deepEqual(paths, ['latest.json']);
});

test('should_rejectAnIncompleteIndex_when_itsCountsDisagree', async () => {
  const { source } = reader(path => path === 'latest.json'
    ? manifest('new') : { ...index('new'), counts: {} });
  await assert.rejects(source.readBlock(['1812_57'], 1), /invalid snapshot index/);
});
