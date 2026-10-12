# `docs/branding/` — las marcas de Paparcar y cómo se llaman

> ✅ **El Compás B+ está cableado en todas las superficies** (21-09-2026). El Trazo de 2022 ya no
> existe en el repo. Revisión completa: [`new-logo-analysis.md`](new-logo-analysis.md) ·
> ticket [`brand-a-new-mark-earns-its-surfaces-001`](../backlog/brand-a-new-mark-earns-its-surfaces-001.md).
>
> ⛔ **Los drawables de la app son SALIDA.** Se regeneran con
> `cd docs/branding/master && python export_surfaces.py`. Editarlos a mano devuelve el problema que
> este maestro existe para resolver.

## Dónde escribe el exportador

| Superficie | Fichero | Verde |
|---|---|---|
| Icono adaptativo (foreground) | `app/…/drawable/ic_launcher_foreground.xml` | `#25F48C` |
| Splash día | `app/…/drawable/splash_glyph.xml` | `#009F5E` |
| Splash noche | `app/…/drawable-night/splash_glyph.xml` | `#25F48C` |
| Small icon de notificación (17 usos + widget) | `app/…/drawable/ic_notification_logo.xml` | blanco (lo tiñe el sistema) |
| Icono del Dev Catalog | `app/src/mock/…/ic_launcher_foreground.xml` | `#DDD6FE` |
| Marca en la app, tema oscuro | `composeResources/…/paparcar_iconmark_green.xml` | `#25F48C` |
| Marca en la app, tema claro | `…/paparcar_iconmark_forest.xml` | `#009F5E` |
| Marca tintable (`PaparcarMark`) | `…/paparcar_iconmark_white.xml` | blanco |
| Favicon | `hosting/public/favicon.svg` | según `prefers-color-scheme` |
| Documentación (README de la raíz) | `docs/assets/paparcar-logo.svg` | según `prefers-color-scheme` — el tema de GitHub no lo controlamos y `#25F48C` sobre su blanco da **1,42:1** |

**A mano, porque no son plantillas:** `web/lib/icons.mjs` (`logoMark`),
`docs/release/play-listing/assets/{icon,feature}.html` y `web/assets/img/apple-touch-icon.png`.

### Borrados, no regenerados

- Los **10 mipmap PNG** de launcher: con `minSdk 26`, `mipmap-anydpi-v26/ic_launcher.xml` gana
  siempre y esos PNG no se cargaban nunca.
- `ic_logo_color.xml`: huérfano — no hay ni un `setLargeIcon` en el repo, y encima era el disco azul
  de 2022.
- `paparcar_logo.xml` y `paparcar_logo_dark.xml`: eran *glifo + disco*. Sin disco son exactamente
  `paparcar_iconmark_*`, así que `PaparcarLogo` pasa a usar ésos.

## Los nombres

Había tres dibujos distintos llamándose todos «el logo», y eso hacía imposible hablar de ellos.
Desde aquí se llaman así:

| Nombre | Qué es | Dónde está |
|---|---|---|
| **Trazo** | El logo de 2022, el que está en la app. Un coche de perfil dibujado a pulso: grosores variables, ruedas elípticas giradas −40°, losa horizontal. Dice «coche» y nada más. | `shared/…/composeResources/drawable/paparcar_logo.xml` y otros 24 sitios |
| **Fluida** | La primera **P**. Orgánica, con movimiento y aire. ⚠️ **Nunca se vectorizó**: sólo existe como render. | `reference/source/paparcar-mark-fluida-master.png` · vectorizada en `reference/paparcar-mark-fluida.svg` |
| **Compás** | La misma P pasada a regla. Es la propuesta que se revisó: radios exactos, pata a 45°, todo arcos. | `reference/paparcar-mark-2026.svg` (copia limpia del entregable) · original en `reference/source/` |
| **Compás B+** | ⭐ **LA MARCA.** El Compás con las correcciones de esta revisión: tangencia, anchura, canal único, punta redondeada. **Generado**, no dibujado. | `mark/paparcar-mark-compas-b.svg` |
| **Compás B+ compacta** | La misma con **menos cosas**: ruedas gemelas, sin media luna ni repisa. Nació para ≤ 32 dp, se probó como marca única y **se descartó**. | `explorations/09-compacta.svg` — ejemplo, no cableado |

