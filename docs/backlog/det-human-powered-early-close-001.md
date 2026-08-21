# DET-HUMAN-POWERED-EARLY-CLOSE-001 · El veredicto se emite cuando la evidencia está, no cuando expira un reloj

**Estado:** 🟢 Código hecho, sin commitear · rama
`bugfix/DET-HUMAN-POWERED-EARLY-CLOSE-001-verdict-not-clock` · worktree `../Paparcar-early-close`

## Problema

Field 2026-08-19, viaje en bicicleta, los dos móviles (Oppo `1787171533976`, Redmi
`1787171592952`). Llegada a casa 22:42:39, parada firme desde 22:44:59. La sesión **no murió hasta
las 23:01:30**: 19 minutos de servicio en primer plano y GPS a 2-5 s después de acabar el viaje.

```
22:46:30  prompt "¿has aparcado?"  (carril Low/Medium, lowNotifTimeoutMs)
22:50:00  CANDIDATE OPENED   → steps=0/8 → Inconclusive, en bucle
22:55:02  CANDIDATE DISCARDED (ventana de 5 min) → stepCount = 0
22:55:04  CANDIDATE OPENED   → lo mismo
23:00:10  CANDIDATE DISCARDED
23:00:15  CANDIDATE OPENED
23:01:30  ⑊ no user response after 900060ms → Ask(reason=HUMAN_POWERED) → nudge → fin
```

El veredicto final —`HUMAN_POWERED`— no dependía de nada que pasara en esos 19 minutos. La
evidencia (cadencia de pedaleo medida en el propio stream, `DET-MOTOR-PROOF-001`) estaba desde el
viaje. Lo único que faltaba era que venciera `confirmationResponseTimeoutMs`.

Peor: el bucle era **incapaz** de concluir otra cosa. Cada descarte de candidato pone `stepCount = 0`
(`evaluateCandidatePhase` → `Rejected`), así que el candidato siguiente nacía sin los pasos de
egreso que ya habían ocurrido a las 22:42-22:45. Tres vueltas de un bucle que no podía confirmar
nada ni aunque el usuario hubiera aparcado.

## Doctrina violada

- **Un veredicto se emite cuando su evidencia está completa.** `EvaluateUnattendedParkingSaveUseCase`
  responde `Ask(HUMAN_POWERED)` en su PRIMERA línea, sin mirar ancla, ni reloj, ni nada más — pero
  esa línea sólo se ejecuta detrás de `promptShownAt + 15 min`.
- **No preguntar lo que no toca** (`feedback_no_internals_in_user_copy`): a una bici se le preguntó
  "¿has aparcado?".
- **Coste al usuario:** 19 min de FGS + GPS continuo por un veredicto ya decidido.

## Diseño

**El instante en que la evidencia está completa es la parada madura.** "Marcha humana" sola no basta
(un ciclista parado en un semáforo sigue de viaje); lo que convierte "iba en bici" en "el viaje
terminó" es una parada sostenida. Y esa parada ya la certifica el scorer: la confianza **High** sólo
se alcanza por el escalón de 5 minutos parado (`slowPath5MinMs`). No hace falta un reloj nuevo — hace
falta leer el que ya existe.

1. **`ParkingDecision.CloseHumanPowered`** (terminal) en el evaluador que ya poseía la pregunta.
   Se emite cuando `humanPoweredRide && restCertified`, colocado DESPUÉS de las ramas de confirm y
   ANTES de `windowElapsed`: la ventana de observación es un instrumento para decidir candidatos
   indecisos, y éste está decidido.
2. **`restCertified`** es la entrada nueva: "el scorer certificó una parada sostenida". El carril
   rápido de steps+egress la pasa `false` — corre sin parada detrás, y sin eso cerraría a un
   ciclista en un semáforo con cuatro pasos fantasma.
