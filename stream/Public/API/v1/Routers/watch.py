# NetMovies — İzleme geçmişi / favoriler / kaynak istatistikleri client-facing API.
#
# Diğer router'larla aynı desen: Core.Request + request.state.veri (middleware hem
# query params hem JSON body hem form'u state.veri'ye koyar → GET query ve POST body
# ikisi de otomatik desteklenir), dönüş {**api_v1_global_message, "result": ...}.

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Libs import watch_store


def _key_from(veri: dict) -> str:
    """İstek verisinden content_key üretir/alır.

    content_key doğrudan geldiyse onu kullan; gelmediyse title/media_type/year'dan
    site-agnostik türet. Böylece istemci ister hazır key ister ham başlık gönderebilir.
    """
    ck = str(veri.get("content_key") or "").strip()
    if ck:
        return ck
    return watch_store.normalize_key(
        veri.get("title") or "",
        veri.get("media_type") or "",
        veri.get("year"),
    )


# --------------------------------------------------------------- continue watching
@api_v1_router.get("/continue_watching")
async def continue_watching(request: Request):
    """Devam edilecekler listesi (en son izlenen, tamamlanmamış)."""
    veri  = request.state.veri or {}
    try:
        limit = int(veri.get("limit") or 20)
    except (TypeError, ValueError):
        limit = 20
    result = watch_store.list_continue_watching(limit=limit)
    return {**api_v1_global_message, "result": result}


# ---------------------------------------------------------------------- progress
@api_v1_router.post("/progress")
async def save_progress(request: Request):
    """İzleme ilerlemesini kaydeder. Beklenen alanlar (query veya body):
    title, media_type, [year], [content_key], plugin, poster, episode,
    position_seconds, duration_seconds.
    """
    veri = request.state.veri or {}
    ck   = _key_from(veri)
    if not ck:
        return {**api_v1_global_message, "result": {"ok": False, "error": "title veya content_key gerekli"}}

    def _num(v) -> float:
        try:
            return float(v)
        except (TypeError, ValueError):
            return 0.0

    watch_store.upsert_progress(
        ck,
        plugin           = str(veri.get("plugin") or ""),
        title            = str(veri.get("title") or ""),
        poster           = str(veri.get("poster") or ""),
        media_type       = str(veri.get("media_type") or ""),
        episode          = str(veri.get("episode") or ""),
        position_seconds = _num(veri.get("position_seconds")),
        duration_seconds = _num(veri.get("duration_seconds")),
    )
    return {**api_v1_global_message, "result": {"ok": True, "content_key": ck}}


@api_v1_router.get("/progress")
async def read_progress(request: Request):
    """Tek içeriğin ilerleme kaydını döndürür (content_key veya title ile)."""
    veri = request.state.veri or {}
    ck   = _key_from(veri)
    result = watch_store.get_progress(ck) if ck else None
    return {**api_v1_global_message, "result": result}


# ---------------------------------------------------------------------- favorites
@api_v1_router.get("/favorites")
async def get_favorites(request: Request):
    """Tüm favorileri listeler."""
    result = watch_store.list_favorites()
    return {**api_v1_global_message, "result": result}


@api_v1_router.post("/favorites")
async def post_favorite(request: Request):
    """Favori ekler (content_key veya title'dan türetilir)."""
    veri = request.state.veri or {}
    ck   = _key_from(veri)
    if not ck:
        return {**api_v1_global_message, "result": {"ok": False, "error": "title veya content_key gerekli"}}
    watch_store.add_favorite(
        ck,
        plugin     = str(veri.get("plugin") or ""),
        title      = str(veri.get("title") or ""),
        poster     = str(veri.get("poster") or ""),
        media_type = str(veri.get("media_type") or ""),
    )
    return {**api_v1_global_message, "result": {"ok": True, "content_key": ck, "is_favorite": True}}


@api_v1_router.post("/favorites/toggle")
async def toggle_favorite(request: Request):
    """Favori durumunu değiştirir. Dönüş: yeni is_favorite durumu."""
    veri = request.state.veri or {}
    ck   = _key_from(veri)
    if not ck:
        return {**api_v1_global_message, "result": {"ok": False, "error": "title veya content_key gerekli"}}
    new_state = watch_store.toggle_favorite(
        ck,
        plugin     = str(veri.get("plugin") or ""),
        title      = str(veri.get("title") or ""),
        poster     = str(veri.get("poster") or ""),
        media_type = str(veri.get("media_type") or ""),
    )
    return {**api_v1_global_message, "result": {"ok": True, "content_key": ck, "is_favorite": new_state}}


# ------------------------------------------------------------------- source stats
@api_v1_router.get("/source_stats")
async def source_stats(request: Request):
    """Kaynak (plugin) istatistikleri — izlenme sayısına göre azalan."""
    result = watch_store.get_source_stats()
    return {**api_v1_global_message, "result": result}
