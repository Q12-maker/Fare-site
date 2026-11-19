/* Logo Kairos VIVO (Juan, 18-nov-2026): las tres agujas del reloj de arena marcan la HORA REAL de Colombia
   (Mariquita, UTC-5) y se mueven en tiempo real. Uso: <img src="logo-kairos-vivo.svg" data-kairos-reloj>
   El script cambia la imagen por el SVG en línea (una <img> no se puede animar) y gira las agujas. */
(function () {
  "use strict";
  var quieto = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  function horaColombia() {
    var d = new Date(), t = d.getTime() - 5 * 3600e3, u = new Date(t);
    return { h: u.getUTCHours(), m: u.getUTCMinutes(), s: u.getUTCSeconds(), ms: u.getUTCMilliseconds() };
  }
  var relojes = [];
  function girar(r, primera) {
    var t = horaColombia(), seg = t.s, min = t.m + seg / 60, hor = (t.h % 12) + min / 60;
    // Ángulos acumulados: ninguna aguja «da la vuelta hacia atrás» al pasar por las 12
    ["s", "m", "h"].forEach(function (k) {
      var a = { s: seg * 6, m: min * 6, h: hor * 30 }[k], prev = r["a" + k];
      if (!primera && prev != null) { a += Math.round((prev - a) / 360) * 360; if (a < prev - 0.01) a += 360; }
      r["a" + k] = a; r[k].style.transform = "rotate(" + a + "deg)";
    });
  }
  function tic() {
    relojes.forEach(function (r) { girar(r, false); });
    var t = horaColombia(); setTimeout(tic, 1000 - t.ms + 5);
  }
  function montar(img) {
    fetch(img.getAttribute("src")).then(function (r) { return r.text(); }).then(function (txt) {
      var caja = document.createElement("div"); caja.innerHTML = txt;
      var svg = caja.querySelector("svg"); if (!svg) return;
      svg.removeAttribute("width"); svg.removeAttribute("height");
      if (img.className) svg.setAttribute("class", img.className);
      svg.setAttribute("aria-hidden", "true"); svg.setAttribute("focusable", "false");
      svg.style.cssText = img.style.cssText;
      var r = { h: svg.querySelector(".kr-h"), m: svg.querySelector(".kr-m"), s: svg.querySelector(".kr-s") };
      if (!r.h || !r.m || !r.s) return;
      girar(r, true);
      if (!quieto) {
        // el segundero «salta» como un reloj de verdad; minutero y horario avanzan suave
        r.s.style.transition = "transform .32s cubic-bezier(.4,2.1,.55,1)";
        r.m.style.transition = r.h.style.transition = "transform 1s linear";
      }
      img.replaceWith(svg); relojes.push(r);
      if (relojes.length === 1) tic();
    }).catch(function () { /* sin red: se queda la imagen fija */ });
  }
  function iniciar() { document.querySelectorAll("img[data-kairos-reloj]").forEach(montar); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar); else iniciar();
})();
