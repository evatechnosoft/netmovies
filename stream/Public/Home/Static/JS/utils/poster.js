// NetMovies — tek poster URL üreteci (istemci tarafı).
// Sunucudaki Jinja `poster(url, title)` helper'ı ile AYNI sözleşme: zincirin
// tamamı /proxy/image içinde çalışır (kaynak → proxy cache → TMDB → placeholder),
// böylece her ekran kendi onerror zincirini taşımak zorunda kalmaz.

export const posterUrl = (poster, title) => {
    if (poster && (poster.startsWith('/') || poster.startsWith('data:'))) return poster;
    if (!poster && !title) return '';

    let query = 'url=' + encodeURIComponent(poster || '');
    if (title) query += '&title=' + encodeURIComponent(title);
    return '/proxy/image?' + query;
};
