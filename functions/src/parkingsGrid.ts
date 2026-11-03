/**
 * The cell grid, ported from `ParkingZoneGrid.kt`. [PARKINGS-ONE-CALL-MERGED-001]
 *
 * ⛔ **This is the one invariant in this repo that lives in two languages**, and it is here because
 * the merging endpoint cannot do its job without it: cells **partition** the territory, so
 * answering about one cell means reading the ones around it. Kotlin stays the owner — it is what
 * writes the snapshot and what the app asks with.
 *
 * ⛔ **It is therefore the only module here with a MANDATORY cross-language witness.** The numbers
 * below are not to be re-derived, tuned or "improved": `tools/parkings/grid-fixtures.json` is
 * generated from the live Kotlin grid and `parkingsGrid.test.ts` checks this port against it, cell
 * by cell, centre by centre and **block by block, in order**. If a constant drifts on either side,
 * both sides go red. That is the whole point — the trap this repo already paid for once with
 * `workData`, where two copies of a field list drifted in silence.
 */

/** 3 km on a side. Bigger cells are counter-intuitively cheaper: they are fetched far less often. */
const CELL_METERS = 3000;

/** A degree of latitude is the same length everywhere; a degree of longitude is not. */
const METERS_PER_DEGREE_LAT = 111320;

/** `cos(80°)` — clamped so a broken fix cannot produce a cell wider than a continent. */
const MIN_METERS_PER_DEGREE_LON = 111320 * 0.17364818;

const HALF = 0.5;

/** What the lane promises to show around the user. Mirrors `PublicParkingsQuery`. */
const DEFAULT_RADIUS_METERS = 3000;

/**
 * How far from a cell's centre an answer about that cell has to reach: the furthest anybody can
 * stand from the centre is **half a cell** on each axis, and from there they are still promised
 * [DEFAULT_RADIUS_METERS].
 */
const BLOCK_REACH_METERS = CELL_METERS * HALF + DEFAULT_RADIUS_METERS;

const LAT_STEP = CELL_METERS / METERS_PER_DEGREE_LAT;

function metersPerDegreeLonAt(latitude: number): number {
  return Math.max(
    MIN_METERS_PER_DEGREE_LON,
    METERS_PER_DEGREE_LAT * Math.cos((Math.abs(latitude) * Math.PI) / 180),
  );
}

function lonStepAt(latitude: number): number {
  return CELL_METERS / metersPerDegreeLonAt(latitude);
}

/** A cell, identified by two integers with its centre DERIVED — never the other way round. */
export interface Zone {
  readonly bandIndex: number;
  readonly columnIndex: number;
}

export function cellId(zone: Zone): string {
  return `${zone.bandIndex}_${zone.columnIndex}`;
}

export function centreLatitude(zone: Zone): number {
  return (zone.bandIndex + HALF) * LAT_STEP;
}

export function centreLongitude(zone: Zone): number {
  return (zone.columnIndex + HALF) * lonStepAt(centreLatitude(zone));
}

/**
 * A zone back from its id, or null when the text is not one.
 *
 * ⛔ **Strict on purpose**, same as the Kotlin side: this reads a value off a QUERY STRING, so
 * anything it cannot account for has to be a miss rather than a guess. A lenient parse here serves
 * one cell's car parks under another cell's name, and nothing downstream could tell.
 */
export function zoneOf(id: string): Zone | null {
  const separator = id.lastIndexOf('_');
  if (separator <= 0 || separator === id.length - 1) return null;
  const band = asInteger(id.slice(0, separator));
  const column = asInteger(id.slice(separator + 1));
  if (band === null || column === null) return null;
  const zone: Zone = { bandIndex: band, columnIndex: column };
  // The round trip is the check: it rejects `01_2`, `+1_2`, ` 1_2` and anything else that would
  // spell the same cell a second way — which is exactly what must not reach a cache key.
  return cellId(zone) === id ? zone : null;
}

function asInteger(text: string): number | null {
  if (!/^-?(0|[1-9][0-9]*)$/.test(text)) return null;
  const value = Number(text);
  return Number.isSafeInteger(value) ? value : null;
}

/**
 * The cells whose contents answer about [zone].
 *
 * ⛔ **NOT a square of rings**, and the obvious version of that loses car parks — measured on the
 * Kotlin side: 35 sample points outside the block across nine cities, 12 of them at Svalbard. Two
 * structural reasons:
 *
 * - A column's width is derived from **its own band's** centre latitude, so the grids of two
 *   neighbouring bands are OFFSET and the asked cell's centre can sit up to half a cell off the
 *   middle of a neighbouring band's column.
 * - A degree of longitude is worth fewer metres further from the equator, so the reach in DEGREES is
 *   larger at the far edge of the block than at its centre.
 *
 * So the block comes from the **geographic box** the answer must cover, with the longitude delta
 * taken at the box's worst latitude and the columns resolved **per band**. Deliberately generous at
 * the edges (`floor` on both ends): reading one file too many is cheap, and a block with a hole in
 * it looks exactly like "there are no car parks here".
 */
export function blockAround(zone: Zone): Zone[] {
  const centreLat = centreLatitude(zone);
  const centreLon = centreLongitude(zone);
  const latDelta = BLOCK_REACH_METERS / METERS_PER_DEGREE_LAT;
  const firstBand = Math.floor((centreLat - latDelta) / LAT_STEP);
  const lastBand = Math.floor((centreLat + latDelta) / LAT_STEP);
  // The most expensive degree of longitude in the box: the latitude furthest from the equator, where
  // a metre is worth most degrees. Taking it at the centre is what left holes.
  const worstLatitude = Math.max(Math.abs(centreLat - latDelta), Math.abs(centreLat + latDelta));
  const lonDelta = BLOCK_REACH_METERS / metersPerDegreeLonAt(worstLatitude);

  const block: Zone[] = [];
  for (let band = firstBand; band <= lastBand; band++) {
    const step = lonStepAt((band + HALF) * LAT_STEP);
    const firstColumn = Math.floor((centreLon - lonDelta) / step);
    const lastColumn = Math.floor((centreLon + lonDelta) / step);
    for (let column = firstColumn; column <= lastColumn; column++) {
      block.push({ bandIndex: band, columnIndex: column });
    }
  }
  return block;
}
