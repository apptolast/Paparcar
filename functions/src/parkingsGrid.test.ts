import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { blockAround, cellId, centreLatitude, centreLongitude, zoneOf } from './parkingsGrid';

/**
 * **El testigo cruzado de la rejilla.** [PARKINGS-ONE-CALL-MERGED-001]
 *
 * `tools/parkings/grid-fixtures.json` lo GENERA la rejilla viva de Kotlin
 * (`:shared:parkingSnapshotTool --grid-fixtures`) y lo vigila por allí
 * `ParkingGridFixtureGuardrailTest`. Esto es la otra mitad: si este puerto de TypeScript se desvía
 * de la rejilla de Kotlin —una constante, un `floor` por un `ceil`, el orden del bloque— se pone
 * rojo aquí, y si alguien cambia la rejilla y no regenera la tabla se pone rojo allí.
 *
 * ⛔ Es el único sitio de `functions/` con esta obligación, y existe porque el invariante vive en
 * dos idiomas. La trampa que evita ya se pagó una vez en este repo con `workData`: dos copias de
 * una lista de campos que derivaron en silencio.
 *
 * ⚠️ Los diez puntos no son decorativos: cubren los dos hemisferios, el meridiano cero, el ecuador
 * y el paralelo 89 — donde el paso de longitud se dispara y el clamp de la rejilla entra en juego.
 */

interface Sample {
  lat: number;
  lon: number;
  cell: string;
  centreLat: number;
  centreLon: number;
  block: string[];
}

const FIXTURES = join(__dirname, '..', '..', 'tools', 'parkings', 'grid-fixtures.json');

/** 1e-9 grados son ~0,1 mm: absorbe la diferencia de impresion, no una rejilla distinta. */
const TOLERANCE_DEGREES = 1e-9;

function samples(): Sample[] {
  const raw = readFileSync(FIXTURES, 'utf8');
  const parsed = JSON.parse(raw) as { samples: Sample[] };
  assert.equal(parsed.samples.length, 10, 'la tabla tiene que traer los diez puntos');
  return parsed.samples;
}

test('cada punto de la tabla cae en la misma celda que dice Kotlin', () => {
  for (const sample of samples()) {
    const zone = zoneOf(sample.cell);
    assert.ok(zone, `'${sample.cell}' tiene que ser un nombre de celda valido`);
    assert.equal(cellId(zone), sample.cell);
  }
});

test('el centro de la celda se deriva igual que en Kotlin', () => {
  for (const sample of samples()) {
    const zone = zoneOf(sample.cell)!;
    assert.ok(
      Math.abs(centreLatitude(zone) - sample.centreLat) < TOLERANCE_DEGREES,
      `${sample.cell}: latitud del centro ${centreLatitude(zone)} != ${sample.centreLat}`,
    );
    assert.ok(
      Math.abs(centreLongitude(zone) - sample.centreLon) < TOLERANCE_DEGREES,
      `${sample.cell}: longitud del centro ${centreLongitude(zone)} != ${sample.centreLon}`,
    );
  }
});

test('el bloque es el mismo, Y EN EL MISMO ORDEN, que el de Kotlin', () => {
  // El orden cuenta: es el que decide en que orden se leen las celdas y en que orden se fusionan,
  // asi que una discrepancia de orden es una discrepancia de resultado el dia que dos fuentes
  // escriban el mismo campo.
  for (const sample of samples()) {
    const zone = zoneOf(sample.cell)!;
    assert.deepEqual(
      blockAround(zone).map(cellId),
      sample.block,
      `${sample.cell}: el bloque no coincide con el de Kotlin`,
    );
  }
});

test('un nombre de celda que no es uno se rechaza, no se adivina', () => {
  // ⛔ Esto lee un valor de la QUERY STRING y lo convierte en clave de cache. Un parse indulgente
  // sirve los parkings de una celda bajo el nombre de otra y nadie aguas abajo podria notarlo.
  for (const bad of ['', '_', '1_', '_1', '1', 'a_b', '1_2_', '01_2', '1_+2', ' 1_2', '1_2.0', '-0_1']) {
    assert.equal(zoneOf(bad), null, `'${bad}' no es un nombre de celda`);
  }
});

test('el bloque incluye su propia celda y no repite ninguna', () => {
  for (const sample of samples()) {
    const zone = zoneOf(sample.cell)!;
    const block = blockAround(zone).map(cellId);
    assert.ok(block.includes(sample.cell), `${sample.cell}: el bloque tiene que incluirse`);
    assert.equal(new Set(block).size, block.length, `${sample.cell}: una celda se lee dos veces`);
  }
});
