/**
 * Qué contesta `/api/parkings?cell=…`, decidido sin tocar la red. [PARKINGS-ONE-CALL-MERGED-001]
 *
 * ⛔ Módulo **puro**, como `fanout.ts` y `sharedCarFanout.ts`: la función de `index.ts` lee ficheros
 * y escribe cabeceras, y todo lo que es una DECISIÓN está aquí, donde se puede probar sin desplegar
 * nada ni mirar un móvil.
 *
 * Hoy la única fuente es la base pre-cocinada de OpenStreetMap (ticket 1). El esquema, en cambio,
 * va **completo desde el primer día** — `availability`, `offer`, `mergedFrom`, `licences` — para que
 * la ocupación municipal (ticket 3) y Parclick (ticket 5) **añadan una fuente y no rediseñen un
 * contrato**, y para que el cliente no se vuelva a tocar: los campos aparecerán en una respuesta
 * que ya sabe leer.
 */

/** Un registro tal y como lo escribe el snapshot. Espejo de `PublicParkingSnapshotEntry.kt`. */
export interface SnapshotEntry {
  readonly id: string;
  readonly lat: number;
  readonly lon: number;
  readonly kind: string;
  readonly fee: string;
  readonly name?: string;
  readonly operator?: string;
  readonly capacity?: number;
  readonly maxHeightM?: number;
  readonly openingHours?: string;
}

/** Un fichero de celda. Espejo de `PublicParkingSnapshotCell.kt`. */
export interface SnapshotCell {
  readonly cell: string;
  readonly version: string;
  readonly parkings: SnapshotEntry[];
}

/** El puntero `latest.json`. Espejo de `PublicParkingSnapshotManifest.kt`. */
export interface SnapshotManifest {
  readonly version: string;
  readonly generatedAtMs: number;
  readonly cells: number;
  readonly parkings: number;
  /**
   * ⛔ **El interruptor, y vive AQUÍ a propósito.** La app no tiene Remote Config, y meterlo entero
   * para un `if` es un SDK, su ciclo de fetch, su registro en Koin y su fake del flavor mock. El
   * puntero ya se lee en cada respuesta y ya va con `must-revalidate`, así que poner la bandera en
   * él da lo que Remote Config compraba —**revertir sin release, con efecto inmediato**— cambiando
   * un fichero. Ausente se interpreta como `true`: un puntero viejo no apaga el carril.
   */
  readonly serve?: boolean;
}

/** Quién dice qué, y bajo qué licencia. Una entrada por fuente presente en la respuesta. */
export interface Licence {
  readonly id: string;
  readonly source: string;
  readonly attribution: string;
}

export interface Availability {
  readonly free: number;
  readonly total?: number;
  readonly updatedAt: number;
  readonly source: string;
  /** ⛔ Nunca un enum de confianza: `source` es PROCEDENCIA. El valor oficial es `OFFICIAL`. */
  readonly sourceKind: string;
  readonly licence: string;
}

export interface Offer {
  readonly fromMinor: number;
  readonly currency: string;
  readonly windowMinutes: number;
  readonly provider: string;
  readonly firm: boolean;
  readonly licence: string;
}

export interface ApiParking extends SnapshotEntry {
  /**
   * ⛔ **El TESTIGO de la fusión, y sólo aparece cuando ha HABIDO una.** Con una sola fuente sería
   * siempre `[su propio id]`, o sea ~25 B × 926 registros por bloque de ruido: casi el doble de
   * payload para no decir nada. Su **presencia** significa que dos catálogos se emparejaron, que es
   * la única vez que hay algo que auditar — y un emparejamiento fallido deja **dos registros con un
   * id cada uno a 12 m**, que es afirmable en un test.
   */
  readonly mergedFrom?: string[];
  readonly availability?: Availability;
  readonly offer?: Offer;
}

export interface ApiResponse {
  readonly cell: string;
  readonly baseVersion: string;
  readonly generatedAt: number;
  /** `false` ⇒ el cliente se va a Overpass para esta pregunta. Ver [SnapshotManifest.serve]. */
  readonly serve: boolean;
  readonly licences: Licence[];
  readonly parkings: ApiParking[];
}

