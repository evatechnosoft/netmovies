# NetMovies — oynatma denemesinin sonucu (istemciden gelir).
#
# Kaynak sırası artık kanıta göre kuruluyor; kanıtı yalnız oynatıcı biliyor:
# sunucu "link buldum" diyebilir ama gerçekten açılıp açılmadığı televizyonda
# belli olur (bkz. hafıza: "kaynak bulundu ≠ oynatılır"). Televizyon ilk kare
# geldiğinde başarı, kaynak düştüğünde hata bildirir.

from CLI  import konsol
from Core import Request
from .    import api_v1_router, api_v1_global_message
from ..Libs import source_score


@api_v1_router.post("/source_event")
async def source_event(request: Request):
    veri   = request.state.veri or {}
    plugin = (veri.get("plugin") or "").strip()
    if not plugin:
        return {**api_v1_global_message, "result": {"ok": False, "error": "plugin bekleniyor"}}

    basarili = bool(veri.get("ok"))
    puan     = source_score.kaydet(plugin, basarili)
    konsol.log(f"[cyan]◆ kaynak:[/] {plugin} · {'oynadı' if basarili else 'açılmadı'} · puan {puan:.0f}")
    return {**api_v1_global_message, "result": {"ok": True, "puan": round(puan, 1)}}


@api_v1_router.get("/source_score")
async def source_score_get(request: Request):
    """Teşhis: hangi sağlayıcı hangi puanda."""
    return {**api_v1_global_message, "result": {"kaynaklar": source_score.ozet()}}
