# MOCK-THE-FLAGSHIP-FIGURE-HAS-NO-MOCK-DATA-001 · La cifra estrella del pivote no tiene datos en el mock

**Estado:** ✅ Done · mergeado a master en squash

## Problema

El chip **«tiempo buscando plaza»** no aparece nunca en el flavor `mock`. Se vio grabando la
escena 09 del vídeo de Shipaton: la pestaña de actividad sólo ofrece `parkings` y `distance`,
nunca la tercera.

No es un bug de la UI. El chip es **condicional**, y su condición no se cumple jamás:

```kotlin
// presentation/vehicles/ChartMetric.kt
fun availableChartMetrics(sessions: List<UserParking>): List<ChartMetric> = buildList {
    add(ChartMetric.PARKINGS)
    if (sessions.any { it.routeDistanceMeters != null }) add(ChartMetric.DISTANCE)
    if (sessions.any { it.searchTimeMs != null })        add(ChartMetric.SEARCH_TIME)   // ← nadie
}
```

Medido: **`searchTimeMs` aparece 0 veces en todo `fakes/`**. Las 128 sesiones del catálogo lo
dejan en su default `null`. El tile va por el mismo camino (`stats.searchTime?.let { }`) y también
desaparece.

La asimetría que lo delata: la **galería de estados sí lo usa** (6 sitios) y los previews también
(2). O sea, el estado *está* dibujado y es revisable como pintura — pero la app real del mock, que
es la que se navega y la que se graba, no puede alcanzarlo nunca.

## Doctrina violada

CLAUDE.md, *Sistema de pruebas mock*: **«No añadir pantalla/estado/flujo nuevo sin actualizar el
sistema de pruebas mock»**, y la regla de que una variante nueva entra *en la misma tarea*. Cuando
`TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001` creó la medida y `STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001`
le dio su chip, la galería recibió sus variantes y **el catálogo de sesiones no**. Media tarea.

El invariante que faltaba, y que es el que evita que esto se repita:

> **Un campo que decide si una superficie existe tiene que tener datos en el catálogo del mock,
> no sólo en la galería.** La galería prueba el DIBUJO de un estado; el catálogo prueba que ese
> estado sea ALCANZABLE. Un estado dibujado pero inalcanzable se revisa bien y se rompe en
> silencio, porque nadie lo ve en el flujo real.

`searchTimeMs` no es un campo cualquiera: es la cifra que el pivote de trayectos declaró como
estrella y la que el paywall vende en su primera fila (*«Your full history: spend, time…»*).

## Señales / datos disponibles

`domain/detection/ParkingSearchTime` ya define que el veredicto es **de tres valores**, y eso es
exactamente lo que el catálogo tiene que reproducir — no rellenar 128 números y ya:

| valor | significado | constante |
|---|---|---|
| `null` | **inmedible** — sin llegada presenciada, sin fix fuera del radio, sin relojes | `ARRIVAL_WITNESS_METERS`, `SEARCH_RADIUS_METERS` |
| `0` | **llegada DIRECTA medida** — la línea conducida dentro del radio es la recta de entrada | `DIRECT_EXCESS_FACTOR`, `MIN_EXCESS_METERS` |
| `> 0` | **búsqueda medida** — dio vueltas dentro del radio | mínimo `MIN_SEARCH_MS` = 90 s, tope `MAX_PLAUSIBLE_SEARCH_MS` = 45 min |

Y se mide **sólo al confirmar**, desde los fixes crudos de la ruta grabada, porque la polilínea
guardada no lleva relojes. Sin ruta no hay medida.

## Diseño

Los cuatro coches del mock son **perfiles deliberados**, no copias con otro nombre, y el tiempo de
búsqueda tiene que respetar ese reparto en vez de atropellarlo:

| coche | perfil actual | searchTimeMs |
|---|---|---|
| 001 · **My car** | FULL DATA, 65 sesiones con ruta | **la mezcla de tres valores** — es el coche donde el chip debe aparecer |
| 002 · **Second car** | BT, sesiones **sin ruta** (km, CO₂ y coste desaparecen a propósito) | **null en todas**, y escrito como decisión: sin ruta no hay nada que medir |
| 003 · **My bike** | vacío a propósito | n/a |
| 004 · **Work van** | moderado, 18 sesiones con ruta | mezcla, con otro reparto para que no calque al 001 |

Dentro de un coche con ruta, el reparto sigue la doctrina:

- **pin manual → `null`.** Un pin a mano no tiene conducción grabada que medir. Es el mismo motivo
  por el que `detectionPath` ya vale `"manual"` ahí.
- **una parte → `0`.** Llegada directa medida. Cero **es una medida**, no un hueco, y sin ceros la
  media miente hacia arriba.
- **el resto → entre 90 s y ~9 min**, variado por módulo, como ya se hace con `routeDistanceMeters`
  y con las estancias («varied stays so the average is a real average, not one number repeated 65
  times»).

Que sobrevivan nulls es parte del diseño: es lo que hace que la línea de cobertura
(*«14 of 16 trips»*) diga algo verdadero en vez de ser decorativa.

## Criterio de éxito

- En el mock, pestaña Vehículos → actividad: aparece el **tercer chip**, y al pulsarlo el gráfico
  dibuja una serie distinta a `parkings` y a `distance`.
- El **tile** de tiempo de búsqueda aparece en el panel de cifras, con su línea de cobertura.
- El **Second car** sigue sin ofrecer el chip — sin ruta no hay medida, y eso es correcto.
- `./gradlew testAll` en verde.
- Grabable: la escena 09 del vídeo puede enseñar la cifra que el guion promete.

## Verificado el 24-09-2026

`./gradlew testAll` → **BUILD SUCCESSFUL**. Instalado en el Pixel 8, preset `numbers`, `en-IE`:

- El **tercer chip aparece** y se llama **«finding a spot»** — no «searching», que es como lo
  buscaba yo por `grep` y por lo que llegué a darlo por roto cuando ya funcionaba. La pantalla lo
  desmintió.
- Seleccionado, el gráfico dibuja una **serie distinta**: 7 · 2 · 9 · 15 · — · 8 · 5 min. El
  martes sale vacío porque sus sesiones midieron 0 (llegada directa), que es justo lo que un cero
  medido debe pintar.
- El **tile** sale: `45 min · FINDING A SPOT · 12 of 16 trips`.
- La **cobertura discrimina**: gasto, combustible y CO₂ dicen *14 of 16*, y la búsqueda *12 of 16*.
  Esos dos nulls extra son los pines manuales — exactamente lo que el diseño buscaba, y la prueba
  de que la línea de cobertura no es decorativa.
- **Second car: sigue sin ofrecer chips.** Ni `distance` ni `finding a spot`, porque no tiene ruta.
  El criterio inverso se cumple.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `fakes/data/repository/FakeUserParkingRepository.kt` | **cerrado** — es el catálogo, el agujero estaba aquí |
| `presentation/vehicles/ChartMetric.kt` | **exento** — la condición es correcta; le faltaban datos, no lógica |
| `app/src/mock/…/StateGalleryScreen.kt` | **exento** — ya tenía sus 6 usos; el dibujo nunca fue el problema |
| `presentation/preview/FakeData.kt` | **exento** — ya lo usa en sus 2 previews |
| `domain/stats/ScopedMeasure.kt` · `sumMeasuredSearchTime` | **exento** — ya trata el null como cobertura, que es lo que queremos ejercitar |
| `domain/detection/ParkingSearchTime.kt` | **exento** — es la fuente de la doctrina, no se toca |
