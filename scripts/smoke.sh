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

# 0) Isınma — rebuild'in hemen ardından çalıştırıldığında container ayakta ama
#    henüz "healthy" değil; ısınmadan yapılan çağrılar boş dönüp yanlış alarm
#    üretiyordu. Sağlık raporu gelene kadar (en fazla 120 sn) beklenir.
step "Isınma"
for name in netmovies-engine netmovies-stream; do
  waited=0
  while [[ $waited -lt 120 ]]; do
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null)"
    [[ "$state" == "healthy" ]] && break
    sleep 3
    waited=$((waited + 3))
  done
  [[ "$state" == "healthy" ]] && ok "$name hazır (${waited}sn)" || fail "$name ısınmadı (${waited}sn, durum: ${state:-yok})"
done

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

# 6) Oynatma zinciri — TV, telefon ve web'in ortak ucu.
step "Oynatma zinciri (resolve_sources)"
first="$(curl -s --max-time 60 "${AUTH_ARGS[@]}" "$BASE/api/v1/aggregate_new?type=movie"   | "$PY" -c 'import json,sys; items=(json.load(sys.stdin).get("result") or {}).get("items") or []; i=items[0] if items else {}; print(i.get("plugin",""));print(i.get("url",""));print(i.get("title",""))' 2>/dev/null)"
plugin="$(printf '%s' "$first" | sed -n 1p)"
curl_url="$(printf '%s' "$first" | sed -n 2p)"
title="$(printf '%s' "$first" | sed -n 3p)"
if [[ -z "$plugin" ]]; then
  fail "katalog boş — zincir denenemedi"
else
  resolved="$(curl -s --max-time 90 "${AUTH_ARGS[@]}"     --get --data-urlencode "plugin=$plugin" --data-urlencode "title=$title" --data-urlencode "mode=fast"     "$BASE/api/v1/resolve_sources?encoded_url=$curl_url"     | "$PY" -c 'import json,sys; r=(json.load(sys.stdin).get("result") or {}); s=r.get("sources") or []; print(len(s), (s[0].get("language",{}).get("label") if s else "-"), len(r.get("diagnostics") or []))' 2>/dev/null || echo "0 - 0")"
  count="$(printf '%s' "$resolved" | cut -d' ' -f1)"
  label="$(printf '%s' "$resolved" | cut -d' ' -f2-)"
  if [[ "$count" -gt 0 ]]; then ok "$plugin · $count kaynak · ilk sıra: $label"; else fail "$plugin · zincir kaynak vermedi"; fi
fi

# 7) Sözleşme testleri — çalışan container içinde, gerçek import grafiğiyle.
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
