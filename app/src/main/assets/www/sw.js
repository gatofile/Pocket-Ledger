const CACHE='pocket-ledger-shell-v18';
const FILES=['./','./index.html','./styles.css','./app.js','./manifest.json','./manifest-light.json','./manifest-green.json','./manifest-gray.json','./manifest-amoled.json','./icon.svg','./icon-dark.svg','./icon-gray.svg','./icon-amoled.svg','./icon-192.png','./icon-512.png','./icon-light-192.png','./icon-light-512.png','./icon-green-192.png','./icon-green-512.png','./icon-gray-192.png','./icon-gray-512.png','./icon-amoled-192.png','./icon-amoled-512.png'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  event.respondWith(caches.match(event.request).then(cached=>cached||fetch(event.request).then(response=>{
    if(new URL(event.request.url).origin===self.location.origin){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(event.request,copy));}
    return response;
  }).catch(()=>caches.match('./index.html'))));
});
