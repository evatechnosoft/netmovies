// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import { parseRemoteUrl, createHlsConfig, suggestInitialMode, hasCustomHeaders, ProxyMode, buildProxyUrlWithMode } from '../../video-utils.min.js';
import { t } from '../../utils/dom.min.js';

// HLS.js motoru, ses/kalite track yönetimi ve kütüphane yükleme mantığı.
// VideoPlayer.prototype'a mixin olarak eklenir; `this` bağlamı birebir korunur.
export const hlsSetupMixin = {
    /**
     * HLS Ses izlerini kontrol et ve gerekirse UI oluştur
     */
    checkHlsAudioTracks(hls) {
        const audioBtn = document.getElementById('custom-audio');

        if (hls.audioTracks && hls.audioTracks.length > 1) {
            this.logger.info('🔊', 'AUDIO', 'Audio Tracks Found', { 'Count': hls.audioTracks.length });

            // Restore preference
            const preferredAudio = localStorage.getItem('wb_preferred_audio');
            if (preferredAudio) {
                const foundIdx = hls.audioTracks.findIndex(t => (t.name || t.lang) === preferredAudio);
                if (foundIdx !== -1 && hls.audioTrack !== foundIdx) {
                    hls.audioTrack = foundIdx;
                    this.logger.info('🔊', 'AUDIO', 'Preference Restored', { 'Name': preferredAudio });
                }
            }

            let currentIndex = typeof hls.audioTrack === 'number' ? hls.audioTrack : 0;
            if (currentIndex < 0 || currentIndex >= hls.audioTracks.length) {
                currentIndex = 0;
            }
            const currentTrack = hls.audioTracks[currentIndex];
            const currentLabel = currentTrack?.name || currentTrack?.lang || t('audio_track_label', { index: currentIndex + 1 });
            this.setAudioTooltip(currentLabel);

            if (audioBtn) {
                this.showElement(audioBtn);

                const newBtn = audioBtn.cloneNode(true);
                audioBtn.parentNode.replaceChild(newBtn, audioBtn);
                this.showElement(newBtn);

                newBtn.onclick = () => {
                    this.showSelectionModal(
                        t('selection_audio'),
                        'fa-headphones-alt',
                        hls.audioTracks.map((track, index) => ({
                            label: track.name || track.lang || t('audio_track_label', { index: index + 1 }),
                            value: index,
                            action: () => {
                                try {
                                    hls.audioTrack = index;
                                    const label = track.name || track.lang || t('audio_track_label', { index: index + 1 });
                                    this.setAudioTooltip(label);
                                    localStorage.setItem('wb_preferred_audio', label);
                                    this.logger.info('🔊', 'AUDIO', 'Track Changed', { 'Target': label });
                                    this.hideSelectionModal();
                                } catch(e) {
                                    this.logger.error('❌', 'AUDIO', 'Change Error', { 'Details': e.message });
                                }
                            }
                        })),
                        newBtn,
                        hls.audioTrack
                    );
                };
            }

        } else if (audioBtn) {
            this.hideElement(audioBtn);
            this.setAudioTooltip(null);
        }
    },

    /**
     * HLS kalite (çözünürlük) seviyelerini kontrol et ve seçici UI oluştur.
     * hls.currentLevel = -1 => otomatik (adaptif bitrate). Kullanıcı seçimi
     * ve admin varsayılanı (window.DEFAULT_QUALITY: 'auto' | '1080' | '720')
     * localStorage 'wb_preferred_quality' ile kalıcıdır.
     */
    checkHlsQualityLevels(hls) {
        const qualityBtn = document.getElementById('custom-quality');
        if (!qualityBtn) return;

        const levels = hls.levels || [];
        if (levels.length <= 1) {
            this.hideElement(qualityBtn);
            return;
        }

        const labelOf = (lvl) => (lvl && lvl.height) ? `${lvl.height}p` : (lvl && lvl.bitrate ? `${Math.round(lvl.bitrate / 1000)}k` : '—');

        // Tercih / admin varsayılanını uygula (yoksa otomatik)
        const pref = localStorage.getItem('wb_preferred_quality') || window.DEFAULT_QUALITY || 'auto';
        if (pref && pref !== 'auto') {
            const idx = levels.findIndex(l => String(l.height) === String(pref));
            if (idx !== -1) hls.currentLevel = idx;
        } else {
            hls.currentLevel = -1;
        }

        this.showElement(qualityBtn);
        const newBtn = qualityBtn.cloneNode(true);
        qualityBtn.parentNode.replaceChild(newBtn, qualityBtn);
        this.showElement(newBtn);

        const refreshLabel = () => {
            const cur = hls.currentLevel;
            const isAuto = (cur === -1 || !levels[cur]);
            const short  = isAuto ? t('quality_auto') : labelOf(levels[cur]);
            newBtn.title = `${t('tooltip_quality')}: ${short}`;
            const lbl = newBtn.querySelector('.ctrl-label');
            if (lbl) lbl.textContent = short;
        };
        refreshLabel();
        hls.on(Hls.Events.LEVEL_SWITCHED, refreshLabel);

        newBtn.onclick = () => {
            const options = [
                { label: t('quality_auto'), value: -1, action: () => this._applyQuality(hls, -1, 'auto', refreshLabel) },
                // Yüksekten düşüğe sırala
                ...levels
                    .map((lvl, index) => ({ lvl, index }))
                    .sort((a, b) => (b.lvl.height || 0) - (a.lvl.height || 0))
                    .map(({ lvl, index }) => ({
                        label: labelOf(lvl),
                        value: index,
                        action: () => this._applyQuality(hls, index, String(lvl.height || ''), refreshLabel),
                    })),
            ];
            this.showSelectionModal(t('selection_quality'), 'fa-gauge-high', options, newBtn, hls.currentLevel);
        };
    },

    _applyQuality(hls, levelIndex, prefValue, refreshLabel) {
        try {
            hls.currentLevel = levelIndex;             // -1 => otomatik
            localStorage.setItem('wb_preferred_quality', prefValue || 'auto');
            if (typeof refreshLabel === 'function') refreshLabel();
            this.logger.info('🎚️', 'QUALITY', 'Level Changed', { 'Target': prefValue || 'auto' });
            this.hideSelectionModal();
        } catch (e) {
            this.logger.error('❌', 'QUALITY', 'Change Error', { 'Details': e.message });
        }
    },

    loadHLSVideo(originalUrl, referer, userAgent, forceMode = null, extraHeaders = null) {
        this.logger.info('🚀', 'HLS', 'Starting HLS.js', { 'Mode': forceMode ? `Forced ${forceMode}` : 'Smart' });
        this.retryCount = 0;

        // Uzak sunucunun origin'ini al (absolute path'leri çözümlemek için)
        const { origin, baseUrl } = parseRemoteUrl(originalUrl);
        this.lastLoadedOrigin = origin;
        this.lastLoadedBaseUrl = baseUrl;

        // HLS video için
        if (Hls.isSupported()) {
            try {
                // HLS.js yapılandırması
                let initialMode = forceMode;
                if (forceMode === true) {
                    initialMode = ProxyMode.FULL;
                } else if (forceMode === false || forceMode === null) {
                    initialMode = window.PROXY_ENABLED === false ? ProxyMode.NONE : suggestInitialMode(originalUrl, hasCustomHeaders(referer, extraHeaders));
                }
                this.currentProxyMode = initialMode; // video-utils xhrSetup bunu okuyacak
                this.logger.info('⚙️', 'HLS', 'Initial Proxy Mode', { 'Mode': initialMode });

                const hlsConfig = createHlsConfig(userAgent, referer, this, initialMode, extraHeaders);
                const hls = new Hls(hlsConfig);
                this.currentHls = hls;

                // HLS hata olaylarını dinle
                hls.on(Hls.Events.ERROR, (event, data) => {
                    if (data.fatal) {
                        this.logger.error('❌', 'HLS', 'Fatal Error', { 'Details': data.details });

                        switch (data.type) {
                            case Hls.ErrorTypes.NETWORK_ERROR:
                                const getNextMode = (current) => {
                                    if (current === ProxyMode.NONE) return ProxyMode.MANIFEST_ONLY;
                                    if (current === ProxyMode.MANIFEST_ONLY) return ProxyMode.FULL;
                                    return null;
                                };
                                const currentMode = this.currentProxyMode;
                                const nextMode = window.PROXY_ENABLED !== false ? getNextMode(currentMode) : null;

                                // Parse veya HTTP 4xx/5xx hataları için eğer başka mod kalmadıysa (veya proxy kapalıysa) anında dur
                                if (!nextMode && (data.details === Hls.ErrorDetails.MANIFEST_PARSING_ERROR || (data.response && data.response.code >= 400))) {
                                    let errLabel = 'Invalid Manifest - Aborting Retries';
                                    if (data.response && data.response.code >= 400) {
                                        errLabel = `HTTP ${data.response.code} - ${data.response.text || 'Error'}`;
                                    }
                                    this.logger.error('❌', 'HLS', errLabel);
                                    this.cleanup();
                                    this.onVideoError(errLabel);
                                    break;
                                }

                                // Eğer proxy henüz en üst seviyede değilse ve manifest parse/HTTP hatası aldıysak (örn: CORS/HTML engeli), direkt bir sonraki moda hemen geç
                                if (nextMode && (data.details === Hls.ErrorDetails.MANIFEST_PARSING_ERROR || (data.response && data.response.code >= 400))) {
                                    this.logger.warn('🛡️', 'HLS', `Deterministic manifest error in ${currentMode} mode, escalating to ${nextMode} mode immediately...`);
                                    this.cleanup();
                                    this.loadHLSVideo(originalUrl, referer, userAgent, nextMode, extraHeaders);
                                    break;
                                }

                                this.retryCount++;
                                if (this.retryCount <= 2) {
                                    this.logger.info('🔄', 'HLS', `Retrying Network Error (${this.retryCount}/2)`);
                                    hls.startLoad();
                                } else if (nextMode) {
                                    this.logger.warn('🛡️', 'HLS', `Network issues in ${currentMode} mode, escalating to ${nextMode} mode...`);
                                    this.cleanup();
                                    this.loadHLSVideo(originalUrl, referer, userAgent, nextMode, extraHeaders);
                                } else {
                                    this.onVideoError(data.details);
                                }
                                break;
                            case Hls.ErrorTypes.MEDIA_ERROR:
                                this.logger.info('🔧', 'HLS', 'Media Error, attempting recovery...');
                                hls.recoverMediaError();
                                break;
                            default:
                                this.cleanup();
                                this.onVideoError(data.details);
                                break;
                        }
                    }
                });

                hls.on(Hls.Events.MANIFEST_PARSED, (event, data) => {
                    this.logger.info('✅', 'HLS', 'Manifest Parsed Successfully');
                    this.retryCount = 0;

                    // Ses izlerini kontrol et
                    this.checkHlsAudioTracks(hls);
                    // Kalite (çözünürlük) seviyelerini kontrol et
                    this.checkHlsQualityLevels(hls);
                });

                // Ses izleri güncellendiğinde de kontrol et
                hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => {
                     this.checkHlsAudioTracks(hls);
                });

                // Manifest kaynağını belirle
                const loadUrl = buildProxyUrlWithMode(originalUrl, userAgent, referer, initialMode, this, extraHeaders);
                this.currentLoadingUrl = loadUrl;
                this.logger.info('🔑', 'HLS', 'Final Resource URL', { 'Origin': (initialMode === ProxyMode.NONE) ? 'Direct' : 'Proxy', 'Url': loadUrl });

                hls.loadSource(loadUrl);
                hls.attachMedia(this.videoPlayer);
            } catch (error) {
                this.logger.error('❌', 'HLS', 'Startup Error', { 'Details': error.message });
                this.onVideoError();
            }
        } else if (this.videoPlayer.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS desteği (Safari/iOS)
            this.logger.info('🍎', 'HLS', 'Native Engine Used');
            // Native HLS de header'ları (UA/Referer/extra) taşıyabilmek için
            // HLS.js yolundaki gibi provider proxy üzerinden yüklenmeli —
            // proxyBase'siz çağrı ham URL döndürüp tüm başlıkları düşürüyordu.
            const loadUrl = this.buildProxyUrl(originalUrl, userAgent, referer, 'video', extraHeaders);
            this.currentLoadingUrl = loadUrl;
            this.videoPlayer.src = loadUrl;
            this.videoPlayer.load();
        } else {
            this.logger.error('❌', 'HLS', 'Engine Not Supported');
            this.onVideoError();
        }
    },

    loadNormalVideo(proxyUrl, originalUrl) {
        this.logger.info('🎬', 'PLAYER', 'Loading MP4/Generic Format');
        this.currentLoadingUrl = proxyUrl;

        try {
            // MKV dosyaları için ek seçenekler
            if (originalUrl.includes('.mkv')) {
                this.videoPlayer.setAttribute('type', 'video/x-matroska');
                this.logger.info('📦', 'PLAYER', 'MKV Format Forced');
            }

            this.videoPlayer.src = proxyUrl;
            this.videoPlayer.load(); // Bazı tarayıcılarda (Safari/Mobile) şart
        } catch (error) {
            this.logger.error('❌', 'PLAYER', 'Load Error', { 'Details': error.message });
            this.onVideoError();
        }
    },

    loadHlsLibrary() {
        this.logger.info('📦', 'SYSTEM', 'Loading HLS.js Library...');

        // Engelli CDN'lere karşı çoklu kaynak: önce self-host (hiç dışa çıkmaz),
        // sonra jsDelivr, sonra cdnjs. Biri açıksa oynatıcı çalışır.
        const sources = [
            '/static/home/JS/vendor/hls.min.js',
            'https://cdn.jsdelivr.net/npm/hls.js@1.4.12/dist/hls.min.js',
            'https://cdnjs.cloudflare.com/ajax/libs/hls.js/1.4.12/hls.min.js',
        ];

        const onReady = () => {
            this.logger.info('✅', 'SYSTEM', 'HLS.js Library Loaded');
            if (this.videoData.length > 0) {
                const preferredSource = localStorage.getItem('wb_preferred_source');
                let startIndex = 0;
                if (preferredSource) {
                    const found = this.videoData.findIndex(v => v.name === preferredSource);
                    if (found !== -1) startIndex = found;
                }
                this.loadVideo(startIndex);
            } else {
                this.logger.warn('⚠️', 'SYSTEM', 'No Video Sources Found');
                this.onVideoError('No Video Sources Found', t('video_no_sources_title'), t('video_no_sources_message'));
            }
        };

        const tryLoad = (i) => {
            if (typeof Hls !== 'undefined') { onReady(); return; }
            if (i >= sources.length) {
                this.logger.error('❌', 'SYSTEM', 'HLS.js Library Failed to Load (tüm kaynaklar)');
                this.onVideoError('HLS.js Library Failed to Load');
                return;
            }
            const s = document.createElement('script');
            s.src = sources[i];
            s.onload = onReady;
            s.onerror = () => {
                this.logger.warn('↩️', 'SYSTEM', `HLS.js kaynağı başarısız, sıradaki deneniyor: ${sources[i]}`);
                tryLoad(i + 1);
            };
            document.head.appendChild(s);
        };

        tryLoad(0);
    },

    setupGlobalErrorHandling() {
        this.videoPlayer.addEventListener('error', (e) => {
            this.logger.error('❌', 'PLAYER', 'Global Video Error', { 'Details': e.message });
        });
    },
};
