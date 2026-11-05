/* Introducción cinematográfica (~2,4 s). Se muestra una vez por sesión, se puede saltar con un toque
   y no aparece si el dispositivo pide reducir movimiento ni en el panel. */
(function () {
  const root = document.documentElement, C = window.CFG || {};
  try { const p = localStorage.getItem('pal') || C.urbanPalette; if (p && p !== 'amarillo') root.dataset.pal = p; } catch (e) { if (C.urbanPalette && C.urbanPalette !== 'amarillo') root.dataset.pal = C.urbanPalette; }
  function build(el) {
    el.textContent = '';
    const mk = (tag, cls, text) => { const n = document.createElement(tag); n.className = cls; if (text) n.textContent = text; return n; };
    let mark;
    if (C.monogramUrl) { mark = mk('img', 'in-mark'); mark.src = C.monogramUrl; mark.alt = ''; }
    else mark = mk('span', 'in-mark in-ik', 'IK');   // respaldo tipográfico hasta cargar tu monograma real
    el.append(mark, mk('p', 'in-name', C.brandName || 'IKONOSTUDIO'), mk('p', 'in-desc', C.descriptor || "GENTLEMEN'S BARBER"),
      motto(), skip());
    return mark;
  }
  function motto() {   // cada tema muestra su frase
    const p = document.createElement('p'); p.className = 'in-motto';
    const a = document.createElement('span'); a.className = 'm-a'; a.textContent = C.heroTitle || 'El estilo habla por vos.';
    const b = document.createElement('span'); b.className = 'm-b'; b.textContent = C.heroTitleUrban || 'No es solo un corte. Es tu estilo.';
    p.append(a, b); return p;
  }
  function skip() {
    const b = document.createElement('button'); b.type = 'button'; b.className = 'in-skip'; b.textContent = 'Saltar';
    b.setAttribute('aria-label', 'Saltar la introducción'); return b;
  }
  function run(force) {
    let el = document.getElementById('intro');
    if (force) { if (el) el.remove(); el = document.createElement('div'); el.id = 'intro'; document.body.append(el); root.classList.remove('no-intro'); }
    if (!el) return;
    if (!force && (root.classList.contains('no-intro') || C.intro === false)) { el.remove(); root.classList.remove('intro-on'); return; }
    root.classList.add('intro-on');
    const mark = build(el);
    let done = false, t1, t2;
    const end = () => { if (done) return; done = true; clearTimeout(t1); clearTimeout(t2); el.remove(); root.classList.remove('intro-on'); try { sessionStorage.setItem('intro', '1'); } catch (e) { /* ok */ } };
    const leave = () => {   // el monograma viaja hasta el logo del encabezado y el fondo se disuelve
      el.classList.add('out');
      const brand = document.querySelector('header.bar .brand');
      if (brand) {
        const a = mark.getBoundingClientRect(), b = brand.getBoundingClientRect();
        mark.style.animation = 'none'; mark.style.opacity = '1'; mark.style.transform = 'none'; void mark.offsetWidth;
        const s = Math.min(1, Math.max(0.2, (b.height * 1.2) / a.height));
        mark.style.transition = 'transform .75s cubic-bezier(.65,0,.35,1), opacity .45s ease .3s';
        mark.style.transform = `translate(${b.left + 16 - (a.left + a.width / 2)}px, ${b.top + b.height / 2 - (a.top + a.height / 2)}px) scale(${s})`;
        mark.style.opacity = '0';
      }
      root.classList.remove('intro-on');
      t2 = setTimeout(end, 850);
    };
    t1 = setTimeout(leave, 4400);
    el.addEventListener('click', () => { clearTimeout(t1); leave(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape' || e.key === 'Enter') { clearTimeout(t1); leave(); } }, { once: true });
  }
  window.runIntro = run;
  run(false);
})();
