# HISTORY-DETAIL-001 — Detalle de aparcamiento histórico: icono real, método real y navegación prev/next

**Rama:** `feature/HISTORY-DETAIL-001-real-vehicle-icon-detection-prevnext`
**Estado:** ⏳ implementado, pendiente build/tests verdes + review + device.

## Problema (reportado por el usuario)
Al abrir un aparcamiento del historial en el mapa de detalle (`HistoryParkingDetailScreen`):
1. El icono de vehículo del modal **y** del marcador era un coche genérico, no el del vehículo real.
2. El método de detección **siempre** decía "detección automática", aunque el aparcamiento fuese
   manual o de geocerca de casa.
3. No había forma de pasar de un aparcamiento histórico a otro sin salir de la pantalla.

## Diagnóstico
1. **Icono genérico:** `AddressHeroRow` resolvía el icono con `sizeCategory.icon` (glifo genérico),
   ignorando `carbodyType`; el marcador no recibía `parkingVehicleCarbody`/`parkingVehicleColor`
   (el `PaparcarMapView` ya los soportaba). El color solo vive en `Vehicle`, no en `UserParking`.
2. **Siempre "automática":** bug de **persistencia**, no de UI. `UserParking.spotType` tenía default
   `AUTO_DETECTED` y **nunca se guardaba**: no existía columna en `UserParkingEntity`, ni campo en
   `ParkingHistoryDto`, ni mapeo. `ConfirmParkingUseCase`/`SaveManualParkingUseCase` lo fijaban bien
   en memoria, pero al releer de Room volvía al default.

## Solución
### Pieza 1 · Persistir `spotType` end-to-end
- `UserParkingEntity` gana columna `spotType TEXT` + `MIGRATION_14_15` (aditiva) + `AppDatabase` v15;
  migración registrada en Android + iOS DI.
- `ParkingHistoryDto` gana `spotType`; `RemoteUserProfileDataSourceImpl` lo lee (write ya serializa el
  DTO completo con `.set`).
- `ParkingSessionMapper` mapea las 4 direcciones. Legacy (null) → `AUTO_DETECTED` vía `toEnumOrDefault`.

### Pieza 2 · Icono de vehículo real (modal + marcador)
- `ParkingLocationViewModel` observa vehículos y resuelve `focusedVehicle` por `vehicleId` (color +
  fallback de carbody).
- El modal usa `VehicleIcon` (pictograma isométrico, sin tinte; solo el glifo de moto se tinta).
- El marcador recibe `parkingVehicleCarbody` + `parkingVehicleColor`.
- Resolución: `session.carbodyType ?: vehicle.carbodyType`, `session.sizeCategory ?: vehicle.sizeCategory`,
  `vehicle.color`.

### Pieza 3 · Stepper prev/next
- El VM mantiene `orderedSessions` (todo el historial, más reciente → más antiguo) y `focusedSessionId`;
  intents `FocusPrevious`/`FocusNext` mueven el foco (clamp en los extremos).
- Cámara + marcador siguen a `focusedSession` (nuevo `CameraTarget` con `token`).
- UI: dos chevrons en la cabecera del sheet (`PapSectionHeaderRow` trailing), deshabilitados en los
  extremos. Alcance: **todo el historial por fecha** (decisión del usuario).

## i18n
`parking_detail_prev` / `parking_detail_next` (content descriptions) en los 9 locales.

## Dev Catalog
Nuevo grupo "Detalle de aparcamiento histórico" en `StateGalleryScreen` (sheet aislado): auto/manual/
home, coche/furgoneta/moto, y extremos del stepper. `HistoryDetailSheet` pasa a `internal`.

## Tests
- `ParkingSessionMapperTest`: round-trip de `spotType` + default legacy.
- `ParkingLocationViewModelTest`: foco por id, prev/next, límites, resolución de vehículo.

## Pendiente
- [ ] Build/tests verdes (`assembleMockDebug` + unit tests).
- [ ] Review + commit (con permiso).
- [ ] Device: comprobar icono real, etiqueta correcta y stepper en Oppo/Redmi.
- [ ] iOS (migración registrada, sin device).
