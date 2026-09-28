# DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001 · un carril retira la plaza que publicó el otro, y le gasta su única nominación

**Estado:** ✅ Done (28-09-2026) · rama `bugfix/DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001-own-nomination` · ⏳ sin ver en campo, y puede que nunca se vea: la ventana es una carrera. Lo que sí se verá si ocurre es el `SPOT_RELEASE action=SKIPPED reason=not_my_nomination` · ⚠️ iOS escrito y revisado, **no compilado**

## Problema

`withdrawSpotRelease(lane)` retira **lo que haya pendiente**, venga de donde venga. El KDoc del sitio
que la llama al cerrar el job del Coordinator afirma otra cosa:

> *«A nomination STILL PENDING at the end of **the trip it opened** means the departure never came»*
> — `CoordinatorDetectionService.kt:2430`

Pero la llamada no compara nada. El sello `PendingSpotRelease` guarda `spotId`, `vehicleId` y
`nominatedAtMs`; **ninguno dice quién lo puso**, y el use case no recibe con qué compararlo. «El
viaje que la abrió» es una afirmación del comentario, no del código.

Dentro del carril del Coordinator la afirmación se sostiene por accidente: la retirada vive en el
camino de retorno NORMAL, y un job superseded sale por `CancellationException` sin pasar por ahí. Lo
que no se sostiene es **entre carriles**, porque las dos mitades las invocan jobs distintos:

| Quién nomina | Job | Quién retira | Job |
|---|---|---|---|
| ACL CONNECT del coche emparejado (`:1732`) | `btWatchJob` | `BtDepartureOutcome.NeverDrove` (`:1769`) | `btWatchJob` |
| AR `IN_VEHICLE` ENTER en fence propia (`:1259`) | `detectionJob` | fin normal del job (`:2430`) | `detectionJob` |

`detectionJob` retira lo que puso `btWatchJob`. Y no es teórico: **`handleBtConnected` cancela
`btHuntJob`, no `detectionJob`**, así que un job del Coordinator que ya estuviera corriendo cuando
llega el CONNECT sigue vivo y termina por su cuenta unos segundos después.

### La ventana, con los datos del 27-09

Esa noche hubo **tres** jobs del Coordinator armados por `ARM:SIGNIFICANT_MOTION` (sentry-wake) que
retornaron normalmente en **18-36 s**, con `aborted_false_enter` y honest close `too_close`/silent:

```
20:31:15 → 20:31:37   (22 s)  aborted_false_enter · steps 21
20:33:14 → 20:33:36   (22 s)  aborted_false_enter · steps 13
20:42:15 → 20:42:34   (19 s)  aborted_false_enter · steps  8
```

Un CONNECT dentro de cualquiera de esas ventanas habría publicado la plaza y la habría visto caer
segundos después, retirada por un job que no la puso. **No pasó** —el `parkdiag` del informe
`1790592808332` lo descarta: la única nominación de la noche es la de las 23:38:58, y el job del
Coordinator ya se había destruido a las 23:38:46, doce segundos antes—. Así que la evidencia de este
ticket es **de código, no de campo**: el camino existe y nada lo impide.

⛔ **Y hay una segunda herida, peor que la primera.** `WithdrawSpotReleaseUseCase` **conserva el sello
a propósito** —para que un enlace BT que se cae y vuelve no re-anuncie el mismo coche parado— y
`EvaluateSpotReleaseNominationUseCase` salta con `already_nominated` mientras
`pending.spotId == session.id`. Ese razonamiento es correcto cuando la retirada fue una
**refutación** («el coche no llegó a salir»). Cuando la retirada fue el accidente de otro carril, la
sesión queda **quemada para siempre**: esa plaza ya no puede volver a nominarse, ni en el embarque
real de tres horas después. Se pierde la plaza y se pierde la ventaja, en silencio.

## Doctrina violada

- **Sistemas, no parches.** El invariante —*sólo el carril que abrió una nominación puede cerrarla*—
  no vive en ningún sitio. Está enunciado en un comentario y en ninguna línea ejecutable.
- **Fallo asimétrico.** Aquí muerde en las dos direcciones a la vez, que es lo raro: se retira una
  plaza que sí iba a liberarse (falso negativo para la comunidad) **y** se gasta la única publicación
  de la sesión (el embarque real se queda mudo). Ninguna de las dos deja rastro que el usuario vea.
- **[DET-ONE-SERVICE-TWO-LANES-001]**: *carriles separados = slots + use cases separados, mismo host*.
  Los slots de job están separados; el sello que comparten, no.

## Señales / datos disponibles

- `PendingSpotRelease` ya es durable (DataStore en Android, `NSUserDefaults` en iOS) y ya viaja con
  `spotId` + `vehicleId` + `nominatedAtMs`. Falta un campo, no una estructura.
- El carril que pide ya llega a los dos use cases: `SpotReleaseLane.BT` / `.COORDINATOR`, entró con
  [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] (`33aabb9ef`) **para telemetría**. Este ticket lo
  asciende de dato observado a dato que decide.
