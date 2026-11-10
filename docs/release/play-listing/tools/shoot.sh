#!/bin/bash
# [PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001]
# Captures the ELEVEN raws one locale needs for the 8 Play frames, on the Pixel 8 emulator
# (1080x2400 @ 420dpi). usage: shoot.sh <locale> <outdir> [name ...] — with no names, shoots all.
#
# Eleven raws for eight frames because the carousel is COMPOSED: three frames mount two captures
# each (map + list, list + peek, garage + group), and the rest mount one. Which raw goes where is
# the compositor's business — `assets/screenshot-frame.html`.
#
# ⛔ The build for a shot session is `assembleMockDebug -PmockAsProd`, which now ALSO signs debug
# with the release key. Both halves are needed and they used to pull apart: mock+release paints the
# map (it passes the Maps key's package+SHA-1 restriction) but ignores `--es shot` (R8 renames the
# ShotPreset constants); mock+debug honours every preset and paints the map BLACK. Measured 25-09.
#
# Taps that depend on WHICH element is on screen do not guess coordinates: the Home sheet settles at
# a genuinely different height run to run (measured 16-09: sheet-top at 0, 642, 888 and fully
# collapsed across identical repeats), so a fixed tap y is a coin flip that FAILS SILENTLY — it
# opens another tab and the capture still looks plausible. `uiautomator dump` gives the real bounds:
#   - Home's "see all" actions are resolved from the app's localized plural resources
#   - the car-park row by "Parking Plaza Mayor", a fixture proper noun that no locale translates
# ⛔ Esos literales son un CONTRATO con el mundo mock, y se rompio en silencio: la mudanza a Madrid
# dejo dos de los tres apuntando a sitios que ya no existen. Lo vigila ahora
# `CaptureAnchorGuardrailTest` [PLAY-SHOTS-THE-CAPTURE-ANCHORS-DIED-IN-THE-MOVE-001].
set -u
L="$1"; OUT="$2"; shift 2
NAMES=("$@")
[ ${#NAMES[@]} -eq 0 ] && NAMES=(home sheet spotmap spots fuel carparks carpeek numbers garage members settings)
P=com.rndeveloper.paparcar
D="${SHOT_DEVICE:-emulator-5554}"
DUMP="$OUT/.dump.xml"
DUMP_WIN=$(cygpath -w "$DUMP" 2>/dev/null || echo "$DUMP")
TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# ...and the SCRIPT path too: `python /c/Users/.../uiquery.py` is not a path Windows python opens.
TOOLS_WIN=$(cygpath -w "$TOOLS" 2>/dev/null || echo "$TOOLS")
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT"

# Bottom-nav tab centres (1080x2400): Home · Vehicles · Settings.
NAV_VEHICLES_X=539; NAV_SETTINGS_X=906; NAV_Y=2233

sh()  { adb -s "$D" shell "$@" >/dev/null 2>&1; }
cap() { adb -s "$D" exec-out screencap -p > "$OUT/$1.png"; }
tap() { adb -s "$D" shell input tap "$1" "$2" >/dev/null 2>&1; }
swp() { adb -s "$D" shell input swipe "$1" "$2" "$3" "$4" "$5" >/dev/null 2>&1; }
launch() {
  sh input keyevent KEYCODE_WAKEUP
  sh am force-stop $P
  sleep 1
  sh am start -n $P/com.rndeveloper.paparcar.DevMainActivity --es shot "$1"
  sleep 17
}
# Expands the Home sheet to its top detent. Two swipes: the first from the collapsed card, the
# second a no-op if it is already up (the sheet clamps), which is cheaper than asking where it is.
expand_sheet() { swp 540 2010 540 500 400; sleep 4; }

# ⛔ `adb` AND `python` are Windows binaries: both the destination of the pull and the path handed
# to the query have to be Windows paths, even when the script speaks POSIX. Hence the
# script speaks POSIX. With `MSYS_NO_PATHCONV=1` set (needed so `/sdcard/...` reaches the device
# unmangled) a POSIX destination is passed through verbatim and the pull FAILS SILENTLY — the dump
# never lands, every lookup misses, and the session taps blind. That is the real reason a first pass
# came back with three captures of the wrong lane.
dump() {
  adb -s "$D" shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1
  adb -s "$D" pull /sdcard/window_dump.xml "$DUMP_WIN" >/dev/null 2>&1
}
# Taps whatever is on screen with this exact text. The coordinate comes from `uiquery.py`, which
# reads the dump and walks UP to the smallest CLICKABLE node containing the label — Compose
# publishes the label and the pressable row as different nodes, and the lane tabs expose their count
# as a non-clickable TextView inside a clickable row. Tapping the label's own centre works most of
# the time and silently does not when the sheet is mid-settle; tapping the row always works.
#
# Retries the DUMP, not just the tap: Compose's semantics reach uiautomator through the
# accessibility bridge, and that bridge can hand back a nearly-empty tree (measured: 4 nodes, on a
# screen the screenshot showed fully populated) instead of an error.
tap_text() {
  local text="$1"; shift
  local point="" nodecount
  for i in $(seq 1 12); do
    dump
    nodecount=$(grep -o 'text=' "$DUMP" | wc -l)
    if [ "$nodecount" -gt 15 ]; then
      point=$(python "$TOOLS_WIN\uiquery.py" "$DUMP_WIN" "$text" --clickable "$@" 2>/dev/null) || point=""
    fi
    [ -n "$point" ] && break
    sleep 1.5
  done
  if [ -z "$point" ]; then echo "WARN: text '$text' not found after 12 tries" >&2; return 1; fi
  tap $point
  return 0
}

# Open the appropriate Home card action and verify the resulting subject heading. The card can
# move below the viewport when another locale wraps, so scroll to find it instead of tapping a fixed
# coordinate. A missing action or heading fails the capture instead of saving the previous lane.
select_lane() {
  local kind="$1" point
  case "$kind" in spots|fuel|carparks) ;; *) return 1 ;; esac
  # Home now exposes localized "see all" actions inside vertically scrolling cards.
  # Resolve each action from the app resources in uiquery, then verify the opened list.
  for try in 1 2 3 4; do
    dump
    point=$(python "$TOOLS_WIN\uiquery.py" "$DUMP_WIN" "@$kind:$L" --clickable 2>/dev/null) || point=''
    if [ -n "$point" ]; then
      tap $point
      sleep 5
      dump
      python "$TOOLS_WIN\uiquery.py" "$DUMP_WIN" "@heading-$kind:$L" >/dev/null 2>&1 && return 0
      return 1
    fi
    swp 540 1990 540 1200 500
    sleep 3
  done
  return 1
}

