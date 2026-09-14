# DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001 · un trigger dice qué produjo, no solo cómo murió

**Estado:** ✅ Done · mergeado en master el 14-09-2026

## Problema

Encargo del user (14-09-2026): *"a partir de ahora también almacenaremos estadística de con qué
triggers o sistemas conseguimos detección, para medir cuál es la más eficiente y productiva y cuál
menos"*.

Hoy no se puede contestar, y no por falta de datos sino porque **las dos mitades de la fracción
viven en sitios que no se pueden dividir**:

- **Denominador — existe y está bien.** [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] dejó
  `DetectionEvent.Trigger` con `trigger` + `TriggerDisposition`, una sola puerta
  (`CoordinatorDetectionService.logTrigger`, 11 call sites) y un ledger diario. *"type=TRIGGER
  agrupado por disposition es el histograma completo de un dispositivo"*. Perfecto… para saber cómo
  MUEREN los triggers.
- **Numerador — existe, pero en otra colección.** Cada pin persiste `armEvidence` (qué lo armó) y
  `detectionPath` (qué lo confirmó) desde [DET-PIN-PROVENANCE-001]. Pero eso vive en
  `users/{uid}/parkingHistory`, una colección por usuario que no es un ledger y que no se puede
  cruzar con los triggers de `diagnostics/{uid}`.

O sea: sabemos cuántos GEOFENCE_EXIT murieron huérfanos y sabemos cuántos pins llevan
`armEvidence = GEOFENCE_EXIT`, y **no hay forma de poner una cifra encima de la otra**. La palabra
que falta en el vocabulario no es "cómo murió" sino **"qué produjo"**.

Y la disposición no lo sustituye: `ARMED` es el bucket interesante y es exactamente el que no dice
nada del resultado — un trigger armado puede acabar en pin, en pregunta o en nada, y esos tres
desenlaces son justo lo que el user quiere medir.

## Doctrina

No hay invariante roto: esto es vocabulario nuevo de observabilidad, no un guard. Las reglas que sí
aplican y que el diseño respeta:

