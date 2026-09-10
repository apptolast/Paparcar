# FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001 · la tabla lleva su fecha y nadie la lee

**Estado:** ✅ Done · rama `feature/FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001-dated-average` ·
worktree `../Paparcar-dated-average`

## Problema

`AverageFuelPriceTable` transporta un `asOf` desde que se creó, y un `grep` de `.asOf` sobre
producción **no devuelve una sola línea**. Es exactamente el mismo defecto que
`STATS-A-COMPUTED-LITRE-WITH-NO-READER-001`: un campo que viaja sin lector.

Aquí importa más por lo que significa. Hay dos formas de envejecer y **ninguna se comprueba**:

1. **El snapshot embebido envejece con la release.** Hoy va `AS_OF = "2026-09-05"`. Si pasan seis
   meses sin publicar, la app cotiza precios de hace seis meses con exactamente la misma cara que
   uno de hoy.
2. **El refresco vive en memoria** y muere con el proceso, así que cada arranque en frío vuelve al
   snapshot del binario hasta que la red conteste.

Y la línea que acompaña a la cifra dice, literalmente:

> *«Spend estimated with your country's average price.»*

Sin fecha. Ninguna.

## Doctrina violada

La escribió el propio fichero que esto sustituyó, y la incumplimos:

> ⛔ *«Every figure here carries `VALID_AS_OF` and the UI says "average". **A price with no date is a
> number someone made up**: fuel moves weekly…»*

Llevamos la fecha y no la enseñamos. Es la misma trampa que dejó la tabla estática un **20 %** por
debajo del Ministerio durante meses: no fue que el dato fuera malo, fue que **nadie miraba cuándo**.

## Diseño

**La fecha viaja DENTRO del precio, no al lado.** `FuelPrice` ya existe para eso — es el objeto de
procedencia, y su KDoc dice que la procedencia viaja con el número «porque una cifra que no puede
describirse a sí misma acaba descrita por quien la lea». Le falta la mitad temporal.

```kotlin
data class FuelPrice(
    val perUnit: Money,
    val source: FuelPriceOrigin,
    /** Fecha ISO en que se midió, cuando la fuente sabe fecharse. Null para el precio del usuario. */
    val measuredOn: String? = null,
)
```

⛔ **Un solo campo aguas arriba, no dos.** La presentación pasa a llevar `costPrice: FuelPrice?` en
vez de `costOrigin: FuelPriceOrigin?`. Origen y fecha salen del MISMO objeto y no pueden discrepar —
dos campos paralelos sí podrían, y este repo ya tiene cicatrices de eso.

**Se dice la EDAD, no la fecha.** Reutiliza `relativeTimeText`, que ya existe y ya está traducido a
los 9 locales («hace 5 días» / «5 days ago»). Responde mejor a la pregunta real —*¿cómo de vieja
es?*— que un «5 sep» que obliga al lector a hacer la resta, y evita inventar un formateador de meses
para nueve idiomas.

Copy: `stats_cost_from_average` pasa a llevar un `%1$s` con esa edad. El precio del usuario **no**
cambia: lo suyo no tiene fecha porque no la tiene.

### Lo que este ticket NO hace

**No se niega a cotizar** una tabla vieja. Se consideró (encaja con «mejor falso negativo que falso
positivo») y se descarta: dejaría sin cifra a quien no abre la app a menudo, que es precisamente
quien más agradece una estimación. Decirlo basta — el lector pondera solo, igual que hace con
`ScopedDistance`, que ya viaja con su cobertura pegada.

## Criterio de éxito

- La línea bajo el gasto dice cuándo se midió la media, en los 9 idiomas.
- El precio del usuario sigue sin fecha, porque no la tiene.
- Origen y fecha vienen del mismo objeto: no hay forma de que discrepen.
- Galería en paridad.

## Consumidores a barrer

`FuelPrice` · `ResolveFuelPriceUseCase` · `AverageFuelPriceTable` (expone su `asOf`) ·
`HistoryState.Resolved` · `HistoryContent` · `StatsState` · `StatsViewModel` · `StatsScreen` ·
`VehicleStatsBlock` · `StateGalleryScreen`.

## Verificado en el Redmi (en español)

Dev Catalog, `sha256` device↔local idéntico. La línea bajo el gasto dice:

> **«Gasto estimado con el precio medio de tu país, medido hace 5 días.»**

El snapshot embebido lleva `AS_OF = "2026-09-05"` y hoy es el 10 — la edad cuadra, y se recalcula
sola porque `relativeTimeText` ya se re-renderiza cada minuto.

### ⛔ Hallazgo: los fakes reproducían el bug de producción

La galería **nunca había podido enseñar el tile de gasto**, y no por el gate de Plus: las seis
`AddressInfo` de `FakeData` se construían **sin `countryCode`** — exactamente la misma forma que
`FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001` tenía en producción. Sin país no hay precio, sin
precio no hay tile, y sin tile no hay línea de origen que vigilar.

Un fake que no puede reproducir la pantalla no la vigila. Corregido (`countryCode = "es"`), y con
eso la galería enseña por primera vez el coste: `1.20 EUR`, que cuadra con lo demás —
0,4788 L × 1,8544 €/L + 1,995 kWh × 0,16 €/kWh = 1,207 €.

Los litros y los kWh del enchufable siguen cuadrando: 11,4 km × 4,2 l/100 = 0,4788 → `0.5 L`;
11,4 km × 17,5 kWh/100 = 1,995 → `2 kWh`.
