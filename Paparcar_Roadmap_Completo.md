# Paparcar — Roadmap & Definición Completa del Proyecto

---

## 1. Visión General

Paparcar es una app de **compartición de plazas de aparcamiento en tiempo real** para KMP (Kotlin Multiplatform) con Android como plataforma principal e iOS como target futuro. Detecta automáticamente cuando el usuario aparca o sale de su vehículo y publica la disponibilidad de plazas para la comunidad.

---

## 2. Estimación de Tiempos (Desarrollador Solo, ~30h/semana)

| Fase | Duración | Fechas Estimadas | Progreso Actual |
|------|----------|------------------|-----------------|
| **Phase 0** — Foundations | 4–5 semanas | Abr 2026 | ~40% hecho |
| **Phase 1** — Home & Mapa | 5–6 semanas | May–Jun 2026 | ~25% hecho |
| **Phase 2** — Vehículos & BT | 6–7 semanas | Jun–Jul 2026 | 0% |
| **Phase 3** — UI/UX Design | 4–5 semanas | Jul–Ago 2026 | 0% |
| **Phase 4** — History & Settings | 3–4 semanas | Ago–Sep 2026 | ~30% hecho |
| **Phase 5** — QA & Estabilidad | 3–4 semanas | Sep–Oct 2026 | ~15% hecho |
| **Phase 6** — iOS Port | 6–8 semanas | Oct–Dic 2026 | 0% |
| **TOTAL** | **~7–9 meses** | **Abr–Dic 2026** | |

> **Nota**: Las fases no son 100% secuenciales. Phase 3 (UI) puede solaparse con Phase 1 y 2 si se define el Design System primero. La Phase 5 (QA) debería ser continua, no solo al final.

---

## 3. PHASE 0 — Foundations (Estabilización)

### Objetivo
Solidificar la base antes de añadir features nuevas. Sin esto, cada feature nueva acumula deuda técnica.

### Nomenclatura de tareas

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `FND-001` | Extraer strings hardcoded a `strings.xml` / recursos compartidos | Refactor | Alta |
| `FND-002` | Eliminar magic numbers → crear `ParkingConstants.kt`, `DetectionConfig.kt` | Refactor | Alta |
| `FND-003` | Revisar y unificar el manejo de errores (Result/Either pattern) | Arquitectura | Alta |
| `FND-004` | Fix bug geofence departure: la plaza no se publica al desaparcar | Bug | Crítica |
| `FND-005` | Integrar Firebase Crashlytics (Android) | Infra | Alta |
| `FND-006` | Crear `expect/actual` wrappers pendientes para iOS | Arquitectura | Media |
| `FND-007` | Ampliar tests unitarios: UseCases, Repos, ViewModels | Testing | Alta |
| `FND-008` | Limpiar archivos del repo: `build_errors.log`, `build_log.txt`, `.kotlin/` | Repo | Baja |
| `FND-009` | Configurar CI básico (GitHub Actions: build + tests) | Infra | Media |
| `FND-010` | Documentar expect/actual contracts para iOS | Docs | Media |

### Detalle crítico: FND-004 (Fix Geofence Departure)

**Problema actual**: Cuando el usuario sale de la geocerca de 80m, el spot no se publica correctamente a Firestore.

**Flujo esperado**:
```
GeofenceBroadcastReceiver (GEOFENCE_EXIT)
  → DetectParkingDepartureUseCase
    → Verificar que la sesión activa corresponde a esa geofence
    → Marcar UserParking.isActive = false
    → Ejecutar ReportSpotReleasedUseCase
      → Crear Spot en Firestore con TTL (time-to-live)
      → Eliminar geofence
      → Notificar al usuario "Plaza publicada"
```

**Posibles puntos de fallo a investigar**:
- El `GeofenceBroadcastReceiver` no se registra correctamente tras boot
- El `PendingIntent` pierde la referencia (revisar FLAG_MUTABLE)
- El `geofenceId` no coincide entre creación y departure
- Falta retry en caso de fallo de red al publicar a Firestore

### Archivos de constantes sugeridos

