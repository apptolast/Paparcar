# WEB-A-DERIVED-ASSET-MUST-MATCH-ITS-GENERATOR-001 · tres derivados de `web/` no son lo que su generador produce hoy

**Estado:** 🔵 Abierto (sólo el testigo; el caso 1 ya lo corrigió master) · sin rama · dos
hallazgos colaterales de
[WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001](web-the-mark-wears-its-disc-on-the-site-001.md)
(30-09-2026). Ninguno se arregló allí: el primero es ruido ajeno al encargo y el segundo cambia un
icono que el user declaró **exento** de aquel ticket.

## Problema

`web/` tiene derivados commiteados —los escribe un script, no una persona— que **no coinciden con
lo que ese script produce hoy**. Nada lo comprueba, así que sólo se descubren cuando alguien vuelve
a correr el generador y ve cambiar ficheros que no ha tocado.

### 1 · Dos assets llevan en el nombre un hash que no es el suyo

`web/build.mjs` publica cada asset como `<nombre>.<sha256[0:8]>.<ext>`. Dos de los commiteados no
hashean a su propio nombre:

| commiteado como | sha256[0:8] de su contenido |
|---|---|
| `hosting/public/assets/css/legal.223eea16.css` | `c62e5db9` |
| `hosting/public/assets/js/site.af2f7e02.js` | `9feafadd` |

Medido byte a byte: el contenido es **idéntico** al reconstruido (4755 y 3857 bytes, cero CR). Sólo
cambia el nombre. Los nombres se calcularon en un build donde esos bytes llevaban **CRLF**, y al
commitear el `.gitattributes` (`* text=auto eol=lf`) los normalizó a LF: el nombre se quedó con el
hash de antes y el contenido con el de después.

Es el mismo `.gitattributes` que [BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001] llamó *«funcionó a
medias»*, pero un escalón más abajo de lo documentado entonces: allí el ruido era un `M` con diff
vacío —inocuo—, aquí es un **renombrado**, que sí entra en el árbol de quien reconstruya.

> ✅ **Ya corregido en master, y no por este ticket**: el release `8c45ce88a` (1.4.0) reconstruyó el
> sitio y publicó los dos con su nombre bueno — `legal.c62e5db9.css` y `site.9feafadd.js`, los
> mismos que había calculado WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001 antes de rebasar. Dos
> reconstrucciones independientes llegando al mismo nombre es la confirmación del diagnóstico:
> el contenido siempre fue ése, lo que estaba mal era la etiqueta.
>
> ⛔ **Que se arreglara solo es exactamente el problema.** Se arregló porque alguien reconstruyó,
> no porque nada lo comprobara — y volverá a separarse la próxima vez que se construya desde un
> checkout con CRLF. Lo que queda abierto es el testigo, no el síntoma.

### 2 · El `apple-touch-icon` no es el que produce su propia función

`prepare_touch_icon()` dice, y hace, una cosa: el touch icon **es** `play-icon-512.png`
redimensionado a 180. Pero el commiteado no es eso — lleva la marca **notablemente más grande**.
Medido: 31 832 de 32 400 píxeles distintos entre el commiteado y el que la función genera hoy.

Los dos llevan la marca de 2026; lo que cambia es la fracción. Viene de que en
[BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001] el touch icon y el icono de Play **se regeneraron por
separado** —el propio doc de [PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001] lo dice— y el de
Play acabó con la fracción de `icon.html` (`0.51458`), que es más pequeña.

⛔ **Consecuencia práctica, y es la que muerde**: cualquiera que corra `prepare-assets.py` por
cualquier otro motivo **encoge el touch icon sin pretenderlo**. Pasó en este mismo ticket; se
revirtió a mano al detectarlo, porque el user había declarado esa superficie exenta.

## Doctrina violada

*El logo se GENERA, no se copia* — y su corolario no escrito: **lo generado tiene que corresponder
a lo que su generador produce**. Si no, el generador es decorativo y el fichero es una copia a mano
con otro nombre.

`node web/build.mjs --check` verifica enlaces, anclas y páginas a mano. `WebSiteParityGuardrailTest`
verifica hechos, claves, gates, tokens, salida construida y —desde el ticket que abrió éste— que la
marca venga del maestro. **Ninguno comprueba que un derivado sea el que su generador escribe.**

## Diseño

Un solo chequeo cubre los dos casos, y es el cálculo que los scripts ya hacen, leído al revés:
regenerar a un directorio temporal y comparar. Concretamente:

1. **Assets con hash**: recorrer `hosting/public/assets`, recalcular el sha256 y compararlo con el
   que lleva el nombre. Barato y exacto.
2. **Touch icon**: regenerarlo en memoria desde `play-icon-512.png` y comparar con el commiteado.
3. Decidir además si `build.mjs` debe **normalizar a LF** lo que escribe. Hoy copia bytes tal cual,
   así que el hash depende del checkout de quien construya — que es la raíz del caso 1 y no se
   arregla sólo con detectarlo.

⚠️ Antes del caso 2 hay una decisión de diseño, no de código: **cuál de los dos tamaños es el
bueno**. Si vale el grande —el que está publicado—, lo que hay que cambiar es la función, no el
PNG; y entonces el touch icon deja de ser «el icono de Play redimensionado» y necesita su propia
fracción. Es del user.

## Criterio de éxito

- Reconstruir en un clon limpio no cambia ningún derivado que nadie haya tocado.
- Un testigo que falsifique los dos defectos: renombrar un asset a mano, o dejar el touch icon
  desfasado de su fuente, tiene que poner la suite en rojo.

## Consumidores auditados

- `grep -rn "assets/" hosting/public/*.html` → las páginas referencian los assets por el nombre del
  manifiesto, así que un nombre equivocado **no rompe nada mientras el fichero exista con ese
  nombre**. Por eso lleva ahí sin que nadie lo note; rompería el día que alguien barra huérfanos.
- `grep -rn "apple-touch-icon" web/ hosting/` → lo referencia el `<head>` del sitio y lo escribe
  sólo `prepare_touch_icon()`. Un consumidor, un productor, y no coinciden.
