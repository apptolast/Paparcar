# UI-PREFERRED-SESSION-RECENCY-001 · La sesión preferida es la última aparcada, no la del coche BT

**Estado:** 🟡 Commiteado en rama, **sin mergear** · rama `bugfix/UI-PREFERRED-SESSION-RECENCY-001-preferred-session-recency` · worktree `../Paparcar-preferred-session`

Va en un **commit único** junto a [DET-READY-TRIP-OVER-PARKED-001](det-ready-trip-over-parked-001.md),
ya **rebasado 16-08 sobre master `4400a583`** (único conflicto: la entrada del log de detección; ver
el doc del otro ticket). Suite verde tras el rebase: **1176 tests**, mock y prod compilando.
⏳ Pendiente: validar en device (abrir la app con Kamiq BT + Focus aparcados, Focus más reciente →
debe enfocar el Focus).

> El resolver vive ahora en dominio (`domain/model/PreferredParkingSession.kt`) porque
> [DET-READY-TRIP-OVER-PARKED-001](det-ready-trip-over-parked-001.md), abierto sobre esta misma rama,
> lo necesita en `ObserveDetectionReadinessUseCase`. `preferredSession()` de `HomeState` queda como
> delegación fina.

## Problema
Con dos coches aparcados (Kamiq con BT + Focus activo sin BT), al abrir la app la cámara, el peek
y el midpoint enfocan SIEMPRE el Kamiq, aunque el usuario lleve días usando el Focus. Reportado en
campo (ago 2026): "siempre que entro en la app navega directamente al BT... pero estoy cogiendo el
vehículo activo estos días".

Causa: `preferredSession()` (`HomeState.kt`) elige la sesión que representa "tu aparcamiento"
cuando no hay nada seleccionado usando `monitoringStatus().sortRank()` — Bluetooth > Active >
Inactive. Es un ranking de *confianza de vigilancia* contestando una pregunta de *uso*.

## Doctrina violada
Ninguna doctrina de detección. Es un error conceptual de UI: dos preguntas distintas contestadas
con el mismo ranking:
1. "¿Qué coche está mejor vigilado?" → BT > Active > Inactive (correcto para cards, identidad, badge).
2. "¿Qué aparcamiento me importa AHORA?" → pregunta de recencia de uso, no de vigilancia.

## Señales / datos disponibles
- `UserParking.location.timestamp` = instante del park (estampado en la confirmación; el DAO ya
  ordena `ORDER BY timestamp DESC` en todas sus queries).
- `ParkingLocationViewModel` ya resuelve "mi parking" por recencia (`observeActiveSessions()
  .firstOrNull()` sobre ese orden) — el cambio ALINEA Home con lo que History ya hace.

## Diseño
El invariante vive en UN sitio: `preferredSession(activeSessions, vehicles)` en `HomeState.kt`,
consumido vía `HomeState.userParking` y `HomePeekSlice.userParking`.

Nuevo criterio: **la sesión con `location.timestamp` más reciente gana**; el ranking de vigilancia
(`sortRank`) queda solo como desempate (timestamps idénticos); empate total → orden de lista.
Se autocorrige: el día que se vuelva al coche BT, su nuevo park será el más reciente.

`sortRank` NO se toca — las cards de vehículos y la identidad siguen ordenando por método.

## Criterio de éxito
- Test: sesión más reciente de un coche sin BT gana a una sesión más antigua del coche BT.
- Test: a igual timestamp, gana el mejor vigilado (BT).
- En device: con Kamiq (BT) y Focus aparcados, si el Focus se aparcó después, la app abre
  enfocando el Focus.

## Consumidores auditados
`preferredSession` / `userParking` (Home):
- `HomeState.userParking` (get) → cámara inicial, `HomeScreen` LaunchedEffect (:737), midpoint (:729), sheet (:594) → **cubiertos por el cambio único**.
- `HomeSlices.kt:122` `HomePeekSlice.userParking` → mismo resolver → cubierto (KDoc actualizado).
- `HomePeekHandle.kt:169/175` → fallback de release/peek → cubierto.
- `parkedWatchBadge` (`HomeState`) → ahora refleja la vigilancia del coche ENFOCADO (más correcto:
  el badge habla del coche que miras). Cambio de comportamiento aceptado en la decisión del ticket.
- `ParkingLocationViewModel` → ya usaba recencia; sin cambio, ahora coherente.
- `HomeTripController.monitoredVehicle` (puck) → pregunta distinta (qué coche sigue la estrategia
  activa), NO usa preferredSession → exento.
