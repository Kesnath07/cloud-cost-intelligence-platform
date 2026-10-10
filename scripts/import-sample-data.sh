#!/usr/bin/env bash
# Loads the fictional demonstration dataset into a running instance through the API.
# Usage: APP_ADMIN_PASSWORD=... scripts/import-sample-data.sh [base-url]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
USERNAME="${APP_ADMIN_USERNAME:-admin}"
: "${APP_ADMIN_PASSWORD:?Set APP_ADMIN_PASSWORD (see .env)}"

echo "Loading demonstration data into ${BASE_URL} (fictional figures, not real AWS spending)..."
status=$(curl --silent --show-error --output /dev/stderr --write-out '%{http_code}' \
  --user "${USERNAME}:${APP_ADMIN_PASSWORD}" --request POST "${BASE_URL}/api/imports/demo")
echo
case "${status}" in
  201) echo "Demonstration dataset loaded." ;;
  409) echo "Demonstration dataset is already loaded." ;;
  *) echo "Request failed with HTTP ${status}." >&2; exit 1 ;;
esac