```kotlin
// detection/config/DetectionConfig.kt
object DetectionConfig {
    const val GEOFENCE_RADIUS_METERS = 80f
    const val STILL_THRESHOLD_SECONDS = 90
    const val EXTENDED_STILL_SECONDS = 300
    const val SPEED_THRESHOLD_MS = 1.0
    const val HIGH_CONFIDENCE_THRESHOLD = 0.75
    const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55
    const val ACTIVITY_EXIT_BASE_SCORE = 0.50
    const val STILL_90S_SCORE = 0.40
    const val STILL_5MIN_SCORE = 0.70
    const val SPOT_TTL_MINUTES = 30L
    const val TRANSITION_REREGISTER_HOURS = 12L
}
```

---

## 4. PHASE 1 — Home & Mapa

### Objetivo
Completar la pantalla principal como experiencia core de la app.

### Nomenclatura de tareas

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `HOME-001` | Definir layout Home: Mapa + BottomSheet + Header flotante | UI | Alta |
| `HOME-002` | Implementar BottomSheet con lista de spots cercanos (`HomeSpotRows`) | UI | Alta |
| `HOME-003` | Vehicle Card component: modelo, matrícula, estado (aparcado/circulando) | UI | Alta |
| `HOME-004` | FAB Menu: acciones manuales (reportar plaza / liberar vehículo) | UI | Alta |
| `HOME-005` | Map markers: diseño custom, clusters para densidad alta | UI | Media |
| `HOME-006` | Map type switcher (normal / terrain / satellite) | UI | Baja |
| `HOME-007` | Navegación principal: BottomNav vs Drawer — decisión e implementación | UI/UX | Alta |
| `HOME-008` | Search bar con geocoding (`SearchAddressUseCase`) | Feature | Media |
| `HOME-009` | Banner informativo GPS accuracy | UX | Media |
| `HOME-010` | Pull-to-refresh spots cercanos | Feature | Baja |

### Decisión de navegación: BottomNavigation (recomendado)

**Mi recomendación: BottomNavigationBar con 3-4 destinos.**

Razones:
- La app tiene pocas pantallas principales (Home/Map, History, Settings)
- BottomNav es el patrón más natural en apps de mapas/localización (Google Maps, Waze, Uber)
- Es más descubrible que un Drawer para funciones clave
- En Compose Multiplatform funciona bien con `NavigationBar`

```
BottomNav destinos:
┌──────────┬──────────┬──────────┬──────────┐
│  🗺 Mapa  │ 🕐 Historial │ 🚗 Mi Coche │ ⚙ Ajustes │
└──────────┴──────────┴──────────┴──────────┘
```

La pestaña **"Mi Coche"** es una adición que sugiero: centraliza el estado del vehículo activo, permite cambiar entre coches si hay más de uno, y da acceso rápido a la configuración Bluetooth. Esto evita sobrecargar el Home.

### Flujo FAB — Acciones Manuales

```
FAB (pulsación) → Expandir mini-menu:
  ├─ "Reportar plaza libre" (vi una plaza disponible que no es la mía)
  │   → Toca posición en mapa
  │   → ReportManualSpotUseCase
  │   → Crear Spot con type = MANUAL, reportedBy = userId
  │   → TTL más corto (15 min vs 30 min para automáticas)
  │
  └─ "Liberar mi vehículo" (estoy saliendo con el coche)
      → Confirmar "¿Dejar plaza disponible para otros?"
        ├─ Sí → ReleaseParkingWithSpotUseCase
        │       → UserParking.isActive = false
        │       → Crear Spot tipo AUTO_RELEASED
        │       → Eliminar geofence
        └─ No → ReleaseParkingWithoutSpotUseCase
                → UserParking.isActive = false
                → NO crear Spot
                → Eliminar geofence
```

### Tipos de Spot (nueva enumeración sugerida)

```kotlin
enum class SpotType {
    AUTO_DETECTED,   // Detectado automáticamente por la app
    AUTO_RELEASED,   // Usuario salió manualmente y dejó plaza
    MANUAL_REPORT,   // Usuario reportó plaza que vio libre
}

enum class SpotStatus {
    AVAILABLE,       // Plaza disponible
    CLAIMED,         // Alguien va de camino
    EXPIRED,         // TTL expirado sin reclamar
}
```

---

## 5. PHASE 2 — Vehículos & Bluetooth

### Objetivo
Soportar múltiples vehículos por usuario y mejorar la precisión de detección con Bluetooth.

