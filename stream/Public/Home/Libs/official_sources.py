"""Curated links to broadcaster-owned live and on-demand pages.

These are navigation links, not scraped manifests. The broadcaster keeps
control of playback, geo restrictions, advertising and availability.
"""

from __future__ import annotations

from typing import TypedDict


class OfficialSource(TypedDict):
    title: str
    category: str
    broadcaster: str
    description: str
    url: str
    icon: str


OFFICIAL_SOURCES: tuple[OfficialSource, ...] = (
    {
        "title": "Star TV Canlı",
        "category": "Canlı TV",
        "broadcaster": "Star TV",
        "description": "Resmi canlı yayın ve yayın akışı.",
        "url": "https://www.startv.com.tr/canli-yayin",
        "icon": "fa-tv",
    },
    {
        "title": "Show TV Canlı",
        "category": "Canlı TV",
        "broadcaster": "Show TV",
        "description": "Resmi canlı yayın ve program akışı.",
        "url": "https://www.showtv.com.tr/canli-yayin/",
        "icon": "fa-tv",
    },
    {
        "title": "Kanal D Canlı",
        "category": "Canlı TV",
        "broadcaster": "Kanal D",
        "description": "Resmi canlı yayın ve yayın akışı.",
        "url": "https://www.kanald.com.tr/canli-yayin",
        "icon": "fa-tv",
    },
    {
        "title": "atv Canlı",
        "category": "Canlı TV",
        "broadcaster": "atv",
        "description": "Resmi canlı yayın ve programlar.",
        "url": "https://www.atv.com.tr/canli-yayin",
        "icon": "fa-tv",
    },
    {
        "title": "NOW Canlı",
        "category": "Canlı TV",
        "broadcaster": "NOW",
        "description": "Resmi canlı yayın ve yayın akışı.",
        "url": "https://www.nowtv.com.tr/canli-yayin",
        "icon": "fa-tv",
    },
    {
        "title": "TRT 1 Canlı",
        "category": "Canlı TV",
        "broadcaster": "TRT",
        "description": "Resmi TRT 1 canlı yayın sayfası.",
        "url": "https://www.trt1.com.tr/Canli-izle",
        "icon": "fa-tv",
    },
    {
        "title": "TV8 Canlı",
        "category": "Canlı TV",
        "broadcaster": "TV8",
        "description": "Resmi canlı yayın ve yayın akışı.",
        "url": "https://www.tv8.com.tr/canli-yayin",
        "icon": "fa-tv",
    },
    {
        "title": "Kanal D Diziler",
        "category": "Dizi",
        "broadcaster": "Kanal D",
        "description": "Yeni diziler ve resmi arşiv bölümleri.",
        "url": "https://www.kanald.com.tr/diziler",
        "icon": "fa-film",
    },
    {
        "title": "Show TV Diziler",
        "category": "Dizi",
        "broadcaster": "Show TV",
        "description": "Güncel diziler ve resmi bölüm sayfaları.",
        "url": "https://www.showtv.com.tr/diziler",
        "icon": "fa-film",
    },
    {
        "title": "Star TV Diziler",
        "category": "Dizi",
        "broadcaster": "Star TV",
        "description": "Güncel ve arşiv dizi sayfaları.",
        "url": "https://www.startv.com.tr/dizi",
        "icon": "fa-film",
    },
    {
        "title": "atv Diziler",
        "category": "Dizi",
        "broadcaster": "atv",
        "description": "Güncel ve arşiv diziler için resmi merkez.",
        "url": "https://www.atv.com.tr/diziler",
        "icon": "fa-film",
    },
    {
        "title": "TRT 1 Sinema",
        "category": "Film",
        "broadcaster": "TRT",
        "description": "Resmi sinema yayınları ve film programı.",
        "url": "https://www.trt1.com.tr/tv/sinema/1",
        "icon": "fa-clapperboard",
    },
    {
        "title": "atv Filmler",
        "category": "Film",
        "broadcaster": "atv",
        "description": "Resmi film yayınları ve yayın duyuruları.",
        "url": "https://www.atv.com.tr/filmler/ozelvideo",
        "icon": "fa-clapperboard",
    },
    {
        "title": "Kanal D Sinemalar",
        "category": "Film",
        "broadcaster": "Kanal D",
        "description": "Resmi film yayınları ve sinema sayfası.",
        "url": "https://www.kanald.com.tr/sinemalar/",
        "icon": "fa-clapperboard",
    },
)


def get_official_sources() -> list[OfficialSource]:
    """Return a template-safe copy of the curated source list."""
    return [dict(source) for source in OFFICIAL_SOURCES]
