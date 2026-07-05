# SNAP-TO-PARK-001 — Sacar el ancla de dentro de edificios (snap al aparcamiento más cercano)

> **Estado**: PENDIENTE — rama `feature/SNAP-TO-PARK-001-snap-anchor-out-of-buildings` (parte de
> `refactor/DET-SOLID-001-evidence-system`). Decidido 2026-07-05: probar primero en campo el
> armado temprano (safety-net + anchor-lock); implementar esto después con datos frescos.

## Problema (incidente de campo 2026-07-04, sesión `1783183613431`)

Trayecto ultra-corto → el exit de la geocerca llegó tan tarde que la sesión se armó con el coche
ya aparcado. El ancla solo pudo nacer de los primeros fixes post-aparcamiento (accuracy 13-17 m,
pegados a la fachada del supermercado) y quedó **dentro del edificio** (`36.60212,-6.25682`,
confirmado por el usuario: a pocos metros del parking real). ANCHOR-LOCK-001 evita que el pin
*empeore* (deriva indoor, save silencioso), pero no puede recuperar una posición que la sesión
nunca observó.

## Diseño

En el **worker de enrichment** (asíncrono, con red y reintentos — donde ya se geocodifica):

1. Point-in-polygon del ancla contra huellas de edificio (`building=*`) vía Overpass en ~60 m.
2. Dentro de un edificio que **NO** sea `amenity=parking` (los parkings cubiertos/subterráneos son
   edificios con esa etiqueta — NO tocar) → candidatos de snap, por orden:
   a. punto más cercano del polígono `amenity=parking` más próximo;
   b. vial aparcable más cercano (`service`/`residential`);
   c. borde del propio edificio.
3. **Cap de distancia ~40 m**: si el snap requiere mover más, no se mueve (mejor honesto a 20 m
   que teletransportado).
4. Mover vía `UpdateParkingLocationUseCase` (ya recoloca pin + geocerca + re-geocodifica).
   Sin red / OSM sin mapear → no-op.

## Cuándo aplica / señales ya persistidas

Solo saves AUTO_DETECTED con evidencia de armado tardío: `armEvidence` ∈
{verified_enter, verified_late, self_observed} y `tripMaxSpeedMps < minimumTripSpeedMps`
(la sesión nunca vio conducción → ancla = "primer sitio quieto"). Con conducción observada el
ancla nace de la llegada real y no se toca.

## Complemento barato (mismo ticket o aparte)

Acción **"Ajustar posición"** en la tarjeta de confirmación cuando esas mismas señales indiquen
ancla incierta → abre el mapa con el pin editable (flujo mover-pin existente).

## Datos ya disponibles

- Overpass ya integrado: `OverpassPlacesDataSourceImpl`, `OverpassRoadNetworkDataSourceImpl`.
- Fixture de replay del incidente: `TraceSupermarket001` (commonTest/replay) — sirve para el test
  del gate "cuándo aplica"; el snap en sí se testea sobre el use case puro con polígonos fake.

## Criterio de éxito

El incidente del 2026-07-04 reproducido acaba con el pin en el polígono `amenity=parking` del
supermercado (≤20 m del coche), sin intervención del usuario.
