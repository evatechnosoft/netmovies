#!/usr/bin/env bash
# Active/standby switch between the two NetMovies servers (laptop <-> ZimaOS).
# Exactly one server is "aktif": stack + tunnel up. The other is "yedek": stack
# stopped, so TV subnet scan and Cloudflare only ever find one server.
#
#   bash scripts/sunucu.sh durum          # which one is up
#   bash scripts/sunucu.sh gec zima       # move data + traffic to ZimaOS
#   bash scripts/sunucu.sh gec laptop     # move back to this laptop
#   ... gec <hedef> --zorla               # switch even if proxy secrets differ
#   bash scripts/sunucu.sh reddet         # (re)start the :3310 RST responder on a standby laptop
#
# gec order keeps the TV outage short (it has ~5 min of buffer):
#   0. both .env must sign proxy tokens with the same secret (sha256 compared)
#   1. prep, source still live: pull + build on target, start warp/doh/engine
#   2. cut: stop source stream+tunnel, copy data, start target stream+tunnel,
#      wait for health 200 -> prints "kesinti: N sn"
#   3. cleanup: stop whole source stack, mark roles
#
# Runs from the laptop (Git Bash); reaches ZimaOS over `ssh zima`.
set -euo pipefail
export MSYS_NO_PATHCONV=1  # Git Bash would rewrite /data, /tmp paths

LAPTOP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ZIMA_DIR=/DATA/AppData/netmovies
COMPOSE="docker compose --profile tunnel"

on() { # on <laptop|zima> <command...>
  local host=$1; shift
  # ZimaOS: /DATA/.docker is unreadable for dean, which hides the compose plugin.
  if [ "$host" = laptop ]; then (cd "$LAPTOP_DIR" && bash -c "$*"); else ssh zima "export DOCKER_CONFIG=/tmp/dc; cd $ZIMA_DIR && $*"; fi
}

# Standby laptop keeps :3310 answered with an instant RST (scripts/yedek_reddet.py) so a TV
# still pointing here fails fast and rediscovers instead of hanging on the stealth firewall.
REDDET_PS="Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | ? CommandLine -match 'yedek_reddet' | % { Stop-Process -Id \$_.ProcessId -Force }"
reddet_dur() { powershell -NoProfile -Command "$REDDET_PS" >/dev/null 2>&1 || true; }
reddet_bas() {
  reddet_dur
  powershell -NoProfile -Command "Start-Process python -ArgumentList '\"$(cygpath -m "$LAPTOP_DIR")/scripts/yedek_reddet.py\"' -WindowStyle Hidden"
}
reddet_var() { powershell -NoProfile -Command "@(Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | ? CommandLine -match 'yedek_reddet').Count" | tr -d ''; }

health() { on "$1" "curl -s -o /dev/null -w '%{http_code}' --max-time 5 localhost:3310/api/v1/health || true"; }

durum() {
  for h in laptop zima; do
    echo "$h: health=$(health $h) rol=$(on $h 'cat .sunucu 2>/dev/null || echo ?') tunel=$(on $h "docker ps -q -f name=netmovies-tunnel | wc -l")"
  done
  echo "laptop reddet: $(reddet_var)"
  echo "w.evaitec.com: $(curl -s -o /dev/null -w '%{http_code}' --max-time 10 https://w.evaitec.com)"
}

