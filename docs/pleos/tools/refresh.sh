#!/usr/bin/env bash
# Re-download Pleos docs into docs/pleos/raw. Requires: python3 venv with beautifulsoup4 markdownify html5lib.
# Usage: docs/pleos/tools/refresh.sh /path/to/venv/bin/python
set -euo pipefail
PY=${1:-python3}
HERE=$(cd "$(dirname "$0")" && pwd); P=$(dirname "$HERE")
W=$(mktemp -d)
curl -sL https://document.pleos.ai/sitemap.xml | grep -o '<loc>[^<]*</loc>' | sed -E 's#<[^>]*>##g; s#https://[^/]*##' | sort -u \
 | grep -E '^/docs/(pleos-only/(vehicle-app-planning-guide|distribute|vehicle-app-developer-guide|app-registration-guide|app-test-guide|app-integration-guide)|connect/guide)' > "$W/targets.txt"
while read -r u; do
  f="$W/html${u%/}"; [ "${u: -1}" = "/" ] && f="$W/html${u%/}/__index"
  mkdir -p "$(dirname "$f")"; curl -sL "https://document.pleos.ai$u" -o "$f.html"
done < "$W/targets.txt"
"$PY" -I "$HERE/html2md.py" "$W/html" "$W/md"
rm -rf "$P/raw" && mkdir -p "$P/raw" && cp -R "$W/md/docs/"* "$P/raw/"
find "$P/raw" -name '__index.md' -execdir mv {} index.md \;
echo "Refreshed $(wc -l < "$W/targets.txt") pages. Review git diff of raw/ and update wiki/ accordingly."
