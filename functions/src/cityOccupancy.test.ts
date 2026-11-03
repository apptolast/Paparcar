import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  MADRID_LICENCE,
  MERGE_RADIUS_METERS,
  fuseAvailability,
  metresBetween,
  isWorthWriting,
  parseMadrid,
  toStored,
  type CityMeasurement,
  type Placed,
} from './cityOccupancy';

/**
 * [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * ⛔ **Contra la respuesta REAL de Madrid**, capturada el 03-11-2026, no contra un ejemplo escrito
 * para pasar. Y el grueso son negativas, porque lo que hay que impedir es lo de siempre: **decir de
 * más sin que se note** — un parking sin ocupación presentado como «completo», una medida pegada al
 * parking de al lado, una cifra nuestra presentada como si la hubiera sellado la ciudad.
 */

const FIXTURE = join(__dirname, 'fixtures', 'madrid-getlistparking.xml');
const FETCHED_AT = 1_762_000_000_000;

function madrid(): CityMeasurement[] {
  return parseMadrid(readFileSync(FIXTURE, 'utf8'), FETCHED_AT);
}

test('saca las 24 medidas de la respuesta real de Madrid', () => {
  const measurements = madrid();
  // ⛔ El numero es el testigo de la FORMA: si Madrid reestructura su respuesta, esto cae y lo canta
  // — que es el precio asumido de extraer por expresion regular en vez de con un parser general.
  assert.equal(measurements.length, 24);
  assert.ok(measurements.every((m) => m.source === 'madrid'));
  assert.ok(measurements.every((m) => m.licence === MADRID_LICENCE));
  assert.ok(measurements.every((m) => m.key.startsWith('madrid_')));
});

test('should_ignoreAParking_when_itPublishesNoOccupancy', () => {
  // La respuesta real trae 75 elementos y solo 24 con ocupacion. Los otros 51 son POI: meterlos con
  // `free = 0` seria decir «completo» de un parking que no ha dicho nada.
  const raw = readFileSync(FIXTURE, 'utf8');
  const elements = raw.match(/<ns2:lstParking>/g)?.length ?? 0;
  assert.equal(elements, 75, 'el fixture tiene que seguir siendo la respuesta entera');
  assert.equal(madrid().length, 24);
});

test('cada medida trae su punto, su cifra y su sello DE LA FUENTE', () => {
  for (const m of madrid()) {
    assert.ok(Number.isFinite(m.latitude) && Number.isFinite(m.longitude), `${m.key} sin punto`);
    assert.ok(Number.isInteger(m.free) && m.free >= 0, `${m.key} con libres raros: ${m.free}`);
    // ⛔ Madrid SELLA cada parking, asi que `updatedAtIsFetch` tiene que ser false: presentar
    // nuestra hora de lectura como si fuera la de la ciudad es dar una precision que no se tiene.
    assert.equal(m.updatedAtIsFetch, false, `${m.key} deberia traer su propio moment`);
    assert.notEqual(m.updatedAt, FETCHED_AT, `${m.key} se quedo con NUESTRA hora`);
  }
});

test('⛔ un CERO es un dato, no una ausencia', () => {
  // `Jacinto Benavente: 0` esta en la respuesta real. Un parser que tratara el 0 como «sin dato»
  // perderia exactamente el caso que la UI tiene que pintar como «Completo».
  const zeros = madrid().filter((m) => m.free === 0);
  assert.ok(zeros.length >= 1, 'el fixture trae al menos un parking lleno; tiene que sobrevivir');
});

test('should_fallBackToOurClock_when_theSourceDoesNotStampItsData', () => {
  // El caso de Malaga, sin su CSV: una fuente sin `moment`. El sello pasa a ser el nuestro y la
  // medida TIENE que decirlo.
  const noStamp = `
    <lstParking><id>9</id><name>Sin sello</name><latitude>40.4</latitude><longitude>-3.7</longitude>
      <lstOccupation><free>7</free></lstOccupation></lstParking>`;
  const [m] = parseMadrid(noStamp, FETCHED_AT);
  assert.equal(m!.free, 7);
  assert.equal(m!.updatedAt, FETCHED_AT);
  assert.equal(m!.updatedAtIsFetch, true, 'sin sello de la fuente hay que decir que el sello es nuestro');
});

// ─── La fusión, falsificada en LAS DOS direcciones ───────────────────────────

const SOL: Placed = { id: 'osm/way/1', lat: 40.41700, lon: -3.70350 };

function measurement(over: Partial<CityMeasurement> = {}): CityMeasurement {
  return {
    key: 'madrid_1',
    source: 'madrid',
    name: 'Prueba',
    latitude: 40.41700,
    longitude: -3.70350,
    free: 42,
    updatedAt: FETCHED_AT,
    updatedAtIsFetch: false,
    licence: MADRID_LICENCE,
    ...over,
  };
}

test('pega la medida al parking que tiene encima', () => {
  const { matched, unmatched, ambiguous } = fuseAvailability([SOL], [measurement()]);
  assert.equal(matched.get('osm/way/1')?.free, 42);
  assert.equal(unmatched.length, 0);
  assert.equal(ambiguous.length, 0);
});

test('⛔ DIRECCIÓN 1 · no se pierde un par que está claramente ahí', () => {
  // ~25 m al norte: dentro del radio y sin competencia. Si esto se quedara sin pegar, la ocupacion
  // de Madrid simplemente no aparecería, y en pantalla se leería como que ese parking no la publica.
  const near = measurement({ latitude: SOL.lat + 0.000225 });
  assert.ok(metresBetween(near.latitude, near.longitude, SOL.lat, SOL.lon) < MERGE_RADIUS_METERS);
  const { matched } = fuseAvailability([SOL], [near]);
  assert.equal(matched.size, 1, 'un par evidente no se puede quedar sin fusionar');
});

