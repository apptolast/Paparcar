# Paparcar — Roadmap & Definición Completa del Proyecto

---

## 1. Visión General

Paparcar es una app de **compartición de plazas de aparcamiento en tiempo real** para KMP (Kotlin Multiplatform) con Android como plataforma principal e iOS como target futuro. Cuando el usuario sale con el coche, la app detecta automáticamente el evento y publica la plaza recién liberada para que otros usuarios cercanos puedan encontrarla. El core del producto es la comunidad: para encontrar plaza, debes compartir la tuya.

---

## 2. Estimación de Tiempos (Desarrollador Solo, ~30h/semana)

| Fase | Duración | Fechas Estimadas | Progreso Actual |
|------|----------|------------------|-----------------|
| **Phase 0** — Foundations | 4–5 semanas | Abr 2026 | ~95% hecho |
| **Phase 1** — Home & Mapa | 5–6 semanas | May–Jun 2026 | ~97% hecho |
| **Phase 2** — Vehículos & BT | 6–7 semanas | Jun–Jul 2026 | ~95% hecho |
| **Phase 3** — UI/UX Design System | 4–5 semanas | Jul–Ago 2026 | ✅ 100% |
| **Phase 4** — History & Settings | 3–4 semanas | Ago–Sep 2026 | ~70% hecho |
| **Phase 4.5** — UX Refinements (Nav + Theme + Connectivity + AddFreeSpot) | 3–4 semanas | May 2026 | 📦 ~75% (6 ramas listas, ninguna mergeada) |
| **Phase 5** — QA & Estabilidad | 3–4 semanas | Sep–Oct 2026 | ~20% hecho |
| **Phase 6** — iOS Port | 6–8 semanas | Oct–Dic 2026 | 0% |
| **TOTAL** | **~7–9 meses** | **Abr–Dic 2026** | |

> **Nota**: Las fases no son 100% secuenciales. Phase 3 (UI) puede solaparse con Phase 2 si se define el Design System antes de implementar las pantallas de vehículo. La Phase 5 (QA) debería ser continua.

---

## 3. PHASE 0 — Foundations (Estabilización)

### Objetivo
Solidificar la base antes de añadir features nuevas.

| ID | Tarea | Estado | Tipo | Prioridad |
|----|-------|--------|------|-----------|
| `FND-001` | Extraer strings hardcoded a `strings.xml` | ✅ Done | Refactor | Alta |
| `FND-002` | Eliminar magic numbers → `companion object` por clase | ✅ Done | Refactor | Alta |
| `FND-003` | Unificar manejo de errores con `PaparcarError` sealed class | ✅ Done | Arquitectura | Alta |
| `FND-004` | Fix bug geofence departure: la plaza no se publica al desaparcar | ✅ Done | Bug | Crítica |
| `FND-005` | Integrar Firebase Crashlytics (Android) | ✅ Done | Infra | Alta |
| `FND-006` | Crear `expect/actual` wrappers pendientes para iOS | ✅ Done | Arquitectura | Media |
| `FND-007` | Ampliar tests unitarios: UseCases, Repos, ViewModels | ✅ Partial (16 tests) | Testing | Alta |
| `FND-008` | Limpiar archivos del repo: logs, `.kotlin/metadata/`, `build/` | ✅ Done | Repo | Baja |
| `FND-009` | Configurar CI básico (GitHub Actions: build + tests) | ✅ Done | Infra | Media |
| `FND-010` | Documentar expect/actual contracts para iOS | ✅ Done | Docs | Media |
| `FND-011` | Migrar `PaparcarLogger` de `println` a Napier/Kermit | ✅ Done | Refactor | Media |
| `FND-012` | Extraer `MONTH_NAMES_SHORT` / `dayLabels` de HistoryScreen a strings.xml | ✅ Done | Refactor | Media |
| `FND-013` | Persistir toggles de Settings en `AppPreferences` (DataStore) | ✅ Done (SET-005) | Feature | Media |

---

## 4. PHASE 1 — Home & Mapa

### Objetivo
Completar la pantalla principal como experiencia core de la app.

| ID | Tarea | Estado | Tipo | Prioridad |
|----|-------|--------|------|-----------|
| `HOME-001` | Layout Home: Mapa + BottomSheet + Header flotante | ✅ Done | UI | Alta |
| `HOME-002` | BottomSheet con lista de spots cercanos (`HomeSpotRows`) | ✅ Done | UI | Alta |
| `HOME-003` | SpotCard en bottom sheet: modelo, estado, selección | ✅ Done | UI | Alta |
| `HOME-004` | FAB Menu: acciones manuales (reportar plaza / liberar vehículo) | ✅ Done | UI | Alta |
| `HOME-005` | Map markers: diseño custom, clusters para densidad alta | ✅ Done | UI | Media |
| `HOME-006` | Map type switcher (normal / terrain / satellite) | ✅ Done | UI | Baja |
| `HOME-007` | Pattern A: HomeScreen fullscreen sin BottomNav, nav via FloatingHeader | ✅ Done | UI/UX | Alta |
| `HOME-008` | Search bar con geocoding (`SearchAddressUseCase`) | ✅ Done | Feature | Media |
| `HOME-009` | Banner GPS accuracy / estado de permisos | ✅ Done | UX | Media |
| `HOME-010` | ~~Pull-to-refresh spots cercanos~~ | ❌ Cancelled | Feature | — |
| `HOME-011` | FAB unification: SpeedDial → right column, MapCircleFab visual language | ✅ Done | Refactor | Alta |
| `HOME-012` | FloatingHeader: acceso a MyCar (DirectionsCar icon) | ✅ Done | UI | Alta |
| `HOME-013` | Implementar pantalla MyCar completa (estado vehículo, selector) | ✅ Done | UI | Alta |
| `HOME-014` | HistoryScreen: lista de sesiones agrupadas por día con mini-mapa | ✅ Done | UI | Media |
| `HOME-015` | Skeleton loading + animación peek handle | ✅ Done | UX | Media |
| `HOME-016` | Prevenir flash "Unknown location" durante geocoding | ✅ Done | Bug | Alta |
| `HOME-017` | `ObserveAdaptiveLocationUseCase` en `ParkingLocationViewModel` (DT-005) | ✅ Done | Refactor | Media |
| `HOME-018` | Mover `PARKING_ITEM_ID` a `HomeState` companion object (DT-007) | ✅ Done | Refactor | Baja |
| `HOME-019` | Rename `MapScreen` → `ParkingLocationScreen` + eliminar dead code | ✅ Done | Refactor | Alta |

