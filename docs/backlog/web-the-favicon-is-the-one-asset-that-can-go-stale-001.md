# WEB-THE-FAVICON-IS-THE-ONE-ASSET-THAT-CAN-GO-STALE-001 · el icono de pestaña es el único asset del sitio sin hash de contenido

**Estado:** ✅ **Done** — sin commitear, a la espera de permiso. Rama
`fix/WEB-THE-FAVICON-IS-THE-ONE-ASSET-THAT-CAN-GO-STALE-001-favicon` · worktree `../Paparcar-favicon`

Lo reportó el user (30-09-2026): *«la web, falta el icono de la pestaña, sigue el logo antiguo»*.

## Problema

**El favicon desplegado ya es correcto** — descargado de `paparcar.com`, dibuja los tres paths de
2026 con su verde por tema. Lo que el user ve es su navegador sirviendo una copia vieja. Pero eso
no es mala suerte: es la única parte del sitio donde **puede** pasar.

**1 · Es el único asset visual sin hash de contenido.** Todo lo demás se publica como
`nombre.<hash>.ext` con `immutable`, así que un cambio de contenido **es una URL nueva** y una copia
cacheada nunca puede ser la equivocada — eso es literalmente lo que `firebase.json` dice que se gana
con `immutable`. El favicon vive en `/favicon.svg`, ruta fija, está en la lista `HAND_WRITTEN` de
`build.mjs` y por eso se salta `publishAssets()`. Cambió de dibujo el 29-09 (`01310a3b3`) y la URL
no cambió.

**2 · Ninguna regla de cabeceras lo cubre.** Medido en vivo:

```
/favicon.svg                 Cache-Control: max-age=3600          <- el DEFECTO de Firebase
/assets/css/site.<hash>.css  Cache-Control: public, max-age=31536000, immutable
/  ·  /terms  ·  *.html      Cache-Control: public, max-age=0, must-revalidate
```

Las cuatro reglas de `firebase.json` cubren `/assets/**`, `*.@(html|xml|txt)`, las rutas sin
extensión y las raíces de locale. **Un `.svg` en la raíz no casa con ninguna**, así que hereda el
`max-age=3600` por defecto — y los navegadores cachean el favicon bastante más de lo que esa
cabecera dice, porque lo guardan por origen y no por documento.

**3 · No hay `favicon.ico`, y nunca lo ha habido.** `/favicon.ico` → **404**, comprobado en vivo;
`git log --diff-filter=A -- "*.ico"` no devuelve nada. El sitio declara **sólo** un favicon SVG. Los
navegadores piden `/favicon.ico` de todos modos —para marcadores, historial y como respaldo— y los
que no soportan favicon SVG se quedan sin icono. Tampoco hay manifest ni iconos 192/512, así que
«añadir a pantalla de inicio» en Android no tiene qué enseñar.

## Doctrina violada

- ***La web es un consumidor más de la verdad del producto***: un cambio de marca que no llega al
  usuario **no ha llegado**. Y aquí no llegaba por construcción, no por olvido.
- El propio `firebase.json` explica por qué el hash de contenido es lo que hace segura una caché
  larga. El favicon quedó fuera de ese sistema sin que nadie lo decidiera.
- ***Sistemas, no parches***: re-desplegar no arregla nada — el siguiente cambio de marca se
  volvería a quedar pegado.

## Señales / datos disponibles

Contraste medido (30-09-2026), que es lo que decide el color del `.ico` — **no puede ser temático**
como el SVG, así que necesita un color que aguante las dos pestañas:

| | pestaña clara (`#F1F3F4` / `#FFF`) | pestaña oscura (`#292A2D` / `#3A3A3C`) |
|---|---|---|
| `PapGreen` `#25F48C` | **1,31 / 1,46:1** ⛔ | 9,84 / 7,78:1 |
| `PapGreenLight` `#009F5E` | 3,08 / 3,43:1 | 3,31 / 4,18:1 |
| Baldosa `#0D1117` | (12,97:1 por dentro) | **1,32:1 contra la pestaña** ⛔ |

`PapGreenLight` es **el único que no se hunde en ningún lado**. Y no es un color nuevo: es el fill
base que el propio `favicon.svg` ya usa — su `@media (prefers-color-scheme: dark)` sólo lo cambia a
`PapGreen` cuando puede permitírselo.

## Diseño

**El favicon deja de ser un caso especial y pasa por donde pasa todo lo demás.**

1. **El SVG se convierte en asset con hash.** `export_surfaces.py` lo escribe en
   `web/assets/img/favicon.svg`; `publishAssets()` lo publica como `/assets/img/favicon.<hash>.svg`
   y el `<link rel="icon">` apunta ahí. Cambiar el dibujo pasa a ser **una URL nueva**, que es la
   única garantía que de verdad funciona. Se sigue escribiendo la copia de raíz `/favicon.svg` para
   quien la pida a pelo.
2. **Nace `favicon.ico`** (16/32/48 en un solo fichero), en `PapGreenLight` sobre transparente —
   el mismo fill que el SVG en su caso base, así que en pestaña clara los dos son idénticos y en
   oscura el `.ico` se queda en 4,18:1, que sigue siendo legible.
