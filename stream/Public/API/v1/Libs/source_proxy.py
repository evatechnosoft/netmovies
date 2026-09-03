# NetMovies — imza/ek başlık isteyen kaynakları sunucu proxy'sine bağlar.
#
# Hem yeni tek uç (resolve_sources) hem de eski `load_links` bunu kullanır:
# böylece güncellenmemiş istemciler de imzalı kaynakları oynatabilir.

from json         import dumps
from urllib.parse import quote

from Public.Proxy.Libs.proxy_token import issue_proxy_token


def route_through_proxy(sources: list, base_url: str) -> list:
    """Ek başlık isteyen kaynakları sunucu proxy'sine bağlar.

    Bazı oynatıcılar tek kullanımlık imza başlığı (X-Sp) istiyor; imza her istekte
    yeniden üretilmek zorunda olduğu için istemci doğrudan çalamaz. Ayrıca TV'nin
    ağı kaynak CDN'ine erişemeyebiliyor (ERR_CONNECTION_REFUSED). Proxy'den geçen
    akışta ikisi de sunucunun sorunu olur — istemci yalnız URL'i çalar.

    Ek başlık istemeyen kaynaklara dokunulmaz: gereksiz yere ev bağlantısı
    üzerinden trafik taşınmasın.
    """
    proxied = []
    for source in sources:
        if not isinstance(source, dict):
            continue

        extra = source.get("extra_headers") or {}
        url   = str(source.get("url") or "")
        if not extra or not url:
            proxied.append(source)
            continue

        token  = issue_proxy_token([url])
        params = [
            f"url={quote(url, safe='')}",
            # Segmentler normalde bant tasarrufu için doğrudan CDN'den çekilir; bu
            # kaynağın CDN'i Referer istiyor (Referer'sız 403) ve istemci onu
            # gönderemez. force_proxy ile segmentler de sunucudan geçer.
            "force_proxy=1",
            f"referer={quote(str(source.get('referer') or ''), safe='')}",
            f"user_agent={quote(str(source.get('user_agent') or ''), safe='')}",
            f"extra_headers={quote(dumps(extra, separators=(',', ':')), safe='')}",
            f"proxy_token={quote(token, safe='')}",
        ]
        source = {**source, "url": f"{base_url}/proxy/video?{'&'.join(params)}", "proxied": True}
        # İmza malzemesi istemciye gitmesin: artık sunucunun işi.
        source.pop("extra_headers", None)
        proxied.append(source)

    return proxied
