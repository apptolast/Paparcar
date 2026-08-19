# DET-STOP-BUTTON-001 · Botón de usuario "Parar detección" durante una detección EN CURSO

**Estado:** 🔵 En progreso · rama `feature/DET-STOP-BUTTON-001-stop-detection` · worktree `../Paparcar-stop-button`
(creada 2026-08-06, especificada y arrancada 2026-08-19)

## Problema

Cuando la detección está **realmente detectando** (job de tracking vivo: armado tras un trigger,
midiendo conducción, evaluando un aparcamiento) el usuario no tiene ninguna salida. Sus únicas
opciones hoy son:

- apagar el toggle global de Ajustes — que mata la feature entera, no esta sesión;
- esperar a que la sesión resuelva sola (4–15 min), aguantando el prompt o el pin que salga.

El caso real es cotidiano: va de pasajero en el coche de otro, sube al autobús con el AR armado, o
simplemente sabe que este trayecto no le interesa. Sin botón, la app le impone su veredicto.

## Doctrina

No viola ninguna doctrina de detección — la **completa** por el lado del fallo asimétrico:

- *Fallo asimétrico: mejor falso negativo que falso positivo.* El usuario es la fuente de verdad de
  más autoridad que existe: si dice "esto no es un viaje mío", ninguna evidencia medida debe poder
  plantar un pin. Parar es un falso negativo **pedido**.
- *El evento NOMINA, solo el movimiento MEDIDO confirma.* Intacto: parar no confirma nada; cancela.
- *Todo trigger dispara SIEMPRE.* Se respeta con un matiz explícito: el silencio posterior lo abre
  **el usuario**, no el sistema, igual que el toggle de Ajustes ya silencia todo. La supresión es
  temporal, acotada y **solo** aplica al ARMADO; ninguna otra función de los triggers se toca (ver
  §Barrido).
- *Los dos carriles no se mezclan.* El botón vive en el carril **Coordinator** (el que tiene sesión
  seguida y `DetectionUiState.Monitoring`). El carril Bluetooth es determinista y sin sesión
  seguida — queda exento, documentado abajo.

## Decisiones de diseño (respondidas por el user, 2026-08-19)

1. **Dónde vive**: en las DOS superficies — CTA secundario en la fila `DetectionStory.Driving` del
   sheet de Home, y acción en la notificación del servicio en primer plano (el caso real es el móvil
   bloqueado en el bolsillo).
2. **Semántica**: **parar en seco**. Cancela la sesión con outcome propio `stopped_by_user`, sin pin
   y sin prompt. No se ofrece "ya he aparcado, marca aquí" — ese flujo manual ya existe y mezclarlo
   convertiría un botón en dos conductas.
3. **Re-armado**: **silencio temporal** para los nominadores automáticos (ventana de
   `userStopQuietPeriodMs`, 15 min). Sin él el botón miente: el AR ENTER o el sensor re-arman en
   segundos y la detección "vuelve sola". El arranque MANUAL ("Estoy conduciendo") no se suprime
   nunca y además **cierra** el silencio: es el mismo usuario retractándose.
4. **Epílogo**: el de siempre (`resolveIdleEpilogue`) — sin caso especial. Si queda un coche
   aparcado y la auto-detección sigue ON, degrada a centinela; si no, para. Parar la sesión no es
   apagar la feature.
5. **Telemetría**: outcome propio `stopped_by_user` en `SessionEnded`, más un evento por cada armado
   suprimido, para poder distinguir en Firestore "el usuario paró" de cualquier abort del sistema.

## Señales disponibles

- `CoordinatorDetectionService` ya serializa un intake único con `ACTION_STOP_TRACKING`, pero ese es
  el **stop interno** (stand-down del centinela y [DET-MANUAL-CANCEL-001]) — no estampa outcome ni
  abre silencio. El botón necesita su propia acción.
