#!/usr/bin/env bash
# NetMovies — Faz 1 kapı kontrolü (deploy sonrası tek komutla doğrulama).
#
#   bash scripts/smoke.sh            # yerel yığın
#   BASE=https://w.evaitec.com bash scripts/smoke.sh   # tünel üzerinden
#
# Her adım gerçek HTTP/Docker çıktısına bakar; hiçbir adım "muhtemelen çalışıyor"
# varsaymaz. Bir adım kırmızıysa çıkış kodu 1'dir.

set -uo pipefail

BASE="${BASE:-http://localhost:3310}"
AUTH_ARGS=()
if [[ -n "${AUTH_USER:-}" ]]; then
  AUTH_ARGS=(-u "${AUTH_USER}:${AUTH_PASS:-}")
fi

# Git Bash (Windows) container içi mutlak yolları C:\ ile değiştiriyor; Linux'ta etkisiz.
export MSYS_NO_PATHCONV=1
PY="$(command -v python3 || command -v python)"
if [[ -z "$PY" ]]; then
  echo "python bulunamadı (JSON sayımları için gerekli)" >&2
  exit 2
fi

fails=0

ok()   { printf '  [OK]   %s\n' "$1"; }
fail() { printf '  [HATA] %s\n' "$1"; fails=$((fails + 1)); }

step() { printf '\n== %s\n' "$1"; }

# 1) Container sağlığı — stream, engine "healthy" raporlamalı.
step "Container sağlığı"
for name in netmovies-engine netmovies-stream; do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null)"
  case "$status" in
    healthy) ok "$name: healthy" ;;
    "")      fail "$name: container yok" ;;
    *)       fail "$name: $status" ;;
  esac
done

# 2) Sağlık ucu — Docker healthcheck ile aynı sözleşme.
step "API sağlık ucu"
code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE/api/v1/health")"
[[ "$code" == "200" ]] && ok "GET /api/v1/health -> 200" || fail "GET /api/v1/health -> $code"

# 3) Eklenti listesi — gateway'in provider'a ulaştığını kanıtlar.
step "Eklenti listesi"
plugins="$(curl -s --max-time 20 "${AUTH_ARGS[@]}" "$BASE/api/v1/get_plugin_names")"
count="$(printf '%s' "$plugins" | "$PY" -c 'import json,sys; print(len(json.load(sys.stdin).get("result") or []))' 2>/dev/null || echo 0)"
[[ "$count" -gt 0 ]] && ok "$count eklenti yüklü" || fail "eklenti listesi boş veya okunamadı"

# 4) Birleşik katalog — ana sayfanın ve TV home ekranının kaynağı.
#    Tip adları engine sözleşmesidir ("serie", "series" değil); TV home bu beş
#    tipi çekiyor, biri boşsa o raf cihazda boş görünür.
#    Soğuk çağrı yavaş (ağır scrape); timeout cömert tutuldu.
step "Birleşik katalog (aggregate_new)"
for kind in movie serie serie_local serie_foreign live; do
  items="$(curl -s --max-time 90 "${AUTH_ARGS[@]}" "$BASE/api/v1/aggregate_new?type=$kind" \
    | "$PY" -c 'import json,sys; d=json.load(sys.stdin).get("result") or {}; print(len(d.get("items") or []))' 2>/dev/null || echo 0)"
  [[ "$items" -gt 0 ]] && ok "$kind: $items içerik" || fail "$kind: içerik yok"
done

# 5) Canlı kanal ucu — TV istemcisinin canlı rafı bu uçtan besleniyor.
step "Canlı kanallar (quick_channels)"
channels="$(curl -s --max-time 60 "${AUTH_ARGS[@]}" "$BASE/api/v1/quick_channels" \
  | "$PY" -c 'import json,sys; print(len(json.load(sys.stdin).get("result") or []))' 2>/dev/null || echo 0)"
[[ "$channels" -gt 0 ]] && ok "$channels kanal" || fail "kanal listesi boş"

# 6) Sözleşme testleri — çalışan container içinde, gerçek import grafiğiyle.
step "Gateway sözleşme testleri"
if docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests >/dev/null 2>&1; then
  ok "stream/tests: tümü geçti"
else
  fail "stream/tests: başarısız (ayrıntı: docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests -v)"
fi

printf '\n'
if [[ "$fails" -eq 0 ]]; then
  printf 'SONUÇ: kapı YEŞİL (%s)\n' "$BASE"
  exit 0
fi
printf 'SONUÇ: %d adım KIRMIZI (%s)\n' "$fails" "$BASE"
exit 1
