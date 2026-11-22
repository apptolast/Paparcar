# UI-THE-FOUNDERS-GIFT-EARNS-ITS-VOLUME-001 · El regalo de Fundadores se dibuja con volumen, no de frente

**Estado:** ✅ Done · en master (22-11-2026) · rama y worktree cerrados.

## Problema

`FoundersGiftIllustration` entró en master (`53d6fb27b`, [HOME-FIRST-ENTRY-FLOW-001]) y **funciona**:
dice regalo + Black Friday + Paparcar sin una palabra, y se lee en los dos temas. Pero está dibujada
**de frente y plana** — un rectángulo con una cinta encima — y es lo primero que ve una cuenta nueva
en el único diálogo que celebra algo. El user lo pidió explícitamente: *"una más currada, en 3D, más
profesional"*.

Lo que falla no es el concepto, es el **acabado**: no hay ni una cara lateral, ni una sombra
proyectada, ni un solo degradado. La caja no ocupa espacio; está pegada al fondo.

## Doctrina violada

Ninguna — y por eso este ticket es de ACABADO, no de corrección. Lo que sí hay son dos reglas que
acotan la solución y que es fácil romper intentando "profesionalizar":

- **Nivel 3** (CLAUDE.md § Iconos): ilustración = vector propio, multicolor, **no se tiñe**, trae su
  color. Nada de importar un render ni un PNG.
- **Vive en `commonMain`**, así que lo que se use tiene que existir en Compose Multiplatform y
  funcionar en iOS. Canvas sí; VectorDrawable con degradados, no (y además el fichero entero es
  Canvas por la razón escrita en su cabecera).
- ⛔ **La marca se GENERA, no se dibuja a mano.** Si en algún momento se quiere el isotipo sobre la
  caja, se compone `PaparcarMark`, no se trazan sus paths.

## Señales / datos disponibles

- `ui/illustrations/PaparcarIllustrations.kt` — las **cuatro hermanas** (`LocationAlert`,
  `EmptySpots`, `EmptyFuelStations`, `EmptyParkings`), todas en el mismo viewBox `140×120`, con
  `viewBox {}`, `drawShadowEllipse()` y variante oscura por parámetro.
- `ui/components/PapPremiumSurface.kt` — el sistema de metal de Plus ya resuelto: `premiumFinish()`
  da **sweep** (bandas especulares a espaciado desigual), **grain** (micro-ranuras repetidas cada
  2 dp) y **bevel** (la luz cae de arriba). Su cabecera tiene medido lo que costó: *la ESTRUCTURA es
  lo que lee como metal, la AMPLITUD es lo que lee como exagerado, y son dos mandos independientes.*
  La luz de todo el diálogo cae en diagonal **arriba-izquierda → abajo-derecha**.
- Capturas del estado actual en los dos temas, en el scratchpad de la sesión del 22-11
  (`G4-regalo-v3.png` oscuro, `G6-regalo-claro.png` claro).

## Diseño

**Volumen por PROYECCIÓN, no por realismo.** La caja pasa a axonométrica: se ven **tres caras**
—tapa, frontal y lateral—, que es lo que da volumen sin una sola sombra. El sombreado llega después
y sólo para apoyarlo:

1. **Tres caras con tono propio** (tapa más clara, frontal media, lateral más oscura), con la misma
   dirección de luz que el metal de Plus.
2. **Degradado por cara**, suave: `Brush.linearGradient` en Canvas funciona en commonMain. Compresión
   de rango corta, como en `premiumFinish` — estructura sí, amplitud no.
3. **Sombra proyectada** sobre el suelo de la plaza, desplazada en la dirección de la luz, no un
   óvalo centrado. La plaza pasa a **paralelogramo** en la misma proyección.
4. **La cinta envuelve**: cruza el canto de la tapa y continúa por la cara lateral. Hoy se corta.
5. **El lazo con solape**: dos lazadas que se pisan + nudo + dos colas. Hoy son dos formas planas.
6. Opcional, a decidir en device: una **oclusión** fina bajo el saliente de la tapa.

### ⛔ El límite: volumen, no fotorrealismo
Esta pieza convive con **cuatro ilustraciones planas** en el mismo fichero y en la misma app. Un
render con reflejos y profundidad de campo no sería "más profesional": sería una pieza de otra
familia metida con calzador. El objetivo es el registro de un buen *flat 3D* editorial — caras,
proyección y una sombra honesta— conservando paleta, grosores y el viewBox `140×120`.

Si al verlo en device el salto respecto a sus hermanas canta, la salida **no** es aplanar ésta:
es anotar en este doc que las otras cuatro han quedado atrás, y abrir su propio ticket.

## Criterio de éxito

- Se lee **regalo + Black Friday + plaza** igual de rápido que ahora (no perder el concepto por
  ganar acabado) y **sin una palabra**: sigue viajando a los nueve idiomas.
