// 최소 서비스워커 — 홈 화면 설치(바로가기 메뉴 포함) 조건을 충족시키기 위한 용도입니다.
// 별도의 오프라인 캐싱은 하지 않고, 네트워크 요청을 그대로 통과시킵니다.
self.addEventListener('install', (e) => { self.skipWaiting(); });
self.addEventListener('activate', (e) => { self.clients.claim(); });
self.addEventListener('fetch', (e) => {
  e.respondWith(
    fetch(e.request).catch(() => new Response('', { status: 503, statusText: 'offline' }))
  );
});
