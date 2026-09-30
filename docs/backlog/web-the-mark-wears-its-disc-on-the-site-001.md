# WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001 · la marca de la web recupera su disco, y el disco deja de estar escrito a mano

**Estado:** ✅ **Done** — sin commitear, a la espera de permiso. Rama
`feature/WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001-web-disc` · worktree `../Paparcar-web-disc`

Decisión del user (30-09-2026), al revisar dónde más quedaba logo por actualizar: **en la web el
disco se queda**. Es la hermana de [PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001] y se apoya en el mismo
motivo medido, no en gusto.

## Problema

Dos cosas distintas, y la segunda es la que ordena el ticket.

**1 · La tarjeta social sigue enseñando la marca de 2022.** `web/assets/img/og-card.jpg` (1200×630) y
su `og-card-small.jpg` son lo que ve quien recibe el enlace de `paparcar.com` en WhatsApp, Slack, X o
iMessage. Llevan el logo retirado **con su disco**, horneado en píxeles. Es el único sitio del repo
donde eso pasa: `git grep "M92.28,57.77"` sólo acierta en prosa histórica (CLAUDE.md y tres docs de
branding). Ningún `grep` encuentra un logo dentro de un JPEG, y ningún guard mira dentro de una
imagen — hereda el diagnóstico de
[`web-the-social-card-still-wears-the-2022-mark-001.md`](web-the-social-card-still-wears-the-2022-mark-001.md),
que este ticket cierra.

**2 · El disco vuelve a la web, y ahora tiene DOS consumidores escritos a mano.** Su geometría vive
hoy sólo en `docs/release/play-listing/assets/feature.html`, a pelo:

```html
<circle cx="54" cy="54" r="54" fill="#0D1117"/>
<circle cx="54" cy="54" r="52.5" fill="none" stroke="#25F48C" stroke-opacity=".28" stroke-width="2"/>
```

Si la web lo copia, el disco pasa a tener dos definiciones y la regla *el logo se GENERA, no se
copia* se rompe justo por donde ya se rompió una vez: un dibujo de marca escrito dentro de un
fichero que nadie asocia a la marca. La fracción `0.52896` —el tamaño óptico de la marca dentro del
disco— ya está declarada en `PLAY_FRACTIONS`, así que copiarla sería copiar el único número que sí
tiene fuente.

## Doctrina violada

- ***El logo se GENERA, no se copia*** (CLAUDE.md § Iconos). El JPEG de la og-card es la forma de
  copia que ningún `grep` encuentra, y el disco está a punto de ser la segunda.
- ***La web es un consumidor más de la verdad del producto*** [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001].
  La tarjeta social lleva 2 días prometiendo una marca que no existe, en cada enlace compartido.
- ***Sistemas, no parches*** — el invariante (*cómo se dibuja el emblema*) se arregla en UN sitio y
  se barren todos sus consumidores. Pegarle el logo nuevo encima al JPEG sería el parche.
- **Un knob muerto es una mentira**: `logoMark({ surface })` elige hoy entre dos verdes porque *no
  hay disco* que sostenga el contraste. Con disco, el parámetro deja de cambiar nada.

## Señales / datos disponibles

Dos hallazgos del inventario que evitan inventar números y desbloquean la tarjeta:

- **La proporción ya existe.** El `logoMark()` anterior a `01310a3b3` usaba `viewBox 0 0 108 108`
  con `circle r="54"` y `size` = **diámetro del disco** — el mismo lienzo que el emblema del banner.
  Los dos call sites (36 y 40) ya significaban eso, así que no hay que tocarlos. El tamaño óptico de
  la marca dentro del disco es `0.52896`, que se conservó a propósito al cambiar de marca.
- **La og-card NO estaba bloqueada.** Su ticket la daba por atascada en el raw `es` de la sesión de
  Play, que no está versionado. Pero `prepare_social()` hace sobre ese raw exactamente lo que
  `prepare_screens()` ya hizo: recortar `STATUS_BAR_PX` y escalar. Y su salida **sí** está
  versionada: `web/assets/img/screen-1-es-1080.webp`, 1080×2280 = 2400−120, ya recortada. Leyendo
  de ahí, la tarjeta se reproduce **desde el repo solo**, que es el criterio 2 de su propio doc.
- **Rasterizador**: no hay uno en Python (`svglib` parsea, pero `reportlab` no tiene backend —
  `cannot import desired renderPM backend rlPyCairo`). El del repo es **Chrome headless**, en
  `docs/release/play-listing/tools/render_graphics.sh`, con su `shoot()` y su `flatten()` ya
  resueltos. No se escribe un renderer nuevo: se usa ése.

## Diseño

**El emblema es una superficie del maestro, no un adorno de cada consumidor.** `export_surfaces.py`
pasa a declarar el disco una vez —tinta, aro, opacidad, grosor y la fracción `F_EMBLEM`— y a
escribirlo en las tres formas que lo necesitan:

