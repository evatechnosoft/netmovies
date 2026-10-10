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
  # "unauthorized" bağlantı onaydan sonra kendiliğinden düzelmez; kopar, yeniden bağlan.
  adb -s "$HOST" get-state 2>/dev/null | grep -q device || adb disconnect "$HOST" >/dev/null 2>&1
  adb connect "$HOST" >/dev/null 2>&1
  if adb -s "$HOST" get-state 2>/dev/null | grep -q device; then
    # Uyanış anı yeniden bağlanmadan önce olur: son yazılan satırdan devam et (-T 1 kaçırıyordu).
    SON=$(tail -n 1 "$LOG" 2>/dev/null | grep -oE '^[0-9]{2}-[0-9]{2} [0-9:.]{12}')
    # Güç/CEC/BT kumanda satırları "kumandadan açılmıyor" şikâyetinin kanıtı (10 Eki).
    adb -s "$HOST" logcat -v time -T "${SON:-1}" NetMoviesPlayback:V AndroidRuntime:E \
      PowerManagerService:I HdmiControlService:I HdmiCecLocalDevicePlayback:I bt_btif_hh:I '*:S' >> "$LOG" 2>&1
  fi
  sleep 30
done
