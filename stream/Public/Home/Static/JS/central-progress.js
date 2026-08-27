// Sync player progress to the server so every device can resume the same title.
document.addEventListener('DOMContentLoaded', () => {
    const video = document.getElementById('video-player');
    const meta = document.getElementById('video-links-data');
    if (!video || !meta) return;

    let timer = null;
    const save = () => {
        if (!Number.isFinite(video.duration) || video.duration < 60 || video.currentTime < 10) return;
        const encodedTitle = meta.dataset.contentTitle || '';
        let title = document.title;
        try { title = encodedTitle ? decodeURIComponent(encodedTitle) : title; } catch (_) { /* keep document title */ }
        fetch('/api/v1/progress', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            keepalive: true,
            body: JSON.stringify({
                title,
                content_url: meta.dataset.contentUrl || '',
                poster: meta.dataset.posterUrl || '',
                plugin: meta.dataset.pluginName || '',
                media_type: meta.dataset.season || meta.dataset.episode ? 'serie' : 'movie',
                episode: meta.dataset.season && meta.dataset.episode ? `S${meta.dataset.season} E${meta.dataset.episode}` : '',
                position_seconds: Math.floor(video.currentTime),
                duration_seconds: Math.floor(video.duration),
            }),
        }).catch(() => {});
    };

    video.addEventListener('timeupdate', () => {
        if (timer === null) timer = window.setTimeout(() => { timer = null; save(); }, 5000);
    });
    video.addEventListener('pause', save);
    video.addEventListener('ended', save);
});
