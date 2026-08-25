# DET-STATE-EGRESS-BIRTH-001 · P2.5-bis — una sola regla de nacimiento, y la asimetría con nombre

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-EGRESS-BIRTH-001-p2-5bis` ·
worktree `../Paparcar-state-5b`

Paso **P2.5-bis**. **El primer paso de todo el refactor marcado `C`** — *puede cambiar conducta* —
así que va en commit propio y con criterio de aceptación literal. Sigue a `7a46ef6a` (P2.5).

## Qué unifica

El nacimiento del egress se decidía en **dos bloques casi idénticos separados por 180 líneas**: uno
para un fix PARADO y otro para uno EN MOVIMIENTO. Las copias divergen: una cota añadida a uno es una
cota que el otro sigue sin tener, y el lector de cualquiera de los dos no puede saber si una
diferencia es una decisión o una divergencia.

Pasan a una sola `AnchorTrust.withEgressBirth`, y la única diferencia entre ellos pasa a ser un
**parámetro con nombre**.

## La asimetría es el bug #6, y se PRESERVA

Un nacimiento necesita un TESTIGO de que la caminata de egress empezó:

| Sabor | Testigo válido |
|---|---|
| fix en MOVIMIENTO | un paso contado **o** un fix cinemático (medido por GPS) |
| fix PARADO | **solo** un paso contado |

Eso es `acceptsKinematicWitness`. Las dos lecturas son defendibles: en un fix parado un testigo
cinemático es o bien el usuario de contador mudo consiguiendo por fin un nacimiento, o bien ruido GPS
inventándolo en un semáforo — y la diferencia decide dónde cae un pin. Resolverlo necesita un replay
dirigido o dato de campo, **no un refactor**.

## Criterio de aceptación, cumplido al pie de la letra

El plan lo enuncia literal: `Trace_Enamorados001` y `Trace_CameliasOppo001` **sin cambio de
desenlace**, y cualquier delta observable detiene el paso y vuelve para aprobación.

```
replays: 18 → 18 · ninguno desaparece · ninguno aparece · 0 fallos
  OK camelias_oppo_001_walk_entered_anchor_prompts_instead_of_pinning_the_house
  OK enamorados_001_sustained_departure_unfreezes_the_traffic_light_and_confirms_at_the_real_arrival
  OK enamorados_001_unattended_timeout_with_disowned_anchor_saves_zone_at_the_egress_birth
  OK enamorados_001_without_recovery_fixes_the_ceiling_prompts_and_a_user_yes_anchors_at_the_car
```

Uno de esos tres de Enamorados va **literalmente** sobre el nacimiento del egress, que es la mejor
red posible para este paso. **Cero delta observable → el paso no se detiene.**

## El experimento, que vale más que el refactor

Poner `acceptsKinematicWitness = true` también en el sabor PARADO —es decir, **arreglar** el bug #6—
**no rompe nada**: ni un test, ni uno de los 18 replays.

El caso **es alcanzable en principio** (un fix en movimiento puede dejar `kinematicEgressFixes` a
distinto de cero antes de que se abra una parada), pero **ningún trace grabado lo alcanza**.

De ahí salen dos cosas, y conviene no confundirlas:

1. La asimetría **no se puede adjudicar con el material de hoy** — exactamente la misma conclusión a
   la que llegó la cohorte de atasco en P1.11. No es evidencia a favor de ninguna de las dos
   lecturas: es ausencia de casos.
2. Hoy **no está protegiendo nada medible**. Lo cual NO significa que sea inocua: significa que si
   se cambia, ningún test lo dirá — que es precisamente por lo que ahora tiene dos tests que fijan
   las dos mitades.

**→ Ticket futuro (bug #6)**: decidir el testigo cinemático en parado. Precondición: un trace que
alcance el caso — parada abierta con `kinematicEgressFixes > 0` acumulado y contador mudo. Sin eso,
la pregunta sigue siendo indecidible.

## Doctrina

Ninguna tocada. **Cero cambio de conducta observable.**

## Tests

`AnchorTrustTest` pasa de 11 a 15. Los tres del nacimiento se re-cablean a la firma nueva **con sus
asserts intactos**, y entran cuatro:

- que sin ancla no hay nacimiento;
- que una caminata que ya avanzó no puede arrastrar el nacimiento (`BUG-REPARK-WALK`);
- **el par del bug #6**: el testigo cinemático abre nacimiento en movimiento y **no** en parado.

**1.618 tests**, 0 fallos. `assembleMockDebug` ✅.

Siguiente: **P2.6**, `state/DetectionSessionState.kt` — la composición de los cinco sub-estados y,
sobre todo, **el orden de reducción DECLARADO**. Es lo que P2.3 dejó pendiente al hacer simétrica la
frontera ancla↔pasos: sin ese orden escrito, un despiste cambia el veredicto de cadencia y nada lo
grita.
