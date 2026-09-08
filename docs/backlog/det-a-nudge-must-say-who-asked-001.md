# DET-A-NUDGE-MUST-SAY-WHO-ASKED-001 · El pin que responde a un nudge no dice qué carril preguntó

**Estado:** 🔵 Abierto, sin rama · descubierto cosechando el corpus de
[DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001](det-bt-a-ride-is-measured-not-timed-001.md)

## Problema

Todas las vías de «no puedo colocarlo, pregúntale» pasan por
`AppNotificationManager.showMarkParkingNudge(source = …)`, y el `source` **se persiste**: el adapter
lo guarda en `PendingParkNudge.source`, documentado ahí mismo como *"which detection path asked —
diagnostics only"*. Pero cuando el usuario responde, el pin se guarda por
`SaveManualParkingUseCase` con `detectionPath = "nudge"` y `armEvidence = null`: **el origen se cae
en el último paso**, justo en el artefacto que sobrevive.

Medido el 08-09-2026 sobre el corpus vivo: una cuenta con BT emparejado desde el 06-09 tiene 12
sesiones en dos días y **6 de ellas son `nudge`**. Con lo que hay en Firestore es imposible decir si
esos seis los preguntó el carril BT (gate de duración), el coordinator (`unattended_*`), o el
watchdog de nudge caducado — y por tanto es imposible responder a la pregunta que los tres tickets
de esa familia acaban haciendo: *¿cuánto estamos preguntando, y quién pregunta de más?*

Los `source` que hoy se pierden son al menos: `bt_no_ride`, `bt_no_move`, `bt_ride_unmeasurable`,
`unattended_<key>` (uno por `UnattendedSaveReason`), `stale_pending_watchdog`, y el `outcome` que
pasa `RunHonestCloseUseCase`.

## Doctrina violada

⛔ *En diagnósticos, identificar SIEMPRE qué trigger/path colocó cada pin* — y `DET-PIN-PROVENANCE-001`,
que existe precisamente para que un pin de campo no haya que trazarlo por cable. `nudge` dice CÓMO
se colocó (lo confirmó el usuario) pero no QUIÉN lo nominó, que es la mitad interesante: el pin es
ground truth del usuario, la pregunta es de quién fue la duda.

Nota: no es un caso de uso nuevo [DET-VERDICT-NOT-PREDICATE-001]. Es un campo que ya existe
viajando un paso más.

## Señales / datos disponibles

- `PendingParkNudge.source` ya está en disco (DataStore en Android, `NSUserDefaults` en iOS) cuando
  el usuario responde: nadie lo lee para el pin.
- `armEvidence` es exactamente la columna de "qué armó esto", ya espejada a Firestore.

## Diseño (a validar al abrir)

El pin de respuesta estampa `armEvidence` con el `source` del nudge pendiente. La duda real es por
dónde llega a `SaveManualParkingUseCase`: leerlo ahí dentro (nueva dependencia de preferencias) o
que lo pase quien ya lee el nudge para pintar el banner. Lo segundo parece más honesto —el use case
no tiene por qué saber que existe un banner— pero toca `HomeViewModel` y su test.

## Criterio de éxito

- Un pin nacido de un nudge dice en `armEvidence` qué carril preguntó, verificado en test.
- Sobre el corpus: los `nudge` se pueden agrupar por origen sin abrir un `parkdiag`.

## Consumidores auditados

Pendiente de abrir. Inventario inicial: `SaveManualParkingUseCase`, `HomeViewModel` (y su test),
`AppNotificationManager.showMarkParkingNudge` (los 4+ call sites que fijan `source`),
`AndroidDataStoreAppPreferences` / `IosAppPreferences` (ya persisten `source`).
