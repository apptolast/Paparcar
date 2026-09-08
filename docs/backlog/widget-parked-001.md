# WIDGET-PARKED-001 · Dónde está el coche, sin abrir la app

**Estado:** ✅ Done — mergeado a master (2026-09-08). ⏳ En mano: colocar el widget en un `/run` y
ver el ciclo aparcar→liberar en device.

Fase **A2 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)) — el item P2 del roadmap
padre del 06-09: 1 día, cero backend, y refuerza la promesa que se vende ("encuentra tu coche").

## Problema

La única forma de saber dónde quedó el coche es abrir la app y esperar al mapa. La promesa central
del producto ("sabe dónde aparcaste") merece la superficie más barata que existe en Android: un
widget en la pantalla de inicio con el nombre del coche, la calle y la hora — y un tap que abre el
mapa YA centrado en el pin.

## Doctrina violada

Ninguna. Reglas que aplican y cómo se cumplen:
- **Lógica de producto en `:shared`**: el widget de `:app` solo PINTA; qué se muestra (qué sesión
  gana con varios coches, qué calle, qué estado vacío) lo decide un resolver puro en commonMain con
  sus tests (patrón `HistoryTimeline.resolve`).
- **Strings ×9**: los del widget viven en `app/src/main/res` (como los de notificaciones), que SÍ
  desescapa `\'` [reference_compose_resources_apostrophe].
- **Copy sin mecánica interna**: el vacío dice qué hacer, no por qué ("Aparca y aquí verás dónde").
- **Detección intocada**: el widget LEE Room vía los repos; jamás dispara nada.

## Señales / datos disponibles

Verificado en master `440a9694` al abrir:
- `UserParkingRepository.observeActiveSessions(): Flow<List<UserParking>>` — dirección (enriquecida
  en background), timestamp y coordenadas ya están ahí.
- `Vehicle.displayName()` ya existe (lo usa `ObserveParkedVehiclesUseCase`) — el nombre no se
  re-deriva.
- El patrón de "abrir centrado" ya existe: `MainActivity.EXTRA_FOCUS_LAT/LON`
  (`buildFocusIntent` de las notificaciones) — el widget construye el MISMO intent.
- `androidx.glance:glance-appwidget` **1.2.0 estable** (verificado en dl.google.com; el catálogo
  solo admite estables). minSdk 26 compatible.
- `app/src/main/res` ya tiene los 9 locales (`values` + 8).

## Diseño

**Un resolver puro en shared, un pintor Glance en :app, un refresco por observación.**

- `shared/presentation/widget/ParkedWidgetState.kt` (puro, commonMain):
  `resolveParkedWidgetState(sessions, vehicles): ParkedWidgetState` —
  `Empty` | `Parked(vehicleName, street?, parkedAtMs, lat, lon, othersCount)`.
  Con varios coches aparcados gana **el más reciente** y `othersCount` cuenta el resto («+1»);
  sesiones sin vehículo resoluble se saltan (misma regla que `ObserveParkedVehiclesUseCase`);
  la calle puede ser null (enriquecimiento pendiente) → el widget pinta un genérico.
- `:app/widget/ParkedWidget.kt` (Glance) + `ParkedWidgetReceiver` + `res/xml/parked_widget_info.xml`
  (3×1, redimensionable, `updatePeriodMillis` 30 min) + `<receiver>` en el manifest.
  Contenido: nombre del coche · calle (o genérico) · **hora absoluta** de aparcado (formato del
  locale). ⚠️ Desviación deliberada del enunciado del padre ("tiempo transcurrido"): un "hace 2 h"
  congelado entre refrescos de 30 min MIENTE; la hora absoluta nunca. Tap → `MainActivity` con
  `EXTRA_FOCUS_LAT/LON` (mismo intent que las notificaciones); el vacío abre la app sin foco.
- **Refresco**: el estado solo cambia desde NUESTRO proceso (UI, servicio de detección, workers) →
  `ParkedWidgetRefresher` arranca en `PaparcarApp.onCreate` (tras Koin), observa
  `combine(sessions, vehicles) → resolve → distinctUntilChanged` y llama
  `ParkedWidget().updateAll()`. Sin widgets colocados es un no-op barato. El `updatePeriodMillis`
  cubre además el re-render tras process death.
- Koin desde el widget: `GlobalContext` (el sistema instancia el receiver; no hay constructor DI).
- Strings nuevos (×9, en `app/src/main/res`): `widget_description`, `widget_empty_title`,
  `widget_empty_hint`, `widget_location_pending`.
- Dev Catalog: **exento con motivo** — el widget no es una pantalla in-app (no hay `XxxContent`
  composable de navegación); en el flavor mock funciona igualmente contra los fakes vía Koin, que
  es como se prueba en device.

## Criterio de éxito

- Tests en commonTest del resolver: vacío, un coche (con y sin calle), varios coches (gana el más
  reciente + cuenta), sesión con vehículo borrado se salta.
- `assembleProdDebug` + `assembleMockDebug` + suite completa + Konsist + klib iOS en verde (el
  resolver es commonMain puro: compila en iOS aunque allí no haya widget).
- En device: widget colocable, pinta la sesión activa, el tap centra el mapa en el pin, y al
  liberar el coche pasa a vacío solo. (⏳ `/run` cuando toque.)

## Consumidores auditados

- `ObserveParkedVehiclesUseCase` — NO se toca ni se reusa entero: da nombre pero no dirección/hora;
  el resolver combina los MISMOS flows fuente y reusa `Vehicle.displayName()` ✔
- Notificaciones (`buildFocusIntent`) — patrón reutilizado, no duplicado: el widget construye el
  mismo intent con las MISMAS extras públicas de `MainActivity` ✔
- Flavor mock — sin binding nuevo: el widget lee los repos que `MockModule` ya sobreescribe ✔
