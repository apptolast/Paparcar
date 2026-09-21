# HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001 · Un viaje en curso merece el zoom que necesita

**Estado:** 🔵 En revisión · rama `bugfix/HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001`

## Problema

Issue **#66**: *«al estar en estado driving y abrir la app, la cámara no me hace caso y no va a la
localización ni se acerca»*.

## Causa raíz

Dos mitades, y la segunda explica la primera.

**1. El seguimiento recentraba SIN zoom, desde el primer fix.** `followDriver` hacía
`setTarget(lat, lon)` con `zoom = null`, y su KDoc lo defendía: *«recentra sin tocar el zoom, el del
usuario manda»*.

Eso es correcto **mientras se sigue** —nadie quiere que la cámara le deshaga el pellizco cada vez que
llega un fix— pero es falso **en el instante en que el seguimiento arranca**: ahí no hay zoom del
usuario que respetar. Hay el que tuviera el mapa, que al abrir la app en mitad de un viaje es el de
por defecto. La cámara iba al coche; lo que no hacía era acercarse.

**2. Y no quedaba otro camino.** El enfoque inicial de `FOCUS-001` necesita un `userGpsPoint`, que
durante un viaje puede no llegar nunca a Home —la detección es quien tiene el GPS—, así que su rama
`else` nunca corre y `centeredOnUser` se queda en `false`. El único movimiento de cámara en ese
escenario era el seguimiento, que era justo el que no ponía zoom.

## Diseño

**El PRIMER seguimiento de un viaje es un encuadre; los siguientes son tracking.**
`setDriverFollowActive(true)` arma `firstFollowPending`, y el primer `followDriver` posterior aplica
`FOCUS_DRIVING_ZOOM` (16). Los demás siguen sin tocar el zoom.

⛔ Entra como **`ZoomIntent.AtLeast`**, igual que `goToPlace`: es un **suelo**. A quien ya estaba más
cerca no se le aleja. Acercar a quien mira de lejos y respetar a quien ya mira de cerca son la misma
regla, no dos.

⛔ **16 y no 17.** El coche *aparcado* se mira a 17 porque lo que importa es la manzana exacta;
conduciendo hace falta ver por dónde se va.

`FOCUS_DRIVING_ZOOM` vive en el `companion object`, que pasa de `private` a `internal`: son
decisiones de producto y el test que las comprueba tiene que poder **nombrarlas** — repetir el 16
suelto en el test convierte un número en dos.

## Tests

`HomeUiControllerTest`, 3 nuevos:
- **el testigo**: el primer seguimiento pone `FOCUS_DRIVING_ZOOM` y con intención `AtLeast`;
- la mitad que ya era correcta y no se puede perder: seguir NO reencuadra;
- un viaje nuevo vuelve a encuadrar, porque el zoom del anterior no dice nada del que empieza.

⚠️ **Falsificados**: reinyectada la regla vieja (`setTarget(lat, lon)` a secas), **dos de los tres
fallan**.

## Pendiente

⏳ **Verlo en device**: abrir la app en mitad de un viaje y comprobar que el mapa aterriza cerca del
coche. Es un cambio visual sin captura (no hay móvil conectado).
