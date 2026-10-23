'use strict';
/* Barber Studio: sitio público + panel. Todo viene de la API real (sin datos simulados). */
const CFG = window.CFG || {};
const LOCAL = ['localhost', '127.0.0.1'].includes(location.hostname);
const API = LOCAL ? (CFG.devApi || 'http://localhost:8080') : '';
const SLUG = LOCAL ? CFG.devSlug : CFG.slug;
const TZ = 'America/Montevideo', OFF = '-03:00';   // Uruguay no usa horario de verano

const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = n => '$ ' + Number(n).toLocaleString('es-UY');
const hm = t => new Date(t).toLocaleTimeString('es-UY', { hour: '2-digit', minute: '2-digit', timeZone: TZ });
const dlong = t => new Date(t).toLocaleDateString('es-UY', { weekday: 'long', day: 'numeric', month: 'long', timeZone: TZ });
const ymd = d => new Date(d).toLocaleDateString('en-CA', { timeZone: TZ });
const pad = n => String(n).padStart(2, '0');
const toISO = (d, t) => new Date(`${d}T${t}:00${OFF}`).toISOString();
const t5 = t => (t ? String(t).slice(0, 5) : '');
const imgUrl = u => (u && u.startsWith('/') ? API + u : u);
const WD = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
const REASONS = { FERIADO: 'Feriado', VACACIONES: 'Vacaciones', CIERRE_EXTRAORDINARIO: 'Cierre extraordinario', MANTENIMIENTO: 'Reforma o mantenimiento', PERSONAL: 'Día personal', LICENCIA: 'Licencia', OTRO: 'Otro motivo' };
const ST = { PENDING: 'Pendiente', CONFIRMED: 'Confirmado', COMPLETED: 'Completado', CANCELLED: 'Cancelado' };
const DAYLBL = { AVAILABLE: 'Disponible', FULL: 'Completo', CLOSED: 'Cerrado', FAR: 'Todavía no disponible', PAST: '' };
const CATS = [['CORTE', 'Cortes'], ['COLOR', 'Color'], ['BARBA', 'Barba'], ['TATUAJE', 'Tatuajes']];
const TN = { light: 'Blanco', dark: 'Azul', street: 'Neón' }, TO = ['light', 'dark', 'street'];
try { const t = localStorage.getItem('theme'); if (TO.includes(t)) document.documentElement.dataset.theme = t; } catch (e) { /* sin almacenamiento */ }

async function api(path, o = {}) {
  const init = { method: o.method || 'GET', credentials: 'include', headers: {} };
  if (o.body !== undefined) { init.headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(o.body); }
  if (o.form) init.body = o.form;
  let r;
  try { r = await fetch(API + path, init); } catch (e) {
    const x = new Error('No se pudo conectar con el servidor. Si recién lo abriste, esperá un minuto y volvé a intentar.'); x.network = true; throw x;
  }
  if (r.status === 204) return null;
  if ([502, 503, 504].includes(r.status)) {   // el servidor gratuito se duerme: Netlify responde así mientras despierta
    const x = new Error('El servidor está despertando. Esperá un momento…'); x.network = true; throw x;
  }
  const t = await r.text(); let d = null; try { d = t ? JSON.parse(t) : null; } catch (e) { /* no era JSON */ }
  if (!r.ok) {
    const x = new Error((d && d.errors && Object.values(d.errors)[0]) || (d && d.detail) || ('Error ' + r.status));
    x.status = r.status; x.data = d; throw x;
  }
  return d;
}
async function retry(fn, n = 10) {
  for (let i = 0; ; i++) { try { return await fn(); } catch (e) { if (!e.network || i >= n) throw e; await new Promise(r => setTimeout(r, 5000)); } }
}

const S = { mode: 'public', shop: null, services: [], barbers: [], styles: [], items: [], list: [], me: null, meChecked: false, cat: '', sty: '', q: '', loading: true, err: null, ref: null };
const P = { tab: 'agenda', date: ymd(new Date()), barber: '', q: '', barbers: [], services: [], items: [], styles: [] };
const fresh = () => { const n = new Date(); return { svc: null, barber: null, y: n.getFullYear(), m: n.getMonth(), days: {}, day: null, slots: [], slot: null, f: {}, done: null, busy: false, tokD: 0, tokS: 0 }; };
let B = fresh();

const dlg = $('#dlg');
const dopen = (html, cls) => { dlg.className = cls || ''; dlg.innerHTML = html; if (!dlg.open) dlg.showModal(); };
const dclose = () => { if (dlg.open) dlg.close(); };
let pending = null;
const ask = (msg, fn, label) => { pending = fn; dopen(`<h2>${esc(msg)}</h2><p class="mu">Esta acción no se puede deshacer.</p><div class="row"><button class="btn" data-a="yes">${label || 'Sí, continuar'}</button><button class="btn ghost" data-a="close">Volver</button></div>`); };
const toast = (m, bad) => { const t = $('#toast'); t.textContent = m; t.className = bad ? 'bad' : ''; clearTimeout(toast.k); toast.k = setTimeout(() => { t.textContent = ''; }, 4500); };
const fv = f => Object.fromEntries(new FormData(f));

/* ============================ SITIO PÚBLICO ============================ */
function header() {
  const th = document.documentElement.dataset.theme;
  return `<header class="bar"><span class="brand">${esc(S.shop ? S.shop.name : '')}</span><nav aria-label="Principal">${S.mode === 'public'
    ? '<button class="chip" data-a="go" data-t="reservar">Reservar</button><button class="chip" data-a="go" data-t="portfolio">Portfolio</button><a class="chip" href="#/panel">Panel</a>'
    : '<a class="chip" href="#/">Ver sitio</a>'}<button class="chip" data-a="theme">Tema: ${TN[th] || 'Auto'}</button></nav></header>`;
}

function render() {
  S.mode = location.hash.startsWith('#/panel') ? 'panel' : 'public';
  $('#app').innerHTML = header() + `<main>${S.mode === 'public' ? pubHTML() : panelShell()}</main>` + (S.mode === 'public' && S.shop ? footer() : '');
  if (S.mode === 'public' && !S.loading && !S.err) { renderBook(); drawGallery(); }
  if (S.mode === 'panel') loadPanel();
}

function footer() {
  const sh = S.shop;
  return `<footer>${sh.address ? esc(sh.address) + '. ' : ''}<a href="${esc(sh.whatsappUrl)}" target="_blank" rel="noopener" style="color:inherit">WhatsApp</a>${CFG.instagram ? ` | <a href="${esc(CFG.instagram)}" target="_blank" rel="noopener" style="color:inherit">Instagram</a>` : ''}</footer>`;
}