### Navegación: Pattern A — IMPLEMENTADO Y CONFIRMADO DEFINITIVAMENTE

```
HomeScreen (fullscreen, sin BottomNav) — nav via FloatingHeader ☰
  ├─ Historia    → HistoryScreen (con BottomNav)
  ├─ Mi Coche   → MyCarScreen (con BottomNav)
  └─ Ajustes    → SettingsScreen (con BottomNav)

BottomNav solo visible en: Historia | Mi Coche | Ajustes
Tab "Mapa" en BottomNav → navega de vuelta a Home (BottomNav desaparece)
```

---

## 5. PHASE 2 — Vehículos & Bluetooth

### Objetivo
Soportar múltiples vehículos por usuario, implementar detección BT y completar el FTUE con registro de vehículo.

### Modelos de datos definitivos

#### `Vehicle`

```kotlin
data class Vehicle(
    val id: String,
    val userId: String,
    val brand: String,                      // "Toyota"
    val model: String,                      // "Corolla"
    val sizeCategory: VehicleSize,          // obligatorio — determina tamaño plaza
    val color: VehicleColor? = null,        // opcional, solo visible al propietario
    val bluetoothDeviceId: String? = null,  // MAC del dispositivo BT del coche
    val bluetoothDeviceName: String? = null,
    val isActive: Boolean = false,          // vehículo en uso actualmente
    val showBrandModelOnSpot: Boolean = false, // si true, visible en Spot público
    val createdAt: Long,
)

enum class VehicleSize { MOTO, SMALL, MEDIUM, LARGE, VAN }

enum class VehicleColor {
    WHITE, BLACK, GREY, SILVER, BLUE, RED, GREEN, YELLOW, BROWN, ORANGE
}
```

> **Nota de privacidad**: `color` nunca sube a Firestore. Solo sirve para que el propietario identifique su coche en notificaciones.
> `licensePlate` eliminado del modelo — dato sensible no necesario para el funcionamiento de la app.

#### `VehicleSize` — strings con ejemplos (strings.xml, claves en todos los 9 idiomas)

```
vehicle_size_moto          → "Moto / Scooter"
vehicle_size_moto_examples → "Vespa, Honda CB500, Kawasaki Z400, BMW R1250…"

vehicle_size_small          → "Coche pequeño"
vehicle_size_small_examples → "Fiat 500, Smart ForTwo, Seat Mii, Toyota Aygo…"

vehicle_size_medium          → "Turismo mediano"
vehicle_size_medium_examples → "VW Golf, Toyota Corolla, Seat Ibiza, Ford Focus…"

vehicle_size_large          → "Coche grande / SUV"
vehicle_size_large_examples → "Toyota RAV4, BMW X3, Audi Q5, Volvo XC60…"

vehicle_size_van          → "Furgoneta"
vehicle_size_van_examples → "Mercedes Vito, Ford Transit, Renault Trafic…"
```

Los `*_examples` son idénticos en todos los idiomas (nombres propios, no se traducen). Solo se traducen los nombres de categoría.

#### `Spot` — modelo revisado

```kotlin
data class Spot(
    val id: String,
    val location: GpsPoint,
    val reportedBy: String,              // interno, NUNCA expuesto a otros usuarios
    val vehicleSize: VehicleSize,        // obligatorio
    val vehicleDisplay: String? = null,  // "Toyota Corolla" si showBrandModelOnSpot=true
    val type: SpotType,
    val confidence: Float,               // 0.0–1.0, interno
    val reliability: SpotReliability,   // UI: fusión de type + confidence
    val enRouteCount: Int = 0,
    val reportedAt: Long,
    val expiresAt: Long,                 // TTL dinámico según type
    val address: String? = null,
    val placeInfo: String? = null,
)

enum class SpotType {
    AUTO_DETECTED,   // Detectado automáticamente por la app
    AUTO_RELEASED,   // Usuario salió manualmente y dejó plaza
    MANUAL_REPORT,   // Usuario reportó plaza que vio libre
}

enum class SpotStatus {
    AVAILABLE,
    CLAIMED,         // Alguien va de camino (TTL 5 min extra)
    EXPIRED,
}

// Calculado en capa de presentación (no persiste en Firestore)
enum class SpotReliability {
    HIGH,    // AUTO_DETECTED + confidence ≥ 0.75  → verde
    MEDIUM,  // AUTO_DETECTED + confidence 0.55–0.75, o AUTO_RELEASED → amarillo
    LOW,     // AUTO_DETECTED + confidence < 0.55  → naranja
    MANUAL,  // MANUAL_REPORT  → azul / icono diferente
}
```

#### `UserParking` — distinción crítica de privacidad

```
UserParking → sesión privada del propietario
  Almacenamiento: Room (cache) + Firestore /users/{uid}/parking/active
  Visible: solo al propietario
  Contiene: vehicleId, location, geofenceId, isActive, detectionMethod

Spot → plaza libre visible a la comunidad
  Almacenamiento: Firestore /spots/{spotId}
  Visible: todos los usuarios
  NO contiene: uid real, vehicleId, color, ningún dato personal
```

### TTL dinámico por tipo de spot

| Tipo | TTL |
|------|-----|
| `AUTO_DETECTED` | 30 min |
| `AUTO_RELEASED` | 30 min |
| `MANUAL_REPORT` | 15 min |
| `CLAIMED` (cualquiera) | +5 min adicionales |

### Nomenclatura de tareas Phase 2

