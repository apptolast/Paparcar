# ANALYTICS-FUEL-HISTORY-ALERTS-COVERAGE-001 · gasolineras, historial y alertas ahora emiten

**Estado:** ✅ Done — `testAll` + `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin`
verdes antes del cierre. El hash vive en `MEMORY.md`, no aquí.

## Problema

Verificado hoy con SQL real contra `pap-26.analytics_524957242` (export GA4→BigQuery): en los
últimos 14 días hay 1.361 `det_trigger_fired`, 220 `parking_session_started`, 193 `spot_released`,
152 `parking_session_ended`, 4 `paywall_shown`, 2 `spot_reported` — y **cero** eventos de
gasolineras, historial o alertas de zona/mes. El sistema de Analytics (GA4, `ANALYTICS-EVENTS-001`)
no tiene NINGÚN choke point en esas tres áreas.

Esto es analíticas (GA4/BigQuery), agregado y anónimo — un sistema DISTINTO de los diagnósticos de
Firestore (`diagnostics/{uid}/sessions`, opt-in, sólo detección). No se mezclan.

## Doctrina violada

Ninguna por omisión — este ticket AÑADE cobertura, no corrige un invariante roto. Sí aplica la
doctrina existente de `AnalyticsTracker.kt`: fire-and-forget, sin PII, ubicación reducida a celda
geohash p7 SIEMPRE en `FirebaseAnalyticsTracker.kt`, nunca en el use case.

## Barrido de choke points (hecho ANTES de tocar código)

### 🅿️ Gasolineras

| Choke point | Fichero | Qué pasa ahí | Candidato |
|---|---|---|---|
| Notificación de movimiento de precio | `FuelPriceWatchWorker.notifyMoves()` (androidMain) | El worker diario decide que hay que avisar (carril SEGUIDAS o carril ALREDEDOR) y llama a `notificationPort.showFuelPriceMove(...)` | `FuelPriceMoveShown(lane, direction)` |
| Notificación de "más barata cerca" | `FuelPriceWatchWorker.notifyCheaper()` (androidMain) | Llama a `notificationPort.showCheaperFuelNearby(...)` | `CheaperFuelNearbyShown(reference)` |
| Seguir/dejar de seguir una gasolinera | `ToggleFollowFuelStationUseCase` (commonMain) | Único punto de escritura del follow, llamado desde `FuelStationPeek` vía `HomeSheetAction.ToggleFollowFuelStation` | `FuelStationFollowToggled(followed: Boolean)` |

`RunFuelPriceWatchUseCase`, `GetNearbyFuelStationsUseCase`, `ResolveFuelPriceUseCase`,
`EvaluateDailyPriceMoveUseCase`, `EvaluateCheaperStationNearbyUseCase` son **predicados** del
veredicto de arriba (alimentan al worker, no deciden nada por sí mismos citable en un
diagnóstico) → no llevan tracking propio. [DET-VERDICT-NOT-PREDICATE-001], aplicado por analogía.

### 📜 Historial

| Choke point | Fichero | Qué pasa ahí | Candidato |
|---|---|---|---|
| «¿Repostaste aquí?» | `AnswerRefuelStopUseCase` (commonMain) | Único punto de escritura: lo llaman Home, Vehículos (Historial) Y el detalle del historial — un solo use case, el veredicto ya existe (`DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001`) | `RefuelStopAnswered(refuelled: Boolean)` |

El resto de `VehiclesIntent` (período, métrica del gráfico, ver en mapa, grupo/coche compartido) es
navegación o UI state — no son hechos de producto nuevos, son lo que `FREE_LIMITS`/paywall ya
miden en otro punto (`OpenStatsPaywall`/`OpenPlusPaywall` → `PaywallShown`, ya cableado). No hay
export/compartir en Historial hoy — sólo lo de coche compartido, que tiene su propia doctrina
(`VEH-SHARED-001`) y no se tocó.

### 🔔 Alertas

| Choke point | Fichero | Qué pasa ahí | Candidato |
|---|---|---|---|
| Alerta de zona entregada (in-app o notificación) | `AppViewModel.kt` ~L205-230 (commonMain) | Único colector del stream de `ObserveZoneSpotAlertsUseCase`; decide canal (`IN_APP` / `SYSTEM_NOTIFICATION`) y llama a `showSpotAlertInApp` o `appNotificationManager.showZoneSpotAlert` | `ZoneSpotAlertShown(channel, location)` |
| Recap de mes | `MonthRecapWorker` (androidMain) | Llama a `notificationPort.showMonthRecap(...)` | `MonthRecapShown` (sin params, como `PaywallShown` — no es un lugar) |
| ⚠️ Push de zona cuando la app está en background | `PaparcarMessagingService.kt` | El payload lo pinta el SO (FCM), la app no decide mostrarlo — **no instrumentable desde el cliente**. Se deja sin tracking, con nota en el doc; no es un hueco, es un límite real. | — |

### Ajustes — los tres interruptores (`ToggleZoneAlertsNotif` / `ToggleFuelAlertsNotif` /
`ToggleMonthRecapNotif`, `SettingsViewModel.kt` ~L248-282)

