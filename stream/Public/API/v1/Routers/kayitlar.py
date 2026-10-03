# NetMovies — Kayıtlar listesi (kuyruk + hazır kayıtlar). Motor: Libs/kayit.py.
#
# TV istemcisi gövdesiz POST + query param kullanır (bkz. _istek.py); dosyalar
# /proxy/kayit/<id>/... altından servis edilir.

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import kayit


@api_v1_router.get("/kayitlar")
async def kayit_listesi(request: Request):
    return {**api_v1_global_message, "result": kayit.liste(), "izleniyor": kayit.izleniyor(), "ayarlar": kayit.ayarlar()}


@api_v1_router.post("/kayitlar/ekle")
async def kayit_ekle(request: Request):
    try:
        meta = kayit.ekle(dict(request.state.veri or {}))
    except ValueError as hata:
        return {**api_v1_global_message, "result": {"ok": False, "error": str(hata)}}
    return {**api_v1_global_message, "result": {"ok": True, "id": meta["id"], "durum": meta["durum"]}}


@api_v1_router.post("/kayitlar/otomatik")
async def kayit_otomatik(request: Request):
    """Ayar açılınca beklemeden bir tur at (normalde 3 saatte bir)."""
    return {**api_v1_global_message, "result": {"ok": True, "eklenen": await kayit._otomatik_tur()}}


@api_v1_router.post("/kayitlar/sil")
async def kayit_sil(request: Request):
    kid = str((request.state.veri or {}).get("id") or "")
    return {**api_v1_global_message, "result": {"ok": kayit.sil(kid)}}
