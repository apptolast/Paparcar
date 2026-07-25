# DET-NUDGE-PERSIST-001 — El nudge "¿Dónde has dejado el coche?" debe sobrevivir como estado de la app

**Estado:** IMPLEMENTADO en rama `feature/DET-NUDGE-PERSIST-001` (pendiente device + merge)
**Origen:** field-test noche 24→25-07-2026 (Redmi, sesión diag `1784939810210`).

## Cómo quedó implementado (2026-07-25)

- `PendingParkNudge` (domain/detection) + evaluador puro `shouldShowParkNudgeBanner`.
- Persistencia en `AppPreferences` (slot único: `observePendingParkNudge`/`set`/`clear`) — impl
  DataStore (Android), NSUserDefaults (iOS), fakes (mock + tests).
- **Invariante en el choke point**: `AppNotificationManager.showMarkParkingNudge(source, vehicleId,
  persistPending)` — el impl Android persiste ANTES de notificar. Imposible saltárselo desde
  cualquier camino futuro. El honest-close pasa `persistPending=false` (su rastro durable ES el
  pin/zona aproximado; un registro pendiente resucitaría como banner fantasma tras su release).
- Fila nueva en `HomeDetectionSurface` (amber, dos CTAs "Marcar mi plaza" + "Descartar"), con
  prioridad sobre todo salvo `BlockedCore`. Gate en `HomeSheetContent` item 0.
- Resolución: (a) todo confirm limpia el registro (`ConfirmParkingUseCase`); (b) janitor reactivo
  en `HomeViewModel.subscribeParkNudge()` limpia registro+notificación cuando una sesión activa
  del vehículo nominado reaparece (sync/carrera); (c) `HomeIntent.DismissParkNudge` →
  `ClearParkNudgeUseCase` (registro + notif 2008).
- Strings `home_nudge_*` en los 9 locales; galería Dev Catalog + previews con la variante nueva;
  tests `ParkNudgeUseCasesTest`.

## Evidencia de campo

Madrugada del 25-07 en el Redmi: el viaje real farmacia→casa cayó entero dentro del freeze de MIUI.
Al despertar (02:36:50) el coordinator procesó el EXIT real, liberó la farmacia y publicó el spot
(correcto), pero sin conducción medida ni anchor fiable (walk-entered) la sesión degradó a preguntar:

- ~02:56 — prompt de confirmación (`promptShownAt`), ignorado (usuario dormido).
- 03:11:36 — `notificationPort.showMarkParkingNudge()` (decisión `UNATTENDED_WALK_ENTERED_NUDGE`,
  outcome `aborted_unattended_walk_entered_anchor`). Verificado en device: la notificación id=2008
  (`action_channel`, "¿Dónde has dejado el coche?" + acción "Marcar mi plaza") se publicó a las
  03:11:36 y seguía viva en la bandeja 14 h después, sin responder.

Resultado: el usuario abrió la app por la mañana y Home dijo "no tienes coche aparcado" — cero
rastro de la pregunta pendiente. La sesión activa se perdió no por detección, sino porque la única
superficie del nudge es una notificación efímera/ignorable a las 3am.

## El hueco

1. `showMarkParkingNudge()` publica la notificación y NADA más: no persiste ningún "nudge pendiente".
2. `PendingDetectionStore` (DET-NEVER-SILENT-001) se limpia en todo terminal — correcto para su
   propósito (detectar muertes de proceso), pero deja los cierres-con-pregunta sin memoria.
3. Home no tiene estado "te preguntamos dónde quedó el coche y no contestaste".

## Fix propuesto (sistema, no parche)

Un único estado durable `PendingParkNudge` (Room o DataStore, commonMain) escrito por TODOS los
caminos que hoy llaman a `showMarkParkingNudge()` (los 5 outcomes `UNATTENDED_*_NUDGE` del
`CoordinatorParkingDetector`, honest-close y el watchdog del `ParkingSafetyNetWorker`):

- Campos: `createdAtMs`, `pathLabel`/outcome, última posición conocida (aprox), `vehicleId?`,
  `zoneRadiusMeters?` (reutilizar la zona aproximada de DET-HONEST-CLOSE-001 cuando exista).
- **Home**: si hay `PendingParkNudge` sin resolver y no hay sesión activa → banner/card persistente
  "No pudimos fijar dónde quedó tu coche — márcalo" con CTA que deep-linkea al mismo flujo que la
  acción "Marcar mi plaza" (reutilizar el deep-link de DET-TOGGLE-002, no duplicar).
- **Resolución**: marcar plaza (manual o vía nudge), nueva sesión confirmada, o descarte explícito
  del usuario → borra el estado. Un nudge nuevo REEMPLAZA al anterior (solo hay un coche perdido).
- Copy sin mecánica interna (causa+consecuencia+remedio) [feedback_no_internals_in_user_copy].
- Strings en los 9 locales; galería mock + escenario Dev Catalog en la MISMA tarea.

## Criterios de aceptación

- [ ] Repro del campo: sesión termina en `UNATTENDED_*_NUDGE` con app cerrada → al abrir la app,
      Home muestra el banner con la CTA de marcar plaza; sobrevive a reinicios de proceso.
- [ ] Responder el nudge (notificación O banner) limpia ambos.
- [ ] Confirmar un parking nuevo limpia el estado.
- [ ] Tests unitarios del estado + evaluador puro de visibilidad del banner.
- [ ] Dev Catalog: variante de Home con nudge pendiente.

## No-objetivos

- No tocar la lógica de decisión del coordinator (los 5 guards de nudge están bien — el campo lo
  confirma).
- No cambiar canales/importancia de la notificación (llegó y sobrevivió; el problema es la app).
