# FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001 · Filtros del carril de gasolineras

**Estado:** 🔵 En revisión · rama `feature/FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001` ·
cierra la **issue #42** · ⛔ **sale de la rama de #37** (las dos tocan el orden del carril)

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