export const OSM_LICENCE: Licence = {
  id: 'odbl-1.0',
  source: 'osm',
  attribution: '© OpenStreetMap contributors',
};

/**
 * Lo que dura una respuesta en el CDN cuando NO lleva nada perecedero. **Una hora, no un día.**
 *
 * ⛔ **Y el motivo no es la frescura del dato: es cuánto tardas en REVERTIR.** La base cambia una
 * vez por semana, así que un día de antigüedad en los parkings no le hace daño a nadie — pero el
 * `s-maxage` es también el **suelo de cualquier marcha atrás**: mientras el CDN tenga la respuesta
 * cacheada sigue sirviéndola **aunque la función se borre**, aunque el puntero diga `serve: false` y
 * aunque el `rewrite` desaparezca. Con 86.400 s, apagar el carril tardaba hasta un día y el
 * interruptor de §D.3 no era un interruptor.
 *
 * Una hora acota la marcha atrás a una hora y cuesta ~24× invocaciones — del orden de 150-500 k/mes
 * [SUPUESTO sobre ~200 celdas activas], que sigue siendo calderilla. Ese cambio de 24× en el coste
 * es el precio de poder retirar algo el mismo día.
 */
export const CACHE_STATIC_SECONDS = 3600;

/** Y cuando lleva ocupación viva: el dato más perecedero manda (la fuente se mueve cada 1-3 min). */
export const CACHE_LIVE_SECONDS = 120;

/**
 * ⛔ **El TTL es propiedad de la RESPUESTA, no del endpoint**, y ahí está el coste.
 *
 * Con un TTL único mandaría siempre el dato más perecedero, y el expediente lo estimó en ~1,2 M
 * invocaciones/mes. Pero el dato perecedero sólo existe en cinco ciudades: una celda de Cuenca no
 * tiene nada que caduque en minutos, así que no hay ninguna razón para darle el TTL de Madrid.
 * Medido en el expediente: ~330.000 invocaciones/mes en vez de ~1,2 M, **y la ocupación más
 * fresca** (120 s en vez de 300).
 */
export function cacheControlFor(hasPerishable: boolean): string {
  return hasPerishable
    ? `public, max-age=60, s-maxage=${CACHE_LIVE_SECONDS}, stale-while-revalidate=60`
    : `public, max-age=300, s-maxage=${CACHE_STATIC_SECONDS}`;
}

/**
 * La respuesta para una celda, a partir de los ficheros del BLOQUE que la cubre.
 *
 * ⛔ **Deduplica por id, y no es paranoia**: el bloque es deliberadamente generoso en los bordes
 * (`floor` en los dos extremos de la caja), así que una celda puede entrar dos veces si el
 * llamante repite, y un parking repetido se vería como dos parkings en el mismo sitio — que es
 * exactamente el fallo que *parece un dato*.
 *
 * ⛔ **Y una celda que falta NO es un error**: la gran mayoría del territorio no tiene parkings, así
 * que su fichero no existe y eso es la respuesta correcta (`[]`). Lo que el llamante no puede hacer
 * es confundir *«esta celda está vacía»* con *«el snapshot no está publicado»*, y para eso está el
 * puntero.
 */
export function responseFor(
  cell: string,
  manifest: SnapshotManifest,
  cells: SnapshotCell[],
  now: number,
): ApiResponse {
  const byId = new Map<string, ApiParking>();
  for (const file of cells) {
    for (const entry of file.parkings) {
      if (!byId.has(entry.id)) byId.set(entry.id, entry);
    }
  }
  return {
    cell,
    baseVersion: manifest.version,
    generatedAt: now,
    serve: manifest.serve !== false,
    licences: [OSM_LICENCE],
    // Orden estable por id: dos respuestas de la misma celda son el mismo byte, así que el CDN y
    // cualquier comparación aguas abajo pueden fiarse de ello.
    parkings: [...byId.values()].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)),
  };
}

/** Si la respuesta lleva algo que caduca en minutos. Hoy nunca; con el ticket 3, en 5 ciudades. */
export function hasPerishable(response: ApiResponse): boolean {
  return response.parkings.some((parking) => parking.availability !== undefined);
}