### Una sola marca, a propósito

La marca es la **completa**, y es la única: la misma forma a todos los tamaños. Hubo dos reglas
antes de ésta y las dos están descartadas —dos dibujos con un umbral en 32 dp (completa arriba,
compacta abajo), y quedarse con la compacta para todo—. La rueda derecha va en **anillo abierto**,
separada del cuerpo por un canal de 10 u igual por los dos lados: ese hueco es lo que hace que el
dibujo se lea como una **P** y no como dos ruedas gemelas.

⚠️ **El precio, dicho, y se paga abajo**: el canal y la ranura de la repisa miden ~1 px a los 51 y a
los 24 px de icono, así que por debajo se cierran solos. A 24 px el dibujo se desmiga en **tres
trozos** —uno es una miga de 6 px— y a 20 px ya ha perdido una rueda; donde muerde es el small icon
de notificación y los chips (§ *Qué se lleva el tamaño pequeño*). Se asume a cambio de leer la P
donde la marca se mira de verdad, y de que ninguna superficie tenga que saber en qué lado de un
umbral está.

🔑 **Decidido el 24-09 mirando las dos formas rasterizadas a sus píxeles reales** —48 y 24— y
ampliadas sin suavizar. Sobre el vector no vale: en una pantalla densa el navegador lo pinta a 2× o
3× y eso favorece a la completa más de lo que la favorece un icono de verdad.

## La estructura

Cuatro carpetas, y cada una responde a una pregunta distinta:

```
master/         los generadores.  ⭐ AQUÍ se cambia el dibujo.
                compas.py · rebuild_lobe.py · build_marks.py · export_surfaces.py
                vectorize_fluida.py  (reproduce la Fluida desde su ráster)
                fluida.py            (la REDIBUJA limpia, con su propio modelo)
                compare_fluida.py    (mide su solape)
                dossier.py           (escribe ../propuesta.html, la página única)

mark/           LA marca, un solo fichero. Salida del generador y entrada del
                exportador: esto es lo que acaba en las 11 superficies.
                paparcar-mark-compas-b.svg

explorations/   los ejemplos, numerados en orden. Salida también, pero NO
                cableada: existen para mirarlos.

propuesta.html  LA página: propuesta + ejemplos + comparación, en un sitio.

reference/      lo que NO es nuestra marca actual, y por eso no se regenera:
                source/   los entregables del diseño, byte a byte (evidencia)
                paparcar-mark-2026.svg          la propuesta original, limpia
                paparcar-mark-fluida.svg        la Fluida vectorizada (salida)
                ic_launcher_foreground_2026.xml candidato histórico, no cableado
                preview-*.png                   las láminas del análisis
```

⛔ **`mark/` y `explorations/` están separadas a propósito.** Antes vivían juntas en `candidate/`, y
ese nombre se quedó mintiendo el día que la marca entró en producción: lo que hay en `mark/` no es
un candidato, es lo que el exportador escribe en once sitios. Un directorio cuyo nombre miente es
como un fichero de ejemplo acaba exportado por error.

⛔ **Todo lo de `mark/` y `explorations/` es salida.** Si hay que cambiar algo del dibujo se cambia
un parámetro en `master/build_marks.py` y se regenera:

```bash
cd docs/branding/master && python build_marks.py
```

Editar el SVG a mano devuelve exactamente el problema que este maestro existe para resolver: un path
que nadie puede volver a tocar.

## Por qué hay un maestro paramétrico

Porque no lo había. `grep -rl "M92.28,57.77"` devuelve **25 ficheros** con el path del Trazo escrito
literalmente dentro — seis VectorDrawable de `:app`, cinco de `composeResources`, dos HTML de Play,
el favicon, `web/lib/icons.mjs`, `docs/assets` y diez páginas generadas. Sin una fuente de la que
derivar, la regla de *Compensación Óptica del Logo* de `CLAUDE.md` tuvo que escribirse como una
pareja de números mágicos (trazo 6 sobre oscuro, 7 sobre claro).

El Compás resultó ser **seis circunferencias y dos rectas tangentes**, nada más:

