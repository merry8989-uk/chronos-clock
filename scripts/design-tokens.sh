#!/usr/bin/env bash
# Design-system regression gate.
#
# The visual direction asks for tokens instead of magic numbers, and for no
# hard-coded colours outside the theme. A rule nobody checks decays, so this
# script counts violations per file and compares them with a committed baseline:
#
#   * a file that gains a violation fails the build
#   * a file that loses violations must have its baseline lowered, so the number
#     can only go down
#
# Regenerate the baseline with: scripts/design-tokens.sh --write
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
baseline="$root/docs/design-baseline.txt"
ui="$root/app/src/main/java/com/osintgram/chronos/ui"

# Colours belong in ui/theme. Font sizes, radii and spacing belong in tokens.
# Screens are measured; the theme and the shared components are exempt because
# that is where the tokens are defined.
count_file() {
  local file="$1"
  local colour sizes radii
  colour=$(grep -cE 'Color\(0x[0-9A-Fa-f]{8}\)' "$file" || true)
  sizes=$(grep -cE 'fontSize *= *[0-9]+(\.[0-9]+)?\.sp' "$file" || true)
  radii=$(grep -cE 'RoundedCornerShape\([0-9]+(\.[0-9]+)?\.dp\)' "$file" || true)
  echo $((colour + sizes + radii))
}

report() {
  find "$ui" -name '*.kt' \
    | grep -vE '/(theme|components)/' \
    | sort \
    | while read -r file; do
        local n
        n=$(count_file "$file")
        if [ "$n" -gt 0 ]; then
          printf '%s %s\n' "${file#"$root"/}" "$n"
        fi
      done
}

if [ "${1:-}" = "--write" ]; then
  report > "$baseline"
  echo "Wrote $(wc -l < "$baseline") file(s) to ${baseline#"$root"/}"
  exit 0
fi

if [ ! -f "$baseline" ]; then
  echo "No baseline at ${baseline#"$root"/}. Run: scripts/design-tokens.sh --write"
  exit 1
fi

current="$(mktemp)"
report > "$current"
failed=0
while read -r file count; do
  [ -z "$file" ] && continue
  was=$(awk -v f="$file" '$1 == f { print $2 }' "$baseline")
  was=${was:-0}
  if [ "$count" -gt "$was" ]; then
    echo "FAIL $file: $count hard-coded value(s), baseline $was"
    failed=1
  elif [ "$count" -lt "$was" ]; then
    echo "NOTE $file: $count hard-coded value(s), baseline $was — lower the baseline"
  fi
done < "$current"
rm -f "$current"

if [ "$failed" -eq 1 ]; then
  echo
  echo "Use Spacing, Radii, ChronosType and the theme colours instead of literals."
  exit 1
fi
echo "Design tokens: no file gained a hard-coded value."
