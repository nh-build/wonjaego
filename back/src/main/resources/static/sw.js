const CACHE_NAME = 'wonjaego-static-v1';

function isCacheableStaticAsset(pathname) {
    return pathname === '/manifest.json' || pathname.startsWith('/icons/');
}

self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);

    // Only ever cache same-origin GETs for known static assets — page renders,
    // form submits, and any future API responses always go straight to the network.
    if (request.method !== 'GET' || url.origin !== self.location.origin || !isCacheableStaticAsset(url.pathname)) {
        return;
    }

    event.respondWith(
        caches.open(CACHE_NAME).then((cache) =>
            cache.match(request).then((cached) => {
                if (cached) {
                    return cached;
                }
                return fetch(request).then((response) => {
                    cache.put(request, response.clone());
                    return response;
                });
            })
        )
    );
});
