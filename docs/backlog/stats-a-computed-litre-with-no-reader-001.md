# STATS-A-COMPUTED-LITRE-WITH-NO-READER-001 · los litros y los kWh se calculaban y no los pintaba nadie

**Estado:** ✅ Done · rama `feature/STATS-A-COMPUTED-LITRE-WITH-NO-READER-001-show-consumption` ·
worktree `../Paparcar-consumption`

## Problema

El user, mirando la ficha del coche en el Oppo: *«no veo ni el gasto ni el consumo medio»*.

El gasto era otra cosa y ya está arreglado (`FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001`).
El consumo es esto, y es más simple: **no falla ningún cálculo, es que no existe la superficie.**

`ComputePeriodStatsUseCase` calcula y expone dos campos:

```kotlin
/** Litres burned over the MEASURED distance (override or FuelDefaults per session's vehicle). */
val fuelLiters: Double?,
/** kWh drawn over the measured distance (EV primary + PHEV secondary). */
val energyKwh: Double?,
```

Y un `grep` de `fuelLiters` y `energyKwh` sobre `presentation/` y `app/` **no devuelve nada**.
`statTileKeys()` ofrece ocho claves (COST, CO2, AVG_DURATION, PEAK_HOUR, USUAL_DAY, USUAL_STREET,
AUTO_DETECTED, SPOTS_SHARED) y ninguna es el combustible. Dos campos calculados en cada resolución
de historial, para nadie.

## Doctrina violada

Un campo calculado sin lector es una de dos cosas — o falta la superficie, o sobra el campo — y
**nunca las dos a la vez**. Mantenerlo así paga el cálculo en cada emisión y deja al siguiente
lector adivinando si el hueco es intencionado.

## ⛔ El hallazgo que decide QUÉ se construye

Lo obvio sería un tile de **«consumo medio» en l/100 km**. Sería un número **falso por construcción**.

En `ComputePeriodStatsUseCase`:

```kotlin
val primaryPer100 = vehicle.consumptionPer100 ?: FuelDefaults.defaultConsumptionPer100(...)
val primary = km * primaryPer100 / PER_100_KM
liters += primary
```

Los litros se derivan del consumo declarado. Así que:

```
litros / km × 100  ≡  consumptionPer100
```

…que es **exactamente la cifra que el usuario tecleó** (o el `FuelDefaults` de su tipo de coche), y
que **ya está escrita en la ficha, dos dedos más arriba**: «Diesel · 5,5 l/100 km · C». Un tile de
consumo medio no mediría nada: repetiría una constante disfrazada de medición, que es justo lo que
esta app no hace — la misma razón por la que `PeriodStats` documenta que nada se rellena con un cero
o un guion que aparente ser una cifra.

**Lo honesto es el COMBUSTIBLE GASTADO**: litros (y kWh) del periodo. Eso sí se mueve con los
kilómetros, sí responde al filtro de periodo, y sí es información que no está ya en pantalla.

## Diseño

Dos claves nuevas en `StatTileKey`, no una: un PHEV consume las dos energías y el agregado ya las
lleva por separado — fundirlas obligaría a inventar una unidad común.

| Clave | Se añade cuando | Icono (Nivel 2, Material Rounded) |
|---|---|---|
| `FUEL` | `fuelLiters != null` | `Icons.Rounded.LocalGasStation` |
| `ENERGY` | `energyKwh != null` | `Icons.Rounded.ElectricBolt` |

Orden: **detrás de COST y delante de CO2.** COST sigue encabezando (es lo que ve el tier gratuito, y
eso no se toca), y el combustible es la magnitud de la que sale el coste, así que se lee antes que
las emisiones.

Nada que tocar en dominio: los campos ya existen, ya se calculan solo sobre distancia MEDIDA y ya
son null por debajo de su umbral. Este ticket es **solo** presentación + strings.

## Criterio de éxito

- Un coche de gasolina/diésel con ruta medida enseña litros; uno eléctrico enseña kWh; un PHEV
  enseña **los dos**; un coche sin ruta medida no enseña ninguno.
- COST sigue siendo el primer tile — el tier gratuito no cambia de cifra.
- Strings en los 9 locales.
- Galería del Dev Catalog en paridad.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `domain/stats/PeriodStats` | ✅ sin cambios — los campos ya estaban |
| `domain/usecase/stats/ComputePeriodStatsUseCase` | ✅ sin cambios |
| `presentation/vehicles/VehicleStatsBlock` | 🔧 2 claves, 2 iconos, 2 ramas, 2 labels |
| `presentation/stats/StatsScreen` | ✅ deriva de `statTiles()`, hereda solo |
| Strings | 🔧 4 keys × 9 locales |

## Verificado en device

**Oppo**, `prodRelease` con R8, `sha256` device↔local idéntico. El tile **`FUEL USED`** aparece
exactamente donde se diseñó: **entre `APPROX. SPEND` y `APPROX. CO₂`**. El coste sigue encabezando,
así que el tier gratuito no cambia de cifra.

Coherencia aritmética con lo que ya había en pantalla: 118,3 km × 5,5 l/100 = **6,5065 L** → el tile
redondea a `6.5 L`, y 6,5065 × 1,8544 €/L = **12,07 €**, que es justo el coste mostrado. Las dos
cifras salen del mismo litro, así que no pueden discrepar.

### El Redmi no servía, y el motivo es información

Su coche («Redmi Test») **no declara combustible** — su línea no dice «Diésel · 5,5 l/100 km» como
la del Oppo. Sin combustible no hay litros, ni CO₂, ni coste: su primer tile es «DURACIÓN MEDIA».
Comportamiento correcto (`PeriodStats` no rellena con ceros lo que no sabe), pero por eso ahí no se
puede ver esta pantalla.

⛔ **El Dev Catalog no se pudo instalar en el Redmi**: MIUI responde
`INSTALL_FAILED_USER_RESTRICTED` a un paquete NUEVO por USB (actualizar uno existente sí pasa). Hace
falta que el user lo autorice en pantalla; no se tocan ajustes del móvil por nuestra cuenta.
La variante «Enchufable · litros Y kWh» de la galería queda por ver en mano — es el único caso que
enciende los dos tiles a la vez.
