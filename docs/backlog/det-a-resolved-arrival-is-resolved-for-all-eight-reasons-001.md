# DET-A-RESOLVED-ARRIVAL-IS-RESOLVED-FOR-ALL-EIGHT-REASONS-001

> **Estado:** ✅ **Done** — mergeado a master el 31-08-2026 (squash, `c5bfd274`). Rama y worktree borrados.
> **Origen:** **Pieza 3c** del rediseño — fallo **#7** («el sello se escribe para las 8 razones, no
> para 1»). **Cierra también #6**, que el propio audit de 3b había declarado inseparable de éste.
> **Con esto la Pieza 3 queda COMPLETA** (3a, 3b, 3c).

---

## 1. Auditoría de 3c contra master (antes de tocar nada)

3c pide dos cosas: **P4** (evidencia declarada inválida no se reutiliza) y **#7** (el sello). De las
cuatro piezas, tres ya estaban cerradas por tickets posteriores:

| pieza de 3c | estado real en master |
|---|---|
| P4 · ancla de hueco en el **guardado desatendido** | ✅ `DET-GAP-ANCHOR-ZONE-001` — el hueco tiene duración, así que acota la zona en vez de invalidarla |
| P4 · ancla de hueco en el **honest close** | ✅ `DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001` — la duda se acota por pasos, no por la accuracy del fix |
| P4 · ancla de hueco en el **backfill** | 🔴 **es el sello: #7** |
| #7 · el sello para las 8 razones | 🔴 **abierto — este ticket** |

Mismo patrón que 3b: lo que quedaba vivo era **un solo sitio**.

## 2. El bug

`CoordinatorDetectionService.maybeStampArrivalResolution()` decidía si sellar comparando por
**igualdad contra UN outcome**:

```kotlin
if (parkingDetectionCoordinator.lastOutcome !=
    SessionOutcome.AbortedUnattended(UnattendedSaveReason.GAP_ANCHOR.key)
) return
```

Pero el veredicto que produce ese outcome tiene **ocho** razones
(`UnattendedSaveReason`: `no_drive`, `no_drive_egress`, `unpinned_anchor`, `egress_mismatch`,
`gap_anchor`, `walk_entered_anchor`, `vehicular_egress`, `human_powered`), **todas** llegan al mismo
`Ask`, y **todas** terminan la sesión por un único productor
(`DetectionEffectExecutor.nudge → SessionOutcome.AbortedUnattended(reason.key)`).

**Siete de cada ocho llegadas quedaban resueltas por el coordinator y las volvía a decidir el safety
net un minuto después**, con menos información. Que es literalmente el defecto para el que existe
`DET-BACKFILL-TAINT-001` (campo 2026-07-30 20:42, Redmi/Jerez: el backfill plantó el pin que el
coordinator acababa de rechazar) — cerrado entonces para una razón y dejado abierto para las otras
siete.

## 3. El arreglo

La pregunta se le hace **al tipo**, no a una igualdad: `SessionOutcome.resolvesTheArrival`, propiedad
abstracta del sealed interface, así que **un noveno outcome no compila hasta que su autor responde**.
Es la cuarta membresía declarada de ese fichero, exactamente como las tres que ya tenía
(`isConfirmed`, `triggersHonestClose`, `sentryStreakEffect`) y por la misma razón escrita en su
cabecera: *la membresía se decidía por cómo estaba escrito el string*.

⛔ **El lado FALSO es lo interesante, y `AbortedResponseTimeout` es el casi-acierto.** Responde
`false` a propósito: ahí el veredicto **sí** dijo *guardar exacto aquí* y fue un **guard de confirm**
quien lo rechazó — otro actor, y la colocación del propio backfill pasa por esos mismos guards. Los
abortos silenciosos (`false_enter`, `no_movement`…) nunca llegaron a una decisión de aparcamiento, y
un `Confirmed` ya tiene pin.

**Y el sello se lleva la razón consigo** (`KEY_ARRIVAL_RESOLUTION_REASON`), para que el aplazamiento
del backfill pueda nombrar su causa en vez de ser un salto inatribuible — el mismo argumento que dio
a cada prompt su motivo (`DET-PROMPT-STATES-ITS-REASON-001`). En un sello escrito por un build
anterior el campo no está, y la traza lo dice en vez de inventarlo.

