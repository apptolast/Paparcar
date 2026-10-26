# -*- coding: utf-8 -*-
"""Genera paste/ desde la ficha que nombra `published-listing.txt`: la ÚNICA fuente del copy.

⛔ El nombre de la ficha NO se escribe aquí. Vivía cableado en este fichero y, otra vez, en
`PlayListingPasteParityGuardrailTest`; así que escribir la ficha de un build nuevo y olvidar una de
las dos copias dejaba al guard verificando una ficha RETIRADA — verde y ciego.
[PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001]

⛔ Los ficheros de `paste/` NO se editan a mano. Existían a mano y por eso se quedaron dos
generaciones atrás del `.md`: seguían prometiendo «todos tus coches en un mismo garaje» cuando el
plan free ya topaba en uno, y les faltaba la corrección del paso 1 del 25-09.
[PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001]

    python docs/release/play-listing/tools/make_paste.py            # escribe
    python docs/release/play-listing/tools/make_paste.py --check    # solo comprueba

`PlayListingPasteParityGuardrailTest` hace la misma comprobación dentro de `./gradlew testAll`, que
es lo que de verdad la va a ejecutar.
"""
import io
import os
import pathlib
import re
import sys

# Los nueve codigos de Play y el patron que los lee —con la REGION OPCIONAL, que es donde estuvo el
# fallo del rumano— viven en un solo sitio. [IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001]
# parents: [0] tools · [1] play-listing · [2] release · [3] docs · [4] raiz del repo
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[4] / "tools" / "release"))
from play_locales import LOCALE_PATTERN, PLAY_LOCALES as LOCALES  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
LISTING_DIR = os.path.dirname(HERE)
POINTER = os.path.join(LISTING_DIR, "published-listing.txt")
PASTE_DIR = os.path.join(LISTING_DIR, "paste")


def published_listing():
    """El nombre de la ficha vigente. Una línea; se ignoran las vacías y las que empiezan por `#`."""
    for line in io.open(POINTER, encoding="utf-8").read().splitlines():
        name = line.strip()
        if name and not name.startswith("#"):
            return name
    raise SystemExit("%s no nombra ninguna ficha" % POINTER)


SOURCE_NAME = published_listing()
SOURCE = os.path.join(LISTING_DIR, SOURCE_NAME)

BANNER = "GENERADO por tools/make_paste.py desde %s — no editar a mano." % SOURCE_NAME


def _section(md, marker):
    """El trozo que sigue a un `<!-- paste-source: MARKER -->`.

    ⛔ Termina en el SIGUIENTE marcador, no en la siguiente cabecera de nivel 2: la variante
    descartada de la descripción breve es un `### ` dentro de la misma sección, y cortando por
    nivel 2 entraba en la tabla y GANABA por ser la última. Eso metía en `paste/` justo el texto
    del 29-08 que este ticket venía a quitar, y sin que nada chillara.
    """
    start = md.index("<!-- paste-source: %s -->" % marker)
    rest = md[start:]
    stops = [i for i in (rest.find("\n<!-- paste-source:", 1), rest.find("\n## ", 1)) if i != -1]
    return rest[: min(stops)] if stops else rest


def _table(md, marker):
    """{locale: texto} de una tabla cuyas filas son: pipe, locale, pipe, texto entre backticks.

    ⛔ La región es OPCIONAL — el rumano de Play es `ro` a secas, y con `[a-z]{2}-[A-Z]{2}` el
    parser no podía ni verlo. El patrón sale de `play_locales.LOCALE_PATTERN` para que esa regla
    tenga UN dueño: el mismo formato se leía aquí, en la fase de notas del corte y en la ficha de
    App Store con tres regex distintas, y una de las tres seguía exigiendo región.
    [PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001] [IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001]
    """
    out = {}
    for line in _section(md, marker).splitlines():
        m = re.match(r"^\|\s*(%s)\s*\|\s*`(.+?)`\s*\|" % LOCALE_PATTERN, line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def _full_descriptions(md):
    """{locale: texto} de los fences bajo `## Descripción completa`."""
    body = md[md.index("## Descripción completa"):]
    out = {}
    for m in re.finditer(r"^### (%s)\s*\n+```\n(.*?)\n```" % LOCALE_PATTERN, body, re.S | re.M):
        out[m.group(1)] = m.group(2)
    return out


def read_source():
    md = io.open(SOURCE, encoding="utf-8").read()
    names, shorts, fulls = _table(md, "app-name"), _table(md, "short-description"), _full_descriptions(md)
    missing = [
        "%s/%s" % (kind, loc)
        for kind, table in (("nombre", names), ("breve", shorts), ("completa", fulls))
        for loc in LOCALES
        if loc not in table
    ]
    if missing:
        raise SystemExit("%s no tiene: %s" % (SOURCE_NAME, ", ".join(missing)))
    return names, shorts, fulls


def render_locale(loc, name, short, full):
    return (
        u"FICHA DE PLAY STORE — %s\n"
        u"%s\n"
        u"%s\n\n"
        u"--- 1. NOMBRE DE LA APLICACION (%d/30) ---\n%s\n\n"
        u"--- 2. DESCRIPCION BREVE (%d/80) ---\n%s\n\n"
        u"--- 3. DESCRIPCION COMPLETA (%d/4000) ---\n%s\n"
    ) % (loc, "=" * 60, BANNER, len(name), name, len(short), short, len(full), full)


def render_import(names, shorts, fulls):
    head = (
        u"# Paparcar — Google Play store listing (9 locales)\n\n"
        u"<!-- %s -->\n\n"
        u"Import these texts VERBATIM. Do not rewrite, rephrase, shorten or translate them:\n"
        u"every locale below is already translated and character-counted.\n"
        u"App name max 30 chars. Short description max 80 chars. Full description max 4000 chars.\n"
    ) % BANNER
    parts = [head]
    for loc in LOCALES:
        parts.append(
            u"\n---\n\n## Locale: %s\n\n### App name\n%s\n\n### Short description\n%s\n\n"
            u"### Full description\n%s\n" % (loc, names[loc], shorts[loc], fulls[loc])
        )
    return "".join(parts)


def main():
    check = "--check" in sys.argv
    names, shorts, fulls = read_source()

    wanted = {
        "play-%s.txt" % loc: render_locale(loc, names[loc], shorts[loc], fulls[loc])
        for loc in LOCALES
    }
    wanted["play-listing-import.md"] = render_import(names, shorts, fulls)

    stale = []
    for filename, text in sorted(wanted.items()):
        path = os.path.join(PASTE_DIR, filename)
        current = io.open(path, encoding="utf-8").read() if os.path.isfile(path) else None
        if current == text:
            continue
        stale.append(filename)
        if not check:
            io.open(path, "w", encoding="utf-8", newline="\n").write(text)

    if check:
        if stale:
            raise SystemExit(
                "paste/ no coincide con %s: %s\n"
                "Regenera con: python docs/release/play-listing/tools/make_paste.py"
                % (SOURCE_NAME, ", ".join(stale))
            )
        print("paste/ al dia (%d ficheros)" % len(wanted))
    else:
        print("escritos %d de %d ficheros" % (len(stale), len(wanted)))


if __name__ == "__main__":
    main()
