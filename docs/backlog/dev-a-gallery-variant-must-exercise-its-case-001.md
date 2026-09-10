# DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001 · la variante del enchufable cambiaba la cabecera y dejaba el cálculo con el coche de gasolina

**Estado:** ✅ Done · rama `bugfix/DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001-phev-reaches-the-maths` ·
worktree `../Paparcar-phev-variant`

## Problema

`STATS-A-COMPUTED-LITRE-WITH-NO-READER-001` añadió a la galería la variante **«Enchufable · litros
Y kWh»**, que existe por una sola razón: es el ÚNICO caso que enciende los dos tiles de consumo a la
vez, y no había ningún coche eléctrico en los fakes.

**No los encendía.** Medido en el Redmi con el Dev Catalog: la variante mostraba `0.7 L ·
COMBUSTIBLE` y **ningún tile de energía**.

## Causa

`statsPanelState` sustituía la lista de vehículos del estado… y llamaba al agregador sin pasarle
ninguno:

```kotlin
val resolved = resolvedHistory(owned, filter, isPlus)   // ← sin vehículo
```

Y `resolvedHistory` tiene un **parámetro por defecto**:

```kotlin
vehicle: Vehicle? = FakeData.vehiclesWithStats.firstOrNull()?.vehicle,
```

Así que el enchufable llegaba a la CABECERA (que lee `StatsState.vehicles`) y el CÁLCULO seguía
haciéndose con el primer coche de `FakeData`, que es de gasolina. De ahí los litros a una tasa
equivocada y la ausencia total de kWh.

## Doctrina violada

**Una variante de galería que no ejercita su caso es peor que no tenerla**: se ve verde, ocupa sitio
en la lista y no vigila nada. Y la forma del fallo es conocida — *un parámetro por defecto es una
respuesta silenciosa permanente*, la misma familia que
`UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001` y
`DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001`.

⛔ Y solo se vio **en device**. Compilaba, la suite estaba verde y la variante aparecía en la lista
con su nombre correcto. Nada en el árbol de código decía que estuviera mintiendo.

## Diseño

El coche se **pasa**, no se deja al defecto:

```kotlin
val resolved = resolvedHistory(owned, filter, isPlus, vehicles.firstOrNull()?.vehicle)
```

Una línea, en el único sitio donde `statsPanelState` puede saber qué coche está pintando.

## Criterio de éxito — verificado en el Redmi

Dev Catalog (`mock`), `sha256` device↔local idéntico. La variante ahora muestra **los dos** tiles, y
las cifras salen del coche que la variante dice usar:

| | antes | ahora | comprobación |
|---|---|---|---|
| Combustible | `0.7 L` (tasa del coche de gasolina) | **`0.5 L`** | 11,4 km × 4,2 l/100 = 0,4788 |
| Energía | *no existía el tile* | **`2 kWh`** | 11,4 km × 17,5 kWh/100 = 1,995 |

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `dev/StateGalleryScreen.statsPanelState` | 🔧 pasa el vehículo al agregador |
| `dev/StateGalleryScreen.resolvedHistory` | ✅ el defecto se queda — sirve al resto de llamantes, que sí quieren el coche de FakeData |
| Producción (`StatsViewModel`, `VehiclesViewModel`) | ✅ exento: nunca usaron ese helper, pasan su vehículo real |
