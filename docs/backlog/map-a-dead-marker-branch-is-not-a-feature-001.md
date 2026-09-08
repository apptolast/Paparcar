# MAP-A-DEAD-MARKER-BRANCH-IS-NOT-A-FEATURE-001 · El tag de coche guardaba una rama azul que nadie pedía desde hace tickets

**Estado:** ✅ Done — en master (squash 08-09-2026)

## Problema

`VehicleBadgeMarker` tenía un parámetro `originDot: Boolean = false` y, dentro del Canvas, una rama
que pintaba el punto de posición como **punto azul con halo blanco** en vez del punto del estado.

**Cero llamadores.** `grep` en `shared/src` y `app/src`: el parámetro no se pasa desde ningún sitio.
El origen del viaje dejó de dibujarse dentro del tag cuando pasó a ser su propio marcador
centrado (`DepartureDotMarker`, [DEPART-CONSISTENCY-001]); la rama vieja se quedó ahí.

Descubierto de rebote al auditar `PapColor.live` para
[UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]: uno de los "9 usos" del azul de movimiento resultó no ser
un uso — era código que no se ejecuta.

## Doctrina violada

Ninguna regla de color o detección: es higiene. Pero sí un principio del proyecto — **un consumidor
muerto ensucia todo barrido futuro**. Ese `PapColor.live` apareció en la auditoría de colores y hubo
que razonar sobre él para acabar concluyendo que no se pinta nunca. La próxima auditoría habría
pagado el mismo peaje.

## Diseño

Se borra el parámetro, su rama y las dos constantes que solo ella usaba
(`ORIGIN_DOT_SCALE`, `ORIGIN_DOT_HALO_SCALE`). El punto de posición queda con una sola forma —el
del estado— y el comentario explica **dónde vive ahora** el origen del viaje, para que nadie lo
vuelva a añadir aquí.

## Criterio de éxito

- Cero referencias a `originDot` / `ORIGIN_DOT_*` en el repo.
- Ni un píxel de cambio en pantalla: la rama borrada no se ejecutaba.
- Compila y suite verde.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `VehicleBadgeMarker(originDot = …)` | **cerrado** — no existía ningún llamador |
| `DepartureDotMarker` | **exento** — es quien dibuja el origen de verdad, y ya lleva su color por parámetro |
| `MapMarkerIdsTest` | **exento** — el tag no keyaba por `originDot` (otra prueba de que estaba muerto) |

## Notas

Sin dominio, sin strings, sin detección.
