# ANALYTICS-EVENTS-001 · Los hechos que ya ocurren no dejan huella medible

**Estado:** ✅ Done — mergeado a master (2026-09-08). Consola cerrada el mismo día: GA verificado
habilitado por API (property `524957242`, 3 streams Android), hosting desplegado (política 08-09
viva en paparcar.com), BigQuery API habilitada, y **enlace GA→BigQuery creado por el user** (GA
Admin → BigQuery Links; la Analytics Admin API exige scope `analytics.edit` que el token del CLI
no lleva, por eso esa parte fue manual). ⏳ Verificación final: el dataset `analytics_524957242`
aparece en BigQuery con el PRIMER ciclo de export (hasta 24 h el diario) — a 08-09 aún no existía,
comprobar al día siguiente con `GET /bigquery/v2/projects/pap-26/datasets`.

Fase **A1 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)) — feature 1a del roadmap
padre ([`ROADMAP-POST-PLUS.md §5.1`](../ROADMAP-POST-PLUS.md)).

## Problema

La app confirma aparcamientos, cierra sesiones y publica plazas todos los días, y **ninguno de esos
hechos se acumula en ningún sitio medible**: no se puede responder "¿cuántos aparcamientos se
confirman por semana?" ni "¿cuántas plazas libera la comunidad?" sin leer Firestore a mano. Es lo
único de toda la ola que se acumula solo con el tiempo — cada semana sin emitir son datos perdidos.

## Doctrina violada

Ninguna de código. Dos restricciones del roadmap padre (§3.3) que este ticket cumple a propósito:
- **No un tercer sistema de telemetría paralelo**: ya existen los diagnósticos Firestore
  (opt-in, `diagnostics/{uid}`) y el `parkdiag.log`. Los eventos salen de los MISMOS choke points
  que ya estampan `detectionPath`/provenance — no de un camino nuevo que cuente el mismo viaje
  distinto.
- **La celda es ubicación**: fila en `docs/legal/DATA-SAFETY-FORM.md` en esta misma tarea.
  Nunca sale una coordenada: el invariante "de la ubicación solo viaja la celda" vive en UN sitio
  (el tracker de data, único traductor GpsPoint→param).

## Señales / datos disponibles

Verificado en master `b747fec8` al abrir:
- `dev.gitlive:firebase-analytics` **existe en 2.6.0** (repo1, mismo ref que firestore/auth) →
  API común KMP, **no hace falta el expect/actual** que el roadmap suponía (era una suposición
  pre-verificación; el patrón `CrashReporter` queda para Crashlytics).
- `data/geohash/Geohash.kt` ya existe en commonMain (lo usan los spots).
- Choke points reales:
  - **Alta**: `ConfirmParkingUseCase` — TODO aparcamiento pasa por ahí (BT, Coordinator, y el
    manual vía `SaveManualParkingUseCase` que lo envuelve). Ya recibe `detectionPath`,
    `armEvidence`, `spotType` — la provenance está en la mano.
  - **Cierre**: 4 llamadores de `clearActiveParkingSession`, cada uno un motivo distinto:
    `ProcessConfirmedDepartureUseCase` (salida confirmada), `FinalizeDeducedDepartureUseCase`
    (salida deducida), `ReleaseActiveParkingSessionUseCase` (liberación manual),
    `RevertParkingUseCase` (retractación).
  - **Plazas**: `ReportSpotReleasedUseCase` — compartido por salida automática, liberación y
    reporte manual (`ReportManualSpotUseCase` lo envuelve con `SpotType.MANUAL_REPORT`).
- El gate de diagnósticos es opt-in por Firestore; analytics es lo contrario (población entera,
  anónimo) — son sistemas con poblaciones distintas a propósito, y por eso no se fusionan.
- `docs-roadmap-post-plus-001.md` verificó que `DATA-SAFETY-FORM.md` ya declara el propósito
  *Analytics* para ubicación — falta el detalle (celda, BigQuery, retención).

## Diseño

**Un puerto en domain, una impl en data, cuatro eventos, cero decisiones tocadas.**

- `domain/analytics/AnalyticsTracker.kt` (puerto) + `AnalyticsEvent` (sealed, tipado — los nombres
  y params viven en UN fichero, no strings sueltos por los use cases):
  - `ParkingSessionStarted(location, spotType, detectionPath?, armEvidence?)`
  - `ParkingSessionEnded(location, reason)` — reason: `departure_confirmed` · `departure_deduced` ·
    `manual_release` · `reverted` (la retractación ES un hecho; se filtra en el análisis)
  - `SpotReleased(location, auto: Boolean)` / `SpotReported(location)` — el nombre lo decide
    `ReportSpotReleasedUseCase` según `SpotType`, un solo punto, sin doble conteo con el wrapper
  - El evento de dominio lleva `GpsPoint`; **la impl es la única que lo convierte** a
    `cell = geohash(7)` y jamás emite lat/lon. Geohash p7 (~150 m) porque un prefijo SIEMPRE se
    puede truncar a celda más gruesa después — elegir fino no cierra puertas.
    ⚠️ Esto **decide de facto geohash (no H3)** como sistema de celdas de la futura agregación
    (feature 3 del padre, "la celda no se puede cambiar"): coherente con el geohash que los spots
    ya usan. Anotado en el padre.
