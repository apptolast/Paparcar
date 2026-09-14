# HOME-WITHOUT-LOCATION-STILL-ANSWERS-001 · Sin ubicación, Home sigue respondiendo lo que sabe del sitio que miras

**Estado:** ✅ Done · en master · verificado con `:shared:testDebugUnitTest` +
`:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` con `--rerun-tasks` (2.798 tests).
⏳ Falta verlo en mano: preset sin permisos del Dev Catalog y denegar en la primera entrada real.

## Problema

Sin el permiso de ubicación en primer plano, Home se vacía casi entero. El gate es
`HomeSheetContent.kt:101-102`:

```kotlin
val showPersonalBlocks = slice.hasCorePermissions && vehicleCards.isNotEmpty()
val showFilterBar      = slice.hasCorePermissions && slice.hasAnySpots
```

y con él desaparecen la tira de coches (`:311`), el carril «cerca de ti» entero —cabecera, pestañas
y las tres listas— (`:329`), los filtros de tamaño (`:543`), la tarjeta de avisar (`:586`) y los
chips de zonas del header (`HomeHeaderSection.kt:89`). Sin *fix* además se apagan la tarjeta de
avisar (`:592`) y los CTA del checklist (`:204`).

Y el aviso que debería explicarlo **no siempre existe**: las tres superficies que lo cuentan —la
píldora del mapa (`HomeSlices.kt:445`), la fila ámbar y la auto-apertura del sheet— leen
`DetectionUiState.warnsLocationMissing`, que es `== BlockedCore`, y `BlockedCore` **solo existe con
coche registrado y detección encendida** (`DetectionUiState.kt:111-115`). El gate del contenido, en
cambio, no mira si hay coche.

> **El peor estado de la app es el del usuario que acaba de decir «No permitir» y todavía no tiene
> coche: mapa mudo, cero contenido y cero explicación.** Es justo el momento en el que hay que
> convencerle de lo contrario.

## Doctrina violada

- **⛔ Nunca se gatea VER** (`CLAUDE.md` § Plus vs free): «plazas, parkings públicos y gasolineras
  son free COMPLETO. Cobrarlo vacía el mapa justo para quien no paga, que es el problema que F5 y F6
  existen para resolver». Vaciarlo por un permiso que aún no han concedido hace exactamente el mismo
  daño, y por una razón más débil: el dato **está** y lo estamos escondiendo.
- **[ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]** — «sin diálogos de culpa, sin pantalla
  bloqueante»: Home sabe vivir sin ubicación. Un sheet vacío es una pantalla bloqueante sin el
  cartel.
- **[HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]** quitó el *takeover* rojo pero dejó pasar
  este gate («sin ubicación "cerca" no significa nada», fila 86 de su tabla de consumidores). Esa
  premisa es **falsa** y el propio código lo demuestra — ver abajo.

## Señales / datos disponibles

Todo el material del sheet está anclado a la **cámara**, no al fix. Medido en `origin/master`:

| Pieza | Qué hace sin fix |
|---|---|
| `HomeViewModel.kt:800-805` `viewportCentreOrGps()` | La cámara primero; el fix es solo el respaldo del arranque en frío |
| `ViewportRefetch.kt:42-45` `isWorthAsking` | Sin GPS devuelve **true**: parkings y gasolineras se piden igual (y sin el tope de 20 km, que es una distancia *a ti*) |
| `NearbyOrdering.kt` | Ya ordena sin `user`: usa la distancia medida desde el punto preguntado. Su propio KDoc: *«With no fix yet, the asked point is all there is»* |
| `HomeScreen.kt:1296-1299` `pinStartPoint` | Los modos de pin **ya** arrancan en la cámara; el fix es el segundo intento |
| `HomeSpotRows.kt:149` | La fila de plaza ya omite distancia y tiempo si `userLocation == null` |

