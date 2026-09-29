# PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001 · la ficha de Play sigue enseñando la marca que ya no existe

**Estado:** ✅ **Done** (29-09-2026) · rama
`chore/PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001-relisting` · worktree
`../Paparcar-play-art` · follow-up sacado a propósito del alcance de
`BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001` (mergeado el 29-09-2026).

## Problema

La marca nueva entró en las 21 superficies del producto —los tres iconmarks, los adaptive icons,
`PaparcarLogo`, la cresta de Plus, el favicon y la web— pero **los PNG ya renderizados de la ficha
de Play no se han vuelto a generar en ninguna vuelta del ticket de marca**. Siguen siendo el render
de `PLAY-LISTING-001`, o sea la marca de 2022 con disco:

- `docs/release/play-listing/assets/play-icon-512.png`
- los feature graphics: el base y los nueve locales de
  `assets/screenshots/{en,es,it,pt,fr,de,nl,pl,ro}/feature-graphic-1024x500.png`, más las
  alternativas de la raíz.

Las **fuentes** (`assets/icon.html`, `assets/feature.html`) ya llevan la marca nueva: su grupo del
glifo lo reescribe `export_surfaces.py`. Lo que falta es ejecutar el render.

## Doctrina violada

*La web es un consumidor más de la verdad del producto* — y la ficha de Play también lo es: la
marca cambió en el repo y su escaparate sigue prometiendo la anterior. Es exactamente el fallo que
`PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001` cerró para el **texto** de la ficha, sin
cerrarlo para la **imagen**.

## Diseño

No hay decisión de diseño pendiente: la marca ya está elegida y las fuentes HTML ya la llevan. Es
ejecución, y va **antes** de volver a tocar la ficha de Play (si no, se sube una ficha con icono
viejo y capturas nuevas, que es peor que no tocarla).

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
# icono y feature: comando exacto en docs/release/play-listing/README.md §Re-renderizar los gráficos
```

⚠️ Los feature graphics están **localizados a los 9 idiomas** desde el 02-09: son diez renders, no
uno. Y el icono de 512 se renderiza aparte del adaptive icon de la app — comparten la marca, no el
encuadre.

## Criterio de éxito

- `play-icon-512.png` y los diez feature graphics muestran la marca de 2026 (sin disco).
- Un diff visual contra el `paparcar_iconmark_*.xml` de master: mismo dibujo, mismo canal de la
  rueda derecha.
- La ficha no se sube a Play hasta que los tres —icono, feature, capturas— hablen de la misma build.

## Consumidores auditados

Pendiente de barrer al abrirlo: `grep -rl "play-icon-512\|feature-graphic" docs/ web/` para saber
qué más referencia esos bytes (el README de release y, posiblemente, el generador de la ficha).

---

## Resuelto — qué se hizo el 29-09-2026

**Primero, la comprobación que valida todo lo demás**: `python docs/branding/master/export_surfaces.py`
no movió **un solo byte**. Es lo que demuestra que `icon.html` y `feature.html` ya llevaban la marca
de 2026 y que lo único pendiente era rasterizar — no había nada que redibujar.

### Nace `tools/render_graphics.sh`

El README tenía un comando de Chrome que había que copiar, completarle una ruta absoluta a mano y
repetir diez veces. Eso no es una herramienta: es una nota. Y el resultado está medido — los PNG
llevaban **ocho días** con el logo de 2022 mientras su propia fuente ya tenía el de 2026.

El script hace las tres cosas que el comando dejaba al lector:
- **recorta las tres celdas** del icono (el row es 1536×512 y el vivo es la B, glifo a 0,72);
- **aplana a RGB**, porque Chrome escribe RGBA y Play quiere los dos gráficos opacos;
- **espera a la fuente** (`--virtual-time-budget=4000`). `feature.html` carga Plus Jakarta Sans por
  `file://` y pinta el tagline desde un script: sin esa espera la captura puede caer en la familia
  de respaldo, y eso **parece un render que salió bien**.

