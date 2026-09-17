#!/bin/bash
# [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001]
# Renders the 8 Play carousel frames for all 9 locales (72 PNGs, 1080x1920).
#
# `screenshot-frame.html` hard-codes its shot paths as `screenshots/raw/shotN.png`, so each locale's
# raws are copied INTO that one directory before its 8 frames are rendered. The 01-09 set could
# only ever be EN inside the phone for exactly this reason; the loop below is what makes the app's
# own UI localised per locale too.
set -eu
SP="C:/Users/rndev/AppData/Local/Temp/claude/C--Users-rndev-Documents-AndroidProjects-Paparcar/80e6592f-87a3-4ab6-a259-094e6939e405/scratchpad"
ASSETS="C:/Users/rndev/Documents/AndroidProjects/Paparcar-play-shots/docs/release/play-listing/assets"
HTML="file:///C:/Users/rndev/Documents/AndroidProjects/Paparcar-play-shots/docs/release/play-listing/assets/screenshot-frame.html"
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"

for L in en es it pt fr de nl pl ro; do
  # Swap the shared raw/ dir to THIS locale's captures.
  cp "$SP/raw/$L/"shot*.png "$ASSETS/screenshots/raw/"
  mkdir -p "$ASSETS/screenshots/$L"
  # El nombre lleva el locale DELANTE, no sólo la carpeta: las 72 acaban juntas en la bandeja de
  # descargas o en el selector del Console, y nueve ficheros llamados `play-shot-1.png` se pisan
  # entre ellos y no hay forma de saber cuál es cuál sin abrirlos.
  for n in 1 2 3 4 5 6 7 8; do
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
      --force-device-scale-factor=1 --window-size=1080,1920 \
      --screenshot="$ASSETS/screenshots/$L/$L-play-shot-$n.png" \
      "$HTML?n=$n&lang=$L" >/dev/null 2>&1
  done
  echo "rendered $L"
done

# Leave EN's raws in place: they are the versioned SOURCE set (the other eight locales' raws are
# derivable by re-running the shoot script, and `.gitignore` keeps only `raw/` out of the ignore).
cp "$SP/raw/en/"shot*.png "$ASSETS/screenshots/raw/"
echo "done"