| ID | Tarea | Estado | Tipo | Prioridad |
|----|-------|--------|------|-----------|
| `VEH-001` | Modelo `Vehicle` definitivo (sin matrícula, con sizeCategory, showBrandModel) | ✅ Done | Data | Alta |
| `VEH-002` | `VehicleRegistration` screen: brand/model/size + BT opcional | ✅ Done | Feature | Alta |
| `VEH-003` | Selector de vehículo activo en MyCarScreen | ✅ Done | UI | Alta |
| `VEH-004` | Room + Firestore: tabla `vehicles` (1:N con UserProfile) | ✅ Done | Data | Alta |
| `VEH-005` | Strings `vehicle_size_*` + `vehicle_size_*_examples` en 9 idiomas | ✅ Done | i18n | Alta |
| `VEH-006` | Seed Room desde Firestore on first launch / device change | ✅ Done | Data | Media |
| `BT-001` | Bluetooth scanning: descubrir dispositivos BLE/Classic cercanos | ✅ Done | Feature | Media |
| `BT-002` | Emparejar dispositivo BT con vehículo específico | ✅ Done | Feature | Media |
| `BT-003` | `BluetoothConfigScreen`: scan, pair, manage | ✅ Done | UI | Media |
| `BT-004` | `BluetoothDetectionStrategy`: trigger en BT CONNECT + GPS sampling | ✅ Done | Feature | Alta |
| `BT-005` | Edge cases BT: ventana de parada breve, oscilación aftermarket, GPS drift check | ✅ Done | Feature | Alta |
| `BT-006` | Permiso BT en flujo de permisos (paso opcional, muy recomendado) | ✅ Done | UX | Media |
| `BT-007` | Acceso a BT Config desde Settings | ✅ Done | UI | Baja |
| `PERM-001` | `PermissionsRationaleScreen`: explica el pacto social antes de pedir permisos | ✅ Done | UX | Alta |
| `PERM-002` | Detection auto-start al aceptar todos los permisos requeridos | ✅ Done | Feature | Alta |
| `NOTIF-001` | Notificación de confirmación siempre para AR path (multi-vehicle selector) | ✅ Done | UX | Alta |

### Estrategia BT — lógica definitiva

**Trigger: BT CONNECT** (usuario sube al coche = posible inicio de salida de plaza)

```
BT CONNECT en coordenadas C_departure
  → Guardar C_departure + timestamp
  → Iniciar muestreo GPS (cada BT_GPS_SAMPLE_INTERVAL)

  Muestreo GPS:
  ├── distancia(pos_actual, C_departure) > BT_MIN_DISPLACEMENT_M
  │   Y tiempo > BT_MIN_TIME_BEFORE_REPORT
  │       → PUBLICAR spot en C_departure (inmediatamente, sin esperar destino)
  │
  ├── tiempo > BT_IDLE_TIMEOUT sin moverse
  │       → CANCELAR (falso positivo)
  │
  └── BT DISCONNECT (aparca en destino)
          → Si ya publicado: fin de flujo
          → Si no publicado aún: re-evaluar con GPS actual

// Si BT reconecta en < BT_RECONNECT_FAST_WINDOW_MS → ignorar ciclo (aftermarket)
```

**Parámetros en `ParkingDetectionConfig`:**
```kotlin
val btMinDisplacementMeters: Float = 150f      // confirmar salida real
val btMinTimeBeforeReportMs: Long  = 180_000L  // 3 min — evitar paradas brevísimas
val btGpsSampleIntervalMs: Long    = 60_000L   // muestreo GPS cada 1 min
val btIdleTimeoutMs: Long          = 600_000L  // 10 min sin moverse → cancelar
val btReconnectFastWindowMs: Long  = 90_000L   // oscilación aftermarket → ignorar
```

**Sin BT: siempre notificación de confirmación**

Para AR path, independientemente del confidence score, siempre se muestra notificación:
- **Confidence HIGH**: "Plaza publicada — ¿Fue un error?" (con acción de retirar)
- **Confidence MEDIUM**: "¿Has aparcado?" con acciones Sí/No
- Si hay múltiples vehículos: selector de vehículo en la notificación o bottom sheet

### FTUE definitivo

```
Splash
  → Auth (login / registro)
  → PermissionsRationaleScreen
      "Paparcar funciona así: cuando tú aparques, tu plaza aparece
       para otros. Para que funcione, necesitamos estos permisos:"
       [Ubicación] [Actividad] [Notificaciones] [Ubicación en segundo plano]
       Cada permiso con su justificación expandible
  → Permissions request (sistema Android)
  → VehicleRegistration
      (si no existe ningún vehículo en BD)
  → [Opcional] BluetoothConfig
      "Empareja tu coche para detección más precisa"
  → Home ← detección automáticamente activa

Permisos y su momento de petición:
  ✅ Bloqueante: ACCESS_FINE_LOCATION
  ✅ Requerido al inicio: ACTIVITY_RECOGNITION
  ✅ Requerido al inicio: POST_NOTIFICATIONS
  ✅ Requerido al inicio: ACCESS_BACKGROUND_LOCATION (con rationale propio)
  🔵 Opcional: BLUETOOTH_CONNECT (cuando usuario va a emparejar coche)
```

---

## 6. PHASE 3 — UI/UX Design System

### Objetivo
Implementar el sistema de diseño y los componentes UI core definidos en la Sesión 4 del UX Audit.