O sea: la capa de datos **ya sabe vivir sin ubicación**. Lo único que falta responder de verdad es
«¿dónde estoy yo?». Lo demás —qué hay en el sitio que estás mirando— lo sabemos igual.

## Diseño

**El invariante, en una frase:** *el permiso de primer plano responde a «¿dónde estoy YO?», no a
«¿qué hay AHÍ?». Se oculta solo lo que es una afirmación sobre TI que no podemos hacer.*

### 1 · La tira de coches no depende de la ubicación

`showPersonalBlocks` pierde el `hasCorePermissions`:

```kotlin
val showPersonalBlocks = vehicleCards.isNotEmpty()
```

Tus coches y su estado de vigilancia no tocan la ubicación por ningún lado. El gate es anterior al
split de módulos (`b949efa1`, heredado) y nunca tuvo ficha que lo sostuviera. Además es el bloque
que le dice al recién llegado que la app ya sabe de su coche.

### 2 · El carril «cerca» se muestra, anclado a la cámara

El `if (slice.hasCorePermissions)` de `:329` cae; el carril, sus pestañas y los filtros se muestran
siempre. Sin fix cambian **dos** cosas, y solo dos:

- **La distancia sale de las filas de parking y gasolinera.** Hoy esas filas rotulan el
  `distanceMeters` que midió el use case *desde el punto preguntado*: con fix es tu posición, sin
  fix es el centro del mapa. Mientras eso duraba los primeros segundos de un arranque en frío era
  un mal menor; si abrimos el carril sin permiso pasa a ser **permanente**, y entonces «a 300 m» es
  una frase sobre un punto donde no hay nadie. Sin fix la fila no rotula distancia ni tiempo de
  conducción — exactamente lo que la fila de plaza ya hace (`HomeSpotRows.kt:149`). El nombre, el
  precio, la tarifa y el gálibo siguen ahí, que es lo que se venía a ver.
- **El orden es por cercanía al centro del mapa**, que es lo que `NearbyOrdering` ya calcula. No
  hace falta tocarlo; sí decirlo, para que nadie lo "arregle" luego.

### 3 · «Marcar aparcamiento» se ofrece sin fix

Sin permiso no hay detección automática: **apuntar el coche a mano es la única forma de que la app
sirva para algo**, y hoy es justo lo que se retira.

⛔ **No hace falta tocar `DetectionStory`**, y eso se descubrió implementando §1. La fila
`BlockedCore` es excluyente (`DetectionStory.kt:219`) y se come los CTA de arranque en frío — pero
con la tira de coches ya en pantalla, **cada card sin sesión trae su propio botón de aparcar**
(`vehiclesSection` → `cardClick`, que entra en `AddingParking` con `initialGps` nullable), y lo trae
*por cada coche*, no solo por el que la proyección elige. Duplicar ese CTA dentro de la fila de
bloqueo habría sido exactamente el «dos superficies diciendo lo mismo» que
`HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001` vino a quitar. La vía existe; lo que
la tapaba era el gate de §1.

Sí cambia **el checklist**: `FirstStep.MARK_PARKING` era el único `needsLocation = true`, sobre la
premisa de que sin fix el pin entra «sin centro». `pinStartPoint` (cámara → fix → null) dice otra
cosa: arranca donde el usuario está mirando, y el caso «ni cámara ni fix» sigue devolviendo `null`,
que los llamantes honran [SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001]. Con ese último uso
fuera, `needsLocation` y el `hasLocation` que lo alimentaba quedan sin consumidores y se borran
enteros — un parámetro que ningún caso puede poner a false no es una opción, es ruido.

### 4 · «Avisar de una plaza» SIGUE exigiendo fix — y ahora por escrito

Se mantiene `enabled = slice.userGpsPoint != null` (`HomeSheetContent.kt:592`), y es el único gate
del ticket que sobrevive, porque su razón es distinta de todas las demás:

