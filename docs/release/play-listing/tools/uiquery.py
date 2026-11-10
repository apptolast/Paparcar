#!/usr/bin/env python3
"""[PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001] Where to tap for a given on-screen text.

    python uiquery.py <window_dump.xml> <text> [--clickable] [--rect] [--starts-with]
                      [--min-x N] [--max-x N] [--min-y N]

Text can also be a localized selector such as @fuel:es or @stats-search:en; these are
resolved from the Compose resources in the worktree.

Prints "<x> <y>" (the point to tap) — or "<x1> <y1> <x2> <y2>" with `--rect` — and exits 0, or
exits 1 if the text is not on screen.

`--rect` is what the magnified windows of the carousel are cut from. A crop rect measured once in
English is wrong in another language the moment the sheet settles at a different height: the PL
capture put the map where the English rect expected the parked card, and the window magnified a
patch of road. The rect has to come from the device, per locale, anchored on a fixture PROPER NOUN
("Camden Street, 21", "Northgate Fuel") that no locale translates.

`--starts-with` matches a prefix instead of the whole string, for values whose unit is localised
("45 min" / "45 Min.").

`--clickable` is the one that matters and it is why this is not a grep: Compose publishes the label
and the thing you can press as DIFFERENT nodes. The lane tabs of the Home sheet expose their count
as a `TextView` with `clickable="false"`, sitting inside a clickable row — tapping the label's own
centre lands on the row most of the time and, when the sheet is mid-settle, does not. With
`--clickable` the point comes from the SMALLEST clickable node that contains the label, which is the
row itself: the coordinate is then the one the app is actually listening on.

`--min-x` / `--max-x` disambiguate a number that appears twice (the sheet header badge and the tab
badge both read "5" once the fuel lane is live).
"""
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def bounds_of(node):
    m = BOUNDS.match(node.get("bounds", ""))
    return tuple(int(g) for g in m.groups()) if m else None


def area(b):
    return (b[2] - b[0]) * (b[3] - b[1])


def contains(outer, x, y):
    return outer[0] <= x <= outer[2] and outer[1] <= y <= outer[3]


def main():
    args = [a for a in sys.argv[1:]]
    want_clickable = "--clickable" in args
    want_rect = "--rect" in args
    prefix = "--starts-with" in args
    args = [a for a in args if a not in ("--clickable", "--rect", "--starts-with")]
    min_x, max_x, min_y = 0, 10 ** 6, 0
    for flag, setter in (("--min-x", "min"), ("--max-x", "max"), ("--min-y", "min_y")):
        if flag in args:
            i = args.index(flag)
            value = int(args[i + 1])
            del args[i:i + 2]
            if setter == "min":
                min_x = value
            elif setter == "max":
                max_x = value
            else:
                min_y = value
    if len(args) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    path, text = args[0], args[1]
    # Derive localized action labels from the same resources the app displays. This avoids
    # putting accented strings through Windows shell argument encoding or duplicating them here.
    if text.startswith('@'):
        kind, locale = text[1:].split(':', 1)
        keys = {'spots': ('home_now_see_all_spots', 6),
                'fuel': ('home_now_see_all_fuel_stations', 5),
                'carparks': ('home_now_see_all_car_parks', 4)}
        values = 'values' if locale == 'en' else f'values-{locale}'
        resources = Path(__file__).resolve().parents[4] / 'shared/src/commonMain/composeResources' / values / 'strings.xml'
        root = ET.parse(resources).getroot()
        if kind == 'stats-search':
            item = root.find("./string[@name='stats_tile_search_time']")
            if item is None or not item.text:
                return 2
            text = item.text.upper()
        elif kind.startswith('heading-'):
            headings = {'spots': 'subject_free_spots_full', 'fuel': 'subject_fuel_stations',
                        'carparks': 'subject_public_parkings'}
            key = headings[kind.removeprefix('heading-')]
            item = root.find(f"./string[@name='{key}']")
            if item is None or not item.text:
                return 2
            text = item.text
        else:
            key, count = keys[kind]
            item = root.find(f"./plurals[@name='{key}']/item[@quantity='other']")
            if item is None or not item.text:
                return 2
            text = item.text.replace('%1$d', str(count))

    nodes = list(ET.parse(path).getroot().iter("node"))
    hits = []
    for n in nodes:
        value = n.get("text") or ""
        if not (value.startswith(text) if prefix else value == text):
            continue
        b = bounds_of(n)
        if b and min_x <= b[0] <= max_x and b[1] >= min_y:
            hits.append(b)
    if not hits:
        return 1
    b = hits[0]
    cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    if want_clickable:
        owners = [bb for bb in (bounds_of(n) for n in nodes if n.get("clickable") == "true")
                  if bb and contains(bb, cx, cy)]
        if owners:
            # The smallest container is the control; the biggest is usually the whole screen.
            b = min(owners, key=area)
            cx, cy = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    if want_rect:
        print(b[0], b[1], b[2], b[3])
    else:
        print(cx, cy)
    return 0


if __name__ == "__main__":
    sys.exit(main())