| ID | Tarea | Estado | Tipo | Prioridad |
|----|-------|--------|------|-----------|
| `UI-001` | Design tokens: color, tipografía, spacing, shapes | ✅ Done | Design | Alta |
| `UI-002` | Componentes base MD3: Button, Card, TextField, Dialog, Badge | ✅ Done | UI | Alta |
| `UI-003` | Glassmorphism para BottomSheet y overlays de mapa (GlassSurface component) | ✅ Done | Experiment | Media |
| `UI-004` | Rediseño Onboarding: carrusel 3 pasos con ilustraciones | ✅ Done | UI | Media |
| `UI-005` | `PermissionsRationaleScreen`: stepper visual, expansión por permiso | ✅ Done | UX | Alta |
| `UI-006` | `SpotCard`: sin botones inline; acción única "Navegar" = en camino, delegada al parent | ✅ Done | UI | Alta |
| `UI-007` | `DetectionStatusBanner` (BT / AR, con CTA de emparejamiento) | ✅ Done | UI | Alta |
| `UI-008` | `SpotMarker` custom con estados TTL y reliability | ✅ Done | UI | Media |
| `UI-009` | `ConfirmationBottomSheet`: countdown 4 min, timeout = publicar (no descartar) | ✅ Done | UI | Alta |
| `UI-010` | `VehicleCard` con estado de detección | ✅ Done | UI | Alta |
| `UI-011` | `TTLIndicator` y `EnRouteIndicator` | ✅ Done | UI | Media |
| `UI-012` | `VehicleSizeSelector` con ejemplos de modelos | ✅ Done | UI | Alta |
| `UI-013` | Dark mode como default, light mode como opción | ✅ Done | UI | Alta |
| `UI-014` | Animaciones y transiciones entre pantallas | ✅ Done | UI | Baja |
| `UI-015` | Añadir fuentes Syne/Jost a `composeResources/font/` | ✅ Done | UI | Media |
| `UI-016` | `SpotReliability` chip expandible en SpotCard | ✅ Done | UI | Media |
| `UI-017` | Canvas-drawn P icon en spot marker | ✅ Done | UI | Media |

### Dirección visual definitiva

**Modo:** Dark como default, light disponible en Settings.
**Paleta primaria:** Emerald green `#10B981` (ya en el tema actual).
**Tipografía:** System font en Phase 1-2; migrar a Syne/Jost en Phase 3 (`UI-015`).

---

## 7. PHASE 4 — History & Settings

### History

| ID | Tarea | Estado | Tipo | Prioridad |
|----|-------|--------|------|-----------|
| `HIST-001` | UI base: lista agrupada por día con mini-mapa en cada sesión | ✅ Done | UI | Alta |
| `HIST-002` | Filtros: por vehículo, rango de fechas | ⏳ Pending | Feature | Baja |
| `HIST-003` | `ParkingLocationScreen`: mapa de detalle desde sesión | ✅ Done | UI | Media |
| `HIST-004` | Tests: HistoryViewModel, mappers, filtrado | ✅ Done — branch `feat/HIST-004-history-tests` |
| `HIST-005` | Estadísticas básicas: tiempo medio aparcado, zonas frecuentes | ⏳ Pending | Feature | Baja |
| `HIST-006` | Fix DT-002: `MONTH_NAMES_SHORT` y `dayLabels` → strings.xml | ✅ Done | Refactor | Media |
| `HIST-007` | Fix DT-003: extraer `DAY_MS = 86_400_000L` a companion object | ✅ Done | Refactor | Baja |

### Settings

| ID | Sección | Contenido | Estado |
|----|---------|-----------|--------|
| `SET-001` | **Perfil** | Nombre, foto, email, logout, eliminar cuenta | ✅ Done |
| `SET-002` | **Mis Vehículos** | Lista, añadir/editar/eliminar, activo por defecto | ✅ Partial (navega a MyCar, falta edit/delete inline) |
| `SET-003` | **Bluetooth** | Dispositivos emparejados, re-escanear, toggle BT detection | ✅ Partial (navega a BT config) |
| `SET-004` | **Detección** | Sensibilidad (auto/manual), radio de geofence, auto-publicar | ✅ Partial (toggle autoDetect, falta sensibilidad/radio) |
| `SET-005` | **Mapa** | Tipo default, unidades distancia, radio de búsqueda | ✅ Done |
| `SET-006` | **Notificaciones** | Toggle por tipo: parking confirmado, plaza cerca, BT events | ✅ Done |
| `SET-007` | **Privacidad** | Compartir ubicación, exportar/eliminar datos (GDPR), política | ✅ Partial (OpenUrl OK, falta GDPR export/delete) |
| `SET-008` | **Sobre la App** | Versión, licencias, contacto | ✅ Done |
| `SET-009` | **Idioma** | Selector: Automático + 9 idiomas soportados | ✅ Done — branch `feat/SET-009-language-selector` |

---

## 7.5. PHASE 4.5 — UX Refinements (Nav + Theme + Connectivity + AddFreeSpot)

### Objetivo
Refinar la experiencia core post-MVP: simplificar navegación inferior, soportar
modo claro/oscuro/sistema completo, dedicar pantalla específica al reporte
manual de plaza libre, y comunicar estados de red al usuario.

> **Nota:** Las tareas de tipo "Refactor / Arquitectura" derivadas de estas
> features (extracción de `PaparcarMapView`, `PaparcarBottomActionBar`,
> `ConnectivityObserver`, NavGraph updates, DataStore migration) se rastrean
> en `Paparcar_Roadmap_TechDebt.md` bajo la fase **QA-6**.

### Estado de ramas — auditado 2026-04-27

Sprint autónomo entregado en 6 ramas independientes. **Ninguna está mergeada
en `master`** — pendiente de revisión y merge por el desarrollador.

| Rama | Commit | Grupos cubiertos | Build | Estado |
|------|--------|------------------|-------|--------|
| `refactor/D-001-paparcar-map-view` | `23e47b4` | D-001 | ✅ verde | 📦 Listo |
| `refactor/D-002-paparcar-bottom-action-bar` | `fb86fb1` | D-002 | ✅ verde | 📦 Listo |
| `feat/E-connectivity-observer` | `54a5dbc` | NET-001..006 + NET-ARCH-001 | ✅ verde | 📦 Listo |
| `feat/C-add-free-spot` | `c206eba` *(incluye D-002)* | AFS-001..008 + AFS-ARCH-001 | ✅ verde | 📦 Listo |
| `feat/A-nav-tabs-vehicles` | `17658e4` | NAV-001, 002 (parcial), 005, 007, 008 | ✅ verde | 📦 Listo (alcance reducido) |
| `feat/B-theme-mode-tri-state` | `51a9bf5` | THEME-001..004 | ✅ verde | 📦 Listo |

**Orden de merge sugerido** (para minimizar conflictos en `App.kt` / `HomeScreen.kt`):
`D-001` → `D-002` → `feat/E` → `feat/C` (resolver D-002 ya presente) →
`feat/A` → `feat/B`.