function pubHTML() {
  if (S.loading) return '<div class="spin">Cargando… si el servidor estaba dormido puede tardar hasta un minuto.</div>';
  if (S.err) return `<div class="empty"><p>${esc(S.err)}</p><button class="btn" data-a="reload">Reintentar</button></div>`;
  const sh = S.shop;
  return `<div class="pole"></div>
 <section class="hero"><h1>${esc(sh.name)}</h1><p>${esc(sh.aboutText || 'Elegí tu servicio, tu barbero y reservá tu turno online.')}</p>
 <div class="row"><button class="btn" data-a="go" data-t="reservar">Reservar turno</button><a class="btn wa" target="_blank" rel="noopener" href="${esc(sh.whatsappUrl)}">Consultar por WhatsApp</a></div></section>
 <section id="servicios"><h2>Servicios</h2>${S.services.length
    ? `<ul class="svc">${S.services.map(s => `<li><span>${esc(s.name)}</span><span class="dots"></span><b>${money(s.price)}</b><small>${s.durationMinutes} min</small></li>`).join('')}</ul>`
    : '<div class="empty">Muy pronto vas a ver acá los servicios y precios.</div>'}</section>
 ${S.barbers.length ? `<section id="equipo"><h2>El equipo</h2><div class="opts">${S.barbers.map(b => `<div class="opt"><b>${esc(b.name)}</b>${b.specialties ? `<span>${esc(b.specialties)}</span>` : ''}${b.bio ? `<span>${esc(b.bio)}</span>` : ''}</div>`).join('')}</div></section>` : ''}
 <section id="reservar"><h2>Reservá tu turno</h2><div id="book"></div></section>
 <section id="portfolio"><h2>Portfolio</h2><div class="row"><input id="q" type="search" placeholder="Buscar por nombre o etiqueta" aria-label="Buscar" style="max-width:280px" value="${esc(S.q)}"><button class="btn ghost" data-a="reset">Restablecer filtros</button></div><div id="chips" class="row" style="margin-top:10px"></div><div id="grid" class="grid"></div></section>`;
}

/* ---- reserva con calendario ---- */
const renderBook = () => { const el = $('#book'); if (el) el.innerHTML = bookHTML(); };
const monthRange = () => { const last = new Date(B.y, B.m + 1, 0).getDate(); return [`${B.y}-${pad(B.m + 1)}-01`, `${B.y}-${pad(B.m + 1)}-${pad(last)}`]; };

function bookHTML() {
  if (B.done) {
    const d = B.done;
    return `<div class="okbox"><h3 class="st" style="margin-top:0">¡Gracias, ${esc(B.f.name || '')}!</h3><p>${esc(d.message)}</p>${d.serviceName ? `<ul><li>${esc(d.serviceName)} con ${esc(d.barberName)}</li><li>${esc(dlong(d.startAt))} a las ${esc(hm(d.startAt))}</li><li>${money(d.price)}</li></ul>` : ''}
    <div class="row"><a class="btn wa" target="_blank" rel="noopener" href="${esc(d.whatsappUrl)}">Avisar por WhatsApp</a><button class="btn ghost" data-a="again">Hacer otra reserva</button></div></div>`;
  }
  const sv = S.services.find(x => x.id === B.svc);
  let h = `<h3 class="st">1. Elegí el servicio</h3><div class="opts">${S.services.map(s => `<button class="opt" data-a="svc" data-id="${s.id}" aria-pressed="${B.svc === s.id}"><b>${esc(s.name)}</b><span>${s.durationMinutes} min</span><span>${money(s.price)}</span></button>`).join('') || '<div class="empty">Todavía no hay servicios cargados.</div>'}</div>`;
  if (!sv) return h;
  const bs = S.barbers.filter(x => x.serviceIds.includes(sv.id));
  if (!bs.length) return h + '<div class="empty">Por ahora no hay barberos para este servicio.</div>';
  h += `<h3 class="st">2. Elegí el barbero</h3><div class="opts"><button class="opt" data-a="brb" data-id="0" aria-pressed="${B.barber === null}"><b>Cualquier barbero disponible</b></button>${bs.map(x => `<button class="opt" data-a="brb" data-id="${x.id}" aria-pressed="${B.barber === x.id}"><b>${esc(x.name)}</b>${x.specialties ? `<span>${esc(x.specialties)}</span>` : ''}</button>`).join('')}</div>`;
  h += `<h3 class="st">3. Elegí el día</h3>${calHTML()}`;
  if (B.day) h += `<h3 class="st">4. Elegí la hora</h3>${slotsHTML()}`;
  if (B.slot !== null) h += `<h3 class="st">5. Tus datos</h3>${formHTML(sv)}`;
  return h;
}

function calHTML() {
  const first = new Date(B.y, B.m, 1), n = new Date(B.y, B.m + 1, 0).getDate(), lead = (first.getDay() + 6) % 7;
  const t = ymd(new Date()).split('-').map(Number), canPrev = B.y * 12 + B.m > t[0] * 12 + (t[1] - 1);
  let cells = ['L', 'M', 'M', 'J', 'V', 'S', 'D'].map(x => `<small>${x}</small>`).join('') + '<span></span>'.repeat(lead);
  for (let d = 1; d <= n; d++) {
    const ds = `${B.y}-${pad(B.m + 1)}-${pad(d)}`, info = B.days[ds], st = info ? info.status : 'LOADING', ok = st === 'AVAILABLE';
    cells += `<button class="day ${st} ${B.day === ds ? 'sel' : ''}" data-a="day" data-d="${ds}" ${ok ? '' : 'disabled'} title="${esc((info && info.reason) || DAYLBL[st] || '')}" aria-label="${d}, ${esc((info && info.reason) || DAYLBL[st] || 'cargando')}">${d}</button>`;
  }
  return `<div class="cal"><div class="cal-h"><button class="btn sm ghost" data-a="mprev" ${canPrev ? '' : 'disabled'} aria-label="Mes anterior">‹</button><span>${esc(first.toLocaleDateString('es-UY', { month: 'long', year: 'numeric' }))}</span><button class="btn sm ghost" data-a="mnext" aria-label="Mes siguiente">›</button></div><div class="cal-g">${cells}</div>
  <div class="legend"><span>Con borde: disponible</span><span>Tachado: completo</span><span>Rayado: cerrado</span></div></div>`;
}

function slotsHTML() {
  if (!B.slots.length) return '<div class="empty">No quedan horarios para ese día. Probá con otro.</div>';
  return `<div class="slots">${B.slots.map((s, i) => `<button class="chip" data-a="slot" data-i="${i}" aria-pressed="${B.slot === i}">${esc(s.label)}</button>`).join('')}</div>`;
}

function formHTML(sv) {
  const f = B.f, s = B.slots[B.slot];
  return `<p class="mu">${esc(sv.name)}, ${esc(dlong(s.startAt))} a las ${esc(s.label)}. ${money(sv.price)}.</p>
 <form data-f="book" class="stack" novalidate>
 <label>Nombre<input data-bf="name" value="${esc(f.name || '')}" autocomplete="name" maxlength="120"></label>
 <label>WhatsApp<input data-bf="whatsapp" value="${esc(f.whatsapp || '')}" inputmode="tel" autocomplete="tel" placeholder="099 123 456"></label>
 <label>Fecha de nacimiento (opcional)<input type="date" data-bf="birth" value="${esc(f.birth || '')}"></label>
 <label>Comentarios (opcional)<textarea data-bf="notes" rows="2" maxlength="500">${esc(f.notes || '')}</textarea></label>
 ${S.ref ? `<p class="mu">Referencia: ${esc(S.ref.title)} <button type="button" class="btn sm ghost" data-a="unref">Quitar</button></p>` : ''}
 <label style="font-weight:400"><input type="checkbox" data-bf="optin" ${f.optin ? 'checked' : ''} style="width:auto"> Acepto recibir mensajes por WhatsApp con novedades y beneficios (opcional)</label>
 <input class="hp" data-bf="website" tabindex="-1" autocomplete="off" aria-hidden="true">
 <small class="err" id="e-book"></small><button class="btn" ${B.busy ? 'disabled' : ''}>${B.busy ? 'Reservando…' : 'Confirmar reserva'}</button></form>`;
}

async function loadDays() {
  const tok = ++B.tokD, [from, to] = monthRange();
  B.days = {}; renderBook();
  try {
    const r = await api(`/api/public/${SLUG}/availability/days?serviceId=${B.svc}${B.barber ? '&barberId=' + B.barber : ''}&from=${from}&to=${to}`);
    if (tok !== B.tokD) return;
    B.days = Object.fromEntries(r.map(x => [x.date, x])); renderBook();
  } catch (e) { toast(e.message, 1); }
}

