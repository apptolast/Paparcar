# DET-STATE-CONFIRMATION-LIFECYCLE-001 · P2.2 — tres formas de terminar un hold que parecían una

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-CONFIRMATION-LIFECYCLE-001-p2-2` ·
worktree `../Paparcar-state-2`

Paso **P2.2** de la Fase 2. **Apilado sobre `ff948b46` (P2.1), que aún no está en master** — ver
«Estado del merge» abajo.

## Qué mueve

`phase`, `pendingConfirm` y `userConfirmedParking` describen **una sola cosa** —la conversación con
el usuario— y vivían como vecinos planos de otros treinta campos sin relación.
`domain/detection/state/ConfirmationLifecycle.kt` los reúne con transiciones con nombre, y
`PendingConfirm` sale del coordinator para sentarse junto al ciclo de vida que lo posee.

`REFACTOR-200` ya había hecho irrepresentables las combinaciones inválidas de los cuatro flags
heredados metiéndolos en `ConfirmationPhase`. Esto es lo mismo un nivel más arriba.

## Lo que arregla de verdad

`pendingConfirm = null` estaba escrito en **tres sitios que significan tres cosas distintas**:

| Sitio | Significado | Ahora |
|---|---|---|
| el pin retenido quedó RANCIO (la posición superó a los pasos) | descartar, seguir detectando, no decir nada | `discardingHold()` |
| el usuario arrancó a mitad del hold | descartar, seguir detectando, no decir nada | `discardingHold()` |
| el save fue rechazado por re-aparcamiento inverosímil | descartar **y poner una pregunta en pantalla** | `degradedToPrompt(shownAt)` |

La segunda mitad del tercero (`phase = Notified(now)`) estaba dentro del mismo `copy`, con pinta de
línea sin relación que casualmente iba ahí.

### La trampa de la identidad, por fin con guardia

El watchdog del hold compara `PendingConfirm` con `===` detrás de un `distinctUntilChanged`. Un
campo que el bucle de fixes actualice —un contador, una ubicación refrescada— hace que **cada fix
produzca una instancia nueva**, lo que cancela y relanza el watchdog en cada fix y hace que el hold
no se resuelva jamás. Era una mina conocida sin nada que la hiciera cumplir; ahora el KDoc lo dice
con un ⛔ y un test lo sostiene.

## Verificado discriminante, no supuesto

| Neutralización | Resultado |
|---|---|
| que un descarte normal borre además el prompt | 🔴 1 test |
| que una transición de fase reconstruya el `PendingConfirm` | 🔴 1 test |

En los dos casos, **el único test que se entera es el nuevo**. El primero importa especialmente: sin
estado de prompt el abort por response-timeout no dispara nunca, que es BUG-STUCK-SESSION reabierto
— y los 1.563 tests anteriores no lo notan.

## Decisiones de frontera

- **`completed` se queda fuera**, y con la misma razón que `sessionOutcome` en P1.11: la última línea
  de `invoke()` lo lee **después** de `reset()`. Son la misma familia — valores que deben SOBREVIVIR
  al borrado del estado, hoy resuelta con `@Volatile` y locales. Deben mudarse **juntos**, en el paso
  que haga explícito el snapshot de fin de sesión (hoy `lastFinishedFix` / `lastFinishedStepEvents` /
  `lastFinishedMaxSpeedMps` / `lastFinishedSessionId`), no de tres en tres con tres excusas.
- **`ConfirmationPhase` no se muda de paquete.** Mudarlo obligaría a tocar
  `ConfirmationPhaseMappingTest` (imports), y el criterio de la fase se cumple sin eso. El
  `promptShownAt` del ciclo delega en el suyo, sin segunda fuente.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

`ConfirmationLifecycleTest` (7). **1.570 tests**, 0 fallos.
**Ni un fichero de test tocado.** Coordinator −19 líneas netas; desaparece además la lambda
`newCandidate`, que existía sólo para rellenar tres argumentos que ahora tiene la transición.
`assembleMockDebug` ✅.

## Red de P0.4

```
tests 1570 - desaparecidos: 5 (los renombrados de P1.8, ya justificados) - nuevos: 121
```

## Estado del merge (2026-08-25)

**P2.1 y P2.2 están sin mergear**, y no por falta de permiso: el worktree principal
(`C:/Users/rndev/Documents/AndroidProjects/Paparcar`, donde `master` está checked out) tiene
**cambios sin commitear de la otra sesión** —un helper `sustainedDriveWitnessed` en
`physics/SpeedBandClock.kt` con sus dos call sites en el coordinator— y git se niega a mover el ref
de `master` mientras su árbol esté sucio. No se toca trabajo ajeno.

⚠️ Ese trabajo edita `CoordinatorParkingDetector.kt`, el mismo fichero que P2.1/P2.2 reescriben. Sus
dos hunks (`sessionSawDriving = …` en `runConfirm` y en el guard de repark) **no** tocan campos que
P2.1 borró, así que re-aplicarlos encima compila — pero cuanto antes se commitee, menos riesgo.

Siguiente: **P2.3**, `state/EgressEvidence.kt` (máquina de pasos, latch de sensor vivo, estampas AR).
