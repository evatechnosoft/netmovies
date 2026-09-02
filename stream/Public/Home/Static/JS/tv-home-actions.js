import { escapeHtml } from './utils/dom.min.js';

const encode = (value) => encodeURIComponent(value || '');
const LIST_STORAGE_KEY = 'netmovies_tv_lists';
const LONG_PRESS_MS = 650;

function watchUrl(item, episode = null) {
    const params = new URLSearchParams({
        url: item.url,
        baslik: item.title,
        content_url: item.url,
        poster_url: item.poster || '',
    });
    if (episode) {
        params.set('bolum_adi', episode.title || '');
        params.set('season', episode.season || '');
        params.set('episode', episode.episode || '');
    }
    return `/izle/${encode(item.plugin)}?${params.toString()}`;
}

function closeEpisodePicker() {
    document.querySelector('.tv-episode-picker')?.remove();
}

function loadLists() {
    try {
        const value = JSON.parse(localStorage.getItem(LIST_STORAGE_KEY) || '{}');
        return value && typeof value === 'object' ? value : {};
    } catch (_) {
        return {};
    }
}

function saveList(item, listName) {
    const lists = loadLists();
    const current = Array.isArray(lists[listName]) ? lists[listName] : [];
    const key = `${item.plugin}|${item.url}`;
    const exists = current.some((entry) => entry === key);
    lists[listName] = exists ? current.filter((entry) => entry !== key) : [...current, key];
    localStorage.setItem(LIST_STORAGE_KEY, JSON.stringify(lists));
    return !exists;
}

function closeActionMenu() {
    document.querySelectorAll('.tv-joystick').forEach((menu) => menu.remove());
}

function joystickButton(label, icon, onClick, className = '') {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `tv-joystick-action ${className}`.trim();
    button.title = label;
    button.setAttribute('aria-label', label);
    button.innerHTML = `<i class="fas ${icon}"></i>`;
    button.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        onClick();
    });
    return button;
}

function showActionMenu(card, item) {
    closeActionMenu();
    card.classList.add('tv-joystick-host');
    const menu = document.createElement('div');
    menu.className = 'tv-joystick';
    menu.setAttribute('role', 'menu');
    menu.setAttribute('aria-label', `${item.title} işlemleri`);
    menu.innerHTML = `
        <button type="button" class="tv-joystick-plus" aria-label="İşlemleri kapat/aç">+</button>
        <div class="tv-joystick-actions"></div>
        <div class="tv-joystick-lists"></div>
        <span class="tv-joystick-status" aria-live="polite"></span>`;
    const actions = menu.querySelector('.tv-joystick-actions');
    const lists = menu.querySelector('.tv-joystick-lists');
    const status = menu.querySelector('.tv-joystick-status');
    const plus = menu.querySelector('.tv-joystick-plus');
    if (!actions || !lists || !status || !plus) return;

    const play = () => { closeActionMenu(); window.location.href = watchUrl(item); };
    const episodes = () => {
        closeActionMenu();
        if (item.mediaType === 'serie') openSeries(item);
        else window.location.href = `/icerik/${encode(item.plugin)}?url=${encode(item.url)}`;
    };
    const pip = async () => {
        const customPip = document.getElementById('custom-pip');
        if (customPip) {
            customPip.click();
            return;
        }
        const video = document.querySelector('video');
        if (!video || !document.pictureInPictureEnabled) {
            status.textContent = 'PiP oynatıcı açıldıktan sonra kullanılabilir.';
            return;
        }
        try { await video.requestPictureInPicture(); } catch (_) { status.textContent = 'PiP başlatılamadı.'; }
    };
    const toggleLists = () => {
        menu.classList.toggle('is-lists-open');
        status.textContent = menu.classList.contains('is-lists-open') ? 'Liste seç' : '';
    };
    const actionButtons = [
        joystickButton('Oynat', 'fa-play', play, 'is-primary tv-joystick-up'),
        joystickButton('Bölümler', 'fa-list', episodes, 'tv-joystick-left'),
        joystickButton('PiP', 'fa-clone', pip, 'tv-joystick-right'),
        joystickButton('Listeler', 'fa-heart', toggleLists, 'tv-joystick-down'),
    ];
    actions.append(...actionButtons);

    [['İzlenecek', 'izlenecek'], ['Planlandı', 'planlandi'], ['Takip', 'takip']].forEach(([label, key]) => {
        const button = joystickButton(label, 'fa-bookmark', () => {
            const added = saveList(item, key);
            status.textContent = added ? `${label} listesine eklendi` : `${label} listesinden çıkarıldı`;
        }, 'tv-joystick-list-button');
        lists.appendChild(button);
    });

    let selectedIndex = 0;
    const selectAction = (index) => {
        selectedIndex = (index + actionButtons.length) % actionButtons.length;
        actionButtons.forEach((button, index) => button.classList.toggle('is-selected', index === selectedIndex));
    };
    plus.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        closeActionMenu();
    });
    menu.addEventListener('keydown', (event) => {
        const keyMap = { ArrowUp: 0, ArrowLeft: 1, ArrowRight: 2, ArrowDown: 3 };
        if (event.key in keyMap) {
            event.preventDefault();
            event.stopPropagation();
            selectAction(keyMap[event.key]);
            actionButtons[selectedIndex].focus();
        } else if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            actionButtons[selectedIndex].click();
        } else if (event.key === 'Escape') {
            event.preventDefault();
            closeActionMenu();
            card.focus();
        }
    });
    menu.addEventListener('click', (event) => event.stopPropagation());
    card.appendChild(menu);
    selectAction(0);
    plus.focus();
}

