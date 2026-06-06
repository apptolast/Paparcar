# VEH-ADD-PILL-001 — Botón "añadir vehículo" invisible con 1 solo vehículo ✅ CLOSED

**Fecha:** 2026-05-19 | **Cerrado:** 2026-06-05
**Resolución:** Ya resuelto — el `size == 1` branch fue eliminado en un sprint anterior. `VehiclesPager` (con `VehicleTabRow` + `PaparcarAddChip`) se usa para todos los casos no vacíos.

## Problema

`VehiclesScreen` tiene tres ramas:

| Caso | Rama | Botón añadir |
|------|------|--------------|
| 0 vehículos | `EmptyVehicleState` | ✅ CTA prominente |
| 1 vehículo  | `VehiclePageContent` directo | ❌ Ninguno |
| 2+ vehículos | `MultiVehicleContent` → chip row + `AddVehiclePill` | ✅ Pill en la fila de chips |

Con exactamente 1 vehículo no hay forma de añadir un segundo desde esta pantalla.

## Causa

`VehiclesScreenContent` (línea ~169):

```kotlin
state.vehicles.size == 1 -> VehiclePageContent(
    vehicleWithStats = state.vehicles.first(),
    onIntent = onIntent,
)
```

El path de 1 vehículo salta la fila de chips y por tanto nunca muestra `AddVehiclePill`.

## Fix

**Opción A (recomendada):** Unificar los casos 1 y 2+ — usar siempre `MultiVehicleContent`
cuando `vehicles.isNotEmpty()`. Con 1 vehículo la fila de chips mostrará 1 chip + el pill `+`.
Requiere eliminar el branch `size == 1` y asegurarse de que `MultiVehicleContent` funciona
bien con `pageCount = 1` (el `HorizontalPager` no hace nada raro con una sola página).

**Opción B:** Mantener el branch `size == 1` pero pasarle `onAddVehicle` a `VehiclePageContent`
y añadir el pill dentro de ese composable. Más cambios dispersos, peor mantenibilidad.

## Archivos a tocar

- `presentation/vehicles/VehiclesScreen.kt` línea ~155-175 (rama `size == 1`)
- Verificar que `MultiVehicleContent` y el `HorizontalPager` no crashean con 1 página
  (ya funciona con 2+, debería ser trivial).

## Notas

- No requiere cambios de modelo, VM ni strings.
- `AddVehiclePill` y `VehiclesIntent.AddVehicle` ya existen y funcionan.
