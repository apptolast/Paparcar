# DET-COORDINATOR-DEAD-MEMBERS-001 · miembros del coordinator que ya no lee nadie

**Estado:** ✅ Done · **1.708 tests, 0 fallos** · diff = 40 borrados en un solo fichero (1.374 → 1.334) · prod + mock compilan

## Problema

Las fases de F6 fueron sacando código del `CoordinatorParkingDetector` hacia `stages/`, `state/` y
el ejecutor, pero — igual que pasó con los 50 imports de
`DET-COORDINATOR-IMPORTS-ITS-OWN-PACKAGE-001` — algunos miembros y KDoc se quedaron atrás sin
lectores. Medido sobre master `ab610847` (1.374 líneas): cada símbolo de la tabla aparece
**exactamente 1 vez** en el fichero (su declaración) y 0 veces en el resto del repo.

| Miembro | Línea | Adónde se fue el código que lo leía |
|---|---|---|
| `attributedVehicleType` (getter privado) | ~276 | Los lectores viven en `stages/StageInputs.kt` y `state/SessionTelemetry.kt`, que leen el sub-estado directamente |
| `TAG` (companion) | ~1315 | Todo el fichero loguea con `DIAG`; `TAG` no lo consume nadie |
| `USER_CONFIRM_NEAR_CAR_MAX_METERS` + su KDoc | ~1353-1359 | Mudó a `UserConfirmStage.NEAR_CAR_MAX_METERS` (P3.x), que es la copia que se usa |
| `IMPLAUSIBLE_REPARK_PROMPT_SCORE` | ~1343 | `DetectionEffectExecutor` tiene su propia copia y es quien la consume |
| `WEAK_EVIDENCE_PROMPT_SCORE` | ~1346 | Ídem — copia viva en `DetectionEffectExecutor` |

Y tres bloques de comentario que documentan código que **ya no está ahí** (mentiras posicionales,
no porqués — la política de "cada guard lleva su incidente dentro" no los cubre):

| Bloque | Línea | Qué pasó |
|---|---|---|
| KDoc de `savedConfirmPostedAt` (13 líneas) | ~165-177 | El campo vive en `DetectionEffectExecutor` **con este mismo KDoc ya migrado** (§"The one piece of state it owns"); la copia del coordinator quedó pegada encima de `diagnostics`, documentando lo que no es |
| KDoc "Arm-evidence label of the in-flight session…" | ~365-367 | El campo se disolvió en el session state (`session.armEvidence`); el doc quedó flotando sin declaración debajo |
| Comentario del cadence latch (§C, 6 líneas) | ~569-574 | Describe un "plain flag" del step collector que ya no existe: hoy es `diagnostics.latchOnce(PEDAL_CADENCE)` |

## Doctrina violada

Ninguna directamente — es higiene. Pero un KDoc que documenta el campo equivocado es peor que
ninguno: quien lea `diagnostics` creerá que cruza sesiones por diseño de notificaciones.

## Diseño

Borrado puro. Cero conducta, cero asserts editados, cero comentarios VIVOS tocados — la regla de
`DET-COORDINATOR-IMPORTS-ITS-OWN-PACKAGE-001` aplica entera: el diff solo puede contener
declaraciones muertas y docs huérfanos, nada más.

Cómo se decidió qué está muerto, sin criterio propio:
1. Símbolo con 1 sola aparición en el fichero (la declaración) **y** 0 en el resto de `src`
   (prod + tests), verificado con grep de palabra completa.
2. Bloque de doc cuyo sujeto ya no existe en el fichero, con la copia viva localizada en su casa
   nueva (citada en la tabla).
3. Después, compilador y suite completa.

## Criterio de éxito

`testProdDebugUnitTest` con los mismos tests que master, `compileProdDebugKotlinAndroid` +
`compileMockDebugKotlinAndroid` verdes, y el diff sin una sola línea que no sea un borrado de los
listados.

## Consumidores auditados

- `attributedVehicleType`: grep en todo `src` → las apariciones restantes son
  `session.attributedVehicleType` en `StageInputs.kt` / `SessionTelemetry.kt` (leen el sub-estado,
  no el getter del coordinator). Cerrado.
- Los 3 consts: grep → las copias vivas están en `DetectionEffectExecutor` y `UserConfirmStage`,
  con sus propios tests. Cerrado.
- KDoc `savedConfirmPostedAt`: la versión migrada existe en `DetectionEffectExecutor` (verificado,
  misma explicación [REFACTOR-300-FIX] ampliada). No se pierde ningún porqué. Cerrado.
