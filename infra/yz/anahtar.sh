#!/bin/sh
# Issue a virtual key for one app: sh anahtar.sh <app> <model,model> [rpm]
# Prints the key once; store it in that app's secret env, never in git.
set -eu
APP=$1; MODELS=$2; RPM=${3:-30}
. "$(dirname "$0")/.env"
MODELS_JSON=$(printf '%s' "$MODELS" | sed 's/[^,]*/"&"/g')
curl -sf http://127.0.0.1:4000/key/generate \
  -H "Authorization: Bearer $LITELLM_MASTER_KEY" -H 'Content-Type: application/json' \
  -d "{\"key_alias\":\"$APP\",\"models\":[$MODELS_JSON],\"rpm_limit\":$RPM}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["key"])'
