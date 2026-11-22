#!/usr/bin/env python3
"""TV'de içerik aç: başlığı ara, bölümü NUMARAYLA bul, `remote/play` gönder.

    python scripts/tv_ac.py "Dexter" 4 11            # dizi: sezon bölüm
    python scripts/tv_ac.py "Dexter" 4 11 --kuru     # yalnız göster, gönderme
    python scripts/tv_ac.py "Heat" --plugin HDFilmCehennemi

Oynayan bir şey varsa sormadan geçer (`force`; client-tv bu alanı tanıyan sürüm
gerekir, eskisi "açayım mı?" kartı gösterir). `--sor` ile kart davranışı korunur.
Adres: NM_BASE (varsayılan ev sunucusu), sağlayıcı: --plugin ya da NM_TV_PLUGIN.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

BASE = os.getenv("NM_BASE", "http://192.168.0.186:3310").rstrip("/")


def _api(yol: str, sorgu: dict[str, str] | None = None, govde: dict | None = None) -> dict:
    adres = f"{BASE}/api/v1/{yol}" + (f"?{urllib.parse.urlencode(sorgu)}" if sorgu else "")
    veri = json.dumps(govde).encode() if govde is not None else None
    istek = urllib.request.Request(adres, data=veri, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(istek, timeout=60) as yanit:
        return json.load(yanit)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("baslik")
    ap.add_argument("sezon", type=int, nargs="?")
    ap.add_argument("bolum", type=int, nargs="?")
    ap.add_argument("--plugin", default=os.getenv("NM_TV_PLUGIN", "DiziAbi"))
    ap.add_argument("--kuru", action="store_true", help="komutu gönderme, yalnız yazdır")
    ap.add_argument("--sor", action="store_true", help="oynayan içerik varsa TV'de onay kartı çıksın")
    a = ap.parse_args()

    sonuclar = _api("search", {"plugin": a.plugin, "query": a.baslik}).get("result") or []
    # Tam ad eşleşmesi önce: "Dexter" araması "Dexter: New Blood"u da getirir.
    sonuclar.sort(key=lambda s: (s.get("title") or "").strip().lower() != a.baslik.strip().lower())
    if not sonuclar:
        print(f"{a.plugin}: '{a.baslik}' bulunamadı", file=sys.stderr)
        return 1
    secilen = sonuclar[0]
    url = urllib.parse.unquote(secilen["url"])
    komut = {"plugin": a.plugin, "url": url, "title": secilen.get("title") or a.baslik,
             "poster": secilen.get("poster") or "", "force": not a.sor}

    if a.bolum is not None:
        bolumler = (_api("load_item", {"plugin": a.plugin, "encoded_url": url}).get("result") or {}).get("episodes") or []
        # Sıra değil numara: sağlayıcılar farklı bölümden başlıyor (episode-match-by-number).
        sira = next((i for i, b in enumerate(bolumler) if b.get("season") == a.sezon and b.get("episode") == a.bolum), None)
        if sira is None:
            print(f"{a.plugin}: S{a.sezon}B{a.bolum} listede yok ({len(bolumler)} bölüm)", file=sys.stderr)
            return 1
        komut["episode"] = sira

    print(json.dumps(komut, ensure_ascii=False))
    if a.kuru:
        return 0
    print(json.dumps(_api("remote/play", govde=komut).get("result"), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
