#!/usr/bin/env bash
# /data gece yedegi (ZimaOS). take: tutarli tar.gz uret + eskileri buda; verify: son tar'i dene.
# python3 sqlite3.backup() dogrudan host dosyasinda (WAL/kilit sorunu yok, stream kosmasa da calisir).
set -euo pipefail
APP=/DATA/AppData/netmovies
SRC=$APP/data                      # docker-compose.yml: ./data:/data
DST=/DATA/Backup/netmovies
KEEP=14
JSONS="admin.json prefs.json markers.json lang_memo.json source_score.json yz_cihazlar.json"
COUNT="import sqlite3,sys;print(sqlite3.connect(sys.argv[1],uri=True).execute('SELECT count(*) FROM watch_history').fetchone()[0])"

take() {
  local ts tmp; ts=$(date +%Y%m%d-%H%M%S); tmp=$(mktemp -d); trap 'rm -rf "$tmp"' RETURN
  mkdir -p "$DST"
  # sqlite3 backup() API: canli DB kilitlenmeden tutarli kopya
  python3 -c "import sqlite3,sys;s=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True);d=sqlite3.connect(sys.argv[2]);s.backup(d);d.close()" "$SRC/netmovies.db" "$tmp/netmovies.db"
  for f in $JSONS; do if [ -f "$SRC/$f" ]; then cp "$SRC/$f" "$tmp/"; fi; done
  tar -czf "$DST/netmovies-$ts.tar.gz" -C "$tmp" .
  # yalniz bu dizin, yalniz netmovies-*.tar.gz
  find "$DST" -maxdepth 1 -name 'netmovies-*.tar.gz' | sort | head -n -"$KEEP" | while read -r o; do rm -f "$o"; done
  ls -l "$DST/netmovies-$ts.tar.gz"
}

verify() {
  local last tmp n_bak n_live; tmp=$(mktemp -d); trap 'rm -rf "$tmp"' RETURN
  last=$(find "$DST" -maxdepth 1 -name 'netmovies-*.tar.gz' | sort | tail -n1)
  [ -n "$last" ] || { echo "FAIL: yedek yok"; return 1; }
  tar -xzf "$last" -C "$tmp"
  [ "$(python3 -c "import sqlite3,sys;print(sqlite3.connect(sys.argv[1]).execute('PRAGMA integrity_check').fetchone()[0])" "$tmp/netmovies.db")" = ok ] || { echo "FAIL: integrity_check"; return 1; }
  n_bak=$(python3 -c "$COUNT" "$tmp/netmovies.db")
  n_live=$(python3 -c "$COUNT" "file:$SRC/netmovies.db?mode=ro")
  python3 -c "import sys;b,l=int(sys.argv[1]),int(sys.argv[2]);sys.exit(0 if abs(b-l)<=max(1,0.05*l) else 1)" "$n_bak" "$n_live" || { echo "FAIL: satir $n_bak vs canli $n_live"; return 1; }
  [ -f "$APP/.env" ] || { echo "FAIL: .env yok"; return 1; }
  echo "OK $(basename "$last") integrity=ok watch_history=$n_bak canli=$n_live .env=var"
}

case "${1:-}" in take) take;; verify) verify;; *) echo "kullanim: $0 take|verify" >&2; exit 2;; esac
