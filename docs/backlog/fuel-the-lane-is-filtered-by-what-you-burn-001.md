# FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001 · Filtros del carril de gasolineras

**Estado:** ✅ Done · master `cda6f03d8` + `1fa6efcd3` · cierra la **issue #42**

## Problema

Pedido por el product owner (#42): *«Aunque el usuario haya elegido un tipo de combustible para su
coche activo, a lo mejor tiene un coche por BT con otro tipo. Deberíamos tener filtros también en
gasolineras: más baratas, más cercanas, diésel, gasolina, GLP… alineándonos con el diseño de filtros
de spots.»*

## Diseño

Dos preguntas distintas en una barra, y se nota que son dos:

- **Orden** — «Más cercanas» (el de siempre) / «Más baratas». ⛔ **Ordena, no filtra**: ninguno
  esconde gasolineras. Un filtro que quitara las caras dejaría al usuario sin saber si es que no hay
  ninguna o es que se la hemos escondido.
- **Producto** — un chip por cada combustible que **esta respuesta cotiza**. Entra por
  `HomeState.pricedFuelType`, que es la ÚNICA definición de «qué echa este conductor», así que
  elegir producto requota **la fila, la cápsula del mapa y el peek a la vez** — exactamente igual
  que cambiar de coche activo.

Detalles que son decisiones:

- ⛔ **Los chips ofrecen lo que HAY** (`fuelProductsAvailable`), no el catálogo de combustibles. Un
  chip «GLP» donde nadie lo vende es una promesa que al pulsarla se convierte en lista vacía, y una
  lista vacía es un veredicto sobre el mundo que no hemos medido.
- ⛔ **Sin chip «Todos»** en producto: una gasolinera no tiene «un precio», tiene el de cada
  producto. Volver al del coche activo se hace pulsando otra vez el chip encendido.
- ⛔ **La barra sólo aparece con respuesta**: encima de «no hemos podido preguntar» haría creer que
  el filtro es la causa de que no haya nada.
- **El orden por precio no aplica el adelanto de la más barata** de [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]:
  ya es la primera por definición, y dos reglas diciendo lo mismo discrepan el día que una cambie.
- **Ninguno de los dos vuelve a preguntar** a Overpass: la tienda es la misma respuesta, cambia cómo
  se lee. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]

## Criterio de éxito

- Con un diésel activo y un GLP por BT, se puede ver el carril cotizado en GLP sin cambiar de coche.
- «Más baratas» reordena, no esconde; las sin precio quedan al final.
- En un país sin feed de precios la barra deja ordenar y no ofrece productos.

## Preguntas (para la revisión)

1. **El producto elegido, ¿debería persistir** entre sesiones, o volver al coche activo al salir?
   Hoy es de la sesión (estado de Home). Persistirlo es una línea en `AppPreferences`.
2. **El divisor vertical entre los dos grupos** es lo que dice que son dos preguntas. Si en device
   se lee mal, la alternativa es una segunda fila — pero cuesta 40 dp de hoja.

## Segunda vuelta (review del user, 20-09)

### 1. El combustible por defecto sale del coche USADO, no del marcado

> «El combustible por defecto que aparezca será el del vehículo usado más recientemente, es lo
> mismo que el orden de vehículos a mostrar en peek y en su row de vehículos.»

`pricedFuelType` resolvía con `vehicles.firstOrNull { it.isActive }`, y **marcar no es conducir**:
quien tiene un diésel de diario y un GLP por Bluetooth veía el carril cotizado por el que dejó
marcado hace un mes. Que es, literalmente, el caso que abrió la issue #42.

Ahora entra por **`vehiclesByRecentUse`**, que reutiliza `vehiclesRowOrder` — el MISMO orden que la
tira de «TUS VEHÍCULOS» y que el stepper del peek: conduciendo primero, luego por sesión aparcada más
reciente, luego por vigilancia. Tres superficies, un solo orden, imposible que discrepen sobre cuál
es «su coche».

