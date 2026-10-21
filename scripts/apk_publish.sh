#!/bin/bash
# Build, sign and publish the TV or Wear APK on ZimaOS (signing key lives only there).
# Usage (from anywhere):
#   ssh zima 'bash /DATA/AppData/netmovies/scripts/apk_publish.sh tv "notes"'
#   ssh zima 'bash /DATA/AppData/netmovies/scripts/apk_publish.sh wear "notes"'
#   ... apk_publish.sh tv --yalniz-derle      # build + certificate check, publish nothing
# Bump the version in client-tv/{app,wear}/build.gradle.kts and push before running.
# Image: scripts/android-build/Dockerfile → netmovies-android-build.
set -euo pipefail

HEDEF=${1:?tv|wear}
NOT=${2:-}
REPO=/DATA/AppData/netmovies
IMZA=/DATA/AppData/netmovies-signing
CACHE=/DATA/AppData/netmovies-build/gradle
GH=/DATA/AppData/claude-rc/cfg/gh
# Certificate of the key every installed NetMovies app must keep (lost-key incident 21 Oct 2026).
SERTIFIKA=7328a32d

case $HEDEF in
  tv)   MODUL=app;  DEG=appVersion;  AD=NetMovies-TV ;;
  wear) MODUL=wear; DEG=wearVersion; AD=NetMovies-Wear ;;
  *) echo "hedef tv|wear olmali" >&2; exit 2 ;;
esac

if [ "${IC:-}" != 1 ]; then
  cd "$REPO"
  git pull --ff-only -q
  export DOCKER_CONFIG=/tmp/dc
  exec docker run --rm --user 999:1000 \
    -v "$REPO":/src -v "$IMZA":/imza -v "$CACHE":/cache -v "$GH":/gh-ro:ro \
    -e IC=1 -e ANDROID_USER_HOME=/imza -e GRADLE_USER_HOME=/cache -e HOME=/cache/home \
    -e GH_CONFIG_DIR=/tmp/gh -e GH_NO_UPDATE_NOTIFIER=1 \
    -w /src netmovies-android-build bash scripts/apk_publish.sh "$@"
fi

# --- inside the container ---
# gh migrates its config on first run; the mounted original stays untouched.
cp -r /gh-ro /tmp/gh
V=$(sed -n "s/^val $DEG = \"\(.*\)\"/\1/p" client-tv/$MODUL/build.gradle.kts)
[ -n "$V" ] || { echo "surum okunamadi" >&2; exit 1; }
DAL=$(git rev-parse --abbrev-ref HEAD)
echo "== $HEDEF $V ($DAL)"

(cd client-tv && ./gradlew -q --no-daemon :$MODUL:assembleDebug)
APK=$(ls client-tv/$MODUL/build/outputs/apk/debug/$AD-v$V.apk)

# Gate: a silently different key makes every device reject the OTA update.
apksigner verify --print-certs "$APK" | grep -q "SHA-256 digest: $SERTIFIKA" \
  || { apksigner verify --print-certs "$APK" | grep SHA-256; echo "IMZA FARKLI — yayin durduruldu" >&2; exit 1; }
echo "imza tamam: $SERTIFIKA…"

[ "$NOT" = --yalniz-derle ] && { ls -l "$APK"; exit 0; }
[ -n "$NOT" ] || { echo "yayin notu gerekli" >&2; exit 2; }

cp "$APK" data/apk/

if [ $HEDEF = tv ]; then
  gh release create "v$V-poc" "$APK" -R evatechnosoft/netmovies --target "$DAL" --prerelease \
    -t "v$V" -n "$NOT"
  ETIKET=netmovies-tv-v$V; DOSYA=netmovies-tv-$V.apk; IDLER="netmovies-tv netmovies-phone"
else
  ETIKET=netmovies-wear-v$V; DOSYA=netmovies-mini-watch-$V.apk; IDLER=netmovies-mini-watch
fi
cp "$APK" "/tmp/$DOSYA"
gh release create "$ETIKET" "/tmp/$DOSYA" -R evatechnosoft/evaglass-releases -t "$ETIKET" -n "$NOT"

# apps.json: update the entries in place, then PUT with the blob sha (optimistic lock).
gh api repos/evatechnosoft/evaitec-ota/contents/apps.json > /tmp/aj.json
python3 - "$V" "$NOT" "/tmp/$DOSYA" "https://github.com/evatechnosoft/evaglass-releases/releases/download/$ETIKET/$DOSYA" $IDLER <<'PY'
import base64, datetime, hashlib, json, os, sys
v, notes, apk, url, *ids = sys.argv[1:]
meta = json.load(open("/tmp/aj.json"))
d = json.loads(base64.b64decode(meta["content"]))
major, minor, patch = map(int, v.split("."))
data = open(apk, "rb").read()
found = 0
for a in d["apps"]:
    if a["id"] in ids:
        a.update(versionCode=major * 10000 + minor * 100 + patch, version=v,
                 releaseDate=datetime.date.today().isoformat(), sizeBytes=len(data),
                 downloadUrl=url, sha256=hashlib.sha256(data).hexdigest(), notes=notes)
        found += 1
assert found == len(ids), f"apps.json: {found}/{len(ids)} kayit bulundu"
d["updated"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
body = json.dumps(d, ensure_ascii=False, indent=2) + "\n"
json.dump({"message": f"{ids[0]} {v}", "sha": meta["sha"],
           "content": base64.b64encode(body.encode()).decode()}, open("/tmp/put.json", "w"))
PY
gh api -X PUT repos/evatechnosoft/evaitec-ota/contents/apps.json --input /tmp/put.json --jq .commit.sha
echo "== yayinlandi: $HEDEF $V"
