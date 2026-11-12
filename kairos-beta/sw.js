// Kairos Beta: al publicar una versión nueva, subir este número.
const C='kairos-beta-v2',F=['./','index.html','manifest.webmanifest','kairos-logo.svg','kairos-icono.svg','fare-movimiento.js','icon-192.png','icon-512.png','fonts/fonts.css','fonts/cinzel-500.woff2','fonts/jetbrains-mono-1.woff2','fonts/manrope-2.woff2'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(F.map(u=>new Request(u,{cache:'reload'})))));self.skipWaiting()});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C&&/^kairos-beta-/.test(x)).map(x=>caches.delete(x)))));self.clients.claim()});
// Red primero para la página (así siempre llega lo último); caché si no hay internet.
self.addEventListener('fetch',e=>{if(e.request.method!=='GET'||new URL(e.request.url).origin!==location.origin)return;
  e.respondWith(fetch(e.request).then(r=>{if(r.ok){const cp=r.clone();caches.open(C).then(c=>c.put(e.request,cp))}return r}).catch(()=>caches.match(e.request).then(r=>r||caches.match('index.html'))))});