```
cabeza   R 84 / r 44 en (164, 84)   ·  ojo r 20 concéntrico
lóbulos  R 56 / r 24 en (56,184) y (192,184)
pata     tangente común exterior R84 ↔ R56      (borde superior)
         tangente a r44 desde la punta de la gota (borde inferior)
bajo     recta tangente al R84 por abajo, y = 168
acuerdo  cuarto de círculo r 16 en (128, 184)
gota     base plana tangente al r44 + recta a la punta (77,6 · 128)
```

`master/compas.py` lo reproduce con **224 píxeles de diferencia sobre 238.080** (0,09 %, todo
antialias) y pasa de 3.402 caracteres de path a 499.

Dos cosas que parecían decisiones y son consecuencias: el acuerdo **r 16 no lo eligió nadie**, vale
`184 − 168`; y la **repisa** del lóbulo derecho sólo existe porque hay 8 u de desajuste entre el
bajo del cuenco y el techo de los agujeros — *es el desajuste hecho forma*.

## Los ejemplos, juntos y en orden

**[`propuesta.html`](propuesta.html)** — **una sola página** con la propuesta, dónde va, los nueve
ejemplos en orden y la comparación con la Fluida. La escribe `master/dossier.py` y **cada cifra se
mide al generarla**. Los SVG sueltos quedan en `explorations/NN-<slug>.svg`.

⛔ Sustituye a `gallery.html` y a `fluida-vs-compas.html`. Tres páginas contando cada una un trozo de
la misma decisión son la versión en HTML del problema que abrió este ticket: quien las abre no sabe
cuál mirar, y al cambiar algo se actualiza una y envejecen dos.

⛔ **Una variante es una fila de `VARIANTS` en `build_marks.py`, no una edición encima de la
anterior.** Antes cada vuelta del dibujo vivía sólo en el historial de git, así que comparar dos era
un `git show` y un visor — y por eso las decisiones se discutían de memoria. Ahora todas se
regeneran a la vez, con el mismo código y desde los mismos parámetros, y añadir una es añadir una
fila.

| # | | Qué cambia | Esquinas vivas |
|---|---|---|---|
| 01 | `compas-b` | La tangencia. Lienzo cuadrado y el canal de la rueda derecha aún desigual (7,39 / 10) | 1 · 125° |
| 02 | `ancho` | +8 u de ancho: cabeza y rueda derecha a la derecha, juntas | 1 · 127° |
| 03 | `canal` | Un solo canal de 10 u alrededor de la rueda derecha | 1 · 127° |
| 04 | `punta` | **← LA ELEGIDA.** La punta de la gota deja de ser un pico: `r 7` | **0** |
| 05 | `recta` | El remate **recto** de la Fluida: la rueda de abajo la corta una recta y el cuenco cierra en esquina | 0 |
| 06 | `recta-viva` | Lo mismo sin redondear esa esquina, para ver cuánto aporta el `r 8` | 1 · 52° |
| 07 | `espejo` | Reflejada en horizontal: el peso se va al lado por el que se empieza a leer | 0 |
| 08 | `fino` | Trazo `34/32/33 → 28/28/28`, casi monolineal: los huecos respiran | 0 |
| 09 | `compacta` | Sin media luna ni repisa, dos ruedas gemelas. Probada como marca única y descartada | 0 |

La última columna no es una impresión: `build_marks.py` **audita** las esquinas al generar. Aplana
cada contorno y busca dónde el recorrido cambia de dirección de golpe (> 20° en un punto). Un arco
aplanado gira uno o dos grados por segmento, así que un pico destaca por dos órdenes de magnitud.

## Qué cambia el Compás B+

Seis cosas, y **ninguna toca la P**. La cabeza sigue siendo el mismo R 84 con su ojo concéntrico
y la rueda derecha sigue abierta y separada, que es lo que sostiene la lectura de la letra
[→ la hoja de construcción en `reference/source/paparcar-construccion-comparativa.png`].

1. **Tangencia.** Los lóbulos bajan de `y = 184` a `y = 192`, con lo que el techo de los agujeros
   (`192 − 24 = 168`) cae justo en el fondo del cuenco de la P (`84 + 84 = 168`).
