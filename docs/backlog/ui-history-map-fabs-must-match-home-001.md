# UI-HISTORY-MAP-FABS-MUST-MATCH-HOME-001 · los tres mandos del mapa se leen igual en Historial que en Home

**Estado:** ✅ Done · mergeado a master (squash) el 2026-09-03

## Problema
En **Historial → detalle** la columna de mandos del mapa tiene tres botones, y ninguno de los dos
que importan está donde el usuario ya aprendió a buscarlo en Home:

| Posición | Home (`HomeMapFabColumn`) | Historial detalle (`MapControlButtons`) |
|---|---|---|
| Arriba | 🚗 `DirectionsCar` → centrar en el coche | 🛣 `Route` → encuadrar los dos puntos |
| Medio | 🛣 `Route` → encuadrar los dos puntos | **🅿 `LocalParking`** → centrar en el aparcamiento |
| Abajo | ◎ `MyLocation` → centrar en mí | ◎ `MyLocation` → centrar en mí |

Reportado por el user: *«tenemos 3 iconos pero hay una P que no sé por qué y está en medio; debería
ser igual que en Home, de arriba a abajo: coche, distancia coche, y loc»*. Es decir, el fallo se
percibe **antes** de poder deducir qué hace el botón: la "P" no se reconoce, y encima ocupa el hueco
del que en Home encuadra la ruta.

## Doctrina violada
- **Iconos, nivel 2** — un glifo de Material tiene UN significado en la app.
  `Icons.Rounded.LocalParking` ya está adjudicado: es
  `PaparcarIcons.ParkingPlace`, la **categoría de POI "parking público"**. Sobre un mapa, la P dice
  *«aquí hay un aparcamiento»*, no *«llévame a este aparcamiento»* — que es lo que hace el botón.
- **Sistemas, no parches** — `MapControlButtons` se escribió declarando en su propio KDoc que era
  *«the same "where am I" affordances Home has»*, y aun así divergió en glifo Y en orden. La
  afirmación estaba en un comentario que nadie comprobaba.
- Lo que NO se toca: la distinción `COPY-SPOT-IS-NOT-A-PARKING-001`. En un histórico el coche ya no
  está ahí, así que el **texto** sigue siendo `map_cd_go_to_parking` («Centrar en este aparcamiento»)
  y `map_cd_midpoint_parking`, no los de Home. Lo que se unifica es el dibujo y el sitio, no la copia.

## Señales / datos disponibles
Nada que medir: es una divergencia estática entre dos ficheros, verificada leyéndolos.
- `LocalParking` sólo se usa en un sitio como glifo directo: la línea que este ticket cambia
  (`grep -rn "Icons.Rounded.LocalParking"` → 1 hit en `MapControlButtons.kt`, más su alias legítimo
  en `PaparcarIcons.ParkingPlace`).
- Las condiciones de visibilidad ya coincidían con Home (`hasParking`, `hasParking && hasGps`,
  siempre), así que reordenar no cambia cuándo aparece cada botón.

## Diseño
Un solo fichero, `presentation/map/components/MapControlButtons.kt`:
1. `LocalParking` → `DirectionsCar`, el mismo glifo que Home usa para el mismo gesto.
2. Los dos `AnimatedVisibility` se intercambian: coche arriba, ruta en medio, `MyLocation` abajo.
3. El KDoc pasa a decir explícitamente qué se comparte con Home (glifo + orden) y qué no (la copia),
   y deja escrito por qué la P no vuelve — para que la próxima edición no la reintroduzca.

No se toca el mecanismo de separación (`padding(bottom = FAB_GAP_DP)` por item): es preferible al
`Arrangement.spacedBy` de Home, porque no deja un hueco fantasma cuando el item oculto no se compone.
Igualar eso sería copiar un defecto en nombre de la paridad.

## Criterio de éxito
- En el detalle de historial, de arriba a abajo: coche · ruta · mi ubicación, con los mismos iconos
  que Home. ⏳ **Sin ver en mano**: se mergeó sin `/run`, así que la comprobación en device queda
  pendiente del siguiente APK. Lo que sí está verificado es el código y la suite.
- El botón del coche sigue centrando en el pin del aparcamiento leído (no en la sesión activa) y su
  content description sigue siendo «Centrar en este aparcamiento».
- `:shared:testDebugUnitTest` verde (guardarraíles de iconos/color/tipografía incluidos).

## Consumidores auditados
| Sitio | Qué asume | Estado |
|---|---|---|
| `presentation/map/components/MapControlButtons.kt` | único render de la columna del historial | ✅ corregido |
| `presentation/map/ParkingHistoryDetailScreen.kt:339` | único call site; pasa los 3 callbacks | ✅ sin cambios (la API no cambia) |
| `presentation/home/sections/map/components/HomeMapFab.kt` | la columna de Home, referencia | ✅ es el patrón, no se toca |
| `ui/icons/PaparcarIcons.ParkingPlace` | `LocalParking` = categoría POI parking público | ✅ queda como único dueño del glifo |
| `presentation/util/MapFab.kt` (KDoc de `MapCircleFab`) | lista quién lo usa | ✅ sigue exacto |
| `StateGalleryScreen` / `*Previews.kt` | ni pantalla ni estado nuevos → nada que añadir | ✅ exento |
| Strings (`map_cd_go_to_parking`, `map_cd_midpoint_parking`) | ya existen en los 9 locales | ✅ sin keys nuevas |
