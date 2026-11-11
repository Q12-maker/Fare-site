/* Fare Movimiento v1: animaciones tipo "web moderna" sin librerías ni React.
   Se pega una vez al final del <body> y se activa con atributos en el HTML:
     data-fm-luz="#c9a45c,#dcdce1"  en una sección: fondo de luz animada (shader WebGL) que sigue el dedo/cursor
     data-fm-revelar                en un título: las palabras entran una a una desde el desenfoque
     data-fm-tilt                   en una imagen o tarjeta: se inclina en 3D con reflejo de luz (principio Fare 8)
     data-fm-iman                   en un botón: se acerca al cursor como un imán
     data-fm-contar="500000"        en un número: cuenta desde 0 al aparecer (data-fm-prefijo / data-fm-sufijo opcionales)
   Respeta "reducir movimiento" del sistema, se pausa fuera de pantalla y no hace nada si el navegador no puede. */
(function () {
  'use strict';
  if (window.FareMovimiento) return;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var fino = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  var css = [
    '.fm-luz{position:absolute;inset:0;width:100%;height:100%;z-index:0;pointer-events:none;mix-blend-mode:screen;opacity:0;transition:opacity 1.6s ease}',
    '.fm-luz.fm-on{opacity:var(--fm-luz-opacidad,.75)}',
    '[data-fm-luz]>:not(.fm-luz){position:relative;z-index:1}',
    '.fm-p{display:inline-block;opacity:0;filter:blur(10px);transform:translate3d(0,.45em,0);transition:opacity .9s cubic-bezier(.2,.7,.2,1),filter .9s cubic-bezier(.2,.7,.2,1),transform .9s cubic-bezier(.2,.7,.2,1);transition-delay:calc(var(--fm-i,0)*70ms)}',
    '.fm-visto .fm-p{opacity:1;filter:none;transform:none}',
    '[data-fm-tilt]{transform-style:preserve-3d;transition:transform .5s cubic-bezier(.2,.7,.2,1)}',
    '[data-fm-tilt].fm-sobre{transition:transform .08s linear}',
    '.fm-glare{position:absolute;inset:0;border-radius:inherit;pointer-events:none;opacity:0;transition:opacity .4s;background:radial-gradient(circle at var(--fm-gx,50%) var(--fm-gy,50%),rgba(255,255,255,.35),rgba(255,255,255,0) 55%);mix-blend-mode:overlay}',
    '.fm-sobre>.fm-glare{opacity:1}',
    '[data-fm-iman]{transition:transform .45s cubic-bezier(.2,.7,.2,1)}'
  ].join('\n');
  var st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);

  // Al verse una vez en pantalla se ejecuta `fn` (para revelar y contar)
  function alVer(el, fn) {
    if (!('IntersectionObserver' in window)) { fn(); return; }
    var io = new IntersectionObserver(function (e) { if (e[0].isIntersecting) { io.disconnect(); fn(); } }, { threshold: .35 });
    io.observe(el);
  }

  /* ---------- Revelar títulos palabra por palabra ---------- */
  // Los textos con degradado (background-clip: text) entran como un bloque para no romper el degradado
  function partir(nodo, cuenta) {
    Array.prototype.slice.call(nodo.childNodes).forEach(function (n) {
      if (n.nodeType === 3) {
        var trozos = n.textContent.split(/(\s+)/), frag = document.createDocumentFragment();
        trozos.forEach(function (t) {
          if (!t) return;
          if (/^\s+$/.test(t)) { frag.appendChild(document.createTextNode(t)); return; }
          var s = document.createElement('span'); s.className = 'fm-p'; s.textContent = t;
          s.style.setProperty('--fm-i', cuenta.n++); frag.appendChild(s);
        });
        n.parentNode.replaceChild(frag, n);
      } else if (n.nodeType === 1) {
        var cs = getComputedStyle(n);
        if (/text/.test(cs.backgroundClip + cs.webkitBackgroundClip) || n.tagName === 'BR' || n.tagName === 'SMALL') {
          if (n.tagName !== 'BR') { n.classList.add('fm-p'); n.style.setProperty('--fm-i', cuenta.n++); }
        } else partir(n, cuenta);
      }
    });
  }
  function revelar(el) {
    if (el.__fm) return; el.__fm = 1;
    if (reduce) return;
    partir(el, { n: 0 });
    alVer(el, function () { el.classList.add('fm-visto'); });
  }

  /* ---------- Tilt 3D con reflejo ---------- */
  function tilt(el) {
    if (el.__fm || reduce || !fino) return; el.__fm = 1;
    var max = parseFloat(el.getAttribute('data-fm-tilt')) || 10;
    // Una imagen no puede tener hijos: el reflejo va en el padre si la imagen está sola
    var g = document.createElement('span'); g.className = 'fm-glare';
    if (el.tagName !== 'IMG') { if (getComputedStyle(el).position === 'static') el.style.position = 'relative'; el.appendChild(g); }
    el.addEventListener('pointermove', function (e) {
      var r = el.getBoundingClientRect(), x = (e.clientX - r.left) / r.width, y = (e.clientY - r.top) / r.height;
      el.classList.add('fm-sobre');
      el.style.transform = 'perspective(900px) rotateX(' + ((.5 - y) * max).toFixed(2) + 'deg) rotateY(' + ((x - .5) * max).toFixed(2) + 'deg)';
      g.style.setProperty('--fm-gx', (x * 100) + '%'); g.style.setProperty('--fm-gy', (y * 100) + '%');
    });
    el.addEventListener('pointerleave', function () { el.classList.remove('fm-sobre'); el.style.transform = ''; });
  }

  /* ---------- Botones imán ---------- */
  function iman(el) {
    if (el.__fm || reduce || !fino) return; el.__fm = 1;
    var f = parseFloat(el.getAttribute('data-fm-iman')) || .3;
    el.addEventListener('pointermove', function (e) {
      var r = el.getBoundingClientRect();
      el.style.transform = 'translate3d(' + ((e.clientX - r.left - r.width / 2) * f).toFixed(1) + 'px,' + ((e.clientY - r.top - r.height / 2) * f).toFixed(1) + 'px,0)';
    });
    el.addEventListener('pointerleave', function () { el.style.transform = ''; });
  }

  /* ---------- Contadores ---------- */
  function contar(el) {
    if (el.__fm) return; el.__fm = 1;
    var fin = parseFloat(el.getAttribute('data-fm-contar')) || 0, pre = el.getAttribute('data-fm-prefijo') || '', suf = el.getAttribute('data-fm-sufijo') || '';
    function pinta(v) { el.textContent = pre + Math.round(v).toLocaleString('es-CO') + suf; }
    if (reduce) { pinta(fin); return; }
    pinta(0);
    alVer(el, function () {
      var t0 = performance.now(), dur = 1600;
      (function paso(t) { var k = Math.min(1, (t - t0) / dur); pinta(fin * (1 - Math.pow(1 - k, 3))); if (k < 1) requestAnimationFrame(paso); })(t0);
    });
  }

  /* ---------- Luz animada (shader WebGL) ---------- */
  // Haces de luz que fluyen en diagonal con los colores de la marca, como luz entrando por una ventana.
  var FRAG = [
    'precision mediump float;',
    'uniform vec2 r; uniform float t; uniform vec2 m; uniform vec3 a; uniform vec3 b;',
    'float h(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}',
    'float n(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(h(i),h(i+vec2(1,0)),f.x),mix(h(i+vec2(0,1)),h(i+vec2(1,1)),f.x),f.y);}',
    'float fbm(vec2 p){float v=0.,s=.5;for(int i=0;i<4;i++){v+=s*n(p);p*=2.03;s*=.5;}return v;}',
    'void main(){',
    ' vec2 uv=gl_FragCoord.xy/r; vec2 p=uv; p.x*=r.x/r.y;',
    ' float d=dot(p-vec2(m.x*r.x/r.y,m.y),normalize(vec2(1.,.55)));',
    ' float w=fbm(vec2(d*3.,t*.08)+fbm(p*1.6+t*.05));',
    ' float rayos=pow(.5+.5*sin(d*9.+w*6.-t*.35),6.)*.9+pow(.5+.5*sin(d*23.-w*9.+t*.22),14.)*.6;',
    ' float halo=exp(-3.2*length(p-vec2(m.x*r.x/r.y,m.y)));',
    ' vec3 c=mix(a,b,smoothstep(.2,.8,w))*rayos*(.35+.65*w)+a*halo*.55;',
    ' float vig=smoothstep(1.25,.25,length(uv-vec2(.5)));',
    ' c+=(h(gl_FragCoord.xy+t)-.5)*.03;',
    ' gl_FragColor=vec4(c*vig,1.);',
    '}'
  ].join('\n');
  function rgb(hex) { hex = hex.trim().replace('#', ''); if (hex.length === 3) hex = hex.replace(/./g, '$&$&'); var v = parseInt(hex, 16); return [(v >> 16 & 255) / 255, (v >> 8 & 255) / 255, (v & 255) / 255]; }
  function luz(sec) {
    if (sec.__fm || reduce) return; sec.__fm = 1;
    var cols = (sec.getAttribute('data-fm-luz') || '#c9a45c,#dcdce1').split(',');
    var cv = document.createElement('canvas'); cv.className = 'fm-luz'; cv.setAttribute('aria-hidden', 'true');
    var gl = cv.getContext('webgl', { alpha: false, antialias: false, powerPreference: 'low-power' });
    if (!gl) return;
    function sh(tipo, src) { var s = gl.createShader(tipo); gl.shaderSource(s, src); gl.compileShader(s); return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null; }
    var vs = sh(gl.VERTEX_SHADER, 'attribute vec2 p;void main(){gl_Position=vec4(p,0.,1.);}'), fs = sh(gl.FRAGMENT_SHADER, FRAG);
    if (!vs || !fs) return;
    var pr = gl.createProgram(); gl.attachShader(pr, vs); gl.attachShader(pr, fs); gl.linkProgram(pr);
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) return;
    gl.useProgram(pr);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    var lp = gl.getAttribLocation(pr, 'p'); gl.enableVertexAttribArray(lp); gl.vertexAttribPointer(lp, 2, gl.FLOAT, false, 0, 0);
    var U = {}; ['r', 't', 'm', 'a', 'b'].forEach(function (k) { U[k] = gl.getUniformLocation(pr, k); });
    gl.uniform3fv(U.a, rgb(cols[0])); gl.uniform3fv(U.b, rgb(cols[1] || cols[0]));
    sec.insertBefore(cv, sec.firstChild);

    // Media resolución: se ve igual de suave (es luz difusa) y gasta cuatro veces menos batería
    var escala = Math.min(window.devicePixelRatio || 1, 2) * .5;
    function medir() { cv.width = Math.max(1, cv.clientWidth * escala | 0); cv.height = Math.max(1, cv.clientHeight * escala | 0); gl.viewport(0, 0, cv.width, cv.height); gl.uniform2f(U.r, cv.width, cv.height); }
    medir(); window.addEventListener('resize', medir);

    var mx = .8, my = .75, ox = mx, oy = my, visible = true, corre = false, t0 = performance.now(), ult = 0;
    sec.addEventListener('pointermove', function (e) { var r = cv.getBoundingClientRect(); mx = (e.clientX - r.left) / r.width; my = 1 - (e.clientY - r.top) / r.height; }, { passive: true });
    function cuadro(t) {
      corre = visible && !document.hidden;
      if (!corre) return;
      requestAnimationFrame(cuadro);
      if (t - ult < 32) return; // ~30 cuadros por segundo bastan para luz lenta
      ult = t;
      ox += (mx - ox) * .04; oy += (my - oy) * .04;
      gl.uniform1f(U.t, (t - t0) / 1000); gl.uniform2f(U.m, ox, oy);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      if (!cv.classList.contains('fm-on')) cv.classList.add('fm-on');
    }
    function arranca() { if (!corre) requestAnimationFrame(cuadro); }
    if ('IntersectionObserver' in window) new IntersectionObserver(function (e) { visible = e[0].isIntersecting; if (visible) arranca(); }).observe(sec);
    document.addEventListener('visibilitychange', arranca);
    arranca();
  }

  function aplicar(raiz) {
    raiz = raiz || document;
    raiz.querySelectorAll('[data-fm-luz]').forEach(luz);
    raiz.querySelectorAll('[data-fm-revelar]').forEach(revelar);
    raiz.querySelectorAll('[data-fm-tilt]').forEach(tilt);
    raiz.querySelectorAll('[data-fm-iman]').forEach(iman);
    raiz.querySelectorAll('[data-fm-contar]').forEach(contar);
  }
  window.FareMovimiento = { aplicar: aplicar, version: 1 };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { aplicar(); }); else aplicar();
})();
