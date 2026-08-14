# UI-FAB-CAR-IDENTITY-001 · El FAB "ir al coche" se tiñe con la identidad del vehículo seleccionado

**Estado:** ✅ Done · mergeado a `master` (ff-only, rama y worktree borrados —
`git log --grep UI-FAB-CAR-IDENTITY-001`) · 1117 tests verdes · APK instalado y verificado por
sha256 en Redmi y Oppo · ⏳ pendiente validación visual en campo con dos coches aparcados

## Problema
El FAB circular de coche de la columna derecha del mapa Home (`HomeMapFabColumn`, icono
`Icons.Rounded.DirectionsCar`) se pintaba **verde marca (`colorScheme.primary`) siempre** que la
sesión estuviera seleccionada, sin importar de qué coche era esa sesión.

Ese FAB **cicla entre las sesiones aparcadas** (`onParkedCar` en `HomeScreen`: sin selección → la
primera; con selección → la siguiente, con vuelta al principio [MULTI-PARKING-001]). Es decir, el
color es la única pista de "sobre qué coche estoy ahora mismo" — y estaba mintiendo: con dos coches
aparcados, el vigilado por Bluetooth y el que no, el FAB los pintaba iguales.

## Doctrina violada
`docs/design/COLOR-SYSTEM.md` / [UI-COLOR-DOCTRINE-001]: *el color de un vehículo es su MÉTODO de
vigilancia* — verde = detección activa (Coordinator), azul `papCarBlue` = Bluetooth, gris = sin
vigilancia — y **sale SIEMPRE del resolver único** `vehicleIdentityColor(watch)`. Aquí había un
verde inline decidido por un booleano de selección, un mini-resolver rival de facto.

## Señales / datos disponibles
Todo estaba ya en `HomeState`: `selectedSession` (la sesión que corresponde a `selectedItemId`) y
`vehicles`. El join sesión → vehículo → `monitoringStatus()` es el mismo que ya usan
`preferredSession`, las tarjetas de vehículo y los peeks.

## Diseño
El FAB no decide colores: **recibe el método de vigilancia y lo pasa por el resolver único.**

1. `HomeFabsSlice.isParkingSelected: Boolean` → `selectedParkingWatch: VehicleMonitoringStatus?`.
   `null` = no hay sesión seleccionada (el booleano viejo era exactamente `!= null`, y su ÚNICO
   consumidor era el tinte del FAB, así que no se pierde información).
2. `HomeState.toFabsSlice()` resuelve el vehículo de `selectedSession` y proyecta su
   `monitoringStatus()`. Si la sesión existe pero su vehículo no se encuentra (carrera de borrado),
   cae a `Inactive`: sigue habiendo selección, simplemente no se le atribuye vigilancia.
3. `HomeMapFabColumn` tiñe con `vehicleIdentityColor(status.watch())`; sin selección deja
   `Color.Unspecified` → el `onSurface` por defecto de `MapCircleFab`, como hasta ahora.

Resultado: BT → azul · detección activa → verde · sin vigilancia → gris (`onSurfaceVariant`, el gris
del resolver, un punto más apagado que el `onSurface` sin seleccionar) · sin selección → neutro.

## Criterio de éxito
- `HomeSlicesTest` cubre las tres proyecciones (BT / activa / inactiva) y el caso sin selección.
- En device con dos coches aparcados (Kamiq con BT + coche sin BT): pulsar el FAB cicla entre pines
  y el icono cambia de azul a verde/gris según a qué coche ha saltado la cámara.
- `ColorGuardrailTest` y el resto de la suite siguen verdes.

## Consumidores auditados
| Sitio | Estado |
|---|---|
| `HomeMapFabColumn` (`iconTint` del FAB coche) | **arreglado** — vía resolver único |
| `HomeMapFabsLayer` / `HomeMapFabsSection` (`HomeScreen`) | cubierto — solo reenvían el slice |
| `HomeFabsSlice` / `toFabsSlice` | cubierto — proyección nueva |
| `HomeSlicesTest.should_project_fabs_booleans_from_sessions_and_gps` | actualizado |
| FAB `MyLocation` (`followsCar` → `PapLiveMap`) | exento — no es identidad de vehículo, es "el mapa sigue al coche en movimiento" (azul de mapa, no `papCarBlue`) |
| `MapControlButtons` (`presentation/map/components`, mismo icono con `primary`+`primaryContainer`) | **código muerto** — sin ningún call site en el repo (grep). No se toca aquí; si algún día revive, debe adoptar el resolver |
| `HomeParkingRow`, peeks, `VehicleStatusIndicators`, `VehicleIdentityHeader`, `VehiclesScreen` | ya usaban `vehicleIdentityColor` |
