# FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001 · un híbrido reposta gasolina, y la fila y el marcador no lo sabían

**Estado:** ✅ Done · rama `bugfix/FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001-pump-product` ·
worktree `../Paparcar-hybrid-pump`

## Problema

Con el móvil en Berlín (Pixel 8 Pro, `geo fix 13.4050 52.5200`, build `3ffb7eae`), el precio de las
gasolineras **no sale ni en el marcador del mapa ni en la fila de la lista** — pero **sí sale en la
modal** del peek, con sus dos surtidores y su línea «precios en tiempo real».

No es un problema de Alemania. Los datos alemanes llegaron enteros: leído el Room del propio device,
`fuel_stations` tiene 35 filas de `de-tankerkoenig` y `fuel_station_fuels` tiene 70 precios,

```
DIESEL    35  2.379 … 2.429 EUR
GASOLINE  35  2.309 … 2.359 EUR
```

y el coche activo de esa cuenta es un Aiways U6 con **`fuelType = HYBRID`**.

Ahí está todo el bug:

- la fila (`HomeFuelStationRow`) y el marcador (`FuelStationMarker`) pintan `NearbyFuelStation.price`,
  que se resuelve como `station.prices[fuelDelCoche]` → `prices[HYBRID]` → **null**;
- la modal (`FuelStationPeek`) no pregunta por el coche: itera los surtidores de los que la fuente
  **habló** (`availabilityOf(fuel) != UNKNOWN`) y pinta los dos que tienen precio.

Por eso una misma estación está a la vez priced y unpriced en dos superficies de la misma pantalla.
Y por eso **muerde en los nueve países, no solo en Alemania**: ningún parser —Minetur, France,
Tankerkönig, OSM— emite jamás una clave `HYBRID` ni `PLUG_IN_HYBRID`, porque **ningún surtidor vende
"híbrido"**. Cualquier cuenta con coche híbrido o híbrido enchufable ve la lista entera sin precios.

## Doctrina violada

**Sistemas, no parches: el invariante se arregla en UN sitio.** Aquí el invariante ya estaba escrito
— *dos veces*, las dos privadas, y ninguna al alcance del carril de gasolineras:

- `AverageFuelPriceTable.pumpProduct()` (privado): «*A hybrid does not fill up with "hybrid": it
  fills up with petrol, and so does a plug-in hybrid.*» Su propio comentario cuenta que este mismo
  bug ya se cazó una vez: *«`PLUG_IN_HYBRID` had no row and the spend for every hybrid came back
  null»*. Es literalmente el bug de este ticket, en el surtidor de al lado.
- `EmissionFactors.kgCo2PerLiter()`: «*Hybrids burn gasoline*», agrupando HYBRID y PLUG_IN_HYBRID con
  GASOLINE en su propio `when`.

Tres superficies necesitan el mismo hecho y cada una lo re-escribe o se lo salta. Un invariante
recurrente mal ubicado es la definición de ticket de sistema.

Y además: **`MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001`**. `HomeState.pricedFuelType` se
documenta como *«la ÚNICA definición de qué echa este conductor»*, leída por el mapa, el peek y la
propia petición del ViewModel. Devolvía el powertrain, no el producto — que no es lo que echa nadie
en el depósito.

## Señales / datos disponibles

- Room del device (`paparcar.db`, tablas `vehicles`, `fuel_stations`, `fuel_station_fuels`): coche
  HYBRID + 70 precios GASOLINE/DIESEL en la misma base. El mismatch es de la CONSULTA, no del feed.
- Llamada directa a `list.php` con la clave de `local.properties`: `ok:true`, 35 estaciones a 5 km de
  Berlín, E5 2,329 / E10 2,269 / diésel 2,399. El feed alemán está sano.
- ⛔ **Por qué la galería mock nunca lo enseñó**: `FakeFuelStationsSource` **sí** emite
  `FuelType.HYBRID` y `FuelType.PLUG_IN_HYBRID` con precio de gasolina, y el coche por defecto de
  `FakeVehicleRepository` es HYBRID. El fake priceaba un surtidor que no existe en ninguna fuente
  real, así que el set de pruebas estaba tapando exactamente el caso que rompía.

## Diseño

El hecho —**qué producto compra en el surtidor un coche de este powertrain**— pasa a ser una sola
propiedad pública del dominio, al lado de las dos preguntas de la misma forma que ya viven ahí:

```kotlin
// domain/model/FuelType.kt, junto a usesLiters / usesElectricity
val pumpProduct: FuelType
```

`when` exhaustivo y sin `else`, para que un `FuelType` nuevo tenga que volver aquí a contestar.

`prices` y `availability` de `FuelStation` **siguen indexados por PRODUCTO** y no se tocan: son dos
preguntas distintas sobre el mismo mapa y meter la conversión dentro de `availabilityOf()` rompería
el peek, que pasaría a listar filas «Híbrido 2,309» y «Híbrido enchufable 2,309» para el mismo
surtidor. La conversión se aplica **donde un coche se convierte en clave de búsqueda**, que son dos
sitios y es idempotente (`GASOLINE.pumpProduct == GASOLINE`):

