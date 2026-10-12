# BRAND-THE-README-MARK-NEEDS-ITS-LIGHT-GREEN-001 · el logo del README daba 1,42:1 en tema claro

**Estado:** ✅ Done · la superficie pasa a `themed=True` · ⏳ **sin ver en GitHub** (no se puede desde aquí)

## Problema

`docs/assets/paparcar-logo.svg` —el logo de la cabecera y del pie del README de la raíz— se generaba
con `PapGreen` **horneado**. El README se lee en GitHub, y en tema claro eso cae sobre blanco:
**1,42:1**, que es exactamente el número que `PapGreenLight` (#009F5E, **3,01:1**) existe para
resolver, y el único ajuste por superficie que la marca admite
[BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001].

Lo dejó abierto el barrido del README del 30-09
([DOCS-THE-README-MUST-MATCH-THE-TREE-001](docs-the-readme-must-match-the-tree-001.md)) y seguía
abierto trece días después.

## Doctrina violada

La del color de la marca, escrita en `CLAUDE.md`: **sobre oscuro `PapGreen`, sobre claro
`PapGreenLight`**, «y no es gusto: #25F48C sobre blanco da 1,42:1 — medido». Una superficie que se
lee en un tema que no controlamos no puede hornear uno de los dos.

## Señales / datos disponibles

Todo estaba ya en el generador, y eso es lo que hace que el arreglo sea pequeño:

- `GREEN = '#25F48C'` y `GREEN_LIGHT = '#009F5E'` declarados, con los contrastes en el comentario.
- `svg_mark(note, color=None, themed=False, fraction=F_MARK)` **ya aceptaba `themed`**, y su rama
  hace exactamente esto: `fill` = verde profundo y un `<style>` con
  `@media (prefers-color-scheme: dark)` que lo sube a `PapGreen`.
- Lo usaba **una** superficie: el favicon, con la razón escrita — *«la pestaña del navegador no la
  controlamos»*. El README de GitHub es el mismo problema.

## Diseño

Una línea: `docs/assets/paparcar-logo.svg` se genera con `themed=True`.

⛔ **Ni `<picture>` ni un segundo fichero**, que es lo que el ticket original proponía. Habría que
tocar el README en **dos** sitios (cabecera, 132 px, y pie, 40 px), mantener dos superficies en vez
de una, y además duplicar una decisión que el proyecto ya había tomado para el favicon. El logo del
README no es un caso nuevo: es el mismo caso.

⚠️ **Degrada bien.** Si el sanitizador de GitHub se come el `<style>`, lo que queda es el `fill` del
`<svg>`, que ya es el verde profundo: el peor caso es verde profundo en los **dos** temas —legible en
ambos— y nunca el 1,42:1 de antes. Es la misma apuesta que ya acepta el favicon.

## Criterio de éxito

Regenerado con `python docs/branding/master/export_surfaces.py`: el SVG abre con
`fill="#009F5E"` y lleva su `@media (prefers-color-scheme: dark) { path { fill: #25F48C; } }`.

⛔ **El generador tocó UNA sola superficie.** `git status` tras regenerar: `paparcar-logo.svg` y el
propio `export_surfaces.py`, nada más — lo que confirma que sigue siendo determinista y que ninguna
otra superficie derivó por el camino. `node web/build.mjs --check`: sin cambios en `hosting/public`,
porque esta superficie no entra en la web (la web hornea su logo desde `web/lib/icons.mjs`, que lee
el maestro aparte).

⏳ **Lo que NO se puede verificar desde aquí**: cómo lo rinde GitHub. Ni el contraste final ni si su
sanitizador conserva el `<style>`. Hay que abrir el README en github.com con cada tema.

## Consumidores auditados

- `README.md` — ✔ **sin cambios**, y eso es el punto: usa el logo en la cabecera (132 px) y en el pie
  (40 px), y las dos heredan el arreglo sin tocar el markdown.
- `docs/branding/README.md` — ✔ su tabla de superficies pasa de `#25F48C` a «según
  `prefers-color-scheme`», la categoría que ya tenía el favicon.
- `web/lib/icons.mjs` (`logoMark`) y los dos HTML de `play-listing/assets/` — ✔ exentos: el propio
  `docs/branding/README.md` los declara «a mano, porque no son plantillas», y la web resuelve su
  verde por su cuenta.
- Nada rasteriza `docs/assets/paparcar-logo.svg` (`grep` en `render_graphics.sh`, `web/`,
  `play-listing/`) — ✔ un PNG tomaría el fill por defecto, y aquí no hay ninguno.