3. **Nacen `icon-192` e `icon-512` + `site.webmanifest`.** Esos sí son **baldosas** (aterrizan sobre
   un fondo ajeno), así que reutilizan `TILE_STORE` y `F_STORE_ICON`, como el icono de iOS y el de
   Play. ⛔ El manifest se queda **sin `display`**: declara nombre, colores e iconos y nada más, para
   que el sitio no empiece a ofrecerse como app instalable — eso sería un cambio de producto que
   nadie ha pedido.
4. **`firebase.json` gana la regla que falta** para las tres rutas sin hash (`/favicon.ico`,
   `/favicon.svg`, `/site.webmanifest`): `max-age=0, must-revalidate`, igual que ya hace con
   `/assets/manifest.json`, que es el otro fichero no hasheado y lleva su propia nota diciendo
   justo esto.

La rasterización sigue siendo de Chrome vía `render_graphics.sh`, que gana el target `favicon`. El
`.ico` multi-tamaño lo compone Pillow, que sabe guardarlo.

## Criterio de éxito

- El `<link rel="icon">` de las 7 páginas construidas **y de las dos legales** apunta a una URL con
  hash; cambiar el dibujo cambia la URL.
- `/favicon.ico` responde **200** y trae los tres tamaños dentro.
- Las tres rutas sin hash responden con `must-revalidate`, no con el `max-age=3600` por defecto.
- Un testigo que falsifique el defecto: hoy nada impide que un asset visual vuelva a servirse sin
  hash.
- `./gradlew testAll --rerun-tasks` verde y `node web/build.mjs --check` limpio.

## Consumidores auditados

| Sitio | Antes | Después |
|---|---|---|
| `layout.mjs` (`<head>` de las 7 páginas) | `/favicon.svg` fijo | `asset(...)` con hash + manifest |
| `restyle-legal.mjs` (las 2 legales) | `/favicon.svg` fijo | igual, por el mismo helper |
| `build.mjs` · `HAND_WRITTEN` | incluye `favicon.svg` | sigue: el fichero es del exportador, no del build |
| `export_surfaces.py` | escribe `hosting/public/favicon.svg` | escribe la fuente en `web/assets/img/` **y** la copia de raíz |
| `firebase.json` | 4 reglas, ninguna cubre la raíz `.svg`/`.ico` | + 1 regla para las tres rutas sin hash |
| `apple-touch-icon` | ya con hash desde `f4181114e` | **sin cambio** |


---

## Verificado

- El `<link rel="icon">` de las **7 páginas y de las dos legales** apunta a
  `/assets/img/favicon.<hash>.svg`. Los tres `<head>` distintos que lo declaraban pasan por un solo
  helper, `iconLinks()` — ya habían derivado: el del 404 no llevaba `apple-touch-icon`.
- `favicon.ico` con los **tres tamaños dentro**, comprobado **reabriendo el fichero escrito**:
  guardar sin error no prueba que estén. Visto ampliado sobre pestaña clara y oscura: legible en los
  tres, incluido el 16.
- El manifest sale con sus dos iconos **hasheados**, y sin `display` ni `start_url`, para no
  convertir el sitio en app instalable sin que nadie lo haya decidido.
- `./gradlew testAll --rerun-tasks` verde · `node web/build.mjs --check` limpio.

## El testigo, y por qué la primera versión no valía

`WebSiteParityGuardrailTest` gana el chequeo 7. **Falsificado en sus tres casos** — pero el primer
intento salió **VERDE al romperlo**, y esa es la parte que merece quedar escrita.

La lista blanca incluía `/favicon.svg`, así que volver a enlazar la ruta fija —**el defecto
exacto de este ticket**— pasaba desapercibido. Ser **una puerta** (un fichero que existe en la raíz
porque el navegador lo pide por su nombre) y estar **permitido como referencia de una página** son
dos cosas distintas, y mezclarlas convertía el guard en decoración. Ahora son dos listas:
`DOORWAYS` (existen y revalidan) y `LINKABLE_UNHASHED` (`/favicon.ico` y `/site.webmanifest`, y
nada más). Con esa separación, revertir el enlace se pone rojo.

*Un guard sólo vale lo que vale su falsificación: sin correrla, éste habría entrado creyendo que
vigilaba algo.*

## Trampas de literal pisadas (Kotlin)

Dos, y ninguna da un error que apunte a su causa:

- **`/assets/**` dentro de un KDoc abre un comentario ANIDADO.** Kotlin admite `/* */` anidados, así
  que esa secuencia `/**` dejó el fichero entero sin cerrar. El compilador reporta *«Unclosed
  comment»* en la ÚLTIMA línea y decenas de *«Unresolved reference»* desde la primera — ninguna
  señala la ruta escrita en la prosa.
- **Una regex que termina en `"` no cabe en un raw string**: `..."""` serían cuatro comillas. Y al
  pasarla a string normal, `\.` es un **escape ilegal** en Kotlin. Se resolvió con `[.]`, que dice
  lo mismo sin un solo backslash.

## Lo que no cubre

⚠️ Esto arregla el **futuro**: a partir de aquí un cambio de dibujo viaja en una URL nueva. A quien
ya tenga el favicon viejo cacheado le llegará cuando su navegador revalide la nueva cabecera —
inmediato para la mayoría, porque el `<link>` apunta a otra URL desde el primer despliegue. Un
usuario con la pestaña abierta puede seguir viendo el anterior hasta recargar.