| Consumidor | Qué recibe | Cómo |
|---|---|---|
| Web (cabecera, pie, legales) | constantes del emblema en `web/lib/mark.generated.mjs` | `logoMark()` compone el SVG |
| Banner de Play | los dos `<circle>` de `feature.html` | `write_play()` los reescribe, igual que ya reescribe el glifo |
| og-card | `docs/assets/paparcar-emblem.svg` → PNG 512 con alfa | `render_graphics.sh emblem` + `prepare_social()` |

Con eso el disco tiene **una** definición y tres salidas derivadas. Que el banner salga
**byte-idéntico** tras el cambio es la prueba de que es refactor y no rediseño.

Piezas:

1. **`docs/branding/master/export_surfaces.py`** — `F_EMBLEM = 0.52896` (y `PLAY_FRACTIONS` lo
   referencia en vez de repetirlo), `DISC_INK`/`DISC_RING`/`DISC_RING_OPACITY`/`DISC_RING_WIDTH`,
   `emblem_transform()` extraído de `play_group()` para que web y banner usen la MISMA matemática,
   `mark_mjs()` emite las constantes, `write_play()` asume los dos círculos, y una superficie nueva
   `docs/assets/paparcar-emblem.svg`.
2. **`web/lib/icons.mjs`** — `logoMark()` dibuja el emblema en `viewBox 0 0 108 108`: disco, aro,
   marca al transform generado. `surface` **se borra** (no se deja muerto): con disco de tinta el
   glifo es `PapGreen` en cualquier página, que es justo lo que el disco compra.
3. **`web/assets/css/site.css`** — fuera `border-radius: 50%` de `.logo-mark`: es residuo de la era
   del disco, inerte sobre un SVG transparente, y con el disco dibujado sigue sin pintar nada.
4. **`web/tools/prepare-assets.py`** — `prepare_social()` deja de pegar `play-icon-512.png` (el
   icono de OTRA superficie, una baldosa opaca) y pega el emblema; su teléfono sale del webp
   versionado, sin re-recortar. Deja de necesitar un argumento de sesión.
5. **`web/tools/restyle-legal.mjs`** — no se toca: importa `logoMark` y es re-ejecutable entre
   marcadores `pap:shell`. Se vuelve a correr y las dos legales se ponen al día.
6. **CLAUDE.md** — la regla del logo pasa de «UNA excepción, y es del user» a dos, con el criterio
   que las une escrito una sola vez: *donde la marca llena su caja, no hay disco; donde es un
   emblema dentro de una composición ajena, el soporte es legítimo*.

## Criterio de éxito

- Cabecera, pie y las dos páginas legales enseñan el emblema con disco, y `node web/build.mjs
  --check` sale limpio (editar el copy y no reconstruir es desplegar lo anterior).
- `og-card.jpg` y `og-card-small.jpg` enseñan la marca de 2026 sobre su disco, y **regenerarlas dos
  veces seguidas da el mismo byte** sin un fichero de sesión.
- `feature.html` sale **byte-idéntico** tras pasar su disco al generador.
- El favicon y el `apple-touch-icon` **no cambian** (decisión del user): uno es un glifo de pestaña,
  el otro una baldosa que el sistema exige opaca — ninguno es un emblema dentro de una composición.
- Un testigo que falsifique el defecto: hoy no hay ninguno que vigile de dónde sale el logo de la
  tarjeta.

## Consumidores auditados

`git grep "M92.28,57.77"` → 5 aciertos, **los 5 prosa** (CLAUDE.md, `docs/branding/README.md`,
`new-logo-analysis.md`, el doc de la marca y el propio `export_surfaces.py` explicando la historia).
Cero en código: el árbol ya está en la marca de 2026.

| Superficie | Fuente | Antes | Después |
|---|---|---|---|
| Cabecera + pie (todas las páginas) | `layout.mjs` → `logoMark()` | 2026 sin disco | **emblema con disco** |
| Páginas legales (×2) | `restyle-legal.mjs` → HTML horneado | 2026 sin disco | **emblema con disco** |
| `og-card.jpg` + `-small` | `prepare_social()` | **2022 con disco** | **emblema con disco** |
| `favicon.svg` | `export_surfaces.py`, temático | 2026 sin disco | exento (decisión del user) |
| `apple-touch-icon.png` | `prepare_touch_icon()` | 2026, baldosa | exento (decisión del user) |
| Banner de Play | `feature.html` | disco a mano | mismo dibujo, **generado** |
| Icono 512 de Play | `icon.html` | baldosa, sin disco | sin cambio |
| App (launcher, splash, notif, composeResources) | `export_surfaces.py` | sin disco | **sin cambio** — ahí `F_MARK = 1.0` |
| `logos/paparcar_disc_mark.*` | — | propuesta DESCARTADA | ⛔ no resucita; la borra `2f26f1a16` |

