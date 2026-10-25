// Offline cache. Bump VERSION on every release so phones pick up the new files.
const VERSION = 'nexsky-web-0.1.0';
const CORE = [
  './', 'index.html', 'app.css', 'manifest.webmanifest',
  'js/app.js', 'js/engine.js', 'js/moon.js', 'js/gear.js', 'js/ui.js', 'js/icons.js', 'js/state.js',
  'js/search.js', 'js/sky.js', 'js/planner.js', 'js/map.js', 'js/about.js',
  'vendor/astronomy.browser.min.js', 'vendor/leaflet/leaflet.js', 'vendor/leaflet/leaflet.css',
  'data/objects.json', 'data/constellations.json', 'data/stars.bin', 'data/moon_map.jpg',
  'data/inter_regular.otf', 'data/inter_medium.otf', 'data/inter_semibold.otf', 'data/inter_bold.otf', 'data/inter_extrabold.otf',
  'icons/icon-192.png', 'icons/icon-512.png', 'icons/apple-touch-icon.png',
];
self.addEventListener('install', e => { self.skipWaiting(); e.waitUntil(caches.open(VERSION).then(c => c.addAll(CORE))); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== VERSION && k !== 'tiles').map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  // Map tiles: cache what was viewed so the map works with no signal.
  if (url.hostname.endsWith('tile.openstreetmap.org')) {
    e.respondWith(caches.open('tiles').then(async c => {
      const hit = await c.match(e.request);
      if (hit) return hit;
      try { const r = await fetch(e.request); if (r.ok) c.put(e.request, r.clone()); return r; } catch { return hit || Response.error(); }
    }));
    return;
  }
  if (url.origin !== location.origin) return;
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then(hit => hit || fetch(e.request)));
});
