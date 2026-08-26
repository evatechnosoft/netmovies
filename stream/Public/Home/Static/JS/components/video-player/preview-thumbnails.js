// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import { detectFormat, createHlsConfig, suggestInitialMode, hasCustomHeaders, ProxyMode, buildProxyUrlWithMode } from '../../video-utils.min.js';
import {
    PREVIEW_SEEK_THROTTLE_MS,
    PREVIEW_SEEK_TIMEOUT_MS,
    PREVIEW_LOADING_DELAY_MS,
    PREVIEW_SEEK_EPSILON,
    PREVIEW_SHORT_BUCKET_SECONDS,
    PREVIEW_LONG_BUCKET_SECONDS,
    PREVIEW_LONG_BUCKET_THRESHOLD_SECONDS,
    PREVIEW_CANVAS_BASE_WIDTH,
    PREVIEW_DEFAULT_ASPECT_RATIO,
    PREVIEW_MIN_ASPECT_RATIO,
    PREVIEW_MAX_ASPECT_RATIO,
} from './constants.min.js';

// Seekbar preview thumbnail mantığı — VideoPlayer.prototype'a mixin olarak eklenir.
// `this` bağlamı orijinal class metotlarıyla birebir aynıdır.
export const previewThumbnailsMixin = {
    clearPreviewSeekTimer() {
        if (this.previewSeekTimer) {
            clearTimeout(this.previewSeekTimer);
            this.previewSeekTimer = null;
        }
    },

    clearPreviewSeekTimeout() {
        if (this.previewSeekTimeout) {
            clearTimeout(this.previewSeekTimeout);
            this.previewSeekTimeout = null;
        }
    },

    clearPreviewLoadingTimer() {
        if (this.previewLoadingTimer) {
            clearTimeout(this.previewLoadingTimer);
            this.previewLoadingTimer = null;
        }
    },

    ensurePreviewContext() {
        if (!this.previewContext && this.previewCanvas) {
            this.previewContext = this.previewCanvas.getContext('2d');
        }
        return this.previewContext;
    },

    getPreviewCanvasWrapper() {
        return this.previewCanvas?.closest('.preview-canvas-wrapper') || null;
    },

    getPreviewAspectRatio(videoEl = this.previewVideo) {
        const width = videoEl?.videoWidth;
        const height = videoEl?.videoHeight;
        if (Number.isFinite(width) && Number.isFinite(height) && width > 0 && height > 0) {
            const ratio = width / height;
            if (Number.isFinite(ratio) && ratio >= PREVIEW_MIN_ASPECT_RATIO && ratio <= PREVIEW_MAX_ASPECT_RATIO) {
                return ratio;
            }
        }
        return PREVIEW_DEFAULT_ASPECT_RATIO;
    },

    syncPreviewCanvasSize(videoEl = this.previewVideo) {
        if (!this.previewCanvas) return;

        const ratio = this.getPreviewAspectRatio(videoEl);
        const nextWidth = PREVIEW_CANVAS_BASE_WIDTH;
        const nextHeight = Math.max(1, Math.round(nextWidth / ratio));

        if (this.previewCanvas.width !== nextWidth || this.previewCanvas.height !== nextHeight) {
            this.previewCanvas.width = nextWidth;
            this.previewCanvas.height = nextHeight;
        }

        const previewCanvasWrapper = this.getPreviewCanvasWrapper();
        if (previewCanvasWrapper) {
            previewCanvasWrapper.style.setProperty('--preview-aspect-ratio', ratio.toFixed(4));
        }
    },

    clearPreviewLoading() {
        this.clearPreviewLoadingTimer();
        this.clearPreviewSeekTimeout();
        if (this.previewThumbnail) {
            this.previewThumbnail.classList.remove('loading');
        }
    },

    schedulePreviewLoading() {
        this.clearPreviewLoadingTimer();
        if (!this.previewThumbnail) return;

        this.previewLoadingTimer = setTimeout(() => {
            this.previewLoadingTimer = null;
            if (this.previewThumbnail && (this.previewIsSeeking || this.previewPendingTime != null)) {
                this.previewThumbnail.classList.add('loading');
            }
        }, PREVIEW_LOADING_DELAY_MS);
    },

    clampPreviewTime(time) {
        const duration = this.previewVideo?.duration;
        if (!Number.isFinite(duration) || duration <= 0) {
            return Math.max(0, time);
        }
        return Math.max(0, Math.min(time, Math.max(0, duration - 0.05)));
    },

    getPreviewBucketSeconds(duration) {
        if (Number.isFinite(duration) && duration >= PREVIEW_LONG_BUCKET_THRESHOLD_SECONDS) {
            return PREVIEW_LONG_BUCKET_SECONDS;
        }
        return PREVIEW_SHORT_BUCKET_SECONDS;
    },

    quantizePreviewTime(time) {
        const duration = this.videoPlayer?.duration;
        const bucket = this.getPreviewBucketSeconds(duration);
        const maxTime = Number.isFinite(duration) && duration > 0 ? Math.max(0, duration - 0.05) : time;
        return Math.max(0, Math.min(Math.floor(time / bucket) * bucket, maxTime));
    },

    schedulePendingPreviewSeek() {
        this.clearPreviewSeekTimer();
        if (this.previewPendingTime == null || this.previewIsSeeking) return;

        const elapsed = Date.now() - this._lastPreviewSeek;
        const delay = Math.max(0, PREVIEW_SEEK_THROTTLE_MS - elapsed);

        if (delay === 0) {
            this.executePreviewSeek();
            return;
        }

        this.previewSeekTimer = setTimeout(() => {
            this.previewSeekTimer = null;
            this.executePreviewSeek();
        }, delay);
    },

    finishPreviewSeek() {
        this.clearPreviewLoading();
        this.previewIsSeeking = false;
        if (this.previewPendingTime != null) {
            this.schedulePendingPreviewSeek();
        }
    },

    retryPreviewWithFullProxy() {
        if (this.previewRetriedWithProxy || !this.previewSource) {
            this.finishPreviewSeek();
            return;
        }

        this.previewRetriedWithProxy = true;
        this.previewIsSeeking = false;
        this.clearPreviewSeekTimer();
        this.clearPreviewSeekTimeout();
        this.setupPreviewVideo({ ...this.previewSource }, ProxyMode.FULL);

        if (this.previewRequestedTime != null) {
            this.previewPendingTime = this.previewRequestedTime;
            this.schedulePreviewLoading();
            this.schedulePendingPreviewSeek();
        }
    },

    drawPreviewFrame() {
        const previewContext = this.ensurePreviewContext();
        if (!previewContext || !this.previewCanvas || !this.previewVideo) {
            this.finishPreviewSeek();
            return;
        }

        try {
            this.syncPreviewCanvasSize(this.previewVideo);
            previewContext.drawImage(
                this.previewVideo,
                0,
                0,
                this.previewCanvas.width,
                this.previewCanvas.height
            );
            this.finishPreviewSeek();
        } catch (e) {
            this.retryPreviewWithFullProxy();
        }
    },

    renderPreviewFrame() {
        if (!this.previewVideo) {
            this.finishPreviewSeek();
            return;
        }

        let settled = false;
        const settle = () => {
            if (settled || !this.previewIsSeeking) return;
            settled = true;
            this.drawPreviewFrame();
        };

        requestAnimationFrame(settle);
        setTimeout(settle, 80);

        if (typeof this.previewVideo.requestVideoFrameCallback === 'function') {
            this.previewVideo.requestVideoFrameCallback(() => settle());
        }
    },

    warmPreviewVideo() {
        if (!this.previewVideo || this.previewWarmupPromise) return;

        if (this.previewVideo.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
            if (this.previewPendingTime != null && !this.previewIsSeeking) {
                this.schedulePendingPreviewSeek();
            }
            return;
        }

        const playPromise = this.previewVideo.play();
        if (playPromise === undefined || typeof playPromise.then !== 'function') {
            this.schedulePendingPreviewSeek();
            return;
        }

        this.previewWarmupPromise = playPromise.then(() => {
            this.previewVideo.pause();
        }).catch(() => {
            // Hidden muted previews can still be blocked on some WebViews.
        }).finally(() => {
            this.previewWarmupPromise = null;
            if (this.previewPendingTime != null && !this.previewIsSeeking) {
                this.schedulePendingPreviewSeek();
            }
        });
    },

    executePreviewSeek() {
        if (!this.previewVideo || this.previewPendingTime == null) return;

        if (this.previewVideo.readyState < HTMLMediaElement.HAVE_METADATA) {
            this.warmPreviewVideo();
            return;
        }

        if (this.previewVideo.seeking) return;

        const nextTime = this.clampPreviewTime(this.previewPendingTime);
        if (Number.isFinite(this.previewVideo.currentTime) &&
            Math.abs(this.previewVideo.currentTime - nextTime) < PREVIEW_SEEK_EPSILON) {
            this.previewPendingTime = null;
            this.renderPreviewFrame();
            return;
        }

        this.previewPendingTime = null;
        this.previewIsSeeking = true;
        this._lastPreviewSeek = Date.now();
        this.schedulePreviewLoading();

        this.clearPreviewSeekTimeout();
        this.previewSeekTimeout = setTimeout(() => {
            if (this.previewIsSeeking) {
                this.retryPreviewWithFullProxy();
            }
        }, PREVIEW_SEEK_TIMEOUT_MS);

        try {
            this.previewVideo.currentTime = nextTime;
        } catch (e) {
            this.retryPreviewWithFullProxy();
        }
    },

    setupPreview() {
        const progressContainer = document.getElementById('custom-progress-container');
        if (!progressContainer || !this.previewThumbnail) return;

        this.syncPreviewCanvasSize();

        progressContainer.addEventListener('mousemove', (e) => this.handlePreviewMove(e));
        progressContainer.addEventListener('mouseleave', () => this.hideElement(this.previewThumbnail));

        // Mobil Touch Desteği
        progressContainer.addEventListener('touchstart', (e) => {
            this.handlePreviewMove(e.touches[0]);
        }, { passive: true });

        progressContainer.addEventListener('touchmove', (e) => {
            this.handlePreviewMove(e.touches[0]);
        }, { passive: true });

        progressContainer.addEventListener('touchend', () => this.hideElement(this.previewThumbnail));
        progressContainer.addEventListener('touchcancel', () => this.hideElement(this.previewThumbnail));
    },

    handlePreviewMove(e) {
        if (!this.videoPlayer || !Number.isFinite(this.videoPlayer.duration) || this.videoPlayer.duration <= 0) return;

        const progressContainer = document.getElementById('custom-progress-container');
        const rect = progressContainer.getBoundingClientRect();

        // Touch veya Mouse koordinatını hesapla
        const clientX = e.clientX || e.pageX;
        let pos = (clientX - rect.left) / progressContainer.offsetWidth;
        pos = Math.max(0, Math.min(1, pos));
        const previewTime = pos * this.videoPlayer.duration;

        // Pozisyonu ayarla (Thumbnail'ı merkeze al ve taşırmama yap)
        const thumbWidth = this.previewThumbnail.offsetWidth || 160;
        let left = clientX - rect.left;
        left = Math.max(thumbWidth / 2, Math.min(rect.width - thumbWidth / 2, left));
        this.previewThumbnail.style.left = `${left}px`;

        // Süreyi güncelle
        if (this.previewTimeEl) {
            this.previewTimeEl.textContent = this.formatDuration(previewTime);
        }

        this.showElement(this.previewThumbnail);

        // Preview videosunu seek et
        this.seekPreview(previewTime);
    },

    seekPreview(time) {
        if (!this.previewVideo) return;

        const quantizedTime = this.quantizePreviewTime(time);
        if (this.previewRequestedTime != null && Math.abs(this.previewRequestedTime - quantizedTime) < PREVIEW_SEEK_EPSILON) {
            return;
        }

        this.previewRequestedTime = quantizedTime;
        this.previewPendingTime = quantizedTime;
        this.schedulePreviewLoading();
        this.schedulePendingPreviewSeek();
    },

    setupPreviewVideo(videoData, forcedMode = null) {
        const originalUrl = videoData.url;
        const referer = videoData.referer || '';
        const userAgent = videoData.userAgent || '';
        const isSameSource =
            this.previewSource?.url === originalUrl &&
            this.previewSource?.userAgent === userAgent &&
            this.previewSource?.referer === referer &&
            (this.previewSource?.format || '') === (videoData.format || '');

        this.clearPreviewSeekTimer();
        this.clearPreviewSeekTimeout();
        this.previewWarmupPromise = null;
        this.previewIsSeeking = false;
        this.previewHlsContext = null;
        if (!isSameSource) {
            this.previewRequestedTime = null;
            this.previewPendingTime = null;
        }
        this.previewSource = { ...videoData };
        this.previewRetriedWithProxy = isSameSource ? (this.previewRetriedWithProxy || forcedMode === ProxyMode.FULL) : forcedMode === ProxyMode.FULL;
        this.clearPreviewLoading();

        // Temizlik
        if (this.previewHls) {
            this.previewHls.destroy();
            this.previewHls = null;
        }
        if (this.previewVideo) {
            this.previewVideo.pause();
            this.previewVideo.removeAttribute('src');
            this.previewVideo.load();
        } else {
            this.previewVideo = document.createElement('video');
            this.previewVideo.muted = true;
            this.previewVideo.playsInline = true;
            this.previewVideo.setAttribute('playsinline', '');
            this.previewVideo.setAttribute('webkit-playsinline', '');
            this.previewVideo.preload = 'auto'; // Mobilde metadata'yı zorla
            // Mobilde display: none olan videoların decode'u durdurulur, bu yüzden görünmez yapıp DOM'a ekliyoruz
            this.previewVideo.style.position = 'absolute';
            this.previewVideo.style.width = '1px';
            this.previewVideo.style.height = '1px';
            this.previewVideo.style.opacity = '0.01';
            this.previewVideo.style.pointerEvents = 'none';
            this.previewVideo.style.zIndex = '-1';
            // crossOrigin burada set edilmiyor — HLS.js path'de set edilir, native path'de kaldırılır
            // (iOS Safari/WebView: crossOrigin='anonymous' CORS header olmayan CDN'lerde video'yu bloke eder)
            document.body.appendChild(this.previewVideo);

            this.previewVideo.addEventListener('loadedmetadata', () => {
                this.syncPreviewCanvasSize(this.previewVideo);
                if (this.previewRequestedTime != null) {
                    this.previewPendingTime = this.previewRequestedTime;
                    this.schedulePendingPreviewSeek();
                    return;
                }
                this.warmPreviewVideo();
            });

            this.previewVideo.addEventListener('loadeddata', () => {
                if (this.previewPendingTime != null && !this.previewIsSeeking) {
                    this.schedulePendingPreviewSeek();
                }
            });

            this.previewVideo.addEventListener('seeked', () => {
                this.renderPreviewFrame();
            });

            this.previewVideo.addEventListener('error', () => {
                this.retryPreviewWithFullProxy();
            });

            this.previewVideo.addEventListener('stalled', () => {
                if (this.previewIsSeeking || this.previewPendingTime != null) {
                    this.retryPreviewWithFullProxy();
                }
            });
        }

        const previewMode = forcedMode || this.currentProxyMode || suggestInitialMode(originalUrl, hasCustomHeaders(referer, videoData.extraHeaders));
        this.previewHlsContext = {
            currentProxyMode: previewMode,
            proxyUrl: this.proxyUrl,
            proxyFallbackUrl: this.proxyFallbackUrl,
            proxyBase: this.proxyBase,
            lastLoadedBaseUrl: this.lastLoadedBaseUrl,
            lastLoadedOrigin: this.lastLoadedOrigin
        };
        const previewUrl = buildProxyUrlWithMode(
            originalUrl,
            userAgent,
            referer,
            previewMode,
            this.previewHlsContext,
            videoData.extraHeaders
        );

        // Format tespiti
        const format = detectFormat(originalUrl, videoData.format || '');

        if (format === 'hls' && typeof Hls !== 'undefined' && Hls.isSupported()) {
            // HLS.js kendi XHR pipeline'ını kullandığı için crossOrigin video element'te güvenli
            this.previewVideo.crossOrigin = 'anonymous';
            const hlsConfig = createHlsConfig(userAgent, referer, this.previewHlsContext, previewMode, videoData.extraHeaders);
            // Preview için buffer'ı minimize et ve hızı maksimize et
            hlsConfig.maxBufferLength = 1;
            hlsConfig.maxMaxBufferLength = 2;
            hlsConfig.capLevelToPlayerSize = true; // 1px video, bu yüzden en düşük kaliteyi çekecek
            hlsConfig.startLevel = 0; // Doğrudan en düşük kalite seviyesinden başla

            const hls = new Hls(hlsConfig);
            this.previewHls = hls;
            hls.on(Hls.Events.ERROR, (_, data) => {
                if (data?.fatal) {
                    this.retryPreviewWithFullProxy();
                }
            });

            hls.loadSource(previewMode === ProxyMode.NONE ? originalUrl : previewUrl);
            hls.attachMedia(this.previewVideo);
        } else {
            // Native path (iOS Safari/WebKit): crossOrigin CORS olmayan CDN'lerde yüklemeyi bloke eder — kaldır
            this.previewVideo.removeAttribute('crossorigin');
            this.previewVideo.src = previewUrl;
            this.previewVideo.load();
        }

        this.warmPreviewVideo();
    },
};
