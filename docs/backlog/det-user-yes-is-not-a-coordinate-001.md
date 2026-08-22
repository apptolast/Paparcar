# DET-USER-YES-IS-NOT-A-COORDINATE-001 · el "sí" del usuario dice QUE aparcó, no DÓNDE

**Estado:** ✅ Done · rama `bugfix/DET-USER-YES-IS-NOT-A-COORDINATE-001-user-doubt` ·
worktree `../Paparcar-user-yes`

## ⚠️ Corrección de la hipótesis inicial

El diagnóstico de campo del 22-08 abrió este ticket afirmando que *"la vía `user` estampa fiabilidad
1.0 sobre el pin peor localizado del día"*. **Esa premisa era falsa** y conviene que quede escrito:

```kotlin
/** Probability [0.0, 1.0] that this is a genuine parking EVENT.
 *  1.0 = user manually confirmed; ~0.90 = vehicle-exit signal observed; … */
val detectionReliability: Float? = null      // UserParking.kt:31
```

`detectionReliability` mide **si el aparcamiento ocurrió**, no con cuánta precisión se localizó. Un
"sí" explícito del usuario es, por definición, certeza sobre el evento: **1.0 es correcto** y no se
toca.

Tampoco es cierto que el pin del Redmi de ese viaje se plantara donde estaba el cuerpo: usó `loc#41`
(el mejor fix de la llegada, `acc 15,7 m`), que es la elección conceptualmente correcta. Sus 12 m de
error son calidad de GPS de ese aparato, no un fallo de lógica — el Oppo, en el mismo sitio y minuto,
tuvo 2,8 m. **Este ticket NO arregla aquel síntoma**; lo que lo habría evitado es
`DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001`, que impide que ese viaje llegara siquiera a preguntar.

Lo que sí queda en pie, y es un agujero real, es otra cosa.

## Problema

La vía `user` razona con mucho cuidado **cuál** es la ubicación correcta y luego la planta como
**punto exacto pase lo que pase** — incluso en la rama donde acaba de concluir que no sabe dónde está
el coche. Del propio comentario en `CoordinatorParkingDetector.kt:1309`:

> *"a gap-born anchor may be a drive-past point hundreds of meters out with **unboundable forward
> error**, so it never wins here"*

…y acto seguido:

```kotlin
completed = runConfirm(
    location = locationToConfirm,
    reliability = config.reliabilityUserConfirmed,
    vehicleId = activeVehicleId,
    pathLabel = "user",
)                                   // zoneRadiusMeters queda en null por defecto
```

Se descarta un ancla por no ser fiable, se cae al fix actual… y se afirma un punto exacto. La duda
que motivó el descarte **no se registra en ninguna parte**: `zoneRadiusMeters` queda `null`, y
`UserParking.isApproximate` (que se deriva de él) dice `false`.

El contraste está en el mismo fichero: ante EXACTAMENTE la misma situación, la vía de timeout
(`EvaluateUnattendedParkingSaveUseCase:272-284`) acota la duda y guarda **zona**:

```kotlin
if (input.anchorGapMs > 0L) {
    val walkableInsideHole = input.anchorGapMs / MILLIS_PER_SECOND * config.maxPedestrianSpeedMps
    return zoneOrAsk(reason = GAP_ANCHOR, center = anchor, doubtMeters = walkableInsideHole, …)
}
```

## Doctrina violada

Es el corolario de barrido que ya nos costó una tarde con `DET-DRIVE-PROOF-001` →
`DET-DEPART-PROOF-001`: **cerrar solo la vía donde mordió no basta.** `DET-GAP-ANCHOR-ZONE-001`
enseñó a acotar la duda de un hueco y `f42e393b` enseñó al cierre honesto a dibujar zona cuando el
fix no da para un punto. La vía `user` no recibió ninguno de los dos barridos.

Y de fondo: *fallo asimétrico*. Un punto exacto falso manda al usuario a un sitio concreto y
equivocado; una zona le dice la verdad de lo que sabemos.

## Diseño

1. **Una sola fórmula de radio.** El cálculo que vivía dentro de `saveUnattendedZone` se extrae a
   `approximateZoneRadius(center, doubtMeters)` y lo usan las dos vías. Cero fórmulas nuevas — el
   refactor profundo tiene anotado como bug #9 la divergencia de fórmulas duplicadas.
2. **La vía `user` hereda la duda de la ubicación que elige.** Si cayó al fix actual porque el ancla
   nació en un hueco de GPS, la duda es la misma cota que usa el timeout: lo que se puede andar
   dentro del hueco.