# Writes the rect a magnified window will be cut from, next to the capture, in RAW device pixels.
# A crop measured once in English is wrong in the next language the moment the sheet settles at a
# different height - the PL capture put the map exactly where the English rect expected the parked
# card, and the window magnified a patch of road. So the rect is asked to the DEVICE, per locale,
# anchored on a fixture name or a translated resource. A missing anchor fails the session, and
# render.sh requires it rather than silently falling back to an unrelated rectangle.
anchor() {
  local name="$1" rect; shift
  dump
  rect=$(python "$TOOLS_WIN\uiquery.py" "$DUMP_WIN" "$@" 2>/dev/null) || return 1
  [ -n "$rect" ] || return 1
  echo "$rect" > "$OUT/$name.anchor.txt"
}

APP_LOCALE="$L"
[ "$L" = en ] && APP_LOCALE=en-IE
adb -s "$D" shell cmd locale set-app-locales $P --locales "$APP_LOCALE" >/dev/null 2>&1
sleep 1

for n in "${NAMES[@]}"; do
case "$n" in
home)
  # Frame 1 (hero) · Home with your own car parked: the street and the minute, on the map.
  launch parked
  cap home
  ;;
sheet)
  # Frame 2 (detail) · The same, sheet up, with the parked vehicle's header in view.
  # The magnified window in frame 2 crops the parked row out of THIS capture.
  launch parked
  expand_sheet
  cap sheet
  # The window in frame 2 magnifies the parked car's header; this is the fixture address, identical
  # in all nine locales. It moved with the demo world [MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001] and
  # the anchor did not, which is how two of these three died unnoticed
  # [PLAY-SHOTS-THE-CAPTURE-ANCHORS-DIED-IN-THE-MOVE-001].
  rm -f "$OUT/sheet.anchor.txt"
  anchor sheet "Calle de Fuencarral, 21" --clickable --rect --min-y 1000 || { echo "ERROR: sheet anchor missing" >&2; exit 1; }
  ;;