### Nomenclatura de tareas

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `VEH-001` | Modelo de datos `Vehicle` (marca, modelo, matrícula, color, btDeviceId) | Data | Alta |
| `VEH-002` | Pantalla de registro de vehículo (post-signup y desde Settings) | Feature | Alta |
| `VEH-003` | Selector de vehículo activo en Home/Mi Coche | UI | Alta |
| `VEH-004` | Room + Firestore: tabla `vehicles` con relación 1:N a `UserProfile` | Data | Alta |
| `BT-001` | Bluetooth scanning: descubrir dispositivos BLE/Classic cercanos | Feature | Media |
| `BT-002` | Emparejar dispositivo BT con un vehículo específico | Feature | Media |
| `BT-003` | Pantalla `BluetoothConfigScreen`: scan, pair, manage | UI | Media |
| `BT-004` | `BluetoothProximityDetector`: detectar connect/disconnect del coche | Feature | Alta |
| `BT-005` | Integrar BT como señal adicional en `CalculateParkingConfidenceUseCase` | Feature | Alta |
| `BT-006` | Permiso BT en flujo de permisos (paso opcional) | UX | Media |
| `BT-007` | Acceso a BT Config desde Settings | UI | Baja |

### Modelo de datos Vehicle

```kotlin
data class Vehicle(
    val id: String,
    val userId: String,
    val brand: String,           // "Toyota"
    val model: String,           // "Corolla"
    val licensePlate: String?,   // "1234 ABC" (opcional)
    val color: String?,
    val bluetoothDeviceId: String?, // MAC o UUID del BT del coche
    val bluetoothDeviceName: String?,
    val isDefault: Boolean,      // Vehículo activo por defecto
    val createdAt: Long,
    val updatedAt: Long,
)
```

### Estrategia Bluetooth — Cómo definirlo

**Principio**: El Bluetooth es un **potenciador de confianza**, no un requisito.

**Flujo sin BT** (funciona como ahora):
- Activity Recognition + GPS → Scoring → Confirmación

**Flujo con BT** (mejora precisión):
- Cuando el BT del coche se **desconecta** → señal fuerte de que saliste del vehículo (+0.60 score)
- Cuando el BT del coche se **conecta** → señal fuerte de que estás subiendo al coche (departure)
- **Multi-coche**: Si el BT que se desconecta corresponde al "Toyota", la sesión de parking se vincula a ese vehículo automáticamente

**Scores actualizados con BT**:

| Señal | Score |
|-------|-------|
| Activity IN_VEHICLE_EXIT | +0.50 |
| BT Disconnect del coche registrado | +0.60 |
| Parado 90s | +0.40 |
| Parado 5 min | +0.70 |
| STILL detectado | bonus |
| GPS alta precisión | bonus |
| **BT + Activity combinados** | **→ casi siempre HIGH** |

**Para multi-coche sin BT**: La app no puede saber cuál de los coches aparcó. Opciones:
1. Preguntar al usuario "¿Con qué coche estás?" al detectar parking (intrusivo)
2. Usar el vehículo marcado como `isDefault` (simple, pragmático)
3. Si hay BT configurado en alguno, usar eso para identificar automáticamente

**Recomendación**: Empezar con opción 2 (default vehicle), y si tiene BT, usar opción 3 automáticamente. Opción 1 solo como fallback si tiene múltiples coches sin BT.

### Flujo de registro de vehículo

```
Post-Signup (o desde Settings > Mis Vehículos):
  → VehicleRegistrationScreen
    → Formulario: Marca (dropdown), Modelo (dropdown dependiente), Color, Matrícula (opcional)
    → "¿Quieres emparejar Bluetooth?" (CTA opcional)
      ├─ Sí → BluetoothConfigScreen (scan → pair → guardar)
      └─ No → Guardar sin BT
    → Si es el primer vehículo → isDefault = true automáticamente
    → Sincronizar a Room + Firestore
```

---

## 6. PHASE 3 — UI/UX Design

### Nomenclatura de tareas

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `UI-001` | Crear Design System: tokens de color, tipografía, spacing, shapes | Design | Alta |
| `UI-002` | Componentes base: Button, Card, TextField, Dialog, Badge | UI | Alta |
| `UI-003` | Rama `experiment/glass-ui` para probar estilo glassmorphism | Experiment | Media |
| `UI-004` | Rediseño Onboarding con ilustraciones/animaciones | UI | Media |
| `UI-005` | Pulir flujo de permisos: stepper visual, explicaciones claras | UX | Media |
| `UI-006` | Banner/Snackbar de precisión GPS con niveles (buena/media/baja) | UX | Media |
| `UI-007` | Animaciones y transiciones entre pantallas | UI | Baja |
| `UI-008` | Dark mode / Light mode con sistema de temas | UI | Media |
| `UI-009` | Iconografía custom para tipos de spot, estados de vehículo | UI | Baja |

