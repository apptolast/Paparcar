/**
 * Cuántas plazas quedan AHORA, y pegarlo al parking correcto. [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * ⛔ Módulo **puro**, como el resto de la decisión en `functions/`: aquí se parsea y se fusiona, y la
 * función de `index.ts` sólo hace I/O. Los tests corren contra la **respuesta real** de Madrid
 * capturada el 03-11-2026 (`fixtures/madrid-getlistparking.xml`), no contra un ejemplo inventado.
 *
 * Hoy la única fuente es **Madrid**. Málaga está medida y **bloqueada**: su CSV no publica
 * coordenadas y no existe mapeo de sus ids de dos letras a una ubicación — adivinarlo por iniciales
 * sería inventarse la fusión. El detalle está en el doc del ticket.
 */

/** Una medida de ocupación, ya normalizada, de donde venga. */
export interface CityMeasurement {
  /** `<source>_<idLocal>`: `madrid_3`. El namespace es PROCEDENCIA, nunca confianza. */
  readonly key: string;
  readonly source: string;
  readonly name: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly free: number;
  /** Aforo, donde la fuente lo diga. Madrid sí; Málaga no. */
  readonly total?: number;
  /** Epoch ms. Cuándo lo midió LA FUENTE, o cuándo lo leímos nosotros — ver [updatedAtIsFetch]. */
  readonly updatedAt: number;
  /**
   * ⛔ **`true` cuando la fuente NO sella su dato y el sello es nuestra lectura.**
   *
   * No es un detalle de implementación: es la segunda cláusula de la regla de frescura. *«Hace 3
   * minutos»* significa dos cosas distintas — «la ciudad lo midió hace 3 min» y «lo preguntamos hace
   * 3 min, y no sabemos de cuándo es» — y sin distinguirlas se presenta la cifra con una precisión
   * que no tiene. Madrid sella por parking (`<moment>`); Málaga no sella nada.
   */
  readonly updatedAtIsFetch: boolean;
  readonly licence: string;
}

/** Madrid: CC BY 4.0, y lo dice su propia ficha sin ambigüedad (al contrario que Málaga). */
export const MADRID_LICENCE = 'cc-by-4.0';
export const MADRID_SOURCE = 'madrid';

/**
 * ⛔ **40 m, y lo que importa no es el número: es qué se hace cuando hay dos candidatos.**
 * Ver [fuseAvailability].
 */
export const MERGE_RADIUS_METERS = 40;

/**
 * La respuesta de `GetListParking` de Madrid → medidas.
 *
 * ⚠️ **Extracción por expresión regular y no con un parser de XML, a propósito y con su precio
 * dicho:** es un servicio SOAP fijo, de un solo emisor, cuya forma está capturada en el fixture; un
 * `fast-xml-parser` traería una dependencia nueva a un `functions/` que hoy tiene **dos**. La
 * contrapartida es que esto **no es un parser general** y se rompería si Madrid reestructurara su
 * respuesta — por eso el test va contra la respuesta REAL y cuenta cuántas medidas salen: si la
 * forma cambia, el número cae y el test lo canta.
 *
 * ⛔ **Y sólo cuentan los que traen `<lstOccupation>` con un `<free>` numérico.** De los 75
 * elementos de la respuesta, **24** lo traen [MEDIDO 03-11]; el resto son POI sin ocupación, y
 * meterlos con `free = 0` sería decir «completo» de un parking que no ha dicho nada.
 */
export function parseMadrid(xml: string, fetchedAtMs: number): CityMeasurement[] {
  const out: CityMeasurement[] = [];
  for (const block of blocksOf(xml, 'lstParking')) {
    const occupation = firstBlock(block, 'lstOccupation');
    if (!occupation) continue;
    const free = intField(occupation, 'free');
    if (free === null) continue;

    const id = textField(block, 'id');
    const latitude = floatField(block, 'latitude');
    const longitude = floatField(block, 'longitude');
    // Sin id o sin punto no hay nada que fusionar, y una medida que no se puede colocar en el mapa
    // no es una medida: es un numero suelto.
    if (!id || latitude === null || longitude === null) continue;

    const moment = textField(occupation, 'moment');
    const stamped = moment ? Date.parse(moment) : NaN;
    const hasStamp = Number.isFinite(stamped);

    out.push({
      key: `${MADRID_SOURCE}_${id}`,
      source: MADRID_SOURCE,
      name: (textField(block, 'name') ?? '').trim(),
      latitude,
      longitude,
      free,
      // ⛔ **Madrid NO publica el aforo en esta operacion, y estaba leyendo un campo inexistente.**
      // Dentro de `<lstOccupation>` hay exactamente `code`, `free`, `moment`, `name` y
      // `renewalIndex` — contado sobre la respuesta real: `occupation` sale **0 de 24 veces**, porque
      // es el nombre del elemento de fuera. Asi que el aforo no viaja: si alguna vez hace falta,
      // vendra de `GetDetailParking` o del `capacity` de OSM (que lo trae en el 3,2 %), y mientras
      // tanto `total` ausente es la verdad. [MEDIDO 03-11-2026]
      updatedAt: hasStamp ? stamped : fetchedAtMs,
      updatedAtIsFetch: !hasStamp,
      licence: MADRID_LICENCE,
    });
  }
  return out;
}

