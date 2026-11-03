import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  CACHE_LIVE_SECONDS,
  CACHE_STATIC_SECONDS,
  cacheControlFor,
  hasPerishable,
  responseFor,
  type ApiResponse,
  type SnapshotCell,
  type SnapshotEntry,
  type SnapshotManifest,
} from './parkingsCell';

/**
 * [PARKINGS-ONE-CALL-MERGED-001]
 *
 * Casi todo negativas, como en el resto de `functions/`: lo que hay que impedir es **contestar de
 * menos sin decirlo** — una celda que falta leída como «aquí no hay parkings», un parking repetido
 * leído como dos parkings, un TTL de un día puesto sobre una cifra que caduca en minutos.
 */

function entry(over: Partial<SnapshotEntry> = {}): SnapshotEntry {
  return {
    id: 'osm/way/1',
    lat: 40.4168,
    lon: -3.7038,
    kind: 'SURFACE',
    fee: 'UNKNOWN',
    ...over,
  };
}

function cell(id: string, parkings: SnapshotEntry[]): SnapshotCell {
  return { cell: id, version: '2026-W45', parkings };
}

function manifest(over: Partial<SnapshotManifest> = {}): SnapshotManifest {
  return { version: '2026-W45', generatedAtMs: 1, cells: 1, parkings: 1, ...over };
}

const NOW = 1_762_000_000_000;

test('junta los parkings de todas las celdas del bloque', () => {
  const response = responseFor(
    '1499_-105',
    manifest(),
    [cell('1499_-105', [entry()]), cell('1499_-104', [entry({ id: 'osm/way/2' })])],
    NOW,
  );
  assert.deepEqual(response.parkings.map((p) => p.id), ['osm/way/1', 'osm/way/2']);
  assert.equal(response.cell, '1499_-105');
  assert.equal(response.baseVersion, '2026-W45');
});

test('should_countAParkingOnce_when_twoCellsBringIt', () => {
  // El bloque es deliberadamente generoso en los bordes, asi que un id puede llegar dos veces. Dos
  // registros del mismo parking se verian como DOS parkings en el mismo sitio: el fallo que parece
  // un dato.
  const response = responseFor(
    '1499_-105',
    manifest(),
    [cell('1499_-105', [entry()]), cell('1499_-104', [entry()])],
    NOW,
  );
  assert.equal(response.parkings.length, 1);
});

test('should_answerEmpty_when_theBlockHasNoFiles', () => {
  // La mayoria del territorio no tiene parkings, asi que su fichero NO existe. Eso es la respuesta
  // correcta, no un error — y el puntero es lo que distingue «vacia» de «sin publicar».
  const response = responseFor('1520_-102', manifest(), [], NOW);
  assert.deepEqual(response.parkings, []);
  assert.equal(response.serve, true);
  assert.equal(response.baseVersion, '2026-W45');
});

test('should_keepServing_when_thePointerSaysNothingAboutIt', () => {
  // Un puntero viejo, de antes de que existiera la bandera, NO puede apagar el carril.
  assert.equal(responseFor('c', manifest(), [], NOW).serve, true);
});

test('should_sendTheClientBackToOverpass_when_thePointerSaysSoExplicitly', () => {
  assert.equal(responseFor('c', manifest({ serve: false }), [], NOW).serve, false);
  // Y `true` explicito tambien sirve, para que el fichero pueda decirlo sin ambiguedad.
  assert.equal(responseFor('c', manifest({ serve: true }), [], NOW).serve, true);
});

test('el orden es estable por id, para que dos respuestas iguales sean el mismo byte', () => {
  const ids = ['osm/way/3', 'osm/node/9', 'osm/relation/1'];
  const response = responseFor('c', manifest(), [cell('c', ids.map((id) => entry({ id })))], NOW);
  assert.deepEqual(
    response.parkings.map((p) => p.id),
    ['osm/node/9', 'osm/relation/1', 'osm/way/3'],
  );
});

test('acredita a OpenStreetMap siempre que sirve su base', () => {
  const response = responseFor('c', manifest(), [cell('c', [entry()])], NOW);
  assert.deepEqual(response.licences.map((l) => l.id), ['odbl-1.0']);
  assert.match(response.licences[0]!.attribution, /OpenStreetMap/);
});

test('should_notPromiseAFusion_when_thereIsOnlyOneSource', () => {
  // `mergedFrom` solo aparece cuando ha habido fusion. Con una fuente seria siempre «[su propio
  // id]»: ~25 B por registro, casi el doble de payload para no decir nada.
  const response = responseFor('c', manifest(), [cell('c', [entry()])], NOW);
  assert.equal(response.parkings[0]!.mergedFrom, undefined);
});

test('⛔ el TTL lo manda el dato mas perecedero, no el endpoint', () => {
  const stat = cacheControlFor(false);
  const live = cacheControlFor(true);
  assert.match(stat, new RegExp(`s-maxage=${CACHE_STATIC_SECONDS}`));
  assert.match(live, new RegExp(`s-maxage=${CACHE_LIVE_SECONDS}`));
  assert.ok(CACHE_LIVE_SECONDS < CACHE_STATIC_SECONDS);
  // Publica y cacheable por el CDN en los dos casos: una respuesta `private` no la cachea nadie y
  // la funcion correria una vez POR USUARIO.
  assert.match(stat, /^public,/);
  assert.match(live, /^public,/);
});

test('⛔ el s-maxage estatico acota la MARCHA ATRAS, no solo el coste', () => {
  // Mientras el CDN tenga la respuesta cacheada la sigue sirviendo aunque la funcion se borre, el
  // puntero diga `serve: false` y el rewrite desaparezca. Asi que este numero es el suelo de
  // cuanto tardas en retirar algo, y por eso NO es un dia.
  assert.ok(
    CACHE_STATIC_SECONDS <= 3600,
    `con ${CACHE_STATIC_SECONDS}s de CDN, apagar el carril tarda ${CACHE_STATIC_SECONDS / 3600} h`,
  );
  // Y el `max-age` del cliente no puede ser mayor que el del CDN: seria una copia en el movil que
  // sobrevive a la del CDN, o sea una marcha atras aun mas lenta y sin forma de medirla.
  const maxAge = Number(/max-age=(\d+)/.exec(cacheControlFor(false))![1]);
  assert.ok(maxAge <= CACHE_STATIC_SECONDS, `max-age=${maxAge} > s-maxage=${CACHE_STATIC_SECONDS}`);
});

test('hoy nada caduca, y con ocupacion si', () => {
  const base = responseFor('c', manifest(), [cell('c', [entry()])], NOW);
  assert.equal(hasPerishable(base), false);

  const withLive: ApiResponse = {
    ...base,
    parkings: [
      {
        ...entry(),
        availability: {
          free: 42,
          updatedAt: NOW,
          source: 'madrid',
          sourceKind: 'OFFICIAL',
          licence: 'cc-by-4.0',
        },
      },
    ],
  };
  assert.equal(hasPerishable(withLive), true);
});
