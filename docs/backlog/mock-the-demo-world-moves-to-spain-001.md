# MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001 · el mundo de demostración se muda a España

**Estado:** ✅ Done (14-10-2026) · rama y worktree borrados al cerrar
· ⏳ queda regenerar las 72 capturas de la ficha de Play, que ahora enseñan Dublín (su propia tanda)

## Problema

El mundo del mock vive en **Dublín** desde `4b90ae506` (25-09). Para las capturas de la ficha en
inglés eso tenía sentido; para producir **contenido en castellano** no lo tiene: una pieza que
enseña la app con *«Camden Street, 21»* y *«MY CAR»* no es de aquí, y lo primero que hace un
espectador español es no reconocerse.

Lo pidió el user el 14-10 montando los materiales de Instagram: *«crea los mocks que sea necesario
que para eso está el mock»*.

## Doctrina violada

Ninguna regla de código. Sí el principio de *sistemas, no parches*, por partida doble:

1. **La ciudad estaba cableada en tres sitios** del generador (la consulta al geocodificador, los
   sufijos de tramo `Lower`/`Upper` y el comentario del fichero generado), así que una mudanza se
   quedaba a medias por construcción.
2. **El mundo no tenía ancla**: 82 coordenadas sueltas repartidas por 6 fakes, sin ninguna constante
   de la que derivaran. Mover el mundo era editar 82 literales a mano.

Y una tercera, que es la que de verdad muerde:

3. ⛔ **El pin del coche no derivaba de su calle.** `FakeUserParkingRepository` llevaba una
   coordenada y, por separado, la cadena `"Camden Street, 21"`. Nada las ataba. Es exactamente el
   defecto que [`MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001`](mock-a-demo-route-must-follow-the-streets-001.md)
   cerró para las RUTAS y que seguía abierto para el PIN — y se vio en la primera captura de esta
   tarea: rótulo «Calle de Fuencarral, 21» sobre un mapa centrado en Zurbano, a 900 m.

## Diseño

**Ciudad elegida: Madrid.** Es la que cualquier conductor español lee como «esto es aquí» sin
pensar, tiene cobertura de OSM/OSRM sobrada, y no ata el mundo de demostración al pueblo del autor
(que es un dato personal, no una decisión de producto).

1. **El generador se parametriza.** `fetch_demo_routes.py` gana `CITY`, `COUNTRY` y `NAME_VARIANTS`
   arriba del todo, y el encabezado del fichero generado se formatea con la ciudad. Mover la ciudad
   vuelve a ser cambiar tres constantes y correr el script — no buscar «Dublin» por el fichero.
   *(`NAME_VARIANTS` existe porque en Dublín la misma calle se parte en `Lower`/`Upper` y el
   geocodificador caía en el tramo peatonal. En Madrid el nombre va entero, así que la tupla es
   `("",)` — pero el mecanismo se queda, documentado, para la siguiente ciudad.)*
2. **Las 12 calles pasan a Madrid** y se regeneran sus 36 trayectos contra OSRM. Las 12 resolvieron
   a su calzada al primer intento, con el nombre comprobado por el propio script.
3. **Las 82 coordenadas se trasladan con la escala correcta**, no con un desplazamiento a pelo: un
   grado de longitud mide 66,5 km en Dublín y 84,7 km en Madrid, así que la diferencia de longitud
   se multiplica por `cos(53,345°)/cos(40,425°) = 0,7842`. Sin eso el mundo se estiraría un 27 % en
   el eje este-oeste y las plazas dejarían de estar a la distancia a la que estaban.
4. ⭐ **Y el mundo se ancla a su propia calle**: el desplazamiento final se elige para que el pin del
   coche caiga **exactamente** sobre el punto de calzada que el generador midió para
   `Calle de Fuencarral`. El rótulo y el mapa dejan de poder mentir, que era el punto 3 del problema.
5. **El callejero textual acompaña**: direcciones, ciudad, provincia, país y código ISO; los nombres
   de los vehículos (`Mi coche`, `Segundo coche`, `Mi moto`) y la zona (`Garaje de casa`).
   ⛔ Los nombres de gasolinera **siguen siendo genéricos** —`Estación de Servicio Castellana`,
   `Carburantes Tetuán`…—: la regla de no meter marcas de terceros en una captura de tienda no
   cambia porque cambie el país.

## Señales / datos disponibles

- `mockkit/tools/fetch_demo_routes.py` ya existía y ya hablaba con Nominatim + OSRM.
- `DemoStreets.kt` es **salida**, con su aviso de no editar a mano.
- Los 6 fakes con coordenadas: `FakeOtherDataSources`, `FakeSharedCarRepository`, `FakeSpotRepository`,
  `FakeUserParkingRepository`, `FakeZoneRepository`, `FakeLocationDataSource`.

## ⚠️ El coste, dicho entero

**Dublín no estaba por descuido.** Las unidades las manda la REGIÓN del locale (`en-US` pone millas
y mpg), así que las capturas de tienda en inglés se graban en **`en-IE`** —inglés, métrico y euro—
y por eso el mundo se mudó allí. Con el mundo en Madrid, **la ficha en inglés enseña calles
españolas**.

Se asume a conciencia, y la razón es que los dos públicos no pesan igual: la ficha en inglés es una
de nueve y su contenido no depende del callejero, mientras que **todo** el contenido en castellano
que se produzca a partir de ahora vive o muere por reconocerse. Si algún día hay que recuperar el
mundo inglés, el camino está abierto: tres constantes y volver a correr el script.

⚠️ **Y los nombres de vehículo son el mismo problema sin solución**: `Mi coche` no se traduce en la
captura alemana igual que `My car` no se traducía en la española. Un nombre que pone el usuario no es
un recurso de idioma. Se ha elegido el castellano por lo mismo que la ciudad.

## Criterio de éxito

- ✅ `./gradlew testAll` verde.
- ✅ `:app:assembleMockDebug -PmockAsProd` compila e instala.
- ✅ **Comprobado en el emulador**: la app abre en castellano con *«MI COCHE · APARCADO · Calle de
  Fuencarral, 21»*, y el mapa dibuja Malasaña — Plaza de San Ildefonso, C. de Colón, C. de la
  Farmacia, C. de Hortaleza, Plaza Pedro Zerolo— con el pin **sobre la propia Calle de Fuencarral**.
- ✅ Cero rastro de Irlanda: `grep -riE "dublin|ireland|leinster|camden|baggot|…"` sobre `mockkit/src`
  no devuelve nada.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `DemoStreets.kt` | ✅ regenerado por el script |
| los 6 fakes con coordenadas | ✅ trasladados y anclados |
| `FakeVehicleRepository`, `FakeZoneRepository` (nombres) | ✅ al castellano |
| `FakeFuelStationsSource` | ✅ direcciones de Madrid, nombres genéricos intactos |
| Capturas de la ficha de Play (`docs/release/play-listing/assets/screenshots/`) | ⚠️ **quedan caducadas**: enseñan Dublín. Se regeneran con `tools/shoot.sh` cuando toque la próxima tanda — no es parte de este ticket |
| `project-mock-the-demo-world-speaks-the-apps-language-001` (memoria) | ⚠️ pendiente de actualizar al cerrar |

## Fuera de alcance

- **Regenerar las 72 capturas de la ficha.** Es otra tanda, con su propia verificación por idioma.
- **Un mundo por idioma.** Sería lo ideal y es mucho más caro: el catálogo es uno solo y el
  generador escribe un único `DemoStreets.kt`. Si algún día hace falta, el parámetro ya está.