- `DetectionSessionOutcomes` (commonMain) ya es el vocabulario compartido de outcomes terminales.
- `DetectionStory.Driving` (`HomeDetectionSurface.kt`) es hoy una `ActionRow` **sin CTA** — el hueco
  exacto, simétrico al "Estoy conduciendo" de `AwaitingFirstPark`.
- `SentryResidenceStore` es el patrón de sello persistente en `SharedPreferences("parking_safety_net")`.

## Diseño

**El invariante nuevo: mientras dura el silencio pedido por el usuario, ningún nominador AUTOMÁTICO
puede armar una sesión.** Vive en UN sitio, y ese sitio es puro:

- `domain/detection/UserStopQuietPeriod.kt` (commonMain, funciones puras — patrón
  `SentryWakeCooldown.kt`, no un caso de uso: es política compartida por 5 llamantes, no un
  veredicto con nombre propio [DET-VERDICT-NOT-PREDICATE-001]):
  - `isArmSuppressedByUserStop(trigger, stoppedAtMs, nowMs, config)` — MANUAL nunca se suprime.
  - `userStopQuietPeriodRemainingMs(...)` para la telemetría y el log.
- `DetectionSessionOutcomes.STOPPED_BY_USER = "stopped_by_user"`.
- `ParkingDetectionConfig.userStopQuietPeriodMs = 15 min`.
- **Un solo punto de aplicación**: `startParkingDetection(trigger, …)` en el servicio, por donde
  pasan los 5 armados (GEOFENCE_EXIT · AR_VEHICLE_ENTER · SIGNIFICANT_MOTION · MANUAL · sentry-wake).
  El servicio consulta la función pura y ejecuta; la decisión no vive en androidMain.
- `CoordinatorParkingDetector.onUserStoppedDetection()`: estampa el outcome, **descarta el
  `pendingConfirm` retenido** y descarta la evidencia. Sin esto el `finally` del `invoke()` remata
  un confirm retenido ("belt to the watchdog's braces") y el botón plantaría el pin que el usuario
  acaba de rechazar.
- `UserStopStore` (androidMain): sello persistente del stop, porque el proceso muere entre triggers.
- `ManualParkingDetection.stopByUser()`: método propio, NO se sobrecarga `stop()`
  ([DET-MANUAL-CANCEL-001] significa "el usuario marcó plaza a mano", otra conducta).

## Barrido de consumidores

`grep` de todo lo que arma, lo que consume triggers y lo que cancela sesiones:

| Consumidor | Veredicto |
|---|---|
| `startParkingDetection` × 5 call sites (geofence exit, AR enter ×2, sentry wake, manual) | **cerrado** — todos pasan por el gate único |
| `handleGeofenceExit` job (1): despacho de salida (`GeofenceEvent.Exited` + `DepartureDetectionWorker`) | **exento con razón** — liberar la plaza al salir es independiente de seguir el viaje; suprimirlo dejaría plazas ocupadas fantasma. El silencio solo tapa el ARMADO |
| `ACTION_STOP_TRACKING` (stand-down centinela, [DET-MANUAL-CANCEL-001]) | **exento con razón** — stop interno del sistema, no pide silencio ni estampa outcome |
| `ACTION_BT_OVERRIDE` | **exento con razón** — carril BT, arbitraje entre estrategias |
| `BluetoothDetectionService` / `BluetoothParkingDetector` | **exento con razón** — carril determinista sin sesión seguida; nunca produce `DetectionUiState.Monitoring`, luego el botón nunca se muestra sobre él |
| `ParkingSafetyNetWorker` / `EvaluateSafetyNetCheckUseCase` | **exento con razón** — reconcilia salidas ya ocurridas y nunca planta pin por sí solo; silenciarlo perdería aparcamientos reales de coches ya aparcados |
| `SignificantMotionMonitor.applyRearmCooldown` ([DET-SENTRY-COOLDOWN-001]) | **cubierto por convergencia** — otro silencio, otra causa; ambos convergen en el mismo gate de armado sin pisarse |
| `nextSentryWakeAbortStreak` (racha de aborts del centinela) | **cerrado** — `stopped_by_user` no es un abort de paseo, así que resetea la racha (rama `else`), que es lo correcto: el mundo cambió por decisión del usuario |

