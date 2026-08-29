---
name: det-change
description: Checklist obligatorio al cambiar el algoritmo de detección de aparcamiento en Paparcar — doctrina, tests, PARKING-DETECTION.md, galería mock y barrido de consumidores. Usar SIEMPRE que se toque CoordinatorDetectionStrategy, BluetoothDetectionStrategy, los Evaluate*UseCase, ParkingDetectionConfig, los workers de detección (safety-net, backfill), el geofence manager, los mappers UserParking o cualquier guard DET-*/LOC-*/PARKING-*/MAPPER-*.
---

# Cambio en el algoritmo de detección

Esto es la joya de la corona y la parte donde un cambio silencioso cuesta un field-test entero.
Nada se declara terminado sin los 5 bloques.

## 1 · Comprobar la doctrina ANTES de escribir código

> **El evento NOMINA, solo el movimiento MEDIDO confirma.**
> Un EXIT de geocerca o un AR ENTER solo despiertan/arman. Ninguno confirma una plaza por sí mismo:
> hace falta conducción medida en el stream (o pasos/egress inambiguos). Un evento re-entregado
> (Doze/OEM) **nunca** coloca un pin.

> **Fallo asimétrico: mejor falso negativo que falso positivo.**
> Ante la duda se PREGUNTA (nudge / prompt), nunca se planta una plaza fantasma.

> **Todo trigger dispara SIEMPRE**, aunque llegue tarde, con verificación tardía. Un evento viejo
> pierde autoridad directa (pasa al evaluador), nunca se descarta.

⛔ **Las dos estrategias NUNCA se mezclan.** No meter señales Bluetooth en el scoring del
Coordinator. BT es determinista (MAC del coche → disconnect → fix → ≥30 m → confirma); Coordinator
es probabilístico. Son carriles separados que solo convergen en `ConfirmParkingUseCase`.

⛔ **La decisión vive en un use case PURO de `commonMain`** (`EvaluateGeofenceExitUseCase`,
`EvaluateArEnterArmUseCase`, `EvaluateParkingDecisionUseCase`, `EvaluateSafetyNetCheckUseCase`…).
`CoordinatorDetectionService` (androidMain) solo hace I/O y side-effects, y serializa todos los
triggers en el intake único [DET-INTAKE-001]. Si te ves metiendo lógica de decisión en el service,
para: va en un use case testeable.

⛔ **Sistemas, no parches.** Si el bug recurre, falta un modelo coherente, no otra comprobación.
Los invariantes van en UN sitio.

## 2 · Barrido de consumidores — el paso que más caro sale saltarse

Al cambiar el significado de una señal (qué cuenta como conducción, como egress, como armado…):

```bash
grep -rn "<señal / helper / flag>" shared/src app/src --include=*.kt
```

Clasificar **cada** hit en el doc del ticket: `cerrado` / `cubierto por convergencia` /
`exento con razón`.

> DET-DRIVE-PROOF-001 cerró "un fix no es movimiento" solo en la estadística de sesión. Cuatro
> horas después el mismo fix fantasma explotó el mismo defecto en la vía de publicación de plaza
> (`isCredibleDrivingSpeed`) → segundo falso positivo, plaza fantasma publicada, una tarde perdida.
> El barrido costaba un `grep`.

## 3 · Tests

- Toda UseCase nueva o modificada → test unitario. Fakes, no mocks. Naming
  `should_expectedBehavior_when_condition`.
- Los evaluadores son puros y síncronos → se testean con escenarios de evidencia, sin Android.
- Ejecutar la suite completa antes de dar nada por hecho (vía Bash, **no** PowerShell —
  el repo no tiene `gradlew.bat`):
  ```bash
  ./gradlew :shared:testDebugUnitTest --console=plain
  ./gradlew :shared:testDebugUnitTest --tests "com.rndeveloper.paparcar.domain.coordinator.*" --console=plain
  ```
- Reportar el número de tests verdes en el resumen (el user lo usa como referencia entre sesiones).

## 4 · Documentación y mock — en la MISMA tarea, nunca "en un PR de limpieza"

**a) `docs/detection/PARKING-DETECTION.md`** — obligatorio. Añadir entrada en la **Sección 2** (log
cronológico de fixes) con: id de ticket, commit (o "pending"), reporte del user en una línea, causa
raíz, fix, y riesgo de fix acompañante. Si el cambio resuelve algo de la **Sección 3** (preguntas
abiertas) → **MOVERLO**, no duplicarlo.

> Por qué: es la referencia canónica que explica **por qué existe cada guard**. Sin ella, la
> siguiente sesión borra un guard sin saber qué modo de fallo cubría — exactamente el patrón que
> originó LOC-001 y LOC-002.

**b) Dev Catalog / galería de estados** — si el cambio introduce pantalla, estado MVI, variante
(loading/empty/error/modo) o condición que afecte al routing:
- `ScreenGroup` nuevo en `app/src/mock/.../dev/StateGalleryScreen.kt`, llamando a
  `XxxContent(state=…)` y espejando su `*Previews.kt`.
- Condición de routing → `MockScenario` + el fake que la lee + preset/control en `DevCatalogScreen.kt`.
- Verificar `./gradlew :app:assembleMockDebug` y que prod no se rompe.

**c) `docs/backlog/<ticket>.md`** — actualizado en tiempo real, no al final.

**d) Strings nuevos → los 9 locales** (`values`, `-es`, `-it`, `-pt`, `-fr`, `-de`, `-nl`, `-pl`, `-ro`).

## 5 · Provenance y copy

- Si el cambio crea un camino nuevo de confirmación → **añadir su valor a `detectionPath`** y
  espejarlo a Firestore. Un pin sin provenance no es diagnosticable en remoto (lección del 20-07).
  Auditar la paridad DTO ⇄ Entity ⇄ dominio end-to-end al tocar campos.
- Si el cambio genera texto para el user (nudge, prompt, notificación): **causa + consecuencia +
  remedio, sin mecánica interna** y sin jerga inventada. El user nunca lee "sentry-wake" ni
  "egress cinemático".

## 6 · Checklist final

- [ ] Doctrina respetada (evento nomina / movimiento confirma · fallo asimétrico · carriles separados)
- [ ] Decisión en use case puro de `commonMain`, service solo I/O
- [ ] Barrido de consumidores hecho y clasificado en el doc
- [ ] Tests verdes (nº reportado) + tests nuevos para la lógica nueva
- [ ] `docs/detection/PARKING-DETECTION.md` actualizado
- [ ] Dev Catalog / `MockScenario` en sync + `assembleMockDebug` compila
- [ ] `docs/backlog/<ticket>.md` al día
- [ ] Strings en los 9 locales
- [ ] `detectionPath` / `armEvidence` cubren el camino nuevo