# Copy live state (db + json settings + lists + missing APKs) src -> dst.
tasi() {
  local src=$1 dst=$2 ts; ts=$(date +%Y%m%d-%H%M%S)
  on "$dst" "cp data/netmovies.db data/netmovies.db.bak-$ts"
  if [ "$src" = laptop ]; then
    (cd "$LAPTOP_DIR/data" && tar -cf - netmovies.db *.json) | ssh zima "tar -C $ZIMA_DIR/data -xf -"
    (cd "$LAPTOP_DIR" && tar -cf - lists) | ssh zima "tar -C $ZIMA_DIR -xf -"
  else
    ssh zima "cd $ZIMA_DIR/data && tar -cf - netmovies.db *.json" | tar -C "$LAPTOP_DIR/data" -xf -
    ssh zima "cd $ZIMA_DIR && tar -cf - lists" | tar -C "$LAPTOP_DIR" -xf -
  fi
  # APKs: copy only names the destination lacks (549 MB total, most already there).
  local have_src have_dst
  have_src=$(on "$src" "ls data/apk"); have_dst=$(on "$dst" "ls data/apk")
  for f in $(comm -23 <(echo "$have_src" | sort) <(echo "$have_dst" | sort)); do
    echo "apk: $f"
    if [ "$src" = laptop ]; then scp -q "$LAPTOP_DIR/data/apk/$f" "zima:$ZIMA_DIR/data/apk/"; else scp -q "zima:$ZIMA_DIR/data/apk/$f" "$LAPTOP_DIR/data/apk/"; fi
  done
}

# Short hash of the proxy signing secret (PROXY_TOKEN_SECRET, else AUTH_PASS); never prints the value.
secret_hash() {
  on "$1" 'v() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d "\r"; }; s=$(v PROXY_TOKEN_SECRET); [ -n "$s" ] || s=$(v AUTH_PASS); printf %s "$s" | sha256sum | cut -c1-16'
}

gec() {
  local dst=$1 zorla=${2:-} src
  case "$dst" in laptop) src=zima ;; zima) src=laptop ;; *) echo "hedef: laptop|zima" >&2; exit 2 ;; esac

  if [ "$(secret_hash "$src")" != "$(secret_hash "$dst")" ]; then
    echo "UYARI: proxy imzası uyuşmuyor, TV'deki oynatma geçişte kopar (.env PROXY_TOKEN_SECRET/AUTH_PASS)" >&2
    [ "$zorla" = --zorla ] || exit 1
  fi

  echo "1/3 $dst: hazırlık ($src canlı)"
  local before after
  before=$(on "$dst" "git rev-parse HEAD")
  on "$dst" "git pull --ff-only -q"
  after=$(on "$dst" "git rev-parse HEAD")
  if [ "$before" != "$after" ] || ! on "$dst" "docker image inspect netmovies-stream netmovies-engine >/dev/null 2>&1"; then
    on "$dst" "$COMPOSE build"
  fi
  on "$dst" "$COMPOSE up -d warp doh engine"
  local i
  for i in $(seq 1 24); do
    [ "$(on "$dst" "docker inspect -f '{{.State.Health.Status}}' netmovies-engine")" = healthy ] && break
    [ "$i" = 24 ] && { echo "$dst: engine 120 sn'de healthy olmadı, $src dokunulmadı" >&2; exit 1; }
    sleep 5
  done

  echo "2/3 kesim: $src -> $dst"
  local t0=$SECONDS
  on "$src" "$COMPOSE stop cloudflared stream"
  [ "$dst" = laptop ] && reddet_dur  # frees :3310 for the stream container
  tasi "$src" "$dst"
  on "$dst" "$COMPOSE up -d stream cloudflared && $COMPOSE up -d --force-recreate cloudflared"
  for i in $(seq 1 12); do [ "$(health "$dst")" = 200 ] && break; sleep 5; done
  [ "$(health "$dst")" = 200 ] || echo "UYARI: $dst health 60 sn'de 200 olmadı" >&2
  local kesinti=$((SECONDS - t0))

  echo "3/3 $src: yedek moda"
  on "$src" "$COMPOSE stop && echo yedek > .sunucu"
  on "$dst" "echo aktif > .sunucu"
  [ "$src" = laptop ] && reddet_bas
  durum
  echo "kesinti: $kesinti sn"
}

case "${1:-durum}" in
  durum) durum ;;
  gec) gec "${2:-}" "${3:-}" ;;
  reddet) [ "$(on laptop 'cat .sunucu 2>/dev/null')" = yedek ] && reddet_bas || echo "laptop yedek değil, reddet başlatılmadı" >&2 ;;
  *) echo "kullanım: $0 durum | gec <laptop|zima> [--zorla] | reddet" >&2; exit 2 ;;
esac
