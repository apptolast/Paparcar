# FUEL-A-FEED-QUOTES-EACH-FUEL-IN-ITS-OWN-UNIT-001 · Cada combustible trae su unidad, y leíamos la de la fila

**Estado:** 🔵 En progreso · rama `bugfix/FUEL-A-FEED-QUOTES-EACH-FUEL-IN-ITS-OWN-UNIT-001-per-fuel-unit`
· worktree `../Paparcar-fuel-unit`

## Problema

OpenVan publica **dos** declaraciones de unidad por país: `unit` en la fila y `units` **por
combustible**. Leemos solo la primera — `fuelaverages.py:115` (`factor = LITRES_PER_UNIT.get(
country.get("unit"))`) y, con el mismo gesto, `OpenVanFuelPricesParser:73`.

No coinciden, y no por capricho del feed: **el GNC se vende por kilo o por metro cúbico** y el GLP
por kilo en varios países, porque son gases. Consultado el feed el 16-09-2026: **18 filas** mezclan
unidades y en **7 leemos de verdad un precio mal uniteado**.

| País | Lo que leemos | Unidad real | Se guarda como |
|---|---|---|---|
| AR · BO · BR | CNG, vía el fallback `lpg ?: cng` | **m³** | precio por litro |
| IN | CNG, mismo fallback | **kg** | precio por litro |
| GH · PK · TH | LPG | **kg** | precio por litro |

Brasil: `4,8098 BRL/m³` acaba guardado como BRL/litro. Argentina, `799 ARS/m³`. **No es un error de
escala, es de categoría**: un metro cúbico de gas no es un litro de líquido, y no hay factor que los
convierta sin saber la presión. Un kilo de GLP tampoco: son ~1,96 litros, pero el surtidor cobra el
kilo y quien conduce paga el kilo.

Gasolina y diésel **nunca** se ven afectados: siempre vienen en la unidad de la fila. El daño se
limita a coches de GLP/GNC en esos siete países, donde el gasto del periodo sale con un precio que
no es por litro.

## Doctrina violada

El KDoc del propio parser ya lo promete y **no lo cumple**: *«a unit neither of them knows is
DROPPED, never assumed to be litres»*. Es verdad para la unidad de la FILA y mentira para la del
combustible — que es la que manda cuando existe. La promesa estaba escrita contra la mitad del
problema.

Misma familia que [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001], un piso más abajo: allí la
unidad se leía mal al SALIR a pantalla; aquí se lee mal al ENTRAR del feed. Y el mismo principio la
resuelve — **la unidad la declara quien vende, no quien lee**.

## Señales / datos disponibles

Del feed, tal cual (16-09-2026):

```json
"ES": { "unit": "liter", "units": { "cng": "kg", "lpg": "liter", … }, "prices": { "lpg": 1.131, "cng": 1.804 } }
"BR": { "unit": "liter", "units": { "cng": "m3",  … },               "prices": { "lpg": null,  "cng": 4.8098 } }
"US": { "unit": "gallon","units": { "gasoline": "gallon", … },       "prices": { "gasoline": 4.2021 } }
```

España enseña por qué el fallback existe y por qué hoy no muerde ahí: tiene GLP en litros, así que
nunca llega al GNC. Brasil no tiene GLP, cae al GNC y se traga los metros cúbicos.

## Diseño

**La unidad de un precio es la de SU combustible, con la de la fila como respaldo.** Un solo cambio,
el mismo en los dos bordes, porque son el mismo borde escrito dos veces:

```
unidad(fuel) = units[fuel] ?: unit          # el respaldo, no al revés
factor       = LITRES_PER_UNIT[unidad(fuel)]  # null ⇒ ese combustible es null
```