3. **Zona solo cuando de verdad supera lo que un punto puede afirmar.** El umbral es el suelo de zona
   que ya existe (`honestCloseMinZoneRadiusMeters`, 60 m): por debajo de él una zona es menos
   informativa que el punto, así que el punto se queda. **Consecuencia deliberada:** el caso Redmi de
   15,7 m sigue siendo un punto exacto. Este ticket no ensucia los pines buenos; solo deja de mentir
   en los que ya se sabían dudosos.

`reliability` sigue siendo 1.0 en todos los casos: el usuario confirmó el evento. Lo que cambia es
que la incertidumbre **de posición** deja de perderse.

## Criterio de éxito

- Test: "sí" del usuario con ancla nacida en un hueco de GPS largo → se guarda **zona** con radio
  acotado por el hueco, `isApproximate = true`, `reliability` **1.0**.
- Test de regresión: "sí" del usuario con ancla normal y fix decente → **punto exacto**,
  `zoneRadiusMeters = null` (comportamiento de hoy intacto).
- Ambos tests verificados con el guard neutralizado, para que no sean tests complacientes.

## Consumidores auditados

`grep -rn "runConfirm(" composeApp/src/commonMain --include=*.kt`

| Call site | Clasificación |
|---|---|
| `CPD:1375` — vía `user` directa | **cerrado** — es donde mordía |
| `CPD:1140` — "Sí" **durante un hold** (`runConfirm(pending.location, reliabilityUserConfirmed, …, "user")`) | **cubierto por convergencia** — es un segundo call site con `pathLabel = "user"`, así que se auditó expresamente. Un hold solo se abre desde una vía automática, y `DET-GAP-ANCHOR-001` ya prohíbe que un ancla nacida en hueco pinche en silencio (demostrado por el test `should_…_gap_entered_anchor must never pin silently`, `CPDTest:2417`): con hueco no hay hold, hay prompt. Si algún día lo hubiera, el guard que lo impide es ese, no este |
| `CPD:852`, `CPD:1142`, `CPD:1604` — cierres de hold automáticos | **exento** — no son la vía `user`; su duda la gobierna el evaluador que abrió el hold |
| `CPD:1485` | **exento** — vía de backfill/red de seguridad, con su propia fiabilidad |
| `saveUnattendedZone` (`CPD:1847`) | **cerrado** — ahora comparte la fórmula de radio en vez de tener la suya |
| `EvaluateUnattendedParkingSaveUseCase:276` | **cerrado** — pasa a usar `walkableInsideGapMeters` compartida; su constante privada duplicada se retiró |

## Predicado extraído

La cota del hueco la necesitaban ya dos veredictos, así que en vez de duplicar la constante
`MILLIS_PER_SECOND` se extrajo a `domain/detection/GapDoubt.kt` →
`walkableInsideGapMeters(gapMs, maxPedestrianSpeedMps)`, siguiendo el patrón de `SpeedBandClock.kt` /
`HumanPoweredRide.kt` [DET-VERDICT-NOT-PREDICATE-001]. Función pura, directamente testeable, sin
ceremonia de clase inyectada.

## Honestidad sobre los tests

- `should_saveBoundedZone_when_user_confirms_over_a_gap_entered_anchor` **discrimina**: neutralizando
  el guard (`zoneRadiusMeters = null`) falla, con él pasa.
- `should_keep_the_exact_pin_when_user_confirms_over_a_witnessed_anchor` **pasa en ambos casos a
  propósito**: es un guard de regresión cuyo trabajo es demostrar que el cambio NO convierte pines
  buenos en manchas de 60 m. No se presenta como prueba del fix.

## Estado final

- ✅ **1397 tests verdes** (`testProdDebugUnitTest`), incluidos los 2 nuevos.
- ✅ `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid`.
- Sin strings nuevos. `zoneRadiusMeters` ya existía en Room (v14), en el mapper y en
  `UserParking.isApproximate` → sin migración ni cambios de DTO.
- ✅ El pendiente heredado de `DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001` (**la UI no dibujaba el radio
  de zona**) se cerró en la misma tanda con `UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001` (master
  `4092044e`), así que una zona guardada por esta vía **sí se ve** ahora como anillo de duda en el
  mapa, no solo en Firestore/Room.
- ⏳ Campo: un "sí" tras un hueco de GPS largo debe guardar zona.
