# UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001 · El historial que aún no sabe no puede afirmar que no hay nada

**Estado:** ✅ Done — mergeado a master el 31-08-2026 (squash)
**Abierto:** 31-08-2026 · sobre master `c604a058`
**Pendiente de campo:** ⏳ sin ver en device. La ventana real de skeleton depende de cuánto tarde
Room en su primera emisión; se comprueba con `pm clear` + `/run`.

## Problema

El user reporta: *"a veces veo datos por defecto que no cuadran"* en el historial de Vehículos.

Hay skeleton — `HistorySkeletonSection` (`HistoryContent.kt:367-429`: chart + 4 chips + cabecera +
3 filas) y su rama de render en `HistoryContent.kt:200-204`. **Pero en producción no se dibuja
nunca**, porque nadie levanta jamás su bandera:

| sitio | qué hace con `isLoading` |
|---|---|
| `HistoryState.kt:8` | default **`false`** |
| `VehiclesViewModel.kt:58-63` | construye `HistoryState(...)` sin tocarlo → `false` |
| `VehiclesState.kt:21` (fallback del estado derivado) | **`HistoryState(isLoading = false)`** literal |
| `VehiclesScreen.kt:252` (fallback por página del pager) | **`HistoryState(isLoading = false)`** literal |
| `StateGalleryScreen.kt:1198` | **único `isLoading = true` de todo el repo** — la galería mock |

Consecuencia medida en el código: mientras Room/Firestore resuelven, `HistoryContent` no cae en la
rama del skeleton sino en la siguiente, `state.sessions.isEmpty()` (`HistoryContent.kt:205`), que
pinta **`EmptyHistoryState`** — el "aún no hay historial" con su ilustración — y una hero card sin
fila de stats (`hasStats = sessionCount > 0`, `VehiclePageContent.kt:111,148`).

La pantalla **afirma "no hay nada" cuando lo cierto es "todavía no lo sé"**. Y es una afirmación
sobre datos del user, no un adorno: dice que su coche no tiene historial.

La ventana se abre de par en par justo después de un reset de DB o una reinstalación (como el del
30-08): Room arranca vacío, `observeAllSessions()` (`UserParkingRepositoryImpl.kt:80-81`) emite `[]`
al instante, y el historial sostiene el "vacío" hasta que la sync trae los datos. El spinner global
de `VehiclesState.isLoading` (`VehiclesState.kt:10`, este sí arranca en `true`) sólo cubre la carga
de VEHÍCULOS, y se apaga en la misma emisión en la que el historial todavía no ha llegado.

## Doctrina violada

- **"Ante la duda se PREGUNTA, nunca se planta un dato fantasma"** — la doctrina rectora de
  detección aplicada a la UI: un estado que no sabe debe decir que no sabe, no rellenar con ceros.
- **`DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001`** (`c604a058`, cerrado ayer): *un input de
  decisión no lleva default, porque un default es una respuesta silenciosa permanente*. `isLoading`
  es exactamente eso: un default `false` que responde "ya cargó" por omisión, en los tres sitios
  donde nadie se acordó de contestar.
- **`VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001`** (`22abbcc4`): el mismo patrón —
  cuatro puertas deletreando una respuesta que el tipo debería impedir deletrear mal.
- **Sistemas, no parches**: poner `isLoading = true` en tres sitios deja el cuarto call site futuro
  igual de libre para equivocarse. El invariante tiene que vivir en el TIPO.

## Señales / datos disponibles

Ya está todo; no hace falta telemetría nueva:

- `VehiclesViewModel.observeVehicles()` sabe perfectamente si ha habido emisión (`combine` de
  `observeVehicles()` + `observeAllSessions()`, `VehiclesViewModel.kt:34-39`). Hoy simplemente no lo
  transmite.
- `state.historyCache[vehicleId] == null` **ya significa** "de este coche aún no sé nada" en los dos
  fallbacks — y ambos lo traducen a `HistoryState(isLoading = false)`, o sea, a "de este coche sé que
  no hay nada". La información existe y se tira en la puerta.

