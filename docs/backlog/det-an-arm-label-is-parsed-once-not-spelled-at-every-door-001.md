# DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001 · la palabra del arm se parsea una vez

**Estado:** ✅ Done · **1.985 tests, 0 fallos** · `:app:compileProdDebugKotlin` +
`:app:compileMockDebugKotlin` verdes · conducta idéntica: los dos conjuntos cerrados conservan sus
miembros exactos, congelados en test
**Origen:** primera de las tres membresías pendientes de la **Pieza 2** del rediseño
(`docs/detection/REDESIGN-DETECTION-SYSTEM.md:594`, *«`isVerifiedLabel(String)` → propiedad declarada
en el `sealed interface`»*).

## Problema

Un arm lleva **medidas** — una velocidad, un hueco enter→exit, una duración de enganche BT, un pico
heredado. La sesión persistida lleva sólo la **palabra**. Así que el string no se puede convertir de
vuelta en `ArmEvidence` sin inventarse los números, y cada puerta que recibe una sesión en vez de un
arm contestaba su pregunta **volviendo a deletrear**:

| sitio | qué preguntaba | cómo |
|---|---|---|
| `ArmEvidence:316` `isVerifiedLabel(String?)` | ¿la salida se probó FUERA de este stream? | 4 `label ==` |
| `ArmEvidence:335` `confirmsSilentlyWithoutMeasuredDrive(String?)` | ¿puede pinchar en silencio? | 3 `label ==` |
| `ConfirmParkingUseCase:193,207` | los guards de repark y de aserción | llaman al primero |
| `EvaluateParkingDecisionUseCase:323` | la política de confirm silencioso | llama al segundo |

Los dos predicados son **espejos a mano** de dos `when` exhaustivos que viven unas líneas más arriba,
en el mismo fichero. Nada los ata. Un arm nuevo cuyo `when` conteste `true` no aparece en la lista de
strings, y las dos representaciones dejan de coincidir sin que falle nada.

⚠️ **Dirección del fallo: FN, no FP.** Ambos espejos fallan CERRADO — una etiqueta desconocida
contesta `false`, lo que significa *pregunta*. Así que la divergencia no planta pines fantasma:
pregunta donde no debía, en silencio, para siempre. Es un defecto de mantenimiento con consecuencia
de usuario, no un FP vivo. Conviene decirlo porque el análisis previo llegó a afirmar que
`isVerifiedLabel` **ya discrepaba** sobre `verified_enter`: no discrepa — se comparó con la hermana
equivocada (`confirmsSilentlyWithoutMeasuredDrive`, que contesta otra pregunta). Coinciden hoy.

**Y el guardarraíl que prohíbe exactamente esta forma no lo veía.**
`DetectionDoctrineGuardrailTest.no hand-kept set of arm labels decides anything` hace
`.filter { it.name != "ArmEvidence" }` — y los dos conjuntos vivían dentro de `ArmEvidence.kt`. La
exención es legítima para **declarar** las palabras y estaba amnistiando una **decisión** tomada
comparándolas. Misma lección que `UI-TYPE-SYSTEM-HYGIENE-001`.

## Doctrina violada

*Sistemas, no parches* y la regla de la Pieza 2: **la membresía se declara en el tipo, no se
deletrea en el sitio que decide.** `SessionOutcome`, `DetectionPath` y el propio `ArmEvidence` ya
curaron esto en su esquina; la palabra persistida se quedó fuera.

## Señales / datos disponibles

- `DetectionPath.ofLabel` ya es el patrón: string → tipo, **falla a null**, y su guardarraíl
  (`every detectionPath literal is a declared DetectionPath`) se apoya en él.
- El productor **ya tenía el valor tipado**: `StageInputs:70` hacía `evidenceLabel = session.armEvidence`
  (String) para que el evaluador, tres líneas después, lo volviera a clasificar por deletreo.

## Diseño

**Un tipo para la palabra.** `ArmLabel` — enum de las 9 palabras que la app escribe — con
`ofPersisted(String?)` como **único** sitio donde se compara un string de arm, y las dos preguntas de
clasificación contestadas ahí una vez. `ArmEvidence` declara su `label: ArmLabel` (un caso nuevo no
compila hasta elegirla) y **delega** ambas preguntas en él, en vez de contestarlas por segunda vez.

