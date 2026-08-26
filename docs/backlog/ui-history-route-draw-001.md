# UI-HISTORY-ROUTE-DRAW-001 — La ruta guardada no se dibujaba en el detalle del historial

**Estado:** ✅ EN MASTER (`a4eadc45`) · verificado en device Oppo 2026-08-10 (detalle del parking
Kamiq 19:50 con datos reales: línea azul dibujada)
**Origen:** field-test 2026-08-10 — el parking `916a33ca` tenía `routePolyline` (1218 chars) +
`routeSnapped=1` en Room Y Firestore, pero el detalle mostraba solo el coche aparcado.

## Causa raíz

`ParkingLocationScreen` crea un `State` NUEVO cuando la sesión enfocada resuelve
(`remember(polyline, snapped) { mutableStateOf(...) }` — null→sesión al entrar, y en cada paso del
stepper). `PaparcarMapView` leía sus params observables dentro de scopes de larga vida creados UNA
vez — `LaunchedEffect(Unit) + snapshotFlow` (trails, puck) y `remember { derivedStateOf }` sin keys
(puckMeta, departure) — cuyo closure capturó la PRIMERA instancia (la vacía). El swap de instancia
era invisible: el colector seguía observando el State viejo y la ruta nunca aparecía.

Home no lo sufría porque `trip.trail` es un único `MutableState` estable que se MUTA, no se reemplaza.

## Fix (invariante en UN sitio — el consumidor)

En `PaparcarMapView`: wrappers `rememberUpdatedState` para los cuatro params observables
(`drivingPuck`, `tripTrail`, `matchedTrail`, `departurePoint`) leídos desde los scopes aislados.
Los colectores/deriveds leen ahora `current*.value` — doble lectura snapshot (wrapper + interno), de
modo que reaccionan tanto a la mutación del contenido como al reemplazo de la instancia, sin
reiniciar los scopes. Cualquier caller futuro puede pasar instancias nuevas sin romper el mapa.

## Ficheros

- `ui/components/PaparcarMapView.kt` — wrappers + 4 sitios de lectura (2 snapshotFlow, 2 derivedStateOf).

## Pendiente

- [ ] Commit + merge a master (squash) con go-ahead del user.
- [ ] Ojo de regresión en device: puck/trail en conducción real (Home) — los scopes tocados son los
      mismos que pintan el puck nativo [DRIVE-PUCK-NATIVE-001]; en frío no cambia nada (Home pasa
      instancias estables), pero conviene mirarlo en el próximo field-test.
- Relacionado, NO cubierto aquí: viajes BT-owned no graban ruta (gap de diseño, ver
  `det-bt-wrong-car-abort-001.md`); el pin Focus 19:51 sin ruta fue efecto del doble confirm.
