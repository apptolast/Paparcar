# UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001 · «Sin ubicación» habla por la píldora, no por el peek

**Estado:** 🔵 En progreso · rama `feature/UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001` ·
worktree `../Paparcar-no-location-pill` · issue [#27](https://github.com/apptolast/Paparcar/issues/27) · PR [#28](https://github.com/apptolast/Paparcar/pull/28)

## Problema

Petición del user (issue #27): *«El badge que usamos en el peek cuando el usuario no tiene ubicación
activada es bastante largo y ocupa mucho espacio horizontal. En lugar de ir ahí mostraremos el mismo
contenido (icono + texto) pero dentro de la píldora que mostramos en pantalla cuando tenemos poco
accuracy: si no hay GPS no hay accuracy, y en su lugar podemos mostrar la misma píldora indicando
que no tenemos ubicación.»*

Hoy el aviso vive en el trailing del peek (`PapSheetTrailing.Warning`, «SIN UBICACIÓN» en caps +
`LocationOff`), y compite por el ancho con el nombre del coche, la dirección y la cápsula del carril
— en la captura del issue el badge se come media fila. La píldora de precisión, en cambio, está
callada exactamente en ese momento: sin fix no hay metros que contar.

## Doctrina violada

Ninguna se rompe hoy; lo que había era **dos superficies para un mismo sujeto**. La ubicación de
Home ya tiene su sitio propio —la banda de FABs, donde vive la píldora desde
`UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001`— y el peek tenía que prestarle un hueco que necesita
para nombrar cosas. Es la misma corrección que ya hizo
`HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001` cuando dos superficies de Home decían
lo mismo: se apartan.

La regla que sí manda aquí es la de color: el badge es `PapColor.attention` («pendiente y lo puedes
arreglar»), NUNCA `danger` — nada está roto, hay un permiso esperando al usuario
[HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]. La píldora de precisión sí es `danger`. Al
fundirlas en un componente, **cada mensaje se queda con su color**.

## Señales / datos disponibles

- `DetectionUiState.warnsLocationMissing` (== `BlockedCore`) — la única fuente del aviso; ya la leen
  el badge, la fila ámbar de la lista y el auto-open de la hoja.
- `HomePeekSlice.showsLocationWarning` la expone al peek; `HomePeekHandle` la convierte en
  `onFixLocation = { OpenCorePermissions }`.
- `HomeFabsSlice.gpsAccuracy` (Float?) alimenta `HomeGpsAccuracyPill` en `HomeMapGpsLane`.
- String `home_location_off_badge` ya existe en los 9 locales («Sin ubicación» / «No location»).

## Diseño

**Una píldora, un veredicto sobre la ubicación.** `HomeGpsAccuracyPill` pasa a ser
`HomeLocationPill`, y lo que decide qué dice es una función pura —`locationPillMessage(...)`— con
tres salidas excluyentes:

| Estado | Cuándo | Color | Pulsable |
|---|---|---|---|
| `NoLocation` | `warnsLocationMissing` | `attention` | sí → `OpenCorePermissions` |
| `PoorAccuracy(m)` | fix con precisión > 50 m | `danger` | no |
| silencio | resto (casi siempre) | — | — |

**Sin ubicación GANA**: si no hay fix no hay precisión que contar, así que los dos estados no pueden
coincidir de verdad — pero el orden se escribe, no se deja al azar de qué campo llegue antes.

La píldora se lleva la ACCIÓN, no solo el texto: el badge del peek era pulsable y llevaba a los
permisos. Una advertencia que no se puede tocar convierte un problema con arreglo en una queja.

`locationMissing` se muda al **fabs slice**, que es donde se dibuja — la misma razón por la que
`gpsAccuracy` vive ahí y no en el del header.

El badge sale del peek **limpio**: `onFixLocation`, el `locationWarning` de `BrowsePeek` y, si
quedan huérfanos, `PapSheetTrailing.Warning` + `PapSheetWarningBadge`.

## Consecuencia asumida

La banda de FABs se pinta con `fabsVisible = sheetAtPeekLevel`: con la hoja **expandida** el aviso
deja de verse, donde el badge del peek se veía siempre. Es la contrapartida de la petición, y a
cambio el aviso gana los modos pin (colocar aparcamiento, avisar de plaza, dibujar zona), donde la
cabecera se esconde y el badge del peek también — que es justo cuando no tener ubicación decide si
el pin vale algo.

## Criterio de éxito

- Sin permiso de ubicación: el peek no pinta badge, y abajo a la izquierda sale la píldora ámbar
  «Sin ubicación» que al pulsarla abre los permisos.
- Con fix pobre (> 50 m): la píldora roja de siempre, con sus metros.
- Con fix bueno: nada, y sin coste de layout.
- Test unitario de `locationPillMessage` con los cuatro casos, incluido el de prioridad.
- Variantes de galería para los dos mensajes.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `BrowsePeek` (`onFixLocation`, `locationWarning`, 3 `trailing`) | borrado — y con él sus 3 imports |
| `HomePeekHandle` (paso de `showsLocationWarning`) | borrado |
| `HomePeekSlice.showsLocationWarning` | borrado — se queda sin consumidor; su prueba se muda al fabs slice |
| `PapSheetTrailing.Warning` / `PapSheetWarningBadge` | borrados — huérfanos, con `PapBadge` y `Role` de `PapSheet` |
| `HomeGpsAccuracyPill` → `HomeLocationPill` | reescrito con el veredicto puro |
| `HomeFabsSlice.locationMissing` / `HomeMapGpsLane` / `HomeScreen` | cambiados |
| `"core"` (focus de permisos) | ahora `CORE_PERMISSIONS_FOCUS` — dos llamadas que tienen que aterrizar igual |
| `home_location_off_badge` → `home_location_off_pill` | renombrada ×9: ya no es un badge |
| `DetectionUiState.warnsLocationMissing` | sin cambio — sigue siendo la única fuente |
| Fila ámbar de la lista y auto-open de la hoja | sin cambio — leen la misma fuente, no el badge |
| Galería mock (`StateGalleryScreen`) | 3 variantes: los dos colores, la prioridad y la entrada |

## Verificación (14-09-2026)

`:shared:testDebugUnitTest` + `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin`, con
`--rerun-tasks` para que los guardarraíles de Konsist (imports, tipografía, color, paridad de
locales) se ejecuten de verdad y no salgan UP-TO-DATE. Todo en verde.

⏳ **Falta verlo en mano**: la píldora ámbar pulsable con el permiso denegado, y que al concederlo se
vaya sola. El CI de la organización está caído por facturación de GitHub Actions, así que la
verificación es local.

## Pregunta abierta

Con la hoja **expandida** la banda de FABs no se pinta, así que ahí no hay aviso de ubicación donde
antes lo había. Es la contrapartida escrita arriba; si molesta en device, la salida no es devolver
el badge al peek sino dejar que esta única píldora sobreviva a la hoja abierta.
