# Paparcar — Roadmap & Definición Completa del Proyecto

---

## 1. Visión General

Paparcar es una app de **compartición de plazas de aparcamiento en tiempo real** para KMP (Kotlin Multiplatform) con Android como plataforma principal e iOS como target futuro. Cuando el usuario sale con el coche, la app detecta automáticamente el evento y publica la plaza recién liberada para que otros usuarios cercanos puedan encontrarla. El core del producto es la comunidad: para encontrar plaza, debes compartir la tuya.

---

## 2. Estimación de Tiempos (Desarrollador Solo, ~30h/semana)

| Fase | Duración | Fechas Estimadas | Progreso Actual |
|------|----------|------------------|-----------------|
| **Phase 0** — Foundations | 4–5 semanas | Abr 2026 | ~85% hecho |
| **Phase 1** — Home & Mapa | 5–6 semanas | May–Jun 2026 | ~95% hecho |
| **Phase 2** — Vehículos & BT | 6–7 semanas | Jun–Jul 2026 | 0% |
| **Phase 3** — UI/UX Design System | 4–5 semanas | Jul–Ago 2026 | 0% |
| **Phase 4** — History & Settings | 3–4 semanas | Ago–Sep 2026 | ~55% hecho |
| **Phase 5** — QA & Estabilidad | 3–4 semanas | Sep–Oct 2026 | ~15% hecho |
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
| `FND-006` | Crear `expect/actual` wrappers pendientes para iOS | ⏳ Pending | Arquitectura | Media |
| `FND-007` | Ampliar tests unitarios: UseCases, Repos, ViewModels | ⏳ Pending | Testing | Alta |
| `FND-008` | Limpiar archivos del repo: logs, `.kotlin/metadata/`, `build/` | ✅ Done | Repo | Baja |
| `FND-009` | Configurar CI básico (GitHub Actions: build + tests) | ✅ Done | Infra | Media |
| `FND-010` | Documentar expect/actual contracts para iOS | ⏳ Pending | Docs | Media |
| `FND-011` | Migrar `PaparcarLogger` de `println` a Napier/Kermit | ⏳ Pending | Refactor | Media |
| `FND-012` | Extraer `MONTH_NAMES_SHORT` / `dayLabels` de HistoryScreen a strings.xml | ⏳ Pending | Refactor | Media |
| `FND-013` | Persistir toggles de Settings en `AppPreferences` (DataStore) | ⏳ Pending | Feature | Media |

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
| `HOME-013` | Implementar pantalla MyCar completa (estado vehículo, selector) | ⏳ Pending | UI | Alta |
| `HOME-014` | HistoryScreen: lista de sesiones agrupadas por día con mini-mapa | ✅ Done | UI | Media |
| `HOME-015` | Skeleton loading + animación peek handle | ✅ Done | UX | Media |
| `HOME-016` | Prevenir flash "Unknown location" durante geocoding | ✅ Done | Bug | Alta |
| `HOME-017` | `ObserveAdaptiveLocationUseCase` en `ParkingLocationViewModel` (DT-005) | ⏳ Pending | Refactor | Media |
| `HOME-018` | Mover `PARKING_ITEM_ID` a `HomeState` companion object (DT-007) | ⏳ Pending | Refactor | Baja |
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

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `VEH-001` | Modelo `Vehicle` definitivo (sin matrícula, con sizeCategory, showBrandModel) | Data | Alta |
| `VEH-002` | `VehicleRegistration` screen: brand/model/size + BT opcional | Feature | Alta |
| `VEH-003` | Selector de vehículo activo en MyCarScreen | UI | Alta |
| `VEH-004` | Room + Firestore: tabla `vehicles` (1:N con UserProfile) | Data | Alta |
| `VEH-005` | Strings `vehicle_size_*` + `vehicle_size_*_examples` en 9 idiomas | i18n | Alta |
| `VEH-006` | Multi-vehicle: confirmación de vehículo al confirmar plaza (sin BT) | UX | Media |
| `BT-001` | Bluetooth scanning: descubrir dispositivos BLE/Classic cercanos | Feature | Media |
| `BT-002` | Emparejar dispositivo BT con vehículo específico | Feature | Media |
| `BT-003` | `BluetoothConfigScreen`: scan, pair, manage | UI | Media |
| `BT-004` | `BluetoothDetectionStrategy`: trigger en BT CONNECT + GPS sampling | Feature | Alta |
| `BT-005` | Edge cases BT: ventana de parada breve, oscilación aftermarket, GPS drift check | Feature | Alta |
| `BT-006` | Permiso BT en flujo de permisos (paso opcional, muy recomendado) | UX | Media |
| `BT-007` | Acceso a BT Config desde Settings | UI | Baja |
| `PERM-001` | `PermissionsRationaleScreen`: explica el pacto social antes de pedir permisos | UX | Alta |
| `PERM-002` | Detection auto-start al aceptar todos los permisos requeridos | Feature | Alta |
| `NOTIF-001` | Notificación de confirmación siempre para AR path (multi-vehicle selector) | UX | Alta |

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

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `UI-001` | Design tokens: color, tipografía, spacing, shapes | Design | Alta |
| `UI-002` | Componentes base MD3: Button, Card, TextField, Dialog, Badge | UI | Alta |
| `UI-003` | Rama `experiment/glass-ui`: glassmorphism para BottomSheet y overlays de mapa | Experiment | Media |
| `UI-004` | Rediseño Onboarding: carrusel 3 pasos con ilustraciones | UI | Media |
| `UI-005` | `PermissionsRationaleScreen`: stepper visual, expansión por permiso | UX | Alta |
| `UI-006` | `SpotCard`: sin botones inline; acción única "Navegar" = en camino, delegada al parent | UI | Alta |
| `UI-007` | `DetectionStatusBanner` (BT / AR, con CTA de emparejamiento) | UI | Alta |
| `UI-008` | `SpotMarker` custom con estados TTL y reliability | UI | Media |
| `UI-009` | `ConfirmationBottomSheet`: countdown 4 min, timeout = publicar (no descartar) + notificación post-publicación con acción "Retirar" | UI | Alta |
| `UI-010` | `VehicleCard` con estado de detección | UI | Alta |
| `UI-011` | `TTLIndicator` y `EnRouteIndicator` | UI | Media |
| `UI-012` | `VehicleSizeSelector` con ejemplos de modelos | UI | Alta |
| `UI-013` | Dark mode como default, light mode como opción | UI | Alta |
| `UI-014` | Animaciones y transiciones entre pantallas | UI | Baja |
| `UI-015` | Añadir fuentes Syne/Jost a `composeResources/font/` | UI | Media |
| `UI-016` | `SpotReliability` chip expandible en SpotCard | UI | Media |

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
| `HIST-004` | Tests: HistoryViewModel, mappers, filtrado | ⏳ Pending | Testing | Media |
| `HIST-005` | Estadísticas básicas: tiempo medio aparcado, zonas frecuentes | ⏳ Pending | Feature | Baja |
| `HIST-006` | Fix DT-002: `MONTH_NAMES_SHORT` y `dayLabels` → strings.xml | ⏳ Pending | Refactor | Media |
| `HIST-007` | Fix DT-003: extraer `DAY_MS = 86_400_000L` a companion object | ⏳ Pending | Refactor | Baja |

