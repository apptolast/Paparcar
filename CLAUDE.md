# Paparcar — CLAUDE.md

## Proyecto
Paparcar es una app KMP (Kotlin Multiplatform) de compartición de plazas de aparcamiento en tiempo real basada en comunidad. Android es la plataforma principal; iOS es target futuro. Cuando un usuario sale con el coche, la app detecta automáticamente el evento y publica la plaza recién liberada para que otros usuarios cercanos puedan encontrarla.

## Stack
> Fuente de verdad: `gradle/libs.versions.toml`. Versiones reales a 2026-07-01.
- Lenguaje: Kotlin 2.4.0 (KSP 2.3.9)
- Build: AGP 9.2.1 · compileSdk 37 · targetSdk 36 · minSdk 26
- UI: Compose Multiplatform 1.11.1 · Material3 (JB) 1.9.0 · Navigation Compose 2.9.2
- Arquitectura: Clean Architecture + MVI (State + Intent + Effect)
- DI: Koin 4.2.2
- DB local: Room KMP 2.8.4 (SQLite bundled 2.6.2)
- Backend: Firebase (GitLive KMP SDK 2.4.0) · firebase-bom 34.15.0
- Auth: BaseLogin (librería propia, JitPack)
- Async: Coroutines 1.11.0 + Flow · Serialization 1.11.0 · Datetime 0.8.0
- Mapas: kmp-maps (SW Mansion) 0.9.1 — Google Maps (Android) / Apple Maps (iOS)
- Imágenes: Coil 3.5.0 + Ktor 3.5.1 (motor de red)
- Logging: Napier 2.7.1
- Monitoring: Firebase Crashlytics

## Estructura
```
commonMain/  → domain/, data/, presentation/, di/, core/
androidMain/ → detection/, location/, bluetooth/, notification/, worker/, geofence/
iosMain/     → (futuro) CLLocation, CMMotion, CoreBluetooth, BGTask wrappers
```

## Arquitectura
- Domain layer es Kotlin puro — sin imports de Android/iOS
- Todo UseCase retorna `Flow<T>` (observables) o `Result<T>` (stdlib, operaciones one-shot); los evaluadores puros y síncronos pueden retornar un value object de dominio
- ViewModels usan MVI: sealed class State, Intent, Effect
- Repositorios exponen interfaces en domain/, implementación en data/
- Persistencia dual: Room (offline-first local) + Firestore (sync real-time)

## Detección de aparcamiento — Dual Strategy
Dos estrategias independientes, NUNCA se mezclan:

### BluetoothDetectionStrategy (determinista)
- Escucha BT disconnect → GPS fix → distance check > 30m → auto-confirm
- Escucha BT connect → DetectDepartureUseCase
- Sin scoring, sin Activity Recognition — es directo
- Para usuarios con BT emparejado con su coche

### CoordinatorDetectionStrategy (probabilístico)
- Activity Recognition + GPS stream → confidence scoring
- HIGH (≥0.75) → auto-confirm | MEDIUM (≥0.55) → preguntar usuario | LOW → reset
- Fallback para usuarios sin BT o con BT del móvil apagado

### Resolución
```kotlin
fun resolveStrategy(vehicle: Vehicle, isBluetoothEnabled: Boolean): ParkingDetectionStrategy {
    return if (vehicle.bluetoothDeviceId != null && isBluetoothEnabled) {
        BluetoothDetectionStrategy(vehicle.bluetoothDeviceId)
    } else {
        CoordinatorDetectionStrategy()
    }
}
```

Ambas estrategias convergen en: ConfirmParkingUseCase → Room + Firestore + Geofence + Notification + WorkManager geocoding

---

## REGLAS DE CÓDIGO OBLIGATORIAS

### ⛔ Iconos — sistema de 3 niveles
Antes de añadir un icono, decide el nivel. Regla mental: *plumbing de UI → Material; concepto de Paparcar → vector propio.*
- **Nivel 1 · Sistema → Material Symbols (Rounded).** Plumbing de UI: nav inferior, ajustes, buscar, cerrar/atrás, editar, chevron, calendario, filtros, capas. Familia **Rounded** (no Outlined) para casar con la tipografía redondeada (Outfit). `tint = onSurfaceVariant`.
- **Nivel 2 · Iconos de UI → Material Symbols (Rounded) con `tint`.** Incluye POI/categorías (`Icons.Rounded.ShoppingCart`, etc.). NO creamos glifos custom. El mapeo `PlaceCategory → Icons.Rounded.*` vive en la capa de presentación (domain es Kotlin puro, sin `Icons`).
- **Nivel 3 · Ilustración/marcadores → vector propio (relleno de marca, multicolor, NO tintar).** Hero, onboarding, empty states, marcadores, vehículos, fiabilidad.
  - Si el SVG es VectorDrawable-compatible (solo `path`, sin dashes/filtros/text) → VectorDrawable en `composeResources/drawable/` (variante oscura con sufijo `_dark`).
  - Si usa `stroke-dasharray`, nested-svg, filtros o texto → **dibujar en Compose Canvas** en commonMain (dash vía `PathEffect`, dark por parámetros). VectorDrawable NO soporta trazos discontinuos.