/** Algo que se puede situar en el mapa: lo mínimo que la fusión necesita del lado de la base. */
export interface Placed {
  readonly id: string;
  readonly lat: number;
  readonly lon: number;
}

export interface Pairing {
  /** Qué medida va con qué parking de la base. */
  readonly matched: ReadonlyMap<string, CityMeasurement>;
  /**
   * ⛔ Las medidas que **no se pegan a nada** y las que se dejaron sin pegar **por ambigüedad**.
   * No es telemetría: es lo que un diagnóstico cita y lo que un test puede afirmar.
   */
  readonly unmatched: readonly CityMeasurement[];
  readonly ambiguous: readonly CityMeasurement[];
}

/**
 * **Empareja medidas con parkings de la base, y ante la duda NO empareja.**
 * [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * Tres reglas, y la segunda es la que hace esto honesto:
 *
 * 1. **Par más cercano, uno a uno**: una medida reclama **como máximo un** parking de la base, y un
 *    parking recibe **como máximo una** medida.
 * 2. ⛔ **Ambigüedad ⇒ no se fusiona.** Si dentro de [MERGE_RADIUS_METERS] hay un segundo candidato
 *    libre, la medida se queda sin pegar y se marca. Es la doctrina de fallo asimétrico del proyecto
 *    aplicada aquí: un **duplicado** es visible y evidente para el usuario —dos filas donde hay un
 *    parking—, mientras que una **fusión equivocada atribuye plazas libres a otro parking**, que es
 *    un hecho inventado y encima parece un dato. En el centro de Madrid un subterráneo y uno de
 *    varias plantas pueden estar a 30 m **y ser dos instalaciones de verdad**.
 * 3. Lo que no se pega **se cuenta**, no se tira: `unmatched` y `ambiguous` existen para que el
 *    fallo sea un número y no un silencio.
 */
export function fuseAvailability(
  base: readonly Placed[],
  measurements: readonly CityMeasurement[],
): Pairing {
  const matched = new Map<string, CityMeasurement>();
  const claimed = new Set<string>();
  const unmatched: CityMeasurement[] = [];
  const ambiguous: CityMeasurement[] = [];

  // Se resuelven las medidas en orden de su candidato MÁS cercano, para que un par evidente no se lo
  // robe una medida que pasaba por ahí. Sin esto, el resultado dependería del orden del feed.
  const scored = measurements
    .map((m) => ({ m, candidates: candidatesFor(base, m) }))
    .sort((a, b) => (a.candidates[0]?.metres ?? Infinity) - (b.candidates[0]?.metres ?? Infinity));

  for (const { m } of scored) {
    const free = candidatesFor(base, m).filter((c) => !claimed.has(c.id));
    if (free.length === 0) {
      unmatched.push(m);
      continue;
    }
    if (free.length > 1) {
      ambiguous.push(m);
      continue;
    }
    matched.set(free[0]!.id, m);
    claimed.add(free[0]!.id);
  }
  return { matched, unmatched, ambiguous };
}

function candidatesFor(
  base: readonly Placed[],
  m: CityMeasurement,
): { id: string; metres: number }[] {
  return base
    .map((p) => ({ id: p.id, metres: metresBetween(m.latitude, m.longitude, p.lat, p.lon) }))
    .filter((c) => c.metres <= MERGE_RADIUS_METERS)
    .sort((a, b) => a.metres - b.metres);
}

