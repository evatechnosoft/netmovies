// Sade no-op service worker: reklam/push içermez, yalnızca PWA kurulabilirliği sağlar.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));
