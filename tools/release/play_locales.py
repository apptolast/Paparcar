# -*- coding: utf-8 -*-
"""Los NUEVE codigos de locale de la ficha de Google Play, y como se leen.

Un codigo de locale de Play es de **Play**, no de Android. Hay tres espacios de nombres distintos
para el mismo idioma y confundirlos cuesta un rechazo del Console:

| | rumano |
|---|---|
| Carpeta de recursos de Android | `values-ro` |
| Locale de sistema / emulador | `ro-RO` <- valido AQUI, y por eso engana |
| **Ficha de Google Play** | **`ro`** -- el unico de nuestros nueve sin region |

El 30-09-2026 el Console rechazo las notas de 1.4.0 enteras: *«Se ha encontrado la traduccion a un
idioma no admitido: ro-RO»* [PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001].

⛔⛔ **Y la causa no estuvo en la lista, estuvo en el REGEX.** Los parsers leian el locale con
`[a-z]{2}-[A-Z]{2}`, que **no puede casar `ro`**: aunque la lista hubiera sido correcta, el
generador habria muerto diciendo «no tiene: nombre/ro». Los datos y el codigo estaban de acuerdo en
el mismo error, y por eso sobrevivio tres releases. Por eso aqui viven **las dos cosas**: la lista y
el patron. Arreglar una sin la otra ya se demostro insuficiente.

⛔ **Esto NO es la lista de todos. Dos sitios tienen la suya a proposito:**
  · `PlayListingPasteParityGuardrailTest.kt` es el **testigo** de `make_paste.py`. Si leyera esta
    lista, compararia el generador consigo mismo y no comprobaria nada — una prohibicion sin testigo
    no es un chequeo. Mantener las dos copias es la decision, no un olvido.
  · `docs/release/play-listing/tools/scan.py` usa `["en", "es", ...]`: son los nombres de las
    **carpetas de capturas** del device, otro espacio de nombres. No tocarlos desde aqui.

[IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001]
"""
import re

# Los nueve, en el orden en que se pegan en el Console (la base inglesa primero).
PLAY_LOCALES = ["en-US", "es-ES", "it-IT", "pt-PT", "fr-FR", "de-DE", "nl-NL", "pl-PL", "ro"]

# Fragmento para empotrar en el regex de cada consumidor: la REGION ES OPCIONAL.
LOCALE_PATTERN = r"[a-z]{2}(?:-[A-Z]{2})?"

# `release-notes-v<x>.txt`: un bloque por locale, `<xx-XX>` ... `</xx-XX>`.
TAGGED_BLOCK_RE = re.compile(r"<(%s)>\n(.*?)\n</\1>" % LOCALE_PATTERN, re.S)


def tagged_blocks(text):
    """{locale: cuerpo} de un fichero de notas de version.

    El cuerpo viene sin el salto final, que es lo que se pega en el Console y por tanto lo que
    cuenta para el tope de 500 caracteres por locale.
    """
    return {m.group(1): m.group(2).strip() for m in TAGGED_BLOCK_RE.finditer(text)}


# Los pegables `paste/play-<locale>.txt` van en secciones `--- 1. NOMBRE ... ---`, con las cabeceras
# en castellano en los nueve idiomas porque son etiquetas internas, no copy.
PASTE_SECTION_RE = re.compile(r"^--- \d+\.[^\n]*---\s*$", re.M)


def paste_sections(text):
    """{name, short, full} de un pegable de la ficha.

    ⛔ Vive aqui, con el resto de la lectura del copy de Play, y no copiado en cada consumidor: este
    formato ya se parseaba en `metadata_from_play.py` y la fase `verify` del corte necesitaba lo
    mismo. Dos parsers del mismo fichero es como nacio el fallo del rumano.
    """
    parts = PASTE_SECTION_RE.split(text)
    if len(parts) < 4:
        raise ValueError("esperaba al menos 3 secciones (nombre, breve, completa)")
    return {"name": parts[1].strip(), "short": parts[2].strip(), "full": parts[3].strip()}


def missing(found):
    """Los locales de Play que faltan en `found` (una lista, un dict o un set)."""
    return [loc for loc in PLAY_LOCALES if loc not in found]


def unexpected(found):
    """Los que sobran — tipicamente `ro-RO`, que es el error que da nombre a todo esto."""
    return sorted(loc for loc in found if loc not in PLAY_LOCALES)
