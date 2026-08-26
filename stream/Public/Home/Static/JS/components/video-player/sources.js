// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import { detectFormat } from '../../video-utils.min.js';
import { t, escapeHtml } from '../../utils/dom.min.js';
import { isUrlPlayable } from '../../utils/playability.min.js';
import { isTurkish, isForced, isTurkishDub } from './lang-utils.min.js';

// Kaynak toplama / oynatılabilirlik / video yükleme orkestrasyonu / hata UI /
// WatchBuddy buton üretimi. VideoPlayer.prototype'a mixin olarak eklenir.
export const sourcesMixin = {
    collectVideoLinks() {
        this.logger.info('🔍', 'FETCHER', 'Link Extraction Started');
        const container = document.getElementById('video-links-data');
        this.proxyUrl = container?.dataset.proxyUrl;
        this.proxyFallbackUrl = container?.dataset.proxyFallbackUrl;

        const pageMediaMeta = {
            provider_id: container?.dataset.providerId || '',
            provider_base_url: container?.dataset.providerBaseUrl || '',
            plugin_name: container?.dataset.pluginName || '',
            content_id: container?.dataset.contentId || '',
            content_url: container?.dataset.contentUrl || '',
            poster_url: container?.dataset.posterUrl || '',
            year: container?.dataset.year || '',
            rating: container?.dataset.rating || '',
            season: container?.dataset.season || '',
            episode: container?.dataset.episode || ''
        };
        pageMediaMeta.is_live = this.isLiveYear(pageMediaMeta.year);

        const videoLinks = Array.from(document.querySelectorAll('.video-link-item'));
        this.videoData = videoLinks.map(link => {
            // Altyazıları topla
            const subtitles = Array.from(link.querySelectorAll('.subtitle-item')).map(sub => {
                return {
                    name: sub.dataset.name,
                    url: sub.dataset.url
                };
            });

            let extraHeaders = {};
            try {
                extraHeaders = link.dataset.extraHeaders ? JSON.parse(link.dataset.extraHeaders) : {};
            } catch { /* bozuk JSON: extraHeaders yok say */ }

            return {
                name: link.dataset.name,
                url: link.dataset.url,
                referer: link.dataset.referer,
                userAgent: link.dataset.userAgent,
                extraHeaders: extraHeaders,
                format: link.dataset.format || '',
                subtitles: subtitles,
                mediaMeta: { ...pageMediaMeta }
            };
        });

        this.logger.info('✅', 'FETCHER', 'Links Found', { 'Count': this.videoData.length });

        // Oynatılabilirlik durumunu başlat
        this.videoPlayability = this.videoData.map(() => ({
            status: 'checking',
            reason: ''
        }));
    },

    renderVideoLinks() {
        const sourcePanel = document.querySelector('.source-selection');

        if (this.videoData.length <= 1) {
            if (sourcePanel) sourcePanel.style.display = 'none';
            return;
        }

        if (sourcePanel) sourcePanel.style.display = '';

        if (this.videoData.length > 4) {
            const sourceSelectBtn = document.createElement('button');
            sourceSelectBtn.id = 'source-select-btn';
            sourceSelectBtn.className = 'button button-primary';
            sourceSelectBtn.setAttribute('data-i18n', 'selection_source');
            sourceSelectBtn.innerHTML = `<i class="fas fa-server"></i> ${t('selection_source')} <i class="fas fa-ellipsis-v"></i>`;

            const updateLabel = () => {
                if (this.currentVideoIndex !== null) {
                    const currentSource = this.videoData[this.currentVideoIndex];
                    sourceSelectBtn.innerHTML = `<i class="fas fa-server"></i> ${currentSource.name} <i class="fas fa-ellipsis-v"></i>`;
                }
            };

            sourceSelectBtn.onclick = () => {
                const sourceOptions = this.videoData.map((video, index) => ({
                    label: video.name,
                    value: index,
                    action: () => {
                        this.logger.clear();
                        this.loadVideo(index);
                        updateLabel();
                        this.hideSelectionModal();
                    }
                }));

                this.showSelectionModal(t('selection_source'), 'fa-server', sourceOptions, sourceSelectBtn, this.currentVideoIndex);
            };

            this.videoLinksUI.appendChild(sourceSelectBtn);

            // İlk yüklemede de etiketi güncellemek için bir event dinleyelim veya loadVideo içinde halledelim
            window.addEventListener('video:loaded', updateLabel);
        } else {
            this.videoData.forEach((video, index) => {
                const linkButton = document.createElement('button');
                linkButton.className = 'button source-btn';
                linkButton.dataset.index = index;
                if (index === this.currentVideoIndex) {
                    linkButton.classList.add('active');
                }

                const status = (this.videoPlayability && this.videoPlayability[index])
                    ? this.videoPlayability[index].status
                    : 'checking';
                const reason = (this.videoPlayability && this.videoPlayability[index])
                    ? this.videoPlayability[index].reason
                    : '';

                linkButton.innerHTML = `<span class="playability-dot ${status}" title="${escapeHtml(reason)}"></span> ${escapeHtml(video.name)}`;
                linkButton.onclick = () => {
                    this.logger.clear();
                    this.loadVideo(index);
                };
                this.videoLinksUI.appendChild(linkButton);
            });
        }
    },

    async checkAllPlayability() {
        if (!this.videoData || this.videoData.length === 0) return;

        this.logger.info('🔍', 'PLAYABILITY', 'Starting background playability checks for all sources...');
        const proxyBase = this.proxyUrl || this.proxyFallbackUrl;

        const checkPromises = this.videoData.map(async (video, index) => {
            try {
                const result = await isUrlPlayable(video.url, video.userAgent, video.referer, proxyBase, video.extraHeaders);
                this.videoPlayability[index] = {
                    status: result.playable ? 'online' : 'offline',
                    reason: result.reason
                };
                this.logger.info('🔍', 'PLAYABILITY', `Source ${index} (${video.name}): ${result.playable ? 'ONLINE' : 'OFFLINE'} - ${result.reason}`);
            } catch (err) {
                this.videoPlayability[index] = {
                    status: 'offline',
                    reason: `Hata: ${err.message}`
                };
                this.logger.error('❌', 'PLAYABILITY', `Source ${index} (${video.name}) check failed`, { 'Error': err.message });
            }

            // UI güncelle (Eğer <= 4 buton modundaysak)
            const dot = this.videoLinksUI.querySelector(`.source-btn[data-index="${index}"] .playability-dot`);
            if (dot) {
                dot.className = `playability-dot ${this.videoPlayability[index].status}`;
                dot.setAttribute('title', this.videoPlayability[index].reason);
            }
        });

        await Promise.all(checkPromises);
        this.logger.info('✅', 'PLAYABILITY', 'All source checks completed.');
    },

    tryNextPlayableSource() {
        if (!this.videoPlayability || this.videoPlayability.length <= 1) return false;

        if (!this.triedIndices) {
            this.triedIndices = new Set();
        }
        this.triedIndices.add(this.currentVideoIndex);

        // Henüz denenmemiş ve 'online' olan ilk kaynağı bul
        const nextIndex = this.videoData.findIndex((video, idx) => {
            return !this.triedIndices.has(idx) &&
                   this.videoPlayability[idx] &&
                   this.videoPlayability[idx].status === 'online';
        });

        if (nextIndex !== -1) {
            this.logger.warn('⚠️', 'PLAYER', `Current source failed. Auto-switching to working Source ${nextIndex}: ${this.videoData[nextIndex].name}`);

            // Kullanıcıya bilgi mesajı göster
            const infoMsg = document.createElement('div');
            infoMsg.className = 'error-message auto-switch-msg';
            infoMsg.innerHTML = `<strong>${t('video_error_title')}</strong><br>Çalışan diğer kaynağa otomatik geçiş yapılıyor... (${escapeHtml(this.videoData[nextIndex].name)})`;

            const container = document.getElementById('video-player-container');
            if (container) {
                container.insertAdjacentElement('afterend', infoMsg);
            }

            // Mesajı temizle ve yeni kaynağı yükle
            setTimeout(() => {
                infoMsg.remove();
                this.isLoadingVideo = false; // loadVideo kilidini aç
                this.loadVideo(nextIndex, true); // true ile denenmiş index geçmişini koru

                // Dropdown etiketini güncelle
                const sourceSelectBtn = document.getElementById('source-select-btn');
                if (sourceSelectBtn) {
                    sourceSelectBtn.innerHTML = `<i class="fas fa-server"></i> ${this.videoData[nextIndex].name} <i class="fas fa-ellipsis-v"></i>`;
                }
            }, 2000);

            return true;
        }

        return false;
    },

    cleanup() {
        // HLS instance'ı varsa temizle
        if (this.currentHls) {
            try {
                this.currentHls.destroy();
            } catch (e) {
                this.logger.error('❌', 'HLS', 'Destroy Error', { 'Details': e.message });
            }
            this.currentHls = null;
        }

        // Zaman aşımı varsa temizle
        if (this.loadingTimeout) {
            clearTimeout(this.loadingTimeout);
            this.loadingTimeout = null;
        }

        // Mevcut track'leri temizle (safely)
        if (this.videoPlayer) {
            this.videoPlayer.pause();
            this.videoPlayer.removeAttribute('src');
            this.videoPlayer.load();
            while (this.videoPlayer.firstChild) {
                this.videoPlayer.removeChild(this.videoPlayer.firstChild);
            }
        }
    },

    onVideoLoaded() {
        this.logger.info('🎬', 'PLAYER', 'Metadata Loaded');
    },

    onVideoCanPlay() {
        this.logger.info('▶️', 'PLAYER', 'Can Play Now');
        this.hideElement(this.loadingOverlay);

        // Timeout'u temizle
        if (this.loadingTimeout) {
            clearTimeout(this.loadingTimeout);
            this.loadingTimeout = null;
        }

        // Video oynatmayı dene
        if (this.videoPlayer.paused) {
            this.videoPlayer.play().catch(e => {
                this.logger.warn('⚠️', 'PLAYER', 'Autoplay Blocked', { 'Details': e.message });
            });
        }
    },

    // ── "Farklı Kaynaklarda" CTA helper ──────────────────────────────
    _buildOtherSourcesCta() {
        const container    = document.getElementById('video-links-data');
        const rawTitle     = container?.dataset.contentTitle || '';
        const contentTitle = rawTitle ? decodeURIComponent(rawTitle) : document.title || '';
        if (!contentTitle) return null;

        const params   = new URLSearchParams(window.location.search);
        const provider = params.get('provider') || '';
        const q        = encodeURIComponent(contentTitle);
        const href     = provider ? `/?q=${q}&provider=${encodeURIComponent(provider)}` : `/?q=${q}`;

        const cta = document.createElement('div');
        cta.className = 'other-sources-cta';
        cta.innerHTML = `
            <i class="fas fa-search"></i>
            <span class="other-sources-cta-text">${escapeHtml(t('try_other_sources'))}</span>
            <a href="${escapeHtml(href)}" class="button button-secondary">
                <i class="fas fa-external-link-alt"></i> ${escapeHtml(t('search_other_sources'))}
            </a>`;
        return cta;
    },

    _buildContributionCta() {
        const cta = document.createElement('div');
        cta.className = 'contribution-cta';
        cta.innerHTML = `
            <i class="fab fa-github"></i>
            <span class="contribution-cta-text">${escapeHtml(t('video_error_contribution'))}</span>
            <a href="https://github.com/keyiflerolsun/KekikStream" target="_blank" rel="noopener noreferrer" class="button button-ghost button-sm">
                <i class="fas fa-code-branch"></i> Pull Request
            </a>`;
        return cta;
    },

    _buildErrorLogs(data) {
        if (!data) return null;

        const details = document.createElement('details');
        details.className = 'error-logs-details';

        const summary = document.createElement('summary');
        summary.innerHTML = `<i class="fas fa-terminal"></i> ${escapeHtml(t('error_details_label'))}`;
        details.appendChild(summary);

        const content = document.createElement('div');
        content.className = 'error-logs-content';

        const infoList = document.createElement('div');
        infoList.className = 'error-info-list';

        const addRow = (label, value) => {
            const row = document.createElement('div');
            row.className = 'error-info-row';
            row.innerHTML = `<span class="error-info-label">${escapeHtml(label)}</span><span class="error-info-value">${escapeHtml(value)}</span>`;
            infoList.appendChild(row);
        };

        if (typeof data === 'object') {
            if (data.source) {
                let decodedSource = data.source;
                try {
                    decodedSource = decodeURIComponent(data.source);
                } catch (e) {
                    // Sessizce geç, orijinali kalsın
                }
                addRow('Source', decodedSource);
            }
            if (data.url) addRow('Stream', data.url);
            if (data.userAgent) addRow('Browser', data.userAgent);
            if (data.error) {
                const errStr = typeof data.error === 'object' ?
                    (data.error.code ? `${data.error.code}: ${data.error.message || 'Unknown'}` : data.error.message) :
                    data.error;
                addRow('Error', errStr);
            }
            if (data.timestamp) addRow('Time', new Date(data.timestamp).toLocaleString());
        } else {
            addRow('Info', data);
        }

        content.appendChild(infoList);
        details.appendChild(content);
        return details;
    },

    onVideoError(details = null, title = null, message = null) {
        const error = this.videoPlayer.error;
        this.hideElement(this.loadingOverlay);

        // Timeout'u temizle
        if (this.loadingTimeout) {
            clearTimeout(this.loadingTimeout);
            this.loadingTimeout = null;
        }

        // Oynatılabilecek başka çalışan kaynak var mı kontrol et ve otomatik geçiş yap
        if (this.tryNextPlayableSource()) {
            return;
        }

        let errorMessage = title || t('video_error_title');
        let errorDetails = message || t('video_error_message');

        if (error) {
            this.logger.error('❌', 'PLAYER', `Physical Error: ${error.code}`, { 'Code': error.code });

            switch (error.code) {
                case MediaError.MEDIA_ERR_ABORTED:
                    errorDetails = t('video_error_aborted');
                    break;
                case MediaError.MEDIA_ERR_NETWORK:
                    errorDetails = t('video_error_network');
                    break;
                case MediaError.MEDIA_ERR_DECODE:
                    errorDetails = t('video_error_decode');
                    break;
                case MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED:
                    errorDetails = t('video_error_not_supported');
                    break;
            }
        }

        // Hata mesajını kullanıcıya göster
        const errorEl = document.createElement('div');
        errorEl.className = 'error-message';
        errorEl.innerHTML = `<strong>${errorMessage}</strong><br>${errorDetails}<br>${t('video_error_try_another')}`;

        // Hata loglarını ekle
        const params = new URLSearchParams(window.location.search);
        const logData = {
            url: this.currentOriginalUrl || 'N/A',
            proxy: this.currentLoadingUrl || this.videoPlayer.src || 'N/A',
            source: params.get('url') || 'N/A',
            error: details || (error ? { code: error.code, message: error.message } : 'Unknown'),
            userAgent: navigator.userAgent,
            timestamp: new Date().toISOString()
        };
        const logs = this._buildErrorLogs(logData);
        if (logs) errorEl.appendChild(logs);

        const contribution = this._buildContributionCta();
        if (contribution) errorEl.appendChild(contribution);

        const cta = this._buildOtherSourcesCta();
        if (cta) errorEl.appendChild(cta);

        // Önceki hata mesajlarını temizle
        document.querySelectorAll('.error-message').forEach(el => el.remove());

        // Oynatıcıyı gizle ve hata mesajını ekle
        const container = document.getElementById('video-player-container');
        if (container) {
            container.style.display = 'none';
            container.insertAdjacentElement('afterend', errorEl);
        }
    },

    loadVideo(index, isAutoSwitch = false) {
        if (!isAutoSwitch) {
            this.triedIndices = new Set();
        }

        // Kaynak butonlarının aktif durumunu güncelle
        if (this.videoLinksUI) {
            this.videoLinksUI.querySelectorAll('.source-btn').forEach(btn => {
                if (parseInt(btn.dataset.index) === index) {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            });
        }

        // Önceki hata mesajlarını temizle
        document.querySelectorAll('.error-message').forEach(el => el.remove());

        // Oynatıcıyı tekrar göster
        const container = document.getElementById('video-player-container');
        if (container) {
            container.style.display = '';
        }

        // Video yükleniyor
        if (this.isLoadingVideo) {
            this.logger.info('⏳', 'PLAYER', 'Loading Already in Progress');
            return;
        }

        this.isLoadingVideo = true;
        this.currentVideoIndex = index;
        this.selectedSubtitleUrl = null; // Yeni video için altyazı seçimini sıfırla
        this.logger.info('📽️', 'PLAYER', `Loading Video: ${index}`, { 'Name': this.videoData[index].name });

        if (!isAutoSwitch && this.videoData[index]?.name) {
            localStorage.setItem('wb_preferred_source', this.videoData[index].name);
        }

        // Önceki kaynakları temizle
        this.cleanup();

        const selectedVideo = this.videoData[index];
        this.isLiveStream = selectedVideo?.mediaMeta?.is_live === true || this.isLiveYear(selectedVideo?.mediaMeta?.year);
        this.applyLiveModeUI();

        // Loading overlay'i göster
        this.showElement(this.loadingOverlay);

        // Yükleme zaman aşımı kontrolü ekle (15 saniye)
        this.loadingTimeout = setTimeout(() => {
            if (this.loadingOverlay && !this.loadingOverlay.classList.contains('is-hidden')) {
                this.hideElement(this.loadingOverlay);
                this.logger.error('❌', 'PLAYER', 'Loading Timeout (15s)');

                // Oynatılabilecek başka çalışan kaynak var mı kontrol et ve otomatik geçiş yap
                if (this.tryNextPlayableSource()) {
                    return;
                }

                const errorEl = document.createElement('div');
                errorEl.className = 'error-message';
                errorEl.innerHTML = `<strong>${t('video_timeout_title')}</strong><br>${t('video_timeout_message')}`;

                const params = new URLSearchParams(window.location.search);
                const logData = {
                    url: this.currentOriginalUrl || 'N/A',
                    proxy: this.currentLoadingUrl || this.videoPlayer.src || 'N/A',
                    source: params.get('url') || 'N/A',
                    error: 'Loading Timeout (15s)',
                    userAgent: navigator.userAgent,
                    timestamp: new Date().toISOString()
                };
                const logs = this._buildErrorLogs(logData);
                if (logs) errorEl.appendChild(logs);

                const timeoutCta = this._buildOtherSourcesCta();
                if (timeoutCta) errorEl.appendChild(timeoutCta);
                const container = document.getElementById('video-player-container');
                if (container) {
                    container.style.display = 'none';
                    container.insertAdjacentElement('afterend', errorEl);
                }

                this.isLoadingVideo = false;
            }
        }, 15000);

        // Video ayarları
        this.videoPlayer.muted = false;

        // Cleanup previous listeners if necessary or just use the same element
        // Removing cloneNode because it breaks custom control listeners attached in setupCustomControls
        // Re-attach core listeners to the same element (or better, use persistent ones)
        const onLoadedMetadata = () => this.onVideoLoaded();
        const onCanPlay = () => this.onVideoCanPlay();
        const onError = () => this.onVideoError();
        const onWaiting = () => {
            if (this.loadingOverlay) this.showElement(this.loadingOverlay);
            this.logger.info('⌛', 'PLAYER', 'Buffering...');
        };
        const onPlaying = () => {
            if (this.loadingOverlay) this.hideElement(this.loadingOverlay);
        };

        // Clear old ones if they were specifically named, but since we replaced the element before,
        // they were gone. Now we keep the same element.
        this.videoPlayer.removeEventListener('loadedmetadata', this._lastOnLoadedMetadata);
        this.videoPlayer.removeEventListener('canplay', this._lastOnCanPlay);
        this.videoPlayer.removeEventListener('error', this._lastOnError);
        this.videoPlayer.removeEventListener('waiting', this._lastOnWaiting);
        this.videoPlayer.removeEventListener('playing', this._lastOnPlaying);

        this._lastOnLoadedMetadata = onLoadedMetadata;
        this._lastOnCanPlay = onCanPlay;
        this._lastOnError = onError;
        this._lastOnWaiting = onWaiting;
        this._lastOnPlaying = onPlaying;

        this.videoPlayer.addEventListener('loadedmetadata', onLoadedMetadata);
        this.videoPlayer.addEventListener('canplay', onCanPlay);
        this.videoPlayer.addEventListener('error', onError);
        this.videoPlayer.addEventListener('waiting', onWaiting);
        this.videoPlayer.addEventListener('playing', onPlaying);

        // Orijinal URL'i al
        const originalUrl = selectedVideo.url;
        this.currentOriginalUrl = originalUrl;
        this.currentLoadingUrl = originalUrl;
        // Referer ve userAgent bilgilerini al (boşsa fallback kullanma)
        const referer = selectedVideo.referer || '';
        const userAgent = selectedVideo.userAgent || '';
        const extraHeaders = selectedVideo.extraHeaders || null;

        // NetMovies: harici oynatıcı (Nova/MX/VLC) için o anki kaynağı yayınla
        try {
            window.dispatchEvent(new CustomEvent('netmovies:playback', { detail: {
                url: originalUrl,
                referer,
                userAgent,
                extraHeaders,
                title: selectedVideo.name || document.title || ''
            }}));
        } catch (e) { /* yoksay */ }

        // Proxy URL'i oluştur (Go/Python fallback destekli)
        let proxyUrl = this.buildProxyUrl(originalUrl, userAgent, referer, 'video', extraHeaders);

        this.logger.info('🔌', 'PROXY', 'Generated URL', { 'Url': proxyUrl });
        const setupPreviewForFormat = (format) => {
            this.setupPreviewVideo({
                ...selectedVideo,
                format: format || selectedVideo.format || ''
            });
        };


        // Video formatını proxy'den Content-Type ile belirle
        this.logger.info('🔎', 'FETCHER', 'Detecting Format (HEAD Request)');

        fetch(proxyUrl, { method: 'HEAD' })
            .then(response => {
                if (!response.ok) {
                    this.logger.warn('⚠️', 'FETCHER', `HEAD Request Status Error: ${response.status}`);
                    throw new Error(`HTTP Error: ${response.status}`);
                }

                const contentType = response.headers.get('content-type') || '';
                this.logger.info('📄', 'FETCHER', 'Content-Type Received', { 'Type': contentType });

                // HLS formats
                const isHLS = contentType.includes('mpegurl') || contentType.includes('x-mpegurl');
                // MP4 / Generic
                const isVideo = contentType.includes('video/') || contentType.includes('mp4');

                if (isHLS) {
                    setupPreviewForFormat('hls');
                    this.loadHLSVideo(originalUrl, referer, userAgent, null, extraHeaders);
                } else if (isVideo) {
                    setupPreviewForFormat('mp4');
                    this.loadNormalVideo(proxyUrl, originalUrl);
                } else if (contentType.includes('text/html')) {
                    this.logger.error('❌', 'FETCHER', 'Invalid Content-Type for Video', { 'Type': contentType });
                    this.onVideoError();
                } else {
                    // Octet-stream veya bilinmeyen tip - URL uzantısına bak
                    const urlFormat = detectFormat(originalUrl, selectedVideo.format || '');
                    setupPreviewForFormat(urlFormat);
                    if (urlFormat === 'hls') {
                        this.loadHLSVideo(originalUrl, referer, userAgent, null, extraHeaders);
                    } else {
                        this.loadNormalVideo(proxyUrl, originalUrl);
                    }
                }
            })
            .catch(error => {
                this.logger.warn('⚠️', 'FETCHER', 'HEAD Request Failed', { 'Details': error.message });

                // Fallback: URL pattern'den format tespiti
                const urlFormat = detectFormat(originalUrl, selectedVideo.format || '');
                setupPreviewForFormat(urlFormat);
                if (urlFormat === 'hls') {
                    this.loadHLSVideo(originalUrl, referer, userAgent);
                } else {
                    this.loadNormalVideo(proxyUrl, originalUrl);
                }
            });

        // Altyazıları ekle
        const ccBtn = document.getElementById('custom-cc');

        // Varsayılan altyazı seçimi. Öncelik: (1) kullanıcının elle seçtiği tercih,
        // (2) tercih yoksa VE kaynak Türkçe dublaj DEĞİLSE → Türkçe altyazıyı otomatik
        // aç ("Forced" hariç; zorlanmış altyazı kendiliğinden açılmaz). Dublaj kaynağında
        // ses zaten Türkçe olduğundan altyazı kapalı kalır. Hiçbiri yoksa → kapalı (-1).
        let defaultIndex = -1;
        const preferredSubName = localStorage.getItem('wb_preferred_subtitle');

        if (!this.isLiveStream && selectedVideo.subtitles && selectedVideo.subtitles.length > 0) {
            if (preferredSubName && preferredSubName !== 'off') {
                const foundIdx = selectedVideo.subtitles.findIndex(s => s.name === preferredSubName);
                if (foundIdx !== -1) defaultIndex = foundIdx;
            } else if (!preferredSubName && !isTurkishDub(selectedVideo.name)) {
                // Tercih yok ve dublaj kaynağı değil → Türkçe (forced olmayan) altyazıyı aç.
                const trIdx = selectedVideo.subtitles.findIndex(s => isTurkish(s.name) && !isForced(s.name));
                if (trIdx !== -1) defaultIndex = trIdx;
            }
            // 'off' tercihi / Türkçe altyazı yok → -1 (altyazı kapalı)
        }

        if (selectedVideo.subtitles && selectedVideo.subtitles.length > 0) {
            this.logger.info('💬', 'SUBTITLE', 'Subtitles Loaded', { 'Count': selectedVideo.subtitles.length });
            if (ccBtn) {
                this.showElement(ccBtn);
                ccBtn.classList.add('active');
            }

            let subtitleSelectBtn = document.getElementById('subtitle-select-btn');
            // Birden fazla altyazı varsa, kaynak listesine altyazı seçim butonu ekle
            if (selectedVideo.subtitles.length > 1) {
                // Mevcut altyazı seçim butonunu kontrol et
                if (!subtitleSelectBtn) {
                    subtitleSelectBtn = document.createElement('button');
                    subtitleSelectBtn.id = 'subtitle-select-btn';
                    subtitleSelectBtn.className = 'button button-secondary';
                    subtitleSelectBtn.style.marginLeft = 'auto'; // Sağa yasla
                    subtitleSelectBtn.style.marginTop = 'var(--spacing-sm)';

                    // Kaynak listesinin yanına ekle
                    const sourceSelection = document.querySelector('.source-selection');
                    if (sourceSelection) {
                        sourceSelection.appendChild(subtitleSelectBtn);
                    }
                }

                // Seçili altyazıyı güncelle (buton etiketinde göster)
                const defaultSubName = selectedVideo.subtitles[defaultIndex]?.name || selectedVideo.subtitles[0].name;
                const currentSubName = this.selectedSubtitleUrl
                    ? selectedVideo.subtitles.find(s => s.url === this.selectedSubtitleUrl)?.name || t('selection_selected')
                    : defaultSubName;
                subtitleSelectBtn.innerHTML = `<i class="fas fa-closed-captioning"></i> ${currentSubName} <i class="fas fa-ellipsis-v"></i>`;

                // Tıklama olayını güncelle
                subtitleSelectBtn.onclick = (e) => {
                    const subOptions = selectedVideo.subtitles.map(s => ({
                        label: s.name,
                        value: s.url,
                        action: () => this.changeSubtitle(s)
                    }));
                    // "Kapalı" seçeneğini ekle
                    subOptions.unshift({
                        label: t('off'),
                        value: null,
                        action: () => this.changeSubtitle(null)
                    });

                    this.showSelectionModal(t('selection_subtitle'), 'fa-closed-captioning', subOptions, subtitleSelectBtn, this.selectedSubtitleUrl);
                };
            } else {
                // Tek altyazı varsa butonu kaldır
                if (subtitleSelectBtn) {
                    subtitleSelectBtn.remove();
                    subtitleSelectBtn = null;
                }
            }

            // Seçili altyazı bilgisini hemen ayarla (modal açılırsa doğru gözüksün).
            // defaultIndex -1 (altyazı kapalı) olabilir → indekse erişmeden null bırak.
            this.selectedSubtitleUrl = defaultIndex >= 0 ? selectedVideo.subtitles[defaultIndex].url : null;

            selectedVideo.subtitles.forEach((subtitle, index) => {
                try {
                    // Altyazı proxy URL'ini oluştur (Go/Python fallback destekli)
                    let subtitleProxyUrl = this.buildProxyUrl(subtitle.url, userAgent, referer, 'subtitle');

                    // Altyazı track elementini oluştur
                    const track = document.createElement('track');
                    track.kind = 'subtitles';
                    track.label = subtitle.name;
                    track.srclang = subtitle.name.toLowerCase();
                    track.dataset.src = subtitleProxyUrl; // Proxy URL'ini dataset'e kaydet (hepsini bir anda indirmemek için)

                    // Belirlenen altyazıyı varsayılan olarak işaretle ve src ataması yap
                    if (index === defaultIndex) {
                        track.src = subtitleProxyUrl;
                        track.default = true;
                    }

                    // Error handling
                    track.onerror = () => {
                        this.logger.error('❌', 'SUBTITLE', 'Load Failed', { 'Name': subtitle.name });
                        // Eğer başka başarılı track yoksa butonu gizleyelim
                        const activeTracks = Array.from(this.videoPlayer.textTracks).filter(t => t.mode !== 'disabled');
                        if (activeTracks.length === 0 && ccBtn) {
                            this.hideElement(ccBtn);
                            ccBtn.classList.remove('active');
                        }
                    };

                    this.videoPlayer.appendChild(track);

                    // Tarayıcı bazen default=true olsa da göstermez, zorla açalım
                    if (index === defaultIndex) {
                        setTimeout(() => {
                            if (this.videoPlayer.textTracks && this.videoPlayer.textTracks[index]) {
                                this.videoPlayer.textTracks[index].mode = 'showing';
                                this.logger.info('✅', 'SUBTITLE', 'Auto-activated', { 'Name': subtitle.name });

                                // Buton metnini ve tooltip'i güncelle
                                const ssBtn = document.getElementById('subtitle-select-btn');
                                if (ssBtn) {
                                    ssBtn.innerHTML = `<i class="fas fa-closed-captioning"></i> ${subtitle.name} <i class="fas fa-ellipsis-v"></i>`;
                                }
                                this.setSubtitleTooltip(subtitle.name);
                            }
                        }, 200);
                    }

                    this.logger.info('➕', 'SUBTITLE', 'Added', { 'Name': subtitle.name });
                } catch (error) {
                    this.logger.error('❌', 'SUBTITLE', 'Addition Error', { 'Name': subtitle.name, 'Details': error.message });
                }
            });
        } else if (ccBtn) {
            this.hideElement(ccBtn);
            ccBtn.classList.remove('active');
            this.setSubtitleTooltip(null);
            this.selectedSubtitleUrl = null;

            // Altyazı butonu yoksa kaldır
            const subtitleSelectBtn = document.getElementById('subtitle-select-btn');
            if (subtitleSelectBtn) {
                subtitleSelectBtn.remove();
            }
        }

        // Aktif buton stilini güncelle
        const allButtons = this.videoLinksUI.querySelectorAll('button');
        allButtons.forEach((btn, i) => {
            if (i === index) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });

        // Watch Buddy butonunun linkini güncelle
        this.updateWatchPartyButtons();

        // Video yükleme tamamlandı (asenkron işlemler devam edebilir ama UI hazır)
        this.isLoadingVideo = false;

        // Kaynak seçim butonu varsa etiketini güncellemek için event fırlat
        window.dispatchEvent(new CustomEvent('video:loaded', { detail: { index } }));
    },

    /**
     * WatchBuddy butonlarını güncelle
     */
    updateWatchPartyButtons() {
        if (this.currentVideoIndex === null) return;

        const selectedVideo = this.videoData[this.currentVideoIndex];
        if (!selectedVideo) return;

        const watchPartyButton = document.getElementById('watch-party-button');
        const watchPartyAppButton = document.getElementById('watch-party-app-button');

        if (!watchPartyButton && !watchPartyAppButton) return;

        // Referer ve userAgent bilgilerini al
        const referer = selectedVideo.referer || '';
        const userAgent = selectedVideo.userAgent || '';
        const extraHeaders = selectedVideo.extraHeaders || {};

        // Generate strictly 8-character uppercase HEX ID
        const newRoomId = (crypto.randomUUID ? crypto.randomUUID().slice(0, 8) : Math.floor(Math.random() * 0xFFFFFFFF).toString(16).padStart(8, '0')).toUpperCase();
        const wpParams = new URLSearchParams();
        const mediaMeta = selectedVideo.mediaMeta || {};

        const appendMetaParam = (key, value) => {
            const safeValue = String(value || '').trim();
            if (safeValue) {
                wpParams.set(key, safeValue);
            }
        };

        wpParams.set('url', selectedVideo.url);

        // Sayfa başlığını al (player-title elementinden)
        const playerTitleEl = document.querySelector('.player-title');
        const pageTitle = playerTitleEl ? playerTitleEl.textContent.trim() : document.title;
        wpParams.set('title', `${pageTitle} | ${selectedVideo.name}`);
        wpParams.set('user_agent', userAgent || '');
        wpParams.set('referer', referer || '');
        if (extraHeaders && Object.keys(extraHeaders).length > 0) {
            wpParams.set('extra_headers', JSON.stringify(extraHeaders));
        }

        // Seçilen altyazıyı kullan (yoksa ilk altyazıyı kullan)
        const subtitleUrl = this.isLiveStream
            ? null
            : (this.selectedSubtitleUrl || (selectedVideo.subtitles && selectedVideo.subtitles.length > 0 ? selectedVideo.subtitles[0].url : null));

        if (subtitleUrl) {
            wpParams.set('subtitle', subtitleUrl);
        }

        if (this.proxyUrl) {
            wpParams.set('proxy_url', this.proxyUrl);
        }

        appendMetaParam('provider_id', mediaMeta.provider_id);
        appendMetaParam('provider_base_url', mediaMeta.provider_base_url);
        appendMetaParam('plugin_name', mediaMeta.plugin_name);
        appendMetaParam('content_id', mediaMeta.content_id);
        appendMetaParam('content_url', mediaMeta.content_url);
        appendMetaParam('poster_url', mediaMeta.poster_url);
        appendMetaParam('year', mediaMeta.year);
        appendMetaParam('rating', mediaMeta.rating);
        appendMetaParam('source_name', selectedVideo.name || mediaMeta.source_name);
        appendMetaParam('season', mediaMeta.season);
        appendMetaParam('episode', mediaMeta.episode);


        // Web Butonu guncelle
        if (watchPartyButton) {
            watchPartyButton.href = `https://watchbuddy.tv/room/${newRoomId}?${wpParams.toString()}`;
        }

        // Uygulama (Deep Link) Butonu guncelle
        if (watchPartyAppButton) {
            // Sadece mobil cihazlarda göster
            const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
            if (isMobile) {
                this.showElement(watchPartyAppButton);
                // watchbuddy://room/ROOM_ID?params...
                watchPartyAppButton.href = `watchbuddy://room/${newRoomId}?${wpParams.toString()}`;
            } else {
                this.hideElement(watchPartyAppButton);
            }
        }

        this.logger.info('🤝', 'UI', 'WatchBuddy Buttons Updated', { 'Subtitle': subtitleUrl ? 'Available' : 'None' });
    },
};
