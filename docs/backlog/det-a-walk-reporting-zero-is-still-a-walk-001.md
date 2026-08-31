# DET-A-WALK-REPORTING-ZERO-IS-STILL-A-WALK-001 · la caminata que el GPS declara parada no la mide nadie

**Estado:** 🟡 Abierto · sin rama · sin worktree
**Origen:** hallazgo medido de `DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001` (31-08), que fue a
cerrar el «bug #6» del egress birth y encontró que el bug era otro.

## Problema

El carril cinemático es el par del proof de pasos: existe para el hardware cuyo contador se queda
mudo. Pero **sólo cuenta fixes que el propio GPS declara en movimiento**.

`updateStopTracking` parte por `location.speed < stoppedSpeedThresholdMps` (**1 m/s**). El contador
`kinematicEgressFixes` se incrementa **únicamente en la rama MOVING**, y ahí exige además
`frozenByRest`, `speed < minimumTripSpeedMps` y accuracy creíble.

Una persona anda a ~1,4 m/s, así que en la calle con cielo abierto funciona. Pero el campo `speed`
de un fix es Doppler: **en interior, en un garaje, con receptor frío o con proveedor de red, llega
0.0 mientras la persona camina**. Esos fixes caen del lado PARADO, donde:

- no incrementan `kinematicEgressFixes` → `hasKinematicEgressSignal` nunca se cumple → **el camino de
  confirmación cinemático no existe para esa sesión**;
- no abren `egressBirth` → `judgeEgressBirth` responde `NOT_RECORDED` → desde
  `DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001`, el único camino que le queda (AR vehicle-exit +
  ventana) **degrada a pregunta**.

O sea: el usuario con podómetro mudo **y** GPS que no reporta velocidad se queda sin los dos caminos
que no exigen pasos, y su aparcamiento acaba en pregunta. Con **P3**, en nada.

## Lo que ya está medido, y lo que NO

✅ Medido (`AnchorTrustTest`, `DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001`):
- la rama MOVING **sí** abre birth con testigo cinemático (la bandera ya es `true` ahí);
- aceptar el testigo cinemático en la rama STOPPED **no es el arreglo**: fabrica una birth *en el
  ancla* a partir de un contador viejo, que `judgeEgressBirth` leería como `BORN_AT_ANCHOR`.

❌ **Sin medir, y es lo que bloquea este ticket**: cuántos fixes de una caminata real llegan con
`speed < 1 m/s` en los móviles de campo. Es contable desde el `parkdiag` (los fixes ya se trazan con
su `speed`): **contar, en las sesiones que acabaron en `NOT_RECORDED`, cuántos fixes post-ancla
tienen `speed < 1` y desplazamiento > 0 entre ellos.** Sin ese número, cualquier umbral es una
intuición con aspecto de dato.

## Dirección de diseño (a decidir con el número delante)

La doctrina ya está escrita para el caso simétrico: `DET-STOP-MUST-BE-STILL-IN-SPACE-001` dice
*«una parada es una afirmación sobre POSICIÓN, y el campo `speed` declarado no es posición»* — y por
eso una parada se refuta por desplazamiento medido entre fixes, no por lo que diga el Doppler.

**El espejo de esa regla es este ticket**: si `speed` no puede probar reposo, tampoco puede
**desmentir** movimiento. Una caminata debería poder medirse por **desplazamiento** entre fixes
consecutivos con accuracy creíble, no por el campo declarado.

⚠️ Y el riesgo va en la dirección contraria, que es lo que hace esto delicado: el carril cinemático
es un camino de CONFIRMACIÓN. Contar como «caminata» el ruido de un teléfono quieto sobre una mesa
—multipath en interior mueve un fix decenas de metros— plantaría pines. Cualquier medida por
desplazamiento necesita su envolvente de accuracy y un suelo, exactamente como
`outrunsPedestrianReach` y `sustainedDepartureFromAnchor` ya hacen en su lado.

## Criterio de éxito

- Una sesión con podómetro mudo cuyo GPS declara 0 m/s durante una caminata real **abre birth** y
  puede confirmar por el carril cinemático.
- Un teléfono quieto en interior con multipath **no** abre birth ni acumula fixes de egress.
- El número que hoy falta, contado y escrito en el doc antes de elegir umbral.