- **Tema:** Nivel 1/2 se tintan con el color del tema; Nivel 3 trae su color (elige carpeta/variante `light`|`dark`).

### ⛔ Tipografía — sistema de roles (`PaparcarType`)
La familia y el tamaño son propiedad del **ROL** del texto, no del widget. Nunca elijas fuente ni tamaño: elige rol.
Fuente de verdad = `ui/theme/PaparcarType.kt` (19 roles). Se lee `PaparcarType.current.<rol>` (provisto en `PaparcarTheme`). Un `fontWeight`/`color` inline sobre el `Text` SÍ se permite (afinar peso/color); el rol da familia+tamaño+tracking.
- **IDENTITY · Outfit**: `screenTitle`(=appBarTitle), `heroTitle`, `sectionTitle`(“Actividad”/“Historial”), `cardTitle`(nombre/calle), `rowTitle`(título pequeño de fila).
- **STRUCTURE · Inter**: `sectionHeader` (**siempre vía `PapSectionHeader`**), `cta`(botones), `label`(chip/label pequeño).
- **PROSE · Inter**: `subtitle`(16sp, subtítulos hero/onboarding), `body`, `caption`.
- **DATA · Barlow Condensed**: `metadata` ("30 min · 75 m"), `badge`/pin ("ACTIVO", "3 LIBRES"), `sizeToken` ("MEDIANO"), `statNumber` (25sp), `distance`, `chartLabel`, `chartValue`.
- Regla mental: *¿título? → Outfit. ¿Frase que se lee? → Inter. ¿Dato/token que se repite en filas o compite en horizontal con un nombre? → Barlow (rol DATA).*
- **PROHIBIDO** en `presentation/` y `ui/components/`: (a) `fontSize`/`letterSpacing` inline en un `Text`; (b) `MaterialTheme.typography.*` — usa un rol; si falta un tamaño, añade/ajusta un rol en `PaparcarType`. Enforced por `TypographyGuardrailTest` (Konsist). Excepciones allowlisted: canvas/`TextMeasurer` de marcadores de mapa + chrome tokenizado (bottom-nav, banner, action bar).
- `MaterialTheme.typography.*` es solo la base MD3 del framework (definida en `Typography.kt`); la app **siempre** habla con `PaparcarType`.

### ⛔ Color de acción — verde primario, rojo solo alerta
- **Verde de marca = primario.** Todo CTA normal usa `primary`.
- **Rojo (`error`/`PapRed`) RESERVADO** a: bloqueante de permisos, acción destructiva (borrar),
  error de formulario, y estado "baja/caduca" (fiabilidad LOW, TTL crítico/expirado). NUNCA rojo
  para un CTA que no sea alerta real.

### ⛔ Strings — NUNCA hardcoded
- Todo texto visible al usuario va en `composeResources/values/strings.xml`
- Usar `stringResource(Res.string.key)` en Compose
- Convención de key: `feature_component_description`
  - Ejemplo: `home_fab_report_spot`, `detection_parking_confirmation`
- Idiomas soportados: EN (base), ES, IT, PT, FR + futuros P2
- Cuando añadas un string, SIEMPRE añadirlo mínimo en EN y ES
- Keys siempre en inglés: `spot_available` no `plaza_disponible`

### ⛔ Magic numbers — NUNCA inline
- Las constantes van en `companion object` privado de la clase que las usa
- Si se comparte entre 2+ clases → extraer a archivo config del módulo
- Nombre descriptivo en UPPER_SNAKE_CASE
- Ejemplo:
```kotlin
// ✅ En GeofenceManager.kt
private companion object {
    const val GEOFENCE_RADIUS_METERS = 80f
}

// ✅ En CalculateParkingConfidenceUseCase.kt
private companion object {
    const val HIGH_CONFIDENCE_THRESHOLD = 0.75
    const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55
}

// ❌ NUNCA
if (distance > 80f) { ... }
if (score >= 0.75) { ... }
```

### Error handling
- El estándar es `kotlin.Result<T>` (stdlib) — NO hay wrapper `AppResult` propio.
- Operaciones one-shot (UseCase/repo) retornan `Result<T>` vía `runCatching`:
```kotlin
suspend operator fun invoke(...): Result<Unit> = runCatching { /* ... */ }
```
- Los `Flow` aíslan errores con `.catch { e -> ... }` para no matar el stream (la UI sigue sirviendo la cache).
- Los errores de negocio que llegan a la UI se modelan con `PaparcarError` (sealed: `Location`, `Network`, `Database`, `Detection`, `Auth`, `Parking`, `Vehicle`) y se emiten vía `Effect.ShowError(PaparcarError)` → `when` en la pantalla → `SnackbarHost`.