async function loadSlots() {
  const tok = ++B.tokS; B.slots = []; B.slot = null; renderBook();
  try {
    const r = await api(`/api/public/${SLUG}/availability/slots?serviceId=${B.svc}${B.barber ? '&barberId=' + B.barber : ''}&date=${B.day}`);
    if (tok !== B.tokS) return;
    B.slots = r; renderBook();
  } catch (e) { toast(e.message, 1); }
}

/* ---- portfolio público ---- */
const styleOf = id => S.styles.find(s => s.id === id);
const coverAlt = i => ((i.images.find(x => x.cover) || i.images[0] || {}).altText) || i.title;
const card = i => `<button class="card" data-a="open" data-id="${i.id}">${i.coverUrl
  ? `<img class="pic" loading="lazy" src="${esc(imgUrl(i.coverUrl))}" alt="${esc(coverAlt(i))}">`
  : `<div class="ph" style="--h:${(i.id * 47) % 360}" role="img" aria-label="${esc(i.title)}"></div>`}<div class="cap"><b>${esc(i.title)}</b><small>${esc(i.style.name)}</small></div></button>`;

function drawGallery() {
  if (!$('#chips')) return;
  const sub = S.cat ? S.styles.filter(s => s.category === S.cat && S.items.some(i => i.style.id === s.id)) : [];
  $('#chips').innerHTML = [['', 'Todo'], ...CATS].map(([v, l]) => `<button class="chip" data-a="cat" data-v="${v}" aria-pressed="${S.cat === v}">${l}</button>`).join('')
    + (sub.length ? '<span style="width:100%"></span>' + sub.map(s => `<button class="chip" data-a="sty" data-v="${s.id}" aria-pressed="${String(S.sty) === String(s.id)}">${esc(s.name)}</button>`).join('') : '');
  const q = S.q.trim().toLowerCase();
  S.list = S.items.filter(i => (!S.cat || (styleOf(i.style.id) || {}).category === S.cat) && (!S.sty || i.style.id == S.sty)
    && (i.title.toLowerCase().includes(q) || i.tags.some(t => t.includes(q))));
  $('#grid').innerHTML = S.list.length ? S.list.map(card).join('')
    : `<div class="empty" style="grid-column:1/-1">${S.items.length ? 'No hay publicaciones con esos filtros.' : 'Muy pronto vas a ver acá los trabajos.'}</div>`;
}

function openItem(id) {
  const i = S.items.find(x => x.id === id); if (!i) return;
  const main = i.images[0];
  dopen(`${main ? `<img id="mainpic" class="pic" style="aspect-ratio:4/3" src="${esc(imgUrl(main.url))}" alt="${esc(main.altText)}">` : `<div class="ph" style="--h:${(i.id * 47) % 360};aspect-ratio:4/3"></div>`}
 ${i.images.length > 1 ? `<div class="thumbs">${i.images.map(m => `<img src="${esc(imgUrl(m.url))}" alt="${esc(m.altText)}" data-a="pic" data-src="${esc(imgUrl(m.url))}">`).join('')}</div>` : ''}
 <h2>${esc(i.title)}</h2><p class="mu">${esc(i.style.name)}</p>${i.description ? `<p>${esc(i.description)}</p>` : ''}
 ${i.tags.length ? `<p class="row">${i.tags.map(t => `<span class="bd">#${esc(t)}</span>`).join('')}</p>` : ''}
 <div class="row"><a class="btn wa" target="_blank" rel="noopener" href="${esc(i.whatsappUrl)}">Consultar por este estilo</a><button class="btn" data-a="ref" data-id="${i.id}">Reservar con este corte</button>
 <button class="btn ghost" data-a="nav" data-d="-1" data-id="${i.id}" aria-label="Anterior">‹</button><button class="btn ghost" data-a="nav" data-d="1" data-id="${i.id}" aria-label="Siguiente">›</button><button class="btn ghost" data-a="close">Cerrar</button></div>`);
}

async function loadPublic() {
  S.loading = true; S.err = null; render();
  try {
    const base = `/api/public/${SLUG}`;
    const [shop, barbers, styles, items] = await retry(() => Promise.all([api(base + '/shop'), api(base + '/barbers'), api(base + '/styles'), api(base + '/portfolio?size=50')]));
    S.shop = shop; S.services = shop.services; S.barbers = barbers; S.styles = styles; S.items = items.items;
  } catch (e) { S.err = e.status === 404 ? 'No encontramos esta barbería. Revisá el identificador en config.js.' : e.message; }
  S.loading = false; render();
}

/* ============================ PANEL DE GESTIÓN ============================ */
const TABS = [['agenda', 'Agenda'], ['barberos', 'Barberos'], ['cierres', 'Cierres'], ['servicios', 'Servicios'], ['clientes', 'Clientes'], ['portfolio', 'Portfolio'], ['ajustes', 'Ajustes']];

function panelShell() {
  if (!S.me) {
    return `<section><h2>Panel de gestión</h2><form data-f="login" class="stack" novalidate>
 <label>Email<input name="email" type="email" autocomplete="username"></label>
 <label>Contraseña<input name="password" type="password" autocomplete="current-password"></label>
 ${LOCAL ? '<p class="mu"><small>Desarrollo: demo@barberstudio.test / demo1234</small></p>' : ''}
 <small class="err" id="e-login"></small><button class="btn">Ingresar</button></form></section>`;
  }
  return `<div class="pole"></div><div class="tabs">${TABS.map(([k, l]) => `<button class="chip" data-a="tab" data-v="${k}" aria-pressed="${P.tab === k}">${l}</button>`).join('')}
 <span class="mu" style="margin-left:auto">${esc(S.me.email)}</span><button class="chip" data-a="logout">Cerrar sesión</button></div><div id="pbody" class="spin">Cargando…</div>`;
}

async function loadPanel() {
  if (!S.me && !S.meChecked) {
    S.meChecked = true;
    try { S.me = await api('/api/auth/me'); } catch (e) { S.me = null; }
    render(); return;
  }
  if (!S.me) return;
  const el = $('#pbody'); if (!el) return;
  try { const html = await T[P.tab](); const el2 = $('#pbody'); if (el2) { el2.className = ''; el2.innerHTML = html; } }
  catch (e) {
    if (e.status === 401) { S.me = null; render(); toast('Tu sesión venció. Ingresá de nuevo.', 1); }
    else { const el2 = $('#pbody'); if (el2) { el2.className = ''; el2.innerHTML = `<div class="empty">${esc(e.message)}<p><button class="btn" data-a="tab" data-v="${P.tab}">Reintentar</button></p></div>`; } }
  }
}

const acts = a => (a.status === 'PENDING' ? `<button class="btn sm" data-a="pconf" data-id="${a.id}">Confirmar</button>` : '')
  + (a.status === 'CONFIRMED' ? `<button class="btn sm" data-a="pdone" data-id="${a.id}">Completar</button>` : '')
  + (a.status === 'PENDING' || a.status === 'CONFIRMED' ? `<button class="btn sm ghost" data-a="preas" data-id="${a.id}">Cambiar barbero</button><button class="btn sm ghost" data-a="pcancel" data-id="${a.id}">Cancelar</button>` : '');

