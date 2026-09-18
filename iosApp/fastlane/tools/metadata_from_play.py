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

# Play's locale folders vs App Store Connect's language codes. Three of the nine differ, and
# getting one wrong is a silent no-op upload: deliver ignores a folder it does not recognise.
# Verified against FastlaneCore::Languages::ALL_LANGUAGES (fastlane 2.237).
LOCALES = {
    "es-ES": "es-ES",
    "en-US": "en-US",
    "it-IT": "it",
    "pt-PT": "pt-PT",
    "fr-FR": "fr-FR",
    "de-DE": "de-DE",
    "nl-NL": "nl-NL",
    "pl-PL": "pl",
    "ro-RO": "ro",
}

# paparcar.com serves Spanish at the root and English under /en; there is no page for the other
# seven languages, so they point at the English one — an existing page beats an invented path.
ORIGIN = "https://paparcar.com"
PRIVACY_URL = f"{ORIGIN}/privacy-policy"          # single page, no locale prefix

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
    """`release-notes-<version>.txt` carries every locale in `<xx-XX>` tags."""
    text = version_file.read_text(encoding="utf-8")
    return {m.group(1): m.group(2).strip()
            for m in re.finditer(r"<([a-z]{2}-[A-Z]{2})>\n(.*?)\n</\1>", text, flags=re.S)}


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
    for play_locale, app_locale in LOCALES.items():
        src = PASTE / f"play-{play_locale}.txt"
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
        if play_locale in notes:
            fields["release_notes"] = notes[play_locale]
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

    print(f"{'verificados' if args.check else 'escritos'} {written or len(LOCALES)*7} ficheros "
          f"en {len(LOCALES)} idiomas")
    if problems:
        print("\npendiente o incorrecto:")
        for p in problems:
            print(f"  · {p}")
    return 1 if (args.check and problems) else 0


if __name__ == "__main__":
    sys.exit(main())
