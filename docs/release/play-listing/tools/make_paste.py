# -*- coding: utf-8 -*-
"""Genera paste/ desde LISTING-v1.3.0.md, que es la ÚNICA fuente del copy que se pega.

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
import re
import sys

LOCALES = ["en-US", "es-ES", "it-IT", "pt-PT", "fr-FR", "de-DE", "nl-NL", "pl-PL", "ro-RO"]

HERE = os.path.dirname(os.path.abspath(__file__))
LISTING_DIR = os.path.dirname(HERE)
SOURCE = os.path.join(LISTING_DIR, "LISTING-v1.3.0.md")
PASTE_DIR = os.path.join(LISTING_DIR, "paste")

BANNER = "GENERADO por tools/make_paste.py desde LISTING-v1.3.0.md — no editar a mano."


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
    """{locale: texto} de una tabla cuyas filas son: pipe, locale, pipe, texto entre backticks."""
    out = {}
    for line in _section(md, marker).splitlines():
        m = re.match(r"^\|\s*([a-z]{2}-[A-Z]{2})\s*\|\s*`(.+?)`\s*\|", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def _full_descriptions(md):
    """{locale: texto} de los fences bajo `## Descripción completa`."""
    body = md[md.index("## Descripción completa"):]
    out = {}
    for m in re.finditer(r"^### ([a-z]{2}-[A-Z]{2})\s*\n+```\n(.*?)\n```", body, re.S | re.M):
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
        raise SystemExit("LISTING-v1.3.0.md no tiene: %s" % ", ".join(missing))
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
                "paste/ no coincide con LISTING-v1.3.0.md: %s\n"
                "Regenera con: python docs/release/play-listing/tools/make_paste.py" % ", ".join(stale)
            )
        print("paste/ al dia (%d ficheros)" % len(wanted))
    else:
        print("escritos %d de %d ficheros" % (len(stale), len(wanted)))


if __name__ == "__main__":
    main()
