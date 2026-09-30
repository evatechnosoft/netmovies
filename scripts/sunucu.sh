#!/usr/bin/env bash
# Active/standby switch between the two NetMovies servers (laptop <-> ZimaOS).
# Exactly one server is "aktif": stack + tunnel up. The other is "yedek": stack
# stopped, so TV subnet scan and Cloudflare only ever find one server.
#
#   bash scripts/sunucu.sh durum          # which one is up
#   bash scripts/sunucu.sh gec zima       # move data + traffic to ZimaOS
#   bash scripts/sunucu.sh gec laptop     # move back to this laptop
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

health() { on "$1" "curl -s -o /dev/null -w '%{http_code}' --max-time 5 localhost:3310/api/v1/health || true"; }

durum() {
  for h in laptop zima; do
    echo "$h: health=$(health $h) rol=$(on $h 'cat .sunucu 2>/dev/null || echo ?') tunel=$(on $h "docker ps -q -f name=netmovies-tunnel | wc -l")"
  done
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

gec() {
  local dst=$1 src
  case "$dst" in laptop) src=zima ;; zima) src=laptop ;; *) echo "hedef: laptop|zima" >&2; exit 2 ;; esac

  echo "1/4 $src: yazmayı durdur (stream + tünel)"
  on "$src" "$COMPOSE stop cloudflared stream"
  echo "2/4 veri $src -> $dst"
  tasi "$src" "$dst"
  echo "3/4 $src: yedek moda"
  on "$src" "$COMPOSE stop && echo yedek > .sunucu"
  echo "4/4 $dst: güncelle + aç"
  local before after build=""
  before=$(on "$dst" "git rev-parse HEAD")
  on "$dst" "git pull --ff-only -q"
  after=$(on "$dst" "git rev-parse HEAD")
  [ "$before" != "$after" ] && build="--build"
  on "$dst" "$COMPOSE up -d $build && $COMPOSE up -d --force-recreate cloudflared && echo aktif > .sunucu"

  for _ in $(seq 1 30); do [ "$(health "$dst")" = 200 ] && break; sleep 5; done
  durum
}

case "${1:-durum}" in
  durum) durum ;;
  gec) gec "${2:-}" ;;
  *) echo "kullanım: $0 durum | gec <laptop|zima>" >&2; exit 2 ;;
esac