const T = {
  async agenda() {
    const q = `from=${P.date}&to=${P.date}${P.barber ? '&barberId=' + P.barber : ''}`;
    const [sum, ap, bs] = await Promise.all([api('/api/dashboard/summary'), api('/api/appointments?' + q), api('/api/barbers')]);
    P.barbers = bs; P.appts = ap;
    const k = (b, l, n) => `<div class="kpi"><b>${b}</b>${l}${n ? `<br><small>${n}</small>` : ''}</div>`;
    const rows = ap.map(a => `<li><div class="tm">${hm(a.startAt)}<small>${a.durationMinutes} min</small></div><div class="gr"><b>${esc(a.clientName)}</b> <span class="mu">${esc(a.clientWhatsapp)}</span><br><span class="mu">${esc(a.serviceName)} con ${esc(a.barberName)}, ${money(a.priceAgreed)}</span>${a.notes ? `<br><small class="mu">${esc(a.notes)}</small>` : ''}</div><span class="bd ${a.status}">${ST[a.status]}</span><div class="acts">${acts(a)}</div></li>`).join('');
    return `<div class="kpis">${k(money(sum.registeredIncomeToday), 'Ingresos registrados hoy', 'Solo servicios completados. No es ganancia neta.')}${k(sum.completedToday, 'Completados hoy')}${k(sum.pendingToConfirm, 'Por confirmar')}${k(sum.cancelledToday, 'Cancelados hoy')}</div>
 <div class="row" style="margin-bottom:12px"><button class="btn sm ghost" data-a="pday" data-d="-1" aria-label="Día anterior">‹</button><input type="date" id="pdate" value="${P.date}" style="width:auto"><button class="btn sm ghost" data-a="pday" data-d="1" aria-label="Día siguiente">›</button><button class="btn sm ghost" data-a="ptoday">Hoy</button>
 <select id="pbarber" aria-label="Barbero" style="width:auto"><option value="">Todos los barberos</option>${bs.map(b => `<option value="${b.id}" ${String(P.barber) === String(b.id) ? 'selected' : ''}>${esc(b.name)}</option>`).join('')}</select><button class="btn" data-a="pnew">Nuevo turno</button></div>
 <h2 style="font-size:1.4rem;text-transform:capitalize">${esc(dlong(P.date + 'T12:00:00' + OFF))}</h2>${rows ? `<ul class="list">${rows}</ul>` : '<div class="empty">No hay turnos para este día.</div>'}`;
  },

  async barberos() {
    const [bs, sv] = await Promise.all([api('/api/barbers'), api('/api/services')]); P.barbers = bs; P.services = sv;
    return `<div class="row" style="margin-bottom:12px"><button class="btn" data-a="bnew">Nuevo barbero</button></div>${bs.length
      ? `<ul class="list">${bs.map(b => `<li><div class="gr"><b>${esc(b.name)}</b> ${b.active ? '' : '<span class="bd">Inactivo</span>'}<br><span class="mu">${esc(b.specialties || '')} ${b.serviceIds.length} servicio(s), ${b.futureAppointments} turno(s) próximos</span></div><div class="acts"><button class="btn sm" data-a="bhours" data-id="${b.id}">Horario</button><button class="btn sm ghost" data-a="bover" data-id="${b.id}">Excepciones</button><button class="btn sm ghost" data-a="bedit" data-id="${b.id}">Editar</button><button class="btn sm ghost" data-a="bdel" data-id="${b.id}">Borrar</button></div></li>`).join('')}</ul>`
      : '<div class="empty">Todavía no hay barberos.</div>'}<p class="mu"><small>Mientras un barbero no tenga horario cargado, no recibe reservas. Para licencias y vacaciones usá la pestaña Cierres.</small></p>`;
  },

  async cierres() {
    const [cl, bs] = await Promise.all([api('/api/closures'), api('/api/barbers')]); P.barbers = bs;
    return `<h2 style="font-size:1.4rem">Nuevo bloqueo</h2><form data-f="closure" class="two" novalidate>
 <label>Quién<select name="barber"><option value="">Todo el negocio</option>${bs.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('')}</select></label>
 <label>Motivo<select name="reason">${Object.entries(REASONS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></label>
 <label>Desde<input type="date" name="sd" required></label><label>Hasta (opcional)<input type="date" name="ed"></label>
 <label>Hora desde (opcional)<input type="time" name="st"></label><label>Hora hasta (opcional)<input type="time" name="et"></label>
 <label style="grid-column:1/-1">Observaciones internas<input name="notes"></label><div><button class="btn">Revisar y crear</button></div></form><small class="err" id="e-f"></small>
 <h2 style="font-size:1.4rem;margin-top:24px">Bloqueos cargados</h2>${cl.length ? `<ul class="list">${cl.map(c => `<li><div class="gr"><b>${c.barberName ? esc(c.barberName) : 'Todo el negocio'}</b>, ${REASONS[c.reason]}<br><span class="mu">${c.startDate}${c.endDate !== c.startDate ? ' al ' + c.endDate : ''}${c.startTime ? `, de ${t5(c.startTime)} a ${t5(c.endTime)}` : ', día completo'}${c.notes ? '. ' + esc(c.notes) : ''}</span></div><button class="btn sm ghost" data-a="cdel" data-id="${c.id}">Eliminar</button></li>`).join('')}</ul>` : '<div class="empty">No hay bloqueos cargados.</div>'}`;
  },

  async servicios() {
    const sv = await api('/api/services'); P.services = sv;
    return `<div class="row" style="margin-bottom:12px"><button class="btn" data-a="snew">Nuevo servicio</button></div>${sv.length
      ? `<ul class="list">${sv.map(s => `<li><div class="gr"><b>${esc(s.name)}</b> ${s.active ? '' : '<span class="bd">Inactivo</span>'}<br><span class="mu">${money(s.price)}, ${s.durationMinutes} min${s.description ? '. ' + esc(s.description) : ''}</span></div><button class="btn sm ghost" data-a="sedit" data-id="${s.id}">Editar</button></li>`).join('')}</ul>`
      : '<div class="empty">Cargá tu primer servicio para poder recibir reservas.</div>'}<p class="mu"><small>Los servicios no se borran: se desactivan para conservar el historial. Los turnos ya reservados mantienen el precio y la duración con que se hicieron.</small></p>`;
  },

  async clientes() {
    const cl = await api('/api/clients' + (P.q ? '?q=' + encodeURIComponent(P.q) : ''));
    return `<div class="row" style="margin-bottom:12px"><input id="cq" type="search" placeholder="Buscar por nombre o teléfono" aria-label="Buscar cliente" style="max-width:300px" value="${esc(P.q)}"><button class="btn" data-a="cnew">Nuevo cliente</button></div>${cl.length
      ? `<ul class="list">${cl.map(c => `<li><div class="gr"><b>${esc(c.name)}</b> <span class="mu">${esc(c.whatsapp)}</span>${c.notes ? `<br><small class="mu">${esc(c.notes)}</small>` : ''}</div><div class="acts"><button class="btn sm ghost" data-a="cvis" data-id="${c.id}">Historial</button><button class="btn sm ghost" data-a="carch" data-id="${c.id}">Archivar</button></div></li>`).join('')}</ul>`
      : '<div class="empty">No hay clientes para mostrar.</div>'}`;
  },

  async portfolio() {
    const [items, styles] = await Promise.all([api('/api/portfolio'), api('/api/styles')]); P.items = items; P.styles = styles;
    return `<div class="row" style="margin-bottom:12px"><button class="btn" data-a="pnewitem">Nueva publicación</button></div>
 <form data-f="style" class="row" style="margin-bottom:14px"><input name="name" placeholder="Nuevo estilo (ej: Barba candado)" aria-label="Nombre del estilo" style="max-width:260px"><select name="category" aria-label="Categoría" style="width:auto">${CATS.map(([k, l]) => `<option value="${k}">${l}</option>`).join('')}</select><button class="btn sm ghost">Agregar estilo</button></form>
 ${items.length ? `<ul class="list">${items.map(i => `<li>${i.coverUrl ? `<img class="sw" style="object-fit:cover" src="${esc(imgUrl(i.coverUrl))}" alt="">` : '<div class="sw" style="--h:200"></div>'}<div class="gr"><b>${esc(i.title)}</b><br><span class="mu">${esc(i.style.name)}</span></div><span class="bd ${i.status === 'PUBLISHED' ? 'on' : ''}">${i.status === 'PUBLISHED' ? 'Publicado' : i.status === 'DRAFT' ? 'Borrador' : 'Archivado'}</span><div class="acts"><button class="btn sm ghost" data-a="iedit" data-id="${i.id}">Editar</button><button class="btn sm ghost" data-a="itog" data-id="${i.id}">${i.status === 'PUBLISHED' ? 'Despublicar' : 'Publicar'}</button><button class="btn sm ghost" data-a="idel" data-id="${i.id}">Borrar</button></div></li>`).join('')}</ul>` : '<div class="empty">Todavía no hay publicaciones. Los borradores nunca se ven en el sitio público.</div>'}
 <p class="mu"><small>Las fotos se guardan en el servidor. En el plan gratuito de hosting conviene conectar Cloudinary antes de subir fotos definitivas (ver docs/DEPLOY.md).</small></p>`;
  },

  async ajustes() {
    const s = await api('/api/settings'); P.settings = s;
    const n = (k, l, min, max) => `<label>${l}<input type="number" name="${k}" min="${min}" max="${max}" value="${s[k]}"></label>`;
    return `<form data-f="settings" class="two" novalidate>
 <label>Nombre del negocio<input name="name" value="${esc(s.name)}"></label><label>WhatsApp<input name="whatsapp" value="${esc(s.whatsapp)}" placeholder="099 123 456"></label>
 <label>Dirección<input name="address" value="${esc(s.address || '')}"></label><label>Zona horaria<input name="timezone" value="${esc(s.timezone)}"></label>
 <label style="grid-column:1/-1">Presentación (se muestra en la portada)<textarea name="aboutText" rows="3">${esc(s.aboutText || '')}</textarea></label>
 ${n('slotMinutes', 'Intervalo entre turnos (minutos)', 5, 120)}${n('bufferMinutes', 'Tiempo de preparación entre turnos (minutos)', 0, 120)}${n('maxDaysAhead', 'Reservas hasta (días hacia adelante)', 1, 365)}${n('minNoticeMinutes', 'Aviso mínimo para reservar (minutos)', 0, 10080)}
 <label style="grid-column:1/-1;font-weight:400"><input type="checkbox" name="autoConfirm" ${s.autoConfirm ? 'checked' : ''} style="width:auto"> Confirmar automáticamente las reservas (si no, quedan pendientes hasta que las confirmes)</label>
 <div><button class="btn">Guardar ajustes</button></div></form><small class="err" id="e-f"></small>`;
  }
};

