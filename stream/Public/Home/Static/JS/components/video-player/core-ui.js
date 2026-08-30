// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

// Temel yardımcılar: element göster/gizle, süre formatı, live tespiti,
// diagnostics paneli, resume/watched kalıcılığı.
// VideoPlayer.prototype'a mixin olarak eklenir; `this` bağlamı birebir korunur.
export const coreUiMixin = {
    formatDuration(seconds) {
        if (!Number.isFinite(seconds)) return '0:00';
        const hours = Math.floor(seconds / 3600);
        const mins = Math.floor((seconds % 3600) / 60);
        const secs = Math.floor(seconds % 60);
        return hours > 0
            ? `${hours}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
            : `${mins}:${secs.toString().padStart(2, '0')}`;
    },

    isLiveYear(value) {
        return String(value || '').trim().toUpperCase() === 'LIVE';
    },

    showElement(element) {
        if (!element) return;
        element.classList.remove('is-hidden');
        element.style.removeProperty('display');
    },

    hideElement(element) {
        if (!element) return;
        element.classList.add('is-hidden');
        element.style.removeProperty('display');
    },

    setupDiagnostics() {
        if (this.toggleDiagnosticsBtn) {
            // Panel göster/gizle
            this.toggleDiagnosticsBtn.addEventListener('click', () => {
                if (this.diagnosticsPanel.classList.contains('is-hidden')) {
                    this.showElement(this.diagnosticsPanel);
                    this.toggleDiagnosticsBtn.setAttribute('aria-expanded', 'true');
                    this.logger.updateDiagnosticsPanel();
                } else {
                    this.hideElement(this.diagnosticsPanel);
                    this.toggleDiagnosticsBtn.setAttribute('aria-expanded', 'false');
                }
            });

            // Logları temizle
            document.getElementById('clear-logs').addEventListener('click', () => {
                this.logger.clear();
                this.logger.info('🧹', 'SYSTEM', 'Logs Cleared');
            });

            // Logları kopyala
            document.getElementById('copy-logs').addEventListener('click', () => {
                const logText = this.logger.getFormattedLogs();

                // Clipboard API kullanılabilir mi kontrol et (HTTPS veya localhost gerektirir)
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(logText)
                        .then(() => {
                            this.logger.info('📋', 'SYSTEM', 'Logs Copied to Clipboard');
                        })
                        .catch(err => {
                            this.logger.error('❌', 'SYSTEM', 'Clipboard Error', { 'Details': err.message });
                        });
                } else {
                    // Fallback: execCommand kullan (HTTP için)
                    try {
                        const textArea = document.createElement('textarea');
                        textArea.value = logText;
                        textArea.style.position = 'fixed';
                        textArea.style.left = '-9999px';
                        document.body.appendChild(textArea);
                        textArea.select();
                        document.execCommand('copy');
                        document.body.removeChild(textArea);
                        this.logger.info('📋', 'SYSTEM', 'Logs Copied to Clipboard');
                    } catch (err) {
                        this.logger.error('❌', 'SYSTEM', 'Clipboard Error', { 'Details': err.message });
                    }
                }
            });

            // Logları indir
            document.getElementById('download-logs').addEventListener('click', () => {
                const logText = this.logger.getFormattedLogs();
                const blob = new Blob([logText], { type: 'text/plain' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `video-logs-${new Date().toISOString().replace(/[:.]/g, '-')}.txt`;
                document.body.appendChild(a);
                a.click();
                setTimeout(() => {
                    document.body.removeChild(a);
                    URL.revokeObjectURL(url);
                }, 100);
                this.logger.info('💾', 'SYSTEM', 'Logs Downloaded');
            });
        }
    },

    _saveResumePosition() {
        try {
            const video = this.videoPlayer;
            if (!video || !video.duration || video.duration < 60) return;
            if (video.currentTime < 10) return;

            const metaContainer = document.getElementById('video-links-data');
            const contentUrl = metaContainer?.dataset?.contentUrl || window.location.pathname;
            const season  = metaContainer?.dataset?.season  || '';
            const episode = metaContainer?.dataset?.episode || '';
            const resumeKey = (season && episode) ? `${contentUrl}::s${season}::e${episode}` : contentUrl;

            const resumeData = JSON.parse(localStorage.getItem('wb_resume_watching') || '{}');

            if (video.currentTime / video.duration > 0.9) {
                delete resumeData[resumeKey];
            } else {
                resumeData[resumeKey] = {
                    time:      Math.floor(video.currentTime),
                    duration:  Math.floor(video.duration),
                    title:     metaContainer?.dataset?.contentTitle ? decodeURIComponent(metaContainer.dataset.contentTitle) : document.title,
                    poster:    metaContainer?.dataset?.posterUrl || '',
                    plugin:    metaContainer?.dataset?.pluginName || '',
                    season,
                    episode,
                    timestamp: Date.now()
                };
            }

            const entries = Object.entries(resumeData);
            if (entries.length > 50) {
                entries.sort((a, b) => b[1].timestamp - a[1].timestamp);
                localStorage.setItem('wb_resume_watching', JSON.stringify(Object.fromEntries(entries.slice(0, 50))));
            } else {
                localStorage.setItem('wb_resume_watching', JSON.stringify(resumeData));
            }

            // Keep the same progress on Mi Box, TV browsers and other devices.
            const title = metaContainer?.dataset?.contentTitle
                ? decodeURIComponent(metaContainer.dataset.contentTitle)
                : document.title;
            fetch('/api/v1/progress', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                keepalive: true,
                body: JSON.stringify({
                    title,
                    content_url: contentUrl,
                    poster: metaContainer?.dataset?.posterUrl || '',
                    plugin: metaContainer?.dataset?.pluginName || '',
                    media_type: season || episode ? 'serie' : 'movie',
                    episode: season && episode ? `S${season} E${episode}` : '',
                    position_seconds: Math.floor(video.currentTime),
                    duration_seconds: Math.floor(video.duration),
                }),
            }).catch(() => {});
        } catch (e) {
            console.warn('Resume save failed:', e);
        }
    },

    _markEpisodeWatched() {
        try {
            const meta = document.getElementById('video-links-data');
            const contentUrl = meta?.dataset?.contentUrl;
            const season     = meta?.dataset?.season;
            const episode    = meta?.dataset?.episode;
            if (!contentUrl || !season || !episode) return;

            const key = `${season}x${episode}`;
            const watchedData = JSON.parse(localStorage.getItem('wb_watched_episodes') || '{}');
            if (!watchedData[contentUrl]) watchedData[contentUrl] = [];
            if (!watchedData[contentUrl].includes(key)) {
                watchedData[contentUrl].push(key);
                localStorage.setItem('wb_watched_episodes', JSON.stringify(watchedData));
            }

            document.querySelectorAll(`.episode-card[data-season="${season}"][data-episode="${episode}"]`).forEach(el => {
                el.classList.add('is-watched');
            });
        } catch (e) {
            console.warn('Episode watched mark failed:', e);
        }
    },
};
