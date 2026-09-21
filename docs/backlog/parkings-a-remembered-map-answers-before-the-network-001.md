# PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001 · El disco ya tenía la respuesta, y el carril enseñaba un esqueleto encima

**Estado:** ✅ Done — en master (21-09-2026)

Verificado en el **Pixel 8** con `prodRelease` (sha256 comprobado en device): el carril lista **135
parkings en Madrid**, ordenados por distancia, sin esqueleto y sin la nota de «sin conexión».
⚠️ Lo que NO se pudo capturar en pantalla es el instante exacto con las filas recordadas mientras la
petición vuela: al arrancar en frío la cámara se va al coche aparcado —a 294,8 mi en ese
device— y el carril entra en `tooFar` por diseño. La evidencia indirecta sí es concluyente: con la
red del emulador degradada a `gsm`/`gprs`, el contador del carril marcaba **135 parkings a los
5,6 s** de un arranque en frío, y una respuesta fresca de Overpass son 238 KB que por gprs no llegan
en ese tiempo — con la memoria vacía tras matar el proceso, ese número solo puede venir del disco.

Nace de la auditoría completa del carril de parkings y de la investigación de fuentes de datos, que
queda en [`docs/parking-data-research.md`](../parking-data-research.md). Este ticket implementa
**solo el Nivel 1 y el Nivel 2** de ese informe: velocidad con lo que ya tenemos, cero proveedores,
cero backend.

## Problema

«Los parkings de OSM/Overpass todavía tardan demasiado en aparecer.» Medido, no supuesto:

| Tramo | Coste |
|---|---|
| Espera de turno (`OverpassQuota`, 1 pregunta a la vez) | hasta **10 s** |
| Overpass caliente / frío | **1,4 s** / 4,68 s (Madrid) · **7,72 s** (Málaga, fibra) |
| Caminata completa si el primer espejo pasa | hasta **30 s** |
| **Techo antes de que la UI vea nada** | **40 s** |

Y durante todo eso, **en Room había hasta 30 días de parkings de esa MISMA zona** (`osm_coverage` +
`osm_public_parkings`), sin tocar: `DiskBackedPublicParkingsSource` solo leía el disco **cuando la
red fallaba**.

Cuatro cosas más, encontradas en la misma auditoría:

1. **El geocode bloqueaba la petición y nadie leía su respuesta.** No hay registro por país sobre
   este puerto (`OsmPublicParkingsSource.covers()` = `true`), así que el país solo entraba en la
   clave de caché de memoria — donde además **provocaba una petición duplicada por zona**: la
   primera pregunta con `countryCode=""`, el geocoder despierta, la segunda con `"es"`, misma zona,
   dos consultas a Overpass. El geocoder de Android tampoco tiene deadline propio.
   ⚠️ Ya estaba anotado como deuda en
   [`overpass-the-quotas-wait-is-part-of-the-walk-001.md`](overpass-the-quotas-wait-is-part-of-the-walk-001.md)
   § *Fuera de alcance*.
2. **El calentamiento preguntaba gasolineras ANTES que parkings**, y como es secuencial (el cupo es
   1), los parkings pagaban la respuesta entera de gasolineras. El orden no era una decisión: era
   `HomeNearbyLens.entries` leyendo `SPOTS, FUEL, PARKINGS`.
3. **El parseo y el ordenado corrían en el hilo PRINCIPAL.** `viewModelScope` es `Dispatchers.Main` y
   una corrutina se reanuda en el dispatcher de su contexto; no hay un solo `withContext` en
   `data/`. Lo que corría ahí: **238 KB de JSON y 681 elementos en Madrid**, decodificados, más un
   haversine por elemento, más un `sortedBy`.
4. **`RoomPublicParkingsCache.evict()` leía la tabla ENTERA en cada escritura** (`getAll()`, hasta 60
   discos × 1.000 filas) y la cruzaba contra cada disco vivo en Kotlin — y `put` se espera **antes**
   de que la respuesta llegue al carril.

## Doctrina violada

Ninguna doctrina de detección. La que se viola es del propio proyecto, y estaba escrita en el KDoc
que este ticket corrige:

> *«El disco se lee SOLO cuando la red falla, nunca antes que ella.»*

Era **una frase haciendo dos trabajos**, y solo uno era cierto:

- *la respuesta de hoy GANA siempre* → correcto, y queda **intacto**;
- *el disco no se puede MIRAR antes que la red* → no compraba nada y costaba la espera entera. Una
  lista que ya está en este móvil no se vuelve más verdadera por ocultarla ocho segundos.

