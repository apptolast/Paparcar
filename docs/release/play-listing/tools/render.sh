#!/bin/bash
# [PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001]
# Renders the 8 Play frames for every locale (72 PNGs, 1080x1920) out of the raws shot by shoot.sh.
#
# usage: render.sh <rawroot> [locale ...]     — rawroot holds <locale>/<name>.png
#
# `screenshot-frame.html` resolves its captures as `screenshots/raw/<name>.png`, one fixed folder,
# so each locale's raws are copied INTO it before its 8 frames are rendered. That single path is
# also why the 01-09 set could only ever show the phone in English.
set -eu
RAWROOT="$1"; shift
LOCALES=("$@")
[ ${#LOCALES[@]} -eq 0 ] && LOCALES=(en es it pt fr de nl pl ro)

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$HERE/assets"
# Chrome needs a Windows path for both the page and the output.
WINASSETS=$(cygpath -w "$ASSETS" 2>/dev/null | sed 's|\\|/|g') || WINASSETS="$ASSETS"
HTML="file:///$WINASSETS/screenshot-frame.html"
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"

for L in "${LOCALES[@]}"; do
  cp "$RAWROOT/$L/"*.png "$ASSETS/screenshots/raw/"
  mkdir -p "$ASSETS/screenshots/$L"
  # The locale goes in the FILE NAME, not just the folder: the 72 end up together in a download
  # tray and in the Console's picker, and nine files called `play-shot-1.png` overwrite each other
  # with no way to tell which is which without opening them.
  for n in 1 2 3 4 5 6 7 8; do
    # The three frames with a magnified window take the rect the DEVICE reported for THIS locale
    # (see `anchor` in shoot.sh). A rect measured once in English is wrong in the next language the
    # moment the sheet settles at a different height — measured here: the card sat at y=1267 in
    # eight locales and at y=375 in `es`. Passed on the URL rather than read from disk by the page:
    # a file:// fetch is one more thing that can fail silently inside a headless render.
    case "$n" in
      2) src="$RAWROOT/$L/sheet.anchor.txt" ;;
      4) src="$RAWROOT/$L/fuel.anchor.txt" ;;
      6) src="$RAWROOT/$L/numbers.anchor.txt" ;;
      *) src="" ;;
    esac
    anchor=""
    if [ -n "$src" ]; then
      if [ -f "$src" ]; then
        anchor="&anchor=$(tr ' ' ',' < "$src" | tr -d '\r\n')"
      else
        echo "WARN: $L/$n sin ancla — se usaría el recorte por defecto, medido en EN" >&2
      fi
    fi
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
      --force-device-scale-factor=1 --window-size=1080,1920 \
      --screenshot="$WINASSETS/screenshots/$L/$L-play-shot-$n.png" \
      "$HTML?n=$n&lang=$L$anchor" >/dev/null 2>&1
  done
  echo "rendered $L"
done

# Leave EN's raws in place: they are the versioned SOURCE set (`.gitignore` keeps only `raw/` out of
# the ignore), and the other eight locales' raws are re-shootable with one command.
cp "$RAWROOT/en/"*.png "$ASSETS/screenshots/raw/"
echo "done"