**Tareas explícitamente diferidas dentro de Phase 4.5:**
- NAV-003 (`VehiclesViewModel` unificado) — re-planificar tras merge de A
- NAV-004 (lógica de vehículo activo BT) — re-planificar tras merge de A
- NAV-006 (Historial como pestaña por vehículo) — re-planificar tras merge de A
- THEME-005..007 (mapa estilo por tema + auditoría hex) — emparejado con TechDebt `THEME-ARCH-002`
- THEME-008 (strings) — entregadas con naming `settings_theme_mode_*` (no `settings_theme_*`); aceptable funcionalmente

Las leyendas que verás abajo:
- ✅ **Done [`branch`@`commit`]** — entregado en una rama, build verde, **aún no mergeado**
- ⚠️ **Partial** — alcance entregado < acceptance criteria del grupo; ver nota
- ⏳ **Deferred** — aplazado a iteración posterior por el desarrollador

---

### A — Bottom Navigation Restructure (merge MyCar + History)

**Objetivo:** unificar `Mi Coche` + `Historial` en una única pantalla
"Vehículos" donde el usuario ve la lista de vehículos y, al tocar uno, accede
al historial de aparcamientos de ese vehículo en pestañas. Reducir el BottomNav
de 4 a 3 destinos lógicos.

| ID | Tarea | Estado | Tipo | Prioridad | Tamaño |
|----|-------|--------|------|-----------|--------|
| `NAV-001` | Definir orden definitivo de BottomNav (3 tabs: Mapa \| Vehículos \| Ajustes) | ✅ Done [`feat/A-nav-tabs-vehicles`@`17658e4`] | UX | Alta | [SHORT] |
| `NAV-002` | Crear `VehiclesScreen` (lista vehículos + detalle con pestañas Historial/Detección/BT) | ✅ Done — `VehicleDetailScreen` con tabs Details/History [`feat/NAV-005-006-vehicle-detail-history-tab`@`07c912f`] | UI | Alta | [LARGE] |
| `NAV-003` | `VehiclesViewModel` unifica `VehicleRepository` + `UserParkingRepository` agrupando sesiones por `vehicleId` | ✅ Done — `combine()` ambos repos, `VehicleWithStats` model, Room migration 9→10 [`feat/NAV-ARCH-002-vehicles-viewmodel-unify`@`bf19e41`] | Feature | Alta | [MEDIUM] |
| `NAV-004` | Lógica de "vehículo activo": prioridad BT-conectado, fallback a `isDefault = true` | ✅ Done — `VehiclesState.activeVehicle` derived property [`feat/NAV-ARCH-002-vehicles-viewmodel-unify`@`d5107b8`] | Feature | Alta | [MEDIUM] |
| `NAV-005` | Migrar funcionalidad de `MyCarScreen` a la pestaña "Detalles" del nuevo flujo | ✅ Done — `VehicleDetailsTab` con `VehicleCard` expandido [`feat/NAV-005-006-vehicle-detail-history-tab`@`07c912f`] | Refactor | Alta | [MEDIUM] |
| `NAV-006` | Migrar `HistoryScreen` (timeline + WeeklyActivityCard + StatsRow) a pestaña por vehículo | ✅ Done — `HistoryViewModel` acepta `vehicleId?`, filtra con `observeSessionsByVehicle()` [`feat/NAV-005-006-vehicle-detail-history-tab`@`07c912f`] | Refactor | Alta | [MEDIUM] |
| `NAV-007` | Eliminar tabs `MY_CAR` y `HISTORY` del NavGraph; añadir route `vehicles` | ✅ Done — `MY_CAR` eliminado de tabs, `HISTORY` conservado como ruta no-tab para deep-link (NAV-008) [`feat/A-nav-tabs-vehicles`@`17658e4`] | Refactor | Alta | [SHORT] |
| `NAV-008` | Conservar deep-link a `ParkingLocationScreen` desde el historial por vehículo | ✅ Done [`feat/A-nav-tabs-vehicles`@`17658e4`] | Refactor | Media | [SHORT] |

**Criterios de aceptación:**
- BottomNav muestra 3 destinos en el orden acordado.
- Tocar un vehículo en la lista abre su detalle con tabs (Historial / Detección / BT).
- El historial filtrado por vehículo respeta el mismo timeline + WeeklyActivityCard que ya existe.
- Si hay un vehículo conectado por BT, la UI lo marca como "activo" aunque otro tenga `isDefault = true`.
- No se rompe el deep-link "Ver en mapa" desde una sesión histórica.

**Dependencias:** ninguna externa. Conviene hacer NAV-002 después de
`PaparcarBottomActionBar` (D-002) si las tabs van a usarlo.

---

### B — Theme & Dark Mode (Light / Dark / System)

**Objetivo:** dar al usuario control completo sobre la apariencia. Hoy
`darkModeEnabled` es un Boolean — debe pasar a un enum `ThemeMode` con
`LIGHT`, `DARK`, `SYSTEM` y propagarse al MaterialTheme y al estilo de Google
Maps (light/dark JSON).