Y una segunda, que este ticket **respeta**: *un dato dice cuándo se midió*
[FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]. Lo recordado viaja siempre con
`rememberedAtMs`.

## Diseño

### 1 · `remembered()`: una pregunta nueva en el puerto que ya existe

```kotlin
suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? = null
```

Tres preguntas, tres capas, ninguna reutiliza el TTL de otra:

| Capa | Pregunta | Vida |
|---|---|---|
| `CachingPublicParkingsSource` | ¿vuelvo a preguntar? | memoria, 30 min |
| `DiskBackedPublicParkingsSource` | ¿qué enseño si NO puedo preguntar? | Room, 30 días |
| **`remembered()`** | **¿qué puedo enseñar MIENTRAS pregunto?** | lo que haya |

`null` es el valor honesto por defecto: **una fuente que solo sabe preguntar no recuerda nada**, y
por eso `OsmPublicParkingsSource` lo devuelve explícitamente. Memoria primero y disco después, para
no pagar un viaje a Room por algo que el objeto de arriba tiene en un mapa.

### 2 · El carril no necesita ni una frase nueva, y eso no es suerte

Es la forma que la respuesta recordada **ya tenía**:

- `slice.isLoading && slice.parkings.isEmpty() -> NearbyRowsSkeleton()` → con filas, no hay esqueleto.
- `slice.rememberedAtMs?.takeUnless { slice.isLoading }` → la nota de «sin conexión» **ya estaba
  suprimida mientras se carga**, que es exactamente lo correcto: ahora mismo SÍ se puede preguntar y
  se está preguntando. Si la pregunta falla, `isLoading` cae, la nota sale, y entonces es verdad.

⛔ **La selección no se toca al pintar lo recordado**: una lista recordada no es un veredicto sobre
qué sigue existiendo, así que no puede ser lo que cierre el peek de nadie.

### 3 · Fuera el geocode

`PublicParkingsQuery.countryCode` **se queda en el modelo** porque
[`PARKINGS-LIVE-OCCUPANCY-001`](parkings-live-occupancy-001.md) lo necesitará el día que un feed de
ciudad enriquezca esta base. ⚠️ **Cuando llegue, no puede volver como una búsqueda bloqueante delante
de la petición**: el sitio del que sacarlo es `geocoder_cache` (30 días), que ya existe.

Efecto lateral que es medio ticket: la clave de L1 pasa a ser **constante por zona**, así que la
petición duplicada desaparece por construcción, sin contador de reintentos ni cooldown.

### 4 · El orden del calentamiento es una decisión, con su nombre

`WARM_UP_ORDER = listOf(PARKINGS, FUEL)`. Los parkings van delante porque su pregunta es la lenta y
la que no tiene alternativa: un país con feed nacional de carburante **no pasa por Overpass**; un
parking del mundo, siempre.

### 5 · El trabajo pesado, fuera del hilo principal

`withContext(Dispatchers.Default)` alrededor del parseo + haversine + orden. `Default` y no `IO`:
ahí ya no queda ni fichero ni socket, solo CPU.

### 6 · La barrida de huérfanos solo cuando ha desaparecido un disco

Una fila solo queda huérfana cuando **desaparece un disco**. Volver a preguntar por una zona
**reemplaza** su disco en el sitio (`coverageKey` es estable) y `put` ya ha reconciliado lo de
dentro. Se cuentan los discos vivos antes de la poda y, si no ha bajado, se salta el `getAll()`.

⚠️ Residuo, dicho en vez de escondido: un disco que caduca por edad entre dos escrituras no aparece
en ninguno de los dos recuentos, así que una fila suya puede quedarse. **Nunca se SIRVE** — `get()`
solo contesta a través de un disco vivo que contenga la consulta — así que el residuo es espacio en
disco, acotado por la caducidad de 30 días de la propia fila.

## Criterio de éxito

1. En una zona ya visitada, abrir Home y el carril de parkings **enseña filas al instante**, sin
   esqueleto, y sin decir «sin conexión».
2. La respuesta viva sustituye a lo recordado y le quita el sello.
3. Con la red caída, el comportamiento de antes intacto: filas + «sin conexión, esto es lo que había».
4. La misma zona no se pregunta dos veces por culpa del geocoder.
5. Al calentar, **parkings pregunta primero** y gasolineras espera su turno.
6. Volver a preguntar por una zona cacheada no lee la tabla entera.

### Testigos, y los cinco falsificados