test('⛔ DIRECCIÓN 2 · NO se fusionan dos instalaciones distintas: ante la duda, ninguna', () => {
  // Dos parkings de la base a 30 m uno del otro —un subterraneo y uno de varias plantas en el centro
  // de Madrid— y una sola medida entre ambos. Elegir el mas cercano atribuiria las plazas libres a
  // uno de los dos con una moneda al aire: un hecho inventado que ADEMAS parece un dato.
  const other: Placed = { id: 'osm/way/2', lat: SOL.lat + 0.00027, lon: SOL.lon };
  const between = measurement({ latitude: SOL.lat + 0.000135 });
  const { matched, ambiguous } = fuseAvailability([SOL, other], [between]);
  assert.equal(matched.size, 0, 'con dos candidatos no se fusiona');
  assert.equal(ambiguous.length, 1, 'y se CUENTA, no se tira en silencio');
});

test('should_leaveTheMeasurementUnmatched_when_nothingIsNearEnough', () => {
  // Un parking municipal que OSM no tiene. Se cuenta como `unmatched`: es el numero que dice cuanto
  // de la ocupacion publicada se esta quedando fuera del mapa.
  const far = measurement({ latitude: SOL.lat + 0.01 });
  const { matched, unmatched } = fuseAvailability([SOL], [far]);
  assert.equal(matched.size, 0);
  assert.equal(unmatched.length, 1);
});

test('un parking de la base recibe COMO MÁXIMO una medida', () => {
  const a = measurement({ key: 'madrid_a' });
  const b = measurement({ key: 'madrid_b', latitude: SOL.lat + 0.00009 });
  const { matched, unmatched, ambiguous } = fuseAvailability([SOL], [a, b]);
  assert.equal(matched.size, 1, 'no se pueden pegar dos cifras de libres al mismo parking');
  assert.equal(unmatched.length + ambiguous.length, 1);
});

test('el resultado no depende del orden en que llegue el feed', () => {
  const near: Placed = { id: 'osm/way/near', lat: 40.41700, lon: -3.70350 };
  const far: Placed = { id: 'osm/way/far', lat: 40.41730, lon: -3.70350 };
  const onNear = measurement({ key: 'madrid_on_near', latitude: near.lat, longitude: near.lon });
  const onFar = measurement({ key: 'madrid_on_far', latitude: far.lat, longitude: far.lon });

  const forward = fuseAvailability([near, far], [onNear, onFar]).matched;
  const backward = fuseAvailability([near, far], [onFar, onNear]).matched;
  assert.deepEqual(
    [...forward.entries()].map(([k, v]) => [k, v.key]).sort(),
    [...backward.entries()].map(([k, v]) => [k, v.key]).sort(),
    'el orden del feed no puede cambiar quien se queda con que medida',
  );
});

// ─── Escribir sólo si cambia ─────────────────────────────────────────────────

test('⛔ dos ciclos con el MISMO dato son UNA escritura', () => {
  // Lo que mantiene esto dentro de la capa gratuita de Firestore (20.000 escrituras/DÍA): a 3 min
  // reescribiendo los 24 de Madrid eran 11.520 diarias para repetir lo que ya estaba guardado.
  const first = toStored(measurement());
  assert.equal(isWorthWriting(undefined, first), true, 'la primera vez siempre se escribe');
  assert.equal(isWorthWriting(first, toStored(measurement())), false, 'el mismo dato no se reescribe');
});

test('un recuento nuevo, o el mismo recuento RE-SELLADO, sí se escriben', () => {
  const before = toStored(measurement({ free: 42, updatedAt: 1000 }));
  assert.equal(isWorthWriting(before, toStored(measurement({ free: 41, updatedAt: 1000 }))), true);
  // Mismo numero con sello nuevo SI es informacion: dice que la cifra sigue viva, no congelada.
  assert.equal(isWorthWriting(before, toStored(measurement({ free: 42, updatedAt: 2000 }))), true);
});

test('should_followTheParking_when_theSourceMovesOrRenamesIt', () => {
  // Si no, la fusión <40 m seguiría usando el punto viejo para siempre.
  const before = toStored(measurement());
  assert.equal(isWorthWriting(before, toStored(measurement({ latitude: 40.5 }))), true);
  assert.equal(isWorthWriting(before, toStored(measurement({ name: 'Otro nombre' }))), true);
});

test('⛔ lo que se guarda no lleva NI UN undefined: Firestore rechaza el documento entero', () => {
  // Pasó en producción el 03-11-2026: `total: undefined` tumbó TODOS los ciclos del scheduler
  // durante horas, y el único síntoma visible fue que la cifra nunca apareció — la función
  // registraba su error y nadie lee logs. Los tests unitarios lo dejaban pasar porque no tocan
  // Firestore, así que el invariante necesita su propio testigo.
  for (const m of [...madrid(), measurement(), measurement({ total: 587 })]) {
    const stored = toStored(m) as unknown as Record<string, unknown>;
    for (const [key, value] of Object.entries(stored)) {
      assert.notEqual(value, undefined, `${m.key}: '${key}' viaja como undefined`);
    }
  }
});

test('⛔ Madrid no publica el aforo en GetListParking, y no se inventa', () => {
  // Dentro de `<lstOccupation>` hay `code`, `free`, `moment`, `name` y `renewalIndex`. `occupation`
  // es el nombre del elemento de FUERA: leerlo daba undefined las 24 veces.
  assert.ok(madrid().every((m) => m.total === undefined), 'ningún parking de Madrid trae aforo');
});
