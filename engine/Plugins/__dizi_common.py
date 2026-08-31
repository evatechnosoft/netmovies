"""Shared helpers for the Turkish series providers."""

from __future__ import annotations

import base64
import html
import os
import re
from urllib.parse import urljoin

import httpx
from KekikStream.Core import ExtractResult, HTMLHelper

_WARP_PROXY = os.getenv("WARP_PROXY", "http://warp:8080")
_DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
_warp_client: httpx.AsyncClient | None = None


def get_warp_client() -> httpx.AsyncClient | None:
    global _warp_client
    if _warp_client is None:
        try:
            _warp_client = httpx.AsyncClient(
                proxy=_WARP_PROXY,
                headers={"User-Agent": _DEFAULT_UA},
                timeout=12.0,
                follow_redirects=True,
            )
        except Exception:
            _warp_client = None
    return _warp_client


async def fetch_html(
    client: httpx.AsyncClient,
    url: str,
    headers: dict | None = None,
    cookies: dict | None = None,
) -> str:
    """Fetch HTML with automatic WARP proxy fallback on SNI/SSL/Connection block."""
    h = dict(headers or {})
    if "User-Agent" not in h and "user-agent" not in h:
        h["User-Agent"] = _DEFAULT_UA

    try:
        resp = await client.get(url, headers=h, cookies=cookies, timeout=7.0)
        if resp.status_code == 200 and len(resp.text) > 300:
            return resp.text
    except Exception:
        pass

    warp = get_warp_client()
    if warp:
        try:
            resp = await warp.get(url, headers=h, cookies=cookies, timeout=12.0)
            if resp.status_code == 200 or len(resp.text) > 300:
                return resp.text
        except Exception:
            pass

    # Fallback to direct client
    resp = await client.get(url, headers=h, cookies=cookies)
    return resp.text


def normalize_url(url: str, base_url: str) -> str:
    """Replace obsolete or alternate domains in url with the active base_url."""
    if not url:
        return ""
    if url.startswith("/"):
        return base_url.rstrip("/") + url
    return re.sub(r"^https?://[^/]+", base_url.rstrip("/"), url)


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
    """Extract common direct sources from a provider/iframe response."""
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
