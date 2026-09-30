#!/usr/bin/env bash
# Rebuild engine+stream (+tunnel recreate) only when the TV is not watching.
# Idle = no now_playing, or paused for 3 polls, or within the last 90 s of the item.
# Usage: nohup bash scripts/izleme-bitince-kur.sh > kur.log 2>&1 &
set -u
cd "$(dirname "$0")/.."
pause=0
while :; do
  s=$(curl -s --max-time 5 localhost:3310/api/v1/remote/status | python -c '
import sys, json
try:
    r = json.load(sys.stdin)["result"]; n = r.get("now_playing") or {}
    print("idle" if not n or not r.get("tv_online") else ("son" if (n.get("duration") or 0) - (n.get("position") or 0) < 90 else ("pause" if not n.get("playing") else "oynuyor")))
except Exception:
    print("bilinmiyor")')
  echo "$(date +%H:%M:%S) $s"
  case "$s" in
    idle|son) break ;;
    pause) pause=$((pause + 1)); [ "$pause" -ge 3 ] && break ;;
    *) pause=0 ;;
  esac
  sleep 60
done
echo "$(date +%H:%M:%S) kuruluyor"
docker compose --profile tunnel up -d --build engine stream && docker compose --profile tunnel up -d --force-recreate cloudflared
curl -s --retry 20 --retry-delay 3 --retry-all-errors -o /dev/null -w "health %{http_code}\n" localhost:3310/api/v1/health
curl -s -o /dev/null -w "tunel %{http_code}\n" https://w.evaitec.com
curl -s "http://192.168.1.103:3310/api/v1/remote/token" | head -c 120; echo
echo "$(date +%H:%M:%S) BITTI"