## Criterio de éxito

- Tocar "Parar" con una sesión viva la cierra en el mismo segundo, sin pin y sin prompt, con
  `SessionEnded(outcome = "stopped_by_user")` en Firestore.
- Tocar "Parar" con un confirm RETENIDO tampoco planta pin (test unitario del coordinator).
- Un AR ENTER / EXIT de geocerca / sensor dentro de los 15 min siguientes **no** arma, y deja
  traza del armado suprimido.
- "Estoy conduciendo" dentro de esos 15 min **sí** arma y cierra el silencio.
- Un EXIT de geocerca dentro del silencio sigue liberando la plaza.

## Progreso

- [x] Doc + worktree + rama
- [x] Dominio puro: `UserStopQuietPeriod.kt` + outcome + config
- [x] Coordinator: `onUserStoppedDetection()`
- [x] Servicio: acción, sello persistente, gate único
- [x] Puerto `stopByUser()` + impls (Android / iOS / fake)
- [x] UI Home: intent + CTA en la fila Driving
- [x] Notificación FGS: acción "Parar"
- [x] Strings en los 9 locales (2 en Compose Resources + 1 en `androidMain/res` — la notificación usa
      `R.string`, no `Res.string`)
- [x] Tests — 1247 verdes (1236 en master + 11 nuevos)
- [x] Dev Catalog / galería — sin estado nuevo: las variantes `Driving`, `Driving · BT` y `Candidate`
      ya existían en `StateGalleryScreen` y en `HomeDetectionSurfacePreviews`, y ahora pintan el CTA.
      En mock, pulsarlo recorre el bucle completo (`FakeManualParkingDetection.stopByUser()` apaga el
      runtime y la fila desaparece). No afecta a routing → sin `MockScenario` nuevo. `assembleMockDebug` ✅
- [x] `docs/detection/PARKING-DETECTION.md` (entrada al final del log de fixes)

## Validado en mock (2026-08-20, los 2 móviles)

Escenario "Conduciendo" del Dev Catalog (y también armando con "Voy conduciendo"): la fila pinta el
CTA, al pulsarlo el viaje termina, la fila vuelve a la línea de vigilancia, el chip del coche pasa de
"En ruta" a su última plaza, y sale el snackbar. El tono del pill sigue la identidad de método —
verde en el coche del Coordinator (Oppo), azul en el Toyota con BT (Redmi).

**Corrección de layout aplicada aquí**: el CTA nació inline (a la derecha) y partía en dos líneas
tanto el título como el subtítulo, porque el copy de esta fila lleva el NOMBRE del coche. Pasa a
`primaryStacksBelow = true` — ancho completo debajo, el mismo esqueleto que ya usan las filas de
vigilancia frágil/interrumpida por la misma razón [DET-WATCH-HONEST-001].

⚠️ Trampa de verificación: el Oppo (ColorOS) siguió pintando el layout viejo tras `install -r` +
`force-stop` **con el sha256 en device ya correcto**. Hizo falta un segundo `force-stop`
comprobando que no quedaba pid. Verificar el hash del APK no basta; hay que verificar que el proceso
que estás mirando es nuevo.

## Pendiente

- ⏳ **Campo**: (a) parar a mitad de viaje → sin pin, sin prompt, sesión `stopped_by_user` en
  Firestore; (b) subir al coche dentro de los 15 min → NO arranca (traza `ARM_SUPPRESSED_USER_STOP`);
  (c) "Voy conduciendo" dentro de los 15 min → SÍ arranca.
- ⏳ La acción "Parar detección" de la **notificación** no se puede probar en mock (no hay servicio
  en primer plano real) — solo con un viaje de verdad.