/** Haversine. A 40 m cualquier aproximación plana valdría; se usa la de verdad por no tener dos. */
export function metresBetween(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

// ─── Extracción de XML, deliberadamente mínima ───────────────────────────────

/** Los bloques `<ns:tag>…</ns:tag>`, con cualquier prefijo de namespace o ninguno. */
function* blocksOf(xml: string, tag: string): Generator<string> {
  const re = new RegExp(`<(?:[A-Za-z0-9]+:)?${tag}>([\\s\\S]*?)</(?:[A-Za-z0-9]+:)?${tag}>`, 'g');
  let match: RegExpExecArray | null;
  while ((match = re.exec(xml)) !== null) yield match[1]!;
}

function firstBlock(xml: string, tag: string): string | null {
  for (const block of blocksOf(xml, tag)) return block;
  return null;
}

function textField(xml: string, tag: string): string | null {
  const re = new RegExp(`<(?:[A-Za-z0-9]+:)?${tag}>([^<]*)</(?:[A-Za-z0-9]+:)?${tag}>`);
  return re.exec(xml)?.[1] ?? null;
}

function intField(xml: string, tag: string): number | null {
  const raw = textField(xml, tag)?.trim();
  if (!raw || !/^-?\d+$/.test(raw)) return null;
  return Number(raw);
}

function floatField(xml: string, tag: string): number | null {
  const raw = textField(xml, tag)?.trim();
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

// ─── Qué merece una escritura ────────────────────────────────────────────────

/** Lo que queda guardado de una medida. Lo que lee `mergeCell` y lo que compara la ingesta. */
export interface StoredMeasurement {
  readonly free: number;
  readonly total?: number;
  readonly updatedAt: number;
  readonly source: string;
  readonly name: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly updatedAtIsFetch: boolean;
  readonly licence: string;
}

/**
 * A lo que se guarda.
 *
 * ⛔ **Ni un `undefined`, y no es estilo: Firestore RECHAZA el documento entero.** Pasó en
 * producción el 03-11-2026 — `total: undefined` tumbó **todos** los ciclos del scheduler durante
 * horas con *«Cannot use "undefined" as a Firestore value»*, y el único síntoma visible fue que la
 * cifra **nunca apareció**: la función registraba su error y nadie lee logs. Los tests unitarios lo
 * dejaban pasar porque no tocan Firestore, así que el invariante tiene ahora su propio testigo.
 *
 * Una clave ausente y una clave con `undefined` **no son lo mismo** para Firestore: la primera
 * simplemente no está, la segunda es un error.
 */
export function toStored(m: CityMeasurement): StoredMeasurement {
  const stored: Record<string, unknown> = {
    free: m.free,
    updatedAt: m.updatedAt,
    source: m.source,
    name: m.name,
    latitude: m.latitude,
    longitude: m.longitude,
    updatedAtIsFetch: m.updatedAtIsFetch,
    licence: m.licence,
  };
  if (m.total !== undefined) stored.total = m.total;
  return stored as unknown as StoredMeasurement;
}

/**
 * **Si esta medida merece una escritura.** [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * ⛔ **Escribir sólo cuando el valor CAMBIA, y no es una optimización: es lo que mantiene esto
 * dentro de la capa gratuita.** La capa gratuita de Firestore es de 20.000 escrituras **al día**, y
 * a 3 minutos reescribiendo los 24 de Madrid salían 11.520 — el 58 % — para decir en su mayoría lo
 * mismo que ya estaba guardado. A 5 minutos y sólo con cambios, es una fracción.
 *
 * ⛔ **Y de paso se cae una pieza que el roadmap daba por necesaria:** la ventana *«sólo 07-23 h
 * local de cada fuente»*. Un parking cerrado de noche **no cambia de valor**, así que no se escribe,
 * así que la ventana no tiene nada que ahorrar. Un horario codificado por ciudad sería maquinaria
 * que hay que mantener, equivocarse con el cambio de hora y explicar — para hacer lo que la
 * comparación ya hace sola.
 *
 * Compara el VALOR, no el objeto: si la fuente vuelve a sellar el mismo recuento con un `moment`
 * nuevo, eso **sí** es información (dice que la cifra sigue viva), así que `updatedAt` cuenta.
 */
export function isWorthWriting(
  previous: StoredMeasurement | undefined,
  next: StoredMeasurement,
): boolean {
  if (!previous) return true;
  return (
    previous.free !== next.free ||
    previous.updatedAt !== next.updatedAt ||
    previous.total !== next.total ||
    // Si el parking se mueve o se renombra en la fuente, el documento tiene que seguirlo: si no,
    // la fusión <40 m seguiría usando el punto viejo para siempre.
    previous.latitude !== next.latitude ||
    previous.longitude !== next.longitude ||
    previous.name !== next.name
  );
}