| ID | Tarea | Estado | Tipo | Prioridad | Tamaño |
|----|-------|--------|------|-----------|--------|
| `THEME-001` | Definir enum `ThemeMode { LIGHT, DARK, SYSTEM }` en `domain/preferences` | ✅ Done [`feat/B-theme-mode-tri-state`@`51a9bf5`] | Data | Alta | [SHORT] |
| `THEME-002` | Reemplazar `appPreferences.darkModeEnabled: Boolean` por `themeMode: ThemeMode` (migración compatible) | ✅ Done — migración lazy del key legacy a enum en Android+iOS [`feat/B-theme-mode-tri-state`@`51a9bf5`] | Refactor | Alta | [SHORT] |
| `THEME-003` | `AppState.darkTheme: Boolean` se calcula a partir de `ThemeMode` + `isSystemInDarkTheme()` | ✅ Done — `darkTheme` se computa en App() composable, `AppState.darkTheme` eliminado del state [`feat/B-theme-mode-tri-state`@`51a9bf5`] | Feature | Alta | [SHORT] |
| `THEME-004` | UI Settings: reemplazar Switch por segmented control de 3 opciones (Claro / Oscuro / Sistema) con strings i18n | ✅ Done — `SettingsSegmentedItem<ThemeMode>` reutilizado [`feat/B-theme-mode-tri-state`@`51a9bf5`] | UI | Alta | [SHORT] |
| `THEME-005` | `PaparcarMapView` recibe `MapStyleMode` resuelto por el tema activo y aplica `LIGHT_MAP_STYLE` o `DARK_MAP_STYLE` | ✅ Done — `MapStyleMode.AUTO` usa `background.luminance()` del tema [`feat/THEME-005-006-map-style-by-theme`@`75c0a45`] | Feature | Alta | [MEDIUM] |
| `THEME-006` | Añadir JSON de `LIGHT_MAP_STYLE` (paleta neutra) en `PaparcarMapView` | ✅ Done — `MapStyles.kt` con `LIGHT_MAP_STYLE` + `DARK_MAP_STYLE` extraídos [`feat/THEME-005-006-map-style-by-theme`@`75c0a45`] | Asset | Media | [SHORT] |
| `THEME-007` | Auditar Composables que ignoran `MaterialTheme` (hex colors hardcoded) y migrarlos | ✅ Done — `HomeGpsAccuracyBanner` migrado a `error`/`secondary` tokens [`feat/THEME-005-006-map-style-by-theme`@`4910b89`] | Refactor | Media | [MEDIUM] |
| `THEME-008` | Strings `settings_theme_light`, `settings_theme_dark`, `settings_theme_system` en EN + ES | ✅ Done — entregado con prefijo `settings_theme_mode_*` (light/dark/system + label/desc), translaciones en 9 locales [`feat/B-theme-mode-tri-state`@`51a9bf5`] | i18n | Alta | [SHORT] |

**Criterios de aceptación:**
- Selector "Claro / Oscuro / Sistema" en Settings persiste entre relanzamientos.
- Modo "Sistema" cambia automáticamente cuando el usuario alterna el tema del SO sin reiniciar la app.
- Google Maps adopta estilo claro u oscuro según el tema activo.
- Ningún Composable conserva colores hex hardcoded que rompan el contraste en modo claro.

**Dependencias:** afecta a `PaparcarMapView` (D-001). THEME-005/006 deben
ejecutarse después de la extracción del mapa o coordinarse con ella.

---

### C — AddFreeSpot Flow (nueva pantalla + refactor del FAB)

**Objetivo:** sacar la responsabilidad de "reportar plaza libre" del HomeScreen
a una pantalla dedicada con UI mínima (mapa + pin animado + barra de acción).
Eliminar el FAB de logout/release del Home (su acción ya vive en el peek row).

| ID | Tarea | Estado | Tipo | Prioridad | Tamaño |
|----|-------|--------|------|-----------|--------|
| `AFS-001` | Eliminar el "logout/release" FAB de `HomeActionFab.kt` (variante `parked`) | ✅ Done [`feat/C-add-free-spot`@`c206eba`] | Refactor | Alta | [SHORT] |
| `AFS-002` | El megáfono FAB del Home ahora navega a `Routes.ADD_FREE_SPOT` en lugar de invocar `ReportManualSpot` directamente | ✅ Done [`feat/C-add-free-spot`@`c206eba`] | Refactor | Alta | [SHORT] |
| `AFS-003` | Crear `AddFreeSpotScreen` (mapa fullscreen + pin centrado animado + `PaparcarBottomActionBar`) | ✅ Done [`feat/C-add-free-spot`@`c206eba`] | UI | Alta | [MEDIUM] |
| `AFS-004` | `AddFreeSpotViewModel` + State/Intent/Effect aislados (centro de mapa + estado de envío + acción publicar) | ✅ Done [`feat/C-add-free-spot`@`c206eba`] | Feature | Alta | [MEDIUM] |
| `AFS-005` | Animación de "pin drop" del marcador central (drop+rebote al asentar la cámara) | ✅ Done — animación `crosshairScale` con `spring` integrada en `PlatformMap` (modo `reportMode=true`) [`feat/C-add-free-spot`@`c206eba`] | UX | Alta | [SHORT] |
| `AFS-006` | Añadir route `add_free_spot` al NavGraph con `popBackStack` al publicar éxito | ✅ Done [`feat/C-add-free-spot`@`c206eba`] | Refactor | Alta | [SHORT] |
| `AFS-007` | Strings `add_free_spot_title`, `add_free_spot_publish`, `add_free_spot_success` en EN + ES | ✅ Done — entregado con `add_free_spot_title` + `add_free_spot_action` (en lugar de `_publish`); el "success" reusa `home_manual_spot_reported` [`feat/C-add-free-spot`@`c206eba`] | i18n | Alta | [SHORT] |
| `AFS-008` | `HomeViewModel` deja de manejar `ReportManualSpot`; mover lógica de publicación a `AddFreeSpotViewModel` | ✅ Done — `HomeIntent.ReportManualSpot` eliminado; lógica vive en `AddFreeSpotViewModel` [`feat/C-add-free-spot`@`c206eba`] | Refactor | Alta | [MEDIUM] |

**Criterios de aceptación:**
- HomeScreen ya no contiene el FAB de logout (release sigue accesible desde la peek row del bottom sheet).
- El megáfono del Home abre AddFreeSpot sin animación abrupta (transición consistente con el resto de la app).
- El pin central anima un "drop" al asentarse la cámara y reposiciona en cada `onCameraMove` con suavidad.
- Tocar "Publicar" envía el spot al `cameraTarget` actual; al éxito hace `popBackStack` y muestra snackbar.
- AddFreeSpot no muestra BottomNav, ni FABs auxiliares, ni overlays de parking.

**Dependencias:** depende de `PaparcarMapView` (D-001) y `PaparcarBottomActionBar` (D-002).

---

### E — Connectivity Listener & No Internet UX

