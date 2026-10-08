#!/bin/sh
# Mi Box günlüğünü sunucuda kalıcı tutar (ağdan ADB, 5555). TV'nin kendi
# client_log'u bellekte ve yalnız sunucu açıkken yüklenir; "WOL gitti mi" gibi
# sunucu kapalıyken olan şeylerin kanıtı burada kalır (Dean, 8 Ekim).
# Anahtar data/atv/.android'de: TV'de "her zaman izin ver" bir kez onaylanır.
# ponytail: tek dosya, 20 MB'ta .1'e döner; daha uzun geçmiş gerekirse logrotate.
HOST="${ATV_HOST}:5555"
LOG=/app/data/atv/tv.log
while true; do
  [ -f "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 20971520 ] && mv "$LOG" "$LOG.1"
  adb connect "$HOST" >/dev/null 2>&1
  if adb -s "$HOST" get-state 2>/dev/null | grep -q device; then
    adb -s "$HOST" logcat -v time -T 1 NetMoviesPlayback:V AndroidRuntime:E '*:S' >> "$LOG" 2>&1
  fi
  sleep 30
done