function showEpisodePicker(item, episodes) {
    closeEpisodePicker();
    const overlay = document.createElement('div');
    overlay.className = 'tv-episode-picker';
    overlay.innerHTML = `
        <div class="tv-episode-backdrop"></div>
        <section class="tv-episode-panel" role="dialog" aria-modal="true" aria-label="Bölüm seç">
            <button class="tv-episode-close" type="button" aria-label="Kapat">×</button>
            <div class="tv-episode-kicker">BÖLÜM SEÇ</div>
            <h2>${escapeHtml(item.title)}</h2>
            <div class="tv-episode-list"></div>
        </section>`;
    const list = overlay.querySelector('.tv-episode-list');
    episodes.forEach((episode, index) => {
        const link = document.createElement('a');
        link.className = 'tv-episode-item';
        link.href = watchUrl(item, episode);
        link.innerHTML = `<span class="tv-episode-number">${escapeHtml(episode.episode || index + 1)}</span><span>${escapeHtml(episode.title || `Bölüm ${index + 1}`)}</span>`;
        list.appendChild(link);
    });
    overlay.addEventListener('click', (event) => {
        if (event.target.classList.contains('tv-episode-backdrop') || event.target.closest('.tv-episode-close')) closeEpisodePicker();
    });
    document.body.appendChild(overlay);
    list.querySelector('.tv-episode-item')?.focus();
}

async function openSeries(item) {
    const card = item.card;
    card.classList.add('is-loading');
    try {
        const response = await fetch(`/api/v1/load_item?plugin=${encode(item.plugin)}&encoded_url=${encode(item.url)}`);
        const payload = await response.json();
        const content = payload?.result || payload;
        const episodes = Array.isArray(content?.episodes) ? content.episodes.filter(Boolean) : [];
        if (episodes.length) showEpisodePicker(item, episodes);
        else window.location.href = `/icerik/${encode(item.plugin)}?url=${encode(item.url)}`;
    } catch (_) {
        window.location.href = `/icerik/${encode(item.plugin)}?url=${encode(item.url)}`;
    } finally {
        card.classList.remove('is-loading');
    }
}

function readItem(card) {
    return {
        card,
        plugin: card.dataset.plugin || '',
        url: card.dataset.contentUrl || '',
        title: card.dataset.contentTitle || '',
        poster: card.dataset.poster || '',
        mediaType: card.dataset.mediaType || 'movie',
    };
}

document.addEventListener('DOMContentLoaded', () => {
    // data-tv-item="true" → ana sayfa davranışı (tek tık odaklar, açmak için OK/çift-tık).
    // data-tv-item="link" → liste sayfaları (arama/kategori): tek tık href'i AÇAR;
    //   sadece basılı-tutma menüsü + odak çerçevesi eklenir, gezinme bozulmaz.
    document.querySelectorAll('[data-tv-item]').forEach((card) => {
        const linkMode = card.dataset.tvItem === 'link';
        let longPressTimer = 0;
        let longPressTriggered = false;

        card.addEventListener('click', (event) => {
            if (longPressTriggered) {
                event.preventDefault();
                longPressTriggered = false;
                return;
            }
            if (linkMode) return; // liste sayfası: anchor'ın normal gezinmesi çalışsın
            const item = readItem(card);
            // Doğrudan içeriğe git
            if (card.getAttribute('href')) {
                return; // anchor normal gezinmeyi yapsın
            }
            event.preventDefault();
            if (item.mediaType === 'serie') {
                window.location.href = `/icerik/${encode(item.plugin)}?url=${encode(item.url)}`;
            } else {
                window.location.href = watchUrl(item);
            }
        });
        if (!linkMode) {
            card.addEventListener('keydown', (event) => {
                if (event.key !== 'Enter') return;
                event.preventDefault();
                const item = readItem(card);
                const href = card.getAttribute('href');
                if (href) {
                    window.location.href = href;
                } else if (item.mediaType === 'serie') {
                    window.location.href = `/icerik/${encode(item.plugin)}?url=${encode(item.url)}`;
                } else {
                    window.location.href = watchUrl(item);
                }
            });
        }
        card.addEventListener('pointerdown', (event) => {
            if (event.button !== 0 && event.pointerType === 'mouse') return;
            longPressTriggered = false;
            window.clearTimeout(longPressTimer);
            longPressTimer = window.setTimeout(() => {
                longPressTriggered = true;
                showActionMenu(card, readItem(card));
            }, LONG_PRESS_MS);
        });
        ['pointerup', 'pointercancel', 'pointerleave'].forEach((eventName) => {
            card.addEventListener(eventName, () => window.clearTimeout(longPressTimer));
        });
    });
});
