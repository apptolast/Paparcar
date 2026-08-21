# UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001 · Mi plaza provisional no es "una plaza libre cerca de mí", y una selección no se resuelve por id pelado

**Estado:** ✅ Done · en master vía squash · **1352 tests verdes** · `prod` + `mock` compilan ·
⏳ **verlo en device** (galería: «PapSheet · mi plaza provisional NO se me ofrece»)

## Problema

Field 2026-08-21, 23:46:38 → 23:50:47, Oppo. Durante **4 minutos** el user vio dos cosas que se
contradicen, y una tercera que es un bug duro:

1. «La sesión de aparcamiento sigue estando en Covirán **pero la plaza se ha liberado**» — el mapa
   mostraba a la vez su coche aparcado y una plaza libre, en el mismo punto.
2. «Cuando clico en la plaza liberada, el mapa anima a **mi vehículo** y la modal es la de **mi
   vehículo**, no la de la plaza.»

## Qué está bien y no hay que tocar

El mecanismo de detección **es correcto**. A las 23:46:38 la red de seguridad dedujo la salida
(`DepartureProof.Deduced`) y `ProcessConfirmedDepartureUseCase` hizo justo lo que debe:

- publicó la plaza **provisionalmente** (TTL corto) — la frescura es todo su valor para un extraño;
- **mantuvo viva la sesión y su geocerca** — nada MEDIDO decía que el coche se hubiera movido, y la
  valla es el disparador que cazará la salida real [DET-HANDOFF-NOT-MANUAL-001 §B].

Y se retractó sola a las 23:50:47 (`RetractDeducedDeparture`) cuando el viaje no se probó. Perfecto.
**El bug es que la UI no sabe leer ese estado.**

## Doctrina violada

- **«No copy al usuario con mecánica interna… causa + consecuencia + remedio.»** Aquí ni siquiera
  hay copy: hay dos objetos contradictorios en el mismo pixel y el user tiene que adivinar.
