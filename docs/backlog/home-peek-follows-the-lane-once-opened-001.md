# HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001 · Tras abrir Browse, el peek plegado sigue a la pestaña

**Estado:** ✅ Done — en master (PR [#23](https://github.com/apptolast/Paparcar/pull/23), aprobado sin cambios) ·
GitHub issue [#9](https://github.com/apptolast/Paparcar/issues/9)

## Problema
Issue #9: al iniciar Home el peek muestra el estado del vehículo; al abrir el sheet la cabecera pasa
a la pestaña seleccionada (plazas/parkings/gasolineras), pero al volver a plegarlo regresa al
vehículo. Debe mostrar el vehículo solo la primera vez; una vez abierto el sheet, el peek sigue a la
pestaña también plegado.

## Doctrina
Revierte a propósito la regla «⛔ Plegado NO cambia» de
[`home-the-watch-comes-first-and-the-header-follows-the-lane-001.md`](home-the-watch-comes-first-and-the-header-follows-the-lane-001.md)
(marcada como superada allí y en el KDoc de `HomePeekSlice.nearbyLens`). Se mantiene:
- [UI-SHEET-004]: el coche aparcado nunca toma su dirección de la cámara (su info sigue en su card).
- [UI-BROWSE-DRIVING-OVER-PARKED-001] / [DET-ASK-STATE-001]: el **viaje vivo** sigue encabezando el
  peek plegado — es noticia, y su eyebrow lleva la pregunta «¿has aparcado?».

## Diseño
- Latch de UI en `HomeContent`: `browseLaneHeaderLatched` con `rememberSaveable` (sobrevive a
  cambiar de pestaña de la app y a process death; se reinicia con la entrada HOME). Se arma la
  primera vez que el sheet pasa del umbral de cabecera (`sheetBeyondPeek`) **en Browse puro** (sin
  selección): el expand de un tap a marcador es la apertura de un detalle, no de Browse. Cuentan
  también las aperturas automáticas (checklist, pregunta de detección).
- Posición del sheet = estado de UI, no del ViewModel: `HomeContent` ya es dueño de
  `sheetOffsetPx`; meterlo en el VM exigía intent + campo + slice para un booleano de geometría.
- `BrowsePeek(laneHeaderLatched)`: `followsLane = showZoneHeader || laneHeaderLatched` gobierna la
  rama del coche aparcado y `isSpotsLane`; la rama de conducción sigue con `showZoneHeader`.
- El hint «desliza para avisar» solo en cabecera plegada del carril de plazas con 0 plazas.
- Galería: «PapSheet · plegado tras abrir (carril PARKINGS, coche aparcado)».

## Criterio de éxito
- Primera entrada con coche aparcado: peek = «COCHE · APARCADO».
- Abrir el sheet, cambiar a Parkings, plegar: peek = «PARKINGS PÚBLICOS · N»; volver a Plazas y
  plegar: «PLAZAS LIBRES».
- Ir a Vehículos y volver a Home: sigue en el carril.
- Con un viaje en curso, plegado muestra el viaje.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `HomeScreen` → `HomeSheetSection` → `HomeBottomSheet` → `HomePeekHandle` → `BrowsePeek` | nuevo parámetro con default `false` |
| Galería / previews que llaman `HomePeekHandle` | default `false` = comportamiento anterior; variante nueva |
| `sheetAtPeekLevel`, nav progress, FABs | sin cambio (otro umbral) |
| Doc `home-the-watch-comes-first…` + KDoc `nearbyLens` | marcados como superados |

## Preguntas que quedaron sin respuesta
El PR se aprobó sin contestarlas; se conserva el comportamiento implementado.
1. **Viaje en curso**: lo he dejado ganando al carril en el peek plegado (lleva la pregunta de
   aparcamiento). ¿O también debe ceder a la pestaña tras abrir el sheet?
2. «La primera vez que iniciamos home» = hasta la primera apertura en esta sesión de la app (se
   conserva al cambiar de pestaña y tras reinicio del proceso). ¿Os vale, o debe recordarse para
   siempre (preferencia persistente)?
