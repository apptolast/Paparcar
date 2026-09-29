# PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001 · el emblema de un banner no es la marca llenando su caja

**Estado:** ✅ **Done** (30-09-2026) · rama `chore/PLAY-THE-BANNER-MARK-KEEPS-ITS-DISC-001-disc`
· worktree `../Paparcar-banner-disc`

## Problema

`BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001` quitó el disco de **todas** las superficies, y en casi
todas fue lo correcto: la marca de 2026 se sostiene sola. En el **gráfico de funciones de Play** no,
y el user lo vio mirando el render: la marca queda flotando pequeña al lado del wordmark.

**Y el motivo no es de gusto, está escrito en el propio exportador.** `PLAY_FRACTIONS` dice:

> ⛔ Las fracciones NO son nuevas: son las que ya tenían esos gráficos, expresadas como diámetro del
> envolvente sobre el lienzo 108. Se conservan a propósito — el icono de Play está publicado y esto
> cambia el dibujo, no su tamaño óptico.

O sea: el banner se quedó con `0.52896`, una fracción **calculada para vivir dentro de un disco**, y
se le quitó el disco. El tamaño óptico se conservó, pero lo que lo justificaba desapareció. Medido
en el render: la marca ocupa ~46 px al lado de un wordmark de 82 px.

## Doctrina violada

Ninguna, y eso es lo importante de este ticket. La regla `⛔ El logo NO lleva disco` **no dice** que
ningún lienzo pueda llevar fondo: ya exceptúa `ic_launcher_background`, el `apple-touch-icon` y el
512 de Play como «la baldosa que el sistema exige opaca». Lo que faltaba era nombrar el caso que no
es una baldosa del sistema sino una decisión de composición.

**La regla queda así, y es la que zanja el caso siguiente:** *donde la marca llena su caja
(`F_MARK = 1.0`), no hay disco; donde es un emblema dentro de una composición ajena —con un wordmark
al lado y un fondo que no es suyo—, el soporte es legítimo.* Escrito en CLAUDE.md § Iconos.

## Diseño

Dos `<circle>` delante del grupo del glifo en `feature.html`, exactamente los de `PLAY-LISTING-001`:
tinta `#0D1117` a r=54 y aro `PapGreen` al 28 % a r=52,5. **La fracción del glifo no se toca**: era
la correcta desde el principio, sólo le faltaba el soporte.

⛔ No se toca ninguna otra superficie. El icono 512 ya tiene su baldosa cuadrada y la app sigue
`F_MARK = 1.0`.

## Criterio de éxito

- Los diez gráficos (base + 9 idiomas) llevan el disco con la marca de 2026 dentro.
- `export_surfaces.py` sigue pudiendo reescribir el glifo **sin borrar el disco** — su `MARK_GROUP`
  sólo casa el `<g>`, y los dos `<circle>` quedan fuera.
- La regla escrita, para que el próximo barrido de marca no lo «arregle» de vuelta.

## Consumidores auditados

| Sitio que afirma «sin disco» | Estado |
|---|---|
| `CLAUDE.md` § Iconos | ✅ recoge la excepción y su regla mental |
| `docs/branding/master/export_surfaces.py` (docstring ⛔⛔ SIN DISCO) | ✅ nota añadida, con el porqué |
| `docs/release/play-listing/LISTING.md` § 3 | ✅ la composición vuelve a nombrar el disco |
| `feature.html` (comentario en el sitio) | ✅ dice por qué está y por qué no abre la puerta |
| `icon.html`, iconmarks, splash, notificación, favicon, web | — intactos, siguen sin disco |

## Verificación

Muestreo de los 9 PNG en el centro del disco y en su aro: tinta `(13,17,23)` = `#0D1117` exacto y
aro verde en los nueve. Y `export_surfaces.py` corrido **después** del cambio: no movió un byte del
disco.
