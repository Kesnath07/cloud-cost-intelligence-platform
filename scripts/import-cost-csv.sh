#!/usr/bin/env bash
# Uploads a cost CSV file (format: sample-data/README.md) to a running instance.
# Usage: APP_ADMIN_PASSWORD=... scripts/import-cost-csv.sh <file.csv> [base-url]
set -euo pipefail

FILE="${1:?Usage: scripts/import-cost-csv.sh <file.csv> [base-url]}"
BASE_URL="${2:-http://localhost:8080}"
USERNAME="${APP_ADMIN_USERNAME:-admin}"
: "${APP_ADMIN_PASSWORD:?Set APP_ADMIN_PASSWORD (see .env)}"

if [[ ! -f "${FILE}" ]]; then
  echo "File not found: ${FILE}" >&2
  exit 1
fi

status=$(curl --silent --show-error --output /dev/stderr --write-out '%{http_code}' \
  --user "${USERNAME}:${APP_ADMIN_PASSWORD}" --form "file=@${FILE};type=text/csv" "${BASE_URL}/api/imports/costs")
echo
case "${status}" in
  201) echo "Imported ${FILE}." ;;
  400) echo "The file was rejected; see the validation errors above." >&2; exit 1 ;;
  409) echo "This file content was already imported." >&2; exit 1 ;;
  *) echo "Request failed with HTTP ${status}." >&2; exit 1 ;;
esac
