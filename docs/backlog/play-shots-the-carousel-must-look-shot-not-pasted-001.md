# PLAY-SHOTS-THE-CAROUSEL-MUST-LOOK-SHOT-NOT-PASTED-001 · el carrusel parecía una captura pegada, no una foto

**Estado:** ✅ Done

## Problema

El user, mirando la ficha: *«parecen muy oscuras, no sé, un fondo verde a lo mejor, no resaltan las
pantallas con el fondo»*.

Tenía razón y la causa **no era la que parecía**. Medido antes de tocar nada:

| | valor |
|---|---|
| Fondo del lienzo | `#0A100E` — casi negro, con destellos verdes al **12-20 %** |
| Pantalla de la app dentro | `#0D1117` — casi negro |
| Separación entre ambos | un filo blanco al **9 %** y `box-shadow: rgba(0,0,0,.66)` |

**Una sombra negra sobre negro no existe.** No faltaba verde: faltaba **diferencia de luminosidad**.
El fondo y el aparato eran el mismo negro y lo único que los separaba era un pelo blanco.

## El segundo síntoma, y por qué NO era lo que decía

El user añade: *«ajusta bien la captura al dispositivo, la pantalla no encaja muy bien»*. Antes de
ajustar nada lo medí, y **el encaje no estaba roto**:

- los 11 raws son **exactamente 1080×2400**, la relación que el compositor asume;
- `b = screen*0.0215`, `r = screen*0.088`, radio interior `r − b` → **los radios SON concéntricos**;
- las cajas de las tres formas **caben todas** en el lienzo 1080×1920 (el frame 1 acaba en y=1857,
  el delantero de una pareja en y=1801);
- lo que yo leí primero como «banda negra inferior» resultó ser **la sombra**, no bisel.

Lo que fallaba era otra cosa: **13 px de bisel sobre 606 no leen como un teléfono**, leen como un
filo gris alrededor de una captura. El aparato no tenía volumen, así que la pantalla parecía pegada
encima en vez de metida dentro.

⚠️ Vale la pena dejarlo escrito: *«no encaja»* describía un síntoma real con una causa equivocada.
Medir antes de ajustar evitó mover una geometría que estaba bien.

## Diseño

**1 · El fondo pasa a verde de verdad.** `#0A2E20 → #11422F` en vez de negro con brillos. El tono
separa donde la luminosidad no lo hacía. Elegido por el user entre dos maquetas renderizadas —la
otra era un menta claro que separaba aún más pero se alejaba de la identidad nocturna de la app.
El velo superior del titular también sube de verde: sobre fondo verde, un velo negro apaga el tono
justo donde está el texto.

**2 · El chasis, que es lo que arregla el «no encaja».**

- bisel del **2,15 % al 3,3 %** del ancho dibujado, con su radio en proporción (0,088 → 0,105);
- **canto metálico**: luz rasante arriba-izquierda y rebote tenue abajo-derecha, que es como se
  comporta un borde curvo de metal — antes había una sola pasada al 16 %;
- **junta**: línea oscura donde el cristal se encuentra con el marco. Sin ella el bisel y la
  pantalla se funden y el conjunto vuelve a leerse plano;
- **sombra en los dos sentidos**, no sólo hacia abajo: una sombra de un solo lado hace que el
  aparato parezca pegado, no apoyado. Y un halo verde tenue, que sobre el fondo nuevo sí se ve.

**3 · El cruce, que es lo que el user pidió como «imágenes entrecruzadas».** Las tres parejas
(frames 3, 5, 7) tenían el de atrás a −4° y el de delante **recto**: eso es «dos móviles al lado».
Pasan a **−7° y +2,5°**, con más solape. Comprobado que las cajas rotadas siguen dentro del lienzo.

## Lo que este ticket NO puede verificar, y hay que decirlo

**Los frames 2, 4 y 6 no se pueden previsualizar sueltos.** Su lupa recibe el recorte por
`?anchor=x1,y1,x2,y2`, que mide `shoot.sh` sobre el device y pasa `render.sh`. Al renderizar un
frame a mano cae al `crop` cableado, que ya no cuadra con los raws: el frame 2 amplía un trozo de
mapa vacío en vez de la tarjeta del coche. **No lo causa este cambio** — es la consecuencia de que
no haya anclas guardadas en el repo.

Así que de los 8 frames, este ticket valida **5** (los dos *hero* y las tres parejas) y deja los
tres de lupa a la sesión de capturas.

## Consumidores auditados

| Consumidor | Veredicto |
|---|---|
| `assets/screenshot-frame.html` | **cerrado** — es el único fichero que cambia |
| `assets/feature.html` (banner) | **exento** — tiene su propio fondo y composición; no comparte CSS |
| Los 72 PNG de `screenshots/<loc>/` | **no se regeneran aquí** — necesitan la sesión; el molde ya es éste |
| `tools/render.sh` · `tools/shoot.sh` | **sin tocar** — el rediseño es sólo del compositor |

## Lo que sigue bloqueando la sesión de capturas

[PLAY-THE-STORE-SHOTS-STILL-LIVE-IN-DUBLIN-001] sigue abierto, y con un hallazgo que lo precede:
**`shoot.sh` tiene 2 de sus 3 anclas muertas** tras la mudanza del mundo mock a Madrid —
`"Camden Street, 21"` y `"Northgate Fuel"` ya no existen; sólo sobrevive
`"Drury Street Car Park"`, que además es un nombre dublinés dentro de un Madrid. Hasta arreglar eso
no se puede recapturar, y sin recapturar no se ven los 8 frames finales con este diseño.