- `DetectionEvent.SpotRelease` ya existe, así que la negativa nueva es una razón más en una columna
  que ya viaja — cero superficie de serializador.

## Diseño

**El sello dice de quién es, y la retirada sólo puede cerrar la suya.**

```kotlin
data class PendingSpotRelease(
    val spotId: String,
    val vehicleId: String?,
    val nominatedAtMs: Long,
    val lane: String,          // ← nace aquí: QUIÉN la puso
)
```

`WithdrawSpotReleaseUseCase` gana una sola puerta, antes de la de la TTL:

```kotlin
if (pending.lane != lane && pending.lane != LANE_LEGACY) {
    record(SpotReleaseAction.SKIPPED, pending.spotId, lane, REASON_NOT_MY_NOMINATION)
    return false
}
```

Tres decisiones y su porqué:

1. **El carril, no el `vehicleId`.** Comparar vehículos no arregla nada: el caso real es que los dos
   carriles hablan del **mismo coche**. Y no hace falta bajar al job: dentro del Coordinator la
   supersede no llega a la retirada (sale por `CancellationException`), así que «el job que termina
   es el viaje que abrió» ya se cumple ahí. Lo que falta es exactamente el límite entre carriles.
2. **Se niega, no se relaja.** Al negarse, el sello **no se toca** y la plaza sigue en pie — con su dueño legítimo todavía vigilándola (`btWatchJob` la retirará
   en `NeverDrove`) y con la TTL de 4 min como suelo si ese dueño muere. La retirada ajena deja de
   ser destructiva sin volverse permisiva.
3. **`LANE_LEGACY` para el sello viejo.** Un sello escrito por el build anterior no tiene carril. Se
   lee como `legacy` y lo puede retirar cualquiera — la conducta de hoy. La alternativa (que no lo
   pueda retirar nadie) dejaría de pie, tras el update, un fantasma que nada puede tumbar salvo la
   TTL. Vive 4 minutos: no merece una migración, merece una constante y esta línea.

⛔ **No se toca la promoción.** `ProcessConfirmedDeparture` y `ReleaseActiveParkingSession` retiran el
sello por `clearPendingSpotRelease()` comparando `spotId`, que es otro camino y sigue siendo correcto:
una promoción no es una retirada, y quien promueve es el dueño del documento, no el de la nominación.

## Criterio de éxito

1. Un job del Coordinator que termina con una nominación del carril BT viva **no la toca**, y lo dice:
   `SPOT_RELEASE action=SKIPPED reason=not_my_nomination source=coordinator`.
2. **La plaza sobrevive a la negativa**: sigue publicada, con su dueño vigilándola, así que la
   promoción todavía puede llegar cuando el coche arranque de verdad.
   ⚠️ Lo que NO cambia —y no debe cambiar— es que una retirada **legítima** siga gastando la
   nominación de la sesión: eso es una refutación medida («el coche no llegó a salir») y el sello
   tiene que sobrevivirla. Lo que este ticket quita es que la gaste un job que no la puso.
3. Cada carril sigue retirando la suya sin cambio de conducta.
4. Tests unitarios de las dos direcciones + el caso `legacy`. ✅ 7 en la retirada (antes 5) y 4 en la nominación; `./gradlew testAll --rerun-tasks` verde en 1 m 8 s.

## Consumidores auditados

Grep de todo lo que lee o escribe el sello:

| Sitio | Qué hace | Estado |
|---|---|---|
| `CoordinatorDetectionService:1732` | nomina, carril BT | ✅ sin cambio: ya pasaba `lane=bt` desde `33aabb9ef` |
| `CoordinatorDetectionService:1259` | nomina, carril Coordinator | ✅ sin cambio: ya pasaba `lane=coordinator` |
| `CoordinatorDetectionService:1769` | retira, carril BT (`NeverDrove`) | ✅ sin cambio: su sello es suyo, la puerta lo deja pasar |
| `CoordinatorDetectionService:2430` | retira, fin del job del Coordinator | ✅ **cerrado**: la puerta lo para cuando el sello es del otro carril |
| `EvaluateSpotReleaseNominationUseCase` | `already_nominated` por `spotId` | ✅ sin cambio: la negativa no toca el sello |
| `ProcessConfirmedDepartureUseCase:231` | promueve → `clearPendingSpotRelease()` | ✅ exento, camino distinto |
| `ReleaseActiveParkingSessionUseCase:132` | «Me voy» → `clearPendingSpotRelease()` | ✅ exento, camino distinto |
| `AndroidDataStoreAppPreferences` | persiste el sello | ✅ clave `pending_spot_release_lane` + lectura legacy |
| `IosAppPreferences` | ídem en iOS | ✅ espejo exacto — ⚠️ **no compilado**: no hay Kotlin/Native para iOS en esta máquina |
| `FakeAppPreferences` | `storedSpotRelease` | ✅ guarda el objeto entero, sin cambio |
