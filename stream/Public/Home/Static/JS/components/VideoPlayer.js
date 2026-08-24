// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import { buildProxyUrl as buildServiceProxyUrl } from '../service-detector.min.js';
import BuddyLogger from '../utils/BuddyLogger.min.js';

import { coreUiMixin } from './video-player/core-ui.min.js';
import { controlsMixin } from './video-player/controls.min.js';
import { previewThumbnailsMixin } from './video-player/preview-thumbnails.min.js';
import { sourcesMixin } from './video-player/sources.min.js';
import { hlsSetupMixin } from './video-player/hls-setup.min.js';
import { subtitlesMixin } from './video-player/subtitles.min.js';

export default class VideoPlayer {
    constructor() {
        // BuddyLogger'ı başlat ve ata
        this.logger = new BuddyLogger(true);

        // --- Console Welcome Message ---
        BuddyLogger.info(
            '📺',
            'PROVIDER-READY',
            'Detected Configuration:',
            {
                'Provider Name': document.body.dataset.providerName || 'Unknown',
                'Base URL':      window.location.origin,
                'Proxy URL':     document.body.dataset.proxyUrl || 'N/A',
                'Fallback URL':  document.body.dataset.proxyFallbackUrl || 'N/A'
            }
        );
        // ----------------------------------------

        // Global değişkenler (sınıf özellikleri olarak)
        this.currentHls = null;
        this.loadingTimeout = null;
        this.isLoadingVideo = false;
        this.videoData = [];
        this.retryCount = 0;
        this.maxRetries = 5;
        this.lastLoadedBaseUrl = null; // HLS segment URL'leri için base URL takibi
        this.lastLoadedOrigin = null; // HLS absolute path'leri için origin takibi
        this.userGestureUntil = 0; // Kısa süreli user gesture guard
        this.selectedSubtitleUrl = null; // Seçilen altyazı URL'i
        this.currentVideoIndex = null; // Şu anki video index'i
        this.currentOriginalUrl = ''; // Orijinal video URL'i
        this.currentLoadingUrl = ''; // Şu an yüklenmeye çalışılan URL (Proxied)
        this.isLiveStream = false; // year=LIVE için player davranış bayrağı

        this.subtitleSettings = {
            color: '#FFFF00',
            fontSize: 18,
            showBackground: true,
            enabled: true
        };

        // Resume watching debounce timer
        this._resumeSaveTimer = null;

        // Preview video for seekbar thumbnails
        this.previewVideo = null;
        this.previewHls = null;
        this._lastPreviewSeek = 0;
        this.previewPendingTime = null;
        this.previewRequestedTime = null;
        this.previewSeekTimer = null;
        this.previewSeekTimeout = null;
        this.previewLoadingTimer = null;
        this.previewIsSeeking = false;
        this.previewWarmupPromise = null;
        this.previewSource = null;
        this.previewRetriedWithProxy = false;
        this.previewHlsContext = null;

        // DOM Elementleri
        this.videoPlayer = document.getElementById('video-player');
        this.videoLinksUI = document.getElementById('video-links-ui');
        this.loadingOverlay = document.getElementById('loading-overlay');
        this.toggleDiagnosticsBtn = document.getElementById('toggle-diagnostics');
        this.diagnosticsPanel = document.getElementById('diagnostics-panel');
        this.selectionModal = document.getElementById('selection-modal');
        this.selectionList = document.getElementById('selection-list');

        // Preview elements
        this.previewThumbnail = document.getElementById('preview-thumbnail');
        this.previewCanvas = document.getElementById('preview-canvas');
        this.previewTimeEl = document.getElementById('preview-time');
        this.previewContext = this.previewCanvas?.getContext('2d');

        this.init();
        window.addEventListener('lang:changed', () => {
            this.refreshI18n();
        });
    }

    // Proxy URL oluşturucu (yalnızca provider proxy)
    buildProxyUrl(url, userAgent = '', referer = '', endpoint = 'video', extraHeaders = null) {
        const proxyBase = this.proxyUrl || this.proxyFallbackUrl;
        return buildServiceProxyUrl(url, userAgent, referer, endpoint, proxyBase, extraHeaders);
    }

    async init() {
        this.setupDiagnostics();
        this.collectVideoLinks();
        this.renderVideoLinks();
        this.checkAllPlayability();
        this.loadHlsLibrary();
        this.setupUserGestureGuard();
        this.setupKeyboardControls();
        this.setupCustomControls();
        this.setupPreview();
        this.setupGlobalErrorHandling();
        this.setupSelectionModal();
        this.setupSubtitleSettings();

        // Check for fullscreen request from previous page
        const params = new URLSearchParams(window.location.search);
        if (params.get('fs') === '1') {
            // Browser might block auto-fullscreen without user gesture.
            // We'll try, but also bind it to the first interaction.
            const tryFS = async () => {
                await this.toggleFullscreen(true);
                document.removeEventListener('click', tryFS);
                document.removeEventListener('keydown', tryFS);
            };
            document.addEventListener('click', tryFS, { once: true });
            document.addEventListener('keydown', tryFS, { once: true });
            // Try immediately just in case (some environments allow it)
            tryFS();
        }
    }
}

// ── Mixin bileşimi ─────────────────────────────────────────────
// Orijinal god-class'ın tüm metotları sorumluluklara göre modüllere
// ayrıştırıldı ve prototype'a birleştirildi. `this` bağlamı ve çağrı
// zinciri birebir korunur — davranış değişikliği YOK, saf yapısal refactor.
Object.assign(
    VideoPlayer.prototype,
    coreUiMixin,
    controlsMixin,
    previewThumbnailsMixin,
    sourcesMixin,
    hlsSetupMixin,
    subtitlesMixin,
);