2. **Anchura.** Tangenciar costó 8 u de **alto**, así que la marca se devolvió 8 u de **ancho**:
   cabeza y rueda derecha se trasladan `+8` en x —juntas, para no romper el enrase que comparten en
   el borde derecho— y **la rueda izquierda se queda donde estaba**, byte por byte. No hay ninguna
   escala: mover en x no toca la tangencia (`y = 168`), ni el hueco de la media luna (7,39 u), ni el
   acuerdo. Lo que se estira son la **pata** y el **bajo del cuenco**, que es justo donde la P se
   abre. El lienzo pasa de `248 × 248` a **`256 × 248`** — la misma relación que el Compás tenía
   antes de bajar los lóbulos.
3. **Un solo canal.** El hueco entre la rueda derecha y el cuerpo valía **7,39 u por la media luna y
   10 u por la ranura** de la repisa: un **35 %** de diferencia entre un lado y el otro de la misma
   rueda, y se ve. Ahora la media luna se abre a 10 y el canal es uno solo, medido `mín 10,000` de
   punta a punta. De regalo cierra un pendiente: 10 u aguantan hasta iconos de **~37 px**, donde
   7,39 se cerraba a los **~51**.
4. **Menos masa.** Contraforma `r 44 → r 50` y punta de la gota de 77,6 a **71**. Los anillos pasan
   de `40 / 32 / 40,3` a `34 / 32 / 33,2`, y la tinta de **49,3 % a ~44,5 %** del círculo envolvente
   — el peso de la Fluida (44,7 %).
   ⛔ El 71 **no es libre**: la punta de la gota fija el borde *inferior* de la pata, así que al
   alejar la cabeza hay que reseguirla o la pata adelgaza sola (con 68 se queda en 30,8).
5. **La S.** El acuerdo al lóbulo izquierdo pasa de `r 16` —el mínimo geométrico, que se lee como
   círculo pegado a una barra— a `r 34`.
6. **Sin un solo pico.** La contraforma en gota —la que hace de pin— acababa en **punta**: dos
   rectas cortándose a **52,6°**, el único ángulo vivo que le quedaba a una marca que es toda arcos
   y tangencias. Se ve en el hueco y se ve invertido en el verde que lo rodea. Ahora lleva `r 10` y
   la auditoría da **cero** esquinas vivas.
   ⛔ El radio **no se compensa** moviendo `TIP_X`: la punta virtual es lo que fija el borde
   *inferior* de la pata, así que correrla para recuperar el largo perdido la adelgaza (≈0,8 u de
   pata por cada u de punta). La gota se acorta 20,2 u y la pata se queda en 33,2.

De propina, la ranura sube de 8 a 10 u y el dibujo sigue tocando los cuatro bordes.

⛔ **El lienzo ya no es cuadrado, y eso es un dato del dibujo, no del fichero.** Quien lo reciba
tiene que respetar la relación: las superficies de Android y el favicon se encuadran por el
**círculo envolvente** (`frame(fraction)`), que es cuadrado y no se entera; pero cualquiera que
meta la marca en una caja cuadrada a pelo le corta la rueda derecha o la aplasta. Por eso
`web/lib/mark.generated.mjs` publica `MARK_W` y `MARK_H` junto a los paths.

## El remate RECTO — lo único que le queda por copiar a la Fluida

*Ejemplo 04. No está elegido: existe para poder mirarlo al lado del 03.*

En el Compás, la rueda de abajo **se funde en curva** con el bajo del cuenco: el acuerdo `r 34` la
lleva en una S hasta la horizontal. En la Fluida no — **el bajo del cuenco corta la rueda con una
recta y gira en esquina**, y eso es lo que hace que se lea como la panza de una P y no como una
rueda pegada a un tubo.

Medido sobre el contorno vectorizado de la Fluida (`reference/paparcar-mark-fluida.svg`,
el tramo recto del borde exterior bajo el cuenco): un tramo **recto de 52 u** que sube a **≈56°**, y después
un giro corto a la **horizontal del bajo del cuenco**, que corre plana hasta `x ≈ 180`. No es una
curva suave con poca curvatura: es una recta.

