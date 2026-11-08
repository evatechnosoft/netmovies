# A single YouTube episode video belongs to the series playlist the user already has.
#
# Kaos Show (8 Nov): episodes opened as single videos were saved as separate "movie"
# records — Devam Et showed old episodes as loose cards and opening one had no next
# episode. Matching is by exact video id inside a playlist the user already keeps
# (Devam Et / lists / favourites), so a wrong series can never be picked.

import re
import time
from urllib.parse import unquote

from Public.Home.Libs import watch_store
from .                import fuck_dmca

_VIDEO = re.compile(r"[?&]v=([\w-]{11})")
_TTL   = 3600
_onbellek: dict[str, tuple[float, dict | None]] = {}


def video_id(url: object) -> str:
    m = _VIDEO.search(unquote(str(url or "")))
    return m.group(1) if m else ""


def _oynatma_listeleri() -> list[dict]:
    satirlar = watch_store.list_continue_watching(100) + watch_store.list_favorites()
    for ad in watch_store.ALLOWED_LISTS:
        satirlar += watch_store.list_user_list(ad, 100)
    goruldu: set[str] = set()
    sonuc = []
    for r in satirlar:
        url = str(r.get("content_url") or "")
        if r.get("plugin") == "YouTube" and "list=" in url and url not in goruldu:
            goruldu.add(url)
            sonuc.append(r)
    return sonuc


async def diziye_bagla(url: str) -> dict | None:
    """Series fields (title, content_url, poster, episode ref) for a known playlist episode, else None."""
    vid = video_id(url)
    if not vid or "list=" in str(url):
        return None
    hit = _onbellek.get(vid)
    if hit and hit[0] > time.time():
        return hit[1]
    sonuc = None
    for r in _oynatma_listeleri():
        try:
            detay = await fuck_dmca("/load_item", params={"plugin": "YouTube", "encoded_url": r["content_url"]})
        except Exception:
            continue
        for b in (detay or {}).get("episodes") or []:
            if video_id(b.get("url")) == vid:
                sonuc = {
                    "title"      : r.get("title") or (detay or {}).get("title") or "",
                    "content_url": r["content_url"],
                    "poster"     : r.get("poster") or (detay or {}).get("poster") or "",
                    "episode"    : f"S{b.get('season') or 1}B{b.get('episode')}",
                }
                break
        if sonuc:
            break
    _onbellek[vid] = (time.time() + (_TTL if sonuc else 300), sonuc)   # a new playlist shows up soon
    return sonuc
