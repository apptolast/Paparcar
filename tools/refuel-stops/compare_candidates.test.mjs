import assert from 'node:assert/strict';
import { test } from 'node:test';
import { compareCandidates } from './compare_candidates.mjs';
import { osmFootprintMatch } from './osm_footprint.mjs';

const outer = [[0, 0], [0.001, 0], [0.001, 0.001], [0, 0.001], [0, 0]];
const footprint = { type: 'Polygon', coordinates: [outer] };

test('requires the accuracy circle to fit within the mapped station', () => {
    assert.equal(osmFootprintMatch([0.0005, 0.0005], 10, footprint), 'inside');
    assert.equal(osmFootprintMatch([0.00002, 0.0005], 10, footprint), 'boundary');
    assert.equal(osmFootprintMatch([-0.00002, 0.0005], 10, footprint), 'boundary');
    assert.equal(osmFootprintMatch([-0.0003, 0.0005], 10, footprint), 'outside');
    assert.equal(osmFootprintMatch([0.0005, 0.0005], null, footprint), 'unknown');
    assert.equal(osmFootprintMatch([0.0005, 0.0005], 10, { type: 'Point' }), 'unknown');
});

test('excludes holes in multipolygon footprints', () => {
    const hole = [[0.0004, 0.0004], [0.0006, 0.0004], [0.0006, 0.0006], [0.0004, 0.0006], [0.0004, 0.0004]];
    const multipolygon = { type: 'MultiPolygon', coordinates: [[outer, hole]] };

    assert.equal(osmFootprintMatch([0.0005, 0.0005], 5, multipolygon), 'outside');
    assert.equal(osmFootprintMatch([0.0002, 0.0002], 5, multipolygon), 'inside');
});

test('compares an OSM footprint requirement with a radius-only rule', () => {
    const common = { distanceMeters: 16, durationSeconds: 180, accuracyMeters: 10, publishedSpot: true };
    const samples = [
        { ...common, label: 'stationStop', fix: [0.0005, 0.0005], osmFootprint: footprint },
        { ...common, label: 'streetParking', fix: [-0.00002, 0.0005], osmFootprint: footprint },
        { ...common, label: 'streetParking' },
    ];
    const [radius, polygon] = compareCandidates({ samples, rules: [rule, { ...rule, requireOsmInterior: true }] });

    assert.equal(radius.streetPublicationsPotentiallyWithheld, 2);
    assert.equal(polygon.streetPublicationsPotentiallyWithheld, 0);
    assert.equal(polygon.falsePublicationsPotentiallyPrevented, 1);
    assert.deepEqual(polygon.footprintMatches, { inside: 1, boundary: 1, outside: 0, unknown: 1 });
});

const rule = { maxDistanceMeters: 20, minDurationSeconds: 60, maxAccuracyMeters: 10 };

test('counts an adjacent street space separately from a false station publication', () => {
    const samples = [
        { label: 'stationStop', distanceMeters: 20, durationSeconds: 60, accuracyMeters: 10, publishedSpot: true },
        { label: 'stationStop', distanceMeters: 12, durationSeconds: 90, accuracyMeters: 6, publishedSpot: false },
        { label: 'streetParking', distanceMeters: 18, durationSeconds: 180, accuracyMeters: 8, publishedSpot: true },
        { label: 'passThrough', distanceMeters: 0, durationSeconds: 0, accuracyMeters: 5, publishedSpot: false },
    ];

    const [result] = compareCandidates({ samples, rules: [rule] });

    assert.equal(result.potentialPrompts, 3);
    assert.equal(result.falsePublicationsPotentiallyPrevented, 1);
    assert.equal(result.streetPublicationsPotentiallyWithheld, 1);
    assert.deepEqual(result.byLabel.passThrough, { total: 1, candidates: 0, publishedCandidates: 0 });
});

test('keeps unknown accuracy out of rules that require a measured fix', () => {
    const samples = [
        { label: 'stationStop', distanceMeters: 10, durationSeconds: 180, accuracyMeters: null, publishedSpot: true },
    ];
    const [strict, open] = compareCandidates({
        samples,
        rules: [rule, { ...rule, maxAccuracyMeters: null }],
    });

    assert.equal(strict.potentialPrompts, 0);
    assert.equal(open.potentialPrompts, 1);
});

test('rejects invalid input without repeating observation contents', () => {
    assert.throws(() => compareCandidates({
        samples: [{ label: 'unexpected private value' }],
        rules: [rule],
    }), /^Error: Invalid observation at index 0$/);
});
