# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

# * Docker İmajı
FROM python:3.14.3-slim-trixie

# * Etkileşimsiz apt/locale kurulumu
ENV DEBIAN_FRONTEND=noninteractive

# * Sadece build aşamasında güvenli bir locale kullan (C.UTF-8 hazır gelir)
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

# * Çalışma Alanı
WORKDIR /usr/src/Stream
COPY ./ /usr/src/Stream

# * Locales kurulumu ve TR locale üretimi
RUN apt-get update -y && \
    apt-get install --no-install-recommends -y \
        # git \
        # ffmpeg \
        # opus-tools \
        locales \
        curl \
        tzdata && \
    sed -i 's/^# *tr_TR.UTF-8 UTF-8/tr_TR.UTF-8 UTF-8/' /etc/locale.gen && \
    locale-gen tr_TR.UTF-8 && \
    update-locale LANG=tr_TR.UTF-8 LC_ALL=tr_TR.UTF-8 LANGUAGE=tr_TR:tr && \
    ln -fs /usr/share/zoneinfo/Europe/Istanbul /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# * Standart ortam değişkenleri
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING="UTF-8" \
    LANG="tr_TR.UTF-8" \
    LC_ALL="tr_TR.UTF-8" \
    LANGUAGE="tr_TR:tr" \
    TZ="Europe/Istanbul"

# * uv kurulumu
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# * Gerekli Paketlerin Yüklenmesi
RUN uv pip install --system --no-cache .

# Runtime bytecode writes are disabled. Preload the real import graph during the
# image build so Uvicorn workers do not recompile it on every container start.
RUN env -u PYTHONDONTWRITEBYTECODE python3 -c "import Core"

# * hls.js'i self-host için indir (engelli CDN'lere karşı). İndirilemezse
#   çalışma anında jsDelivr/cdnjs fallback devreye girer, build kırılmaz.
RUN mkdir -p Public/Home/Static/JS/vendor && \
    (curl -fsSL --max-time 30 https://cdn.jsdelivr.net/npm/hls.js@1.4.12/dist/hls.min.js \
        -o Public/Home/Static/JS/vendor/hls.min.js || \
     curl -fsSL --max-time 30 https://cdnjs.cloudflare.com/ajax/libs/hls.js/1.4.12/hls.min.js \
        -o Public/Home/Static/JS/vendor/hls.min.js || \
     echo "[uyarı] hls.js indirilemedi; çalışma anında CDN fallback kullanılacak")

# * Sağlık Kontrolü
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://127.0.0.1:3310/api/v1/health || exit 1

# * Uygulamanın Başlatılması
CMD ["python3", "basla.py"]
