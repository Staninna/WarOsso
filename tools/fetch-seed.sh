#!/usr/bin/env bash
# fetch-seed.sh - download the public BAG/OSM data for the default complex area and write it to
# app/src/main/assets/seed.json. The app imports that file into Room on first launch.
#
#   tools/fetch-seed.sh [--lat 51.597787] [--lon 4.800806] [--radius 150]
#
# The query is app/src/main/assets/overpass.ql, shared with the in-app "Refresh from OSM":
# house numbers, building outlines and entrance nodes.
# Data © OpenStreetMap contributors, ODbL.

set -euo pipefail

LAT="51.597787"
LON="4.800806"
RADIUS="150"
ENDPOINT="${OVERPASS_ENDPOINT:-https://overpass-api.de/api/interpreter}"

while [ $# -gt 0 ]; do
    case "$1" in
        --lat) LAT="${2:?--lat needs a value}"; shift 2 ;;
        --lon) LON="${2:?--lon needs a value}"; shift 2 ;;
        --radius) RADIUS="${2:?--radius needs a value}"; shift 2 ;;
        -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

cd "$(dirname "${BASH_SOURCE[0]}")/.."
OUT="app/src/main/assets/seed.json"
AREA="(around:${RADIUS},${LAT},${LON})"

# The same query template the app uses for "Refresh from OSM".
QUERY="$(sed "s|{{area}}|${AREA}|g" app/src/main/assets/overpass.ql)"

mkdir -p "$(dirname "$OUT")"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

curl --fail --silent --show-error --max-time 90 \
    --user-agent "WarOsso-seed-script" \
    --data-urlencode "data=${QUERY}" \
    "$ENDPOINT" > "$tmp"

# Overpass answers 200 with a "remark" when a query times out or runs out of memory.
if grep -q '"remark"' "$tmp"; then
    echo "Overpass reported a problem:" >&2
    grep '"remark"' "$tmp" >&2
    exit 1
fi
grep -q '"elements"' "$tmp" || { echo "Unexpected Overpass response, see $tmp" >&2; trap - EXIT; exit 1; }

mv "$tmp" "$OUT"
trap - EXIT
chmod 644 "$OUT"
echo "Wrote $OUT ($(grep -c '"type":' "$OUT") elements)"
