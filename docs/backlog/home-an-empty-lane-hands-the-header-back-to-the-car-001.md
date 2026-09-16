# HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001 · Con la hoja plegada, un carril vacío devuelve la cabecera al coche

**Estado:** ✅ Done — en master

## Problema
Desde [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001], en cuanto el usuario abre Browse una vez, el
peek plegado deja de hablar del coche aparcado y pasa a hablar del carril elegido (plazas / parkings
/ gasolineras) **para siempre**. El latch no mira si ese carril tiene algo que enseñar.

Resultado: con la hoja cerrada y el carril seleccionado vacío, el estado de reposo de Home —la única
línea que la app dice cuando no la estás mirando— queda en una cabecera que no cuenta nada:
el sujeto («PARKINGS») y una cifra en cero o una cápsula hueca. Mientras tanto el coche aparcado,
que es lo que Home SIEMPRE tiene que decir, está callado a un arrastre de distancia.

## Doctrina violada
Ninguna regla escrita, pero sí el criterio editorial del peek: *plegado, el peek es la NOTICIA*
(viaje vivo > coche aparcado > carril). Un carril sin resultados no es noticia, y al ocupar la
cabecera desplaza a la única que queda.

## Señales / datos disponibles
`HomePeekSlice` ya proyecta todo lo que hace falta, no hay estado nuevo:
- `laneCount: Int?` — cuántos hay del sujeto en pantalla, **null si nadie lo ha preguntado**
  ([FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]).
- `laneLoading: Boolean` — el carril está preguntando ahora mismo.
- `userParking` — si hay coche aparcado al que devolverle la cabecera.
- `showZoneHeader` (hoja más allá del peek) y `laneHeaderLatched` (Browse abierto alguna vez).

## Diseño
El invariante vive en UN sitio: **quién es el sujeto de la cabecera de browse**, que hasta hoy era
la línea `followsLane = showZoneHeader || laneHeaderLatched` dentro de `BrowsePeek`. Se extrae a una
función pura `browseHeaderFollowsLane(...)` en el mismo fichero —citable y testeable, sin ceremonia
de clase— y se le añade la única condición nueva:

> Expandida, la cabecera es SIEMPRE del carril (es la lista que estás leyendo). Plegada y con el
> latch puesto, el carril la conserva **solo mientras tenga algo que enseñar**; si su lista está
> vacía y hay coche aparcado, la cabecera vuelve al coche.

Dos cautelas deliberadas:
- **Un carril que está preguntando no está vacío** (`laneLoading`): la cápsula late y se queda con la
  cabecera hasta que llegue la respuesta. Así no hay parpadeo coche→carril en cada consulta.
- **Sin coche aparcado no se devuelve nada**: no hay peek de vehículo al que ceder, y el sujeto base
  de Home (el contador de plazas) es justo lo que ya enseña el peek sin latch. El carril se la queda.

El carril de plazas entra por la misma puerta: para `SPOTS`, `laneCount` ES `freeCount`.

## Criterio de éxito
- Hoja plegada + Browse abierto antes + carril seleccionado con lista vacía + coche aparcado →
  el peek es el del vehículo (glifo del coche, «<COCHE> · APARCADO», dirección y "hace N · a M").
- El mismo caso sin coche aparcado → la cabecera sigue siendo la del carril (sin cambio).
- Carril cargando → cabecera del carril, cápsula latiendo (sin cambio).
- Hoja expandida → cabecera del carril siempre, vacío incluido (sin cambio: estás leyendo esa lista).
- Viaje vivo → gana a todo, vacío o no (sin cambio).
- Testigo: `BrowseHeaderSubjectTest` en `commonTest` sobre la función pura.
- Variante nueva en la galería de estados del Dev Catalog.

## Consumidores auditados
`grep followsLane | laneHeaderLatched | browseLaneHeaderLatched`:
- `BrowsePeek.kt` — **la fuente**: única decisión, ahora en la función pura.
- `HomePeekHandle.kt` — solo reenvía el flag. Cerrado.
- `HomeBottomSheet.kt` / `HomeScreen.kt` (`HomeSheetSection`) — solo transportan el latch
  (`rememberSaveable` + `LaunchedEffect`). No deciden sujeto. Cerrados.
- `StateGalleryScreen.kt` — dos variantes ejercitan el latch; se añade la del carril vacío.
- `HomeSlices.kt` (`toPeekSlice`) — produce `laneCount` / `laneLoading`; no se toca.

## Estado final
Una sola función pura, `browseHeaderFollowsLane(...)` en `BrowsePeek.kt`, decide el sujeto de la
cabecera de browse; `followsLane` la llama y nadie más decide.

Verificado: `./gradlew testAll` verde (incluye `:app:testMockDebugUnitTest`), con
`BrowseHeaderSubjectTest` ejecutado — **7 tests, 0 fallos**, uno por rama de la regla.
`:app:compileProdDebugKotlin` y `:app:assembleMockDebug`, verdes.

⏳ **Sin ver en mano.** Lo que hay que mirar en campo: el carril re-pregunta con la cámara
(`NearbyTrigger.CAMERA`, gated por modo Browse, **no** por si la hoja está abierta) y con cada fix de
GPS, así que paneando el mapa con la hoja cerrada el sujeto puede ir y venir — 2 parkings → 0 →
coche → 3 → carril. `laneLoading` tapa el hueco de la consulta, no el vaivén en una zona pobre.

Se dejó **reactivo a propósito**: si estás aparcado y aparecen plazas al lado, eso también es
noticia y merece la cabecera. Si en device se ve parpadeo, la alternativa es hacerlo **pegajoso** —
que un carril vacío no pierda la cabecera solo en ese frame, sino que **suelte el latch** en
`HomeScreen` (el `rememberSaveable` de `browseLaneHeaderLatched`), de modo que el coche se la quede
hasta que el usuario vuelva a abrir Browse. Tres líneas sobre lo que ya está.
