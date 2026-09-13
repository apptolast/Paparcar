# A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001 · El mapa enseñaba seis parkings y la lista decía que no había ninguno

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

> ⚠️ **Superado en parte por [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001](nearby-one-store-follows-the-camera-001.md)** (13-09-2026): las tiendas de carril y de capa se fusionaron en UNA por sujeto, y la unión que pintaban mapa y lista desapareció. Lo que aquí se dice de dos tiendas / dos anclas es historia, no regla vigente.

## Problema — visto en device, no deducido

Oppo, prodRelease `df992ed7`, 10-09 a las 03:52, con el usuario en Lugar la Cañada y el mapa
arrastrado sobre **Jerez de la Frontera**:

- El **mapa** pintaba **seis cápsulas «P»** sobre Jerez.
- La **sección** decía, a la vez, **«No public car parks within 1.5 km.»**

Las dos afirmaciones son ciertas y esa es la trampa: la **capa** pregunta por donde está la CÁMARA y
la **sección** por donde está el GPS del usuario, a varios kilómetros. El usuario no ve dos preguntas:
ve una pantalla que se contradice consigo misma.

⚠️ **La sección de gasolineras tiene exactamente el mismo hueco** desde
[FUEL-LAYER-FOLLOWS-THE-CAMERA-001]. No se había notado porque su radio son 5 km y casi siempre
encuentra algo; con los 1.500 m de los parkings, el hueco salta a la primera.

## Doctrina violada

**Una pantalla no puede sostener dos respuestas a lo que el usuario lee como una sola pregunta.** Es
la misma familia que [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]: allí la sección afirmaba algo
que nunca preguntó; aquí afirma algo sobre un sitio que el usuario ya no está mirando.

🔑 **El argumento que lo decide:** la cabecera de la sheet **ya sigue a la cámara** — dice «YOUR ZONE ·
Calle Ronda de los Alunados» mientras el usuario mira Jerez. Si el título de la sheet habla de donde
miras, sus secciones no pueden hablar de donde estás.

## Diseño

Cambia el **ancla** de las secciones, no el número de tiendas:

- La sección pide alrededor de `viewportCentreOrGps()` — la cámara si ya habló, el GPS mientras no
  (arranque en frío, donde ambos coinciden).
- Al asentarse la cámara, si la sección está **desplegada**, se repregunta con el mismo
  `ViewportRefetch` que usa la capa: **medio radio** de movimiento, y `isWorthAsking` para no pedir
  en (0,0) ni a 50 km de ti.
- ⛔ **No se fusionan las tiendas de gasolineras.** La sección lleva `fetchedFor` (el combustible del
  coche) y la capa puede pedir sin combustible: fundirlas resucitaría el bug que `fetchedFor` cerró
  en [FUEL-STATIONS-MAP-AND-PEEK-001]. Lo que cambia es DÓNDE pregunta cada una, no cuántas hay.
- ⛔ Plegada no pide nada: sigue valiendo que nada se descarga hasta que alguien despliega.

## Criterio de éxito

- Con el mapa arrastrado a otra ciudad y la sección abierta: la lista enseña los de ESA ciudad, y
  nunca «no hay ninguno» mientras el mapa pinta cápsulas.
- El caso al revés también: si de verdad no hay nada donde miras, la sección lo dice — y el mapa
  tampoco pinta nada.
- Arranque en frío sin tocar el mapa: idéntico a hoy.
- Suite + Konsist + mock + prod + klib iOS. Verificado en device en el mismo escenario que lo destapó.

## Consumidores auditados

- **`HomeFuelSlice` y `HomeParkingsSlice`** — cada sección estrena su `anchorLat`/`anchorLon`: sin
  saber DÓNDE preguntó, «¿se ha movido lo bastante?» no tiene respuesta posible.
- **`toggleFuelStations` / `togglePublicParkings`** — dejan de leer `userGpsPoint` y pasan por
  `viewportCentreOrGps()`, que ya existía para las capas. En arranque en frío la cámara y el GPS
  coinciden, así que ese camino no cambia.
- **`loadFuelStations` / `loadPublicParkings`** — reciben lat/lon en vez de un `GpsPoint`: el punto ya
  no tiene por qué venir del GPS, y el tipo lo dice en vez de dejarlo a la costumbre.
- **`onCameraPositionChanged`** — un tercer `schedule…` junto a los dos de las capas, con el mismo
  debounce y la misma regla de medio radio.
- ⛔ **Las tiendas de gasolineras NO se fusionaron**: la sección conserva `fetchedFor` y la capa puede
  pedir sin combustible. Fundirlas resucitaría el bug que `fetchedFor` cerró en
  [FUEL-STATIONS-MAP-AND-PEEK-001]. Cambia **dónde** pregunta cada una, no cuántas hay.
- **`SpyPublicParkingsSource`** — estrena `seen`: contar preguntas no distingue «contestó» de
  «contestó **del sitio correcto**», que es exactamente lo que fallaba.

4 tests nuevos, uno por mitad de la regla: pregunta por la cámara al abrir · repregunta al asentarse
lejos · **no** repregunta plegada · **no** repregunta si apenas te moviste.
