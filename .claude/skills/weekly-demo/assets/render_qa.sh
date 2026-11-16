#!/usr/bin/env bash
# Render a deck to PDF + per-slide JPEGs for visual QA, with the brand font (Outfit) available.
# LibreOffice headless on macOS here cannot see system fonts, so it loads Outfit from a private
# profile's user/fonts dir. Usage: render_qa.sh DECK.pptx [OUT_DIR]
set -euo pipefail
deck="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
out="${2:-$(dirname "$deck")/qa}"
assets="${WEEKLY_DEMO_ASSETS:-$HOME/Desktop/libros-leidos-demos/assets}"
soffice="${SOFFICE:-/Applications/LibreOffice.app/Contents/MacOS/soffice}"
profile="${TMPDIR:-/tmp}/weekly-demo-lo-profile"
mkdir -p "$out" "$profile/user/fonts"
cp "$assets"/qa-fonts/*.ttf "$profile/user/fonts/" 2>/dev/null || echo "warn: no QA fonts in $assets/qa-fonts; Outfit will be substituted" >&2
"$soffice" "-env:UserInstallation=file://$profile" --headless --convert-to pdf --outdir "$out" "$deck" >/dev/null
pdf="$out/$(basename "${deck%.pptx}").pdf"
rm -f "$out"/slide-*.jpg
pdftoppm -jpeg -r 110 "$pdf" "$out/slide"
pdffonts "$pdf" | awk 'NR>2{print $1}' | sed 's/^[A-Z]*+//' | sort -u | tr '\n' ' '; echo
ls -1 "$out"/slide-*.jpg
