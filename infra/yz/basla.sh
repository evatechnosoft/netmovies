#!/bin/sh
# Gemini key lives only in the NetMovies admin panel; export it for this process, never to disk.
GEMINI_API_KEY=$(python3 -c 'import json;print(json.load(open("/secrets/admin.json")).get("gemini_api_key",""))' 2>/dev/null)
export GEMINI_API_KEY
exec litellm --config /app/config.yaml --port 4000
