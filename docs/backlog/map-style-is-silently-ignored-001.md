# MAP-STYLE-IS-SILENTLY-IGNORED-001 · El estilo del mapa no se aplica, y por eso vuelven los POI ajenos

**Estado:** 🟡 EN DUDA — todo lo de abajo se midió **en el emulador**, y el user dice que **en su
móvil físico el mapa NO muestra POI** (09-09-2026). Si eso se confirma, el estilo sí se aplica en
hardware real y esto es una diferencia del renderer del emulador (Maps SDK tiene dos, y el
emulador puede caer en el legacy), no un bug de la app.

⛔ **Primer paso antes de tocar NADA: reproducirlo en un móvil físico.** Una captura del mapa en el
Oppo con sesión iniciada zanja el ticket en un sentido o en el otro; hasta entonces esto es una
hipótesis con evidencia de un solo entorno — y el entorno es justo el que suele mentir.

Ticket abierto desde la verificación de la capa de gasolineras.

## Problema

El user recordaba haber quitado los POI de Google del mapa «porque pueden confundirse con nuestro
producto». El estilo **sí lo pide** — `MapStyles.kt` lleva `{"elementType":"labels.icon",
"stylers":[{"visibility":"off"}]}` y `{"featureType":"transit","stylers":[{"visibility":"off"}]}`
en AMBOS temas — pero en pantalla salen píldoras moradas de museos, iconos de metro/RER y demás
POI de Google.

## Lo medido (no deducido)

Muestreando píxeles de una captura del mapa en oscuro (París, prodDebug del 09-09):

| Qué | Lo que pide `DARK_MAP_STYLE` | Lo que pinta la pantalla |
|---|---|---|
| Agua | `#000000` → (0,0,0) | (62,90,119) · (28,42,64) — **azul** |
| Vías | `#2c2c2c` → (44,44,44) gris neutro | (27,40,62) · (34,48,78) — **azul** |

Ni un valor se acerca. **El JSON no se está aplicando**: lo que se ve es el esquema oscuro NATIVO
de Google Maps, y con él vuelven sus POI, su transporte y sus colores.

## Confirmado con un experimento en device (09-09-2026)

Se sustituyó el estilo por uno de **solo visibilidad, cero colores** (`labels.icon: off`,
`poi/labels: off`, `transit: off`) y se miró en el emulador:

- El mapa mantuvo **el tono nativo azulado** — o sea que el scheme siguió mandando.
- Y **los POI siguieron ahí**: rombos de metro, «Museo de las Ilusiones» en su píldora morada, el
  Monasterio de las Descalzas… con reglas que los apagaban explícitamente.

→ **El JSON se ignora ENTERO cuando se declara `mapColorScheme`.** No es un problema de colores ni
de un JSON mal formado (validado aparte: 18 y 21 reglas, sintaxis correcta): sencillamente no se
aplica. El experimento se revirtió; nada de esto quedó en el código.

## Causa (confirmada)

`kmp-maps` pasa **las dos cosas a la vez** (leído en las fuentes de nuestro fork
`io.github.rndevelo.kmpmaps:core:0.9.1-puck4`, `Map.kt:151` y `Extensions.kt:154`):

```kotlin
GoogleMap(
    mapColorScheme = properties.mapTheme.toGoogleMapsTheme(),   // ComposeMapColorScheme.DARK
    properties = properties.toGoogleMapsProperties(...),        // incluye mapStyleOptions(JSON)
)
```

En el SDK de Maps, el **color scheme** y el **JSON styling** no conviven: al declarar un scheme, el
estilo JSON queda fuera. Nuestro `MapTheme` siempre resuelve a un scheme (`LIGHT`/`DARK`/
`FOLLOW_SYSTEM`) — no hay «ninguno» — así que el JSON nunca gana.

⚠️ **Confirmarlo con un experimento controlado antes de tocar nada**: un JSON con un valor
imposible de confundir (agua en rojo) y ver si la pantalla cambia. Un arreglo sobre una causa
supuesta es cómo se tapa un síntoma.

## Las tres salidas, con lo que cuesta cada una

**El user ha decidido lo que QUIERE (09-09): el tono actual —el nativo azulado— pero SIN los POI.**
Eso descarta la salida A y deja dos caminos reales:

| | Qué hace | Qué cuesta |
|---|---|---|
| **A. JSON completo, sin scheme** | En el fork, no declarar `mapColorScheme` cuando hay JSON → nuestro estilo se aplica y los POI desaparecen | ⛔ **Descartada por el user**: el mapa pasaría al gris neutro de `MapStyles.kt`, no al azulado que le gusta |
| **B. Repintar el JSON imitando el nativo** | Un estilo propio que reproduzca el tono azulado + POI apagados, y quitar el scheme | Trabajo de diseño (muestrear y replicar el tema de Google) + tocar el fork. Y quedamos atados a imitar un tema que Google puede cambiar |
| **C. Cloud styling (Map ID)** | Estilo en la consola de Google Cloud: se elige esquema Y se apagan los POI, y convive con el scheme porque ES el mecanismo actual | Crear el Map ID en `pap-26`, configurarlo, y que kmp-maps acepte `mapId` (nuestro fork otra vez). Es la vía que Google empuja — el JSON styling está en camino de legacy |

**Recomendación: C.** Es la única que da exactamente lo pedido (tono nativo + sin POI) sin
mantenernos un tema imitado, y de paso nos pone en el mecanismo que Google mantiene. B es el plan
de respaldo si el Map ID se complica.

⛔ Sea cual sea: **`MapStyles.kt` no puede quedarse como está**. Un estilo que no se aplica hace
creer que el mapa está bajo control cuando no lo está — o se conecta, o se borra.

## Por qué importa (más allá de la estética)

Los POI de Google son **píldoras con icono**, igual que nuestros marcadores de zona y las cápsulas
de gasolinera. Tres lenguajes de píldora en el mismo mapa, y solo dos son nuestros: es exactamente
la confusión que el estilo existía para evitar.
