// NetMovies — Harici oynatıcı yönlendirmesi (Nova / MX / VLC / kopyala)
// Tarayıcı içi hls.js yerine cihazın native oynatıcısına gönderir; 4K/yüksek
// bitrate'te çok daha akıcıdır (Mibox, Android TV, telefon). Header gerektiren
// kaynaklar proxy URL'i üzerinden gider, böylece Nova/MX'te ekstra ayar gerekmez.

const PACKAGES = {
    nova: "org.courville.nova",
    mx:   "com.mxtech.videoplayer.ad",   // MX Free; Pro: com.mxtech.videoplayer.pro
    vlc:  "org.videolan.vlc",
};

let current = null; // {url, referer, userAgent, extraHeaders, title}

// Aktif kaynağı VideoPlayer'dan dinle
window.addEventListener("netmovies:playback", (e) => {
    current = e.detail || null;
    const bar = document.getElementById("external-player-bar");
    if (bar && current && current.url) bar.classList.remove("is-hidden");
});

// Kaynağı proxy URL'ine çevir (header'lar proxy'de enjekte edilir → native oynatıcı temiz URL alır)
function proxiedUrl(src) {
    const p = new URLSearchParams();
    p.append("url", src.url);
    if (src.userAgent) p.append("user_agent", src.userAgent);
    if (src.referer)   p.append("referer", src.referer);
    if (src.extraHeaders && Object.keys(src.extraHeaders).length) {
        p.append("extra_headers", JSON.stringify(src.extraHeaders));
    }
    // Header yoksa doğrudan kaynağı vermek daha hızlı; header varsa proxy şart.
    const needsProxy = src.referer || src.userAgent || (src.extraHeaders && Object.keys(src.extraHeaders).length);
    return needsProxy ? `${window.location.origin}/proxy/video?${p.toString()}` : src.url;
}

function buildIntent(pkg, url, title) {
    const t = encodeURIComponent(title || "NetMovies");
    return `intent:${url}#Intent;package=${pkg};type=video/*;S.title=${t};end`;
}

function status(msg) {
    const el = document.getElementById("external-player-status");
    if (el) el.textContent = msg;
}

async function copyToClipboard(text) {
    try {
        await navigator.clipboard.writeText(text);
        return true;
    } catch {
        return false;
    }
}

document.addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-player]");
    if (!btn) return;
    if (!current || !current.url) {
        status("Önce bir kaynak seçip oynatın.");
        return;
    }

    const url  = proxiedUrl(current);
    const kind = btn.dataset.player;

    if (kind === "copy") {
        const ok = await copyToClipboard(url);
        status(ok ? "Link kopyalandı — oynatıcına yapıştırabilirsin." : "Kopyalanamadı: " + url);
        return;
    }

    const pkg = PACKAGES[kind];
    if (!pkg) return;
    status(`${btn.textContent.trim()} açılıyor…`);
    // Android intent — kurulu değilse chooser/hata; kopyala her zaman yedek.
    window.location.href = buildIntent(pkg, url, current.title);
});
