# NetMovies — İstemciye açık yapılandırma (TV/mobil).
#
# /api/admin/* ADMIN_PASS ile korunuyor; native istemci o parolayı taşımadığı için
# yönetim ayarlarını okuyamıyordu ve sessizce kendi yerleşik listesine düşüyordu
# (web'de yapılan değişiklik TV'ye ulaşmıyordu). Burada YALNIZ istemcinin
# görüntülemek için ihtiyaç duyduğu alanlar auth'suz sunulur — yazma yok, gizli
# alan (vault_pin, provider_url, custom_repos) yok.

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Libs import admin_config


@api_v1_router.get("/client_config")
async def client_config(request: Request):
    cfg = admin_config.load_config()
    return {
        **api_v1_global_message,
        "result": {
            "adult_providers"  : cfg.get("adult_providers") or [],
            "hidden_providers" : cfg.get("hidden_providers") or [],
            "hidden_categories": cfg.get("hidden_categories") or [],
            "vault_alias"      : cfg.get("vault_alias") or "Özel Koleksiyon",
            "min_rating"       : cfg.get("min_rating") or 0.0,
        },
    }