- Volumen visible: se distinguen tres caras y la sombra cae en la dirección de la luz.
- Los **dos temas** verificados en device, no en preview: la caja es negra y el tema claro es donde
  una caja negra se rompe.
- `./gradlew testAll --rerun-tasks` en verde (`ColorGuardrailTest` incluido: `Color(0x…)` sólo es
  legal en `ui/illustrations/`).
- Galería de estados y el preset del regalo siguen enseñándola.

## Consumidores auditados

- `PlusMomentDialog.FoundersWelcomeDialog` → único consumidor, vía `heroIllustration`.
- `PapAlertDialog.heroIllustration` → slot nuevo de [HOME-FIRST-ENTRY-FLOW-001]; **sustituye** el
  plato circular, así que la ilustración no está recortada por ningún círculo y puede crecer.
- `StateGalleryScreen` → la variante del diálogo ya existe; no se añade estado nuevo.
- `PapPremiumCrest` → **no se toca**: sigue viva en el paywall, que es su excepción autorizada.

## ⚠️ Trampas ya medidas (no redescubrirlas)

1. **Geometría, no color.** Los dos fallos de la primera versión se vieron sólo en el device: las
   líneas de la plaza puestas de pie se leían como **patas de un caballete**, y un lazo alto y
   estrecho como **un brote de planta en una maceta**.
2. ⛔ **Anclar los parches por SÍMBOLO, no por una línea de dibujo.** Un parche ancló en
   `drawShadowEllipse(70f, 106f, 40f, 6f, dark)`, que `EmptySpotsIllustration` tiene idéntica, y se
   llevó por delante dos ilustraciones vivas.
3. El acabado metálico **no se dibuja a tamaños pequeños** — medido en `PapPremiumMark`: su finish
   está verificado a 72 dp y por debajo de la mitad las bandas no tienen área que cruzar. Si esta
   ilustración toma prestado el sweep, tiene que ser sobre áreas grandes (una cara entera), nunca
   sobre un filete.


## Implementación

La geometría entera sale de **un punto y dos vectores** (`AX_U`, `AX_V`, con `AX_RISE = 0.46f`):
cualquier punto del techo es `origen + a·U + b·V`, y cada cara lateral cuelga en vertical de uno de
sus cantos. No hay una sola coordenada escrita a mano que pueda contradecir a otra, así que la caja
no se puede descuadrar al retocarla — que era el riesgo real de dibujar esto a pelo.

Sobre esa base:

- **Cinco pinceles, no tres.** Tapa, izquierda, derecha **y los dos cantos de la tapa**, porque un
  canto del mismo tono que la cara de debajo funde tapa y cuerpo en una sola pieza. La tapa es más
  ancha que el cuerpo (`LID_HALF_W` > `BODY_HALF_W`), así que vuela, y por eso se dibuja **después**:
  su canto tapa la parte alta de las caras del cuerpo.
- **Degradado corto por cara.** Lo que separa las caras es el SALTO entre ellas, no la pendiente de
  cada una — la misma lección que ya estaba escrita en `PapPremiumSurface`: la estructura lee como
  volumen, la amplitud lee como exagerado.
- **La luz cae arriba-izquierda → abajo-derecha**, la misma diagonal que el barrido metálico del
  botón del diálogo. De ahí el orden de tonos y, sobre todo, la **sombra desplazada** a
  `(+7, +3)`: centrarla bajo la caja es lo que delata una ilustración plana con una sombra pegada
  encima.
- **La cinta envuelve de verdad**: cruza el techo en las dos direcciones (una sola banda es un
  precinto, no un lazo), baja por el canto de la tapa y sigue por la cara del cuerpo, y **se
  oscurece cara a cara igual que la caja**. Con un verde plano se notaría que las tres caras son el
  mismo dibujo girado.
- **Lazo con colas.** Dos lazadas anchas y bajas más dos colas cayendo sobre el techo; sin colas el
  lazo se lee como una pegatina.

## Lo que decidió el device, no el código

**La plaza abierta y clara competía con el regalo.** En la primera pasada volumétrica la bahía
llegaba de x=14 a x=126 con pintura a `#4D5F6A` y trazo 3,4: al cerrarse el rombo por los cuatro
lados dejaba de leerse como suelo y pasaba a **un alambre brillante flotando** alrededor de la caja,
que además se llevaba la mirada. Ceñida a la caja (24–116), con la pintura apagada (`#38454E`) y
trazo 2,6, vuelve a ser lo que tenía que ser: el sitio donde algo está posado.

Mismo caso con las lazadas: a ±22 unidades y con los controles a −11 tiraban a **hélice**. A ±18
con controles a −9 son un lazo.

⇒ Las dos correcciones son de **proporción**, igual que los dos fallos de la versión plana eran de
geometría. En esta pieza, ningún problema ha sido nunca de color.


## Validación