| Testigo | Se rompió… | Falla ✅ |
|---|---|---|
| `should_paintWhatItRemembers_whileTheLiveAskIsStillFlying` | quitando el pintado previo en el VM | ✅ |
| `should_askOneLaneAtATime_when_warmingUp` | devolviendo `WARM_UP_ORDER` a `FUEL, PARKINGS` | ✅ |
| `should_stillPreferTheLiveAnswer_when_theDiskAlreadyHasTheArea` | leyendo el disco ANTES de la red | ✅ |
| `should_notScanEveryRow_when_reAskingAZoneThatIsAlreadyCached` | quitando el salto de la barrida | ✅ |
| `should_deleteFacilities_when_theDiscCapDropsTheirDisc` | quitando la barrida entera | ✅ |

⛔ El último existe porque la barrida **se quedó sin testigo al optimizarla**:
`should_deleteFacilities_when_noLiveDiscCoversThemAnyMore` pasa por caducidad de fila (todo tenía 31
días), no por la barrida. El nuevo dropa un disco **por el tope de 60** sin que nada haya caducado,
que es el único camino que puede dejar huérfana una fila joven.

Más: `should_askWithNoCountry_soTheSameZoneAlwaysProducesTheSameKey`,
`should_filterAndMeasureTheRememberedList_exactlyLikeTheLiveOne` (lo recordado y lo vivo se filtran y
se miden con el MISMO código, o la lista que parpadea primero acabaría discrepando de la que la
sustituye), `should_askNobody_when_sayingWhatItAlreadyKnows`,
`should_sayNothing_when_nobodyEverAskedAboutThisArea`,
`should_answerFromMemory_when_itAlreadyHoldsTheArea_withoutTouchingTheDisk`,
`should_askTheDisk_when_memoryHasNothing`, `should_keepTheFailure_when_thereIsNothingToRememberEither`,
`should_showNothingButTheSkeleton_when_itHasNeverBeenHere`.

## Consumidores auditados

`grep` de `PublicParkingsSource` · `parkingsNear` · `rememberedAtMs` · `GetNearbyPublicParkingsUseCase`:

| Sitio | Qué asumía | Estado |
|---|---|---|
| `DiskBackedPublicParkingsSource` (KDoc de clase) | «el disco solo se lee si la red falla» | ✅ reescrito: la mitad falsa se retira con su motivo |
| `RoomPublicParkingsCache` (KDoc de clase) | ídem | ✅ reescrito |
| `DataModule` (comentario de la pila) | ídem | ✅ reescrito |
| `HomeParkingsSlice.rememberedAtMs` | «viene del disco **porque la red falló**» | ✅ ahora documenta el par con `isLoading` |
| `HomeParkingsLane` | la nota bajo las filas | ✅ intacta en código; se documenta que `takeUnless { isLoading }` es lo que la mantiene verdadera |
| `CachingPublicParkingsSource` | TTL de 30 min | ✅ intacto — otra pregunta |
| `OsmPublicParkingsSource` | la red | ✅ `remembered` = null explícito |
| `FakePublicParkingsSource` (mock) | puerto sin `remembered` | ✅ el default `null` lo cubre; el flavour mock nunca ha tenido disco |
| `SpyPublicParkingsSource` (tests) | ídem | ✅ gana el mismo mando `held`, en el MISMO spy (un segundo fake derivaría) |
| `GetNearbyFuelStationsUseCase` | sigue con geocoder | ✅ **intacto y correcto**: ahí el país SÍ lo lee el registro por país |
| Galería del Dev Catalog | variante «Recordado del disco (sin conexión)» | ✅ intacta: sigue siendo el caso «no se pudo preguntar», que no cambia |

## Fuera de alcance (decidido, no olvidado)

- **La misma mejora en el carril de gasolineras.** Es el mismo invariante y merece hacerse, pero lo
  recordado allí incluye **precios**, que sí son perecederos, y eso es una decisión de producto, no
  una de caché. → su propio ticket.
- **Dónde dice el MAPA que está recordando.** Deuda ya abierta en
  [`cache-a-map-must-not-empty-on-cold-start-001.md`](cache-a-map-must-not-empty-on-cold-start-001.md)
  § *Pendiente 1*. Este ticket **no la crea pero la hace más frecuente**: ahora se dibujan pines
  recordados también mientras se pregunta. Sigue siendo decisión del user.
- **Todo lo demás del informe** (dataset pre-cocinado de OSM, open data municipal, NAP de la DGT,
  proveedores de pago): Niveles 3 a 5 de `docs/parking-data-research.md`.