## 4. ⛔ #6 se cierra con esto, como predijo el audit de 3b

`EvaluateBackfillDeferralUseCase` devuelve «plantar normal» cuando no hay sello. Parecía un default
permisivo (fallo **#6**), y 3b lo declaró **inseparable de #7**: mientras el sello se escribiera para
1 de 8, el `null` era el **caso normal**, y hacer que difiriera habría suprimido *todo* backfill
legítimo.

Con el sello escrito para las ocho, el `null` por fin significa lo que dice —*nadie resolvió esta
llegada*— y plantar es la respuesta correcta. **No hay cambio de código en #6**: se cierra porque
cambió lo que su entrada significa. Queda escrito en su KDoc.

## 5. Barrido de consumidores

| # | fichero | qué había | qué hay |
|---|---|---|---|
| 1 | `domain/detection/physics/SessionOutcome.kt` | 3 membresías declaradas | +`resolvesTheArrival`, respondida por los **11** casos |
| 2 | `detection/service/CoordinatorDetectionService.kt` | igualdad contra 1 outcome | `outcome.resolvesTheArrival` + sella la razón |
| 3 | `detection/worker/ParkingSafetyNetWorker.kt` | 2 claves de prefs | +`KEY_ARRIVAL_RESOLUTION_REASON` con su contrato |
| 4 | `detection/worker/ParkingBackfillWorker.kt` | `Pair<Long, GpsPoint>` y un log sin causa | `Triple<…, String?>` y el log nombra la razón |
| 5 | `domain/usecase/parking/EvaluateBackfillDeferralUseCase.kt` | KDoc que hacía parecer defensivo el null | dice por qué el null ya es una ausencia real (#6) |
| 6 | `DetectionEffectExecutor.nudge` | único productor de `AbortedUnattended` | **sin tocar** — es lo que hace exacta la propiedad |

## 6. Tests

**Nuevos** (2, en `SessionOutcomeTest`)
- `should_let_exactly_the_unattended_verdict_resolve_the_arrival` — el censo sobre los 11 outcomes:
  **exactamente** los abortos del veredicto desatendido resuelven la llegada, y ninguno más.
- `should_resolve_the_arrival_for_every_one_of_the_eight_unattended_reasons` — recorre
  `UnattendedSaveReason.entries` (con testigo de población: suelo 4 sobre 8 medidas), que es el
  defecto exacto que se cierra.

**Falsación (⛔ un test sin verlo fallar siempre pasa)** — dos inyecciones, una por dirección:
1. `AbortedUnattended.resolvesTheArrival = false` → **2 FAILED**;
2. `AbortedResponseTimeout.resolvesTheArrival = true` (ensanchar el lado falso) → **1 FAILED**.

Restaurado, verde.

**Suite completa:** `:shared:testDebugUnitTest` → **2069 tests, 0 fallos**.
`:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` OK.

⚠️ **Lo que la suite NO cubre, y lo digo en vez de callarlo**: el sello vive en `SharedPreferences` y
lo escribe un `Service` de `androidMain`. La condición que decide *si* sellar es la que se ha tipado y
testeado; la escritura en prefs y su lectura desde el worker no tienen test, igual que antes de este
ticket. Se verá en campo por la línea nueva de traza, que ahora nombra la razón.

## 7. Lo que este ticket NO hace

- **No toca P4**: sus tres mitades ya estaban cerradas (§1).
- **No cambia la ventana ni el radio** del aplazamiento (`arrivalResolutionWindowMs` 20 min,
  `arrivalResolutionMatchRadiusMeters` 500 m). Sellar siete razones más significa que el backfill
  aplazará más veces; si el campo dice que sobra, lo que se toca son esos dos números, no la
  condición.

## 8. Doctrina que aplica

- *Membresía declarada, nunca deducida de cómo está escrito un string* — la regla que este mismo
  fichero (`SessionOutcome`) ya predicaba en su cabecera para sus otras tres preguntas.
- *Sistemas, no parches*: el invariante («una llegada resuelta está resuelta») se responde en el tipo
  y sus 11 casos, en vez de arreglar la comparación del servicio.
- *Un aplazamiento que no puede nombrar su causa es una decisión que ningún field test puede
  comprobar* — de ahí que la razón viaje con el sello.
