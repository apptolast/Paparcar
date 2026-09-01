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

🟢 **MEDIDO el 2026-09-01 — el ticket queda DESBLOQUEADO, y el número es mucho mayor de lo que
sugería el enunciado.**

Método (sin decidir yo qué es una caminata): un fix está «en caminata» si el `parkdiag` registró
eventos de paso **dentro de los 15 s anteriores Y posteriores**. El podómetro es el testigo
independiente; el `speed` del GPS es lo que está en juicio. Barrido sobre **8 sesiones de parkdiag**
distintas (Oppo, Redmi y un tercer device), **6.403 fixes en caminata**:

| sesión | fixes en caminata | `< 1,0 m/s` | `< 0,5 m/s` | mediana declarada |
|---|---|---|---|---|
| oppo | 999 | 71,6 % | 49,8 % | 0,51 |
| oppo | 644 | 70,3 % | 48,6 % | 0,55 |
| oppo | 330 | 77,0 % | 53,0 % | 0,45 |
| redmi | 1 370 | 56,6 % | 42,5 % | 0,79 |
| redmi | 1 331 | 70,2 % | 56,3 % | 0,34 |
| redmi | 1 002 | 56,2 % | 42,2 % | 0,82 |
| redmi | 307 | 57,7 % | 40,4 % | 0,83 |
| 23117RA68G | 420 | 76,0 % | 64,8 % | 0,21 |
| **TOTAL** | **6 403** | **65,5 %** | **~48 %** | **0,21–0,83** |

**Dos de cada tres fixes tomados mientras el usuario anda demostrablemente declaran por debajo del
umbral de parado.** No es un caso raro de interior o garaje: es el caso NORMAL, en todos los devices
y todos los días del corpus.

⛔⛔ **Y el mismo número REFUTA el arreglo obvio.** Bajar `stoppedSpeedThresholdMps` no sirve: con el
**48 %** de los fixes de caminata por debajo de 0,5 m/s, no queda margen entre «anda» y «está quieto»
en el eje de la velocidad declarada — el campo no lleva señal a esas magnitudes. El separador tiene
que ser **posición contra tiempo**, que es exactamente el movimiento que ya hicieron
`DET-STOP-MUST-BE-STILL-IN-SPACE-001` y `DET-A-HOLE-THE-SPEED-FIELD-DENIES-IS-STILL-A-HOLE-001`.

❌ **Lo que sigue sin medir, y es lo que ahora bloquea**: el ruido de un teléfono QUIETO. Hace falta
la distribución del desplazamiento medido entre fixes consecutivos en reposo (interior, multipath)
para saber si separa de la caminata y con qué envolvente. Sin ese segundo número, cualquier umbral
por desplazamiento tiene el mismo problema que tenía el de velocidad.

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
