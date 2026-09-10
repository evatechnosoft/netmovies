"""Her sağlayıcı için uçtan uca oynatma zinciri taraması.

katalog -> (dizi ise) ilk bölüm -> resolve_sources -> manifest ilk baytları.
Katalogdaki `url` ZATEN URL-encoded: tekrar quote edilirse engine çift-encode
görür ve 0 kaynak döner. Bu yüzden ham (encode edilmeden) geçirilir.
"""
import json
import sys
import urllib.parse
import urllib.request

BASE = "http://localhost:3310/api/v1"


def get(path, encoded_url=None, **params):
    query = urllib.parse.urlencode(params)
    if encoded_url is not None:
        query = f"encoded_url={encoded_url}&{query}"
    with urllib.request.urlopen(f"{BASE}/{path}?{query}", timeout=180) as r:
        return json.load(r).get("result")


def first_episode(item):
    detail = get("load_item", encoded_url=item["url"], plugin=item["plugin"]) or {}
    for key in ("episodes", "bolumler", "episode_list"):
        eps = detail.get(key)
        if eps:
            return eps[0].get("url") or eps[0].get("link"), detail
    return None, detail


def scan(kind):
    items = (get("aggregate_new", type=kind) or {}).get("items") or []
    seen = {}
    for item in items:
        plugin = item.get("plugin")
        if plugin and plugin not in seen:
            seen[plugin] = item
    for plugin, item in seen.items():
        url, note = item["url"], ""
        if kind != "movie":
            # DiziPal/DiziMom katalogda zaten BÖLÜM url'si veriyor: episodes boşsa
            # dizi sayfası değil bölüm sayfasıdır, doğrudan çözülür.
            ep_url, _ = first_episode(item)
            url, note = (ep_url, "ep") if ep_url else (url, "bölüm-url")
        try:
            res = get("resolve_sources", encoded_url=url, plugin=plugin,
                      title=item.get("title", ""), mode="fast") or {}
        except Exception as exc:
            print(f"{plugin:18} {kind:6} RESOLVE HATA {exc}")
            continue
        sources = res.get("sources") or []
        if not sources:
            fails = [d["message"] for d in (res.get("diagnostics") or []) if d.get("level") == "fail"]
            print(f"{plugin:18} {kind:6} KAYNAK YOK {note} · {fails[:2]}")
            continue
        play = sources[0].get("url", "")
        try:
            req = urllib.request.Request(play, headers={"Range": "bytes=0-200"})
            head = urllib.request.urlopen(req, timeout=90).read(8)
        except Exception as exc:
            print(f"{plugin:18} {kind:6} MANIFEST HATA {len(sources)} kaynak · {exc}")
            continue
        ok = "OK " if head.startswith(b"#EXTM3U") else "ŞÜPHE"
        print(f"{plugin:18} {kind:6} {ok} {len(sources)} kaynak · {item.get('title','')[:28]} · {head!r}")


for kind in sys.argv[1:] or ["movie", "serie"]:
    print(f"--- {kind} ---")
    scan(kind)
