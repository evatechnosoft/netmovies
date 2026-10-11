// Tizen yerel sayfada satır içi betik/stili engeller (varsayılan CSP); kod burada.
// Sunucu iframe'e izin vermiyor (X-Frame-Options), uygulama doğrudan /tv'ye geçer.
try {
  tizen.tvinputdevice.registerKeyBatch(["MediaPlay", "MediaPause", "MediaPlayPause",
    "MediaFastForward", "MediaRewind", "MediaStop", "ChannelUp", "ChannelDown",
    "ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue"]);
} catch (e) { /* tuşsuz da açılsın */ }
// Monitör hep ev ağında: yoklama yok, doğrudan sunucu. Kızın ekranı → çocuk modu
// (yalnız panelde izin verilen kanallar/çizgi filmler, bkz. Libs/cocuk.py).
window.addEventListener("load", function () {
  setTimeout(function () { location.replace("http://192.168.1.186:3310/tv?profil=cocuk&v=" + Date.now()); }, 0);
});
