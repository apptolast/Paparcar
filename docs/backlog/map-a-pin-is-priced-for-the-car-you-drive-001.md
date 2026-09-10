# MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001 · El mapa heredaba el precio de la copia que ganó el `distinctBy`, no el de tu coche

**Estado:** ✅ Done · rama `bugfix/MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001-fuel-price` ·
worktree `../Paparcar-map-price`

## Problema

En España, con precios en la lista, **las cápsulas del mapa salen sin precio**: el mismo surtidor,
la misma pantalla, dos respuestas distintas. Reportado desde device el 10-09-2026.

Las dos superficies no leen lo mismo:

| superficie | de dónde saca la gasolinera |
|---|---|
| lista del carril | `fuel.stations` — la respuesta pedida **con tu combustible** |
| mapa (`HomeSlices.kt:437`) | `(fuelLayer.stations + fuel.stations).distinctBy { it.station.id }` |
| peek (`HomeSlices.kt:479`) | la misma unión |
| selección (`HomeState.kt:375`) | `fuelLayer` primero, `fuel` como respaldo |

`distinctBy` se queda con la **primera** aparición, o sea **siempre con la copia de la capa**. Y la
capa puede haber preguntado otra cosa:

- `HomeViewModel.kt:582` pide la capa **sin combustible** a propósito, cuando la lista no tenía
  pregunta que hacer (un coche sin `fuelType` declarado). Ahí `GetNearbyFuelStationsUseCase:104`
  resuelve `price = fuel?.let { station.prices[it] }` → **null en todas**.
- Y si el geocode falla en esa petición concreta, `FuelStationsRegistry` degrada a OSM, que no trae
  precios de ninguna clase.

El propio `askFuelForLane` ya lo tenía escrito (`HomeViewModel.kt:612`): *«An answer the layer fetched
fuel-less is a different question — repriced below»*. **Repone el precio en el carril y no en el
mapa**, que es exactamente donde el usuario lo echa en falta.

Y hay un segundo síntoma de la misma causa, que aún nadie ha reportado pero está ahí: cambiar de
coche activo (gasolina → diésel) no repregunta la capa, así que el mapa seguiría enseñando el precio
del combustible del coche anterior. **Un precio correcto para el coche equivocado es peor que ninguno.**

## Doctrina violada

`FUEL-STATIONS-MAP-AND-PEEK-001` dice que la cifra de la cápsula lleva al lado **qué combustible es**,
*«porque una cifra sin sujeto obliga al lector a recordar qué quema su coche»*. La regla completa es
la que faltaba:

> **Un precio en pantalla es el de TU coche, o no es.**

Y de fondo, la de siempre: el precio de una gasolinera **no es una propiedad de la petición**, es una
propiedad de la gasolinera. Heredarlo de la copia que casualmente ganó un `distinctBy` es dejar que
el orden de dos listas decida qué cifra lee el usuario.

## Señales / datos disponibles

- `FuelStation.prices` es un `Map<FuelType, StationPrice>` con **todo lo que se sabe de ese surtidor**,
  no solo lo que se preguntó. O sea: reponer el precio no necesita ninguna petición nueva — el dato ya
  viajó y se estaba tirando.
- `HomeState.activeVehicle` (línea 456) ya resuelve «el coche que cuenta» con la misma regla que usa
  `HomeViewModel.activeFuelType()`.
- Un surtidor sin precio para ese combustible sigue siendo `null`, y la cápsula ya sabe dibujar solo
  el glifo. No se inventa ninguna cifra.

## Diseño

Una sola lista, en `HomeState`, que **repone el precio al proyectar** en vez de heredarlo:

```
val pricedFuelType: FuelType?            // el combustible que esta pantalla cotiza
val fuelStationsShown: List<NearbyFuelStation>   // ambas tiendas, deduplicadas, REPRECIADAS
```

Las tres superficies (mapa, peek, selección) pasan a leer esa lista, así que **da igual qué copia gane
el `distinctBy`**: el precio ya no viene de la copia, viene de la gasolinera. La clase de bug se cierra
en vez de taparse reordenando la suma — un `fuel.stations + fuelLayer.stations` habría arreglado el
síntoma de hoy y habría dejado vivo el del cambio de coche.

`fuelPricesLabel` pasa a etiquetar ese mismo `pricedFuelType`, para que la cifra y su sujeto no puedan
salir de sitios distintos.

`HomeViewModel.activeFuelType()` deja de tener su propia copia de la regla y lee la del estado: **una
definición de «el combustible de tu coche»**, no dos.

## Criterio de éxito

- Tests de `HomeState` / `HomeSlices`: con la capa cargada **sin combustible** y el carril cargado con
  él, el mapa y el peek cotizan; con el coche activo cambiado, cotizan el nuevo sin repreguntar.
- En device: en España, cápsula y fila dicen la misma cifra para la misma gasolinera.

## Consumidores auditados

`grep -rn "fuelLayer.stations"` → cuatro sitios, los cuatro cubiertos:

| sitio | qué hacía | estado |
|---|---|---|
| `HomeSlices.kt:437` (mapa) | unión, capa primero | ✅ pasa por `fuelStationsShown` |
| `HomeSlices.kt:479` (peek) | unión, capa primero | ✅ pasa por `fuelStationsShown` |
| `HomeState.kt:375` (selección) | capa primero, carril de respaldo | ✅ pasa por `fuelStationsShown` |
| `HomeViewModel.kt:663-713` | poda de selección huérfana | ✅ exento: pregunta por PRESENCIA (`.none { id }`), no por precio |
| lista del carril (`slice.fuel`) | tienda pedida con tu combustible | ✅ exento hoy: ya se pide con el combustible correcto |

## Fuera de alcance (apuntado, no hecho)

- **La lista del carril no se reprecia**, solo las proyecciones. Hoy no muerde porque su tienda se pide
  con el combustible correcto; mordería si el usuario cambia de coche sin volver a entrar al carril, y
  entonces la fila diría una cifra vieja mientras la cápsula dice la nueva. Toca la lógica de
  `askFuelForLane` (`fetchedFor`), y eso es otro ticket.
- **`activeFuelType()` presta el combustible de OTRO coche** cuando el activo no declara ninguno
  (`firstOrNull { isActive }?.fuelType ?: firstOrNull()?.fuelType`). Contradice la propia doctrina del
  carril — *«el precio que se enseña tiene que ser el de lo que ESTE conductor echa»* — pero cambiarlo
  cambia lo que dice el carril, no solo el mapa. Se mueve tal cual está, sin tocar la semántica.
- **Una estación que se sabe que NO vende tu combustible** puede colarse en el mapa si vino de una
  petición sin combustible: el filtro `isKnownNotToSell` vive en el caso de uso, que solo puede aplicarlo
  con el combustible que le pasaron. Sale sin cifra, que es honesto, pero sale.