⛔ **El disco que vuelve no es el de `logos/paparcar_disc_mark.*`**, que es otro dibujo (un disco
verde con ranura y un pie) descartado y pendiente de borrado en `2f26f1a16`. Aquí el disco es
**soporte** de la marca de 2026, no una marca alternativa.


---

## Lo que cambió el diseño sobre la marcha

**1 · El disco del banner es INVISIBLE sobre la web, y eso no se vio venir.** El plan era llevar a
la web el emblema del banner tal cual. Medido a tiempo: su relleno es `#0D1117` y el fondo del
sitio es `--ink #0d1117` — **el mismo color**, `1,00:1`. Habría dejado sólo el aro al 28 % mientras
la marca encogía al 52,9 % de su caja: estrictamente peor que no hacer nada. El user eligió el
disco **verde con glifo de tinta**, que da `12,97:1` en los dos sentidos y es, además, el que la web
ya llevaba antes de la marca de 2026.
*De ahí sale la forma final: geometría compartida, **pintura por fondo**.* `EMBLEM_BANNER` y
`EMBLEM_SITE` en el exportador, un solo `emblem_transform()` para los dos.

**2 · La og-card no estaba bloqueada.** Su doc daba por perdido el raw `es` de la sesión de Play.
Pero `prepare_social()` hacía sobre ese raw lo mismo que `prepare_screens()` ya había hecho, y la
salida **sí está versionada**: `screen-1-es-1080.webp`, 1080×2280 = 2400−120, ya recortada. El
fichero que faltaba llevaba meses en el repo, escrito por la función de al lado.

## Verificado

- **`feature.html` byte-idéntico** tras pasar su disco al generador — la prueba de que es refactor
  y no rediseño. Igual las 8 superficies de la app: el exportador las reescribe y `git diff` sale
  vacío.
- **La tarjeta se reproduce desde el repo solo**: dos pases seguidos dan `b1c51d9a…`, y un tercero
  tras rehacer la cadena entera desde cero da el mismo. Sin fichero de sesión.
- **Píxeles**, no exit codes: en el cuadro del logo de la og-card, 69 % verde con `(37,244,140)`
  exacto y 28 % tinta. El emblema rasterizado: 70 % verde, 8 % tinta, 21 % transparente — que es
  `1 − π/4`, o sea que el disco llena su caja.
- **Testigo falsificado en los CUATRO casos** (`WebSiteParityGuardrailTest`, chequeo 6): desfase con
  el maestro, copia a mano en `icons.mjs`, disco ausente en la página construida, e icono de Play
  prestado por la tarjeta. Los cuatro en rojo al romperlos.
- `./gradlew testAll --rerun-tasks` en verde, y `node web/build.mjs --check` limpio.
- La cabecera del sitio y la tarjeta, vistas renderizadas (Chrome sobre el sitio servido), no sólo
  medidas.

⚠️ **El guard mide el MECANISMO, no los píxeles.** Sigue sin haber nada que mire dentro de una
imagen; lo que se vigila es de dónde sale el dibujo, que es la parte que se puede saber. En
particular cuenta **quién abre** `play-icon-512.png` y exige que sea uno solo —el touch icon, que ES
ese icono redimensionado—, no quién lo nombra: el comentario que explica esta misma trampa lo
nombra, y debe seguir nombrándolo.

## Fuera de alcance, con su doc

- `docs/backlog/web-a-derived-asset-must-match-its-generator-001.md` — dos derivados de `web/` que
  no son lo que su generador produce hoy: dos assets cuyo nombre lleva un hash que no es el suyo
  (se arreglan solos al entrar este commit, pero nada impide que se repita), y el
  **`apple-touch-icon`**, que no es `play-icon-512.png` redimensionado aunque su función diga que
  sí. Este último **se revirtió a mano** aquí: el user lo declaró exento y correr el generador lo
  habría encogido sin que nadie lo pidiera.

## Errores cometidos, por si vuelven

- **`git checkout --` sobre ficheros con trabajo sin commitear revierte el trabajo, no la rotura.**
  Al falsificar el guard se restauró así tras cada rotura y se perdieron `icons.mjs`,
  `mark.generated.mjs` y `prepare-assets.py`. Recuperable entero porque los tres salen de un
  generador o de un script de parche guardado — que es exactamente por qué los parches se escriben
  como ficheros. Para falsificar, copia de respaldo, nunca `checkout`.
- **Un `grep -c $'\r'` con el patrón vacío casa TODAS las líneas.** Dio «140 CR» en ficheros que no
  tenían ninguno y mandó la investigación del renombrado de assets por un camino falso durante dos
  medidas. Se confirmó con `tr -cd '\r' | wc -c`, que sí cuenta bytes.
