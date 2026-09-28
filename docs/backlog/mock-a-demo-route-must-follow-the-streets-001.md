# MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001 · una ruta que se declara casada a la calzada tiene que ir por la calzada

**Estado:** ✅ Done · rama `chore/MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001-street-routes` · worktree `../Paparcar-street-routes`

## Problema

Se vio en pantalla, grabando el detalle de un aparcamiento del historial para el vídeo del
Shipaton: la traza del viaje **cruza el mapa en diagonal**, por encima de manzanas, del Liffey y
de Trinity College, y entra en el pin desde donde no hay calle. El user lo dijo así:

> «enseña un detalle histórico que tenga la ruta bien hecha, no esa que cruza el mapa y no va por
> las calles»

No es un fallo del dibujo: es el dato. `routeTo()` en `FakeUserParkingRepository` genera la
polilínea a mano —26 vértices en línea recta desde ~1,5 km, con una comba perpendicular— y la
guarda junto a `routeSnapped = true`.

## Doctrina violada

**Un fixture no puede afirmar lo que su propio contenido desmiente.** `routeSnapped` significa,
literalmente en el KDoc de `UserParking:95`, *«the FINAL on-road line (map-matched, ready to
draw)»*. El catálogo lo pone a `true` sobre una línea que no toca ninguna carretera. Cualquiera
que mire la pantalla ve la contradicción; el código no la puede ver, porque las dos mitades
—el valor y la geometría— se escriben en sitios distintos.

Es la misma familia que [MOCK-THE-FLAGSHIP-FIGURE-HAS-NO-MOCK-DATA-001] y
[MOCK-A-TRIP-WITHOUT-ITS-LINE-CANNOT-BE-SHOWN-001]: el mock es el único sitio donde muchas
superficies se pueden VER, así que un dato de pega que miente se convierte en un diseño evaluado
sobre algo falso.

Segundo desajuste, del mismo origen: `routeDistanceMeters` se inventaba aparte
(`900f + (i % 9) * 850f`) cuando su contrato —`UserParking:109`— dice que es la longitud de
`routePolyline`. Y se estampaba **también en los pines manuales**, que no tienen ruta ninguna.

Y un tercero: la dirección (`leonStreets[i % 9]`) y la coordenada (`i % 7`, `i % 5`) se calculaban
con módulos distintos, así que el rótulo y el pin **nunca** hablaban del mismo sitio.

## Señales / datos disponibles

- `PolylineCodec` es Google precision-5 estándar, y su KDoc ya lo dice: *«same as Google
  Directions / OSRM overview geometry»*. Una polilínea de OSRM entra **literal**, sin traducir.
- `PolylineCodec.lengthMeters()` ya existe: la distancia no hay que inventarla.
- OSRM público (`router.project-osrm.org`) y Nominatim resuelven Dublín sin clave.

## Diseño

**La RUTA manda sobre el pin, no al revés.**

El KDoc anterior argumentaba contra escribir polilíneas literales: *«130 polilíneas literales
serían ilegibles y quedarían desincronizadas del mundo en cuanto las coordenadas se muevan otra
vez — como ya pasó al mudar la demo a Dublín»*. El argumento era correcto **mientras el pin fuera
la fuente**. Al invertir la dependencia desaparece: no hay nada con lo que sincronizarse, porque
la coordenada del pin **es** el último punto de la polilínea y el nombre de la calle **es** el de
esa calzada. Mudar el mundo ya no es editar 130 sitios: es volver a ejecutar el script.

Piezas:

1. `mockkit/tools/fetch_demo_routes.py` — pide a Nominatim dónde está cada calle del catálogo,
   engancha ese punto a la red de **conducción** con OSRM `nearest` exigiendo que la calzada
   **se llame** como la calle (acepta el sufijo de tramo: «Camden Street Lower» ES Camden
   Street), y traza tres llegadas desde rumbos distintos. Descarta lo que no cierra: una ruta
   que acaba a >40 m del pin, o que se va de 7 km por el sentido único.
2. `mockkit/…/fakes/data/DemoStreets.kt` — **generado**, 12 calles × ~3 llegadas = 35 trayectos
   reales, cada uno con sus metros medidos por OSRM sobre la calzada.
3. El catálogo consume `DemoStreets`: pin, dirección, polilínea y distancia salen todos de la
   misma fila.

## Criterio de éxito — verificado

- ✅ El detalle dibuja una línea que va **por calles**. Mirado en el emulador, que es donde se
  vio el fallo: Dame Street entra por los muelles del Liffey y Pearse Street baja por Macken
  Street. Con el FAB de encuadre se ve el viaje entero — Smithfield, Phibsborough, East Wall —
  y se lee como un trayecto, no como una diagonal.
- ✅ `routeDistanceMeters` es `drive?.meters`: no existe sin polilínea, y vale lo que mide OSRM.
- ✅ Dirección y pin salen de la misma fila de `DemoStreets`.
- ✅ `./gradlew testAll` en verde · `:app:assembleMockDebug` compila e instala.

**Lo que NO se ha comprobado:** 35 rutas × 12 calles, y solo se han mirado dos en pantalla. El
script descarta lo que no cierra (final a >40 m del pin, o >7 km), así que el resto está
comprobado por construcción, no a ojo.

## Consumidores auditados

`grep -rn "routePolyline = "` fuera de producción → 14 sitios:

| Sitio | Veredicto |
|---|---|
| `FakeUserParkingRepository` (León, i=184) | **cerrado** — pasa a `DemoStreets.leon` |
| `FakeUserParkingRepository` (Transit, i=289) | **cerrado** — pasa a `DemoStreets.transit` |
| `FakeUserParkingRepository` (Corolla) | **exento por diseño** — perfil BT sin ruta, a propósito |
| 11 tests (`RouteAvailabilityTest`, `SharedCarMirrorTest`, `ParkingSessionMapperTest`, `UserParkingReconcileTest`, `GroupFeedTest`, `RouteGeometryPrivacyTest`, `ParkingSyncWorkerTest`, fake de `commonTest`) | **exentos** — usan la cadena como IDENTIDAD (¿viaja?, ¿gana el local o el remoto?), nunca la dibujan. Una polilínea real no probaría nada más y `"encoded"` dice mejor que el contenido da igual |

`routeTo()` desaparece; con ella los imports `PI`/`cos`/`sin`/`PolylineCodec` del fichero.
