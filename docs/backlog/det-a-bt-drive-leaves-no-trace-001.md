# DET-A-BT-DRIVE-LEAVES-NO-TRACE-001 · Un viaje detectado por Bluetooth no deja ruta ni distancia

**Estado:** 🔵 Abierto · **diseño SIN decidir a propósito** — el user (31-08) descarta por ahora
medir el viaje entero; el ticket queda abierto para definirlo. Sin rama.
**Abierto:** 2026-08-31 · verificado contra master `748648fc`

## Problema

El carril BT despierta en el **destino**: BT disconnect del MAC emparejado → fix GPS → alejarse
≥30 m → confirma. Nadie estaba mirando durante el trayecto, así que la sesión se guarda sin
`routePolyline` y por tanto sin `routeDistanceMeters` (`UserParking.kt:95-99`, *"Null when there is
no route (BT/legacy)"*).

Consecuencias, en orden de visibilidad:

- El historial de un coche BT no dibuja ningún viaje.
- Los km del bloque de actividad describen solo las sesiones que sí tienen ruta, sin decirlo
  (`UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001`).
- El carril BT —el **determinista**, el de nivel "automático", el que mejor detecta— es el que peor
  historia cuenta. El usuario que mejor tiene configurada la app es el que menos ve.

La maquinaria para rellenarlo **ya existe y excluye el BT deliberadamente**.
`EnrichParkingSessionWorker.inferPinToPinRoute` (`EnrichParkingSessionWorker.kt:141-175`) coge la
sesión anterior del mismo coche (`getPreviousSession`), pide el grafo de calles, hace map-matching
entre los dos pines y guarda la ruta marcada **totalmente inferida y pendiente del veredicto del
usuario**; `updateParkingSessionRoute` estampa la distancia. Los elegibles son
`PIN_TO_PIN_ELIGIBLE_PATHS = {safety_net_backfill, manual, user, nudge}`
(`EnrichParkingSessionWorker.kt:205`), y la línea 134 dice por qué el BT no está:

> *Deliberately NOT eligible: "bt" (BT drives should get a MEASURED route — separate gap)*

**Este doc es ese "separate gap".** No tenía entrada en `docs/backlog` — comprobado por grep.

## Doctrina violada

Ninguna, hoy. Un dato ausente se declara ausente, que es lo correcto. Lo que hay es una **capacidad
desaprovechada**: el BT connect es una señal de arranque de viaje al menos tan buena como el AR
ENTER —determinista, atada a la MAC del propio coche, sin scoring— y no se usa para nada más que
para no perder el disconnect.

⚠️ Lo que sí sería una infracción es el atajo tentador: estampar una distancia inferida en
`routeDistanceMeters` sin marca. Sería afirmar medición donde hay deducción — la misma línea que ya
se defendió en `ROUTE-GAP-HONEST-001` y en la prohibición de inventar un radio por defecto.

## Señales / datos disponibles

- **Origen**: `getPreviousSession(vehicleId, timestamp)` ya existe y funciona sobre el historial
  (`UserParkingDao.kt:230-233`) — sirve también para sesiones viejas, así que un backfill
  retroactivo es técnicamente posible.
- **Marcas de honestidad**: `routeInferredSpans` + `routeInferredResolution` ya en el modelo,
  sincronizadas, con su flujo de veredicto (`REJECTED` borra la ruta).
- **Límites ya calibrados**: `TrailMapMatcher.MAX_MEASURED_STEP_METERS` (suelo, saltos triviales) y
  `GAP_BRIDGE_CEILING_METERS` (techo, saltos inverosímiles) acotan cuándo el puente pin-a-pin es
  creíble.
- **Señal de arranque sin explotar**: el ACL connect del MAC emparejado. Ya se escucha en el carril
  BT; hoy no arranca ningún muestreo.

## Diseño

⛔ **Sin decidir. El user lo dejó explícitamente fuera de alcance el 31-08** ("BT no estamos
fixeando todo el viaje, al menos por ahora"). Las dos vías, para cuando se retome:

**A · Inferir pin-a-pin** (barato). Añadir `"bt"` a `PIN_TO_PIN_ELIGIBLE_PATHS` — casi literalmente
una línea. Reconstruye el viaje entre el pin anterior y el nuevo, marcado inferido y pendiente de
veredicto.

> **El orden importa y ya está resuelto: primero el carril, después la distancia.** No se calcula
> ninguna distancia por separado — `inferPinToPinRoute` produce el polyline por map-matching sobre
> las calles y `updateParkingSessionRoute` estampa `routeDistanceMeters` como su longitud haversine.
> Por eso el campo no puede divergir de la ruta, y por eso habilitar el BT daría km sin tocar nada
> más. ⚠️ Lo que se obtiene es la longitud de la **ruta plausible por carretera**, no la del viaje
> realmente hecho: un rodeo o una calle distinta cambian la cifra. Si el usuario rechaza la ruta
> (`RouteInferenceResolution.REJECTED`), se borra — y la distancia se va con ella. Ventaja única e importante: **funciona hacia atrás**, sobre historial ya existente que
nunca podrá re-medirse. Costes: es una deducción, no un viaje; asume que entre los dos pines nadie
movió el coche ni hubo paradas; un backfill masivo son N peticiones de calles; y le pregunta al
usuario "¿fuiste por aquí?" por cada viaje viejo.

**B · Medir el viaje** (lo que reclama el comentario del worker). El BT connect arranca el muestreo
de localización; el disconnect lo cierra. Ruta real, misma calidad que el Coordinator, sin veredicto
que pedir. Costes: batería y un carril que hoy es barato deja de serlo; hay que decidir qué pasa si
el usuario conecta el BT y no conduce (radio encendido en el garaje); y el carril BT es
`DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001`, cuyo arreglo (`0e3940bc`) **sigue sin validarse en
campo porque necesita el Kamiq** — meterle una responsabilidad nueva antes de eso es apilar sin
medir.

No son excluyentes: **B para lo que venga, A solo para el historial que ya existe** es
probablemente la combinación correcta. Pero A sobre historial viejo hereda el riesgo del pin BT sin
validar, y esa es justamente la razón por la que hoy está excluido — *"a guessed route to a guessed
pin stacks doubt on doubt"* (`EnrichParkingSessionWorker.kt:135`).

**Prerrequisito de cualquiera de las dos:** validar en campo el pin BT con el Kamiq. Sin eso no se
sabe sobre qué se está construyendo.

## Criterio de éxito

Por definir con el diseño. Lo que sí es fijo, elija lo que se elija:

- Una distancia deducida **nunca** se presenta como medida. Si se infiere, la sesión lo lleva
  marcado y el agregado puede distinguirlo.
- Si se toca el carril BT, la skill `det-change` manda: doctrina, tests, `PARKING-DETECTION.md` y
  galería mock en la misma tarea.

## Consumidores auditados

Pendiente — depende de la vía. Puntos de partida: `EnrichParkingSessionWorker`
(`PIN_TO_PIN_ELIGIBLE_PATHS`), `BluetoothDetectionStrategy`, `ConfirmParkingUseCase`,
`VehicleHistoryCalculator.sumDistanceMeters`, y `SaveNewParkingSessionWorker` — ⛔ si aparece un
campo nuevo, su lista de `workData` es un contrato que ya se ha roto tres veces.

## Relacionados

- `UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001` — el síntoma visible. Independiente y anterior: hay
  que arreglarlo aunque esto no se haga nunca, porque los nulls no desaparecen del todo ni con B.
- `DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001` (`0e3940bc`, ⏳ sin validar en campo) — prerrequisito.
- `ROUTE-MANUAL-PIN-INFERRED-001` / `ROUTE-GAP-HONEST-001` — de dónde sale la maquinaria y las
  marcas de honestidad.
