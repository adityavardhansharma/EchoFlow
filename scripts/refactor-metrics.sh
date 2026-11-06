#!/usr/bin/env bash
# Prints the numbers the refactor plan tracks, so each phase can be compared with the last:
# the package import graph between top-level packages, and the special-case counts.
# Usage: scripts/refactor-metrics.sh   (from the repo root)
set -euo pipefail
src=app/src/main/java/com/echoflow

echo "== Imports between top-level packages (from -> to: count) =="
for dir in "$src"/*/; do
  from=$(basename "$dir")
  grep -rhoE '^import com\.echoflow\.[a-z]+' "$dir" --include='*.kt' 2>/dev/null \
    | sed 's/^import com\.echoflow\.//' | { grep -vx "$from" || true; } | sort | uniq -c \
    | awk -v f="$from" '{ printf "  %s -> %s: %s\n", f, $2, $1 }'
done

echo
echo "== Special cases =="
count() { { grep -rE "$1" "$src" --include="*.kt" || true; } | wc -l | tr -d " "; }
echo "  runCatching / catch (_:     $(count 'runCatching|catch \(_:')"
echo "  delay(                      $(count '\bdelay\(')"
echo "  OkHttpClient.Builder()      $(count 'OkHttpClient\.Builder\(\)')"
echo "  AppDatabase.getDatabase(    $(count 'AppDatabase\.getDatabase\(')"
echo
echo "== Largest files (lines) =="
find "$src" -name '*.kt' -print0 | xargs -0 wc -l | sort -rn | sed -n '2,11p'
