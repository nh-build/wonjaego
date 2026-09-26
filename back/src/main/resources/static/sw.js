const CACHE_VERSION = 'v2';
const STATIC_CACHE_NAME = `wonjaego-static-${CACHE_VERSION}`;

const STATIC_ASSET_PATTERN = /\.(?:css|js|png|jpg|jpeg|svg|webp|gif|ico|woff2?|ttf|otf)$/;

function isCacheableStaticAsset(pathname) {
    return pathname === '/manifest.json' || STATIC_ASSET_PATTERN.test(pathname);
}

function isAuthPath(pathname) {
    return pathname === '/login' || pathname === '/signup' || pathname === '/logout';
}

self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((names) => Promise.all(
                names.filter((name) => name !== STATIC_CACHE_NAME).map((name) => caches.delete(name))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);

    if (request.method !== 'GET' || url.origin !== self.location.origin) {
        return;
    }

    // Auth routes: never intercepted, always a plain network request — no stale
    // cached login/signup screen can ever be served.
    if (isAuthPath(url.pathname)) {
        return;
    }

    // HTML navigations: network-first, so a redeploy is visible immediately.
    // Falls back to the last cached copy of this same URL only when offline —
    // there is no generic offline page, so it can never mask a different route.
    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request)
                .then((response) => {
                    const copy = response.clone();
                    caches.open(STATIC_CACHE_NAME).then((cache) => cache.put(request, copy));
                    return response;
                })
                .catch(() => caches.match(request))
        );
        return;
    }

    // Static assets (css/js/images/fonts/manifest): stale-while-revalidate.
    if (isCacheableStaticAsset(url.pathname)) {
        event.respondWith(
            caches.open(STATIC_CACHE_NAME).then((cache) =>
                cache.match(request).then((cached) => {
                    const network = fetch(request).then((response) => {
                        cache.put(request, response.clone());
                        return response;
                    });
                    return cached || network;
                })
            )
        );
    }
});