/* ---- diálogos ---- */
const sel = (name, opts, cur) => `<select name="${name}">${opts.map(([v, l]) => `<option value="${v}" ${String(cur) === String(v) ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
const btns = (ok) => `<small class="err" id="e-f"></small><div class="row"><button class="btn">${ok}</button><button type="button" class="btn ghost" data-a="close">Cancelar</button></div>`;

async function newAppt() {
  const [cl, sv, bs] = await Promise.all([api('/api/clients'), api('/api/services?activeOnly=true'), api('/api/barbers')]);
  if (!cl.length || !sv.length || !bs.some(b => b.active)) { toast('Para agendar necesitás al menos un cliente, un servicio y un barbero activo.', 1); return; }
  P.services = sv; P.barbers = bs;
  dopen(`<h2>Nuevo turno</h2><form data-f="appt" class="stack" novalidate>
 <label>Cliente${sel('clientId', cl.map(c => [c.id, c.name + ' ' + c.whatsapp]))}</label><label>Servicio${sel('serviceId', sv.map(s => [s.id, s.name + ', ' + money(s.price)]))}</label>
 <label>Barbero${sel('barberId', bs.filter(b => b.active).map(b => [b.id, b.name]), P.barber)}</label>
 <label>Día<input type="date" name="date" value="${P.date}"></label><label>Hora<input type="time" name="time" value="10:00"></label>
 <label>Estado${sel('status', [['CONFIRMED', 'Confirmado'], ['PENDING', 'Pendiente']])}</label><label>Notas<input name="notes"></label>${btns('Agendar')}</form>`);
}

async function reassign(id) {
  const a = P.appts.find(x => x.id == id), bs = P.barbers.filter(b => b.active && b.serviceIds.includes(a.serviceId) && b.id !== a.barberId);
  if (!bs.length) { toast('No hay otro barbero activo que realice este servicio.', 1); return; }
  dopen(`<h2>Cambiar de barbero</h2><p class="mu">${esc(a.clientName)}, ${esc(hm(a.startAt))}</p><form data-f="reassign" class="stack"><input type="hidden" name="id" value="${id}"><label>Nuevo barbero${sel('barberId', bs.map(b => [b.id, b.name]))}</label>${btns('Reasignar')}</form>`);
}

function bform(id) {
  const b = id ? P.barbers.find(x => x.id == id) : { serviceIds: P.services.map(s => s.id), active: true };
  dopen(`<h2>${id ? 'Editar' : 'Nuevo'} barbero</h2><form data-f="barber" class="stack" novalidate><input type="hidden" name="id" value="${id || ''}">
 <label>Nombre<input name="name" value="${esc(b.name || '')}" maxlength="120"></label><label>Especialidades<input name="specialties" value="${esc(b.specialties || '')}"></label>
 <label>Descripción<textarea name="bio" rows="2">${esc(b.bio || '')}</textarea></label>
 <fieldset><legend>Servicios que realiza</legend>${P.services.map(s => `<label style="font-weight:400"><input type="checkbox" name="svc" value="${s.id}" ${b.serviceIds.includes(s.id) ? 'checked' : ''} style="width:auto"> ${esc(s.name)}</label>`).join('') || '<p class="mu">Primero cargá servicios.</p>'}</fieldset>
 <label style="font-weight:400"><input type="checkbox" name="active" ${b.active ? 'checked' : ''} style="width:auto"> Activo (recibe reservas)</label>${btns('Guardar')}</form>`);
}

async function bhours(id) {
  const hrs = await api(`/api/barbers/${id}/hours`), by = Object.fromEntries(hrs.map(h => [h.weekday, h]));
  dopen(`<h2>Horario habitual</h2><p class="mu">Marcá los días de trabajo. El descanso es opcional.</p><form data-f="hours" novalidate><input type="hidden" name="id" value="${id}">
 <div class="wk"><span></span><small>Inicio</small><small>Fin</small><small>Descanso desde</small><small>Descanso hasta</small></div>
 ${[1, 2, 3, 4, 5, 6, 7].map(d => { const h = by[d]; return `<div class="wk"><label style="font-weight:400;margin:0"><input type="checkbox" name="on${d}" ${h ? 'checked' : ''} style="width:auto"> ${WD[d]}</label><input type="time" name="s${d}" value="${t5(h && h.startTime) || '10:00'}" aria-label="Inicio ${WD[d]}"><input type="time" name="e${d}" value="${t5(h && h.endTime) || '19:00'}" aria-label="Fin ${WD[d]}"><input type="time" name="bs${d}" value="${t5(h && h.breakStart)}" aria-label="Inicio del descanso ${WD[d]}"><input type="time" name="be${d}" value="${t5(h && h.breakEnd)}" aria-label="Fin del descanso ${WD[d]}"></div>`; }).join('')}
 ${btns('Guardar horario')}</form>`, 'wide');
}

async function bover(id) {
  const list = await api(`/api/barbers/${id}/overrides`);
  dopen(`<h2>Excepciones por fecha</h2><p class="mu">Un día libre, o un horario distinto solo para esa fecha. No cambian las demás semanas.</p>
 ${list.length ? `<ul class="list" style="margin-bottom:14px">${list.map(o => `<li><div class="gr"><b>${o.date}</b> ${o.dayOff ? 'Día libre' : `${t5(o.startTime)} a ${t5(o.endTime)}`}${o.note ? `<br><small class="mu">${esc(o.note)}</small>` : ''}</div><button class="btn sm ghost" data-a="odel" data-b="${id}" data-id="${o.id}">Quitar</button></li>`).join('')}</ul>` : '<p class="mu">No hay excepciones cargadas.</p>'}
 <form data-f="override" class="two" novalidate><input type="hidden" name="id" value="${id}"><label>Fecha<input type="date" name="date"></label>
 <label style="font-weight:400"><input type="checkbox" name="dayOff" style="width:auto"> Día libre</label><label>Inicio<input type="time" name="st" value="10:00"></label><label>Fin<input type="time" name="et" value="19:00"></label>
 <label>Descanso desde<input type="time" name="bs"></label><label>Descanso hasta<input type="time" name="be"></label><label style="grid-column:1/-1">Nota<input name="note"></label>
 <div><button class="btn">Agregar excepción</button> <button type="button" class="btn ghost" data-a="close">Cerrar</button></div></form><small class="err" id="e-f"></small>`, 'wide');
}

function affectedDialog(list) {
  dopen(`<h2>Este bloqueo afecta ${list.length} turno(s)</h2><p class="mu">Ningún turno se cancela solo. Elegí qué hacer:</p>
 <ul class="list" style="margin-bottom:14px">${list.map(a => `<li><div class="gr"><b>${esc(dlong(a.startAt))} ${esc(hm(a.startAt))}</b><br><span class="mu">${esc(a.clientName)} (${esc(a.clientWhatsapp)}), ${esc(a.serviceName)} con ${esc(a.barberName)}</span></div><span class="bd ${a.status}">${ST[a.status]}</span></li>`).join('')}</ul>
 <div class="row"><button class="btn" data-a="cgo" data-m="keep">Crear el bloqueo y dejar esos turnos</button><button class="btn ghost" data-a="cgo" data-m="cancel">Crear el bloqueo y cancelarlos</button><button class="btn ghost" data-a="close">Volver</button></div>
 <p class="mu"><small>Si los cancelás, avisales por WhatsApp desde la agenda.</small></p>`, 'wide');
}

function sform(id) {
  const s = id ? P.services.find(x => x.id == id) : { active: true, durationMinutes: 30 };
  dopen(`<h2>${id ? 'Editar' : 'Nuevo'} servicio</h2><form data-f="service" class="stack" novalidate><input type="hidden" name="id" value="${id || ''}">
 <label>Nombre<input name="name" value="${esc(s.name || '')}" maxlength="120"></label><label>Descripción<input name="description" value="${esc(s.description || '')}"></label>
 <label>Precio<input type="number" name="price" min="0" step="1" value="${s.price ?? ''}"></label><label>Duración (minutos)<input type="number" name="duration" min="5" max="480" step="5" value="${s.durationMinutes}"></label>
 <label style="font-weight:400"><input type="checkbox" name="active" ${s.active ? 'checked' : ''} style="width:auto"> Activo (se puede reservar)</label>${btns('Guardar')}</form>`);
}

function cform() {
  dopen(`<h2>Nuevo cliente</h2><form data-f="client" class="stack" novalidate><label>Nombre<input name="name" maxlength="120"></label><label>WhatsApp<input name="whatsapp" inputmode="tel" placeholder="099 123 456"></label><label>Preferencias<input name="preferences"></label>${btns('Guardar')}</form>`);
}

function pform(id) {
  const it = id ? P.items.find(x => x.id == id) : null;
  const groups = CATS.map(([k, l]) => `<optgroup label="${l}">${P.styles.filter(s => s.category === k).map(s => `<option value="${s.id}" ${it && it.style.id === s.id ? 'selected' : ''}>${esc(s.name)}</option>`).join('')}</optgroup>`).join('');
  dopen(`<h2>${id ? 'Editar' : 'Nueva'} publicación</h2><form data-f="item" class="stack" novalidate><input type="hidden" name="id" value="${id || ''}">
 <label>Título<input name="title" maxlength="140" value="${esc(it ? it.title : '')}"></label><label>Estilo<select name="style">${groups}</select></label>
 <label>Descripción<textarea name="desc" rows="2">${esc(it ? it.description || '' : '')}</textarea></label><label>Etiquetas (separadas por coma)<input name="tags" value="${esc(it ? it.tags.join(', ') : '')}"></label>
 <label>${it && it.images.length ? 'Agregar otra imagen' : 'Imagen'} (JPG, PNG o WebP, máx. 5 MB)<input type="file" name="img" accept="image/jpeg,image/png,image/webp"></label>
 <label>Texto alternativo de la imagen (accesibilidad)<input name="alt" placeholder="Ej: Degradé bajo visto de costado"></label>
 ${it && it.images.length ? `<div class="thumbs">${it.images.map(m => `<img src="${esc(imgUrl(m.url))}" alt="${esc(m.altText)}">`).join('')}</div>` : ''}
 <label style="font-weight:400"><input type="checkbox" name="pub" ${it && it.status === 'PUBLISHED' ? 'checked' : ''} style="width:auto"> Publicar en el sitio</label>${btns('Guardar')}</form>`);
}

/* ============================ ACCIONES ============================ */
const done = (msg) => { dclose(); if (msg) toast(msg); loadPanel(); };
const A = {
  close: dclose, yes() { dclose(); if (pending) pending(); pending = null; },
  reload: loadPublic,
  theme(d, b) { const r = document.documentElement, cur = r.dataset.theme || (matchMedia('(prefers-color-scheme:dark)').matches ? 'dark' : 'light'), n = TO[(TO.indexOf(cur) + 1) % 3]; r.dataset.theme = n; try { localStorage.setItem('theme', n); } catch (e) { /* ok */ } b.textContent = 'Tema: ' + TN[n]; },
  go(d) { const el = document.getElementById(d.t); if (el) el.scrollIntoView({ behavior: 'smooth' }); },
  // reserva pública
  svc(d) { B.svc = +d.id; B.barber = null; B.day = null; B.slots = []; B.slot = null; loadDays(); },
  brb(d) { B.barber = +d.id || null; B.day = null; B.slots = []; B.slot = null; loadDays(); },
  mprev() { if (B.m === 0) { B.m = 11; B.y--; } else B.m--; B.day = null; B.slots = []; B.slot = null; loadDays(); },
  mnext() { if (B.m === 11) { B.m = 0; B.y++; } else B.m++; B.day = null; B.slots = []; B.slot = null; loadDays(); },
  day(d) { B.day = d.d; loadSlots(); },
  slot(d) { B.slot = +d.i; renderBook(); const f = document.querySelector('[data-f=book]'); if (f) f.scrollIntoView({ behavior: 'smooth', block: 'center' }); },
  again() { B = fresh(); renderBook(); },
  unref() { S.ref = null; renderBook(); },
  // portfolio público
  cat(d) { S.cat = d.v; S.sty = ''; drawGallery(); }, sty(d) { S.sty = d.v; drawGallery(); },
  reset() { S.cat = ''; S.sty = ''; S.q = ''; const q = $('#q'); if (q) q.value = ''; drawGallery(); },
  open(d) { openItem(+d.id); }, pic(d) { const m = $('#mainpic'); if (m) m.src = d.src; },
  nav(d) { const l = S.list.length ? S.list : S.items, k = l.findIndex(i => i.id == d.id); openItem(l[(k + Number(d.d) + l.length) % l.length].id); },
  ref(d) { S.ref = S.items.find(i => i.id == d.id); dclose(); renderBook(); toast('Elegí servicio, día y hora: tu corte de referencia va incluido.'); const el = $('#reservar'); if (el) el.scrollIntoView({ behavior: 'smooth' }); },
  // panel
  tab(d) { P.tab = d.v; render(); },
  async logout() { try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ok */ } S.me = null; S.meChecked = true; render(); },
  pday(d) { const t = new Date(P.date + 'T12:00:00' + OFF); t.setDate(t.getDate() + Number(d.d)); P.date = ymd(t); loadPanel(); },
  ptoday() { P.date = ymd(new Date()); loadPanel(); },
  pnew() { newAppt().catch(e => toast(e.message, 1)); },
  async pconf(d) { try { await api(`/api/appointments/${d.id}/confirm`, { method: 'POST' }); toast('Turno confirmado.'); loadPanel(); } catch (e) { toast(e.message, 1); } },
  async pdone(d) { try { await api(`/api/appointments/${d.id}/complete`, { method: 'POST', body: {} }); toast('Visita registrada.'); loadPanel(); } catch (e) { toast(e.message, 1); } },
  pcancel(d) { ask('¿Cancelar este turno?', async () => { try { await api(`/api/appointments/${d.id}/cancel`, { method: 'POST' }); toast('Turno cancelado. No genera ingresos.'); loadPanel(); } catch (e) { toast(e.message, 1); } }); },
  preas(d) { reassign(d.id); },
  bnew() { bform(); }, bedit(d) { bform(d.id); },
  bhours(d) { bhours(d.id).catch(e => toast(e.message, 1)); }, bover(d) { bover(d.id).catch(e => toast(e.message, 1)); },
  bdel(d) { ask('¿Borrar este barbero?', async () => { try { await api(`/api/barbers/${d.id}`, { method: 'DELETE' }); toast('Barbero borrado.'); loadPanel(); } catch (e) { toast(e.message, 1); } }); },
  odel(d) { api(`/api/barbers/${d.b}/overrides/${d.id}`, { method: 'DELETE' }).then(() => bover(d.b)).catch(e => toast(e.message, 1)); },
  cdel(d) { ask('¿Eliminar este bloqueo?', async () => { try { await api(`/api/closures/${d.id}`, { method: 'DELETE' }); toast('Bloqueo eliminado.'); loadPanel(); } catch (e) { toast(e.message, 1); } }); },
  async cgo(d) { try { await api('/api/closures', { method: 'POST', body: { ...P.cbody, authorizeAffected: true, cancelAffected: d.m === 'cancel' } }); done(d.m === 'cancel' ? 'Bloqueo creado y turnos cancelados.' : 'Bloqueo creado. Los turnos afectados siguen en la agenda.'); } catch (e) { toast(e.message, 1); } },
  snew() { sform(); }, sedit(d) { sform(d.id); },
  cnew() { cform(); },
  carch(d) { ask('¿Archivar este cliente?', async () => { try { await api(`/api/clients/${d.id}/archive`, { method: 'POST' }); toast('Cliente archivado.'); loadPanel(); } catch (e) { toast(e.message, 1); } }, 'Archivar'); },
  async cvis(d) { try { const v = await api(`/api/clients/${d.id}/visits`); dopen(`<h2>Historial de visitas</h2>${v.length ? `<ul class="list">${v.map(x => `<li><div class="gr"><b>${esc(x.serviceName)}</b><br><span class="mu">${esc(dlong(x.performedAt))}</span></div><b>${money(x.finalPrice)}</b></li>`).join('')}</ul>` : '<p class="mu">Todavía no tiene visitas completadas.</p>'}<div class="row" style="margin-top:12px"><button class="btn ghost" data-a="close">Cerrar</button></div>`); } catch (e) { toast(e.message, 1); } },
  pnewitem() { pform(); }, iedit(d) { pform(d.id); },
  async itog(d) { const i = P.items.find(x => x.id == d.id); try { await api(`/api/portfolio/${d.id}/${i.status === 'PUBLISHED' ? 'unpublish' : 'publish'}`, { method: 'POST' }); toast(i.status === 'PUBLISHED' ? 'Pasó a borrador.' : 'Publicado.'); loadPanel(); } catch (e) { toast(e.message, 1); } },
  idel(d) { ask('¿Borrar esta publicación?', async () => { try { await api(`/api/portfolio/${d.id}`, { method: 'DELETE' }); toast('Publicación borrada.'); loadPanel(); } catch (e) { toast(e.message, 1); } }); }
};

