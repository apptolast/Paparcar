# DET-STARVED-HOLD-HAS-NO-WITNESS-001 · la rama que planta un pin sin fix detrás no tiene un solo test

> ⚠️ **Cruzado con el rediseño (30-08): CERRADO COMO DUPLICADO.** Lo absorbe entero la Pieza 4
> (`DET-NO-CLOCK-PLANTS-A-PIN-001`). Sus **tres caveats** — `confirmHoldMs > 0` es costura de tres
> ficheros de test, `runTest` da tiempo virtual, y `pendingConfirm === pending` se compara por
> IDENTIDAD — pasan a criterio de aceptación de esa pieza. Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.1.

**Estado:** ✅ Done — absorbido por `DET-NO-CLOCK-PLANTS-A-PIN-001`, con sus tres caveats dentro del test · sale de un `//FIXME` del user, contestado el 27-08

## La pregunta que lo abrió

> *"`confirmHoldMs` es estático, con lo cual esta función siempre se va a cumplir (?)"*

Sobre la guarda del hold-watchdog en `CoordinatorParkingDetector`:

```kotlin
val holdWatchdogJob = if (config.confirmHoldMs > 0) { … }
```

## Respuesta: sí en producción, y no, no es código muerto

`ParkingDetectionConfig.confirmHoldMs` vale **2 min por defecto** (`ParkingDetectionConfig.kt:826`),
así que **en la app la condición es siempre cierta** y el watchdog siempre se lanza. La intuición era
correcta.

Pero borrar la guarda rompería los tests: **tres ficheros la ponen a 0** para apagar el watchdog —
`CoordinatorParkingDetectorTest:73`, `DetectionTraceReplayTest:1003` y
`StagePrecedenceCharacterizationTest:215`. Es una **costura de test, no una opción de runtime**, y eso
es lo que ahora dice el comentario en su sitio.

## El hallazgo, que es peor que la pregunta

Al comprobarlo salió esto:

```
grep -rn "STARVED" composeApp/src --include=*.kt
  → CoordinatorParkingDetector.kt   (lo emite)
  → HoldLifecycle.kt                (lo declara)
  → nada más
```

**`HoldAction.STARVED` no aparece en un solo test de todo el repo.** Los tests que sí usan
`confirmHoldMs > 0` ejercen la rama de **descarte** — el "bug del estanco": vuelves a conducir dentro
del hold y el confirm tentativo se tira, re-anclando en la plaza final. **La rama en que el watchdog
dispara de verdad no la ejerce nadie.**

Y es la que más vigilancia merecería, porque su propio comentario lo dice:

> *"A pin planted with NO fix to re-validate it. Deliberate, but a trace has to say so — in forensics
> this is what «a spot appeared and I don't know why» looks like."*

Es **el único sitio donde confirma un RELOJ y no una medición** — la excepción declarada a la
doctrina rectora (*el evento nomina, sólo el movimiento medido confirma*). Una excepción sin testigo
es exactamente lo que la doctrina existe para impedir. Misma familia que
`DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001` y `DET-PARKDIAG-ROTATION-HAS-NO-WITNESS-001`.

## Por qué no está testeada, y por qué no hay excusa

Parece que exige esperar 2 minutos de reloj real. **No los exige:** `runTest` usa tiempo virtual y
salta los `delay`, y el propio fichero de tests ya inyecta su reloj (`clock = { fakeNow }`) y usa
`UnconfinedTestDispatcher`. Está **sin escribir, no bloqueada**.

## Qué haría falta

- Un test que arme un confirm pendiente, **corte el stream de fixes**, y compruebe al vencer
  `confirmHoldMs + HOLD_WATCHDOG_MARGIN_MS`: (a) la plaza se guarda en la ubicación **anclada**,
  (b) se emite `HoldAction.STARVED`, (c) la sesión termina.
- **Neutralización obligatoria**: quitar el `holdWatchdogJob` tiene que ponerlo rojo. Si no, no
  protege nada.
- ⚠️ Cuidado con `pendingConfirm === pending`: la comparación es **por identidad** y es deliberada
  (ver `project_det_proposal_3_mute_branches` — ⛔ no meterle contadores). Un test descuidado que
  reconstruya el objeto pasaría por casualidad o fallaría por el motivo equivocado.

## Relacionado

- `DET-COORDINATOR-IMPORTS-ITS-OWN-PACKAGE-001` — de donde salió el FIXME.
- `DET-AUDIT-002 T7/M2` — el incidente que creó el watchdog.
- `DET-HOLD-BRANCHES-MUST-SPEAK-001` — el guardrail de la vía del hold: vigila que **hable**, no que
  **funcione**.
