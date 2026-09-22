# FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001 · Un radio no se para en el límite del municipio

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#113**: *«gasolinera BP no aparece. A pesar de estar justo en el sitio donde está la BP, no
aparece.»*

## Qué se descartó primero, midiendo

⛔ **No es el filtro por combustible.** La sospecha inmediata era `isKnownNotToSell`: el ministerio
publica «no lo vende» como cadena vacía, y una BP que sólo sirviera premium desaparecería para un
coche de gasolina o gasóleo normales. Consultado el feed real de Cádiz (291 estaciones):

- **31 estaciones con rótulo BP; las 31 publican precio de 95 E5 y de Gasóleo A.**
- En toda la provincia sólo 4 estaciones carecen de 95 E5, y ninguna es una BP (dos taxis, una
  cooperativa, un suministrador).

El filtro queda exonerado. El fallo está antes: en **qué se pregunta**.

## Causa raíz

`MineturStationsSource.stationsNear` pregunta primero por MUNICIPIO y, si la respuesta no viene
vacía, **se la queda y ya no pregunta por la provincia**:

```kotlin
val fromTown = town?.let { parse(fetch(".../FiltroMunicipio/${it.municipalityId}"), query) }
if (fromTown != null && fromTown.stations.isNotEmpty()) return@runCatching fromTown
```

El atajo se escribió en [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] por dos
motivos —*«Precise and smaller»*— y el primero es cierto: el pueblo es la clave que una dirección
siempre trae, y de su fila sale el id de provincia. El segundo es el que muerde: **el municipio
define un PAYLOAD, y se le dejó definir la COBERTURA**.

Y la cobertura es un radio de 5 km alrededor del usuario. Un radio es un círculo; un municipio es un
polígono administrativo. No se contienen.

⛔ Tiene su propio guard para el caso vacío —*«An EMPTY town is a reason to widen»*— y ese guard es
justo el que hace invisible el fallo: mientras el pueblo conteste **algo**, por poco que sea, la
provincia no se consulta y nadie se entera de lo que falta.

## Señales / datos disponibles — cuánto esconde, medido

Sobre el feed real de Cádiz, para cada una de las 291 estaciones, cuántas de sus vecinas a ≤ 5 km
son de OTRO municipio (o sea: cuántas perdería alguien parado justo ahí):

| | |
|---|---|
| vecinas del mismo municipio | 3.710 |
| **vecinas de otro municipio** | **450 → el 11 % de lo que hay a 5 km** |
| peor caso (Puerto Real, bahía de Cádiz) | **19 de 21 vecinas, el 90 %** |
| estaciones donde TODO lo que hay a 5 km es de otro municipio | 4 |

El 11 % es la media de una provincia; el daño se concentra en los bordes municipales, que es
exactamente donde vive el usuario que abre la app en una bahía con cuatro municipios pegados.

⚠️ **No está confirmado que ésta sea la causa de ESA BP concreta** — para eso hace falta el punto, y
las capturas de la issue no se pueden descargar. Lo que sí está medido es que este camino produce el
síntoma exacto que la issue describe, y que lo produce a diario.

## Doctrina violada

*«⛔ Nunca se gatea VER: plazas, parkings públicos y gasolineras son free COMPLETO»* (CLAUDE.md).
Un mapa que se calla una de cada nueve gasolineras cercanas no está gateando, pero deja igual de
incompleto lo que el producto promete entero — y encima sin decirlo, que es peor.

## Diseño

**El pueblo nombra la provincia; la provincia sirve las estaciones.** Se borra el early-return del
municipio y se conserva todo lo demás:

- `town?.provinceId` **sigue siendo la clave nº 1** de enrutado, que es para lo que el pueblo sirve
  de verdad: es el campo que una dirección siempre trae, y ninguna comunidad autónoma es una
  provincia. Nada de lo que aquel ticket arregló se pierde.
- Desaparece la única consulta que podía contestar de menos. El resto de la escalera —postal,
  nombre de región, caja envolvente— y el guard de la provincia ADIVINADA quedan intactos.
- Es una **rama menos**, no una más: el `fetch` del municipio deja de existir, así que una consulta
  sigue costando una petición (más el catálogo, cacheado por proceso).

**Coste medido, y por qué se acepta:** la respuesta por provincia pesa entre **311 KB** (Cádiz) y
**944 KB** (Madrid, 894 estaciones) frente a las decenas de KB de un municipio. Se paga una vez y
queda en el caché de disco (`DiskBackedFuelStationsSource`), que existe precisamente para eso. Un
mapa correcto que pesa una vez vale más que uno barato que miente todos los días.

## Tests

`MineturStationsSourceTest`. El fixture gana una fila, `NEXT_TOWN`: a ~330 m del usuario y en el
municipio de al lado. Tres tests cambian de sentido y uno nace:

- **el testigo** — `should_bringAStationOfTheNextTown_when_itIsInsideTheRadius`;
- «pide el municipio» pasa a «pide la PROVINCIA del municipio, y no pregunta por el municipio»;
- «España se pregunta igual con una comunidad autónoma» sigue viva, mirando ahora la provincia;
- el orden por cercanía incorpora la fila nueva (`CERCA, NEXT_TOWN, MEDIO`), que de paso vigila que
  ordenar siga siendo cosa de la distancia y no del municipio de cada fila;
- desaparece «si el pueblo viene vacío, ensancha»: ya no hay atajo que ensanchar.

⚠️ **Falsificado**: reinstaurado el early-return, **fallan los tres**.

🟢 **La primera falsificación salió verde, y eso destapó un test que mentía.** El `MockEngine`
servía el fixture ENTERO a cualquier URL de estaciones, `/FiltroMunicipio/` incluida — o sea que el
mock no sabía reproducir el bug que el test decía vigilar, y el testigo pasaba por la razón
equivocada. Ahora el endpoint de municipio contesta sólo las filas de ese municipio, como el
ministerio. *Una prohibición sin testigo no es un chequeo, y un testigo que no puede ver el fallo
tampoco.*

## Criterio de éxito

- Parado en cualquier punto, la lista y el mapa traen **todas** las estaciones del feed a ≤ 5 km,
  sean del municipio que sean.
- Un test sobre respuestas de `MockEngine` falla si se reinstaura el atajo: el testigo es una
  estación dentro del radio y fuera del municipio.
- Lo que [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] arregló sigue verde: una
  región que es comunidad autónoma («Andalucía») se sigue resolviendo por el pueblo.

## Consumidores auditados

Los demás países no comparten el defecto — y la razón es que ninguno pregunta por polígono:

| Fuente | Cómo acota | Veredicto |
|---|---|---|
| `MineturStationsSource` (ES) | municipio → provincia | **el bug** |
| `FranceFuelStationsSource` | radio, `limit=100` | exento — acota por distancia, y declara `isTruncated` |
| `GermanyFuelStationsSource` | radio (MTS-K) | exento — ídem |
| `ItalyFuelStationsSource` | buscador por punto | exento |
| `OsmFuelStationsSource` | `around:` de Overpass | exento — radio, con `isTruncated` |
