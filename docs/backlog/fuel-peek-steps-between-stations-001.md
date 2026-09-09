# FUEL-PEEK-STEPS-BETWEEN-STATIONS-001 · El peek de gasolinera pasa a la siguiente sin volver al mapa

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

**Verificado el 09-09 en el Pixel 8 Pro con datos reales** (prodDebug `a41256b2…`, hash comprobado):
‹ › en la cabecera del peek; › pasó de REPSOL (Paseo Acacias) a CEPSA (Barrio Centro-Embajadores)
volando la cámara y re-seleccionando la cápsula. Suite verde con los 2 tests nuevos del derivado
ejecutados; strings ×9; `assembleMockDebug` + `assembleProdDebug` verdes. Las variantes de peek de
la galería enseñan el stepper solas (el fixture ya trae 3 estaciones).

Remate de [`fuel-stations-map-and-peek-001.md`](fuel-stations-map-and-peek-001.md), que dejó el
stepper fuera a propósito («se apunta como mejora si el uso lo pide») — y el user lo pidió mirándolo
en device el 09-09: comparar dos gasolineras obliga hoy a cerrar el peek y cazar la otra cápsula.

## Diseño

El molde entero ya existe en el peek de plazas [UI-PEEK-STEPS-BETWEEN-PINS-001]; esto es darle el
tercer carril:

- **El orden es el de la lista**: `HomePeekSlice.fuelStations` ya viene «más cercana primero» (el
  único orden honesto antes de que el user diga nada de precios). `fuelStationStep` derivado con
  `PeekStep.of(...)` — una estación que dejara de estar (recarga) da `None`, no bordes inventados.
- **‹ / › → `HomeSheetAction.SelectFuelStation`**, la MISMA acción de la fila y la cápsula:
  seleccionar + volar la cámara + peek. El paso no inventa un gesto nuevo.
- **Page-turn**: carril nuevo en `stepDirection` de `HomePeekHandle` (dos `SelectedFuelStation`
  consecutivos → dirección por el mismo orden que alimenta los chevrones, para que flecha y
  deslizamiento no puedan discrepar).
- **CD ×9**: `fuel_peek_step_prev` / `fuel_peek_step_next`.

## Criterio de éxito

- Con 2+ gasolineras cargadas, el peek muestra ‹ › y pasar de una a otra vuela la cámara y
  re-pinta la cápsula seleccionada; en los extremos el lado sin vecina no se ofrece.
- El deslizamiento lateral del peek (gesto ya existente de `PapSheet`) pagina igual que las flechas.
- Test del derivado (`fuelStationStep`: orden, extremos, id ausente → None) y suite verde.
- Galería: las variantes de peek existentes enseñan el stepper (3 estaciones ya en el fixture).
- Strings ×9 · Konsist · assembles · klib iOS.

## Consumidores auditados

- `PeekStep` — reusado tal cual, sin tocar.
- `stepDirection` — carril nuevo aislado; los carriles de plazas y coches intactos.
- `PapSheetStepper` + gesto `pagerSwipe` — vienen con `PapSheet`, cero cambios.