### Testing
- Toda UseCase nueva debe tener test unitario
- Usar fakes sobre mocks: FakeAuthRepository, FakePermissionManager, FakeUserParkingRepository...
- Naming: `should_expectedBehavior_when_condition`

### Commits — Conventional Commits
```
feat(home): implement bottom sheet with nearby spots [HOME-002]
fix(detection): geofence departure not triggering spot publish [FND-004]
refactor(core): extract magic numbers to companion objects [FND-002]
test(domain): add tests for ConfirmParkingUseCase [FND-007]
chore(repo): remove build log files [FND-008]
feat(i18n): add Italian translations [FND-001]
```

### Ramas
```
feature/HOME-001-bottom-sheet
bugfix/FND-004-geofence-departure
refactor/FND-001-extract-strings
experiment/UI-003-glass-ui
chore/FND-008-repo-cleanup
```

### Cosas que NO hacer
- No usar `HorizontalDivider`/`VerticalDivider` crudos en feature → `PapDivider`/`PapVerticalDivider` (fuente única, alpha en `PapBorders.HAIRLINE_DIVIDER_ALPHA`). Enforced por `DividerGuardrailTest`.
- No usar `println` para logs → usar Logger con tag
- No usar wildcard imports (`import com.paparcar.*`)
- No commitear archivos de build: logs, .kotlin/metadata, build/
- No poner constantes en God Objects compartidos
- No escribir strings en español en el código — EN es siempre la base
- No mezclar señales Bluetooth dentro del Coordinator scoring
- No crear pantallas sin sus correspondientes State/Intent/Effect sealed classes
- No añadir pantalla/estado/flujo nuevo sin actualizar el sistema de pruebas mock (ver regla ⛔ abajo)

### ⛔ Sistema de pruebas mock (Dev Catalog) — mantener SIEMPRE en sync
Existe un modo solo-mock (flavor `mock`, `src/mock/.../dev/`) para entrar a la app sin OAuth/Firebase
y probar pantallas y estados en el dispositivo: **Dev Catalog** (launcher, `DevMainActivity` →
`DevRoot`/`DevCatalogScreen`) con escenarios de sesión/permisos (`MockScenario` + fakes
scenario-aware) y una **galería de estados** (`StateGalleryScreen`). Al implementar algo nuevo hay
que actualizarlo en la MISMA tarea, o queda fuera del set probable:
- **Pantalla nueva** → nuevo `ScreenGroup` en `StateGalleryScreen.kt` llamando a su `XxxContent(state=…)`
  (espejar su `*Previews.kt`).
- **Estado/variante nuevo** (loading/empty/error/modo) → añadir la variante a la galería; paridad con `*Previews.kt`.
- **Condición que afecte routing** (sesión, permisos, onboarding, vehículo) → reflejar en `MockScenario`,
  el fake que la lee, y un preset/control en `DevCatalogScreen.kt`.
- Verificar `assembleMockDebug` (y no romper prod). Solo se toca `src/mock/` + fakes de `commonMain/fakes/`.

## Modelos de datos clave
- `Spot` — plaza comunitaria: location, type (AUTO_DETECTED/MANUAL_REPORT), status, confidence, sizeCategory, carbodyType, enRouteCount, TTL
- `UserParking` — sesión propia: vehicleId, location, geofenceId, isActive, detectionMethod, sizeCategory, carbodyType
- `Vehicle` — vehículo: brand, model, licensePlate?, bluetoothDeviceId?, isDefault, sizeCategory, carbodyType?
- `UserProfile` — perfil Firebase: userId, email, displayName, photoUrl

### Categorización bidimensional de vehículos
- `VehicleSize` (5 valores): MOTORCYCLE, MICRO_SMALL, MEDIUM_SUV, LARGE_SEDAN, VAN_HIGH — afecta longitud de la plaza y radio de geofence
- `CarbodyType` (10 valores): HATCHBACK_SMALL, SUV_SMALL, HATCHBACK_MEDIUM, SUV_MEDIUM, SEDAN, FAMILY_LONG, SUV_LARGE, VAN_LIGHT, VAN_COMMERCIAL, PICKUP — afecta anchura, gálibo e identidad visual (icono en mapa, peek)
- Inferencia automática `brand + model → CarbodyType` vía `VehicleCatalog.inferBodyType()` con fallback de patrones (regex `contains`) cuando no hay match exacto
- Compatibilidad `SpotFit` (OPTIMAL / FITS / DOES_NOT_FIT / UNKNOWN) calculada con ambos ejes — ver `docs/architecture/VEHICLE-CATEGORIZATION.md`

## Navegación
BottomNav con 4 destinos: Mapa | Historial | Mi Coche | Ajustes
Splash → Auth → VehicleRegistration → Onboarding → Permissions → Home

## i18n
- Base: EN (siempre completo)
- P0: ES
- P1: IT, PT, FR
- P2: DE, NL, PL, RO
- Excluidos por complejidad UI: idiomas RTL (AR, HE) y glifos complejos (ZH, JA, KO, TH, HI)