### Sugerencia de Design Tokens

```kotlin
// ui/theme/PaparcarTokens.kt
object PaparcarColors {
    // Primary: Emerald green (ya definido en el logo)
    val Primary = Color(0xFF10B981)
    val PrimaryDark = Color(0xFF059669)
    val PrimaryLight = Color(0xFF6EE7B7)

    // Spot states
    val SpotAvailable = Color(0xFF10B981)  // Verde
    val SpotClaimed = Color(0xFFF59E0B)    // Ámbar
    val SpotExpired = Color(0xFF6B7280)    // Gris

    // Vehicle states
    val VehicleParked = Color(0xFF3B82F6)  // Azul
    val VehicleMoving = Color(0xFF10B981)  // Verde

    // GPS accuracy
    val GpsHigh = Color(0xFF10B981)
    val GpsMedium = Color(0xFFF59E0B)
    val GpsLow = Color(0xFFEF4444)
}
```

### Glass UI — Qué probar

El estilo glass (glassmorphism) encaja bien en una app de mapas porque:
- Los fondos semi-transparentes permiten ver el mapa detrás de los controles
- Da sensación premium y moderna
- Funciona bien con BottomSheets y overlays

**Qué aplicar glass**: BottomSheet de spots, Vehicle Card, Header flotante, FAB expandido
**Qué NO aplicar glass**: Formularios, Settings, pantallas sin mapa detrás

---

## 7. PHASE 4 — History & Settings

### History

| ID | Tarea | Tipo | Prioridad |
|----|-------|------|-----------|
| `HIST-001` | Revisar UI: lista agrupada por día con mini-mapa | UI | Media |
| `HIST-002` | Filtros: por vehículo, rango de fechas, zona | Feature | Baja |
| `HIST-003` | Detalle de sesión: mapa, duración, dirección, vehículo | UI | Media |
| `HIST-004` | Tests: HistoryViewModel, mappers, filtrado | Testing | Media |
| `HIST-005` | Estadísticas básicas: tiempo medio aparcado, zonas frecuentes | Feature | Baja |
| `HIST-006` | Limpieza de código y arquitectura de la feature | Refactor | Media |

### Settings — Secciones sugeridas

| ID | Sección | Contenido |
|----|---------|-----------|
| `SET-001` | **Perfil** | Nombre, foto, email, logout, eliminar cuenta |
| `SET-002` | **Mis Vehículos** | Lista de coches, añadir/editar/eliminar, vehículo por defecto |
| `SET-003` | **Bluetooth** | Dispositivos emparejados, re-escanear, activar/desactivar BT detection |
| `SET-004` | **Detección** | Sensibilidad de detección (auto/manual), radio de geofence, auto-publicar plaza |
| `SET-005` | **Mapa** | Tipo de mapa default, unidades distancia, radio de búsqueda spots |
| `SET-006` | **Notificaciones** | Activar/desactivar por tipo (parking confirmado, plaza cerca, BT events) |
| `SET-007` | **Privacidad** | Compartir ubicación, datos anónimos, exportar/eliminar datos (GDPR) |
| `SET-008` | **Sobre la App** | Versión, licencias, contacto, política de privacidad |

---

## 8. PHASE 5 — QA & Estabilidad

| ID | Tarea | Tipo |
|----|-------|------|
| `QA-001` | Tests de integración: flujo completo detect → confirm → publish | Testing |
| `QA-002` | Tests de UI: Compose Preview Tests para pantallas principales | Testing |
| `QA-003` | Crashlytics: configurar alertas, analizar crashes top | Monitoring |
| `QA-004` | Edge cases: sin GPS, modo avión, batería baja, kill del proceso | QA |
| `QA-005` | Performance: profiling de batería del servicio en foreground | QA |
| `QA-006` | Accessibility: content descriptions, contraste, tamaños de fuente | UX |
| `QA-007` | Beta testing interno: distribución via Firebase App Distribution | Release |
| `QA-008` | Disclaimer GPS accuracy: diálogo inicial + banner en mapa | UX |

---

## 9. PHASE 6 — iOS Port

