"""Shared helpers for the Turkish series providers."""

from __future__ import annotations

import base64
import html
import re
from urllib.parse import urljoin

from KekikStream.Core import ExtractResult, HTMLHelper


def absolute(base_url: str, value: str | None) -> str | None:
    if not value:
        return None
    return urljoin(base_url.rstrip("/") + "/", html.unescape(value.strip()))


def first_text(node: HTMLHelper, selectors: tuple[str, ...]) -> str | None:
    for selector in selectors:
        value = node.select_text(selector)
        if value:
            return value.strip()
    return None


def first_attr(node: HTMLHelper, selectors: tuple[str, ...], attr: str) -> str | None:
    for selector in selectors:
        value = node.select_attr(selector, attr)
        if value:
            return value.strip()
    return None


def season_episode(value: str) -> tuple[int, int | None]:
    season_match = re.search(r"(\d+)\s*\.\s*Sezon", value, re.IGNORECASE)
    episode_match = re.search(r"(\d+)\s*\.\s*Bölüm", value, re.IGNORECASE)
    season = int(season_match.group(1)) if season_match else 1
    episode = int(episode_match.group(1)) if episode_match else None
    return season, episode


def extract_embedded_sources(
    html_text: str,
    page_url: str,
    provider_name: str,
) -> list[ExtractResult]:
    """Extract common direct sources from a provider/iframe response.

    Providers commonly expose the final source as a jwplayer ``file`` value,
    a ``source/src`` attribute, or a base64 encoded ``file_link``. If only an
    iframe remains, it is intentionally skipped: returning an iframe as a
    video URL produces a misleading green source in the player.
    """
    source_values: list[str] = []
    patterns = (
        r"(?:file|src|source)\s*[:=]\s*['\"]([^'\"]+\.(?:m3u8|mp4)(?:\?[^'\"]*)?)",
        r"['\"](https?://[^'\"]+\.(?:m3u8|mp4)(?:\?[^'\"]*)?)['\"]",
        r"(?:file_link|video_url)\s*=\s*['\"]([^'\"]+)['\"]",
    )
    for pattern in patterns:
        for match in re.finditer(pattern, html_text, re.IGNORECASE):
            value = html.unescape(match.group(1)).replace("\\/", "/")
            if value not in source_values:
                source_values.append(value)

    results: list[ExtractResult] = []
    for value in source_values:
        if value.startswith(("aHR0c", "Ly8")):
            try:
                value = base64.b64decode(value + "===").decode("utf-8")
            except Exception:
                continue
        source_url = absolute(page_url, value)
        if not source_url or not re.match(r"https?://", source_url):
            continue
        results.append(
            ExtractResult(
                name=f"{provider_name} | Kaynak",
                url=source_url,
                referer=page_url,
            )
        )
    return results