> **La única fiabilidad que tiene un aviso comunitario es que sepamos que estabas cerca.** Un
> aparcamiento es tuyo y lo verifica quien lo pone; una plaza se publica a desconocidos que van a
> conducir hasta ella. Sin fix no podemos afirmar que la viste: solo que alguien arrastró un pin
> sobre un mapa. Marcar tu coche sin ubicación es una nota para ti; avisar de una plaza sin
> ubicación es una afirmación sobre el mundo que no podemos respaldar.

Es la misma doctrina de detección —*el evento NOMINA, solo el movimiento MEDIDO confirma*— aplicada
al reporte manual: **mejor falso negativo que plaza fantasma**. Hoy esto vive como un `enabled`
justificado por una ficha que en realidad cerraba otra cosa (el `0.0, 0.0`); aquí gana su propia
razón, escrita en el sitio, y la deshabilitación **dice por qué** en vez de quedarse muda.

### 4-bis · ⛔ Enseñar el carril no basta: había que poder PREGUNTAR

Descubierto al responder «¿y si no hay ni GPS ni coche aparcado al abrir Home?». Tres cosas
encadenadas, y la primera es la raíz:

1. **El mapa abre en (0, 0), el Golfo de Guinea.** La semilla es
   `cameraTarget ?: initialCamera ?: userLocation ?: Coordinates(0,0)` (`PaparcarMapView`) y Home
   **nunca pasa `initialCamera`**. Sin fix y sin sesión activa no queda nadie que mueva la cámara:
   `centerInitialFocus` espera un fix que no llega y `centerOnParkingWithoutLocation` exige coche
   aparcado. → `HomeUiController.centerOnZoneWithoutLocation`: una **zona guardada** es lo más
   parecido a «donde vivo» que la app tiene sin medir nada, porque la declaró el usuario. Consume el
   mismo one-shot, así que el coche aparcado sigue ganando y un fix posterior no hace saltar la
   cámara.
2. **Las plazas no se pedían NUNCA sin permiso.** `HomeSpotsController` enmascaraba el centro a null
   por `hasCorePermissions`, y el centro sólo se sembraba desde un fix (`maybeRecenterOnPan` salía
   con `false` mientras fuese null). Enseñar el carril sin arreglar esto habría dado un «no hay
   plazas libres cerca» **sin haber preguntado jamás** — exactamente la mentira que
   `FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` prohíbe. → cae el gate de permiso (el centro de
   consulta no es la posición del usuario: es el sitio del mapa por el que pregunta) y el pan
   SIEMBRA el centro cuando no hay ninguno, filtrando (0, 0).
3. **Y cuando de verdad no hay centro, se dice.** `SpotsUpdate.Unasked` → `HomeState.spotsUnasked` →
   el estado vacío del carril cambia de frase: `home_spots_unasked` ×9, que además nombra el remedio
   —buscar una calle vale tanto como activar la ubicación—. Parkings y gasolineras ya eran honestos
   aquí (`viewportCentreOrGps` descarta el (0,0) y el carril dice «no pudimos preguntar»).

Queda un caso sin nada que inventar: usuario nuevo, sin permiso, sin coche y sin zonas. Ahí el mapa
se queda donde el motor lo deje y los tres carriles dicen que aún no han podido preguntar. Es la
verdad, y el remedio —el buscador— está a un toque en la cabecera.

### 4-ter · Los chips de zonas salen sin permiso

Una zona es un sitio TUYO que ya has declarado, no tu posición de ahora — y sin ubicación es cuando
más falta hace poder saltar a una: es la única forma que le queda a la app de llevarte a un sitio
que conoces (y, con §4-bis, de darle centro al carril). `HomeHeaderSlice.hasCorePermissions` se
queda sin consumidores y se borra.

### 5 · La píldora habla también sin coche (decidido por el user, 14-09)

Consecuencia directa de 1-3: con el carril ya visible, un usuario sin coche registrado y sin permiso
vería contenido sin que nadie le explique por qué no aparece su posición.

