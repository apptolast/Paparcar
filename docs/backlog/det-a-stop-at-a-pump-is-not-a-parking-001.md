# DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001 · una parada en el surtidor no es un aparcamiento

**Estado:** ✅ Done · mergeado a master · rama y worktree borrados
**Rebase:** sobre `9844c2473` (23-11). Un solo conflicto, de imports, en `HomeEntryPromptHost.kt`:
master había añadido ahí la ilustración del pitch de detección y este ticket el glifo del surtidor
— hacían falta los dos. Suite re-pasada entera después, no sólo antes.
⏳ **Sin ver en un móvil de verdad**: lo recorrido es el Pixel 8 emulado.
**Origen:** idea del usuario del 03-11-2026, al revisar las [modales de Home](home-the-first-glance-speaks-for-the-car-001.md).

> *«Cuando detectemos que el usuario ha parado en una gasolinera, la detección debería preguntar si
> ha repostado y entender ese aparcamiento como una parada a posible repostaje. Lo guardaríamos en
> historial y datos con el diferencial de que es una parada a repostaje, no un aparcamiento que se
> deba publicar.»*

## El problema

Hoy la detección sólo sabe decir **aparcamiento**. Paras tres minutos en un surtidor, el coordinador
mide la conducción, confirma, planta un pin — y al irte, ese pin **libera una plaza** que la
comunidad va a ver en el mapa. Una plaza que no existe: nadie puede aparcar en el surtidor de una
gasolinera, y quien conduzca hasta allí hace el viaje en balde.

Es un **falso positivo con consecuencia para un tercero**, que es la clase que la doctrina de
detección prohíbe explícitamente: *fallo asimétrico, mejor un falso negativo que un falso positivo*.
Y además mete una sesión de 3 minutos en el historial y en las cifras como si fuera aparcar.

⚠️ No está medido cuántas veces pasa. **Primer paso del ticket: contarlo**, no arreglarlo —
`parkdiag.log` y las sesiones de `diagnostics/{uid}/sessions` ya guardan la ubicación del pin, así
que se puede cruzar contra la capa de gasolineras sin tocar la app.

## Lo que hay ya, y no hay que volver a construir

| Pieza | Dónde | Qué aporta |
|---|---|---|
| Capa de gasolineras con coordenadas | `NearbyFuelStation` / el repositorio de fuel | **dónde hay un surtidor** — la pregunta «¿he parado en una?» es un radio contra esta lista |
| Zonas privadas, que YA deciden si se publica | `VehicleFenceOwnershipPolicy`, `SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001` | **el precedente exacto**: una sesión que se guarda pero NO publica plaza. Esto es el mismo mecanismo con otro disparador |
| El nudge que pregunta ante la duda | la superficie de detección de Home | **dónde preguntar**, sin inventar una pantalla |
| Procedencia y estado de la sesión | `UserParking` | Identifica la parada y cómo se detectó. La respuesta posterior tendrá su propio estado |

## Decisiones que hay que tomar antes de escribir código

1. **¿Qué dispara la pregunta?** Un radio contra la gasolinera más cercana es lo barato, pero una
   gasolinera urbana tiene la acera a 15 m: el radio solo va a confundir «aparqué enfrente» con
   «repostaje». ⛔ Candidato a medir: radio **+ duración** (un repostaje son minutos, no horas).
2. **¿Se pregunta o se deduce?** La doctrina dice que ante la duda se PREGUNTA. Pero una pregunta
   que salta cada vez que pasas cerca de un surtidor es ruido, y `DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001`
   ya estableció que **una interrupción se gana**. Probable respuesta: se deduce para **no publicar**
   (lado seguro, sin preguntar) y se pregunta sólo para **enriquecer** el historial.
3. **¿Es un tipo de sesión o un atributo?** Decidir dónde vive la clasificación antes de
   implementarla. La respuesta debe poder actualizarse después del cierre de la sesión. Revisar
   persistencia local, sincronización, espejo compartido y consumidores de métricas.

## Decisión de producto · Home, historial y métricas

- **Home** ofrece «¿Repostaste aquí?» cuando hay una parada candidata con evidencia suficiente.
  Cerrar o ignorar el aviso conserva la pregunta pendiente en el elemento de esa parada del
  historial del vehículo, incluso al volver a abrir la app.
- **Historial** muestra la parada como «Posible repostaje» con la misma pregunta. Responder en
  cualquiera de las dos superficies resuelve el mismo registro. «Sí» lo confirma como repostaje
  y «No» cierra la pregunta sin registrar un repostaje. La respuesta no determina por sí sola
  si la parada libera una plaza.
