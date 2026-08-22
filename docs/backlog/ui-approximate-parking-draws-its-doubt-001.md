# UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001 · Un aparcamiento aproximado se guarda como área pero se dibuja como punto

**Estado:** ✅ Done · en master · descubierto durante `DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001` ·
⏳ falta verlo en device (preset «Aparcado · aproximado (zona de 154 m)»)

## Problema

El dominio distingue desde hace tiempo entre un pin exacto y un **área**:

```kotlin
// UserParking.kt
val zoneRadiusMeters: Float? = null
val isApproximate: Boolean get() = zoneRadiusMeters != null
```

Lo escriben al menos tres caminos — `closed_approximate_zone` (honest close), la zona del
unattended-timeout, y las zonas de `DET-GAP-ANCHOR-ZONE-001` / `DET-WALK-ENTERED-ANCHOR-ZONE-001`.
El KDoc del propio campo dice *«rendered as a circle, never a…»*.

**Pero nadie lo leía.** `grep -rn "isApproximate\|zoneRadiusMeters" composeApp/src` fuera de dominio
y datos → **sólo tests**. Ni `presentation/` ni `ui/` lo consultaban.

Consecuencia: una sesión guardada como área de 154 m y una guardada como punto de 3 m **se pintaban
igual** — mismo marcador, misma card. El trabajo de ser honestos sobre la duda se quedaba en Room.

## ⚠️ Corrección al enunciado original

El doc de apertura decía que el campo *«viaja ya a Room y a Firestore (los mappers lo cubren)»*.
**Es falso, y el propio mapper lo dice** (`ParkingSessionMapper.kt:107`):

```kotlin
// zoneRadiusMeters: local-only honest-close artifact — round-trips Room, never synced (an
// unrefined approximate zone stays on the device that detected it). [DET-HONEST-CLOSE-001]
```

Consecuencias prácticas:
- La zona **NO se puede verificar en Firestore** — sólo en el device que la detectó. El plan de
  campo de `DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001` decía «mirarlo en Firestore
  (`zoneRadiusMeters != null`)»: eso **no funciona**. Con este ticket se mira **en pantalla**, que
  era justamente el agujero.
- No hace falta tocar sincronización: el dato ya llegaba entero a la UI, sólo que nadie lo miraba.

## Doctrina violada

**«Fallo asimétrico: ante la duda se PREGUNTA.»** Preguntábamos (el nudge salía), pero además
**afirmábamos**: el mapa seguía diciendo «tu coche está AQUÍ» con precisión de metro. La mitad
honesta del contrato no llegaba al usuario.

## Diseño — el dato ya estaba, sólo faltaba dejarlo pasar

Tres piezas, ninguna nueva:

1. **`ParkedVehicleSummary` += `zoneRadiusMeters`** (+ `isApproximate` derivado con la MISMA regla
   que `UserParking`, no una segunda). Es la proyección CQRS que el mapa consume;
   `ObserveParkedVehiclesUseCase` la rellena desde la sesión. Cero cambios de repositorio.
2. **Mapa: anillo de duda** reusando el **mismo `Circle` nativo** que las zonas
   [ZONE-AREA-001] — geodésico de verdad, no una aproximación Web-Mercator en Canvas que se
   desvía de la que dibuja Google. El marcador del coche **no se mueve**: el aro dice «en algún
   punto de aquí», no reubica el pin.
3. **Peek: una fila que lo explica**, sólo cuando la sesión es un área.

### Color — por qué NO usa `vehicleIdentityColor`

Los marcadores del mapa flotan sobre *tiles*, no sobre *surfaces*, así que usan tonos fijos de tema
claro (`PapBlueLight` / `PapGreenLight` / `PapOutlineVariantLight`) en vez del resolver de tema.
El anillo usa **esos mismos tres**, con el mismo `when`, porque tiene que leerse como *el marco de
ese coche ensanchado*, no como un color nuevo. Sigue siendo identidad-por-método
[UI-COLOR-DOCTRINE-001]: verde asistido, azul BT, gris sin vigilancia. **El estado no se tiñe.**

### Orden de pintado y tap

- Los aros de duda se añaden **los últimos** a la lista de círculos: si tu coche quedó dentro de una
  zona que tú dibujaste, la incertidumbre sobre el coche es lo que hay que leer primero.
- `onCircleClick` ya resolvía zonas por las coordenadas del centro; ahora, si no es una zona, cae a
  `sessionIdByCoords` — que **ya existía** para el tap del marcador. Tocar el aro selecciona el
  coche: el aro es parte de ese coche, no un objeto aparte.

### Copy

`home_peek_parking_approximate` en los **9 locales**. Causa + consecuencia + remedio, sin mecánica
interna — no menciona pasos, ancla, GPS ni honest close:

> «En algún punto de 154 m: perdimos el final del trayecto. Edítalo para colocarlo.»

`PeekMetaRow` gana un `maxLines` (default 1, así ninguna fila existente cambia) porque esta línea
no cabe en una; con >1 el icono pasa a alinearse arriba, o flotaría en mitad del párrafo.

## Criterio de éxito

- ✅ Una sesión con `zoneRadiusMeters != null` se ve distinta de una exacta, en mapa y en peek.
- ✅ El copy no menciona pasos, zancadas, huecos de GPS ni honest close.
- ✅ Galería + `assembleMockDebug` en sync, strings en los 9 locales.
- ⏳ Verlo en device (preset «Aparcado · aproximado (zona de 154 m)»).

## Consumidores auditados

`grep -rn "isApproximate\|zoneRadiusMeters\|ParkedVehicleSummary" composeApp/src --include=*.kt`

| Consumidor | Clasificación |
|---|---|
| `PaparcarMapView` — marcador del coche | **cerrado** — el aro se añade; el marcador NO se mueve ni cambia de forma |
| `ParkingPeek` — meta rows | **cerrado** — `ApproximateZoneRow` al final, tras la distancia (que se mide al centro del área) |
| `ObserveParkedVehiclesUseCase` | **cerrado** — propaga el radio; 2 tests nuevos |
| `ParkingSessionMapper` (Room ↔ dominio ↔ Firestore) | **exento con razón** — local-only a propósito; no se toca |
| `HomeParkingRow` / card de Home | **abierto a propósito** — ver follow-up |
| Historial (`Vehículos`) | **abierto a propósito** — ver follow-up |
| `AddingParkingPeek` (editar/posicionar) | **exento** — ahí el usuario está colocando el pin; no hay duda que dibujar |

## Follow-up deliberado

La fila y el aro cubren **Home** (mapa + peek), que es donde el usuario busca el coche. La **card de
Home** y el **historial** siguen pintando una zona igual que un pin. No se ha metido aquí porque
`UI-HISTORY-IDENTITY-AND-SOURCE-001` acaba de tocar el historial y conviene verlo en device antes de
volver a entrar. → `docs/backlog/ui-approximate-zone-in-history-001.md`.

## Verificación

- `:composeApp:testProdDebugUnitTest` → **1397 tests, 0 fallos** (1395 en master + 2 nuevos).
- `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` → verdes.