- **«Sistemas, no parches.»** La selección de Home resuelve el TIPO de lo seleccionado comparando
  ids contra dos listas. Eso no es un descuido puntual: es una suposición —*los UUID de plazas y de
  sesiones no colisionan*— escrita en un KDoc (`HomeState.kt:103-107`, *"Both share the same UUID
  space so equality resolves the type"*) y **falsa por construcción**.

## Señales / datos disponibles

La colisión no es accidental ni improbable: **es deliberada y del 100 % de los casos**.

- `ProcessConfirmedDepartureUseCase.kt:67` → `val spotId = session?.id ?: "auto_…"`
- `ReleaseActiveParkingSessionUseCase.kt:43` → `val spotId = currentSession?.id ?: "manual_…"`

La plaza publicada **reutiliza a propósito el id de la sesión**, y con razón: es lo que hace
idempotente el republicado (la promoción y la salida presenciada reescriben el MISMO documento en
vez de duplicarlo). Eso se queda como está.

Lo que rompe es el lado de la UI (`HomeState.kt:234-245`, duplicado en `HomeSlices.kt:125-136`):

```kotlin
val selectedSpot: Spot?
    get() = selectedItemId
        ?.takeIf { id -> activeSessions.none { it.id == id } }   // ← la sesión gana SIEMPRE
        ?.let { id -> nearbySpots.firstOrNull { it.id == id } }
```

Con la sesión viva, la plaza provisional es **literalmente inseleccionable**: el tap la resuelve
como sesión, abre `ParkingPeek` y el marcador que se ilumina es el del coche. Ya estaba anotado como
riesgo H4 en `docs/backlog/home-flow-analysis.md:122`.

## Diseño

Dos cambios. El primero mata la clase entera de bug; el segundo quita la contradicción de raíz.

### A · La selección lleva el TIPO, no un id pelado

`HomeSelection` sellado (`Spot(id)` / `Parking(id)`) sustituye a `selectedItemId: String?` en
`HomeState`, en `HomePeekSlice` y en `HomeIntent.SelectItem`. Cada propiedad derivada resuelve
**dentro de su propio tipo**, sin preguntarle a la otra lista. La precedencia desaparece porque deja
de haber ambigüedad que arbitrar.

Coste: un `when` en vez de un `takeIf`, y que cada call site diga qué está seleccionando — que es
exactamente la información que hoy se tira a la basura en el punto de origen y se intenta reconstruir
después.

### B · Mi propia plaza provisional no se me ofrece a MÍ

Una plaza cuyo id casa con una de MIS sesiones activas es **mi coche**, no un hueco libre. Se excluye
de la lista «Plazas libres cerca de ti` y de los marcadores del mapa, con la misma forma y el mismo
motivo que la regla que ya existe para las retiradas (`nearbySpots.filter { it.status.isAvailable }`,
[DET-HANDOFF-NOT-MANUAL-001 §B.3]): *no está en oferta*. Para el resto de la comunidad sigue
publicada y con su TTL corto intactos — esto es sólo lo que ve su dueño.

Es el único sitio del código que lee la reutilización de id como un **JOIN** en vez de como una
identidad, y va comentado como tal.

Cuando la salida se promociona (`FinalizeDeducedDepartureUseCase`) o se presencia, la sesión se
limpia, deja de casar, y la plaza reaparece con normalidad. Nada que revertir.

### Por qué las dos y no sólo B

B sola dejaría el bug latente: bastaría con que cualquier otro camino ponga una plaza y una sesión
en el mismo id para que el peek volviera a equivocarse, en silencio. A sola dejaría al user viendo
su coche y una plaza libre encima, que es la incoherencia nº1. Una arregla la **identidad**, la otra
la **legibilidad**.

## Criterio de éxito

- Test: con `spot.id == session.id`, seleccionar la PLAZA da `selectedSpot != null` y
  `selectedSession == null`; seleccionar la SESIÓN da lo contrario.
- Test: una plaza que es gemela provisional de una sesión activa mía no sale ni en
  `filteredNearbySpots()` ni en los marcadores del mapa; en cuanto la sesión se limpia, vuelve.
- Suite completa verde.
- En campo: durante una salida deducida el mapa muestra **el coche**, no coche + plaza encima.

## Consumidores auditados

`grep -rn "selectedItemId" composeApp/src` → **0 hits** fuera del KDoc histórico de
`HomeSelection.kt`. Los 18 sitios que había:

| Sitio | Clasificación |
|---|---|
| `HomeState` (campo + `selectedSession` / `selectedSpot` / `isParkingSelected`) | **cerrado** — cada lado resuelve en su propio tipo; `isParkingSelected` pasa a derivarse de `selectedSession` en vez de repetir la búsqueda |
| `HomeSlices.HomePeekSlice` (mismo trío, espejo) | **cerrado** — idéntico, para que slice y state no puedan discrepar del tipo |
| `HomeStateTransitions.clearedModeFields` / `applyNewSpots` | **cerrado** — la poda comprueba cada tipo contra SU lista (antes una sesión con el mismo id validaba una selección de plaza) |
| `HomeIntent.SelectItem` | **cerrado** — lleva `HomeSelection?` |
| `HomeViewModel` (reducer + limpieza tras `ReleaseParking`) | **cerrado** |
| `HomeScreen` (`selectedSpotId`, `selectedSessionId`, `dimSpots`, `overlayVisible`, los 2 handlers de marcador) | **cerrado** — el tipo se fija en el punto del tap |
| `HomeSheetContent` (fila de plaza + card de vehículo) | **cerrado** |
| `HomeSheetPositioning.SheetTransitionEffects` | **cerrado** — sólo lo usaba como clave de `LaunchedEffect`; el sealed es data class, así que la igualdad sigue funcionando |
| `HomePeekHandle` | **cubierto por convergencia** — su `when` ya prefería el spot; ahora los dos brazos son excluyentes por construcción, así que el orden es presentación y no arbitraje. Comentado en el sitio |
| `SpotPeek` / `ParkingPeek` (`SelectItem(null)`) | **exento** — deseleccionar no tiene tipo |
| FAB de coche aparcado (`HomeScreen`, cicla sesiones) | **cubierto** — pasa por `onMyCarMarkerClick`, ya tipado |
| `ObserveNearbySpotsUseCase` | **exento con razón** — es de dominio y no conoce sesiones; el filtro de propiedad vive en la proyección de Home, que es donde ambos hechos existen |
| Previews (`HomeSheetPreviews`) + galería mock (`StateGalleryScreen`) + `HomeSlicesTest` / `HomeViewModelTest` | **cerrado** — migrados |

## Resultado

- **1352 tests verdes** (1347 → +5 nuevos), `prod` compila, `assembleMockDebug` compila.
- Variante nueva en la galería: **«PapSheet · mi plaza provisional NO se me ofrece (salida
  deducida)»**, montada con el estado exacto del campo (sesión viva + gemela provisional con su id).
- Sin strings nuevos → nada que tocar en los 9 locales. Sin `detectionPath` nuevo. Sin cambio de
  dominio ni de persistencia: la publicación a la comunidad y su TTL corto quedan intactos.