- **Métricas** incorpora los repostajes confirmados asociados al vehículo. Una parada pendiente
  o descartada queda fuera del recuento. Confirmar desde el historial actualiza la cifra con la
  fecha de la parada y cuenta una sola vez. El consumo real requiere además litros medidos y
  kilometraje entre llenados comparables.

## Inicio de la medición

- `Trace_Gondola2508Supersede` documenta una parada en una estación sin compra y un pin del
  Coordinator en ese punto. Prueba que un pin y una visita no bastan para registrar un repostaje.
  `FalseEnterAbortStageInheritedDriveTest` documenta la reanudación de la conducción tras una
  parada de este tipo. No se cuentan ambos fixtures como casos independientes.
- La capa de combustible ofrece localizaciones puntuales de estaciones. Por sí solas no permiten
  distinguir un surtidor de la acera contigua. Los replays revisados tampoco aportan una muestra
  etiquetada de ambos casos ni acreditan que aquel pin acabara publicado como plaza.
- Para medir, cruzar paradas cerradas con estaciones y etiquetar una muestra de paradas en el
  recinto, aparcamientos en calles cercanas y pasos sin parada. Contar por separado candidatas,
  avisos que habrían aparecido, plazas reales que se habrían retenido y plazas fantasma que se
  habrían evitado. Contrastar distancia, precisión disponible y duración antes de fijar un
  umbral. Registrar solo agregados y casos anonimizados en el ticket.
- Pendiente de una muestra etiquetada adicional. Sin esa evidencia no se ha calibrado la regla
  ni modificado el comportamiento del detector.

### Comparación local de candidatos

[La herramienta de medición](../../tools/refuel-stops/compare_candidates.mjs) recibe un JSON local
de observaciones ya etiquetadas. `stationStop` identifica una parada en el surtidor o su zona de
servicio sin plaza liberable, con o sin compra. `streetParking` identifica una plaza real cercana
y `passThrough` un paso sin parada. `publishedSpot` indica si la app publicó de verdad. Usar solo
observaciones cuyo resultado de publicación se conozca. Registrar la distancia a la estación
del catálogo que habría visto la app y usar `null` si falta la precisión del fix.

Ejemplo sintético de formato, con valores ilustrativos:

```json
{
  "samples": [
    { "label": "stationStop", "distanceMeters": 12, "durationSeconds": 180, "accuracyMeters": 7, "publishedSpot": true },
    { "label": "streetParking", "distanceMeters": 15, "durationSeconds": 240, "accuracyMeters": null, "publishedSpot": true }
  ],
  "rules": [
    { "maxDistanceMeters": 20, "minDurationSeconds": 60, "maxAccuracyMeters": 10 },
    { "maxDistanceMeters": 20, "minDurationSeconds": 60, "maxAccuracyMeters": null }
  ]
}
```

Ejecutar `node tools/refuel-stops/compare_candidates.mjs RUTA_MUESTRA.json`. Cada regla devuelve
totales por clase, avisos potenciales y publicaciones observadas que habría retenido al aplicarla.
Una regla con límite de precisión exige un dato de precisión. Una regla con límite `null` permite
comparar el resultado sin ese filtro. Los totales se usan para comparar criterios de aviso y
retención por separado.

### Validar la parada con el recinto OSM

`OsmFuelStationParser` pide `out center` y conserva un punto por estación. `FuelStationsRegistry`
prefiere los catálogos nacionales para alimentar el mapa de combustible. Para este ensayo,
usar el contorno OSM del recinto si está cartografiado y mantener la distancia al punto del
catálogo como referencia de comparación.

Añadir a la observación `fix: [longitud, latitud]` y `osmFootprint`, una geometría GeoJSON
`Polygon` o `MultiPolygon`. El comparador distingue interior, borde dentro del margen de
precisión, exterior y desconocido. Un nodo sin polígono o un fix sin precisión dejan la
pertenencia como desconocida. Con `requireOsmInterior: true`, el criterio exige que el círculo
de precisión estimada quepa entero en el área. Compararlo con la misma regla sin esa exigencia
para medir cuántos casos se pierden por cobertura o por el margen de precisión.

