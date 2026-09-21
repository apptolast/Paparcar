# HOME-A-PINNED-HEADER-NEEDS-ITS-OWN-FLOOR-001 · Una cabecera fijada necesita su propio suelo

**Estado:** 🔵 En revisión · rama `bugfix/HOME-A-PINNED-HEADER-NEEDS-ITS-OWN-FLOOR-001` ·
cierra la **issue #86**

## Problema

Regresión introducida el 21-09 por `PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001` (#43) y
`FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001` (#42): los chips de filtro flotan **sin fondo** y
la lista se desplaza **por debajo**. En la captura del user, «Free / Paid / Covered» y
«Car park · 2.0 km» se leen **a la vez**, superpuestos.

## Causa raíz

Un `stickyHeader` de Compose **no trae fondo**. Flota sobre el contenido y el contenido pasa por
detrás.

Mientras el bloque fijado fue **una sola fila de pestañas** no se notó: los tabs son texto ancho y
opaco y tapan lo suficiente. Al añadir una segunda fila de **chips redondeados**, el hueco entre
chips se convirtió en una ventana a las filas que pasan.

⚠️ O sea que el defecto **no lo introdujo el ticket de los filtros**: lo destapó. La cabecera llevaba
sin suelo desde que existe, y hasta ahora su contenido lo disimulaba.

## Fix

El `Column` del bloque fijado pinta `surfaceContainer` — **el mismo color que el `Surface` del
bottom sheet**, no uno propio. Así la cabecera no se lee como una tarjeta flotando encima, sino como
la parte de la hoja que no se va.

## Lo que NO lleva, y por qué

⛔ **Sin divisor inferior nuevo.** Ya hay un `PapDivider()` en la frontera peek↔lista, alineado al
píxel con el de la bottom-nav (`BUG-PEEK-DIVIDER-ALIGN`), y con la lista arriba del todo el segundo
caería a dos filas de él. El fondo opaco **ya resuelve el bug reportado**; el divisor es una decisión
de estilo distinta, y se decide viendo la pantalla, no leyendo el código.

⛔ **Sin sombra.** La hoja ya tiene la suya (`SHEET_SHADOW_ELEVATION_DP`) y una segunda dentro de la
misma superficie se lee como dos planos donde sólo hay uno.

## Criterio de éxito

Ninguna fila se ve nunca a través de la cabecera, en claro y en oscuro, con los tres carriles.

## Pendiente

⏳ **Device.** Es un cambio puramente visual y no hay móvil conectado: la captura del estado final
que pide `PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001` queda a deber.