- `./gradlew testAll :app:compileProdDebugKotlin --rerun-tasks` → **BUILD SUCCESSFUL**, 105 tareas
  ejecutadas, 0 fallos. Incluye `ColorGuardrailTest` (los `Color(0x…)` siguen confinados a
  `ui/illustrations/`) y el guard del backlog con el recuento nuevo (853/760/84/9).
- Pixel 8, **los dos temas**, con el escenario de regalo del Dev Catalog:
  `V2-3d-oscuro.png` y `V3-3d-claro.png`. La caja negra conserva silueta sobre el diálogo claro
  —que era el riesgo— y la sombra proyectada se lee sobre el suelo claro, donde antes no había
  ninguna.
- El emulador se quedó sin memoria a mitad de la revisión y el sistema lo mató; se rearrancó y se
  repitió la captura. Nada que ver con el código.

## Residual

- ⏳ Sin ver en un **móvil real**: el emulador no dice nada del tamaño aparente ni del brillo, y
  esta pieza mide **56 dp** en el diálogo — todo lo que se ha corregido aquí se vio ampliando a 3×.
- ⏳ La decisión que queda abierta a propósito: si al verla en mano el salto respecto a sus
  **cuatro hermanas planas** canta, la salida no es aplanar ésta, es abrirles ticket a ellas.
- 🔵 Y una pregunta del user sin cerrar: **¿la bienvenida antes de pedir la ubicación?** La
  recomendación fue **no** — el regalo llega por red con 20 s de timeout y puede no existir
  todavía; el mapa de detrás está vacío sin ubicación, así que el diálogo pasaría de buena
  noticia a splash; y regalar y pedir acto seguido se lee como un intercambio. El orden actual lo
  fija `HomeFirstEntryFlow.kt`, que da al permiso nativo su propia ventana. Si se quiere probar,
  se mide con una variante del preset, no se discute.


## Segunda pasada sobre el device (22-11)

Revisando el recorte a 3× —la ilustración mide 56 dp en el diálogo, y a ese tamaño pasaba
cualquier cosa— salieron cinco arreglos, cuatro de ellos pedidos por el user:

1. **El lazo leía MARIPOSA.** Dos manchas redondas pegadas al nudo. Ahora la lazada tiene
   **esquina en la punta** (una cinta doblada sobre sí misma no acaba redonda) y una **cuña
   oscura** donde nace del nudo: una cinta que sale de un nudo se estrangula ahí y se mete en
   sombra. Esa cuña es la pieza que convierte dos manchas en un lazo.
2. **Tono en el lazo.** Cada lazada lleva su degradado anclado al nudo —clara en la punta, oscura
   al nacer—, las colas van más oscuras y el nudo tiene su propia sombra desplazada. Un verde
   plano delataba que las dos lazadas eran el mismo dibujo espejado.
3. **Colas con corte en V**, dibujadas antes que todo lo demás para que salgan de DEBAJO del nudo.
4. **El suelo del tema claro estaba al revés.** El asfalto era más claro que la superficie del
   diálogo, así que la caja parecía posada sobre una hoja de papel. Ahora el suelo es más oscuro
   que la página y la pintura casi blanca: línea pintada sobre pavimento.
5. **Profundidad, en tres piezas y no una**: la sombra **proyectada** (lejos y suave), la de
   **contacto** (pegada a la base y más densa — es la que dice que la caja TOCA) y la
   **oclusión bajo el vuelo de la tapa**, dibujada DESPUÉS de la tapa porque lo que oscurece es
   justo lo que la tapa deja ver del cuerpo. Sin ella, canto y cuerpo son dos rectángulos pegados.
6. **La tapa vuela menos**: el saliente baja de 6 a 3,5 unidades (tapa 37→36, cuerpo 31→32,5).

### ⛔ Y el ensanche del cuerpo resucitó las "patas de caballete"
Al ganar 1,5 unidades por lado, la caja pasó a tapar casi entera cada línea de la plaza, y de las
tres que se pintaban —dos lados y el fondo— sólo asomaban las **puntas**: tres muñones grises en
las esquinas de abajo. Es exactamente el defecto de la versión plana, reaparecido en pequeño.

La causa real no era el ancho: era que se pintaba **una mezcla de aristas delanteras y traseras**.
Ahora se pintan **sólo las dos delanteras**, que convergen BAJO el regalo — una línea que entra
bajo el objeto y no vuelve a salir se lee como suelo; un trozo suelto a cada lado, no. El fondo de
la plaza no necesita línea: ya lo dibuja el borde del asfalto. Y la bahía pasa a derivarse de
`AX_U`/`AX_V` como la caja, así que no puede volver a descuadrarse respecto a ella.

⇒ Tres rondas, y **ningún problema ha sido nunca de color**: geometría, proporción y orden de
dibujo. Capturas finales: `V5-oscuro.png`, `V5-claro.png`, comparativa `REGALO-3D-v5.png`.
