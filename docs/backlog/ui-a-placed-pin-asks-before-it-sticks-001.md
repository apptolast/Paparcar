# UI-A-PLACED-PIN-ASKS-BEFORE-IT-STICKS-001 · Diálogo de confirmación propio al avisar una plaza o marcar/corregir el aparcamiento

**Estado:** ✅ Done · en master (22-11-2026) · rama y worktree cerrados. ⏳ sin ver en móvil real —
solo emulador no disponible en esta sesión (sin Oppo/Redmi conectados); el user decidió mergear
sin esperar al field-device.

## Problema
`ReportPeek` (avisar plaza libre) y `AddingParkingPeek` (marcar/corregir/reaparcar) disparan la
escritura en cuanto se pulsa el CTA primario — sin ningún paso intermedio. Un toque accidental
publica una plaza fantasma o fija mal dónde está el coche; ambas tienen consecuencia real (la
plaza se ve en el mapa de otros, el aparcamiento decide qué ve la detección).

## Doctrina violada
Ninguna de detección. Es un gap de UX: el proyecto ya tiene un molde propio de confirmación
(`PapAlertDialog`) usado para el borrado de un aparcamiento (`confirmingDelete` en
`AddingParkingPeek`), pero los dos CTAs de escritura de ubicación no lo usan — piden confirmación
para destruir un registro y no para crearlo/moverlo.

## Diseño
Mismo molde (`PapAlertDialog`, acento `Primary`), aplicado en los CUATRO botones que escriben una
ubicación nueva:

| Peek | Botón | Intent que dispara | Diálogo |
|---|---|---|---|
| `ReportPeek` | "Confirm spot here" | `ConfirmReportSpot` | nuevo, icono `Campaign` |
| `AddingParkingPeek` (crear) | "Park here" | `ConfirmAddParking(false)` | nuevo, icono `DirectionsCar` |
| `AddingParkingPeek` (corregir) | "Correct location" | `ConfirmAddParking(false)` | nuevo, icono `DirectionsCar` |
| `AddingParkingPeek` (repark) | "Mark new parking" | `ConfirmAddParking(true)` | nuevo, icono `DirectionsCar` |

- El body de cada diálogo repite la dirección/lugar ya resuelto — el mismo `title` que ya recibe
  cada peek (`slice.settlingCameraTitle()`), vía `%1$s`. No se añade ningún campo nuevo al slice.
- `primaryLabel` del diálogo reutiliza el string del botón que lo abrió (mismo patrón que el
  borrado: "Delete this parking?" → primario "Delete parking"), así la confirmación no inventa
  vocabulario nuevo.
- Sin "no preguntar más": aparece siempre. Decisión del user — es una salvaguarda, no una molestia
  a evitar.
- La revisión de detección (Not parked / Wrong car) y el borrado de aparcamiento quedan FUERA de
  este ticket — no se mencionaron en el encargo y ya tienen su propio flujo.
- Vocabulario: PLAZA para `ReportPeek` (comunidad), APARCAMIENTO para `AddingParkingPeek` (tuyo) —
  [COPY-SPOT-IS-NOT-A-PARKING-001]. El "corregir" usa "ubicación/location" genérico, igual que
  `home_parking_menu_correct` ya hace en los 9 locales.

## Criterio de éxito
- Pulsar cualquiera de los 4 CTAs abre `PapAlertDialog` con la dirección resuelta en el cuerpo, y
  SOLO confirmar en el diálogo dispara el intent real.
- Cancelar o descartar el diálogo no cambia nada (sigue en el peek, sin guardar).
- 9 locales con las 8 strings nuevas (`LocaleParityGuardrailTest`).
- `./gradlew testAll` verde.

## Consumidores auditados
- `ReportPeek.kt` — único lugar que dispara `ConfirmReportSpot`.
- `AddingParkingPeek.kt` — único lugar que dispara `ConfirmAddParking` (crear/corregir/repark).
- No hay otro call site de estos dos intents (verificado por grep en `HomeViewModel.kt`).