⛔ **Y se busca el primero que eche LITROS**, no el primero a secas. ⚠️ Trampa medida al escribirlo:
`pumpProduct` **no es nulo para `ELECTRIC`** —se devuelve a sí mismo—, así que no sirve para
saltárselo; hay que preguntar por `usesLiters`. Con un eléctrico arriba, la primera versión dejaba el
carril cotizando un producto que ninguna gasolinera vende. Un segundo `?:` conserva el
comportamiento de siempre para un garaje entero de eléctricos, donde no hay nada mejor que ofrecer.

**Cinco tests nuevos** en `HomeSlicesTest`, y el primero es el testigo del cambio: con la bandera
`isActive` en el GLP y la sesión reciente en el diésel, el carril tiene que cotizar **diésel**.

### 2. El divisor vertical se queda

> «No sé a qué te refieres, haz la opción más recomendada.»

Se queda. Es lo que dice que la barra hace **dos preguntas distintas** —cómo se ordena y qué producto
se cotiza— y cuesta 1 dp. La alternativa era una segunda fila, que cuesta 40 dp de hoja.

### 3. Los chips sticky: dependen de #43, y no se duplica el mecanismo

> «Chips han de ser sticky.»

⛔ **La fijación no se resuelve en este carril.** La resuelve `LaneFilterBar`, el bloque de cabecera
que [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] (#43) introduce en `HomeSheetContent`, donde
tabs y filtros comparten **un único** `stickyHeader` — y tiene que ser uno solo, porque en una
`LazyColumn` el segundo sticky expulsa al primero y la barra de filtros echaría fuera las pestañas.

Duplicar ese seam aquí sería escribir dos veces la misma decisión y **garantizar un conflicto** al
mergear. Así que `HomeFuelFilterBar` se hace **pública** y se sigue dibujando dentro del carril
mientras #43 no esté dentro. En cuanto lo esté, el cableado es sustituir el
`HomeNearbyLens.FUEL -> Unit` de `LaneFilterBar` por una llamada a esta función y quitar el bloque
del carril — el hueco ya está escrito y comentado en #43.

**Orden de merge obligado: #43 → #33 → éste.**

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin` → BUILD SUCCESSFUL (85 tests en `HomeSlicesTest`,
los 5 nuevos ejecutados y verdes).

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).

## Tercera vuelta — regresión del merge (21-09): la barra se quedó fuera del bloque fijado

Al mergear #43 y #42 seguidas, el cableado que esta PR dejó pendiente **no lo hizo nadie**: el
`LaneFilterBar` de `HomeSheetContent` seguía con su `HomeNearbyLens.FUEL -> Unit` y la barra de
gasolineras se seguía dibujando **dentro** del carril.

Resultado: **plazas y parkings con los chips fijados bajo los tabs, y gasolineras no**. O sea
exactamente la incoherencia que [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] existe para
impedir — dos carriles comportándose distinto en la misma hoja —, colada por la puerta de atrás de
un merge.

**Lo cazó la regresión cruzada, no los tests**: cada PR estaba verde por separado, porque el defecto
no vive en ninguna de las dos sino en su unión. Es el mismo patrón que
[DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001] pagó con dos reglas de residencia.

### Qué se hizo

- `LaneFilterBar` gana su rama `FUEL`, con la condición propia del carril (`stations.isNotEmpty()`).
- `HomeFuelLane` deja de dibujar la barra y **pierde `onSortSelect` y `onProductSelect`**, que ya no
  tiene a quién dárselos.
- **Dev Catalog**: sus dos variantes de filtros pasaban esos parámetros — ⚠️ **lo volvió a cazar el
  compilador del flavor mock**, por segunda vez en este ticket. Ahora componen barra + carril igual
  que la hoja, como ya hacía la de parkings.

`./gradlew testAll :app:assembleProdDebug :app:assembleMockDebug --rerun-tasks` → BUILD SUCCESSFUL,
**3.524 tests, 0 fallos**.
