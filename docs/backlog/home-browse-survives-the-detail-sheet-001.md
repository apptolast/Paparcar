# HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001 · Cerrar un detalle devuelve el browse tal como estaba

**Estado:** ✅ Done — en master (PR [#12](https://github.com/apptolast/Paparcar/pull/12)) ·
GitHub issue [#6](https://github.com/apptolast/Paparcar/issues/6)

## Problema
Issue #6: «Cuando abrimos modal de detalle a partir de un elemento clickado en la modal de browse, si
le damos a la x de la modal al volver a browse, debería de mantener su estado anterior».

Qué se perdía al pulsar × en un detalle (plaza, parking, gasolinera, tu coche) abierto desde la lista:

| Estado del browse | Dónde vivía | ¿Sobrevivía? |
|---|---|---|
| Carril (plazas/parkings/gasolineras), filtro de tamaño, búsqueda | ViewModel | sí — `SelectItem(null)` no los toca |
| Scroll vertical de la lista | `rememberLazyListState` en `HomeContent` | sí |
| **Altura del sheet** (peek / media / expandido / completo) | `Animatable` en `HomeContent` | **no** — `SheetTransitionEffects` lo mandaba SIEMPRE a peek con `selection == null` |
| **Scroll horizontal de la tira de vehículos** | `LazyRow` sin estado, dentro de un item | **no** — la lista sale de composición bajo el detalle |
| **Scroll horizontal de los chips de tamaño** | `rememberScrollState()` dentro de un item | **no** — idem |

Resultado: con la lista abierta y desplazada, abrir un parking y pulsar × dejaba el sheet cerrado en
peek; había que volver a abrirlo, y la lista (que sí conservaba su scroll) aparecía «a mitad» sin que
se viera.

## Doctrina violada
Ninguna regla escrita; es un invariante de navegación implícito: **una superficie encima de otra no
reinicia la de debajo**. Home ya lo cumplía para el estado del ViewModel y lo rompía en el estado
de Compose.

## Señales / datos disponibles
- `SheetTransitionEffects` ya centraliza los movimientos por cambio de `selection`/`mode`.
- `SheetPositioning` es geometría pura y testeable; `capsAtPeek()` es la única respuesta a «¿este
  estado tope en peek?» [UI-SHEET-SPOT-PEEK-RESTS-ON-ITS-CONTENT-001].

## Diseño
Un sitio por cada pieza de estado perdida:

**Altura del sheet — `HomeSheetPositioning.kt`**
- `BrowseDetent { PEEK, HALF, EXPANDED, FULL }` + `SheetPositioning.nearestBrowseDetent(offset)` /
  `offsetOf(detent)` (puros, testeados). Se recuerda el ANCLA, no los píxeles: la geometría de
  browse vuelve con otro peek medido, y un píxel recordado aterrizaría al lado.
- Un colector registra el ancla en la que **descansa** el browse puro (sin selección, modo Browse,
  sin animación en curso). No registra el despegue del tap en un marcador: esa animación ya es el
  detalle abriéndose.
- Al volver a browse puro (transición no-puro → puro) con un ancla ≠ PEEK, `restoreBrowseDetent`:
  espera a que la geometría deje de estar topada (el cap del detalle se va un frame tarde), da un
  frame a la lista para medirse, vuela al ancla y re-apunta una vez si el peek siguió encogiendo.
  Timeout de 1 s → peek. Mientras vuela, la corrección de layout del peek se aparta
  (`restoringBrowse`) para no tirar el sheet abajo.
- Si el browse estaba en peek (tap a un marcador con el sheet cerrado), el comportamiento no cambia.
- Aplica a todo lo que se abre encima del browse: detalles de plaza, parking, gasolinera, sesión y
  los modos de pin (reportar, zona, marcar aparcamiento). Mismo invariante.

**Scroll horizontal — `HomeSheetContent.kt`**
- `HomeSheetRowsScroll(vehicles: LazyListState, sizeFilter: ScrollState)` +
  `rememberHomeSheetRowsScroll()`, hoisteado en `HomeContent` junto al `lazyListState` y pasado vía
  `HomeSheetFrame.rowsScroll`. `homeSheetItems(rowsScroll = null)` mantiene el comportamiento
  anterior para previews y galería.

## Criterio de éxito
- ✅ Visto en emulador (Pixel 8 Pro, flavor mock, «Con coches · All»): sheet expandido + tira de
  vehículos y chips desplazados → abrir «Calle Larga 14» → × → vuelve expandido con la tira y los
  chips donde estaban. Igual en el carril Parkings (abrir «Aparcamiento Estación», incluso tras
  minimizar el detalle, → × → carril Parkings expandido con su lista).
- `HomeSheetPositioningTest`: detent más cercano, resolución contra la geometría del frame de
  vuelta, cap en peek pliega todos los detents, lista corta limita FULL.
- Guardarraíles Konsist + `compileProdDebugKotlin` + `compileMockDebugKotlin` verdes.
- Galería: sin variante nueva — la galería pinta el sheet estático (`peek()`/`sheet()`), sin
  `HomeContent` ni física, así que no puede enseñar esta transición.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `LaunchedEffect(selection, mode)` (reset a peek) | rama de restauración antes del reset; resto intacto |
| `LaunchedEffect(peekOffsetPx)` (corrección de layout) | se aparta mientras `restoringBrowse` |
| Auto-aperturas (`promptShownAtMs`, `firstStepsCue`) | intactas; van a `expanded` igual que un restore a EXPANDED, no chocan |
| `SheetMotion` (toggle, `animateToExpanded`, nested scroll) | sin cambio |
| Stepper ‹ › entre detalles | selección no-nula → no-nula: no es «volver a browse», no dispara restore ni graba ancla |
| `homeSheetItems` en previews / galería | `rowsScroll` opcional, null = como antes |
| Refetch del carril al volar la cámara a un parking/gasolinera | **no cambiado** — ver pregunta 2 |

## Decisiones de la revisión (13-09-2026)
1. **Aceptado**: el browse recuerda su altura también al volver de los modos de pin (reportar plaza,
   añadir zona, marcar aparcamiento).
2. El refetch del carril al volar la cámara a un parking/gasolinera queda **como estaba** (sin
   decisión en la revisión): sigue el diseño de un solo almacén que sigue a la cámara
   [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]. Si molesta en uso real, ticket aparte.