- `data/analytics/FirebaseAnalyticsTracker.kt`: GitLive `Firebase.analytics.logEvent(name, params)`,
  fire-and-forget con `runCatching` — nunca rompe el flujo que lo llama (mismo contrato no-bloqueante
  que `DetectionEventLogger`).
- Cableado con el patrón establecido de sink opcional (`detectionEventLogger` nullable en
  `ConfirmParkingUseCase`): párametro nullable con default en los 6 use cases → los dobles de test
  existentes no cambian; los tests nuevos inyectan `FakeAnalyticsTracker`.
- Koin: `single<AnalyticsTracker>` en dataModule; fake en MockModule (mock no emite nada real).
- **Sin uid en params** (Firebase adjunta su app-instance id; no lo enriquecemos), sin toggle nuevo
  de Ajustes en esta fase (la base legal es la política aceptada en el alta; el dato es anónimo
  y agregado).
- BigQuery export: se activa en la consola (pendiente del user, sin código).

## Criterio de éxito

- Cada confirmación, cierre, liberación y reporte emite exactamente UN evento con celda y sin
  coordenada — probado con `FakeAnalyticsTracker` en los tests de los 6 use cases.
- El reporte manual no cuenta doble (wrapper → un solo evento, `spot_reported`).
- Un fallo del SDK no afecta a detección ni a spots (tracker que lanza → el use case ni se entera).
- Suite completa + Konsist + `assembleProdDebug` + `assembleMockDebug` + klib iOS en verde.
- `DATA-SAFETY-FORM.md` con su fila nueva en el mismo commit.

## Lo que quedó al implementar (08-09)

- **Sin doble conteo en la promoción**: `FinalizeDeducedDeparture` re-publica el MISMO documento de
  spot al promover una salida deducida — `ReportSpotReleasedUseCase` gana el param
  `promotion: Boolean` y una promoción no emite (test lo fija con el MISMO tracker en ambos).
- **La retractación emite** `parking_session_ended(reason=reverted)` solo si la sesión se encontró
  (sin ubicación no hay nada honesto que contar).
- ⛔ **Advertising id fuera**: `play-services-measurement` fusiona `AD_ID` (GMS) +
  `ACCESS_ADSERVICES_AD_ID` + `ACCESS_ADSERVICES_ATTRIBUTION` (Android 13+). Los tres se eliminan
  con `tools:node="remove"` y está **verificado en el manifest FUSIONADO de prodDebug** (solo queda
  el `uses-library android.ext.adservices`, opcional e inocuo). Sin anuncios, el Data Safety no
  declara Advertising ID.
- **Data Safety + política de privacidad (EN/ES) actualizadas en esta tarea** (fila App
  interactions, nota AD_ID, retención 14 meses, fecha 08-09). ⏳ El hosting hay que redesplegarlo
  (`firebase deploy --only hosting`, CLI — el MCP es no-op).
- Verificado: suite completa + Konsist en verde, `assembleProdDebug` + `assembleMockDebug` +
  klib iOS (`compileKotlinIosSimulatorArm64`) compilan.

## Consumidores auditados

- `SaveManualParkingUseCase` → envuelve Confirm: hereda el evento, no emite propio ✔
- `ReportManualSpotUseCase` → envuelve Released: el nombre lo decide el interior por SpotType; test
  fija exactamente UN `spot_reported` a través del wrapper ✔
- `UpdateParkingLocationUseCase` / reposicionado → NO emite (no es un aparcamiento nuevo) ✔
- Sync/reconcile/workers → no tocan el tracker: emiten los use cases, no la persistencia ✔
- CI `apple`: ✔ sin cambios en el workflow — descarga el zip COMPLETO de firebase-ios-sdk
  (`unzip '*ios-arm64_x86_64-simulator*'` sobre todo el release) y el build nombra TODOS los
  frameworks que encuentra, así que `FirebaseAnalytics.framework` ya viaja y se linka solo.
- Dev Catalog → sin pantalla/estado nuevo; `NoOpAnalyticsTracker` en `MockModule` (el catálogo no
  emite nada real). Sin strings nuevos → paridad ×9 intacta ✔
