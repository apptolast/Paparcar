#!/usr/bin/env python3
"""Generate the App Store listing from the Play listing already in this repo.

The copy has ONE source of truth: `docs/release/play-listing/paste/play-<locale>.txt`, which is
what was actually published on Play and what the user reviews. Scraping the Play web page would
make the store a source instead of a consumer, and the repo would drift behind it silently — the
same mistake `web/build.mjs` exists to prevent for paparcar.com.

What this does NOT generate, on purpose:
  · subtitle.txt (30 chars) and keywords.txt (100 chars) — the App Store has them and Play does
    not, so there is nothing to derive them FROM. Inventing them in nine languages is exactly the
    kind of guess this project refuses. They are written by hand, reviewed, and left alone here.

Run:  python3 iosApp/fastlane/tools/metadata_from_play.py
      python3 iosApp/fastlane/tools/metadata_from_play.py --check   (verifies, writes nothing)
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
PASTE = ROOT / "docs" / "release" / "play-listing" / "paste"
OUT = ROOT / "iosApp" / "fastlane" / "metadata"

sys.path.insert(0, str(ROOT / "tools" / "release"))
from play_locales import PLAY_LOCALES, tagged_blocks  # noqa: E402

# Play's locale folders vs App Store Connect's language codes. Four of the nine differ, and getting
# one wrong is a silent no-op upload: deliver ignores a folder it does not recognise.
# Verified against FastlaneCore::Languages::ALL_LANGUAGES (fastlane 2.237).
#
# ⛔ The KEYS are Play's codes and must be exactly PLAY_LOCALES — `locale_coverage()` below checks
# it, because this table said `ro-RO` for 27 days and nothing noticed: Romanian on the App Store
# listing could not be regenerated at all, and the error was one line in a report that exits 0 when
# run without --check. Romanian on Play is `ro`, with no region, and `ro-RO` is an ANDROID locale.
# [PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001] [IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001]
LOCALES = {
    "es-ES": "es-ES",
    "en-US": "en-US",
    # ⛔ en-GB es el idioma PRINCIPAL de la ficha en App Store Connect (comprobado con la API el
    # 18-09), y Apple exige que el principal esté completo o no deja enviar. Sale del MISMO copy
    # inglés que en-US: la app no tiene recursos en-GB —cae a la base inglesa— así que las dos
    # fichas dicen y enseñan exactamente lo mismo. Duplicar aquí es más honesto que dejar vacío el
    # idioma que manda. [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
    "en-US|en-GB": "en-GB",
    "it-IT": "it",
    "pt-PT": "pt-PT",
    "fr-FR": "fr-FR",
    "de-DE": "de-DE",
    "nl-NL": "nl-NL",
    "pl-PL": "pl",
    "ro": "ro",
}


def locale_coverage():
    """Los codigos de Play que esta tabla no cubre, o que se inventa.

    Es el testigo de que las claves de arriba son las de Play: una clave de mas (`ro-RO`) deja un
    idioma de la ficha SIN poder regenerarse, y una de menos lo deja sin tocar.
    """
    keys = set(LOCALES) - {"en-US|en-GB"}
    return {
        "sin cubrir": [loc for loc in PLAY_LOCALES if loc not in keys],
        "inventados": sorted(loc for loc in keys if loc not in PLAY_LOCALES),
    }

# paparcar.com serves Spanish at the root and English under /en; there is no page for the other
# seven languages, so they point at the English one — an existing page beats an invented path.
ORIGIN = "https://paparcar.com"
PRIVACY_URL = f"{ORIGIN}/privacy-policy"          # single page, no locale prefix

# Categorías de la ficha, a nivel de APP (no por idioma). Decisión del user el 19-09: Navegación
# como primaria —es donde viven Parkopedia, EasyPark y compañía, y donde Play ya tiene a Paparcar
# (Mapas y navegación)— y Viajes como secundaria.
#
# ⛔ Viven aquí y no en la web de App Store Connect porque SIN ELLAS la subida entera muere: `deliver`
# intenta actualizar la app info, la API devuelve vacío y fastlane lo reporta como `No data`, un
# error que no menciona la palabra «categoría» por ningún lado. Medido el 19-09 en la primera
# subida real. [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
CATEGORIES = {
    "primary_category": "NAVIGATION",
    "secondary_category": "TRAVEL",
}

# App Store Connect's hard limits. A field over the limit is rejected at upload, not at review.
LIMITS = {"name": 30, "subtitle": 30, "promotional_text": 170, "keywords": 100, "description": 4000}


def site_base(app_locale: str) -> str:
    return ORIGIN if app_locale == "es-ES" else f"{ORIGIN}/en"


def parse_listing(text: str) -> dict:
    """The paste files are sectioned by `--- N. TITLE (used/limit) ---`, in Spanish in every
    language because the headers are internal labels, not copy."""
    parts = re.split(r"^--- \d+\.[^\n]*---\s*$", text, flags=re.M)
    if len(parts) < 4:
        raise ValueError("expected at least 3 sections (name, short, full)")
    return {"name": parts[1].strip(), "short": parts[2].strip(), "full": parts[3].strip()}


def release_notes(version_file: pathlib.Path) -> dict:
    """`release-notes-<version>.txt` carries every locale in `<xx-XX>` tags -- or `<ro>`.

    ⛔ Shared parser on purpose. This function had its own regex demanding `xx-XX`, so it read 8 of
    the 9 locales and Romanian was invisible. [IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001]
    """
    return tagged_blocks(version_file.read_text(encoding="utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="verify only, write nothing")
    ap.add_argument("--version-notes", default=None, help="release-notes-<x>.txt to use")
    args = ap.parse_args()

    def version_key(path):
        # By VERSION, never by mtime: touching an old file must not make it the current notes.
        return tuple(int(n) for n in re.findall(r"\d+", path.stem)) or (0,)

    notes_file = (PASTE / args.version_notes) if args.version_notes else max(
        PASTE.glob("release-notes-v*.txt"), key=version_key)
    notes = release_notes(notes_file)
    print(f"notas de versión: {notes_file.name} ({len(notes)} idiomas)")

    problems, written = [], 0
    coverage = locale_coverage()
    for loc in coverage["sin cubrir"]:
        problems.append(f"{loc}: locale de Play que la tabla LOCALES no cubre")
    for loc in coverage["inventados"]:
        problems.append(f"{loc}: no es un locale de Play (¿querías `{loc.split('-')[0]}`?)")
    for loc in sorted(set(notes) - set(PLAY_LOCALES)):
        problems.append(f"{loc}: locale que Play no admite en las notas")
    for play_locale, app_locale in LOCALES.items():
        play_file = play_locale.split("|")[0]
        src = PASTE / f"play-{play_file}.txt"
        if not src.exists():
            problems.append(f"{play_locale}: falta {src.name}")
            continue
        listing = parse_listing(src.read_text(encoding="utf-8"))
        base = site_base(app_locale)
        fields = {
            "name": listing["name"],
            "promotional_text": listing["short"],
            "description": listing["full"],
            "marketing_url": base,
            "support_url": f"{base}/support",
            "privacy_url": PRIVACY_URL,
        }
        if play_file in notes:
            fields["release_notes"] = notes[play_file]
        else:
            problems.append(f"{app_locale}: sin notas de versión en {notes_file.name}")

        for field, limit in LIMITS.items():
            if field in fields and len(fields[field]) > limit:
                problems.append(f"{app_locale}/{field}: {len(fields[field])} chars > {limit}")

        folder = OUT / app_locale
        for field, value in fields.items():
            target = folder / f"{field}.txt"
            if args.check:
                if not target.exists() or target.read_text(encoding="utf-8").rstrip("\n") != value:
                    problems.append(f"{app_locale}/{field}.txt desactualizado")
            else:
                folder.mkdir(parents=True, exist_ok=True)
                target.write_text(value + "\n", encoding="utf-8")
                written += 1
        for hand_written in ("subtitle", "keywords"):
            if not (folder / f"{hand_written}.txt").exists():
                problems.append(f"{app_locale}/{hand_written}.txt PENDIENTE (se escribe a mano)")

    # Las categorías son de la app, no de un idioma: fichero suelto en la raíz de metadata/.
    for field, value in CATEGORIES.items():
        target = OUT / f"{field}.txt"
        if args.check:
            if not target.exists() or target.read_text(encoding="utf-8").strip() != value:
                problems.append(f"{field}.txt desactualizado (debe decir {value})")
        else:
            OUT.mkdir(parents=True, exist_ok=True)
            target.write_text(value + "\n", encoding="utf-8")
            written += 1

    print(f"{'verificados' if args.check else 'escritos'} {written or len(LOCALES)*7} ficheros "
          f"en {len(LOCALES)} idiomas")
    if problems:
        print("\npendiente o incorrecto:")
        for p in problems:
            print(f"  · {p}")
    return 1 if (args.check and problems) else 0


if __name__ == "__main__":
    sys.exit(main())