### Componentes que necesitan `expect/actual` o equivalente nativo

| Componente Android | Equivalente iOS | Complejidad |
|--------------------|----------------|-------------|
| `ParkingDetectionService` (Foreground Service) | Background Task + CLLocationManager | Alta |
| `ActivityTransitionReceiver` (BroadcastReceiver) | CMMotionActivityManager | Media |
| `BootCompletedReceiver` | No aplica (iOS no tiene boot receiver) | N/A |
| `FusedLocationProvider` | CLLocationManager | Media |
| `WorkManager` (Enrich, Report, Reregister) | BGTaskScheduler | Alta |
| `GeofencingClient` | CLCircularRegion + CLLocationManager | Media |
| `BluetoothAdapter / BLE` | CoreBluetooth (CBCentralManager) | Alta |
| `NotificationManager` | UNUserNotificationCenter | Media |
| `Permissions handling` | Info.plist + runtime requests | Media |

### Recomendación para iOS
Crear un módulo `iosMain/` paralelo a `androidMain/` con implementaciones `actual` para cada `expect` interface. La lógica de negocio (domain/) y los ViewModels ya son compartidos gracias a KMP.

---

## 10. Nomenclatura Global del Proyecto

### Convención de nombres para ramas

```
feature/HOME-001-bottom-sheet
feature/VEH-002-vehicle-registration
feature/BT-003-bluetooth-config
bugfix/FND-004-geofence-departure
refactor/FND-001-extract-strings
experiment/UI-003-glass-ui
chore/FND-008-repo-cleanup
```

### Convención de commits

```
feat(home): implement bottom sheet with nearby spots [HOME-002]
fix(detection): geofence departure not triggering spot publish [FND-004]
refactor(core): extract magic numbers to DetectionConfig [FND-002]
test(domain): add tests for ConfirmParkingUseCase [FND-007]
chore(repo): remove build log files [FND-008]
style(ui): apply glass effect to vehicle card [UI-003]
```

### Estructura de paquetes sugerida (evolución)

```
composeApp/
├── src/
│   ├── commonMain/
│   │   └── kotlin/com/paparcar/
│   │       ├── core/
│   │       │   ├── config/          ← DetectionConfig, AppConstants
│   │       │   ├── model/           ← GpsPoint, Result wrappers
│   │       │   ├── util/            ← Extensions, helpers
│   │       │   └── designsystem/    ← Tokens, Theme, base components
│   │       ├── domain/
│   │       │   ├── model/           ← Spot, UserParking, Vehicle, UserProfile
│   │       │   ├── repository/      ← Interfaces
│   │       │   └── usecase/
│   │       │       ├── auth/
│   │       │       ├── parking/
│   │       │       ├── spot/
│   │       │       ├── vehicle/
│   │       │       ├── location/
│   │       │       └── bluetooth/
│   │       ├── data/
│   │       │   ├── local/           ← Room DAOs, entities
│   │       │   ├── remote/          ← Firestore datasources
│   │       │   ├── mapper/
│   │       │   └── repository/      ← Implementations
│   │       ├── presentation/
│   │       │   ├── navigation/
│   │       │   ├── splash/
│   │       │   ├── onboarding/
│   │       │   ├── permissions/
│   │       │   ├── home/
│   │       │   │   ├── HomeScreen.kt
│   │       │   │   ├── HomeViewModel.kt
│   │       │   │   ├── components/  ← VehicleCard, SpotRow, MapControls
│   │       │   │   └── model/       ← HomeState, HomeIntent, HomeEffect
│   │       │   ├── history/
│   │       │   ├── settings/
│   │       │   ├── vehicle/         ← Registration, selector
│   │       │   └── bluetooth/       ← Config, scan, pair
│   │       └── di/
│   │
│   ├── androidMain/
│   │   └── kotlin/com/paparcar/
│   │       ├── detection/           ← Service, Receivers, Coordinator
│   │       ├── location/            ← FusedLocation wrapper
│   │       ├── bluetooth/           ← Android BT implementation
│   │       ├── notification/
│   │       ├── permission/
│   │       ├── worker/              ← WorkManager workers
│   │       └── geofence/
│   │
│   └── iosMain/
│       └── kotlin/com/paparcar/
│           ├── detection/           ← CMMotionActivity wrapper
│           ├── location/            ← CLLocationManager wrapper
│           ├── bluetooth/           ← CoreBluetooth wrapper
│           ├── notification/
│           ├── permission/
│           └── background/          ← BGTaskScheduler wrapper
```