Aquí no se copia el ángulo, se copia la **regla**. La recta se pide **tangente al lóbulo** y con el
**mismo apoyo en el bajo** que tenía el acuerdo (`x = 145,443`). Con esas dos condiciones la recta y
su punto de contacto quedan determinados — no hay ángulo que elegir a ojo — y salen **52,2°**, a
cuatro grados de la Fluida.

- ⛔ **La tangencia no es un detalle de acabado.** Una recta que corte el círculo sin ser tangente
  deja un **pico** donde la rueda se encuentra con ella, y ese pico no se ve hasta que se amplía.
- ⛔ **Mismo apoyo a propósito**: cambiar de acuerdo a recta debe cambiar la *curva*, no *dónde
  apoya*. Si se mueven las dos cosas, la comparación ya no dice cuál de las dos mirabas.
- El precio: la rueda izquierda **pierde su cuadrante superior derecho** y el dibujo gana tinta
  justo ahí. Es un cambio de carácter, no un remate — por eso va como ejemplo, no como corrección.
- La esquina contra el bajo lleva `r 8` (ejemplo 04). El 05 la deja viva, para ver si esos 8
  cambian algo: la marca presume de **no tener ni una esquina sin radio**.

## El espejo y el trazo fino — ejemplos 07 y 08, sin elegir

**07 · Espejo.** La elegida reflejada en horizontal, para llevar el peso al lado por el que se
empieza a leer.

- ⛔ **No se hace con parámetros, se refleja la SALIDA.** La construcción tiene *mano* metida en
  todas partes: el `side` de cada tangente, el sentido de cada arco, la media luna que el lóbulo
  derecho le roba a la cabeza. Reflejar los centros no refleja el dibujo — lo rompe. El grupo con
  `scale(-1,1)` es exacto y no toca ni un flag.
- ⛔ **Invierte la P, y eso no es un detalle.** El cuenco de una P va a la **derecha** del asta.
  Reflejada, la figura deja de leerse como letra; las otras dos lecturas —el punto de ubicación en
  el hueco y el coche— sobreviven, pero la que da nombre a la marca no. Es la misma pieza que
  sostiene la rueda derecha abierta: *antes de llamar defecto a una asimetría, preguntar qué
  sostiene.*

**08 · Trazo fino.** Anillos `34 / 32 / 33,2` → **`28 / 28 / 28,2`**, casi monolineal. Tinta del
**44,6 % al 38,7 %** del círculo envolvente.

El detalle que decide cómo se hace: **el agujero de la rueda no es libre, lo pina la tangencia**
(`LC.y − R_HOLE = 168`). Así que engordar el agujero para adelgazar el anillo rompería justo lo que
costó dos vueltas conseguir. Se adelgaza por el otro lado — `R_LOB 56 → 52` — y entonces los dos
centros de lóbulo **se recolocan solos**, porque no son datos: `LC.x = R_LOB` (full bleed) y
`RC.x = HC.x + R_HEAD − R_LOB` (enrase derecho). El lienzo queda `256 × 244`.

- La contraforma sube a `r 56` y la punta de la gota baja a `61` —resuelto, no elegido: es el
  `TIP_X` que deja la pata en 28.
- ⚠️ **Medido, y no salió lo que esperaba**: comprobé si adelgazar cuesta legibilidad de icono
  contando contraformas cerradas de 40 px a 18 px, y **las dos variantes dan lo mismo** (2, siempre).
  La medida no las separa, así que no se puede decir ni que mejore ni que empeore a tamaño pequeño.

## La Fluida, vectorizada — y por qué NO con el Compás

**`reference/paparcar-mark-fluida.svg`**, escrita por `master/vectorize_fluida.py`. Y
[`propuesta.html`](propuesta.html) la pone al lado de la elegida.

La Fluida nunca se dibujó en vectorial: su único original es un ráster, y lo que había era un
**calco** (marching squares + Douglas-Peucker + Catmull-Rom, 185 puntos repartidos por el trazador,
sin tolerancia declarada). Ahora hay una vectorización de verdad:

| | |
|---|---|
| contorno | **subpíxel**, marching squares sobre el canal alfa al 50 % |
| ajuste | cúbicas por mínimos cuadrados (Schneider), tolerancia **declarada** |
| resultado | **80 cúbicas**, 3.168 caracteres |
| fidelidad | desvío máximo **0,92 u** contra el contorno real = **3,9 px del propio ráster** |
| solape | **IoU 0,962** con el original |

