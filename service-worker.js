
const CACHE='depth-v49';
self.addEventListener('install',e=>{
 self.skipWaiting();
 e.waitUntil(caches.open(CACHE).then(c=>c.addAll(['./','./index.html'])));
});
self.addEventListener('activate',e=>clients.claim());
self.addEventListener('fetch',e=>{
 e.respondWith(caches.match(e.request).then(r=>r||fetch(e.request)));
});
