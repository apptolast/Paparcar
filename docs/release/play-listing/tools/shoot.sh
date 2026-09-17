#!/bin/bash
# [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001]
# Captures the 8 Play carousel raws for ONE locale on the Pixel 8 emulator (1080x2400 @ 420dpi).
# usage: shoot.sh <locale> <outdir> [stepN ...]   — with no steps given, shoots 1..8.
#
# Taps that depend on WHICH element is on screen no longer guess coordinates from pixel color or
# from a fixed y calibrated on one lucky run. Measured on device 16-09: the Home sheet's own drag
# settles at a genuinely different height run to run (a `sheet_mid` swipe landed sheet-top at 0,
# 642, 888 and fully-collapsed-2063 across otherwise-identical repeats), so any FIXED tap y is a
# coin flip. `uiautomator dump` gives the real on-screen bounds of the element instead — this
# taps by TEXT, never by a remembered coordinate:
#   - the lane tabs are found by their COUNT BADGE ("6"/"4"/"5" in this fixture) — plain Arabic
#     numerals, stable across all 9 locales, unlike "Fuel stations"/"Tankstellen"/…
#   - the car-park row is found by "Parking Plaza Mayor" — a proper noun, identical in every
#     locale by construction, not a translated string
set -u
L="$1"; OUT="$2"; shift 2
STEPS=("$@")
[ ${#STEPS[@]} -eq 0 ] && STEPS=(1 2 3 4 5 6 7 8)
P=com.rndeveloper.paparcar
D="emulator-5554"
DUMP="$OUT/.dump.xml"
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT"

sh()  { adb -s $D shell "$@" >/dev/null 2>&1; }
cap() { adb -s $D exec-out screencap -p > "$OUT/shot$1.png"; }
tap() { adb -s $D shell input tap "$1" "$2" >/dev/null 2>&1; }
swp() { adb -s $D shell input swipe "$1" "$2" "$3" "$4" "$5" >/dev/null 2>&1; }
launch() {
  sh input keyevent KEYCODE_WAKEUP
  sh am force-stop $P
  sleep 1
  sh am start -n $P/com.rndeveloper.paparcar.DevMainActivity --es shot "$1"
  sleep 17
}
# Dumps the current view hierarchy to $DUMP.
dump() {
  adb -s $D shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1
  adb -s $D pull /sdcard/window_dump.xml "$DUMP" >/dev/null 2>&1
}
# Finds the exact-text node's bounds and taps its center. Retries the dump itself (not just the
# tap) because a dump taken mid-recomposition can miss the node entirely.
tap_text() {
  # `uiautomator dump` reads Compose's semantics tree through the accessibility bridge, and that
  # bridge can hand back a nearly-EMPTY tree (measured on device 16-09: 4 nodes total, immediately
  # after a scroll/expand, on a screen the screenshot at the very same moment showed fully
  # populated) rather than an error — there is nothing to notice and retry on except "did my text
  # show up". So every retry re-dumps AND checks the tree isn't suspiciously sparse before trusting
  # a miss to mean "not on screen" rather than "the bridge hadn't caught up yet".
  local text="$1" bounds="" x1 y1 x2 y2 nodecount
  for i in $(seq 1 12); do
    dump
    nodecount=$(grep -o 'text=' "$DUMP" | wc -l)
    bounds=$(grep -oE "text=\"${text}\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" "$DUMP" \
      | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    if [ -n "$bounds" ] && [ "$nodecount" -gt 15 ]; then break; fi
    bounds=""
    sleep 1.5
  done
  if [ -z "$bounds" ]; then
    echo "WARN: text '$text' not found after 12 tries" >&2
    return 1
  fi
  x1=$(echo "$bounds" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/')
  y1=$(echo "$bounds" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/')
  x2=$(echo "$bounds" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/')
  y2=$(echo "$bounds" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/')
  tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
  return 0
}

adb -s $D shell cmd locale set-app-locales $P --locales "$L" >/dev/null 2>&1
sleep 1

for n in "${STEPS[@]}"; do
case "$n" in
1)
  # 1 · Home, own car parked.
  launch parked
  cap 1
  ;;
2)
  # 2 · Same, sheet open: your cars with the street and the minute already saved. No tap depends
  # on the resulting detent — either shows the same content.
  launch parked
  swp 540 2010 540 500 400; sleep 4
  cap 2
  ;;
3)
  # 3 · Free spots lane (default) — what the community is offering right now. Same: no tap.
  launch map
  swp 540 2010 540 500 400; sleep 4
  cap 3
  ;;
4)
  # 4 · Fuel lane — a price per forecourt. "5" is the fuel-stations badge in this fixture.
  # A full relaunch-and-retry, not just re-dumping: measured on device 16-09, sustained runs of
  # many back-to-back app relaunches occasionally leave the accessibility bridge degraded in a way
  # that outlasts tap_text's own internal retries — starting the whole step over recovers it.
  ok=0
  for attempt in 1 2; do
    launch map
    swp 540 2010 540 500 400; sleep 4
    if tap_text "5"; then ok=1; break; fi
  done
  [ "$ok" = "1" ] || echo "ERROR: shot4 gave up after 2 full attempts" >&2
  sleep 5
  cap 4
  ;;
5)
  # 5 · Car parks — the peek of one: tariff, spaces and max height. "4" is the car-parks badge.
  ok=0
  for attempt in 1 2; do
    launch map
    swp 540 2010 540 500 400; sleep 4
    if tap_text "4"; then
      sleep 4
      if tap_text "Parking Plaza Mayor"; then ok=1; break; fi
    fi
  done
  [ "$ok" = "1" ] || echo "ERROR: shot5 gave up after 2 full attempts" >&2
  sleep 5
  cap 5
  ;;
6)
  # 6 · Your numbers: spend, fuel, CO2, usual place, spots shared. A two-swipe scroll here
  # overshot into the "full breakdown" chart's collapsed header, which slides UNDER the status
  # bar and overlaps it (measured on device 16-09) — two short flings compound however far EACH
  # one actually travels, and that travel is exactly as variable as every other fling in this
  # script. One moderate, slower drag lands short of that overlap zone consistently.
  launch numbers
  tap 539 2233; sleep 6
  swp 540 1900 540 1100 600; sleep 3
  cap 6
  ;;
7)
  # 7 · One car, several drivers — the group sheet.
  launch sharedcar
  tap 539 2233; sleep 6
  tap 540 914; sleep 5
  cap 7
  ;;
8)
  # 8 · Settings: the alerts you choose and where your trips are stored.
  launch settings
  tap 906 2233; sleep 6
  swp 540 1800 540 500 500; sleep 4
  cap 8
  ;;
esac
done

rm -f "$DUMP"
echo "done $L -> $OUT (${STEPS[*]})"