1. `GetNearbyFuelStationsUseCase` — el veredicto de dominio («dónde reposto ESTE coche, y a cuánto»)
   queda correcto para cualquier llamante, no solo para Home. Cubre a la vez el precio y el filtro
   `isKnownNotToSell`, que con HYBRID nunca podía ser cierto y dejaba visibles estaciones que sí
   declaran no vender gasolina.
2. `HomeState.pricedFuelType` — la definición única de presentación. Es **obligatorio** que sea aquí
   y no más abajo: `fuelPricesFor = pricedFuelType` es el rótulo que el marcador pinta bajo la cifra,
   y `HomeSlices` ya avisa de que *«una cápsula que lee "1,529 Diésel" mientras cita gasolina es el
   único fallo que este emparejamiento existe para evitar»*. Cifra y sujeto salen del mismo sitio:
   el conductor de un híbrido lee «2,309 Gasolina», que es verdad.

Las dos copias privadas se borran y pasan a leer la propiedad compartida.

## Criterio de éxito

- En device, Berlín, coche HYBRID: la fila y el marcador muestran el precio de gasolina, con el
  rótulo «Gasolina» bajo la cifra, y la modal sigue enseñando los dos surtidores.
- Tests (`should_expectedBehavior_when_condition`):
  - `FuelTypeTest` — cada powertrain nombra su producto; los tres productos son su propio producto.
  - `GetNearbyFuelStationsUseCaseTest` — un coche HYBRID recibe el precio de gasolina de una estación
    que solo cotiza gasolina y diésel.
  - `NearbyOrderingTest` — un híbrido ordena priced-first como un gasolina.
  - `HomeSlicesTest` — `fuelPricesFor` de un coche híbrido es GASOLINE (cifra y rótulo, un origen).
  - `AverageFuelPriceTableTest` / emisiones — la conducta no cambia al dedupicar.

## Consumidores auditados

`grep -rn "prices\[\|availabilityOf(\|isKnownNotToSell\|FuelType.HYBRID\|PLUG_IN_HYBRID"`:

| Sitio | Pregunta que hace | Veredicto |
|---|---|---|
| `GetNearbyFuelStationsUseCase:123` `prices[it]` | coche → precio | **arreglado** (`pumpProduct`) |
| `GetNearbyFuelStationsUseCase:116` `isKnownNotToSell(fuel)` | coche → ¿lo vende? | **arreglado** (misma línea) |
| `NearbyOrdering:36` `prices[it]` | coche → precio | **cubierto** — recibe ya el producto desde `pricedFuelType` |
| `HomeState:437` `pricedFuelType` | coche → qué echa | **arreglado** (origen del rótulo) |
| `FuelStationPeek:94,141,155,166` | surtidor → precio | **exento a propósito** — itemiza PRODUCTOS, no el coche |
| `AverageFuelPriceTable:75` `pumpProduct()` privado | coche → producto | **dedupe** → propiedad compartida |
| `EmissionFactors:16` | coche → kg CO₂/l | **dedupe** → `when (fuel.pumpProduct)` |
| `FuelDefaults:26-27` | coche → l/100 km | **exento** — un híbrido consume MENOS que un gasolina; es su propia cifra, no la del producto |
| `AverageFuelPriceTable:59` `ELECTRIC/HYBRID/PHEV -> null` | rama inalcanzable | **cubierto** — se mantiene nombrada, sin `else` |
| `RoomFuelStationsCache:168,189` | producto → fila | **exento** — persiste lo que dijo la fuente |
| `FakeFuelStationsSource:98-99` | fake | **arreglado** — deja de pricear HYBRID/PHEV, que ninguna fuente real emite |
| `OpenVanFuelPricesParser:80` `prices[key]` | JSON → tabla | **exento** — no es este mapa |

## Verificado

**Suite:** 2.994 tests, 0 fallos, 0 skipped. `:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin`
verdes.

**Testigo, no decorado.** Falsificada la invariante (`HYBRID, PLUG_IN_HYBRID -> this`) caen **8**:
los cinco nuevos —`FuelTypeTest` ×2, `GetNearbyFuelStationsUseCaseTest` ×3,
`NearbyOrderingTest`, `HomeSlicesTest`— y además
`ResolveFuelPriceUseCaseTest > should_priceBothEnergies_forAPlugInHybrid`, que es el testigo de que
el dedupe de `AverageFuelPriceTable` conserva la conducta que ya tenía.

**En device** — Pixel 8 Pro (Android 37), `geo fix 13.4050 52.5200`, coche activo Aiways U6 HYBRID,
sha256 device ↔ local `df5dd022…`:

- **Fila:** `Shell 2.349 EUR · ARAL 2.349 · ARAL 2.339 · TotalEnergies 2.319 …` — antes, en blanco.
- **Marcador:** cápsula `2.349 / Gasoline` — la cifra y su sujeto, los dos ciertos para un híbrido.
- **Modal:** sigue itemizando `Gasoline 2.349` + `Diesel 2.399` + «Prices update in real time»,
  intacta — era la única superficie que ya preguntaba por productos.

Las cifras cuadran con la llamada directa a `list.php` para Berlín (E5 2,329–2,349), así que lo que
se pinta es el feed, no una caché vieja.

## Fuera de alcance

El peek no destaca cuál de los surtidores que lista es **el del coche que conduces**: para un
híbrido, «Gasoline» y «Diesel» se leen igual de relevantes. Es una decisión de producto, no este
bug, y la modal nunca estuvo rota.
