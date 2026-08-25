# DET-STATE-SESSION-TELEMETRY-001 · P2.1 — la sesión deja de ser un montón de variables sueltas

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-SESSION-TELEMETRY-001-p2-1` ·
worktree `../Paparcar-state-1`

Paso **P2.1**, el primero de la **Fase 2**. Base: `0e2e8661` (los dos bugfixes de campo del 24-08
que la otra sesión mergeó mientras este paso empezaba — el worktree se rebasó sobre ellos antes de
tocar el coordinator).

## Qué mueve

Diez valores que describen **quién es la sesión y qué le está permitido** vivían en tres sitios
distintos:

| Dónde vivían | Cuáles |
|---|---|
| campos planos de `ParkingDetectionState` | `hasEverReachedDrivingSpeed`, `drivingSpeedOnArmTrustOnly`, `hasEverMoved`, `sessionOrigin`, `sessionStartMs`, `lastSpeedMps`, `previousFix` |
| `var` locales de `invoke()` (700 líneas) | `activeVehicleId`, `activeVehicleType`, `locationCount` |
| `@Volatile` del coordinator | `currentArmEvidence` |

`domain/detection/state/SessionTelemetry.kt` los reúne en un sub-estado **sólo de `val`s**, cuyos
cambios pasan todos por una transición con nombre: `armed`, `seededOnArmTrust`, `countFix`, `onFix`,
`observed`, `departureConfirmed`, `departureDismissed`, `enterArmStepVeto`, `attributeVehicle`,
`keepingIdentity`, `keepingAuthorization`.

## Lo que este paso arregla de verdad

### 1. La autorización y su etiqueta ya no pueden desincronizarse

`hasEverReachedDrivingSpeed` — la **autorización de ciclo de vida**, no un grado de prueba — se
mutaba desde cinco sitios, y **tres** de ellos la escribían por separado de la etiqueta de evidencia
con la que viaja:

| Sitio | Antes |
|---|---|
| departure confirmada tarde | evidencia, luego seed — dos escrituras |
| departure descartada | seed, luego evidencia — dos escrituras |
| veto del enter-arm por pasos | evidencia, luego un-seed — dos escrituras |

Entre las dos escrituras la sesión era legible como *autorizada con evidencia `self_observed`*, o
*sin autorizar con `verified_enter`* — estados que no deberían existir. Nadie observó uno en la
práctica porque las escrituras van a microsegundos en una sola corrutina, pero **«nadie lo observó»
es suerte, no un invariante**.

### 2. La lista de preservación escrita a mano

`onUserDeniedParking` borraba el estado entero y **volvía a copiar dos campos por su nombre**. Un
campo añadido a ese conjunto tenía que *acordarse* de aparecer ahí, y nada fallaba si no lo hacía —
la misma forma de bug que las cinco condiciones de rebind copiadas que P2.5 existe para borrar.

Es exactamente lo que la neutralización demuestra: quitar `hasEverMoved` de la lista (lo que
re-armaría en silencio el guard de no-movimiento) **pasó desapercibido para los 1.552 tests
anteriores**.

## Lo que queda escrito, no arreglado

`authorizedOnArmTrustOnly` **no** sobrevive a un «sigo conduciendo», así que a partir de ahí un seed
prestado ya no puede ser retractado por un dismissal. Es la conducta de hoy, es una rareza y no un
diseño, y cambiarla dentro de un movimiento sería cambio de conducta. Queda en el KDoc de
`keepingAuthorization` y en su test, con el `assertFalse` explicado.

## Decisiones de frontera

- **`sessionOutcome` se queda fuera**, a propósito. Debe SOBREVIVIR al `reset()` del `finally` — el
  servicio lo lee después de que `invoke` retorne — y meterlo en el state exige el patrón de
  snapshot (`lastFinished*`). Es un argumento distinto y merece su propio commit reversible.
- **`attributedVehicleId` se lee EN VIVO**, no del snapshot `state` de la iteración: la atribución
  ocurre a mitad de iteración y los lectores de más abajo tienen que verla, exactamente como cuando
  era un `var` local. La trampa está en el gate de atribución, donde `state.session.driveAuthorized`
  es el snapshot (como antes) y `attributedVehicleId` es la lectura viva (como antes).
- **`countFix()` es su propia transición** y no parte de `onFix`: el `loc#N` se registra ANTES de
  juzgar el fix, y ese número no puede moverse en la traza.

## Verificado discriminante, no supuesto

| Neutralización | Resultado |
|---|---|
| que la evidencia deje de viajar con la retractación | 🔴 1 test |
| olvidar `hasEverMoved` en la lista de preservación | 🔴 1 test |

En los **dos** casos el único test que se entera es el nuevo. Eso es la medida de lo que faltaba.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

`SessionTelemetryTest` (11). **1.563 tests**, 0 fallos.

**Criterio de la Fase 2 cumplido en su forma más fuerte: no se ha tocado NI UN fichero de test.**
El coordinator baja 33 líneas netas (99 añadidas, 132 borradas) y pierde 3 `var`.
`assembleMockDebug` ✅.

## Red de P0.4

```
tests 1563 - desaparecidos: 5 (los renombrados de P1.8, ya justificados) - nuevos: 114
```

Siguiente: **P2.2**, `state/ConfirmationLifecycle.kt` (`ConfirmationPhase` + `pendingConfirm` +
`completed` + `toDetectionPhase()`).