- **Una sola puerta por hecho.** El valor del ledger de triggers es que *ninguna rama emite su
  variante propia*. El de rendimientos tiene que nacer igual: una puerta por tipo de rendimiento,
  y las dos existen ya y están documentadas como tales — `ConfirmParkingUseCase` ("ambas estrategias
  convergen aquí") y `ReportSpotReleasedUseCase` ("the single choke point for every spot
  publication", que es donde [ANALYTICS-EVENTS-001] ya cuenta `spot_released`).
- **El vocabulario es dominio puro**, la emisión es side-effect. La disposición ya lo hace así
  (enum en `domain/detection/sentry/`, emisión en el service); el rendimiento igual.
- ⛔ **No inventar un vocabulario paralelo.** `ArmEvidence`, `DetectionPath` y `ParkingStrategy` ya
  nombran al productor. Lo único que falta es el RECUENTO, así que el evento nuevo los cita, no los
  duplica.

## ⚠️ El diseño cambió al leer el código — dos hallazgos

Lo de arriba se escribió antes de abrir `AnalyticsTracker.kt`. Al hacerlo, dos cosas:

**1. El numerador ya estaba casi entero.** `AnalyticsEvent.ParkingSessionStarted` **ya lleva
`detectionPath`** y se emite desde `ConfirmParkingUseCase` — la puerta donde convergen las dos
estrategias. Así que no hace falta ningún evento `det_detection_achieved` nuevo: sería un
vocabulario paralelo contando lo mismo dos veces. Lo único que le faltaba era la dimensión de QUIÉN
armó (`armEvidence`); el path dice cómo se confirmó, y dos pins del mismo path pueden venir de
sistemas distintos.

**El hueco real es el denominador**: los éxitos se contaban, **los intentos no**. Por eso ninguna
tasa se podía calcular sobre la base instalada — solo sobre los tres usuarios con diagnósticos.

**2. ⛔ Una salida no tiene procedencia.** Al ir a cablear la mitad de `SpotReleased` resultó que no
hay nada honesto que estampar: `detectionPath` y `armEvidence` describen cómo se colocó el PIN,
nunca cómo se detectó la MARCHA. Pasarlos ahí etiquetaría la plaza con la procedencia de la llegada
que la precedió — una cifra falsa, que es peor que ninguna. La materia prima existe
(`DepartureProof`, `preconfirmed`, el trigger que despachó) pero no se persiste como etiqueta en
ningún sitio. Ficha propia: **`DET-A-DEPARTURE-HAS-NO-PROVENANCE-001`**.

## Diseño (el que se ejecutó)

**El denominador, que era el hueco real.** `AnalyticsEvent.TriggerFired(trigger, disposition)`,
emitido desde `CoordinatorDetectionService.logTrigger` — la puerta única que ya existía, de modo que
el contador anónimo y el ledger del dispositivo no pueden discrepar. Reusa el MISMO
`TriggerDisposition` que la traza per-device: las dos poblaciones hablan el mismo idioma y sus
cifras son comparables.

**El numerador, completado.** `ParkingSessionStarted` gana `armEvidence`, la única dimensión que le
faltaba. ⛔ **Ningún evento nuevo de "detección conseguida"**: `parking_session_started` YA es ese
hecho y se emite en `ConfirmParkingUseCase`, donde convergen BT y Coordinator. Añadir uno habría
sido exactamente el vocabulario paralelo que la doctrina de arriba prohíbe, contando lo mismo dos
veces.

**El detalle por dispositivo no necesitaba nada.** `DetectionEvent.Trigger` (denominador) y la
pareja `SessionStarted.evidence` → `SessionEnded.outcome` (numerador) ya viven en el mismo
`diagnostics/{uid}`: la fracción sale de una `collectionGroup` sobre `events`. El `DetectionEvent.
Yield` que este doc proponía antes de leer el código habría duplicado esa información.

⛔ **Sin ubicación.** `det_trigger_fired` no lleva celda a propósito: un trigger no es un lugar, y
con celda estaría informando de dónde está el usuario **cuando no se detectó nada** — más revelador
que los eventos que sí la llevan, y un tipo de dato nuevo en la declaración de Data Safety. Tal como
queda, este ticket **no la invalida**.

## Lo que se deja fuera, dicho

- ⛔ **La mitad de la SALIDA**, por no poder etiquetarla con verdad →
  [`DET-A-DEPARTURE-HAS-NO-PROVENANCE-001`](det-a-departure-has-no-provenance-001.md).
- ⏳ **"Preguntamos en vez de plantar"** como desenlace medible. Es frecuente —Fernando lleva **6 de
  43** pins en `nudge_bt_no_ride`— pero los nudges y prompts salen de cuatro sitios sin puerta
  común, y abrirla es un ticket propio, no un apéndice de éste. La conversión pregunta→pin SÍ se ve
  hoy (el pin nace con `detectionPath = nudge`); lo que no se ve son las preguntas que nadie
  contestó.
- ⏳ **"No produjo nada"** no se emite: es el complemento aritmético (intentos menos éxitos), y
  emitirlo sería contar lo mismo dos veces.

## Criterio de éxito

- `det_trigger_fired` desglosado por `trigger` y `disposition`, dividido por
  `parking_session_started` desglosado por `arm_evidence`, responde qué sistema es productivo sobre
  toda la base instalada.
- En un dispositivo opted-in la misma fracción sale de `diagnostics/{uid}`, sin evento nuevo.
- Ni un solo evento nuevo lleva ubicación.

## Lo implementado (14-09-2026)

| Cambio | Dónde | Qué responde |
|---|---|---|
| `AnalyticsEvent.TriggerFired(trigger, disposition)` | emitido desde `logTrigger`, la puerta única que ya existía | **el denominador**, para toda la base |
| `ParkingSessionStarted.armEvidence` | `ConfirmParkingUseCase` | qué sistema armó el pin que se consiguió |
| `EVENT_TRIGGER_FIRED = "det_trigger_fired"` + `PARAM_TRIGGER`/`PARAM_DISPOSITION`/`PARAM_ARM_EVIDENCE` | `FirebaseAnalyticsTracker` | los nombres de cable, fuera de los use cases |

⛔ **`det_trigger_fired` va sin `cell`, a propósito y con razón escrita en el código**: un trigger no
es un lugar, y en el momento en que llevara celda estaría informando de dónde está el usuario
**cuando no se detectó nada** — bastante más revelador que los eventos que sí la llevan, y un tipo de
dato nuevo en la declaración de Data Safety. Tal como queda, este ticket **no invalida** la
declaración ya hecha.

El detalle por dispositivo no necesita evento nuevo: `DetectionEvent.Trigger` (denominador) y la
pareja `SessionStarted.evidence` → `SessionEnded.outcome` (numerador) ya viven en el mismo
`diagnostics/{uid}`, así que la fracción se calcula con una `collectionGroup` sobre `events`.

**Suite: 2833 tests verdes, 0 fallos** (258 clases). Compilan iOS y los dos sabores.
**Dev Catalog:** sin cambios (ni pantalla, ni estado, ni routing). **Strings:** ninguno.

## Consumidores auditados

| Sitio | Clasificación |
|---|---|
| `logTrigger` (11 call sites vía una puerta) | **cerrado** — emite a los dos destinos desde la misma puerta; ninguna rama publica su variante |
| `ConfirmParkingUseCase` | **cerrado** — convergencia de BT y Coordinator, estampa `armEvidence` |
| `ReportSpotReleasedUseCase` / `SpotReleased` | **fuera de alcance con razón** — no hay procedencia de salida que estampar (`DET-A-DEPARTURE-HAS-NO-PROVENANCE-001`) |
| Nudges y prompts (`showMarkParkingNudge`, `showStillParkedPrompt`, 4 sitios) | **fuera de alcance** — `ASKED` necesita una puerta común que no existe |
| `NoOpAnalyticsTracker` (sabor mock) | **cubierto** — el `when` exhaustivo no aplica, es un `Unit` |
| `FakeAnalyticsTracker` (tests) | **cubierto** — graba el evento nuevo sin cambios |

## Pendiente

- 📋 [`DET-A-DEPARTURE-HAS-NO-PROVENANCE-001`](det-a-departure-has-no-provenance-001.md) — ficha escrita, sin rama.
- ⏳ `ASKED` como rendimiento, cuando los nudges tengan puerta común.
