# DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001 · una tarjeta de aparcamiento no puede sobrevivir al viaje que la creó

**Estado:** ✅ Done · rama `bugfix/DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001-stale-card`
· worktree `../Paparcar-stale-saved-card` · suite **3.753 tests, 0 fallos**

## Problema

La tarjeta de guardado («Kamiq aparcado» + *Sí, confirmar* / *No, cancelar*, ranura 2002) puede
seguir en la bandeja mientras el usuario **ya está haciendo otro viaje**, y sus dos botones siguen
apuntando al aparcamiento VIEJO. Dos vías la dejan ahí, por razones distintas:

**(a) La ventana de 15 min del Coordinator.** `CoordinatorParkingDetector.kt:609` se salta el
`dismiss(2002)` de arranque de sesión si la tarjeta tiene menos de
`confirmationResponseTimeoutMs` = 15 min. Es deliberado (REFACTOR-300-FIX): protege la ventana de
deshacer frente a un `IN_VEHICLE_ENTER` espurio mientras el usuario camina desde el coche. Efecto
no buscado: si el arranque es un viaje REAL dentro de esos 15 min, la tarjeta convive con él.

**(b) El carril BT no la retira nunca.** `btHuntJob` / `btTrackingJob` no pasan por
`CoordinatorParkingDetector.invoke()`, que es donde vive el único dismiss de arranque. Un viaje BT
puede empezar y terminar con la tarjeta del viaje anterior intacta en la bandeja.

Con la tarjeta ahí, los dos botones se comportan distinto:

- **«Sí, confirmar»** (`ACTION_PARKING_ACK` → `CoordinatorDetectionService.kt:738`) es un no-op:
  sólo `dismiss(2002)`. El epílogo (`:1888`) guarda los cuatro slots de job, así que el viaje vivo
  no se entera. **No hay bug aquí** — queda escrito porque es la pregunta que abrió el ticket.
- **«No, cancelar»** (`ACTION_PARKING_REVERT`) lleva el `parkingId` VIEJO en el extra del
  PendingIntent y `RevertParkingUseCase` (`RevertParkingUseCase.kt:71`) **no comprueba nada**: no
  mira si esa sesión sigue activa, ni si existe. Retira un viaje que sí ocurrió.

### Alcance REAL del daño (medido en el DAO, no supuesto)

Dos de los tres efectos ya están blindados, y hay que decirlo para no arreglar lo que no está roto
(`UserParkingDao.kt:70-79`):

| Efecto del revert stale | ¿Muerde? |
|---|---|
| `endedAtMs` reescrito a la hora del tap | **No** — `COALESCE(endedAtMs, :endedAtMs)`: el primer cierre manda |
| `publishedSpot` bajado a `false` | **No** — `MAX(publishedSpot, :publishedSpot)`: nunca retracta un `true` |
| `removeGeofence(idViejo)` | **No** — idempotente, la geocerca ya no existe |
| `retractParkingSession(id)` → `retractedAtMs` | **SÍ** — el viaje real **sale del histórico** y de las cuatro lecturas que lo alimentan |
| `DetectionEvent.Reverted` con `sessionAgeMs=null`, `location=null` | **SÍ** — un viaje correcto queda fichado como falso positivo etiquetado por el usuario |

O sea: no corrompe las cifras de la sesión, pero **borra del histórico un viaje real y envenena la
telemetría de detección** — que es, según el KDoc del propio caso de uso, «la señal más valiosa que
la telemetría puede producir».

## Doctrina violada

- **Fallo asimétrico: mejor falso negativo que falso positivo.** Un tap ambiguo sobre una tarjeta
  vieja produce hoy una acción destructiva silenciosa. Ante la duda no se retira nada.
- **Sistemas, no parches.** El invariante —*una acción sobre una sesión resuelve su sujeto contra
  el repositorio, no contra el extra que traía el PendingIntent*— ya está bien resuelto en el
  gemelo de la ranura 2005 y mal en el de la 2002 (ver consumidores).
- **Un aviso responde por el viaje que lo emitió** [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]: la
  ranura única se pensó para que la frase más nueva sustituya a la anterior; el carril BT no
  participa en esa conversación.

## Señales / datos disponibles

- `userParkingRepository.observeActiveSessions()` ya se lee dentro del propio
  `RevertParkingUseCase` (línea ~76) **y su resultado se usa sólo para el log** — el dato para
  decidir ya está en la mano, simplemente no se consulta.
- El carril BT ya sabe cuándo arranca: `handleBtConnected` / `startBtTracking` /
  `handleBtDisconnected` en `CoordinatorDetectionService`.
- `effects.savedConfirmPostedAt` fecha la tarjeta; `forgetSavedConfirm()` la olvida.

## Diseño

**Un invariante, un sitio, dos consumidores barridos.**

1. **El sujeto se resuelve contra el repositorio** — dentro de `RevertParkingUseCase`, no en cada
   llamador. Si el `parkingId` no corresponde a una sesión **activa**, el revert no retira ni
   ficha: sólo `dismiss(2002)` y un evento de diagnóstico propio (`REVERT_STALE` o equivalente),
   porque un tap sobre una tarjeta caducada es información de campo, no un falso positivo.
   Vive en commonMain ⇒ **cubre iOS gratis** (`IosNotificationActionHandler.kt:69`), que es el otro
   llamador y hoy tiene exactamente el mismo agujero.
2. **El carril BT entra en la conversación de la ranura**: el arranque de un viaje BT retira la
   tarjeta 2002 con la MISMA regla de los 15 min que usa el Coordinator (no una copia suelta —
   el predicado se extrae y lo comparten los dos carriles, patrón `domain/detection/`).
   No mezcla señales: es higiene de notificación, no scoring.