**Objetivo:** detectar cambios de conectividad en tiempo real e informar al
usuario sin destruir el estado de la pantalla.

#### Decisión recomendada — Opción 2: Banner persistente / Snackbar overlay

**Justificación:**
1. **Paparcar tiene funcionalidad parcial offline.** El mapa con tiles cacheados
   sigue siendo visible, Room cachea spots y sesiones, y la lógica de detección
   BT/AR funciona sin red (sólo el publish a Firestore se difiere). Forzar al
   usuario a una pantalla bloqueante (Opción 1) destruiría contexto útil.
2. **No-destructivo del back stack.** Volver al estado previo en la reconexión
   es trivial cuando no hay navegación que restaurar.
3. **Implementable a nivel de root scaffold.** `PaparcarApp` ya tiene un
   `Surface` raíz; el banner se ancla bajo el TopAppBar (o sobre el BottomNav)
   sin tocar pantallas individuales.
4. **Las acciones que requieren red ya tienen feedback granular** (Snackbar de
   error desde efectos ViewModel). El banner ofrece el contexto global; el
   feedback puntual sigue por pantalla.

> Las opciones 1 (full-screen) y 3 (overlay semi-transparente) quedan
> descartadas: la primera por destructiva, la segunda por sobre-bloquear UX
> que sí debería seguir siendo navegable (ver mapa cacheado, abrir Settings).

| ID | Tarea | Estado | Tipo | Prioridad | Tamaño |
|----|-------|--------|------|-----------|--------|
| `NET-001` | Definir tipo `ConnectivityStatus { Online, Offline }` en domain | ✅ Done [`feat/E-connectivity-observer`@`54a5dbc`] | Data | Alta | [SHORT] |
| `NET-002` | Banner persistente "Sin conexión" anclado al root scaffold de `PaparcarApp` (visible mientras `Offline`) | ✅ Done — `ConnectivityBanner` ancla al root [`feat/E-connectivity-observer`@`54a5dbc`] | UI | Alta | [SHORT] |
| `NET-003` | Snackbar transitorio "Conexión restablecida" al pasar `Offline → Online` (auto-dismiss 2s) | ✅ Done [`feat/E-connectivity-observer`@`54a5dbc`] | UX | Media | [SHORT] |
| `NET-004` | En reconexión: re-emitir `LoadNearbySpots` desde `HomeViewModel` y `observeAllSessions` desde repos | ✅ Done — `reconnectTick` en `HomeViewModel` re-dispara la carga al pasar Offline→Online [`feat/E-connectivity-observer`@`54a5dbc`] | Feature | Alta | [MEDIUM] |
| `NET-005` | Strings `connectivity_offline_banner`, `connectivity_restored_snackbar` en EN + ES | ✅ Done [`feat/E-connectivity-observer`@`54a5dbc`] | i18n | Alta | [SHORT] |
| `NET-006` | Acciones que requieren red (publish spot, set active vehicle) muestran Snackbar de bloqueo si offline | ✅ Done — string `connectivity_action_blocked_offline` + chequeo en `HomeViewModel` [`feat/E-connectivity-observer`@`54a5dbc`] | UX | Media | [SHORT] |

**Criterios de aceptación:**
- Activar modo avión muestra el banner en menos de 2 segundos.
- Desactivar modo avión hace desaparecer el banner y muestra un Snackbar breve.
- El back-stack y el state de cada ViewModel se preservan durante el evento.
- Spots cacheados en Room siguen visibles mientras está offline.
- Al reconectar, los spots se refrescan automáticamente (sin acción del usuario).

**Dependencias:** ninguna externa. La arquitectura concreta del observer
(expect/actual, lifecycle, inyección Koin) está en TechDebt.md (`NET-ARCH-*`).

---

## 8. PHASE 5 — QA & Estabilidad

| ID | Tarea | Tipo |
|----|-------|------|
| `QA-001` | Tests integración: flujo completo detect → confirm → publish | Testing |
| `QA-002` | Tests de UI: Compose Preview Tests pantallas principales | Testing |
| `QA-003` | Crashlytics: alertas, análisis crashes top | Monitoring |
| `QA-004` | Edge cases: sin GPS, modo avión, batería baja, kill de proceso | QA |
| `QA-005` | Performance: profiling batería del foreground service | QA |
| `QA-006` | Accessibility: content descriptions, contraste, tamaños fuente | UX |
| `QA-007` | Beta testing interno: Firebase App Distribution | Release |
| `QA-008` | Disclaimer GPS accuracy: diálogo inicial + banner | UX |

---

## 9. PHASE 6 — iOS Port

| Componente Android | Equivalente iOS | Complejidad |
|--------------------|----------------|-------------|
| `SpotDetectionForegroundService` | Background Task + CLLocationManager | Alta |
| `ActivityTransitionReceiver` | CMMotionActivityManager | Media |
| `FusedLocationProvider` | CLLocationManager | Media |
| `WorkManager` | BGTaskScheduler | Alta |
| `GeofencingClient` | CLCircularRegion + CLLocationManager | Media |
| `BluetoothAdapter / BLE` | CoreBluetooth (CBCentralManager) | Alta |
| `NotificationManager` | UNUserNotificationCenter | Media |

---

## 10. Decisiones de Diseño Confirmadas

### Privacidad — reglas permanentes

1. `Spot.reportedBy` = interno, nunca expuesto a otros usuarios
2. `Vehicle.color` = solo en Room local, nunca a Firestore
3. `Vehicle.licensePlate` = eliminado del modelo (dato sensible innecesario)
4. `UserParking` = colección privada `/users/{uid}/parking/`, nunca pública
5. Lo que ven otros usuarios de una plaza: ubicación, `vehicleSize`, `vehicleDisplay?` (opt-in), tipo, TTL, enRouteCount
6. Comunicación al usuario: transparencia en cada punto de contacto (permisos, registro, spot publicado)

### Navegación — Pattern D, definitivo

- HomeScreen fullscreen sin BottomNav
- BottomNav solo en History / MyCar / Settings
- FloatingHeader (☰) en HomeScreen da acceso a todo
- Sin botón de activación de detección — se activa con los permisos

