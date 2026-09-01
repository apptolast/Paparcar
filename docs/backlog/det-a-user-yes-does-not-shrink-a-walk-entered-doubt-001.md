# DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001

> **Estado:** implementado 2026-09-01 · rama `bugfix/DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001-anchordoubt` (base `0912ddf5`)
> **Origen:** la auditoría de `TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001`. Salió al leer,
> por primera vez, el ground-truth de `TraceCameliasOppo001` — que llevaba desde julio sin que ningún
> test lo tocara.

---

## 1. El defecto, medido sobre el mismo stream

Trace `TraceCameliasOppo001` (campo 2026-07-15, Oppo). Ancla **walk-entered** (contaminada: la
congeló el peatón, no el coche), en `36.5976591,-6.2506682` — **37 m del coche real**. Dos puertas de
salida de la MISMA sesión, con la MISMA duda:

| quién decide | forma | radio | outcome |
|---|---|---|---|
| nadie contesta → timeout desatendido | **ZONA** | 60 m | `confirmed_unattended_zone_walk_entered_anchor` |
| el usuario toca **"Sí"** | **PIN EXACTO** | — | `confirmed_user` |

El pin exacto caía **a menos de un metro del FP de campo**. La zona de 60 m **cubre** los 37 m de
error real; el pin exacto, no.

## 2. La causa, y la trampa que casi me lleva al arreglo equivocado

`UserConfirmStage.shapeFor` acotaba la duda **de UNA sola fuente**, el agujero de GPS
(`capture.gapMs`) — la duda para la que se escribió `DET-USER-YES-IS-NOT-A-COORDINATE-001`. Un ancla
walk-entered no tiene agujero → `doubtMeters = 0` → `ExactPin`.

⛔ **Pero pasarle la duda del walk-in a la puerta que ya había NO habría arreglado nada.** Medido:
el bound del walk-in en ese trace es **29,5 m**, y la puerta era
`max(accuracy, doubt) > honestCloseMinZoneRadiusMeters (60)`. 29,5 < 60 → seguiría siendo pin exacto.
*(Medido bajando el suelo a 1 m en un replay y leyendo el radio resultante.)*

Lo que hace insostenible el pin no es el TAMAÑO del número, es **qué es**: un **bound INFERIOR** —
la caminata sólo se vio en parte — sobre lo equivocado que está el **SITIO**. El error real (37 m) es
**mayor** que el bound que supuestamente nos tranquilizaba.

## 3. La regla, escrita donde faltaba

La frase del KDoc de `UserConfirmStage` —*«por debajo del suelo un área dice menos que el punto, así
que el punto se queda»*— **es sobre PRECISIÓN, y se le estaba preguntando por el SITIO**:

- **Duda ya GASTADA relocalizando** (ancla de hueco: la cascada se va a otro fix) → lo que queda es
  una distancia desde el punto elegido. Es precisión, manda el suelo, **no se toca**.
- **Duda que el guardado SE LLEVA** (ancla walk-entered: la cascada **no** relocaliza — a propósito,
  una puerta a 40 m es peor apuesta que la parada que la sesión midió) → el punto guardado es el que
  la propia sesión marcó como del peatón. Ahí **la mancha licencia el área, sea cual sea su
  magnitud**, y la magnitud sólo la dimensiona (suelo incluido).

Es el mismo trato que el timeout desatendido lleva haciendo siempre sobre esa misma ancla
(`WALK_ENTERED_ANCHOR`, licenciado por `doubt > 0`). **Dos puertas, una respuesta.**

## 4. Cambios

| fichero | qué |
|---|---|
| `physics/WalkInDoubt.kt` **(nuevo)** | `walkedInToAnchorMeters(...)` — el bound, con UN nombre. Hermano de `walkableInsideGapMeters`. Su KDoc dice que es un bound INFERIOR y prohíbe leer «número pequeño» como «ancla buena» |
| `EvaluateUnattendedParkingSaveUseCase` | la expresión inline (`max(steppedBound, walkInSpan)`) pasa a llamar a la función. Mismo resultado, un solo sitio |
| `UserConfirmStage` | `whereTheCarIs` devuelve `Where(point, keptATaintedAnchor)` — el hecho de que la cascada **conservó** un ancla contaminada vivía sólo en su flujo de control y no llegaba a nadie. `shapeFor` lo lee |