⚠️ **Decisión pendiente de contraste, escrita aquí para no olvidarla**: la regla de los 15 min
protege la ventana de deshacer; aplicarla al carril BT significa que un viaje BT dentro de esos
15 min también conserva la tarjeta. Eso es coherente, pero deja el caso (a) vivo por diseño. La
protección real contra (a) es el punto 1: aunque la tarjeta siga ahí, el botón ya no puede dañar.

## Criterio de éxito — cómo quedó

- ✅ `SavedCardIsStaleTest` (4): sin tarjeta, ventana pasada, ventana abierta, y **edad negativa**
  (skew / backup restaurado) → se conserva.
- ✅ Cinco casos nuevos en `RevertParkingUseCaseTest`: no retira, no ficha `Reverted` sino
  `Decision(REVERT_STALE)`, apaga la tarjeta igual, no emite `ParkingSessionEnded`, y distingue
  `sessions_unreadable` de `not_an_active_session`. Los ocho de antes siguen verdes.
- ✅ `SavedCardSlotGuardrailTest` (Konsist) en lugar del test de carril BT que este doc pedía:
  **el arnés Robolectric del service sólo cubre el camino de intent nulo**, que es explícitamente el
  único que no toca dependencias inyectadas por Koin (lo dice su propio KDoc). Montar Koin para una
  línea de wiring habría costado más de lo que vale; se guarda leyéndola.
- ⏳ **Campo, pendiente**: viaje BT justo después de aparcar → la bandeja no debe conservar la
  tarjeta del viaje anterior. Y un tap tardío en «No, cancelar» debe dejar `REVERT_STALE` en
  `parkdiag` sin tocar el histórico.

### Falsificaciones (una prohibición sin testigo no es un chequeo)

| Falsificación | Resultado |
|---|---|
| Quitar la llamada `dismissSavedCardIfStale` de `handleBtConnected` | 🔴 guard (a) |
| Añadir un segundo lector de `savedConfirmPostedAt` en otro fichero de main | 🔴 guard (b) |
| Reintroducir `retractParkingSession` + `Reverted` en la rama stale | 🔴 3 tests del revert |
| Anular el early return con `if (false && …)` | **ni compila** — el resto del cuerpo depende de que la sesión exista, que es el testigo más fuerte de los cuatro |

## Consumidores auditados

Grep de todo lo que actúa sobre una sesión con un id traído por un PendingIntent:

| Sitio | Estado |
|---|---|
| `RevertParkingUseCase` ← `handlePostSaveRevert` (Android) | ✅ **cerrado** — el use case resuelve su sujeto |
| `RevertParkingUseCase` ← `IosNotificationActionHandler.kt:69` (iOS) | ✅ **cerrado sin tocar iOS** — el invariante vive en commonMain |
| `handleWatchdogDeparture` → `ProcessConfirmedDepartureUseCase` (ranura 2005, extra `geofenceId`) | ✅ **cubierto** — resuelve `getActiveSessionByGeofence(...)` y, si es null, no publica nada y sólo converge el estado. **Es el modelo a copiar.** |
| `handlePostSaveAck` (ranura 2002, sin extras) | ✅ **exento** — no toca estado, sólo apaga la tarjeta |
| `handleUserStop` (`:713`) | ✅ **cubierto** — ya distingue el tap stale (`detectionJob` inactivo) y deliberadamente no abre quiet period |
| `handleFollowDeparture` (extra `geofenceId`) | ✅ **cubierto** — resuelve `getSessionById(id)` y trata el null como «sin ancla», no como error |
| `handleBoardedAway` (extra `geofenceId`) | ✅ **exento, y por otra razón** — no resuelve nada porque **no escribe nada** con ese id: sólo viaja al `detail` de la traza. Verificado, no supuesto |
| (ambos) | ⚠️ Fuera de la familia en rigor: los entrega el worker / AR, no un PendingIntent de notificación, así que ninguno puede quedarse «pulsable» en la bandeja |

## Lado iOS

- Punto 1: **cubierto por construcción** (el invariante vive en commonMain; iOS es un llamador más).
- Punto 2: **no aplica** — en iOS el carril BT es estructuralmente imposible
  [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001], así que no hay arranque BT que retire nada.

## Lo que cambió respecto al diseño escrito arriba

- **El alcance del daño era menor de lo que decía el primer borrador de este doc**, y se corrigió
  tras leer `UserParkingDao.kt:70-79`: `COALESCE(endedAtMs, …)` y `MAX(publishedSpot, …)` ya impedían
  falsear las cifras de la sesión. Queda la retirada del histórico y el falso positivo fichado.
- El guard del carril BT se resolvió con Konsist en vez de con un test del service, por la razón de
  arriba. Está dicho, no escondido.

## Ficheros

| Fichero | Qué |
|---|---|
| `domain/detection/SavedCardIsStale.kt` | **nuevo** — la regla de los 15 min, pura y compartida |
| `domain/detection/CoordinatorParkingDetector.kt` | el inline sale; nace `dismissSavedCardIfStale(nowMs, lane)` |
| `detection/service/CoordinatorDetectionService.kt` | el CONNECT del carril BT pide la ranura |
| `domain/usecase/parking/RevertParkingUseCase.kt` | resuelve el sujeto; tres desenlaces |
| `fakes/FakeUserParkingRepository.kt` | `observeActiveSessionsThrows` para poder probar «no pude mirar» |
| `SavedCardIsStaleTest` · `RevertParkingUseCaseTest` · `SavedCardSlotGuardrailTest` | tests y guard |
| `docs/detection/PARKING-DETECTION.md` | entrada en §2 |
