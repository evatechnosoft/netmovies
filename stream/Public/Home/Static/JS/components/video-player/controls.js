// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import { t } from '../../utils/dom.min.js';

// Oynatıcı kontrol / klavye / tam ekran / özel altyazı overlay mantığı.
// VideoPlayer.prototype'a mixin olarak eklenir; `this` bağlamı birebir korunur.
export const controlsMixin = {
    async toggleFullscreen(forceOpen = false) {
        const wrapper = document.getElementById('video-player-wrapper');
        const isFS = !!(document.fullscreenElement || document.webkitFullscreenElement || this.videoPlayer.webkitDisplayingFullscreen);

        if (isFS && !forceOpen) {
            if (document.exitFullscreen) await document.exitFullscreen().catch(() => {});
            else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
        } else {
            try {
                const fsMethod = wrapper.requestFullscreen || wrapper.webkitRequestFullscreen || wrapper.mozRequestFullScreen || wrapper.msRequestFullscreen;
                if (fsMethod) {
                    await fsMethod.call(wrapper);
                    if (screen.orientation?.lock) await screen.orientation.lock('landscape').catch(() => {});
                } else if (this.videoPlayer.webkitEnterFullscreen) {
                    this.videoPlayer.webkitEnterFullscreen();
                }
            } catch (e) { /* ignore */ }
        }
    },

    applyLiveModeUI() {
        const isLive = this.isLiveStream === true;
        document.body.classList.toggle('is-live-stream', isLive);

        const progressContainer = document.getElementById('custom-progress-container');
        const backwardBtn = document.getElementById('custom-backward');
        const forwardBtn = document.getElementById('custom-forward');
        const currentTimeEl = document.getElementById('current-time');
        const durationTimeEl = document.getElementById('duration-time');
        const timeDisplay = currentTimeEl?.closest('.time-display') || null;

        if (progressContainer) {
            progressContainer.style.display = isLive ? 'none' : '';
        }
        if (backwardBtn) {
            backwardBtn.disabled = isLive;
            backwardBtn.style.display = isLive ? 'none' : '';
        }
        if (forwardBtn) {
            forwardBtn.disabled = isLive;
            forwardBtn.style.display = isLive ? 'none' : '';
        }

        if (timeDisplay) {
            let liveIndicator = timeDisplay.querySelector('.live-indicator');
            if (isLive) {
                if (!liveIndicator) {
                    liveIndicator = document.createElement('div');
                    liveIndicator.className = 'live-indicator';
                    liveIndicator.innerHTML = '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#ff3b30;margin-right:8px;"></span><span>LIVE</span>';
                    liveIndicator.style.display = 'inline-flex';
                    liveIndicator.style.alignItems = 'center';
                    liveIndicator.style.fontWeight = '700';
                    liveIndicator.style.letterSpacing = '0.02em';
                    liveIndicator.style.color = '#ff3b30';
                    timeDisplay.appendChild(liveIndicator);
                }
                if (currentTimeEl) currentTimeEl.style.display = 'none';
                if (durationTimeEl) durationTimeEl.style.display = 'none';
                for (const node of Array.from(timeDisplay.childNodes)) {
                    if (node.nodeType === Node.TEXT_NODE && node.textContent?.includes('/')) {
                        node.textContent = '';
                    }
                }
            } else {
                if (liveIndicator) liveIndicator.remove();
                if (currentTimeEl) currentTimeEl.style.display = '';
                if (durationTimeEl) durationTimeEl.style.display = '';
            }
        }
    },

    setupUserGestureGuard() {
        const onGesture = () => { this.userGestureUntil = Date.now() + 1200; };
        if (this.videoPlayer) {
            this.videoPlayer.addEventListener('pointerdown', onGesture);
            this.videoPlayer.addEventListener('mousedown', onGesture);
            this.videoPlayer.addEventListener('touchstart', onGesture, { passive: true });
        }
    },

    setupKeyboardControls() {
        const SEEK_STEP = 10; // 10 saniye ileri/geri (Flutter parity)

        // Video element'in focus almasını engelle (native keyboard handling devre dışı)
        if (this.videoPlayer) {
            this.videoPlayer.tabIndex = -1;

            // Seek eventlerini yakala ve durdur (native default'ları ezmek için)
            const onSeeking = (e) => {
                if (e.isTrusted || Date.now() < this.userGestureUntil) {
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                }
            };
            this.videoPlayer.addEventListener('seeking', onSeeking, { capture: true });
            this.videoPlayer.addEventListener('seeked', onSeeking, { capture: true });
        }

        document.addEventListener('keydown', (e) => {
            if (!this.videoPlayer || this.isLoadingVideo) return;

            // Input alanındayken kısayolları devre dışı bırak
            const activeEl = document.activeElement;
            if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA')) {
                return;
            }

            switch (e.code) {
                case 'Space':
                case 'KeyK':
                    e.preventDefault();
                    if (this.videoPlayer.paused) {
                        this.videoPlayer.play().catch(() => {});
                    } else {
                        this.videoPlayer.pause();
                    }
                    break;
                case 'ArrowRight':
                case 'KeyL':
                    e.preventDefault();
                    if (!this.isLiveStream && Number.isFinite(this.videoPlayer.duration)) {
                        this.videoPlayer.currentTime = Math.min(this.videoPlayer.duration, this.videoPlayer.currentTime + SEEK_STEP);
                    }
                    break;
                case 'ArrowLeft':
                case 'KeyJ':
                    e.preventDefault();
                    if (!this.isLiveStream) {
                        this.videoPlayer.currentTime = Math.max(0, this.videoPlayer.currentTime - SEEK_STEP);
                    }
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    this.videoPlayer.volume = Math.min(1, this.videoPlayer.volume + 0.1);
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    this.videoPlayer.volume = Math.max(0, this.videoPlayer.volume - 0.1);
                    break;
                case 'KeyF':
                    e.preventDefault();
                    if (document.fullscreenElement) {
                        document.exitFullscreen().catch(() => {});
                    } else {
                        const container = document.getElementById('video-player-wrapper') || this.videoPlayer;
                        if (container.requestFullscreen) {
                            container.requestFullscreen().catch(() => {});
                        } else if (container.webkitRequestFullscreen) {
                            container.webkitRequestFullscreen();
                        }
                    }
                    break;
                case 'KeyM':
                    e.preventDefault();
                    this.videoPlayer.muted = !this.videoPlayer.muted;
                    break;
            }

            // Kendi kontrollerimiz çalıştıysa yayılımı durdur
            e.stopPropagation();
        }, { capture: true });
    },

    setupCustomControls() {
        if (!this.videoPlayer) return;

        const wrapper = document.getElementById('video-player-wrapper');

        const bottomPlayBtn = document.getElementById('bottom-play-pause');
        const muteBtn = document.getElementById('custom-mute');
        const volumeSlider = document.getElementById('custom-volume-slider');
        const progressContainer = document.getElementById('custom-progress-container');
        const progressFill = document.getElementById('custom-progress-fill');
        const currentTimeEl = document.getElementById('current-time');
        const durationTimeEl = document.getElementById('duration-time');
        const fullscreenBtn = document.getElementById('custom-fullscreen');
        const backwardBtn = document.getElementById('custom-backward');
        const forwardBtn = document.getElementById('custom-forward');
        const ccBtn = document.getElementById('custom-cc');
        const actionAnimation = document.getElementById('action-animation');

        const htmlEl = document.documentElement;
        if (bottomPlayBtn && progressContainer && fullscreenBtn) {
            htmlEl.classList.add('custom-controls-ready');
            // Firefox güvenliği: native controls'u programatik olarak da kaldır
            // Firefox bazen CSS ::-moz-media-controls gizlemeyi yoksayabilir
            this.videoPlayer.removeAttribute('controls');
        } else {
            htmlEl.classList.remove('custom-controls-ready');
        }

        const togglePlay = () => {
            this.userGestureUntil = Date.now() + 1200;
            if (this.videoPlayer.paused) {
                this.videoPlayer.play().catch(() => {});
            } else {
                this.videoPlayer.pause();
            }
        };

        const updatePlayIcons = () => {
            const iconClass = this.videoPlayer.paused ? 'fa-play' : 'fa-pause';
            if (bottomPlayBtn) bottomPlayBtn.querySelector('i').className = `fas ${iconClass}`;
        };

        const triggerAnimation = (iconClass) => {
            if (!actionAnimation) return;
            actionAnimation.querySelector('i').className = `fas ${iconClass}`;
            actionAnimation.classList.remove('active');
            void actionAnimation.offsetWidth; // trigger reflow
            actionAnimation.classList.add('active');
            // Animasyon bitince class'ı kaldır — sabit kalmasın
            setTimeout(() => actionAnimation.classList.remove('active'), 600);
        };

        // Events


        bottomPlayBtn?.addEventListener('click', (e) => {
            e.stopPropagation();
            togglePlay();
        });

        this.videoPlayer.addEventListener('play', updatePlayIcons);
        this.videoPlayer.addEventListener('pause', updatePlayIcons);



        // Volume
        volumeSlider?.addEventListener('input', (e) => {
            this.videoPlayer.volume = e.target.value;
            this.videoPlayer.muted = false;
        });

        muteBtn?.addEventListener('click', () => {
            this.videoPlayer.muted = !this.videoPlayer.muted;
        });

        this.videoPlayer.addEventListener('volumechange', () => {
            const val = this.videoPlayer.muted ? 0 : this.videoPlayer.volume;

            if (volumeSlider) {
                volumeSlider.value = val;
                // Dolu/Boş ayrımı için gradient
                const percent = val * 100;
                // Stream tarafında primary-color kullanılıyor
                volumeSlider.style.background = `linear-gradient(to right, var(--primary-color) ${percent}%, rgba(255, 255, 255, 0.2) ${percent}%)`;
            }

            if (muteBtn) {
                let icon = 'fa-volume-up';
                if (this.videoPlayer.muted || this.videoPlayer.volume === 0) icon = 'fa-volume-mute';
                else if (this.videoPlayer.volume < 0.5) icon = 'fa-volume-down';
                muteBtn.querySelector('i').className = `fas ${icon}`;
            }
        });

        // Başlangıç durumu
        if (volumeSlider) {
            const val = this.videoPlayer.muted ? 0 : this.videoPlayer.volume;
            const percent = val * 100;
            volumeSlider.style.background = `linear-gradient(to right, var(--primary-color) ${percent}%, rgba(255, 255, 255, 0.2) ${percent}%)`;
        }

        // Progress / Seeking
        // Progress / Seeking (Drag Support & Optimistic UI)
        let isDragging = false;
        let seekButtonsTimeout;
        let longPressTimer;

        const setSeekingState = (active) => {
            document.body.classList.toggle('seeking-active', active);
        };

        const showSeekButtonsTemporarily = () => {
            if (window.innerWidth > 480 || !wrapper) return;
            wrapper.classList.add('show-seek-buttons');

            clearTimeout(seekButtonsTimeout);
            seekButtonsTimeout = setTimeout(() => {
                wrapper.classList.remove('show-seek-buttons');
            }, 2500);
        };

        const handleSeekMove = (e) => {
            if (this.isLiveStream) return;
            const rect = progressContainer.getBoundingClientRect();
            let pos = (e.pageX - rect.left) / progressContainer.offsetWidth;
            pos = Math.max(0, Math.min(1, pos)); // Clamp between 0 and 1

            if (progressFill) progressFill.style.width = `${pos * 100}%`;

            // Show preview time if wanted (optional)
            if (Number.isFinite(this.videoPlayer.duration)) {
                 const previewTime = pos * this.videoPlayer.duration;
                 if (currentTimeEl) currentTimeEl.textContent = this.formatDuration(previewTime);
            }
        };

        const handleSeekEnd = (e) => {
            if (this.isLiveStream) return;
            if (!isDragging) return;
            isDragging = false;
            setSeekingState(false);

            document.removeEventListener('mousemove', handleSeekMove);
            document.removeEventListener('mouseup', handleSeekEnd);

            const rect = progressContainer.getBoundingClientRect();
            let pos = (e.pageX - rect.left) / progressContainer.offsetWidth;
            pos = Math.max(0, Math.min(1, pos));

            if (Number.isFinite(this.videoPlayer.duration)) {
                this.userGestureUntil = Date.now() + 1200;
                this.videoPlayer.currentTime = pos * this.videoPlayer.duration;
            }
        };

        progressContainer?.addEventListener('mousedown', (e) => {
            if (this.isLiveStream) return;
            isDragging = true;
            setSeekingState(true);
            handleSeekMove(e); // Update UI immediately

            document.addEventListener('mousemove', handleSeekMove);
            document.addEventListener('mouseup', handleSeekEnd);
        });

        // Touch support
        progressContainer?.addEventListener('touchstart', (e) => {
            if (this.isLiveStream) return;
            isDragging = true;
            if (e.cancelable) e.preventDefault();
            setSeekingState(true);
            // Use the first touch point
            const touch = e.touches[0];
            const fakeEvent = { pageX: touch.pageX };
            handleSeekMove(fakeEvent);

            const handleTouchMove = (e) => {
                if (e.cancelable) e.preventDefault();
                const touch = e.touches[0];
                handleSeekMove({ pageX: touch.pageX });
            };

            const handleTouchEnd = (e) => {
                isDragging = false;
                setSeekingState(false);
                document.removeEventListener('touchmove', handleTouchMove);
                document.removeEventListener('touchend', handleTouchEnd);
                document.removeEventListener('touchcancel', handleTouchEnd);

                // For touch end, we use the last known position or we need changedTouches
                // Ideally handleSeekMove updates a variable we can use, but simply calculating based on last move is tricky without state.
                // Simpler: Just rely on the last visual update? No, we need to set currentTime.
                // Let's re-calculate from changedTouches
                if (e.changedTouches.length > 0) {
                     const touch = e.changedTouches[0];
                     const rect = progressContainer.getBoundingClientRect();
                     let pos = (touch.pageX - rect.left) / progressContainer.offsetWidth;
                     pos = Math.max(0, Math.min(1, pos));

                     if (Number.isFinite(this.videoPlayer.duration)) {
                        this.userGestureUntil = Date.now() + 1200;
                        this.videoPlayer.currentTime = pos * this.videoPlayer.duration;
                    }
                }
            };

            document.addEventListener('touchmove', handleTouchMove, { passive: false });
            document.addEventListener('touchend', handleTouchEnd);
            document.addEventListener('touchcancel', handleTouchEnd);
        });

        const updateTimeUI = () => {
            if (this.isLiveStream) {
                if (currentTimeEl) currentTimeEl.textContent = this.formatDuration(this.videoPlayer.currentTime || 0);
                if (durationTimeEl) durationTimeEl.textContent = 'LIVE';
                return;
            }
            if (Number.isFinite(this.videoPlayer.duration) && this.videoPlayer.duration > 0) {
                // Dragging sırasında ilerleme çubuğunu güncelleme (jitter önleme)
                if (!isDragging) {
                    const percent = (this.videoPlayer.currentTime / this.videoPlayer.duration) * 100;
                    if (progressFill) progressFill.style.width = `${percent}%`;
                    if (currentTimeEl) currentTimeEl.textContent = this.formatDuration(this.videoPlayer.currentTime);
                }
                if (durationTimeEl) durationTimeEl.textContent = this.formatDuration(this.videoPlayer.duration);
            }
        };

        this.videoPlayer.addEventListener('timeupdate', () => {
            updateTimeUI();

            // Next Episode Early Trigger
            if (!this.isLiveStream && this.videoPlayer.duration > 60) {
                const timeLeft = this.videoPlayer.duration - this.videoPlayer.currentTime;

                // Show panel in last 60 seconds
                if (timeLeft <= 60 && timeLeft > 30) {
                    const panel = document.getElementById('next-episode-panel');
                    if (panel && window.__nextEpisodeUrl) {
                        panel.classList.remove('is-hidden');
                    }
                }

                // Start countdown in last 30 seconds
                if (timeLeft <= 30 && timeLeft > 0) {
                    window.dispatchEvent(new CustomEvent('player:requestNextEpisode', {
                        detail: { secs: Math.floor(timeLeft) }
                    }));
                }
            }

            // Debounced resume position save (every 5s)
            if (!this._resumeSaveTimer) {
                this._resumeSaveTimer = setTimeout(() => {
                    this._resumeSaveTimer = null;
                    this._saveResumePosition();
                }, 5000);
            }

            // Mark episode watched at 85%
            if (!this.isLiveStream && this.videoPlayer.duration > 0 && this.videoPlayer.currentTime / this.videoPlayer.duration > 0.85) {
                this._markEpisodeWatched();
            }
        });
        this.videoPlayer.addEventListener('loadedmetadata', updateTimeUI);
        this.videoPlayer.addEventListener('durationchange', updateTimeUI);

        this.videoPlayer.addEventListener('ended', () => {
            // Clear resume timer and remove entry (finished watching)
            if (this._resumeSaveTimer) {
                clearTimeout(this._resumeSaveTimer);
                this._resumeSaveTimer = null;
            }
            try {
                const metaContainer = document.getElementById('video-links-data');
                const contentUrl = metaContainer?.dataset?.contentUrl || window.location.pathname;
                const season  = metaContainer?.dataset?.season  || '';
                const episode = metaContainer?.dataset?.episode || '';
                const resumeKey = (season && episode) ? `${contentUrl}::s${season}::e${episode}` : contentUrl;
                const resumeData = JSON.parse(localStorage.getItem('wb_resume_watching') || '{}');
                delete resumeData[resumeKey];
                localStorage.setItem('wb_resume_watching', JSON.stringify(resumeData));
            } catch (e) { /* ignore */ }
            this._markEpisodeWatched();
        });

        // Backward / Forward
        const SEEK_STEP = 10;
        backwardBtn?.addEventListener('click', () => {
            if (this.isLiveStream) return;
            this.userGestureUntil = Date.now() + 1200;
            this.videoPlayer.currentTime = Math.max(0, this.videoPlayer.currentTime - SEEK_STEP);
            triggerAnimation('fa-undo');
        });

        forwardBtn?.addEventListener('click', () => {
            if (this.isLiveStream) return;
            this.userGestureUntil = Date.now() + 1200;
            this.videoPlayer.currentTime = Math.min(this.videoPlayer.duration, this.videoPlayer.currentTime + SEEK_STEP);
            triggerAnimation('fa-redo');
        });

        // Fullscreen with mobile orientation support
        fullscreenBtn?.addEventListener('click', async () => {
            await this.toggleFullscreen();
        });

        const handleFullscreenChange = () => {
            const isFS = !!(document.fullscreenElement || document.webkitFullscreenElement || this.videoPlayer?.webkitDisplayingFullscreen);

            // İkonu güncelle
            if (fullscreenBtn) {
                const icon = fullscreenBtn.querySelector('i');
                if (icon) icon.className = `fas ${isFS ? 'fa-compress' : 'fa-expand'}`;
            }

            document.body.classList.toggle('is-fullscreen', isFS);

            // Fullscreen çıkışında orientation kilidini kaldır ve cleanup
            if (!isFS) {
                if (screen.orientation?.unlock) {
                    try { screen.orientation.unlock(); } catch (_) {}
                }
                document.body.classList.remove('keyboard-open');
                window.dispatchEvent(new Event('resize'));
            }
        };

        document.addEventListener('fullscreenchange', handleFullscreenChange);
        document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
        this.videoPlayer.addEventListener('webkitbeginfullscreen', handleFullscreenChange);
        this.videoPlayer.addEventListener('webkitendfullscreen', handleFullscreenChange);

        // Ekran döndüğünde layout'u tazele (bazı mobil tarayıcılar için)
        window.addEventListener('orientationchange', () => {
            if (document.fullscreenElement) {
                setTimeout(() => {
                    window.dispatchEvent(new Event('resize'));
                }, 300);
            }
        });

        // Subtitles (CC)
        ccBtn?.addEventListener('click', () => {
            const tracks = this.videoPlayer.textTracks;
            // Eğer o anki videonun birden fazla altyazısı varsa modalı aç
            if (this.currentVideoIndex !== null &&
                this.videoData[this.currentVideoIndex].subtitles &&
                this.videoData[this.currentVideoIndex].subtitles.length > 1) {

                const subOptions = this.videoData[this.currentVideoIndex].subtitles.map(s => ({
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

                this.showSelectionModal(t('selection_subtitle'), 'fa-closed-captioning', subOptions, ccBtn, this.selectedSubtitleUrl);

            } else if (tracks.length > 0) {
                // Tek altyazı varsa toggle yap
                const isShowing = tracks[0].mode === 'showing';
                tracks[0].mode = isShowing ? 'hidden' : 'showing';
                ccBtn.classList.toggle('active', !isShowing);
            }
        });

        // Auto-hide controls (Robust Logic)
        let hideTimeout;

        const showControls = () => {
            wrapper.classList.add('show-controls');
            wrapper.style.cursor = 'default';

            clearTimeout(hideTimeout);
            if (!this.videoPlayer.paused) {
                const isMobile = window.innerWidth <= 1024;
                const hideDelay = isMobile ? 4500 : 3000;
                hideTimeout = setTimeout(() => {
                    hideControls();
                }, hideDelay);
            }
        };

        const hideControls = (force = false) => {
            if (!force && this.videoPlayer.paused) return; // Paused iken asla gizleme (mobile tap force ile bypass eder)
            if (isDragging) return; // Kullanıcı arama yaparken (parmak basılıyken) gizleme
            wrapper.classList.remove('show-controls');
            if (document.fullscreenElement) {
                wrapper.style.cursor = 'none';
            }
        };

        // ── Mobile Double-Tap Seek ──
        let lastTapTime = 0;
        let lastTapSide = null;
        let singleTapTimeout = null;

        const handleMobileTap = (e) => {
            const now = Date.now();
            const rect = wrapper.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const side = x < rect.width / 2 ? 'left' : 'right';

            if (now - lastTapTime < 300 && lastTapSide === side) {
                // Double-tap → Seek
                clearTimeout(singleTapTimeout);
                lastTapTime = 0;
                lastTapSide = null;

                if (!Number.isFinite(this.videoPlayer.duration) || this.videoPlayer.duration <= 0) return;
                this.userGestureUntil = Date.now() + 1200;

                if (side === 'left') {
                    this.videoPlayer.currentTime = Math.max(0, this.videoPlayer.currentTime - SEEK_STEP);
                    triggerAnimation('fa-undo');
                } else {
                    this.videoPlayer.currentTime = Math.min(this.videoPlayer.duration, this.videoPlayer.currentTime + SEEK_STEP);
                    triggerAnimation('fa-redo');
                }
            } else {
                lastTapTime = now;
                lastTapSide = side;
                singleTapTimeout = setTimeout(() => {
                    // Single-tap → Toggle controls
                    lastTapTime = 0;
                    lastTapSide = null;
                    if (wrapper.classList.contains('show-controls')) {
                        hideControls(true); // Mobile tap: force hide (paused olsa bile)
                    } else {
                        showControls();
                    }
                }, 300);
            }
        };

        const toggleControls = (e) => {
            // Eğer tıklanan eleman interaktif ise sadece süreyi yenile (veya input ise)
            if (e && e.target.closest('.bottom-controls, .player-header, .ctrl-btn, .subtitle-modal, input, .button')) {
                showControls();
                return;
            }

            // Modern Player UX:
            // Masaüstü: Tıkla -> Oynat/Durdur
            // Mobil: Çift dokunma -> Seek, Tek dokunma -> Kontrolleri Aç/Kapa
            const isDesktop = window.innerWidth > 1024;

            if (isDesktop) {
                togglePlay();
                triggerAnimation(this.videoPlayer.paused ? 'fa-pause' : 'fa-play');
                showControls();
            } else {
                handleMobileTap(e);
            }
        };

        // Hareket takibi (Sadece Mouse için - Touch cihazlarda click/tap çalışır)
        wrapper.addEventListener('pointermove', (e) => {
            if (e.pointerType === 'touch') return; // Dokunmatik cihazlarda hover emülasyonunu engelle
            showControls();
        });

        // Click / Tap (Toggle)
        wrapper.addEventListener('click', (e) => {
            toggleControls(e);
        });

        // Long press: show seek buttons on very small screens
        wrapper.addEventListener('touchstart', (e) => {
            const isControlHit = e.target.closest('.bottom-controls, .control-row, .ctrl-btn, .progress-container, .volume-group');
            if (isControlHit || window.innerWidth > 480) return;

            clearTimeout(longPressTimer);
            longPressTimer = setTimeout(() => {
                showControls();
                showSeekButtonsTemporarily();
            }, 450);
        }, { passive: true });

        wrapper.addEventListener('touchend', () => {
            clearTimeout(longPressTimer);
        }, { passive: true });

        wrapper.addEventListener('touchcancel', () => {
            clearTimeout(longPressTimer);
        }, { passive: true });

        // ── Buffering Spinner Helpers ──
        let bufferSpinnerTimer = null;

        const hideBufferSpinner = () => {
            if (bufferSpinnerTimer) { clearTimeout(bufferSpinnerTimer); bufferSpinnerTimer = null; }
            if (this.loadingOverlay) {
                this.hideElement(this.loadingOverlay);
                this.loadingOverlay.classList.remove('is-buffering');
            }
        };

        const showBufferSpinner = () => {
            if (this.loadingOverlay) {
                this.loadingOverlay.classList.add('is-buffering');
                this.showElement(this.loadingOverlay);
            }
            // Güvenlik: 8s sonra hâlâ görünüyorsa otomatik gizle
            if (bufferSpinnerTimer) clearTimeout(bufferSpinnerTimer);
            bufferSpinnerTimer = setTimeout(hideBufferSpinner, 8000);
        };

        // Video durumu değişiklikleri
        this.videoPlayer.addEventListener('play', () => {
            showControls();
            hideBufferSpinner();
        });

        // playing: buffering sonrası da spinner'ı temizle (play tetiklenmez)
        this.videoPlayer.addEventListener('playing', () => {
            hideBufferSpinner();
        });

        this.videoPlayer.addEventListener('pause', showControls);

        this.videoPlayer.addEventListener('waiting', () => {
            showControls();
            // Sadece gerçek buffering'de spinner göster
            if (!this.videoPlayer.paused && !this.isLoadingVideo) {
                showBufferSpinner();
            }
        });

        // canplay/canplaythrough: buffer bitince spinner temizle
        this.videoPlayer.addEventListener('canplay', hideBufferSpinner);
        this.videoPlayer.addEventListener('canplaythrough', hideBufferSpinner);

        // Mouse Wheel ile Ses Kontrolü
        wrapper.addEventListener('wheel', (e) => {
            e.preventDefault();
            const delta = Math.sign(e.deltaY) * -1;
            const step = 0.05;
            let newVol = this.videoPlayer.volume + (delta * step);
            newVol = Math.max(0, Math.min(1, newVol));
            this.videoPlayer.volume = newVol;

            // Mute varsa kaldır
            if (newVol > 0 && this.videoPlayer.muted) this.videoPlayer.muted = false;

            // Volume Bar'ı Göster
            if (volumeSlider) {
                const group = volumeSlider.closest('.volume-group');
                if (group) {
                    group.classList.add('show-slider');

                    if (this.volumeTimer) clearTimeout(this.volumeTimer);

                    this.volumeTimer = setTimeout(() => {
                        group.classList.remove('show-slider');
                        this.volumeTimer = null;
                    }, 1500);
                }
            }

            // Kontrolleri de göster
            showControls();
        }, { passive: false });

        // Fullscreen'e girildiğinde kontrolleri zorla göster
        document.addEventListener('fullscreenchange', () => {
            showControls();
        });
        document.addEventListener('webkitfullscreenchange', () => {
            showControls();
        });

        // Özel altyazı sistemi (Native ::cue desteği yetersiz olduğu için her tarayıcıda kullanıyoruz)
        this.setupCustomSubtitles();

        // Başlangıçta kontrolleri göster
        showControls();
    },

    /**
     * Özel altyazı render sistemi
     * Native ::cue CSS'i gelişmiş stilleri desteklemediği için bu overlay'i kullanıyoruz.
     */
    setupCustomSubtitles() {
        const subtitleOverlay = document.getElementById('custom-subtitle-overlay');
        if (!subtitleOverlay) return;

        // TextTrack cue değişikliklerini dinle
        const updateSubtitleOverlay = () => {
            const tracks = this.videoPlayer.textTracks;
            let activeText = '';

            for (let i = 0; i < tracks.length; i++) {
                const track = tracks[i];
                if (track.mode === 'showing' && track.activeCues) {
                    for (let j = 0; j < track.activeCues.length; j++) {
                        const cue = track.activeCues[j];
                        if (cue.text) {
                            // HTML tag'lerini temizle (VTT formatting)
                            const cleanText = cue.text.replace(/<[^>]*>/g, '');
                            activeText += (activeText ? '\n' : '') + cleanText;
                        }
                    }
                }
            }

            if (activeText) {
                subtitleOverlay.innerHTML = `<span>${activeText}</span>`;
            } else {
                subtitleOverlay.innerHTML = '';
            }
        };

        // Her textTrack için cuechange event'i dinle
        const bindTrackEvents = () => {
            const tracks = this.videoPlayer.textTracks;
            for (let i = 0; i < tracks.length; i++) {
                tracks[i].removeEventListener('cuechange', updateSubtitleOverlay);
                tracks[i].addEventListener('cuechange', updateSubtitleOverlay);
            }
        };

        // Track'ler eklendiğinde event'leri bağla
        this.videoPlayer.textTracks.addEventListener('addtrack', bindTrackEvents);

        // Video yüklendiğinde track'leri bağla
        this.videoPlayer.addEventListener('loadedmetadata', bindTrackEvents);

        // Başlangıçta bağla
        bindTrackEvents();
    },
};