spotmap)
  # Frame 3 (pair, back) · The map itself: what the community is offering right now, as markers.
  launch map
  cap spotmap
  ;;
spots)
  # Frame 3 (pair, front) · The same answer as a list, with how long ago each spot was left.
  launch map
  expand_sheet
  select_lane spots || { echo "ERROR: spots list did not open" >&2; exit 1; }
  cap spots
  ;;
fuel)
  # Frame 4 (detail) · A price per forecourt; the fixture contains five stations.
  # A whole relaunch on failure, not just another dump: sustained runs of back-to-back relaunches
  # can leave the accessibility bridge degraded in a way that outlasts tap_text's own retries.
  ok=0
  for attempt in 1 2; do
    launch map; expand_sheet
    if select_lane fuel; then ok=1; break; fi
  done
  [ "$ok" = "1" ] || { echo "ERROR: fuel gave up after 2 full attempts" >&2; exit 1; }
  sleep 5
  cap fuel
  # Frame 4 magnifies the price column; the anchor is a forecourt row with one below it.
  # ⛔ By PREFIX, and not for brevity: every Madrid forecourt name carries an accent
  # ("Estación", "Tetuán", "Área", "Chamartín") and `uiquery.py` compares by EXACT equality, so one
  # mis-encoded character between bash, Windows argv and the XML dump loses the anchor — and losing
  # it used to print only a WARN, so the frame could ship wrong. "Carburantes" is accent-free and unique.
  rm -f "$OUT/fuel.anchor.txt"
  anchor fuel "Carburantes" --starts-with --clickable --rect || { echo "ERROR: fuel anchor missing" >&2; exit 1; }
  ;;
carparks)
  # Frame 5 (pair, back) · The car-parks lane; the fixture contains four car parks.
  ok=0
  for attempt in 1 2; do
    launch map; expand_sheet
    if select_lane carparks; then ok=1; break; fi
  done
  [ "$ok" = "1" ] || { echo "ERROR: carparks gave up after 2 full attempts" >&2; exit 1; }
  sleep 5
  cap carparks
  ;;
carpeek)
  # Frame 5 (pair, front) · One car park opened: tariff, spaces and max height before you drive in.
  ok=0
  for attempt in 1 2; do
    launch map; expand_sheet
    if select_lane carparks && tap_text "Parking Plaza Mayor"; then ok=1; break; fi
  done
  [ "$ok" = "1" ] || { echo "ERROR: carpeek gave up after 2 full attempts" >&2; exit 1; }
  sleep 5
  cap carpeek
  ;;
numbers)
  # Frame 6 (detail) · Your numbers: spend, fuel, CO2, time spent looking, usual place. One slow
  # drag, not two flings: two compound however far EACH actually travels and overshoot into the
  # breakdown header, which slides under the status bar.
  launch numbers
  tap $NAV_VEHICLES_X $NAV_Y; sleep 6
  swp 540 1900 540 1100 600; sleep 3
  cap numbers
  # The displayed duration changes as the fixture's rolling week advances. Anchor on the
  # translated tile label rather than a value observed on one particular day.
  rm -f "$OUT/numbers.anchor.txt"
  anchor numbers "@stats-search:$L" --rect || { echo "ERROR: numbers anchor missing" >&2; exit 1; }
  ;;
garage)
  # Frame 7 (pair, back) · The garage: your cars, each with its body, its fuel and its last parking.
  launch numbers
  tap $NAV_VEHICLES_X $NAV_Y; sleep 6
  cap garage
  ;;
members)
  # Frame 7 (pair, front) · One car, several drivers — who is in the group. The row sits right under
  # the vehicle card, whose height is fixture-fixed, so this y is stable across locales.
  launch sharedcar
  tap $NAV_VEHICLES_X $NAV_Y; sleep 6
  tap 540 916; sleep 5
  cap members
  ;;
settings)
  # Frame 8 (hero) · Settings: the alerts you choose and where your trips are kept.
  launch settings
  tap $NAV_SETTINGS_X $NAV_Y; sleep 6
  swp 540 1800 540 500 500; sleep 4
  cap settings
  ;;
esac
done

rm -f "$DUMP"
echo "done $L -> $OUT (${NAMES[*]})"
