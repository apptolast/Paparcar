# PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK-001 · corregir el sitio no puede reiniciar el reloj

**Estado:** ✅ Done — en master. Sale del diagnóstico de campo de
[[home-an-unseeded-camera-is-not-a-place-001]] (16-09).

## Problema

La hoja de editar aparcamiento promete, literalmente, el eje que separa sus dos botones:

> «Corregir mantiene el tiempo aparcado; marcar uno nuevo lo pone a cero»
> (`home_add_parking_helper_secondary_edit`)

Y el código hace lo contrario. `UpdateParkingLocationUseCase.kt:60`:

```kotlin
// Stamp the timestamp to "now" so the session timeline reflects the
// user's deliberate correction, not the auto-detect moment.
val stamped = newLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds())
```

Medido en campo el 16-09: pin creado a las 10:42:55, corregido a las 10:49:00 y a las 10:51:14 →
`timestamp` final `1789548674013` = 10:51:14, y el peek mostrando **«Aparcado hace <1m»** sobre un
aparcamiento de ocho minutos.

No es solo la frase: `timestamp` es el reloj de «aparcado desde», así que una corrección también
falsea la **duración** de la sesión en historial y estadísticas.

## Doctrina violada

*No copy al usuario con mecánica interna ni promesas que el código no cumple.* Y el eje es lo único
que distingue los dos confirms de esa hoja
([COPY-PARKING-EDIT-THREE-ANSWERS-ONE-QUESTION-001]): si corregir también pone el reloj a cero, los
dos botones hacen lo mismo y la frase que los separa es falsa.

## Diseño

Manda el copy: **corregir mueve el SITIO, no la hora.** `UpdateParkingLocationUseCase` deja de
estampar `timestamp`; el re-park (`asNewSession = true`) ya crea una sesión nueva y por tanto ya
tiene su propio reloj, que es donde el cero es verdad.

⚠️ Revisar por qué se estampaba: el comentario alega paridad con `ConfirmParkingUseCase`, que
normaliza timestamps al escribir. Hay que comprobar si algo aguas abajo (geocerca, safety-net,
backfill, enriquecimiento) usa `timestamp` como «cuándo empezó a vigilarse» en vez de «desde cuándo
está aparcado» — si es así, son dos hechos distintos con un solo campo, y eso es lo que hay que
arreglar.

## Criterio de éxito

- Corregir un pin de 8 minutos deja el peek diciendo «Aparcado hace 8 min».
- «Marcar aparcamiento nuevo» sigue poniéndolo a cero.
- Test de `UpdateParkingLocationUseCase` sobre el `timestamp` conservado, y la duración en el
  historial sin alterar tras una corrección.

## Consumidores auditados

`grep -rn "location\.timestamp"` — el campo resultó ser **el único reloj de la sesión**, con tres
familias de lectores. Ninguna pierde nada al dejar de estamparlo; dos estaban siendo falseadas:

| Consumidor | Qué lee | Veredicto |
|---|---|---|
| Peek «Aparcado hace …» · `SharedCarMirror.parkedAtMs` | aparcado desde | **Cerrado** — era la mentira visible. |
| `ParkingClusters` · `ParkingHeatmap` · `ComputePeriodStatsUseCase` · `RunMonthRecapUseCase` | duración de estancia | **Cerrado** — cada corrección acortaba la estancia. |
| `HistoryEntitlementGate:30` | ventana de 7 días | **Cerrado** — una corrección movía la sesión dentro de la ventana. |
| `PreferredParkingSession:33` | orden de sesiones | **Cerrado por convergencia.** |
| `DetectParkingDepartureUseCase:138` · `EvaluateSafetyNetCheckUseCase:206` | `sessionStartMs` de [DET-SESSION-BIRTH-001] | **Cambio deliberado** — la frontera pasa a medirse contra el nacimiento REAL. Ver riesgo abajo. |
| `EvaluateArEnterArmUseCase:85` | admisibilidad del AR ENTER | **Cambio deliberado**, mismo motivo. |
| veto de identidad BT (`lastBtConnectedAtMs < sessionStartMs`) | identidad | **Mejora** — un connect posterior al aparcamiento sí prueba identidad; antes se descartaba por haber corregido el pin. |
| `ConfirmParkingUseCase:259` | edad de la sesión anterior | **Exento** — mira OTRA sesión, no la corregida. |
| `ObserveAdaptiveLocationUseCase` · `StopTracking` | el `timestamp` del fix en vivo | **Exento** — ahí sí es el reloj de la medición, no el de la sesión. |
| `SpotDtoMapper:57 reportedAt` | plaza publicada | **Exento** — lo estampa la publicación. |

**Riesgo del cambio deliberado:** lo único defendible del estampado era que, tras mover el pin, una
evidencia vieja no dispare una salida falsa. Ya estaba cubierto: el `departureEventBus.reset()` de
ese mismo caso de uso borra el `lastVehicleEnteredAt` anterior ([BUG-WALK-DEPART-001]), que es
exactamente la evidencia que el estampado neutralizaba.

## Lo hecho

- `UserParkingDao.updateLocation` — el `UPDATE` deja de escribir `timestamp` (columna intacta, sin
  migración de Room) y el parámetro desaparece.
- `UserParkingRepositoryImpl` — deja de pasarlo.
- `UpdateParkingLocationUseCase` — fuera el estampado de `now` (y el `Clock` que ya no se usa).
- ⛔ **Los DOS fakes** de `UserParkingRepository` (`commonTest` y el del Dev Catalog) copiaban la
  `location` entera: el reloj nuevo entraba por la puerta de atrás. Espejan el DAO. El test nuevo
  falló primero justo por esto, con el fake equivocado arreglado — la prueba de que el guard mira.
- Tests: `should_keepTheParkedSinceClock_when_theUserCorrectsTheLocation` (dominio) y una aserción
  nueva en `ParkingAddressSurvivesEnrichmentTest`, que corre contra el **UPDATE real de Room** —
  la autoridad, porque un fake puede espejar mal.
- `docs/detection/PARKING-DETECTION.md` §2 con la entrada del fix.
- Sin strings nuevos, sin pantallas ni estados nuevos (no toca Dev Catalog), sin `detectionPath`
  nuevo: `user_moved` ya existía. iOS viaja solo, todo es `commonMain`.

**Verificación:** `./gradlew testAll` → **3312 tests, 0 fallos**.

⏳ **Pendiente:** verlo en device.