⛔ **Se intentó tres veces con el modelo del Compás y no puede.** No es que saliera regular: es que
el modelo **no puede representar este dibujo**. Allí la cabeza hace *tres trabajos con un solo
radio* —enrasar arriba, poner el bajo del cuenco (`2·R_HEAD`) y dar el ángulo de la espalda por
tangencia con la rueda—, y la Fluida no cumple esa atadura. Medido: su espalda va a **54,2°** y el
modelo se quedaba en **46,9°**; forzarlo dejaba la pared inferior del cuenco en **2,2 u** de 33.
*Un modelo que no puede representar un dibujo no es el camino para reproducirlo.*

⛔ **Un solo path con los cinco contornos, regla nonzero.** La orientación que devuelve marching
squares ya es opuesta en los agujeros, así que nonzero los abre sola. Emitirlos como cinco paths los
**une** —los agujeros salen rellenos— y el solape se desploma de 0,982 a **0,786**.

⚠️ **El 0,80 de tolerancia es un punto medido, no un gusto.** Con 0,30 salen **174 cúbicas** —más
que los 185 puntos del calco que esto sustituye— y lo que reproducen de más es el *temblor del
antialiasing*, no el dibujo.

## Qué se lleva el tamaño pequeño

Esto es el **coste asumido** de tener una sola marca completa: lo que a 24 px ya no es nada, y está
medido. La compacta existió para ganarlo y se descartó porque a cambio afloja la P a todos los
tamaños (§ *Una sola marca*).

Lo que se cierra solo cuando el icono baja:

- la **media luna** que separa la rueda derecha del cuerpo mide 7,39 u — el 2,5 % del envolvente, o
  sea 1 px sobre los **~51 px** de icono. Por debajo de ahí ya se cerraba sola;
- la **repisa y su ranura** de 10 u: 1 px a los **~24 px**.

La respuesta de la compacta era asumirlas: el lóbulo derecho pasa a **anillo entero fusionado con el
cuerpo**, igual que el izquierdo —dos ruedas gemelas, marca de una pieza—, los agujeros suben de
r 24 a **r 28** y la contraforma baja a r 46, que es lo que ensancha los huecos donde se juega la
legibilidad. Anillos: **38 / 28 / 34,2**. Esa vía está descartada; se queda como ejemplo `09`.

Medido en blanco sobre negro —el caso duro, la barra de estado—, piezas de tinta / contraformas:

| px | Compás B+ | Compás B+ compacta |
|---|---|---|
| 20 | 2 piezas · **2 contraformas** (ya falta una rueda) | 2 piezas · **3 contraformas**, ruedas 7 / 7 |
| 24 | **3 piezas** (una miga de 6 px) · 41/10/8 | **2 piezas** · 36 / 11 / 10 |
| 32 | 3 piezas (una de 10 px suelta) · 74/24/15 | **2 piezas** · 63 / **21 / 21** |

⛔ **Las dos columnas son reales y hay que elegir cuál se paga.** La compacta aguanta mejor abajo
—donde la P no se lee de ninguna manera— pero afloja la letra también a 96 y a 210 px, que es donde
la marca se mira de verdad. Se eligió pagar el tamaño pequeño: ahí el small icon es una silueta con
tres piezas, y la P sobrevive arriba.

### Dos vías que descarté, y por qué

- ⛔ **Subir el bajo del cuerpo a `y = 160`.** Es la vía obvia y **rompe el cuenco de la P**: ese
  bajo no es una línea suelta, *es el punto más bajo del círculo R 84*. Entre `x = 164` y `199,8`
  una recta a 160 va por encima del arco y le amputa el trozo de cuenco que queda junto a la rueda
  derecha.
- ⛔ **Encoger la cabeza a R 76.** También tangencia y también conserva el cuenco, pero rompe un
  enrase que el dibujo original sí tenía: **la cabeza y el lóbulo derecho comparten borde derecho en
  `x = 248`** (`164+84` y `192+56`). Con R 76 la cabeza se queda en 240 y la rueda sobresale 8 u.