3. **El coordinador consulta el veredicto al alcanzar High**, antes de abrir candidato y antes de
   notificar: por eso una sesión de bici ya **no emite el prompt de aparcamiento**. Si la evidencia
   llega tarde (un AR `ON_BICYCLE` se entrega hasta ~2 min tarde, con el candidato ya abierto), el
   carril del candidato también resuelve el mismo veredicto.
4. **El prompt Low/Medium se suprime, no se aplaza**, para una marcha humana: la fase se queda en
   `LowReached` (nada de un `shownAt` que mienta sobre un prompt que nadie vio), así que si el veto
   se levanta —un AR `IN_VEHICLE` que supersede la bici— el prompt sale con normalidad en el
   siguiente fix, con su timeout medido desde `firstReachedAt`.
5. **Un `parkingDecisionInput(...)` único** en el coordinador: los tres carriles construían a mano
   el mismo input de 16 campos (copia-pega triple donde una señal nueva se olvidaba en dos sitios).

Lo que **no** cambia: una marcha humana **con** todas las pruebas (pasos + egreso) sigue
degradando a `Prompt` — un toque y la plaza se guarda, que es mejor que cerrar y obligar a colocar
el pin a mano. El cierre nunca le roba un Prompt.

### El nudge que sobra

El cierre reutiliza `UnattendedSaveReason.HUMAN_POWERED` (mismo `nudgeSource`, mismo
`aborted_unattended_human_powered`), así que la comparación de campo con todas las sesiones de bici
anteriores sigue alineada. Pero ese nudge —"¿dónde has dejado el coche?"— es **síntoma de otro bug**:
la salida ya se cometió (plaza publicada, sesión liberada, geocerca borrada) antes de que nada
probara un viaje, así que el coche del usuario está sin pin y preguntar es la única vuelta atrás.
Cuando entre **DET-HANDOFF-NOT-MANUAL-001 §B**, una marcha humana debe cerrar **en silencio**: el pin
viejo nunca estuvo mal.

## Criterio de éxito

- Una sesión de marcha humana termina en la primera parada madura, no en el timeout de respuesta.
- Esa sesión no emite nunca el prompt "¿has aparcado?".
- Un ciclista parado brevemente (sin parada madura) NO se cierra.
- Un coche parado en una cola conserva su ventana de observación intacta.
- Campo: bici corta → una sola notificación honesta a los ~5 min de dejarla; coche → confirma igual.

## Consumidores auditados

| Sitio | Asume | Clasificación |
|---|---|---|
| `evaluateCandidatePhase` (`when` sobre `ParkingDecision`) | 4 valores | **cerrado** — rama terminal nueva; el `when` exhaustivo obligó |
| carril rápido steps+egress (`is Confirmed` / `is Prompt`) | resto cae al log | **cerrado** — pasa `restCertified = false`, nunca ve el valor nuevo |
| `advanceHigh` / `advanceLowMedium` / `evaluateConfidence` | devolvían `Unit` | **cerrado** — devuelven "la sesión termina aquí" |
| rama del `confirmationResponseTimeoutMs` (unattended) | única puerta del veredicto HUMAN_POWERED | **cubierto por convergencia** — sigue existiendo para todo lo demás (anclas dudosas, sin conducción…); la marcha humana ya no llega ahí |
| `EvaluateUnattendedParkingSaveUseCase` | `humanPoweredRide` primero | **exento** — misma respuesta, ahora rara vez alcanzada |
| `ConfirmationPhase` (invariante "Candidate ⇒ prompt visible") | `shownAt` no nulo | **exento** — el cierre ocurre ANTES de entrar a Candidate; no se abre candidato sin prompt |

## Resultado

1252 tests verdes (1247 en master + 5 nuevos: 4 del evaluador puro y 1 de coordinador que reproduce
la sesión del 19-08 y exige que muera en la parada madura sin haber preguntado nada).
`compileProdDebugKotlinAndroid` y `compileMockDebugKotlinAndroid` OK.
