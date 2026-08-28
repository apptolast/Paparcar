# DET-JANITOR-LANE-TELLS-ONCE-FROM-PERIODIC-001 · El registro del janitor dice qué reloj lo pidió

**Estado:** ✅ Done (28-08-2026) · mergeada a master vía squash · 1.724 tests verdes (3 nuevos) · mock compila

## Problema
`GeofenceJanitorWorker` emite `DetectionEvent.GeofenceRegistration` con `source = "janitor"` para
TODAS sus vías de entrada: el periódico de 12 h, el `enqueueOnce` de boot, el de app-start
(`PaparcarApp`) y el de post-sync (`WorkManagerParkingSyncScheduler.enqueueGeofenceRestore`).
Medido el 27-08-2026 (counts sobre el collection group `events`, todos los uids): **155 registros
`janitor` vs 99 `cure`** — la lane SIN gates (ni distancia, ni fix fresco, ni throttle propio)
registra más que la gateada, y cada registro re-abre la ventana ciega INSIDE/OUTSIDE de Play
Services. Pero con un solo label **no se puede saber si el ruido lo pone el periódico de 12 h o
los `once` de arranque** — y esa es exactamente la decisión de recorte que
DET-FENCE-REREGISTER-BY-CAUSE-001 §D dejó pendiente de datos.

## Doctrina violada
La de §D del propio DET-FENCE-REREGISTER-BY-CAUSE-001: instrumentar ANTES de tocar la política.
La instrumentación quedó a medias — mide la lane (janitor vs cure) pero no el reloj dentro de la
lane, así que la política sigue sin poder decidirse con datos.

## Señales / datos disponibles
- `GeofenceRegistration.source` ya viaja a Firestore y es agrupable (índice composite
  `events(type, source)` creado el 27-08 en pap-26).
- Los cuatro puntos de encolado son enumerables: periódico (`enqueueKeep` desde `PaparcarApp` y
  `BootCompletedReceiver`) y `enqueueOnce` desde `BootCompletedReceiver` (post-boot),
  `PaparcarApp` (app-start) y `WorkManagerParkingSyncScheduler` (post-sync).

## Diseño
El invariante: *cada registro de valla nombra el reloj que lo pidió.*
1. `enqueueOnce(workManager, reason)` recibe la razón y la mete en `inputData`
   (`KEY_TRIGGER`): `"boot"` · `"app-start"` · `"post-sync"`. El request periódico no lleva
   `inputData` → ausencia = `"periodic"` (los periódicos instalados no se re-crean con KEEP, así
   que la ausencia es el valor honesto para ellos, viejos y nuevos).
2. `doWork` compone el label: `source = "janitor:" + trigger` → `janitor:periodic`,
   `janitor:boot`, `janitor:app-start`, `janitor:post-sync`. El prefijo conserva la agrupación
   actual (`starts-with "janitor"`); no se toca el serializer ni el DTO — mismo campo `source`.
3. Sin cambio de conducta: qué se registra y cuándo queda EXACTAMENTE igual. Solo provenance.
4. Follow-up explícito (NO en este ticket): con ~1–2 semanas de datos, decidir el recorte de la
   lane que domine (bajar cadencia del periódico, o gatear los once por causa) — esa es la
   continuación real de DET-FENCE-REREGISTER-BY-CAUSE-001.

## Criterio de éxito
- Test unitario de la composición del label (pure function o test del worker con inputData).
- En remoto, tras un ciclo de campo: counts separables por
  `source IN ("janitor:periodic", "janitor:boot", "janitor:app-start", "janitor:post-sync")` con
  el índice ya creado.
- `rg '"janitor"'` no deja ningún emisor con el label plano viejo.

## Consumidores auditados (grep `REGISTRATION_SOURCE_JANITOR|"janitor"`)
- Emisor: `GeofenceJanitorWorker` — **cerrado**: único escritor; compone `janitor:<trigger>` vía
  `registrationSource` (pura, testeada).
- Call sites de `enqueueOnce` — **cerrados los 3**: `BootCompletedReceiver` (distingue
  `boot`/`app-update` por el action del intent), `PaparcarApp` (`app-start`),
  `WorkManagerParkingSyncScheduler.enqueueGeofenceRestore` (`post-sync`). El compilador garantiza
  el barrido: el parámetro `trigger` es obligatorio.
- Lectores del label en código — **ninguno**: nadie filtra `source == "janitor"` (verificado por
  grep). El KDoc de `DetectionEvent.GeofenceRegistration.source` actualizado al formato nuevo.
- Consultas remotas — **cubiertas por prefijo**: el índice composite `events(type, source)` de
  pap-26 sirve counts por trigger con igualdad exacta, y la lane completa con rango/prefijo
  `janitor`. Los docs de análisis (`08-flujo-e2e.md`) citan `source = "janitor"` como histórico —
  exentos (describen el estado de entonces).
- `FenceRegistrationLedger` y la cure del safety-net — **exentos**: no leen el label.

## Resultado
- Cambio de conducta: cero — qué se registra y cuándo, intacto. Solo provenance.
- 3 tests nuevos en `androidUnitTest` (`GeofenceJanitorRegistrationSourceTest`): periodic por
  ausencia, cada trigger, y el prefijo como contrato.
- Entrada en `docs/detection/PARKING-DETECTION.md` Sección 2 con el follow-up explícito: el
  recorte de política se decide con 1–2 semanas de datos por trigger.
- Sin strings nuevos, sin UI/mock, sin `detectionPath` nuevo.