⛔ **Por qué un enum aparte y no un caso más del sealed**: `ofPersisted` tiene que ser fiel. Devolver
`VerifiedBySpeed(speedKmh = 0f)` para la palabra `verified_speed` sería inventarse una medida que
alguien podría leer después. La palabra no lleva payload; el arm sí.

⚠️ `VERIFIED_LATE` es la razón de que esto no pueda vivir sólo en la jerarquía sellada: **es una
palabra sin arm**. `SessionTelemetry.departureConfirmed()` la escribe cuando el worker mide una
salida DESPUÉS del arm, así que sólo existe como palabra sobre una sesión viva.

El string queda confinado a los bordes que persisten:
- `SessionTelemetry.armEvidence` pasa a ser `ArmLabel` (era `String`).
- `ConfirmParkingUseCase(armEvidence: ArmLabel?)` y `ParkingDecisionInput.evidenceLabel: ArmLabel?`.
- `.persisted` se escribe en 4 sitios: el `SessionStarted` de diagnóstico, el `UserParking` que
  guarda el confirm, y las dos comparaciones de un replay contra el campo persistido.

**Guardarraíl nuevo** — `no arm word is compared to a string literal` — y la exención de `ArmEvidence`
retirada del guardarraíl viejo. Comparar el TIPO (`armEvidence == ArmLabel.VERIFIED_ENTER`) no se
prohíbe: el compilador es dueño de ese vocabulario. Lo que se prohíbe es re-derivar la membresía de
una ortografía.

## Criterio de éxito

- No existe ningún predicado que clasifique un arm comparando strings. `ofPersisted` es la única
  comparación de palabra del dominio.
- Las dos representaciones no pueden discrepar: `ArmLabelTest` ata `driveAuthorization != None` con
  `label.isVerifiedDeparture` **para todos** los arms, y exige que `allArms` cubra todas las palabras
  salvo la que no tiene arm.
- ✅ **El guardarraíl nuevo se vio fallar antes de darlo por bueno**: inyectado
  `input.evidenceLabel?.persisted == "manual"` en `EvaluateParkingDecisionUseCase`, el test se puso
  rojo (`no arm word is compared to a string literal FAILED`); revertido, verde.
- Conducta idéntica: los dos conjuntos (confirm silencioso · salida verificada) están congelados en
  test con sus miembros exactos, y son los mismos que antes.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `ArmEvidence.isVerifiedLabel` · `confirmsSilentlyWithoutMeasuredDrive(String?)` | **cerrados** — borrados; las preguntas viven en `ArmLabel` |
| `ArmEvidence.LABEL_*` (9 constantes) | **cerradas** — borradas; el vocabulario es `ArmLabel(persisted)` |
| `ConfirmParkingUseCase:193,207,305,250` | **cerrado** — pregunta al tipo; escribe `.persisted` al persistir y al trazar |
| `EvaluateParkingDecisionUseCase:323` + `ParkingDecisionInput.evidenceLabel` | **cerrado** — tipado |
| `SessionTelemetry.armEvidence` + `armed/departureConfirmed/departureDismissed` | **cerrado** — tipado |
| `StageInputs:70` | **cubierto** — productor y consumidor son ya el mismo tipo, sin conversión |
| `CoordinatorParkingDetector:473,575` | **cerrado** — `.label` al armar, `.persisted` sólo en la traza |
| `BluetoothParkingDetector:180,209` | **cerrado** — pasa `.label` en vez de `.persistLabel` |
| `DetectionEffectExecutor:189` | **cubierto** — ya pasa el tipo |
| `UserParking.armEvidence` (Room/Firestore) · `ParkingSessionMapper` · `UserParkingReconcile` · `SaveNewParkingSessionWorker` | **exentos con razón** — son la frontera de persistencia: ahí la palabra ES un string, y ninguno la clasifica |
| `presentation/` · `ui/` | **exentos** — no leen `armEvidence`: verificado por grep, cero hits |
| `VerifyDepartureEvidenceUseCase:31` | **cerrado** — sólo era un enlace KDoc a la constante borrada |
| `DetectionDoctrineGuardrailTest` (`ArmEvidence` eximido) | **cerrado** — exención retirada + regla nueva, falsificada |