Primera muestra de contraste: dos paradas en el recinto (una con repostaje y otra sin compra),
dos aparcamientos reales en la calle próxima (incluido uno junto al borde), y un paso sin
parada. Priorizar una estación urbana con contorno OSM y una representada solo por un punto
para comprobar también la cobertura. Cada caso necesita el fix de la parada, precisión si se
conoce, duración, evidencia de si se publicó una plaza y la etiqueta de lo ocurrido. El mapa
ayuda a etiquetar la ubicación. La compra solo la confirma quien hizo la parada. Esta tanda
sirve para verificar el criterio en campo antes de calibrarlo con más observaciones.

El fichero de entrada se guarda fuera del repositorio y la salida contiene solo agregados.
Validado con seis pruebas sintéticas mediante
`node --test tools/refuel-stops/compare_candidates.test.mjs`.

## Lo que se construyó

> Relato completo —causa raíz, qué señal se reutilizó y por qué, y los testigos— en la entrada
> `DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001` de `docs/detection/PARKING-DETECTION.md` §2.

**El hallazgo que cambió el tamaño del ticket:** la fase de medición estaba construyendo por fuera
(`tools/refuel-stops/osm_footprint.mjs`) algo que **producción ya hacía**. `NearbyPlacePolicy` mide
al **contorno OSM** del sitio, no a su centro —0 m estando dentro del recinto— y estampa
`PlaceCategory.FUEL` en `UserParking.placeInfo` al enriquecer. O sea que «¿está el coche en una
gasolinera?» ya venía contestada, sincronizada, y **sin red en el momento de la salida**, que es
cuando hay que decidir. No hizo falta ni una consulta nueva.

**Decisión 1 · ¿qué dispara?** POI `FUEL` **+ duración ≤ 30 min**
(`SpotReleasePolicy.REFUEL_STOP_MAX_DWELL_MS`). El radio solo no vale: 40 m al borde es honesto para
nombrar y demasiado para decidir que la acera de enfrente es del surtidor. ⚠️ El umbral está
**razonado, no medido** —la muestra etiquetada sigue pendiente— y puesto del lado generoso a
propósito: retener una plaza de más es el fallo barato.

**Decisión 2 · ¿se pregunta o se deduce?** Las dos, y en ese orden: se **deduce para no publicar**
(sin molestar a nadie) y se **pregunta para enriquecer**. Y es **un solo veredicto**:
`Shape.refuelStopAnswer()` convierte el motivo de retención en la pregunta, en la misma escritura.

**Decisión 3 · ¿tipo o atributo?** Atributo: **una** columna, `UserParking.refuelStop`
(`RefuelStopAnswer?`), donde `null` es el cuarto valor y el normal. Un booleano de candidatura más
un veredicto nullable pueden contradecirse (contestado pero no candidato) y nada sabría a cuál
creer. `detectionPath` se queda intacto como procedencia.

## Consumidores barridos

| Consumidor | Estado |
|---|---|
| `SpotReleasePolicy.shapeFor` | **cerrado** — el motivo vive aquí; `placeInfo`/`dwellMs` sin defecto, así que el compilador pregunta a cada puerta |
| `ProcessConfirmedDepartureUseCase` | **cerrado** — `endedAtMs` izado por encima del gate: la publicación y el cierre fechan la MISMA salida |
| `FinalizeDeducedDepartureUseCase` | **cerrado** — fechado por la salida DEDUCIDA, no por el reloj de la promoción, que estiraría un depósito hasta parecer un aparcamiento |
| `ReleaseActiveParkingSessionUseCase` | **cerrado** — «Me voy» atestigua la salida, no el suelo |
| `EvaluateSpotReleaseNominationUseCase` | **cerrado** — sesión abierta ⇒ duración medida hasta el embarque; el motivo sale solo como `skip="refuel_stop"` (`reason.name.lowercase()`) |
| `RevertParkingUseCase` | **cerrado** — `refuelStop = null`: un aparcamiento que nunca ocurrió no deja pregunta |
| `GeofenceJanitorWorker` (5ª puerta, **la que no estaba en la tabla**) | **cerrado** — la encontró el compilador, no el grep: un supersede no midió nada, y el `COALESCE` impide que borre una respuesta |
| `UserParkingDao.reopenById` | **cerrado** — vuelve a `NULL` con `endedAtMs` y `publishedSpot`: la pregunta era SOBRE una salida que no ocurrió |
| Room · DTO · mapper (×4) · reconcile · deserializador manual | **cerrado** — migración v21 sin backfill; el reconcile no deja que un doc viejo borre la respuesta |
| Espejo del coche compartido | **exento con razón** — el grupo ve el viaje; si su conductor llenó el depósito es su registro. La lista blanca ahí es el TIPO |
| `ComputePeriodStatsUseCase` → `PeriodStats.refuelsConfirmed` | **cerrado** — sólo `CONFIRMED`, sobre sesiones cerradas, fechado por la sesión: contestar tarde acredita al periodo de la parada |
| `HomeEntryPromptHost` / `resolveHomeEntryPrompt` | **cerrado** — `HomeEntryPrompt.REFUEL`, tras el onboarding y antes del consentimiento |
| Historial del vehículo (`HistoryTimeline` → `VehiclesIntent`) | **cerrado** — misma pregunta, mismo `AnswerRefuelStopUseCase`, mismo registro |
| Ficha del detalle (`ParkingHistoryDetailScreen` → `ParkingHistoryIntent`) | **cerrado** — la tercera puerta; ver abajo |
| `firestore.rules` | **exento** — `users/{uid}/parkingHistory` es dueño-escribe-todo, sin validación por campo |
| `paparcar.com` y la ficha de Play | **exento** — ninguna de las dos promete nada que esto contradiga; no hay límite free ni feature nombrada que cambie |

