"""Sağlayıcı bazlı uçtan uca oynatma taraması.

smoke.sh yalnız ilk sağlayıcının ilk kaynağını dener; burada HER sağlayıcının
HER alternatif kaynağı (oynatıcı sekmesi) manifeste kadar sürülür — bir sağlayıcı
"çalışıyor" görünürken ikinci/üçüncü oynatıcısı ölmüş olabiliyor.

    python scripts/chain_scan.py                  # movie + serie, sağlayıcı başına 1 içerik
    python scripts/chain_scan.py serie --n 3      # yalnız dizi, sağlayıcı başına 3 içerik
    BASE=https://w.evaitec.com python scripts/chain_scan.py

Katalogdaki `url` ZATEN URL-encoded: tekrar quote edilirse engine çift-encode
görür ve 0 kaynak döner; bu yüzden ham geçirilir.
"""
import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE = os.getenv("BASE", "http://localhost:3310").rstrip("/") + "/api/v1"


def get(path, encoded_url=None, **params):
    query = urllib.parse.urlencode(params)
    if encoded_url is not None:
        query = f"encoded_url={encoded_url}&{query}"
    with urllib.request.urlopen(f"{BASE}/{path}?{query}", timeout=240) as response:
        return json.load(response).get("result")


def playable(url):
    """Manifest gerçekten iniyor mu — kaynak bulunması oynadığı anlamına gelmiyor."""
    try:
        request = urllib.request.Request(url, headers={"Range": "bytes=0-200"})
        head = urllib.request.urlopen(request, timeout=90).read(8)
    except Exception as exc:
        return False, type(exc).__name__ + ": " + str(exc)[:60]
    if head.startswith(b"#EXTM3U"):
        return True, "#EXTM3U"
    return bool(head), repr(head)


def episode_url(item):
    """Dizi sayfasıysa ilk bölüm; katalog zaten bölüm verdiyse (DiziPal/DiziMom) kendisi."""
    detail = get("load_item", encoded_url=item["url"], plugin=item["plugin"]) or {}
    episodes = detail.get("episodes") or []
    if episodes:
        return episodes[0].get("url") or episodes[0].get("link") or item["url"]
    return item["url"]


def scan(kind, per_plugin):
    items = (get("aggregate_new", type=kind) or {}).get("items") or []
    picked = {}
    for item in items:
        plugin = item.get("plugin")
        if plugin:
            picked.setdefault(plugin, []).append(item)

    broken = []
    for plugin, plugin_items in sorted(picked.items()):
        for item in plugin_items[:per_plugin]:
            url = item["url"] if kind == "movie" else episode_url(item)
            title = (item.get("title") or "")[:30]
            try:
                result = get("resolve_sources", encoded_url=url, plugin=plugin,
                             title=item.get("title", ""), mode="full") or {}
            except Exception as exc:
                print(f"{plugin:18} {title:32} RESOLVE HATA {exc}")
                broken.append((plugin, title, "resolve"))
                continue
            sources = result.get("sources") or []
            if not sources:
                fails = [d["message"] for d in (result.get("diagnostics") or []) if d.get("level") == "fail"]
                print(f"{plugin:18} {title:32} KAYNAK YOK · {fails[:1]}")
                broken.append((plugin, title, "kaynak yok"))
                continue
            for source in sources:
                ok, note = playable(source.get("url", ""))
                name = (source.get("name") or "?").split("|")[-1].strip()[:22]
                print(f"{plugin:18} {title:32} {'OK   ' if ok else 'ÖLÜ  '} {name:24} {note}")
                if not ok:
                    broken.append((plugin, title, name))
    return broken


parser = argparse.ArgumentParser()
parser.add_argument("kinds", nargs="*", default=["movie", "serie"])
parser.add_argument("--n", type=int, default=1, help="sağlayıcı başına içerik sayısı")
args = parser.parse_args()

all_broken = []
for kind in args.kinds or ["movie", "serie"]:
    print(f"--- {kind} ---")
    all_broken += scan(kind, args.n)

print()
if all_broken:
    print(f"ÖLÜ KAYNAK: {len(all_broken)}")
    for plugin, title, name in all_broken:
        print(f"  {plugin} · {title} · {name}")
    raise SystemExit(1)
print("TÜM KAYNAKLAR OYNUYOR")
