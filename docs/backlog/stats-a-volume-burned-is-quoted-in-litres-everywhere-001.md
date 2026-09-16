# STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001 · Los totales del periodo no cambiaban de unidad nunca

**Estado:** ✅ Implementado y verde · rama
`feature/STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001-volume-and-mass` · worktree
`../Paparcar-burned-volume` · ⏳ sin ver en mano

## Problema

`stats_value_liters` («%1$s L») era la ÚNICA magnitud de volumen sin borde de unidad. Con el ajuste
en imperial el consumo ya se enseñaba en **mpg** y con locale de EE. UU. el precio ya se cotizaba
**por galón**, pero el depósito gastado seguía diciendo «142 L» en los nueve locales y en los dos
ajustes.

**Y al auditarlo no era una cifra, eran dos**: `stats_value_kg` (el CO₂) tiene exactamente el mismo
agujero, y no hay **ninguna** conversión de masa en el repo — ni libras, ni factor, ni allowlist. El
kWh es el único de los cuatro tiles que legítimamente no necesita borde.

Es el último de la serie: una cifra correcta bajo una unidad que ese lector no usa.

## Por qué no entró en ninguno de los dos tickets anteriores

- [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001] cerró las magnitudes POR DISTANCIA (l/100 km ⇄
  mpg): un total gastado no es una magnitud por distancia, es un volumen suelto.
- [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001] cerró el DINERO por volumen. Un volumen a secas
  no es un precio, y ampliar aquel encargo a esto habría sido cambiar el alcance por mi cuenta.

## ✅ La pregunta abierta, resuelta: manda el eje del CONSUMO, no el del surtidor

El doc anterior la dejaba para decidir. **La decide la aritmética, no el gusto.**

Los litros quemados **son el consumo integrado sobre la distancia** (`km × consumo/100`), así que las
tres cifras tienen que CERRAR en la misma pantalla: *distancia ÷ consumo = volumen*.

Un británico con el ajuste en imperial lee **«1.234 mi»** y **«51 mpg»**. 1234 ÷ 51 = **24,2 galones
imperiales**. Si el volumen usara el galón del SURTIDOR (`localePumpGallon()`, que en el Reino Unido
es `null` porque allí se reposta en litros) leería «142 L» al lado y la fila no cerraría.

Así que el eje es **el ajuste imperial + `localeGallonKind()`** — el mismo par que ya usa
`consumptionFigure` — y **no** `LocalPumpGallon`. Consecuencia que se acepta a propósito: en el Reino
Unido la app cotiza el surtidor en litros y el depósito gastado en galones imperiales. Suena raro y
es correcto, porque contestan dos preguntas distintas, que es justo lo que `LocaleUnits.kt` separa.

Y la masa **no tiene galón**: una libra es una libra en Detroit y en Kent, así que el CO₂ sólo
depende del ajuste y no pregunta por el gas.

## Diseño

`presentation/util/StatTotalsFormat.kt` — el TERCER borde de unidad de la casa y el último que
faltaba, hermano de `ConsumptionFormat` (magnitud por distancia, se invierte) y `PumpPriceFormat`
(dinero por volumen, factor limpio). Éstas son magnitudes a secas, acumuladas sobre el periodo:

```kotlin
fun burnedVolumeToDisplay(litres: Double, unit: DistanceUnit, gallon: GallonKind): Double
fun emittedMassToDisplay(kg: Double, unit: DistanceUnit): Double
```

⛔ **No hay inversa, y la ausencia es la nota.** El consumo y el precio la necesitan porque el usuario
los TECLEA y el formulario se los devuelve; estos dos sólo se leen — se derivan de los kilómetros
medidos y de lo que ya declaró. Una `fromDisplay` aquí sería una puerta por la que sólo puede entrar
una unidad equivocada.

**El dominio no se entera**, como en los otros dos: `PeriodStats.fuelLiters` sigue en litros y
`co2Kg` en kilos. Y la **delta no se convierte**: `statDelta` es una RAZÓN (porcentaje o «×N»), y una
razón no tiene unidad — convertir sus dos lados daría exactamente el mismo número.

## Criterio de éxito

- Con el ajuste en imperial, los cuatro tiles cierran entre ellos: *millas ÷ mpg = galones*.
- En métrico no cambia ni un carácter.
- `./gradlew testAll` en verde. ✅

### Tests

`StatTotalsFormatTest`, y el que importa no es ninguna conversión suelta —eso es un factor y no se
equivoca nadie— sino **la fila entera**: se parte de km y l/100 km, se computan los litros como los
computa el dominio, se convierten las tres a imperial y se comprueba que `millas ÷ mpg` da los
galones, **con los dos galones**. Con el galón equivocado la igualdad se rompe por un 20 % y ninguna
cifra parece absurda: simplemente la fila deja de cuadrar. Ése es el fallo que este test caza.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `VehicleStatsBlock.statTiles` (tiles FUEL y CO2) | **Cerrado** — y es el ÚNICO call site: `statTiles(` sale una vez en todo el repo |
| `PeriodStats.fuelLiters` / `co2Kg`, `ComputePeriodStatsUseCase` | **Exentos** — son el dominio: litros y kilos |
| `statDelta` | **Exento, y medido**: devuelve una razón; un porcentaje no tiene unidad que convertir |
| `StatTileKey.ENERGY` (kWh) | **Exento** — un kWh es un kWh en los 195 países |
| `HistoryWeeklyChart` / `ChartMetric` | **Exentos**: sólo grafican PARKINGS, DISTANCE y SEARCH_TIME — los litros y el CO₂ no se grafican nunca, por [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001] |
| `RunMonthRecapUseCase` y su notificación | **Exento** — no nombra ni litros ni kilos; sólo pasa el precio al agregador |
| `FakeOtherDataSources` (`useImperialUnits`) | **Cerrado** — arrancaba en métrico pasara lo que pasara; ahora el mismo default que producción |
| `StatsScreen` | **Exento** — pinta el bloque, no las unidades |

## Y el fake que escondía el carril entero

`FakeOtherDataSources` arrancaba `useImperialUnits` en `MutableStateFlow(false)`, **ignorando el
locale**, mientras producción lo resuelve con `localePrefersImperialUnits()`
(`AndroidDataStoreAppPreferences:345`, `IosAppPreferences:371`, las dos plataformas).

Se vio en el emulador **en-US** cerrando el ticket del galón: la ficha del coche pedía «Consumption
(l/100 km)» en un móvil estadounidense, que no es lo que hace la app instalada. O sea, el flavor mock
—que es DONDE se prueban las pantallas— dejaba invisible el carril imperial entero: mpg, millas, y
ahora galones y libras. **Un fake que miente sobre el arranque no simula, esconde**, y encima esconde
justo lo que este ticket añade.

Corregido al mismo default que producción. No es ampliar el encargo: es que sin ello lo que entrega
este ticket no se puede probar en el único sitio donde se prueban las pantallas.

⚠️ **La variante de la galería se queda igualmente**, y no es redundante: el fake ahora dice la
verdad sobre el móvil que lo corre, así que en un teléfono español sigue —correctamente— en métrico.
Proveer `LocalDistanceUnit` a mano es lo que permite mirar el carril imperial sin cambiarle el idioma
al dispositivo.
