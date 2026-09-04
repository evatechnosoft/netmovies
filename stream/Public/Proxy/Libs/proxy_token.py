"""Short-lived, host-scoped authorization for the local media proxy."""

import base64
import hashlib
import hmac
import json
import os
import secrets
import time
from urllib.parse import urlparse

# Token, manifest yazilirken bir kez basiliyor ve VOD manifesti tekrar indirilmiyor:
# omru FILM SURESINDEN uzun olmali. 15 dk iken uzun icerik ortasinda segmentler 403
# aliyor, oynatici siradaki kaynaga dusuyor ve "calisan kaynak bulunamadi" yaziyordu.
_TOKEN_TTL_SECONDS = int(os.getenv("PROXY_TOKEN_TTL", str(6 * 60 * 60)))
_RUNTIME_SECRET = secrets.token_urlsafe(32)


def _secret() -> bytes:
    configured = os.getenv("PROXY_TOKEN_SECRET", "") or os.getenv("AUTH_PASS", "")
    return (configured or _RUNTIME_SECRET).encode("utf-8")


def _encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def _decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def _host(value: str) -> str:
    return (urlparse(value).hostname or "").lower().rstrip(".")


def issue_proxy_token(urls: list[str]) -> str:
    hosts = sorted({_host(url) for url in urls if _host(url)})
    payload = {"exp": int(time.time()) + _TOKEN_TTL_SECONDS, "hosts": hosts}
    encoded = _encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signature = hmac.new(_secret(), encoded.encode("ascii"), hashlib.sha256).digest()
    return f"{encoded}.{_encode(signature)}"


def validate_proxy_token(token: str, target_url: str) -> bool:
    try:
        encoded, supplied_signature = token.split(".", 1)
        expected_signature = _encode(
            hmac.new(_secret(), encoded.encode("ascii"), hashlib.sha256).digest()
        )
        if not hmac.compare_digest(supplied_signature, expected_signature):
            return False
        payload = json.loads(_decode(encoded).decode("utf-8"))
        if int(payload.get("exp", 0)) < int(time.time()):
            return False
        target_host = _host(target_url)
        return bool(target_host and target_host in payload.get("hosts", []))
    except (ValueError, TypeError, KeyError, json.JSONDecodeError, UnicodeError):
        return False
