// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import { t, escapeHtml } from '../../utils/dom.min.js';

// Seçim modalı (kaynak/ses/kalite/altyazı), altyazı değişimi, altyazı ayar paneli,
// tooltip ve i18n tazeleme mantığı. VideoPlayer.prototype'a mixin olarak eklenir.
export const subtitlesMixin = {
    setAudioTooltip(label) {
        const audioBtn = document.getElementById('custom-audio');
        if (!audioBtn) return;
        audioBtn.title = label ? t('audio_tooltip', { label }) : t('tooltip_audio');
    },

    setSubtitleTooltip(label) {
        const ccBtn = document.getElementById('custom-cc');
        if (!ccBtn) return;
        if (label === t('off')) {
            ccBtn.title = t('subtitle_off_tooltip');
            return;
        }
        ccBtn.title = label ? t('subtitle_tooltip', { label }) : t('tooltip_subtitle');
    },

    refreshI18n() {
        if (this.currentHls && this.currentHls.audioTracks && this.currentHls.audioTracks.length > 1) {
            let currentIndex = typeof this.currentHls.audioTrack === 'number' ? this.currentHls.audioTrack : 0;
            if (currentIndex < 0 || currentIndex >= this.currentHls.audioTracks.length) {
                currentIndex = 0;
            }
            const currentTrack = this.currentHls.audioTracks[currentIndex];
            const currentLabel = currentTrack?.name || currentTrack?.lang || t('audio_track_label', { index: currentIndex + 1 });
            this.setAudioTooltip(currentLabel);
        } else {
            this.setAudioTooltip(null);
        }

        if (this.currentVideoIndex !== null && this.videoData[this.currentVideoIndex]?.subtitles?.length) {
            if (!this.selectedSubtitleUrl) {
                this.setSubtitleTooltip(t('off'));
            } else {
                const currentSub = this.videoData[this.currentVideoIndex].subtitles.find(s => s.url === this.selectedSubtitleUrl);
                if (currentSub?.name) {
                    this.setSubtitleTooltip(currentSub.name);
                } else {
                    this.setSubtitleTooltip(t('tooltip_subtitle'));
                }
            }
        } else {
            this.setSubtitleTooltip(null);
        }

        const subtitleSelectBtn = document.getElementById('subtitle-select-btn');
        if (subtitleSelectBtn && this.currentVideoIndex !== null) {
            const subs = this.videoData[this.currentVideoIndex]?.subtitles || [];
            let label = t('off');
            if (this.selectedSubtitleUrl) {
                label = subs.find(s => s.url === this.selectedSubtitleUrl)?.name || t('selection_selected');
            } else if (subs.length > 0) {
                label = subs[0].name || t('selection_selected');
            }
            subtitleSelectBtn.innerHTML = `<i class="fas fa-closed-captioning"></i> ${label} <i class="fas fa-ellipsis-v"></i>`;
        }
    },

    /**
     * Seçim modalını ayarla (Genel)
     */
    setupSelectionModal() {
        if (!this.selectionModal) return;

        // Kapatma butonu
        const closeBtn = document.getElementById('selection-close-btn');
        closeBtn?.addEventListener('click', (e) => {
            e.stopPropagation();
            this.hideSelectionModal();
        });

        // Pencere boyutu değişince kapat (Responsive güvenliği)
        window.addEventListener('resize', () => {
            if (!this.selectionModal.classList.contains('is-hidden')) {
                this.hideSelectionModal();
            }
        });

        // ESC tuşu ile kapat
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !this.selectionModal.classList.contains('is-hidden')) {
                this.hideSelectionModal();
            }
        });

        // Dışına tıklayınca kapat
        document.addEventListener('mousedown', (e) => {
            if (!this.selectionModal.classList.contains('is-hidden')) {
                const isClickInside = this.selectionModal.contains(e.target);
                const isClickOnTrigger = e.target.closest('#custom-cc, #custom-audio, #subtitle-select-btn, #source-select-btn');

                if (!isClickInside && !isClickOnTrigger) {
                    this.hideSelectionModal();
                }
            }
        });

        // Click olaylarının arkaya (videoya) geçmesini engelle
        this.selectionModal.addEventListener('click', (e) => e.stopPropagation());
        this.selectionModal.addEventListener('mousedown', (e) => e.stopPropagation());
    },

    /**
     * Seçim dropdownını göster (Genel)
     * @param {string} title - Başlık
     * @param {string} iconClass - İkon sınıfı
     * @param {Array} items - { label, value, action } objeleri
     * @param {HTMLElement} trigger - Tetikleyici element (konumlandırma için)
     * @param {any} currentValue - Mevcut seçili değer
     */
    showSelectionModal(title, iconClass, items, trigger, currentValue = undefined) {
        if (!this.selectionModal || !this.selectionList) return;

        // Toggle Mantığı: Eğer zaten açıksa ve aynı trigger tıklandıysa kapat
        if (!this.selectionModal.classList.contains('is-hidden') && this.lastSelectionTrigger === trigger) {
            this.hideSelectionModal();
            return;
        }

        // Aktif trigger'ı kaydet
        this.lastSelectionTrigger = trigger;

        // Başlık ve İkonu Güncelle
        const titleEl = document.getElementById('modal-title');
        const iconEl = document.getElementById('modal-icon');

        if (titleEl) titleEl.querySelector('span').textContent = title;
        if (iconEl) iconEl.className = `fas ${iconClass}`;

        // Önceki listeyi temizle
        this.selectionList.innerHTML = '';

        // Altyazı seçimi ise Ayarlar butonu ekle
        if (iconClass === 'fa-closed-captioning') {
            const settingsBtn = document.createElement('button');
            settingsBtn.className = 'subtitle-item-btn subtitle-settings-trigger';
            settingsBtn.style.background = 'rgba(239, 127, 26, 0.1)';
            settingsBtn.innerHTML = `
                <i class="fas fa-cog" style="color:var(--primary-color)"></i>
                <span style="color:var(--primary-color); font-weight:700;">${t('video_subtitle_settings')}</span>
            `;
            settingsBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.showSubtitleSettings();
            });
            this.selectionList.appendChild(settingsBtn);

            const divider = document.createElement('div');
            divider.style.height = '1px';
            divider.style.background = 'rgba(255,255,255,0.1)';
            divider.style.margin = '4px 0';
            this.selectionList.appendChild(divider);
        }

        items.forEach((item) => {
            const btn = document.createElement('button');
            btn.className = 'subtitle-item-btn';

            // Aktif öğeyi işaretle
            if (currentValue !== undefined && item.value === currentValue) {
                btn.classList.add('active');
            }

            let dotHtml = '';
            if (iconClass === 'fa-server' && this.videoPlayability && this.videoPlayability[item.value]) {
                const status = this.videoPlayability[item.value].status;
                const reason = this.videoPlayability[item.value].reason;
                dotHtml = `<span class="playability-dot ${status}" title="${escapeHtml(reason)}" style="margin-left: auto;"></span>`;
            }

            btn.innerHTML = `
                <i class="fas ${currentValue !== undefined && item.value === currentValue ? 'fa-check-circle' : iconClass}"></i>
                <span>${item.label}</span>
                ${dotHtml}
            `;
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (item.action) item.action();
                else this.hideSelectionModal();
            });
            this.selectionList.appendChild(btn);
        });

        // Mobilde her zaman bottom-sheet davranisi kullan.
        if (window.innerWidth <= 1024) {
            this.showElement(this.selectionModal);
            this.selectionModal.style.position = 'fixed';
            this.selectionModal.style.top = 'auto';
            this.selectionModal.style.left = '0';
            this.selectionModal.style.right = '0';
            this.selectionModal.style.bottom = '0';
            return;
        }

        // Konumlandırma
        if (trigger) {
            const isInsidePlayer = trigger.closest('#video-player-wrapper');
            const wrapper = document.getElementById('video-player-wrapper');

            // Elemanı ilgili kapsayıcıya taşı (Tam ekran ve konumlandırma için)
            if (isInsidePlayer) {
                if (this.selectionModal.parentElement !== wrapper) {
                    wrapper.appendChild(this.selectionModal);
                }
            } else {
                const container = document.querySelector('.detail-container');
                if (this.selectionModal.parentElement !== container) {
                    container.appendChild(this.selectionModal);
                }
            }

            this.showElement(this.selectionModal);
            const rect = trigger.getBoundingClientRect();
            const dropdownRect = this.selectionModal.getBoundingClientRect();

            if (isInsidePlayer && wrapper) {
                // Player içindeki kontrollerde trigger elementine göre pozisyon al
                const wrapperRect = wrapper.getBoundingClientRect();
                const triggerLeft = rect.left - wrapperRect.left;
                const triggerBottom = wrapperRect.bottom - rect.top;

                this.selectionModal.style.position = 'absolute';
                this.selectionModal.style.bottom = `${triggerBottom + 10}px`;
                this.selectionModal.style.top = 'auto';

                // Ortala ve sınırları koru
                let leftPos = triggerLeft + (rect.width / 2) - (dropdownRect.width / 2);
                if (leftPos < 10) leftPos = 10;
                if (leftPos + dropdownRect.width > wrapperRect.width - 10) {
                    leftPos = wrapperRect.width - dropdownRect.width - 10;
                }

                this.selectionModal.style.left = `${leftPos}px`;
                this.selectionModal.style.right = 'auto';
            } else {
                // Player dışındaki buton - Body koordinatları
                const scrollY = window.scrollY || window.pageYOffset;
                const scrollX = window.scrollX || window.pageXOffset;

                if (this.selectionModal.parentElement !== document.body) {
                    document.body.appendChild(this.selectionModal);
                }

                this.selectionModal.style.position = 'absolute';
                this.selectionModal.style.top = `${rect.bottom + scrollY + 5}px`;
                this.selectionModal.style.bottom = 'auto';

                let leftPos = rect.left + scrollX + (rect.width / 2) - (dropdownRect.width / 2);
                if (leftPos < 10) leftPos = 10;
                if (leftPos + dropdownRect.width > window.innerWidth - 10) {
                    leftPos = window.innerWidth - dropdownRect.width - 10;
                }

                this.selectionModal.style.left = `${leftPos}px`;
                this.selectionModal.style.right = 'auto';
            }


            // Ekran dışına taşma kontrolü
            const finalRect = this.selectionModal.getBoundingClientRect();
            if (finalRect.left < 10) {
                this.selectionModal.style.left = '10px';
            } else if (finalRect.right > window.innerWidth - 10) {
                this.selectionModal.style.left = `${window.innerWidth - finalRect.width - 10}px`;
            }
        } else {
            this.showElement(this.selectionModal);
        }
    },

    /**
     * Seçim dropdownını gizle
     */
    hideSelectionModal() {
        if (this.selectionModal) {
            this.hideElement(this.selectionModal);
            this.lastSelectionTrigger = null;
        }
    },

    /**
     * Altyazıyı değiştir (Player ve UI)
     */
    changeSubtitle(subtitle) {
        const tracks = Array.from(this.videoPlayer.textTracks);
        const trackElements = Array.from(this.videoPlayer.querySelectorAll('track'));
        const subtitleSelectBtn = document.getElementById('subtitle-select-btn');

        if (!subtitle) {
            // Altyazı kapat
            this.selectedSubtitleUrl = null;
            this.logger.info('🔇', 'SUBTITLE', 'Closed');
            tracks.forEach(track => track.mode = 'hidden');
            if (subtitleSelectBtn) subtitleSelectBtn.innerHTML = `<i class="fas fa-closed-captioning"></i> ${t('off')} <i class="fas fa-ellipsis-v"></i>`;
            this.setSubtitleTooltip(t('off'));

            const ccBtn = document.getElementById('custom-cc');
            if (ccBtn) ccBtn.classList.remove('active');
            localStorage.setItem('wb_preferred_subtitle', 'off');
        } else {
            // Altyazı aç/değiştir
            this.selectedSubtitleUrl = subtitle.url;
            this.logger.info('💬', 'SUBTITLE', 'Switched', { 'Name': subtitle.name });

            trackElements.forEach(trackEl => {
                if (trackEl.label === subtitle.name && !trackEl.getAttribute('src')) {
                    trackEl.src = trackEl.dataset.src;
                }
            });

            tracks.forEach(track => {
                if (track.label === subtitle.name) {
                    track.mode = this.subtitleSettings.enabled ? 'showing' : 'hidden';
                } else {
                    track.mode = 'hidden';
                }
            });

            if (subtitleSelectBtn) subtitleSelectBtn.innerHTML = `<i class="fas fa-closed-captioning"></i> ${subtitle.name} <i class="fas fa-ellipsis-v"></i>`;
            this.setSubtitleTooltip(subtitle.name);

            const ccBtn = document.getElementById('custom-cc');
            if (ccBtn) {
                if (this.subtitleSettings.enabled) ccBtn.classList.add('active');
                else ccBtn.classList.remove('active');
            }
            localStorage.setItem('wb_preferred_subtitle', subtitle.name);
        }

        this.updateWatchPartyButtons();
        this.hideSelectionModal();
    },

    /**
     * Altyazı ayarlarını overlay'e ve preview'a uygula
     */
    applySubtitleSettings() {
        const overlay = document.getElementById('custom-subtitle-overlay');
        const preview = document.getElementById('subtitle-preview-text');
        const settings = this.subtitleSettings;

        if (overlay) {
            const remSize = (settings.fontSize / 16).toFixed(3);
            overlay.style.setProperty('--sub-color', settings.color);
            overlay.style.setProperty('--sub-font-size', `${remSize}rem`);
            overlay.style.setProperty('--sub-bg', settings.showBackground ? 'rgba(0, 0, 0, 0.25)' : 'transparent');
            overlay.style.setProperty('--sub-backdrop', settings.showBackground ? 'blur(1px)' : 'none');
            overlay.style.display = settings.enabled ? '' : 'none';
        }

        if (preview) {
            preview.style.color = settings.color;
            preview.style.fontSize = `${settings.fontSize}px`;
            preview.style.background = settings.showBackground ? 'rgba(0, 0, 0, 0.25)' : 'transparent';
            preview.style.backdropFilter = settings.showBackground ? 'blur(1px)' : 'none';
            preview.style.webkitBackdropFilter = settings.showBackground ? 'blur(1px)' : 'none';
        }
    },

    /**
     * Altyazı ayarları panelini kur
     */
    setupSubtitleSettings() {
        const panel            = document.getElementById('subtitle-settings-panel');
        const closeBtn         = document.getElementById('subtitle-settings-close');
        const enabledToggle    = document.getElementById('subtitle-enabled-toggle');
        const bgToggle         = document.getElementById('subtitle-bg-toggle');
        const colorOptions     = document.getElementById('subtitle-color-options');
        const sizeSlider       = document.getElementById('subtitle-size-slider');
        const sizeValue        = document.getElementById('subtitle-size-value');
        const optionsContainer = document.getElementById('subtitle-settings-options');

        if (!panel) return;

        // localStorage'dan ayarları yükle
        try {
            const saved = localStorage.getItem('wb-subtitle-settings');
            if (saved) {
                const parsed = JSON.parse(saved);
                Object.assign(this.subtitleSettings, parsed);
                // UI'ı mevcut ayarlarla senkronize et
                if (enabledToggle) enabledToggle.checked = this.subtitleSettings.enabled;
                if (bgToggle)      bgToggle.checked = this.subtitleSettings.showBackground;
                if (sizeSlider)    sizeSlider.value = this.subtitleSettings.fontSize;
                if (sizeValue)     sizeValue.textContent = this.subtitleSettings.fontSize;
                if (colorOptions) {
                    colorOptions.querySelectorAll('.subtitle-color-btn').forEach(btn => {
                        btn.classList.toggle('active', btn.dataset.color === this.subtitleSettings.color);
                    });
                }
                if (optionsContainer) {
                    optionsContainer.classList.toggle('disabled', !this.subtitleSettings.enabled);
                }
            }
        } catch { /* ilk kullanım */ }

        // Ayarları kaydet
        const saveSettings = () => {
            try {
                localStorage.setItem('wb-subtitle-settings', JSON.stringify(this.subtitleSettings));
            } catch { /* quota exceeded */ }
        };

        // Başlangıç uygulaması
        this.applySubtitleSettings();

        // Kapat
        closeBtn?.addEventListener('click', (e) => {
            e.stopPropagation();
            panel.classList.add('is-hidden');
        });

        // Altyazı açma/kapama
        enabledToggle?.addEventListener('change', () => {
            this.subtitleSettings.enabled = enabledToggle.checked;
            optionsContainer?.classList.toggle('disabled', !this.subtitleSettings.enabled);

            // TextTrack'leri de kontrol et
            if (this.videoPlayer) {
                const tracks = Array.from(this.videoPlayer.textTracks);
                if (!this.subtitleSettings.enabled) {
                    tracks.forEach(t => t.mode = 'hidden');
                    document.getElementById('custom-cc')?.classList.remove('active');
                } else {
                    // Eğer bir altyazı seçili ise onu aktif et
                    if (this.selectedSubtitleUrl) {
                        const currentSub = this.videoData[this.currentVideoIndex]?.subtitles.find(s => s.url === this.selectedSubtitleUrl);
                        if (currentSub) {
                            tracks.forEach(t => {
                                if (t.label === currentSub.name) t.mode = 'showing';
                                else t.mode = 'hidden';
                            });
                            document.getElementById('custom-cc')?.classList.add('active');
                        }
                    }
                }
            }

            saveSettings();
            this.applySubtitleSettings();
        });

        // Renk değişimi
        colorOptions?.addEventListener('click', (e) => {
            const btn = e.target.closest('.subtitle-color-btn');
            if (!btn) return;

            colorOptions.querySelectorAll('.subtitle-color-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            this.subtitleSettings.color = btn.dataset.color;

            saveSettings();
            this.applySubtitleSettings();
        });

        // Boyut değişimi
        sizeSlider?.addEventListener('input', () => {
            this.subtitleSettings.fontSize = parseInt(sizeSlider.value, 10);
            if (sizeValue) sizeValue.textContent = this.subtitleSettings.fontSize;

            saveSettings();
            this.applySubtitleSettings();
        });

        // Arka plan toggle
        bgToggle?.addEventListener('change', () => {
            this.subtitleSettings.showBackground = bgToggle.checked;

            saveSettings();
            this.applySubtitleSettings();
        });

        // Click olaylarının arkaya (videoya) geçmesini engelle
        if (panel) {
            panel.addEventListener('click', (e) => e.stopPropagation());
            panel.addEventListener('mousedown', (e) => e.stopPropagation());
        }
    },

    /**
     * Altyazı ayarları panelini göster
     */
    showSubtitleSettings() {
        const panel = document.getElementById('subtitle-settings-panel');
        if (!panel) return;

        // Selection modalı kapat
        this.hideSelectionModal();

        panel.classList.remove('is-hidden');

        // Mobilde her zaman bottom-sheet davranisi kullan.
        if (window.innerWidth <= 1024) {
            panel.style.position = 'fixed';
            panel.style.top = 'auto';
            panel.style.left = '0';
            panel.style.right = '0';
            panel.style.bottom = '0';
            panel.style.width = '100%';
            panel.style.maxWidth = 'none';
            panel.style.borderRadius = 'var(--border-radius-xl) var(--border-radius-xl) 0 0';
            return;
        }

        // Konumlandırma (Dropdown gibi)
        const ccBtn = document.getElementById('custom-cc');
        if (ccBtn) {
            const wrapper = document.getElementById('video-player-wrapper');
            if (wrapper) {
                if (panel.parentElement !== wrapper) {
                    wrapper.appendChild(panel);
                }

                const rect = ccBtn.getBoundingClientRect();
                const wrapperRect = wrapper.getBoundingClientRect();
                const triggerBottom = wrapperRect.bottom - rect.top;

                panel.style.position = 'absolute';
                panel.style.bottom = `${triggerBottom + 10}px`;
                panel.style.right = '10px';
                panel.style.left = 'auto';
                panel.style.top = 'auto';
                panel.style.width = '300px';
                panel.style.maxWidth = '90%';
                panel.style.borderRadius = 'var(--border-radius-xl)';
            }
        }
    },
};