### Settings

| ID | Sección | Contenido | Estado |
|----|---------|-----------|--------|
| `SET-001` | **Perfil** | Nombre, foto, email, logout, eliminar cuenta | ⏳ Pending |
| `SET-002` | **Mis Vehículos** | Lista, añadir/editar/eliminar, activo por defecto | ⏳ Pending |
| `SET-003` | **Bluetooth** | Dispositivos emparejados, re-escanear, toggle BT detection | ⏳ Pending |
| `SET-004` | **Detección** | Sensibilidad (auto/manual), radio de geofence, auto-publicar | ⏳ Pending |
| `SET-005` | **Mapa** | Tipo default, unidades distancia, radio de búsqueda | ⏳ Pending |
| `SET-006` | **Notificaciones** | Toggle por tipo: parking confirmado, plaza cerca, BT events | ⏳ Pending |
| `SET-007` | **Privacidad** | Compartir ubicación, exportar/eliminar datos (GDPR), política | ⏳ Pending |
| `SET-008` | **Sobre la App** | Versión, licencias, contacto | ⏳ Pending |
| `SET-009` | **Idioma** | Selector: Automático + 9 idiomas soportados | ⏳ Pending |

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

### Sprint actual (Phase 0 + 1 pendientes)
- `HOME-017` / `HOME-018` — deuda técnica menor en Home
- `FND-011` — migrar Logger
- `FND-012` / `FND-013` — strings y persistencia Settings

### Sprint Phase 2 — arranque
- `PERM-001` / `PERM-002` — pantalla de rationale + auto-start detección
- `VEH-001` / `VEH-004` — modelo Vehicle + BD
- `VEH-002` / `VEH-003` — registro y selector
- `VEH-005` — strings de VehicleSize en 9 idiomas

### Sprint Phase 2 — Bluetooth
- `BT-001` / `BT-002` — scanning y emparejamiento
- `BT-004` / `BT-005` — estrategia BT + edge cases
- `NOTIF-001` — notificación confirmación con multi-vehicle

### Sprint Phase 3 — Design System
- `UI-001` tokens → `UI-002` base components → componentes específicos (`UI-006` a `UI-012`)
- `UI-003` experimento glass en rama separada

### Sprint Phase 4 — Completar secundarias
- `HIST-002` / `HIST-005` — filtros y estadísticas
- `SET-001` a `SET-009` — todas las secciones de Settings

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
*Última actualización: Abril 2026 — post UX Audit Sessions 1–4*
