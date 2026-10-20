const C='audio-fare-v27',F=['./','index.html','manifest.webmanifest','icon-192.png','icon-512.png','fonts/fonts.css','fonts/bricolage-grotesque-0.woff2','fonts/jetbrains-mono-1.woff2','fonts/manrope-2.woff2'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(F.map(u=>new Request(u,{cache:'reload'})))));self.skipWaiting()});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C&&x!=='af-inbox').map(x=>caches.delete(x)))));self.clients.claim()});
self.addEventListener('fetch',e=>{
  if(e.request.method==='POST'&&new URL(e.request.url).pathname.endsWith('/compartir')){
    e.respondWith((async()=>{try{const fd=await e.request.formData(),c=await caches.open('af-inbox');let i=0;
      for(const f of fd.getAll('audio')){if(!f||!f.size)continue;await c.put('inbox/'+Date.now()+'-'+(i++),new Response(f,{headers:{'content-type':f.type||'audio/mpeg','x-name':encodeURIComponent(f.name||'cancion')}}))}}catch(err){}
      return Response.redirect('./',303)})());return}
  if(e.request.method!=='GET')return;e.respondWith(caches.match(e.request).then(r=>r||fetch(e.request).then(res=>{if(res.ok&&new URL(e.request.url).origin===location.origin){const cp=res.clone();caches.open(C).then(c=>c.put(e.request,cp))}return res}).catch(()=>caches.match('index.html'))))});
