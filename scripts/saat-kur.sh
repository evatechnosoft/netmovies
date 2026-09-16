#!/usr/bin/env bash
# NetMovies Mini — saate ADB ile kurulum (tek seferlik köprü).
#
# Neden var: evaitecOTA bileklikte APK kuramıyor ("abort"), ve saatteki sürümde
# kendi kendini güncelleme YOK. Uygulama içi güncelleme 0.1.5 ile geliyor —
# ama onu bileğe ilk kez koymanın tek yolu ADB. Bu betik o tek seferi yapar;
# sonraki sürümler uygulamanın kendi "⬆ güncelle" şeridinden iner.
#
# Saatte önce: Ayarlar → Sistem → Hakkında → Sürüm numarasına 7 kez dokun
#              → Ayarlar → Geliştirici seçenekleri → ADB hata ayıklama: AÇIK
#              → Wi-Fi üzerinden hata ayıklama: AÇIK (ekranda IP yazar)
#
# Kullanım:
#   bash scripts/saat-kur.sh              # ağı tarar, bulduğu saate kurar
#   bash scripts/saat-kur.sh 192.168.0.42 # adresi biliyorsan doğrudan

set -u

ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
if [ -z "${ADB:-}" ] || [ ! -e "$ADB" ]; then
  echo "adb bulunamadı. ADB=<yol> ile ver." >&2
  exit 1
fi

APK="$(ls -1 "$(dirname "$0")/../data/apk/"NetMovies-Wear-v*.apk 2>/dev/null | sort -V | tail -1)"
if [ -z "$APK" ]; then
  echo "data/apk/ içinde NetMovies-Wear-v*.apk yok. Önce: cd client-tv && ./gradlew :wear:assembleDebug" >&2
  exit 1
fi
echo "APK: $(basename "$APK")"

baglan() {
  "$ADB" connect "$1:5555" 2>&1 | grep -qiE "connected to" && return 0 || return 1
}

hedef=""
if [ $# -ge 1 ]; then
  echo "bağlanılıyor: $1"
  baglan "$1" && hedef="$1:5555"
else
  # Ağı tara: 5555'e cevap veren adresler. Saatin IP'si her açılışta değişebiliyor,
  # elle yazmak yerine aranır. Paralel — 254 adres sırayla dakikalar sürer.
  # Makinede birden çok adaptör olabiliyor (Wi-Fi + sanal ağlar): TEK önek
  # seçmek saatin bulunduğu ağı kaçırıyordu. Hepsi taranır.
  onekler="$(ipconfig 2>/dev/null | grep -oE '192\.168\.[0-9]+\.' | sort -u)"
  onekler="${onekler:-192.168.0.}"
  echo "ağ taranıyor: $(echo $onekler | tr '
' ' ')1-254 (5555)"
  for onek in $onekler; do
    for i in $(seq 1 254); do
      ( baglan "${onek}${i}" && echo "BULUNDU ${onek}${i}" ) &
    done
  done >/tmp/saat-tara.$$ 2>/dev/null
  wait
  hedef="$(grep -m1 '^BULUNDU ' /tmp/saat-tara.$$ | awk '{print $2}')"
  rm -f /tmp/saat-tara.$$
  [ -n "$hedef" ] && hedef="$hedef:5555"
fi

if [ -z "$hedef" ]; then
  echo "Saat bulunamadı. Saatte 'Wi-Fi üzerinden hata ayıklama' açık mı? Ekrandaki IP ile:" >&2
  echo "  bash scripts/saat-kur.sh <ip>" >&2
  exit 1
fi

echo "hedef: $hedef"
"$ADB" -s "$hedef" shell getprop ro.product.model 2>/dev/null

# -r: mevcut sürümün üstüne. -d: sürüm düşürmeye de izin (geri almak gerekirse).
if "$ADB" -s "$hedef" install -r -d "$APK"; then
  echo "KURULDU. Saatte NetMovies Mini'yi aç — bundan sonraki sürümler '⬆ güncelle' şeridinden iner."
else
  echo "Kurulum düştü. Sık sebepler:" >&2
  echo "  INSTALL_FAILED_UPDATE_INCOMPATIBLE → imza farklı: önce 'adb -s $hedef uninstall com.evaitec.netmovies.wear'" >&2
  echo "  INSTALL_FAILED_ABORTED             → saatte onay ekranı çıkmış olabilir, bileğe bak" >&2
  exit 1
fi