**Dos sujetos que se habían fundido en un predicado.** «No hay ubicación» es un hecho del TELÉFONO,
cierto con coche o sin él; «la vigilancia está bloqueada por falta de CORE» es un hecho de la
DETECCIÓN, que sin coche no existe. Coincidían mientras solo se miraba con coche delante.

- Verdicto nuevo, puro y testeable: `homeHasNoLocation(hasCorePermissions, detectionUiState)` =
  permiso CORE ausente **o** `warnsLocationMissing` (la única vía por la que la app se entera de que
  los servicios de localización están apagados).
- Lo leen la **píldora del mapa** (`toFabsSlice`) y la **cámara** que vuela al coche sin fix
  (`HomeScreen`), que hasta ahora deletreaba esos dos mismos términos por su cuenta: eran dos
  copias, y dos copias es como el mapa acaba volando mientras la píldora calla.
- `warnsLocationMissing` se queda **exactamente donde estaba** para la fila ámbar y la auto-apertura
  del sheet: ambas necesitan un coche para tener sentido — la fila no tendría nada que ofrecer y el
  sheet nada sobre lo que abrirse. Su KDoc lo dice ahora en vez de dejarlo al lector.

## Criterio de éxito

- Sin permiso de ubicación y con coche registrado: el sheet muestra tira de coches + carril con sus
  tres pestañas, y la superficie de detección enseña a la vez el aviso ámbar **y** «Marcar
  aparcamiento».
- Sin permiso y **sin** coche: el carril responde igual (es lo que arregla el mapa mudo).
- Con permiso y GPS apagado: idéntico, más la píldora «Sin ubicación».
- Las filas de parking y gasolinera **no** rotulan distancia ni tiempo sin fix; con fix, sin cambio.
- «Avisar de una plaza» sigue deshabilitada sin fix, y con su razón visible.
- Al conceder el permiso, todo vuelve a su estado completo sin recomponer a mano (readiness ya es
  reactiva — [DET-A-GRANTED-PERMISSION-MUST-REOPEN-THE-WATCH-001]).
- Sin permiso y con el mapa llevado a una calle (búsqueda o pan): **salen las plazas de esa calle**.
  Es la prueba de que §2 responde de verdad y no enseña un carril vacío.
- Sin centro de ningún tipo: el carril de plazas dice que hay que buscar o activar la ubicación, y
  NO «no hay plazas libres cerca».
- Sin fix, sin coche aparcado y con una zona guardada: Home abre sobre la zona, no en el océano.
- ✅ Tests: `LocationAvailabilityTest` (nuevo, el veredicto puro), `HomeSlicesTest` y
  `HomeSpotsControllerTest` — los cuatro casos que el ticket invierte se REESCRIBEN, no se borran:
  el checklist sobrevive sin permiso, la píldora habla sin coche, las plazas se piden sin permiso y
  el pan siembra el centro (con su testigo de que (0,0) sigue prohibido).
  `should_notShowTheLocationWarning_when_onlyProducerPermissionsAreMissing` pasa a declarar su
  `hasCorePermissions = true`, que es lo que siempre quiso decir.
- ✅ Galería: «Sheet · sin permiso de ubicación, con coche» y «…, sin coche». Preview equivalente en
  `HomeSheetPreviews`.
- ✅ `:shared:testDebugUnitTest` + `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin`
  verdes con `--rerun-tasks` (2.794 tests; sin él los guards de Konsist salen UP-TO-DATE).
- ⏳ **Falta verlo en mano**: preset sin permisos en el Dev Catalog, y el caso real de denegar en la
  primera entrada.

## Copy nueva

Dos keys, ×9 locales cada una:

- `home_report_needs_location` — el subtítulo que la tarjeta de avisar lleva mientras está
  deshabilitada. EN: *«Turn on location to report: a spot is only trustworthy if you were next to
  it.»* Un control apagado y **mudo** se lee como una app rota; este tiene una razón, y una que el
  usuario puede resolver.
