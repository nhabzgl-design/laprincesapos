/* Service worker ng La Princesa Aquaspin POS. Palitan ang VERSION kapag may bagong update para mag-refresh ang cache. */
var VERSION='lp-pos-v2';
var CORE=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png','./icon-maskable-512.png','./apple-touch-icon.png'];
self.addEventListener('install',function(e){
  e.waitUntil(caches.open(VERSION).then(function(c){return Promise.all(CORE.map(function(u){return c.add(u).catch(function(){})}))}).then(function(){return self.skipWaiting()}));
});
self.addEventListener('activate',function(e){
  e.waitUntil(caches.keys().then(function(ks){return Promise.all(ks.filter(function(k){return k!==VERSION}).map(function(k){return caches.delete(k)}))}).then(function(){return self.clients.claim()}));
});
self.addEventListener('fetch',function(e){
  var r=e.request;
  if(r.method!=='GET')return;
  var u=new URL(r.url);
  var same=u.origin===self.location.origin;
  var sdk=u.hostname==='www.gstatic.com'&&u.pathname.indexOf('/firebasejs/')===0;
  if(!same&&!sdk)return; /* Firestore at Auth requests ay hindi ginagalaw */
  if(r.mode==='navigate'||(same&&/\.html?$/.test(u.pathname))){
    /* Network muna para laging bago ang POS; kapag offline, gamitin ang naka-save */
    e.respondWith(fetch(r).then(function(res){var cp=res.clone();caches.open(VERSION).then(function(c){c.put('./index.html',cp)});return res}).catch(function(){return caches.match('./index.html').then(function(m){return m||caches.match('./')})}));
    return;
  }
  e.respondWith(caches.match(r).then(function(m){
    var net=fetch(r).then(function(res){if(res&&res.ok){var cp=res.clone();caches.open(VERSION).then(function(c){c.put(r,cp)})}return res}).catch(function(){return m});
    return m||net;
  }));
});
