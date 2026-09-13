# CARPARK-DETAIL-HAS-PREV-NEXT-CLOSE-001 · El peek de parking público pasa al siguiente sin volver al mapa

**Estado:** 🟡 En revisión · PR desde rama `feature/CARPARK-DETAIL-HAS-PREV-NEXT-CLOSE-001` ·
GitHub issue [#8](https://github.com/apptolast/Paparcar/issues/8)

## Problema
Issue #8: «Cuando seleccionamos parkings y aparece la modal de su detalle, deberíamos tener los mismos
iconos de acciones que tienen las modales de detalle de spots y gasolineras ‹ › ×».

Hoy `PublicParkingPeek` solo pasa `onDismiss` a `PapSheet`: enseña la `×` y nada más. Comparar dos
parkings obliga a cerrar el peek y cazar la otra cápsula, justo lo que
[`fuel-peek-steps-between-stations-001.md`](fuel-peek-steps-between-stations-001.md) resolvió para
gasolineras.

## Doctrina violada
Coherencia de un mismo esqueleto: los tres carriles de «cerca» (plazas, gasolineras, parkings)
comparten `PapSheet`, y uno de ellos se quedaba sin su stepper. No rompe ningún invariante de datos.

## Señales / datos disponibles
- `HomePeekSlice.publicParkings` ya viene en el orden de la lista («más cercano primero»,
  `NearbyOrdering.publicParkings`) — la misma proyección `publicParkingsShown` que pinta filas y
  cápsulas.
- `HomeSheetAction.SelectPublicParking(id)` ya existe: la fila y la cápsula la usan (seleccionar +
  volar la cámara + expandir).

## Diseño
Tercer carril del molde existente [UI-PEEK-STEPS-BETWEEN-PINS-001], sin tocar el ViewModel:
- `HomePeekSlice.publicParkingStep` = `PeekStep.of(publicParkings ids, selectedPublicParking id)`.
  Un parking que desaparece en una recarga da `PeekStep.None`, nunca bordes inventados.
- `PublicParkingPeek(step)` → `PapSheetStepper` con ‹ / › → `SelectPublicParking`, la MISMA acción
  de fila y cápsula. El gesto de swipe lateral (`pagerSwipe`) viene gratis con el stepper.
- `stepDirection` en `HomePeekHandle` gana el carril parking→parking, para que flecha y
  deslizamiento horizontal no discrepen.
- CD ×9: `parking_peek_step_prev` / `parking_peek_step_next` («Previous car park» / «Parking
  anterior»…). Vocabulario [COPY-SPOT-IS-NOT-A-PARKING-001]: EN «car park», IT/RO con calificador.

## Criterio de éxito
- ✅ Visto en emulador (Pixel 8 Pro, flavor mock): «Parking Plaza Mayor» (el más cercano) con ‹ apagado,
  › y ×; › pasa a «Aparcamiento Estación» volando la cámara, ya con ‹ › activos.
- Con 2+ parkings cargados el peek muestra ‹ › ×; en los extremos el lado sin vecino sale apagado.
- Pasar de uno a otro vuela la cámara y re-pinta la cápsula seleccionada, con page-turn horizontal.
- Tests del derivado en `HomeSlicesTest` (orden de la lista aunque el store venga desordenado,
  extremos, id ausente → None).
- Galería: las variantes «Peek de parking…» del grupo «Home · parkings públicos» ya enseñan el
  stepper (el fixture trae 3 parkings) — sin variante nueva.

## Consumidores auditados
- `PeekStep` / `PapSheetStepper` / `pagerSwipe` — reusados tal cual.
- `stepDirection` — carril nuevo aislado; plazas, gasolineras y coches intactos.
- `HomeViewModel` — sin cambios: `SelectItem(PublicParking)` ya era el reductor de fila y cápsula.
- `HomeBottomSheet` — ya trataba `selectedPublicParking` como selección (lista oculta, tope en peek).