⛔ **Sin caso de uso nuevo** [DET-VERDICT-NOT-PREDICATE-001]: esto es un **predicado** compartido por
dos veredictos → función pura de nivel superior en `domain/detection/physics/`, como
`walkableInsideGapMeters`, `HumanPoweredRide` o `SentryWakeCooldown`.

## 5. Barrido de consumidores

`grep -rn "anchorStrideMeters\|walkInSpanMeters\|isAnchorWalkEntered" shared/src app/src` (producción):

| sitio | clasificación |
|---|---|
| `EvaluateUnattendedParkingSaveUseCase:337` (rama walk-entered) | **cerrado** — ahora llama a la función |
| `UserConfirmStage:188` | **cerrado** — este ticket |
| `EvaluateUnattendedParkingSaveUseCase:198` `stepCount * stride` | **exento con razón**: es la caminata de EGRESS (pasos desde que se dejó el coche), otra caminata |
| `AnchorPredicates:259,307` `stepCountAtBirth * stride + envelopes` | **exento con razón**: alcance del NACIMIENTO del egress, con envolventes de accuracy; otra pregunta |
| `AnchorPredicates:122,142,166`, `HoldResolutionStage:178` | **exento**: alimentan `PedestrianReach`, otra función |
| `StageInputs:74,122,124,142,144` | **cubierto por convergencia**: presentan el estado a los evaluadores, no deciden |
| `honestZoneRadius` en `EvaluateHonestCloseUseCase:437` | **exento con razón**: el honest close **no tiene ancla** (la sesión abortó); acota por pasos desde el pin viejo. Misma fórmula, otro testigo |
| `honestZoneRadius` en `DetectionEffectDispatcher:83` | **exento**: es el clamp compartido, no la duda |
| `inferredPinDoubtRadius` | **exento**: responde otra pregunta (¿puede un pin INFERIDO decirse exacto?) |

## 6. Tests y falsación

**2.092 tests, 0 fallos.** Al aplicar el arreglo cayó **exactamente un** test en toda la suite: la
CARACTERIZACIÓN que este ticket había dejado escrita el día anterior, que se ha **volteado** a exigir
zona (no borrado). Nada más se movió — el cambio es quirúrgico.

- `WalkInDoubtTest` (6 tests) — incluido el testigo de que es un bound inferior.
- El par de replays sobre `TraceCameliasOppo001` afirma ahora **el mismo radio** por las dos puertas:
  si alguien enseña algo a una y no a la otra, se pone rojo.

Falsaciones, ambas en rojo:
1. quitar `where.keptATaintedAnchor ||` de la puerta → vuelve el pin exacto;
2. invertir la comprobación de identidad (`point !== anchor`) → vuelve el pin exacto. La fontanería
   de la mancha es lo que lo sostiene, no la casualidad.

## 7. Lo que este ticket NO toca

- **La rama del hueco sigue con su puerta de suelo.** Su duda ya se gastó relocalizando y la decisión
  está razonada en `DET-USER-YES-IS-NOT-A-COORDINATE-001`. ⚠️ Queda una pregunta legítima: si un
  bound de walk-in puede quedarse corto (29,5 m contra 37 m reales), ¿puede quedarse corto también el
  del hueco? **No se responde aquí y no hay medición que lo sostenga todavía.**
- **`detectionPath` no cambia**: sigue siendo `user`. No hay camino de confirmación nuevo, así que no
  hay valor nuevo que espejar a Firestore.
- **Sin Dev Catalog ni strings**: no hay pantalla, estado MVI ni copy nuevos. `isApproximate` ya
  existía y ya lo producían otras vías; que Home e Historial pinten un área como un punto es un
  defecto **anterior**, con su propio ticket (`ui-approximate-zone-in-history-001`).
- ⚠️ **Coste aceptado**: un "Sí" sobre un ancla walk-entered pasa de pin exacto a área de 60 m. Es la
  población que `DET-CREDIBLE-DRIVE-001` ya degrada a pregunta — anclas que la sesión declaró del
  peatón — y en el único caso de campo medido el área acierta donde el punto fallaba por 37 m.