### Detección — principios permanentes

- Detection auto-start: activar permisos = detección encendida permanentemente
- BT path: trigger BT CONNECT + GPS drift check (no esperar destino)
- AR path: siempre notificación de confirmación (usuario puede estar en taxi/autobús)
- Estrategias BT y AR son **independientes y nunca se mezclan** en el scoring
- Multi-vehículo sin BT: usar `isActive = true`; preguntar en notificación si hay varios

### UX — principios de glanceability

- Info crítica (distancia, TTL) legible en un golpe de vista a 40cm
- Touch targets mínimo 48dp
- Codificación de color consistente: verde=disponible, ámbar=reclamado/bajo TTL, rojo=urgente
- Dark mode por defecto (uso nocturno y en garajes)

---

## 11. Nomenclatura Global

### Ramas
```
feature/HOME-013-mycar-screen
feature/VEH-002-vehicle-registration
feature/BT-004-bt-detection-strategy
feature/PERM-001-permissions-rationale
bugfix/HIST-006-month-names-hardcoded
refactor/FND-011-logger-napier
experiment/UI-003-glass-ui
```

### Commits
```
feat(vehicle): add VehicleSize model with string examples [VEH-001]
feat(detection): implement BT connect trigger with GPS drift check [BT-004]
feat(permissions): add rationale screen before permission request [PERM-001]
fix(history): extract MONTH_NAMES_SHORT to strings.xml [HIST-006]
feat(settings): add language selector [SET-009]
feat(ui): implement SpotCard with TTL and reliability indicators [UI-006]
```

---

## 12. Orden de Ejecución Recomendado

### Sprint actual — Phase 4.5 (UX Refinements) — actualizado 2026-04-27

**Orden recomendado** (justificación: dependencias y entrega incremental):

**Sprint 1 — Base reutilizable:** ✅ Code-complete (ramas listas, sin merge)
- `D-001` (TechDebt) — extraer `PaparcarMapView` reutilizable → `refactor/D-001-paparcar-map-view`
- `D-002` (TechDebt) — extraer `PaparcarBottomActionBar` → `refactor/D-002-paparcar-bottom-action-bar`
- `THEME-001..003` — sustituir `darkModeEnabled: Boolean` por `ThemeMode` enum → `feat/B-theme-mode-tri-state`

**Sprint 2 — Conectividad y AddFreeSpot:** ✅ Code-complete (ramas listas, sin merge)
- `NET-001..006` — Connectivity observer + banner + reconexión → `feat/E-connectivity-observer`
- `AFS-001..008` — Nueva pantalla AddFreeSpot + retiro del FAB de logout → `feat/C-add-free-spot`

**Sprint 3 — Restructure y polish de tema:** ⚠️ Parcial
- `NAV-001..008` — Merge Mi Coche + Historial → Vehículos → `feat/A-nav-tabs-vehicles`
  - Entregado: NAV-001, 002 (parcial), 005 (parcial), 007, 008
  - Diferido: NAV-003 (VM unificado), NAV-004 (BT-active), NAV-006 (Historial-tab)
- `THEME-004..008` — UI selector 3-way + estilo de mapa por tema → `feat/B-theme-mode-tri-state`
  - Entregado: THEME-004, 008 (con naming alternativo)
  - Diferido: THEME-005, 006 (Map style — emparejado con `THEME-ARCH-002`), THEME-007 (audit hex)

**Próxima acción del desarrollador:**
1. Revisar y mergear las 6 ramas en el orden listado en "Estado de ramas — auditado 2026-04-27".
2. Decidir prioridad para los grupos diferidos (NAV-003/004/006, THEME-005/006/007) o cerrar Phase 4.5 sin ellos y replanificar como follow-up.

### Sprint paralelo — Pendientes pre-Phase-4.5 (no bloqueantes)

**Prioridad 1 — Funcionalidad:**
- `FND-011` — migrar `PaparcarLogger` a Napier/Kermit
- `SET-007` — implementar `OpenUrl` + GDPR (exportar/eliminar datos)
- `SET-009` — selector de idioma
- `HOME-013` — completar MyCarScreen (delete/edit vehicle) — *NOTA: parte de esto se reabsorbe en NAV-005*

**Prioridad 2 — Pulido:**
- `HOME-017` — `ObserveAdaptiveLocationUseCase` en `ParkingLocationViewModel`
- `HOME-018` — `PARKING_ITEM_ID` a companion object
- `HIST-002` — filtros historial (por vehículo, fecha) — *NOTA: el filtro por vehículo se cumple gratis con NAV-006*
- `HIST-005` — estadísticas básicas
- `HIST-007` — extraer `DAY_MS` constant
- `SET-001` — añadir eliminar cuenta
- `SET-004` — sensibilidad detección y radio geofence
- `SET-008` — licencias y contacto

**Prioridad 3 — Tests:**
- `TEST-002` — ParkingDetectionCoordinator (3 paths)
- `TEST-003` — SpotRepositoryImpl (offline-first)
- ViewModel tests (Home, History, MyCar, Settings)

### Fases completadas (no planificar)
- ~~Phase 2 — Vehículos & BT~~ ✅ (16/16 tareas)
- ~~Phase 3 — Design System~~ ✅ (17/17 tareas)
- ~~Tech Debt QA-1/QA-4/QA-5~~ ✅ (todo completado)

---

## 13. Métricas de Progreso

| Métrica | Objetivo |
|---------|----------|
| Cobertura tests | >60% en domain/, >40% en data/ |
| Crash-free rate | >99% (Crashlytics) |
| Strings externalizados | 100% |
| Magic numbers eliminados | 100% |
| Flujo detect → publish | Funcional sin bugs |
| FTUE completion rate | >70% (permiso → vehículo → home) |
| Tiempo medio detección parking | <3 minutos |

---

*Documento vivo — actualizar conforme avance el desarrollo.*
*Última actualización: 27 Abril 2026 — auditadas las 6 ramas del sprint Phase 4.5; ninguna mergeada todavía. Estados de tareas reflejan el contenido real de cada rama, no asumen merge.*
