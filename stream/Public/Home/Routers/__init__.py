# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from fastapi            import APIRouter
from fastapi.templating import Jinja2Templates

from ...API.v1.Libs         import fuck_dmca, get_client_headers
from ..Libs.provider_client import get_provider_client
from ..Libs.helpers         import build_context, detect_lang, detect_provider

home_router   = APIRouter(prefix="")
home_template = Jinja2Templates(directory="Public/Home/Templates")

from urllib.parse import quote as _quote

def _poster_proxy(url: str | None, title: str | None = None) -> str:
    """Poster URL'i üretir — zincirin tamamı sunucuda: kaynak → proxy cache →
    TMDB (başlıkla) → placeholder.

    `title` verilirse kaynak poster boş/kırık olduğunda proxy TMDB'ye yönlendirir;
    şablonların ayrı ayrı `onerror` zinciri taşımasına gerek kalmaz. Yerel/data
    URL'leri olduğu gibi bırakılır."""
    if url and url.startswith(("/", "data:")):
        return url
    if not url and not title:
        return ""

    query = f"url={_quote(url or '', safe='')}"
    if title:
        query += f"&title={_quote(title, safe='')}"
    return f"/proxy/image?{query}"

home_template.env.globals["poster"] = _poster_proxy

from . import (
    ana_sayfa,
    seo,
    eklenti,
    kategori,
    icerik,
    ara,
    izle,
    admin,
    tmdb
)