## El hueco que se vio en el emulador, no leyendo el código

La primera versión preguntaba en Home y en la LISTA del historial. Entrar en la **ficha** desde esa
lista hacía **desaparecer la pregunta**: esa pantalla no decía nada del repostaje. No lo encontró
ninguna revisión — se vio recorriendo el flujo en el Pixel 8.

Al cerrarlo se unificó lo que ya había en vez de añadir una tercera copia: **`RefuelStopRow`** vive
en `PeekShared.kt` (junto a `PeekMetaRow` y `ApproximateZoneRow`, que es de donde esta pantalla ya
sacaba sus filas de hecho) y lo usan la lista y la ficha. La primera versión tenía su propio
`PapBadge` dentro de `HistoryTimeline`: dos formas de decir lo mismo son dos sitios donde puede
acabar diciendo cosas distintas. Tres estados y sólo tres:

| Estado | Qué se ve |
|---|---|
| `PENDING` | el hecho («Posible repostaje») + la pregunta con sus dos respuestas |
| `CONFIRMED` | el hecho en pasado («Repostaste aquí») — un dato de la sesión, como la plaza que te cedió la comunidad |
| `DISMISSED` o `null` | **nada**. Un «no» no deja rastro que enseñar, y no haber preguntado menos |

## Verificado en el Pixel 8 (emulador, flavor `mock`)

Recorrido completo, en los dos temas: Home pregunta nombrando el sitio → cerrar sin contestar deja
la pregunta viva en la lista → entrar en la ficha la conserva → contestar ahí la cierra en las tres
superficies y deja el hecho en pasado. La fila de la lista **sigue abriendo el mapa** pese a llevar
dos botones dentro, que era el riesgo de meter acciones en una `Card` clicable.

## Lo que queda

- ⏳ **Verlo en un móvil de verdad.** Lo anterior es emulador: para el carril visual basta, pero la
  detección (parar en un surtidor y que el gate retenga la plaza) sólo se prueba en campo.
- ⏳ **Calibrar los 30 min** con la muestra etiquetada (`compare_candidates.mjs`), no a ojo.
- 🔵 Un repostaje confirmado es media cifra: con litros y kilometraje entre llenados saldría el
  consumo REAL, que hoy se estima. Eso es `FUEL-*`, no este ticket.

## Lo que NO es

- ⛔ No es un botón de «he repostado». Eso ya existiría si el usuario tuviera que decirlo él.
- ⛔ No es gatear nada: el carril free recibe avisos de precio **alrededor** y esto no lo toca.
- ⛔ No publica una plaza nunca, ni siquiera «por si acaso». Ese es el bug que cierra.

## Lado iOS — **cubierto, sin hueco pendiente**

Todo lo que decide vive en `commonMain`: el veredicto (`SpotReleasePolicy`), la columna, el caso de
uso de la respuesta y las dos superficies son Compose Multiplatform. El resolver cae siempre a
Coordinator en iOS [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001], así que el disparador —POI del pin +
duración medida— **vale igual**, y el POI lo estampa el mismo enriquecimiento.

Lo único que NO se puede dar por visto allí es la ventana de 24 h de Home: en iOS no hay nudge
residente, así que la pregunta se ofrece **al abrir la app**, que es exactamente lo que hace
`HomeEntryPromptHost`. No hace falta código específico; hace falta un iPhone delante.