---

## 11. Lo Que Yo Añadiría

### Añadir
1. **Spot TTL dinámico**: Las plazas manuales expiran en 15 min, las automáticas en 30 min, las de zonas de alta rotación en 20 min
2. **Spot claiming**: Cuando un usuario va camino a una plaza, marcarla como "reclamada" para que otros no la busquen (TTL de 5 min para llegar)
3. **Gamificación ligera**: Puntos por compartir plazas, badges, ranking semanal de tu zona. Incentiva el uso
4. **Widget Android**: Mini-widget con estado del coche (aparcado/libre) y tiempo transcurrido
5. **Zona habitual**: Detectar las 2-3 zonas donde más aparca el usuario y priorizarlas en la búsqueda
6. **Rate limiting de spots**: Evitar spam de plazas manuales falsas
7. **Reportar spot falso**: Botón en el detalle del spot para reportar que no existe
8. **Deep links**: `paparcar://spot/{id}` para compartir una plaza por WhatsApp

### Quitar o Modificar
1. **Simplificar el onboarding**: En lugar de múltiples screens, un carrusel de 3 pasos máximo con animaciones
2. **No forzar todos los permisos de golpe**: Pedir permisos contextualmente (BG location cuando detecte el primer viaje, no al inicio)
3. **Eliminar `build_errors.log` y `build_log.txt` del repo**: Son artefactos de desarrollo, no pertenecen al VCS
4. **El directorio `.kotlin/metadata/`** tampoco debería estar commiteado

### Modificar
1. **BaseLogin library**: Ya que es vuestra, considerar añadir el flujo de registro de vehículo como step post-auth dentro de la propia librería
2. **Geofence radius**: Hacerlo configurable por zona (centro ciudad = 50m, periferia = 100m) basado en densidad
3. **Modelo Spot**: Añadir `confidence` y `verifiedCount` para implementar trust score futuro

---

## 12. Orden de Ejecución Recomendado

### Sprint 1 (Semanas 1-2): Lo urgente
- `FND-004` Fix geofence departure (bloquea el flujo core)
- `FND-001` Strings hardcoded
- `FND-002` Magic numbers
- `FND-008` Limpiar repo

### Sprint 2 (Semanas 3-4): Infraestructura
- `FND-005` Crashlytics
- `FND-009` CI básico
- `FND-003` Error handling unificado
- `FND-007` Tests core (empezar)

### Sprint 3-4 (Semanas 5-8): Home funcional
- `HOME-007` Decidir e implementar BottomNav
- `HOME-001` Layout Home
- `HOME-002` BottomSheet spots
- `HOME-003` Vehicle Card

### Sprint 5-6 (Semanas 9-12): Home completo + mapa
- `HOME-004` FAB menu
- `HOME-005` Map markers
- `HOME-008` Search bar
- `HOME-006` Map type switcher

### Sprint 7-9 (Semanas 13-18): Vehículos
- `VEH-001` a `VEH-004` Modelo y registro
- `BT-001` a `BT-003` Bluetooth básico
- `BT-004` a `BT-005` BT como señal de detección

### Sprint 10-12 (Semanas 19-24): UI + Polish
- `UI-001` Design System
- `UI-003` Glass UI experiment
- `UI-005` Permisos polish
- `HIST-001` a `HIST-006` History
- `SET-001` a `SET-008` Settings

### Sprint 13-15 (Semanas 25-30): QA + Beta
- `QA-001` a `QA-008` Testing completo
- Beta interna

### Sprint 16-22 (Semanas 31-42): iOS
- Port completo de `androidMain` → `iosMain`
- QA iOS + App Store

---

## 13. Métricas de Progreso

Para ir midiendo el avance, estas métricas te ayudarán:

| Métrica | Objetivo |
|---------|----------|
| Cobertura de tests | >60% en domain/, >40% en data/ |
| Crash-free rate (Crashlytics) | >99% |
| Strings externalizados | 100% |
| Magic numbers eliminados | 100% |
| Features con BottomNav accesibles | 4/4 |
| Flujo parking detect → publish | Funcional sin bugs |
| Tiempo medio detección parking | <3 minutos |

---

*Documento vivo — actualizar conforme avance el desarrollo.*
*Última actualización: Marzo 2026*