Hoy, encender Zone/MonthRecap siendo free YA dispara `PaywallShown(reason=ZONE_ALERTS|MONTH_RECAP)`
— el intento de activar es el embudo. Lo que NO se mide es el ÉXITO del cambio (encender siendo
Plus, apagar, o el interruptor libre de gasolineras). Pendiente de decidir con el user (ver abajo).

## Lista de eventos candidatos (para validar antes de escribir código)

```kotlin
sealed class AnalyticsEvent {
    // … los 6 existentes …

    /** Una notificación de movimiento de precio salió (carril seguidas o alrededor). */
    data class FuelPriceMoveShown(val lane: FuelMoveLane, val direction: PriceMoveDirection) : AnalyticsEvent()

    /** Salió el aviso de "hay una más barata cerca". */
    data class CheaperFuelNearbyShown(val reference: CheaperReference) : AnalyticsEvent()

    /** El usuario siguió/dejó de seguir una gasolinera (gesto gratis). */
    data class FuelStationFollowToggled(val followed: Boolean) : AnalyticsEvent()

    /** Alerta de zona entregada, por el canal que decidió el momento. */
    data class ZoneSpotAlertShown(val location: GpsPoint, val channel: AlertChannel) : AnalyticsEvent()

    /** El recap del mes se mostró. Sin location — no es un lugar, como PaywallShown. */
    data object MonthRecapShown : AnalyticsEvent()

    /** Respuesta a «¿Repostaste aquí?», desde cualquiera de las 3 superficies que la hacen. */
    data class RefuelStopAnswered(val refuelled: Boolean) : AnalyticsEvent()

    /** Un interruptor de notificación de Ajustes se aplicó de verdad — nunca el intento gratis
     *  que abre el paywall, eso ya lo cuenta PaywallShown. */
    data class NotificationChannelToggled(val channel: PaywallReason, val enabled: Boolean) : AnalyticsEvent()
}
```

## ✅ Confirmado con el user (23-11)

1. Sí, medir TAMBIÉN el éxito de los tres interruptores → 7º evento
   `NotificationChannelToggled(channel: PaywallReason, enabled: Boolean)`, reutilizando el
   vocabulario cerrado de `PaywallReason` en vez de un segundo enum para los mismos tres canales.
2. Los 6 candidatos del doc son correctos, sin ajustes.

## Diseño (una vez validada la lista)

Mismo patrón que `FirebaseAnalyticsTracker.kt`: casos nuevos en el `when` de `track()`, nombres y
params SOLO ahí. Cableado `analyticsTracker: AnalyticsTracker? = null` en los use cases/worker
afectados; `NoOpAnalyticsTracker` ya cubre mock. Test unitario por cada emisor nuevo con
`FakeAnalyticsTracker`.

## Criterio de éxito

- `./gradlew testAll` verde con un test nuevo por emisor.
- `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` verdes.
- Los 7 eventos nuevos aparecen en el `when` de `FirebaseAnalyticsTracker.kt`, nada más.

⚠️ `FuelPriceWatchWorker` y `MonthRecapWorker` (androidMain) no tienen test unitario nuevo:
ninguno de los dos tenía NINGÚN test antes de esta tarea (son el I/O que decide nada — la lógica
vive en `RunFuelPriceWatchUseCase`/`RunMonthRecapUseCase`, que sí están testeados), y montar
Robolectric+WorkManager+Koin para dos líneas de `analyticsTracker.track(...)` habría ampliado el
encargo más allá de lo pedido. Si se quiere cerrar ese hueco, es un ticket propio.

## Consumidores auditados

- `FuelPriceWatchWorker`, `MonthRecapWorker` (androidMain) — únicos lugares que posan las
  notificaciones de gasolinera/recap.
- `ToggleFollowFuelStationUseCase`, `AnswerRefuelStopUseCase` — únicos puntos de escritura de sus
  gestos (grep confirmado, un solo call site de escritura cada uno aunque los lean 3 pantallas).
- `AppViewModel.kt` — único colector del stream de alertas de zona (dedupe ya vive en
  `ZoneWatchRepository`, no hay doble contaje posible).
- `PaparcarMessagingService.kt` — descartado como choke point: el SO pinta el push, no hay decisión
  de la app que instrumentar.
- `domain/analytics/AdAttribution.kt` — el `when` exhaustivo de la lista blanca hacia Meta no
  compilaba hasta decidir los 7 eventos nuevos. Los 7 son `null`: ninguno es activación ni ingreso,
  y no se pueden defender ante un anunciante. `AdAttributionEventTest.kt` (su propio `when`
  exhaustivo, el testigo) actualizado a la vez.
- `di/PresentationModule.kt` — `AppViewModel` pasó de `viewModelOf(::AppViewModel)` a un `viewModel {
  }` explícito: `KoinOptionalDependencyGuardrailTest` prohíbe un parámetro nullable
  (`analyticsTracker`) detrás de un registro por referencia a constructor, sea o no siempre
  resuelto — la regla es de FORMA, no de si hoy casualmente no rompe nada.
- `docs/backlog/README.md` — recontado (866/774/83/9): `BacklogStatusGuardrailTest` compara la
  cabecera contra el directorio real y un doc nuevo sin recontar es build rojo.
- La rama se quedó un commit por detrás de `master` a mitad de tarea (otra sesión mergeó
  `1e566d46a`+2 más) — `git merge master --ff-only` sin conflicto: 0 ficheros compartidos.
