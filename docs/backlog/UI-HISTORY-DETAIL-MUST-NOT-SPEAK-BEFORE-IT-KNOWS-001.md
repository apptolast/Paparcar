# UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001 · El detalle histórico no afirma nada hasta que ha resuelto su sesión

**Estado:** ✅ Done — mergeado a master el 31-08-2026
**Pendiente de device:** ⏳ el skeleton y la cara «ya no está» sin ver en mano.
**Abierto:** 31-08-2026 · sobre master `748648fc`

## Problema

Al abrir un aparcamiento del historial en el mapa, la pantalla habla antes de saber. Tres fallos
distintos, el mismo origen.

**1. No hay estado de carga, y la bandera que lo diría no la lee nadie.** `ParkingHistoryState.isLoading`
arranca en `true` y sólo la baja el stream de **GPS** (`ParkingHistoryViewModel.kt:49`): no significa
"ha llegado el historial", significa "ha llegado un fix. Ningún consumidor la lee — la pantalla no
tiene rama de carga. Es la misma familia que `UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001`
(`9aab82eb`), aquí en la otra dirección: la bandera se mantiene y nadie la mira.

**2. Mientras no ha resuelto, la tarjeta afirma "sin dirección".** `focusedSession` busca el id dentro
de `allSessions` (`ParkingHistoryState.kt:29`); hasta que Room emite es `null`, y con `null` la tarjeta
pinta `parking_detail_no_address` — *"Sin dirección"* — con stepper y CTA deshabilitados. El usuario ve
una ficha que niega los datos de su propio aparcamiento.

**3. Y el mapa puede plantar el pin de OTRO aparcamiento.**

```kotlin
parkingLocation = focusedSession?.location ?: parkingGpsPoint ?: state.userParking?.location
```
`ParkingHistoryDetailScreen.kt:214`. El tercer fallback es la **sesión activa de ahora**. Entrando sin
coords en el nav-arg (deep link, o navegación con `sessionId` a secas), el mapa dibuja el coche donde
está aparcado HOY mientras resuelve el histórico. No es un hueco vacío: es el dato de otra sesión
presentado como si fuera este.

**Raíz común:** `focusedSession` devuelve `null` para dos cosas que no son la misma — *"el historial
aún no ha llegado"* y *"ese id no está en el historial"* (sesión retractada, borrada, deep link muerto).
Un solo `null` para dos preguntas, y la UI elige el peor significado de los dos.

## Doctrina violada

- **"Ante la duda se PREGUNTA, nunca se planta un dato fantasma"**, aplicado a la UI: una pantalla que
  no sabe no rellena con el dato que tenga más a mano — y menos con el de OTRA sesión.
- **`DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001`** (`c604a058`) y
  **`UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001`** (`9aab82eb`): un default es una
  respuesta silenciosa permanente. Aquí el default no es un booleano, es un `null` sobrecargado.
- **Sistemas, no parches**: no se arregla añadiendo un `if` antes de la tarjeta, sino haciendo que el
  estado no pueda expresar las dos dudas con el mismo valor.

## Señales / datos disponibles

Todo está ya en el ViewModel; no hace falta nada nuevo:
- `observeAllSessions()` (`ParkingHistoryViewModel.kt:35`) sabe perfectamente si ha emitido. Hoy su
  primera emisión y "no hay nada" son indistinguibles porque ambas acaban en una lista vacía.
- `focusedSessionId` sabe qué se pidió; `allSessions` sabe qué hay. Con las dos se distingue
  "todavía no" de "no está", que son las dos ramas que la UI necesita.

## Diseño

1. `allSessions: List<UserParking>?` **sin default** — `null` = Room no ha emitido. El compilador
   obliga a cada call site a decir si el historial llegó.
2. La pregunta de la pantalla se responde con un tipo, no con un `null`:
   ```kotlin
   sealed interface FocusedParking {
       data object Unresolved : FocusedParking          // el historial aún no ha llegado
       data object NotFound : FocusedParking            // llegó, y ese id no está
       data class Resolved(val session: UserParking) : FocusedParking
   }
   ```
3. La pantalla ramifica sobre ese tipo: `Unresolved` → **skeleton** en la ficha; `NotFound` → un texto
   honesto ("este aparcamiento ya no está en tu historial"); `Resolved` → la tarjeta de siempre.
4. **Se retira el fallback a `userParking`.** Sin sesión resuelta el mapa no pinta pin: `parkingGpsPoint`
   (las coords que el llamante pasa de ESTE aparcamiento) sigue sirviendo de semilla, que es legítimo.
   Al caer el fallback, `state.userParking` y su suscripción a `observeActiveSessions()` quedan sin uso
   → se borran (una suscripción a Room menos).
5. `isLoading` se retira: la lee nadie y lo que mide es el GPS.
6. Skeleton con **`PapShimmerBox`**, siguiendo a `PeekLocationSkeleton` (`BrowsePeek.kt:227-258`) — el
   precedente del mismo molde de sheet. (No aplica aquí el hallazgo de `UI-EVERY-SKELETON-BREATHES-THE-SAME-001`:
   ese va de skeletons de LISTA, que llevan otra rampa.)
7. Superficie muerta que el barrido destapa, borrada en limpio: `state.spots` nunca se rellena (la
   pantalla pasa `spots = emptyList()`), `onSpotClick = {}`, y con ellos `ParkingHistoryIntent.OnSpotSelected`
   y `ParkingHistoryEffect.NavigateToSpotDetails` son inalcanzables.

## Criterio de éxito

- Abrir un histórico desde la lista y desde un deep link sin coords: nunca aparece "Sin dirección"
  transitorio ni el pin del aparcamiento de hoy.
- Reintroducir el `null` sobrecargado **no compila** (el tipo no tiene default).
- Test: con `allSessions = null` el estado responde `Unresolved`; con lista emitida y un id ausente,
  `NotFound`; con el id presente, `Resolved`. Falsación medida, no supuesta.
- Galería mock con las tres caras.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `ParkingHistoryState.kt` | ⛔ origen: `null` sobrecargado + `isLoading` que mide el GPS |
| `ParkingHistoryViewModel.kt:23-29` | ⛔ `observeActiveSessions()` sólo alimentaba el fallback → se retira |
| `ParkingHistoryViewModel.kt:47-55` | ⛔ mantiene `isLoading` que nadie lee |
| `ParkingHistoryDetailScreen.kt:214` | ⛔ el pin de otra sesión |
| `ParkingHistoryDetailScreen.kt:425-432` | ⛔ "Sin dirección" como estado de carga |
| `ParkingHistoryDetailScreen.kt:207,221` | ⛔ `spots`/`onSpotClick` muertos |
| `StateGalleryScreen.kt:243` | 🔧 usa `HistoryDetailSheet` directamente (map-free) — añadir las caras nuevas |