- **La unidad desconocida cae el COMBUSTIBLE, no el país.** Hoy una unidad de fila desconocida tira
  la fila entera, y eso se queda: sin gasolina ni diésel no hay país que valer. Pero que Brasil venda
  el GNC por m³ no invalida su gasolina, y dejarlo sin precio de gas es la respuesta honesta — la
  misma que el KDoc de `CountryFuelPrices` ya da para un país que no vende GLP: *«a missing fuel is
  null and stays null»*.
- ⛔ **No se convierte kg ni m³ a litros.** Tentador y falso: un m³ de GNC depende de la presión, y
  un kilo de GLP se cobra como kilo. Inventar el factor sería exactamente el fallo que este ticket
  arregla, con más decimales.
- El fallback `lpg ?: cng` **se queda**: sigue siendo cierto que los dos son `FuelType.LPG_CNG` y que
  el GLP es el que un coche encuentra. Lo que cambia es que ahora el GNC solo contesta si viene en
  una unidad que sabemos leer.

### Efecto medido del cambio

El generador lo dice en voz alta al correr, que era parte del arreglo — una decisión que sólo se ve
aquí no es una decisión, es un recorte silencioso:

```
  162 countries, as of 2026-09-12
  AR: no litre price for cng -- the feed quotes it per 'm3'
  BO: no litre price for cng -- the feed quotes it per 'm3'
  BR: no litre price for cng -- the feed quotes it per 'm3'
  GH: no litre price for lpg -- the feed quotes it per 'kg'
  IN: no litre price for cng -- the feed quotes it per 'kg'
  PK: no litre price for lpg -- the feed quotes it per 'kg'
  TH: no litre price for lpg -- the feed quotes it per 'kg'
```

Los siete previstos, ni uno más. **Ninguno pierde gasolina ni diésel**; sólo el gas pasa a `null`.
España **no** aparece, y es la comprobación que importa: declara `cng` en kilos, pero tiene GLP en
litros, así que el fallback nunca llega al gas y no hay nada que descartar.

Al regenerar, el fichero embebido además refresca los precios (161 países → 162, del 05-09 al 12-09);
es lo que hace el generador y se dice aquí para que el diff no sorprenda. El control es EE. UU.:
`1.0845 → 1.1101` de gasolina, que es exactamente el `4.2021/gal` que publica hoy el feed dividido
por 3,785411784 — y su GLP se queda en `0.9035`, porque allí el gas **sí** se vende por galón y su
`units` lo dice.

## Criterio de éxito

- Ningún precio guardado en una unidad que no sea el litro, **con un test que lo mire por cada forma
  en que el feed lo declara** (`kg`, `m3`, y la fila en `gallon`, que debe seguir funcionando).
- Gasolina y diésel intactos en los 161 países.
- `./gradlew testAll` en verde. ✅

## Estado

✅ Implementado y verde. Cuatro tests nuevos en `OpenVanFuelPricesParserTest`, uno por cada forma en
que el feed lo declara: `m3` en el fallback de GNC, `kg` en el GLP, el caso ESPAÑOL donde la unidad
rara está en un combustible que nunca se lee, y —el que fija la dirección— una fila en litros con el
diésel en galones, que sin el arreglo saldría ×3,79 alto.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `OpenVanFuelPricesParser` (borde de red) | **Cerrado** — es el cambio |
| `tools/fuelaverages/fuelaverages.py` (borde del generador) | **Cerrado** — el mismo cambio, misma razón |
| `EmbeddedAverageFuelPrices.kt` | **Regenerado** — es salida del anterior |
| `AverageFuelPriceTable.averageLitre` | **Exento** — ya sólo conoce litros; ése es su invariante |
| `StaticAverageFuelPrices` (kWh) | **Exento** — escrito a mano, sin feed y sin unidad ambigua |
| `ItalyStationParser`, `MineturStationParser` y los otros feeds nacionales | **Auditados aparte**: cotizan por litro y ninguno publica un mapa de unidades |
| `FuelType.LPG_CNG` / `pumpProduct` | **Exento** — el producto no cambia, sólo si tiene precio |