const err = m => { const e = $('#e-f'); if (e) e.textContent = m; };
const F = {
  async book() {
    const f = B.f, s = B.slots[B.slot], e = $('#e-book'); e.textContent = '';
    if (!(f.name || '').trim()) { e.textContent = 'Ingresá tu nombre.'; return; }
    if (!(f.whatsapp || '').trim()) { e.textContent = 'Ingresá tu WhatsApp.'; return; }
    B.busy = true; renderBook();
    try {
      B.done = await api(`/api/public/${SLUG}/bookings`, { method: 'POST', body: { serviceId: B.svc, barberId: B.barber, startAt: s.startAt, name: f.name.trim(), whatsapp: f.whatsapp.trim(), birthDate: f.birth || null, marketingOptIn: !!f.optin, notes: f.notes || null, portfolioItemId: S.ref ? S.ref.id : null, website: f.website || '' } });
      B.busy = false; S.ref = null; renderBook(); const el = $('#reservar'); if (el) el.scrollIntoView({ behavior: 'smooth' });
    } catch (x) {
      B.busy = false; toast(x.message, 1);
      if (x.status === 409) { B.slot = null; await loadSlots(); loadDays(); } else renderBook();
    }
  },
  async login(f) {
    const d = fv(f); $('#e-login').textContent = '';
    try { S.me = await api('/api/auth/login', { method: 'POST', body: { email: d.email, password: d.password } }); S.meChecked = true; render(); }
    catch (e) { $('#e-login').textContent = e.message; }
  },
  async appt(f) {
    const d = fv(f);
    try { await api('/api/appointments', { method: 'POST', body: { clientId: +d.clientId, serviceId: +d.serviceId, barberId: +d.barberId, startAt: toISO(d.date, d.time), status: d.status, notes: d.notes || null } }); done('Turno agendado.'); }
    catch (e) { err(e.message); }
  },
  async reassign(f) { const d = fv(f); try { await api(`/api/appointments/${d.id}/reassign`, { method: 'POST', body: { barberId: +d.barberId } }); done('Turno reasignado.'); } catch (e) { err(e.message); } },
  async barber(f) {
    const fd = new FormData(f), id = fd.get('id');
    if (!fd.get('name').trim()) return err('Ingresá el nombre.');
    const body = { name: fd.get('name').trim(), specialties: fd.get('specialties') || null, bio: fd.get('bio') || null, active: !!fd.get('active'), serviceIds: fd.getAll('svc').map(Number) };
    try { const r = await api(id ? `/api/barbers/${id}` : '/api/barbers', { method: id ? 'PUT' : 'POST', body });
      done(!r.active && r.futureAppointments ? `Barbero desactivado. Atención: tiene ${r.futureAppointments} turno(s) próximos para reasignar.` : 'Barbero guardado.'); }
    catch (e) { err(e.message); }
  },
  async hours(f) {
    const fd = new FormData(f), id = fd.get('id'), items = [];
    for (let d = 1; d <= 7; d++) {
      if (!fd.get('on' + d)) continue;
      const it = { weekday: d, startTime: fd.get('s' + d), endTime: fd.get('e' + d) };
      if (fd.get('bs' + d) && fd.get('be' + d)) { it.breakStart = fd.get('bs' + d); it.breakEnd = fd.get('be' + d); }
      items.push(it);
    }
    try { await api(`/api/barbers/${id}/hours`, { method: 'PUT', body: { items } }); done('Horario guardado.'); } catch (e) { err(e.message); }
  },
  async override(f) {
    const d = fv(f);
    if (!d.date) return err('Elegí la fecha.');
    const body = { date: d.date, dayOff: !!d.dayOff, startTime: d.dayOff ? null : d.st || null, endTime: d.dayOff ? null : d.et || null, breakStart: d.bs || null, breakEnd: d.be || null, note: d.note || null };
    try { await api(`/api/barbers/${d.id}/overrides`, { method: 'POST', body }); toast('Excepción guardada.'); bover(d.id); } catch (e) { err(e.message); }
  },
  async closure(f) {
    const d = fv(f); err('');
    if (!d.sd) return err('Elegí la fecha de inicio.');
    P.cbody = { barberId: d.barber ? +d.barber : null, startDate: d.sd, endDate: d.ed || d.sd, startTime: d.st || null, endTime: d.et || null, reason: d.reason, notes: d.notes || null };
    try { await api('/api/closures', { method: 'POST', body: P.cbody }); done('Bloqueo creado.'); }
    catch (e) { if (e.status === 409 && e.data && e.data.affected) affectedDialog(e.data.affected); else err(e.message); }
  },
  async service(f) {
    const d = fv(f);
    if (!d.name.trim() || d.price === '') return err('Completá nombre y precio.');
    const body = { name: d.name.trim(), description: d.description || null, price: Number(d.price), durationMinutes: Number(d.duration), active: !!d.active };
    try { await api(d.id ? `/api/services/${d.id}` : '/api/services', { method: d.id ? 'PUT' : 'POST', body }); done('Servicio guardado.'); } catch (e) { err(e.message); }
  },
  async client(f) {
    const d = fv(f);
    try { await api('/api/clients', { method: 'POST', body: { name: d.name, whatsapp: d.whatsapp, preferences: d.preferences || null, notes: null } }); done('Cliente guardado.'); }
    catch (e) { err(e.status === 409 ? 'Ya existe un cliente con ese WhatsApp.' : e.message); }
  },
  async style(f) {
    const d = fv(f); if (!d.name.trim()) return toast('Escribí el nombre del estilo.', 1);
    try { await api('/api/styles', { method: 'POST', body: { name: d.name.trim(), category: d.category } }); toast('Estilo agregado.'); loadPanel(); } catch (e) { toast(e.message, 1); }
  },
  async item(f) {
    const fd = new FormData(f), id = fd.get('id'), file = f.img.files[0];
    if (!fd.get('title').trim()) return err('Ingresá un título.');
    if (file && !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return err('Formato no permitido. Usá JPG, PNG o WebP.');
    if (file && file.size > 5 * 1024 * 1024) return err('La imagen supera los 5 MB.');
    const body = { styleId: Number(fd.get('style')), title: fd.get('title').trim(), description: fd.get('desc') || null, tags: fd.get('tags').split(',').map(s => s.trim()).filter(Boolean) };
    try {
      let it = await api(id ? `/api/portfolio/${id}` : '/api/portfolio', { method: id ? 'PUT' : 'POST', body });
      if (file) { const form = new FormData(); form.append('file', file); form.append('alt', fd.get('alt') || it.title); it = await api(`/api/portfolio/${it.id}/images`, { method: 'POST', form }); }
      if (fd.get('pub') && it.status !== 'PUBLISHED') await api(`/api/portfolio/${it.id}/publish`, { method: 'POST' });
      done('Publicación guardada.');
    } catch (e) { err(e.message); }
  },
  async settings(f) {
    const d = fv(f), n = k => Number(d[k]);
    const body = { name: d.name, aboutText: d.aboutText || null, whatsapp: d.whatsapp, address: d.address || null, timezone: d.timezone, slotMinutes: n('slotMinutes'), bufferMinutes: n('bufferMinutes'), autoConfirm: !!d.autoConfirm, maxDaysAhead: n('maxDaysAhead'), minNoticeMinutes: n('minNoticeMinutes') };
    try { await api('/api/settings', { method: 'PUT', body }); err(''); toast('Ajustes guardados.'); loadPublic(); } catch (e) { err(e.message); }
  }
};

let tq;
document.addEventListener('click', e => { const b = e.target.closest('[data-a]'); if (b && A[b.dataset.a]) A[b.dataset.a](b.dataset, b); });
document.addEventListener('submit', e => { const f = e.target.closest('form[data-f]'); if (f) { e.preventDefault(); F[f.dataset.f](f); } });
document.addEventListener('input', e => {
  const t = e.target;
  if (t.dataset && t.dataset.bf) B.f[t.dataset.bf] = t.type === 'checkbox' ? t.checked : t.value;
  if (t.id === 'q') { S.q = t.value; drawGallery(); }
  if (t.id === 'cq') { clearTimeout(tq); tq = setTimeout(() => { P.q = t.value; loadPanel().then(() => { const c = $('#cq'); if (c) { c.focus(); c.setSelectionRange(c.value.length, c.value.length); } }); }, 350); }
});
document.addEventListener('change', e => {
  const t = e.target;
  if (t.dataset && t.dataset.bf && t.type === 'checkbox') B.f[t.dataset.bf] = t.checked;
  if (t.id === 'pdate' && t.value) { P.date = t.value; loadPanel(); }
  if (t.id === 'pbarber') { P.barber = t.value; loadPanel(); }
});
document.addEventListener('keydown', e => {
  if (!dlg.open || (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft')) return;
  const b = dlg.querySelector('[data-a=nav]'); if (b) A.nav({ id: b.dataset.id, d: e.key === 'ArrowRight' ? 1 : -1 });
});
addEventListener('hashchange', () => { render(); scrollTo(0, 0); });
addEventListener('scroll', () => { const h = document.documentElement, b = document.querySelector('header.bar'); if (b) b.style.setProperty('--p', (scrollY / ((h.scrollHeight - innerHeight) || 1) * 100) + '%'); }, { passive: true });
document.addEventListener('pointermove', e => {
  const hero = e.target.closest && e.target.closest('.hero');
  if (hero) { const r = hero.getBoundingClientRect(); hero.style.setProperty('--mx', (e.clientX - r.left) + 'px'); hero.style.setProperty('--my', (e.clientY - r.top) + 'px'); }
  if (document.documentElement.dataset.theme === 'street') { const s = document.documentElement.style; s.setProperty('--cx', e.clientX + 'px'); s.setProperty('--cy', e.clientY + 'px'); }
});

loadPublic();
