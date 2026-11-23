import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { osmFootprintMatch } from './osm_footprint.mjs';

const LABELS = ['stationStop', 'streetParking', 'passThrough'];

function nonNegative(value) {
    return Number.isFinite(value) && value >= 0;
}

function validateSample(samples) {
    if (!Array.isArray(samples) || samples.length === 0) {
        throw new Error('samples must contain at least one labelled observation');
    }

    for (const [index, sample] of samples.entries()) {
        if (!sample || !LABELS.includes(sample.label) ||
            !nonNegative(sample.distanceMeters) ||
            !nonNegative(sample.durationSeconds) ||
            !(sample.accuracyMeters === null || nonNegative(sample.accuracyMeters)) ||
            typeof sample.publishedSpot !== 'boolean') {
            throw new Error(`Invalid observation at index ${index}`);
        }
    }
}

export function compareCandidates({ samples, rules }) {
    validateSample(samples);
    if (!Array.isArray(rules) || rules.length === 0) {
        throw new Error('rules must contain at least one candidate rule');
    }

    const observations = samples.map(sample => ({
        ...sample,
        footprintMatch: osmFootprintMatch(sample.fix, sample.accuracyMeters, sample.osmFootprint),
    }));

    return rules.map((rule, index) => {
        if (!rule || !nonNegative(rule.maxDistanceMeters) ||
            !nonNegative(rule.minDurationSeconds) ||
            !(rule.maxAccuracyMeters === null || nonNegative(rule.maxAccuracyMeters)) ||
            (rule.requireOsmInterior !== undefined && typeof rule.requireOsmInterior !== 'boolean')) {
            throw new Error(`Invalid rule at index ${index}`);
        }

        const byLabel = Object.fromEntries(LABELS.map(label => [
            label, { total: 0, candidates: 0, publishedCandidates: 0 },
        ]));

        const footprintMatches = { inside: 0, boundary: 0, outside: 0, unknown: 0 };
        for (const sample of observations) {
            footprintMatches[sample.footprintMatch] += 1;
            const counts = byLabel[sample.label];
            counts.total += 1;

            const passesAccuracy = rule.maxAccuracyMeters === null ||
                (sample.accuracyMeters !== null && sample.accuracyMeters <= rule.maxAccuracyMeters);
            const passesFootprint = !rule.requireOsmInterior || sample.footprintMatch === 'inside';
            if (passesAccuracy && passesFootprint && sample.distanceMeters <= rule.maxDistanceMeters &&
                sample.durationSeconds >= rule.minDurationSeconds) {
                counts.candidates += 1;
                if (sample.publishedSpot) counts.publishedCandidates += 1;
            }
        }

        return {
            rule: {
                maxDistanceMeters: rule.maxDistanceMeters,
                minDurationSeconds: rule.minDurationSeconds,
                maxAccuracyMeters: rule.maxAccuracyMeters,
                requireOsmInterior: rule.requireOsmInterior ?? false,
            },
            footprintMatches,
            byLabel,
            potentialPrompts: Object.values(byLabel).reduce((sum, row) => sum + row.candidates, 0),
            falsePublicationsPotentiallyPrevented: byLabel.stationStop.publishedCandidates,
            streetPublicationsPotentiallyWithheld: byLabel.streetParking.publishedCandidates,
        };
    });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        const path = process.argv[2];
        if (!path || process.argv.length !== 3) {
            throw new Error('Usage: node tools/refuel-stops/compare_candidates.mjs SAMPLE.json');
        }
        const input = JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''));
        process.stdout.write(JSON.stringify(compareCandidates(input), null, 2) + '\n');
    } catch (error) {
        // Parser and I/O errors can include raw input or the local filename.
        const message = error instanceof SyntaxError ? 'Invalid sample JSON' :
            error.code ? 'Could not read the sample' :
            error instanceof TypeError ? 'Expected an object containing samples and rules' : error.message;
        process.stderr.write(message + '\n');
        process.exitCode = 1;
    }
}
