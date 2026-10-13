# FUEL-THE-MAP-CAPSULE-MUST-WEAR-THE-FOLLOW-001 · la cápsula del mapa no dice si sigues esa gasolinera

**Estado:** 🔵 Abierto · sin empezar · sale de
[FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001](fuel-a-follow-must-show-itself-001.md), que cerró la fila y el
peek y dejó esta superficie escrita como follow-up.

## Problema

Una gasolinera se ve en **tres** sitios: la fila del carril, la hoja del peek y la **cápsula del
marcador en el mapa**. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001] le puso la estrella a las dos primeras —
un solo componente, `FuelFollowMark`, en la línea del nombre. La cápsula sigue sin decirlo, así que
mirando el mapa —que es la pantalla por defecto— no hay forma de saber cuál de los seis surtidores a
la vista es el que vigilas.

## Doctrina violada

La misma que abrió el ticket padre: *una gasolinera seguida se ve donde se ve la gasolinera*. Cerrar
dos de tres superficies deja el invariante a medias, y la que queda fuera es la más vista.

## Por qué no entró en el ticket padre

No es el mismo trabajo. Fila y peek son Compose sobre `PapListItem`/`PapSheet`: la marca es un
`Icon` en un `Row` y se ve al instante. La cápsula se **dibuja**: vive en `PaparcarMapMarkers` /
`PaparcarMapView`, con `TextMeasurer`, su propia geometría y una **clave de caché** que decide
cuándo se vuelve a pintar un marcador — la misma clave que ya mordió en
`reference_kmpmaps_marker_flicker` (keyar por `hashCode` provocaba flicker).

Eso convierte esto en tres decisiones que piden device, no una:

1. **Dónde** va la marca sin tapar el precio, que es lo que la cápsula existe para decir.
2. **Qué forma** toma a tamaño de marcador: una estrella de 16 dp al lado de una cifra de 11 sp no
   es la misma figura que en una fila, y puede pedir corona/anillo en vez de glifo.
3. **La clave de caché**: `isFollowed` tiene que entrar en ella o la estrella no aparecerá hasta que
   el marcador se repinte por otra razón — exactamente el fallo de
   [MAP-THE-HANDLER-MAP-MUST-BE-REBUILT-WITH-THE-MARKER-LIST-001] con `watchKey`.

## Señales / datos disponibles

- `HomeMapSlice` ya proyecta las gasolineras que el carril lista
  (`toMapSlice().fuelStations`), y `HomeState.followedFuelStationKeys` es el mismo conjunto que leen
  ya la fila y el peek. El dato está; falta la pintura y su clave.
- `FuelFollowMark` existe y fija color (`PapColor.actionText`) y etiqueta
  (`fuel_peek_following`): la cápsula tiene que reusar el token, no inventar otro.

## Criterio de éxito

- Con la cápsula a la vista, seguir y dejar de seguir cambia el marcador **sin** panear ni recargar.
- Un caso en la galería de estados con la cápsula seguida y la no seguida juntas, y captura en
  device (la decisión 2 no se cierra sin verla).
