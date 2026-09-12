# UI-GAS-STATION-MARKER-ICON · Unificar icono de gasolinera

**Estado:** ✅ Done · rama `bugfix/UI-GAS-STATION-MARKER-ICON` · worktree `../Paparcar-gas-station-marker-icon`

## Problema
El icono de las gasolineras usado en las listas no coincide con el icono de sus markers del mapa.

## Doctrina violada
La misma entidad debe conservar la misma identidad visual en todas las superficies.

## Señales / datos disponibles
La lista usa el icono de gasolinera de `PaparcarIcons`; el marker del mapa tiene una representación distinta.

## Diseño
Localizar el origen de ambos iconos y hacer que el marker reutilice el icono de gasolinera ya usado en listas, manteniendo una única fuente visual.

## Criterio de éxito
El marker de una gasolinera muestra el mismo surtidor que las filas y demás superficies de gasolineras; compilación y tests de UI pasan.

## Consumidores auditados
`HomeFuelStationRows`, `HomeNearbyLensRow`, `BrowsePeek` y `FuelStationMarker` usan `PaparcarIcons.Fuel`.
El `FuelPumpMark` custom se eliminó para que no pueda reaparecer una segunda representación.

## Validación
`compileMockDebugKotlin` y `compileProdDebugKotlin` pasan. `testDebugUnitTest` compila y ejecuta 2.710
tests, pero falla en cuatro pruebas preexistentes de paridad de locales/refetch de viewport, sin
relación con iconos.

La rama se rebasó sobre `master` renombrado como `f2552d47` sin conflictos.