## Diseño

**Que el estado no pueda expresar "cargando" y "vacío" con el mismo valor.** Hoy los dos son
`sessions = emptyList()`, y sólo un booleano con default los separa.

1. `HistoryState` deja de tener `isLoading` + `sessions` y pasa a tener **un solo campo sin default**
   que responde "¿está resuelto?":

   ```kotlin
   sealed interface HistoryTimeline {
       /** Aún no ha llegado la primera emisión de Room para este vehículo. */
       data object Unresolved : HistoryTimeline
       data class Resolved(val sessions: List<UserParking>) : HistoryTimeline
   }
   ```

   Sin default en el constructor: **el compilador pasa a ser el testigo**. Ningún call site futuro
   puede volver a elegir "vacío" por omisión, que es como se llegó aquí.

2. `HistoryContent` decide sobre el sealed (`when`), no sobre un booleano: `Unresolved` → skeleton;
   `Resolved(emptyList())` → `EmptyHistoryState`; `Resolved(_)` → timeline.

3. Los dos fallbacks (`VehiclesState.kt:21`, `VehiclesScreen.kt:252`) pasan a `Unresolved`, que es lo
   que de verdad significa una entrada ausente en `historyCache`.

4. ~~El skeleton se apoya en `PapShimmerBox`, el primitivo único del proyecto.~~
   ⛔ **REFUTADO al implementarlo — fuera de alcance.** Al ir a portarlo se midió dónde se usa de
   verdad cada dialecto, y la premisa era falsa:

   | dialecto | alfa · duración | quién lo usa |
   |---|---|---|
   | `PapShimmerBox` (`PapShimmer.kt:53-54`) | 0.15→0.40 · 600 ms | **sólo placeholders inline pequeños**: glifo del peek 26 dp, barras de texto de `BrowsePeek.kt:237-255`, glifo de `PapSheet.kt:355` |
   | skeleton de LISTA a mano | 0.06→0.16 · 900 ms | Home, `SpotsSkeletonList` (`HomeSheetContent.kt:350-407`) |
   | skeleton de LISTA a mano | 0.06→0.18 · 600 ms | **este**, `HistorySkeletonSection` |

   O sea: el historial **no** es el raro frente al resto de la app — es casi idéntico a su único
   hermano (el de Home), y `PapShimmerBox` nunca ha vestido un skeleton de lista. Portarlo lo dejaría
   ~2,5× más oscuro que el de Home: crearía una inconsistencia en vez de quitarla, y encima un cambio
   visual que el user no pidió. La divergencia real (0.16/900 ms vs 0.18/600 ms, dos juegos de
   constantes que difieren por accidente) sale a ticket propio:
   **`UI-EVERY-SKELETON-BREATHES-THE-SAME-001`**.

**Precedente que confirma el diseño:** Home ya lo hace bien — `HomeViewModel.kt:816` levanta la
bandera con `.onStart { copy(isLoading = true) }` y su `SpotsSkeletonList` (`HomeSheetContent.kt:300`)
sí se ve. `HistoryState` es el único `isLoading` del repo que ningún productor levanta jamás.

## Verificación (31-08-2026)

- `:shared:testDebugUnitTest` + `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` →
  **BUILD SUCCESSFUL**.
- **Falsación del test, medida, no supuesta** (la regla del proyecto: un test que no se ha visto
  fallar no prueba nada). Se reintrodujo el bug cambiando el fallback de `VehiclesState` por
  `HistoryTimeline.resolve(emptyList(), All)` — un "resuelto y vacío", exactamente la mentira
  anterior — y el resultado fue:

  ```
  VehiclesViewModelTest > should_report_history_as_unresolved_when_the_vehicle_is_not_in_the_cache_yet FAILED
  11 tests completed, 1 failed
  ```

  Falla ese test y **sólo** ese. Después se restauró el fallback y los 11 vuelven a verde.
- El otro camino de vuelta al bug (un call site que construya `HistoryState` sin decir si resolvió)
  **ya no compila**: `timeline` no tiene default.

