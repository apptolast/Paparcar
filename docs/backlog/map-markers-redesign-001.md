# MAP-MARKERS-REDESIGN-001 — Sistema visual de markers coherente

**Fecha:** 2026-05-19
**Estado:** ✅ Done 2026-06-05 — pendiente commit
**Prioridad:** Alta — los markers son la UI principal del mapa.
**Esfuerzo:** Medio (modelo + composables + rewire PaparcarMapView).

## Objetivo

Tres markers con lenguaje visual propio y coherente:

| Marker          | Forma              | Color         | Contenido                   |
|-----------------|--------------------|---------------|-----------------------------|
| Vehículo aparcado | Rectángulo 2.5:1 + punta inferior | Ámbar #F59E0B | Matrícula (últimos 7 chars) |
| Plaza libre      | Teardrop/gota      | Verde #22C55E  | "P" bold                    |
| Zona guardada    | Hexágono           | Azul #3B82F6   | 3 chars del nombre de zona   |

## Cambios de modelo

Añadir `licensePlate: String?` al pipeline completo:
- `Vehicle.kt`, `VehicleEntity.kt`, `VehicleDto.kt`, `VehicleMapper.kt`
- Room: bump a versión 4 (fallbackToDestructiveMigration ya configurado)
- `ParkedVehicleView.kt` — añadir el campo para que llegue al marker
- `ObserveParkedVehiclesUseCase.kt` — pasar `vehicle.licensePlate`
- `VehicleRegistrationState/Intent/Screen/ViewModel` — campo de matrícula en el formulario

## Composables (PaparcarMapMarkers.kt)

### LicensePlateMarker(plate, isSelected)
- Canvas 76×40dp
- Viewport 80×42: rectángulo (4,4)-(76,32) + triángulo-punta (34,32)-(46,32)-(40,40)
- Fill: ámbar #F59E0B, stroke: #D97706, texto: #1C0900 bold
- Halo de selección rectangular (mismo two-pass que el resto)
- Sombra elipse sutil bajo la punta

### FreeSpotMarker(isSelected) — simplificado
- Eliminar parámetro `reliability: SpotReliabilityLevel`
- Verde único #22C55E, onColor Forest
- Misma forma teardrop + "P" que antes
- Eliminar MANUAL badge y los 4 tiers de color

### ZoneMarker(zoneCode, isSelected)
- Hexágono flat-top (viewport 50×50, circumradius 20)
- Fill: azul #3B82F6, stroke blanco fino, texto blanco bold
- Sin sombra pronunciada
- `zoneCode` = `zone.name.take(3).uppercase()`

## PaparcarMapView.kt — rewire

### Free spot: 12 contentIds → 3
- Eliminar `MARKER_FREE_SPOT_HIGH/MEDIUM/LOW/MANUAL` y sus `_DIM` y `_SEL` variantes
- Añadir: `MARKER_FREE_SPOT`, `MARKER_FREE_SPOT_DIM`, `MARKER_FREE_SPOT_SEL`
- Eliminar `reliabilityContentId()`, `reliabilityDimmedContentId()`, `reliabilitySelectedContentId()`

### Vehículo aparcado
- `vehicleBadgeContentId()` → `vehiclePlateContentId(v, selected, dim)`
- `ParkedCarBadgeMarker` → `LicensePlateMarker`

### Zona
- `"zone_${iconKey}"` (per-preset) → `"zone_${zone.id}"` (per-instance)
- `ZoneMarker(icon)` → `ZoneMarker(zoneCode)`
- Añadir `zones` a la clave del `remember` de `customMarkerContent`

## Archivos a tocar

- `domain/model/Vehicle.kt`
- `data/datasource/local/room/VehicleEntity.kt`
- `data/datasource/remote/dto/VehicleDto.kt`
- `data/mapper/VehicleMapper.kt`
- `data/datasource/local/room/AppDatabase.kt`
- `domain/model/ParkedVehicleView.kt`
- `domain/usecase/parking/ObserveParkedVehiclesUseCase.kt`
- `presentation/vehicle/VehicleRegistration{State,Intent,Screen,ViewModel}.kt`
- `composeResources/values*/strings.xml` (EN, ES + P2)
- `ui/components/PaparcarMapMarkers.kt`
- `ui/components/PaparcarMapView.kt`
- `androidMain/…/PaparcarMapMarkersPreviews.kt`

## Notas

- `MyVehicleMarker` (teardrop verde) se conserva: lo usa ParkingLocationScreen como fallback
  cuando no hay `ParkedVehicleView` con contexto de vehículo.
- `VehicleAccentPalette` y `ParkedCarBadgeMarker` quedan obsoletos tras este ticket;
  se pueden eliminar en cleanup posterior.
- El sistema de reliability tiers (SpotReliabilityLevel) permanece en dominio/datos;
  solo pierde su codificación visual en los markers de mapa.