⚠️ Al escribirlo mordió una trampa que merece quedar escrita: `cygpath -w | sed 's|\|/|g'` (la
forma que usa `render.sh`) se rompió aquí, y con `set -eu` el fallo **no paró el script** — cayó al
fallback POSIX y Chrome rindió **igualmente** capturas del tamaño correcto. Se cambió a
`cygpath -m`, que ya devuelve barras normales. La lección es la de siempre en este ticket: el
tamaño del PNG no dice nada de su contenido; **hay que mirarlo**.

### Verificado mirando los píxeles, no el exit code

- Icono y gráfico `en` abiertos y comprobados a ojo: marca de 2026, sin disco.
- Las 9 franjas del tagline tienen **hash distinto** entre sí y ninguna está vacía — eso descarta de
  una vez «el `?lang=` no se aplicó» y «la fuente no cargó».
- Los tres iconos difieren entre ellos (no se cortó tres veces la misma celda).
- El gráfico de la raíz es **idéntico** al `en`, que es la relación que debe cumplirse.

### Y un arreglo de copy que salió al mirar el render

El banner francés escribía el apóstrofo RECTO (`n'y pensez`, `s'en souvient`), heredado de vivir
dentro de una cadena JS donde además había que escaparlo. En francés el recto es un sustituto de
máquina de escribir: la forma correcta es la tipográfica `’` (U+2019). Cambiado en `feature.html`
y re-renderizado sólo el `fr`. Es el ÚNICO idioma afectado — ninguno de los otros ocho taglines
lleva apóstrofo.

⛔ No confundir esta decisión con la regla de la app. Allí lo que se prohíbe es el ESCAPE: en
`composeResources` el apóstrofo va crudo porque `\'` se imprime tal cual, con la barra dentro
[COPY-APOSTROPHE-IS-NOT-ESCAPED-001]. Aquí no estamos en Compose Resources sino en HTML+JS, el
escape funciona, y lo que se decide no es cómo escribirlo sino QUÉ GLIFO se dibuja.

### Barrido de consumidores

| Consumidor de la marca en la ficha | Estado |
|---|---|
| `assets/play-icon-512.png` (celda B) | ✅ regenerado |
| `assets/alt-icon-512-small-glyph.png` (celda A) · `alt-icon-512-glow.png` (celda C) | ✅ regenerados |
| `assets/play-feature-graphic-1024x500.png` (= `en`) | ✅ regenerado |
| Los 9 `screenshots/<loc>/<loc>-feature-graphic-1024x500.png` | ✅ regenerados (derivados, gitignored) |
| `assets/alt-feature-graphic-no-name-repeat.png` | 🗑 **borrado**: marca de 2022 **y** copy superseded el 29-08, no regenerable desde la plantilla |
| `paste/` (los textos de los 9 idiomas) | ✅ `make_paste.py --check` → al día; el cambio de marca no toca copy |
| `web/assets/img/apple-touch-icon.png` | ✅ ya traía la marca nueva del ticket de marca |
| `web/assets/img/og-card.jpg` + `og-card-small.jpg` | 🔴 **SIGUEN CON LA MARCA DE 2022** → ticket propio |
| Las 72 capturas de teléfono | ✅ no llevan la marca dentro |

**El hallazgo que no estaba en el enunciado**: `web/tools/prepare-assets.py` construye la tarjeta
social de `paparcar.com` pegando **`play-icon-512.png`** dentro (línea ~191). O sea que el icono de
Play no es sólo el icono de Play: es también el logo de la tarjeta que se ve al compartir el enlace.
`apple-touch-icon` sí se rehízo en el ticket de marca; la `og-card` no, y ahí sigue el disco. No se
arregla aquí porque `prepare_social()` necesita el raw **es** de la sesión de Play, que no está
versionado (sólo lo está el set `en`). → `docs/backlog/web-the-social-card-still-wears-the-2022-mark-001.md`
