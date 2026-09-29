# WEB-THE-SOCIAL-CARD-STILL-WEARS-THE-2022-MARK-001 · la tarjeta que se ve al compartir paparcar.com lleva el logo retirado

**Estado:** 🔵 Abierto · sin rama · hallazgo del barrido de consumidores de
`PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001` (29-09-2026).

## Problema

`web/assets/img/og-card.jpg` (1200×630) y su `og-card-small.jpg` (600×315) son la imagen que
WhatsApp, Slack, X, LinkedIn o iMessage enseñan cuando alguien pega el enlace de `paparcar.com`.
Arriba a la izquierda llevan **la marca de 2022, con su disco**, horneada dentro del JPEG.

No es un descuido aislado, es un consumidor que nadie sabía que lo era:

```python
# web/tools/prepare-assets.py, prepare_social()
logo = Image.open(ROOT / "docs/release/play-listing/assets/play-icon-512.png").convert("RGBA")
logo = logo.resize((84, 84), Image.LANCZOS)
card.paste(logo, (80, 74), logo)
```

**El icono de Play no es sólo el icono de Play**: es también el logo de la tarjeta social. Mientras
`play-icon-512.png` tuvo la marca vieja, cualquier regeneración de la tarjeta la volvía a hornear.
Su hermano `prepare_touch_icon()` lee el mismo fichero, y ése sí se rehízo en el ticket de marca —
por eso `apple-touch-icon.png` está bien y la `og-card` no: **se regeneraron por separado**.

## Doctrina violada

*La web es un consumidor más de la verdad del producto.* Y una capa más: *el logo se GENERA, no se
copia* — pero aquí se copia **dentro de un JPEG**, que es la forma de copia que ningún `grep`
encuentra. `WebSiteParityGuardrailTest` vigila hechos, claves, gates, tokens y salida construida;
**ninguno de sus cinco chequeos mira dentro de una imagen**.

## Por qué no se arregló en el ticket que lo encontró

`prepare_social()` recibe `es_dir / "shot1.png"`: el raw **en español** de la sesión de capturas de
Play. El repo sólo versiona el set `en` (`docs/release/play-listing/assets/screenshots/raw/`, que es
el de Dublín, en inglés) — los otros ocho idiomas son derivados gitignored. Con el raw `en` saldría
una tarjeta con copy español y teléfono inglés, que es peor que la que hay.

⛔ **Y no vale pegarle el logo nuevo encima al JPEG actual**: eso es exactamente la copia a mano que
el maestro paramétrico vino a matar, y deja el fichero sin forma de reproducirse.

## Diseño

Dos caminos, y el segundo es el que cierra la familia entera:

1. **El barato**: recuperar (o volver a tomar) el raw `es` de la sesión de Play y correr
   `python web/tools/prepare-assets.py <es-raw> <en-raw>`. Arregla ESTA tarjeta, hoy.
2. **El que arregla la clase**: que `prepare_social()` no pegue un PNG rasterizado, sino que dibuje
   la marca desde el maestro —igual que `export_surfaces.py` hace con las otras 21 superficies— y
   que el raw que usa la tarjeta sea **versionado**, no un archivo suelto de una sesión. Mientras la
   tarjeta dependa de un fichero que no está en el repo, se queda atrás en cuanto nadie lo tenga.

## Criterio de éxito

- `og-card.jpg` y `og-card-small.jpg` muestran la marca de 2026 sin disco.
- Regenerar dos veces seguidas da el mismo byte (la tarjeta es reproducible desde el repo solo).
- Un chequeo que falsifique el defecto: hoy no existe ninguno que mire dentro de una imagen.

## Consumidores auditados

`grep -rn "play-icon-512" web/` → `prepare-assets.py` en dos sitios: `prepare_social()` (roto) y
`prepare_touch_icon()` (ya correcto). Fuera de `web/`, el icono sólo lo consume la propia ficha de
Play.