- `home_spots_unasked` — el carril de plazas cuando no hay centro. EN: *«Search for a street or turn
  on location to see the free spots there.»* Nombra los DOS remedios, y el primero no pide permiso
  ninguno.

Ambas: causa, consecuencia y remedio, sin mecánica interna.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `HomeSheetContent` `showPersonalBlocks` | ✅ cae el `hasCorePermissions` |
| `HomeSheetContent` `showFilterBar` | ✅ queda `hasAnySpots` |
| `HomeSheetContent` carril entero | ✅ sin gate |
| `HomeSlices.showsFirstSteps` | ✅ cae el `hasCorePermissions` (era el gate que dejaba el checklist sin poder mostrar su CTA) |
| `FirstStepsCard` `hasLocation` / `FirstStepCopy.needsLocation` | ✅ **borrados** — sin consumidores tras §3 |
| `HomeSheetContent` tarjeta de avisar | ✅ visible siempre, `enabled` por fix, con su razón escrita (§4) |
| `HomeSpotRows.HomeReportSpotCard` | ✅ subtítulo nuevo mientras está deshabilitada |
| `DetectionStory` prioridad `BlockedCore` | ✅ **sin cambio** — la vía de marcar vive en la card de cada coche (§3) |
| `NearbyOrdering` | ✅ sin cambio — ya ordena sin `user` |
| `ViewportRefetch.isWorthAsking` | ✅ sin cambio — sin GPS ya devuelve true |
| `HomeSpotRows` distancia de plaza | ✅ sin cambio — ya es null-safe |
| `HomePublicParkingRows` / `HomeFuelStationRows` | ✅ `hasFix`: sin fix, sin distancia |
| `HomeParkingsLane` / `HomeFuelLane` | ✅ pasan el `hasFix` desde el slice |
| Peeks de plaza / parking / gasolinera / sesión | ✅ exentos — ya calculan la distancia con `userGpsPoint?.let`, o sea ya se callan sin fix |
| `warnsLocationMissing` (fila ámbar + auto-apertura) | ✅ sin cambio — necesitan coche y hacen bien en callarse sin él (§5) |
| `toFabsSlice().locationMissing` (píldora) | ✅ pasa a `homeHasNoLocation` |
| `HomeScreen` `locationUnavailable` (cámara) | ✅ pasa a `homeHasNoLocation` — era la segunda copia del mismo predicado |
| `HomeUiController.centerOnParkingWithoutLocation` | sin cambio — sigue siendo el foco sin fix |
| `StateGalleryScreen` | ✅ tres variantes nuevas: sin permiso con coche, sin coche, y plazas sin preguntar |
| `HomeSheetPreviews` | ✅ preview «sin permiso de ubicación», en paridad con la galería |
| `HomeHeaderSection` chips de zonas | ✅ visibles siempre (§4-ter); `HomeHeaderSlice.hasCorePermissions` **borrado** |
| `HomeSpotsController` gate de permiso + semilla del centro | ✅ §4-bis — era lo que hacía mentir al carril |
| `HomeUiController` semilla de cámara | ✅ `centerOnZoneWithoutLocation`, mismo one-shot |
| `HomeEmptySpots` | ✅ frase propia cuando no se ha preguntado |
| `HomePeekSlice.laneCount` para SPOTS | 🔸 sin cambio — sigue contando 0 sin centro. Su KDoc dice que las plazas «nunca son null» por ser una suscripción permanente, y eso deja de ser cierto sin ubicación; el carril abierto ya lo explica, y la píldora del peek es un contador, no una frase |

## Notas de base

⚠️ El worktree parte de `origin/master` (`1f276225`), no del `master` local: el árbol principal
estaba **1 ahead / 2 behind** — tiene `191ddcfc` (FIRST-STEPS) sin pushear y le faltaban `c476e74d`
(la píldora de ubicación, central para este ticket) y `1f276225`. Al cerrar habrá que rebasar sobre
un master ya reconciliado.