## Criterio de éxito

- Con la DB vaciada, entrar en Vehículos muestra **skeleton** hasta la primera emisión; el "aún no
  hay historial" sólo aparece si de verdad no hay sesiones. Verificable en device (`/run`) tras un
  `pm clear`.
- El fix es **estructural, no de comportamiento observado**: reintroducir el bug (un call site que
  elija vacío por omisión) tiene que **no compilar**. Esto evita el fallo conocido del proyecto —
  *un test de prohibición sin verlo fallar siempre pasa* — porque el testigo aquí es el compilador,
  no una aserción.
- Test unitario sobre el derivado: `VehiclesState.historyState` con `historyCache` vacío devuelve
  `Unresolved`, no una lista vacía resuelta.
- Galería mock actualizada: la variante "Cargando" (`StateGalleryScreen.kt:1198`) deja de ser el
  único sitio del mundo donde ese estado existe.

## Consumidores auditados

Barrido de todo lo que construye o lee `HistoryState` (`grep HistoryState(`):

| sitio | veredicto |
|---|---|
| `HistoryState.kt:8` | ⛔ **origen** — el default que responde por omisión |
| `VehiclesViewModel.kt:58-63` | ⛔ único productor real: debe emitir `Resolved` |
| `VehiclesState.kt:21` | ⛔ fallback miente: cache ausente ≠ vacío |
| `VehiclesScreen.kt:252` | ⛔ fallback miente (mismo caso, por página del pager) |
| `VehiclePageContent.kt:70-93` | 🔎 lector: pasa el estado a `HistoryContent` y lee `statsData` |
| `HistoryContent.kt:200-348` | 🔎 lector: las 3 ramas de render |
| `StateGalleryScreen.kt:1161-1198, 1278-1345` | ✅ galería: "Vacío" y "Sin resolver (skeleton)" son ahora dos variantes distintas, + "Historial sin resolver" en el grupo Vehicles |
| `VehiclesPreviews.kt:19-24` | ✅ previews: `previewHistory` resuelve, + preview `VehiclesHistoryUnresolvedPreview` en paridad con la galería |

**Hallazgo colateral del barrido — dos pares incoherentes que el tipo ya no permite construir.**
Ni la galería ni las previews eran meros call sites tontos: emparejaban a mano listas que se
contradecían entre sí.

- `StateGalleryScreen.kt:1187-1195` ("Filtro: esta semana") declaraba `activeFilter = ThisWeek` pero
  `filteredSessions = FakeData.allSessions` — es decir, la galería enseñaba el filtro semanal
  mostrando el set SIN filtrar. La variante mentía sobre lo que estaba probando.
- `StateGalleryScreen.kt:1298-1304` ("Pocos datos") y `VehiclesPreviews.kt:19-24` omitían
  `statsData`, así que ninguna de las dos ejercitaba jamás el pie de facts ni la celda de plazas
  cedidas — justo lo que `VEH-STATS-SAY-SOMETHING-USEFUL-001` mandó añadir.

Los tres eran posibles porque cada call site derivaba las vistas por su cuenta. `HistoryTimeline.resolve()`
las calcula en un solo sitio (el mismo que usa el ViewModel), de modo que un set filtrado no puede
volver a contradecir a su filtro.

Otros `isLoading` del repo, para descartar que el defecto se repita:

| estado | default | ¿lo levanta alguien? |
|---|---|---|
| `HomeState.kt:67` | `false` | ✅ sí, `HomeViewModel.kt:816` (`.onStart`) — precedente sano |
| `VehiclesState.kt:10` | `true` | ✅ sano por construcción |
| `ParkingHistoryState.kt:9` | `true` | ✅ sano; se baja en `ParkingHistoryViewModel.kt:49,52` |
| `BluetoothConfigState.kt:13` | `true` | ✅ sano por construcción |
| **`HistoryState.kt:8`** | **`false`** | ⛔ **nadie. El único del repo.** |
