#!/usr/bin/env python3
"""[PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001] Reviews a shot session before it is uploaded.

    python scan.py raws   <rawroot>    [locale ...]
    python scan.py frames <assetsroot> [locale ...]

Two passes, because the two halves fail differently:

* **raws** — the captures straight off the device. Every defect that ever shipped lived here and
  none of them announces itself: a capture with the map unloaded weighs the same as a good one, the
  system's "isn't responding" dialog looks like a dialog the app drew, an emulator that died mid-run
  writes 0 bytes, and a lane tab that missed leaves a perfectly plausible screenshot OF THE WRONG
  LANE (that one shipped three times over in a first pass here). The lane check is comparative: if
  `fuel` and `spots` are the same picture, the tap did not take.
* **frames** — the 72 composed PNGs. Cheap structural checks only: they exist, they are 1080x1920,
  and they actually carry the brand green and rendered text (a font that failed to load renders
  nothing and the file still weighs a normal amount).

Exit code is the number of problems, so it can gate an upload.
"""
import sys
import os
from PIL import Image

LOCALES = ["en", "es", "it", "pt", "fr", "de", "nl", "pl", "ro"]
RAWS = ["home", "sheet", "spotmap", "spots", "fuel", "carparks", "carpeek",
        "numbers", "garage", "members", "settings"]
# The raws whose whole point is a loaded map: these are the ones where a black tile area is a bug
# rather than a dark sheet. Measured tile colour in the dark style: (28,42,64) / (49,67,94).
MAP_RAWS = ["home", "spotmap", "carparks", "carpeek", "fuel"]
STEP = 24


def pixels(img, box):
    x1, y1, x2, y2 = box
    return [img.getpixel((x, y)) for x in range(x1, x2, STEP) for y in range(y1, y2, STEP)]


def pct(sample, pred):
    return 100.0 * sum(1 for p in sample if pred(p)) / max(1, len(sample))


def near_black(p):
    return p[0] <= 14 and p[1] <= 14 and p[2] <= 16


def neutral_grey(p):
    # The system dialog is NEUTRAL (blue channel ~ red); every surface of ours is blue-ish or green.
    return abs(p[2] - p[0]) <= 10 and 15 <= p[0] <= 60 and 15 <= p[1] <= 60


def brand_green(p):
    return p[1] > 120 and p[1] - p[0] > 45 and p[1] - p[2] > 35


def light_text(p):
    return p[0] > 170 and p[1] > 170 and p[2] > 165


def signature(img, box):
    """A coarse fingerprint of a region — for asking "is this the same screen?"."""
    x1, y1, x2, y2 = box
    return tuple(img.crop(box).resize((16, 32)).convert("L").tobytes())


def sig_distance(a, b):
    return sum(abs(x - y) for x, y in zip(a, b)) / len(a)


def scan_raws(root, locales):
    problems = []
    for L in locales:
        d = os.path.join(root, L)
        sigs = {}
        for name in RAWS:
            p = os.path.join(d, f"{name}.png")
            if not os.path.exists(p):
                problems.append(f"{L}/{name}: FALTA")
                continue
            if os.path.getsize(p) == 0:
                problems.append(f"{L}/{name}: 0 BYTES (el emulador se cayo a media tanda)")
                continue
            img = Image.open(p).convert("RGB")
            if img.size != (1080, 2400):
                problems.append(f"{L}/{name}: {img.size}, se esperaba 1080x2400")
                continue
            top = pixels(img, (60, 260, 1020, 1200))
            if pct(top, neutral_grey) >= 35:
                problems.append(f"{L}/{name}: dialogo del sistema encima")
            if name in MAP_RAWS and pct(top, near_black) >= 45:
                problems.append(f"{L}/{name}: el mapa no cargo (casi negro) — ¿build sin firmar con la key de release?")
            # The lane bottom half is what tells free spots from fuel from car parks.
            sigs[name] = signature(img, (0, 1200, 1080, 2400))
        for name in ("fuel", "carparks", "carpeek"):
            if name in sigs and "spots" in sigs and sig_distance(sigs[name], sigs["spots"]) < 6:
                problems.append(f"{L}/{name}: es la MISMA pantalla que spots — el tap de carril no entro")
        if "carpeek" in sigs and "carparks" in sigs and sig_distance(sigs["carpeek"], sigs["carparks"]) < 6:
            problems.append(f"{L}/carpeek: identico a carparks — la ficha del parking no se abrio")
    return problems


def scan_frames(root, locales):
    problems = []
    for L in locales:
        for n in range(1, 9):
            p = os.path.join(root, "screenshots", L, f"{L}-play-shot-{n}.png")
            if not os.path.exists(p):
                problems.append(f"{L}/{n}: FALTA")
                continue
            if os.path.getsize(p) == 0:
                problems.append(f"{L}/{n}: 0 BYTES")
                continue
            img = Image.open(p).convert("RGB")
            if img.size != (1080, 1920):
                problems.append(f"{L}/{n}: {img.size}, se esperaba 1080x1920")
                continue
            head = pixels(img, (80, 90, 1000, 380))
            # 0.4, not 0.8: a headline that did not render at all measures ~0, while a SHORT one in
            # a language that packs it into two lines measures 0.79 (pt/7). A threshold that close
            # to real copy only produces false alarms, and a false alarm on a 72-file review is how
            # a real one gets waved through.
            if pct(head, light_text) < 0.4:
                problems.append(f"{L}/{n}: el titular no se pinto (¿la fuente no cargo?)")
            if pct(pixels(img, (0, 0, 1080, 1920)), brand_green) < 0.2:
                problems.append(f"{L}/{n}: sin verde de marca — algo no se renderizo")
    return problems


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    mode, root = sys.argv[1], sys.argv[2]
    locales = sys.argv[3:] or LOCALES
    problems = scan_raws(root, locales) if mode == "raws" else scan_frames(root, locales)
    for p in problems:
        print("PROBLEMA:", p)
    print(f"scan {mode}: {len(problems)} problemas en {len(locales)} idiomas")
    return len(problems)


if __name__ == "__main__":
    sys.exit(main())
