/* Utilidades compartidas por el sitio y los módulos del panel. */
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

export async function api(path, o = {}) {
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
export async function retry(fn, n = 10) {
  for (let i = 0; ; i++) { try { return await fn(); } catch (e) { if (!e.network || i >= n) throw e; await new Promise(r => setTimeout(r, 5000)); } }
}

const dlg = $('#dlg');
const dopen = (html, cls) => { dlg.className = cls || ''; dlg.innerHTML = html; if (!dlg.open) dlg.showModal(); };
const dclose = () => { if (dlg.open) dlg.close(); };

const toast = (m, bad) => { const t = $('#toast'); t.textContent = m; t.className = bad ? 'bad' : ''; clearTimeout(toast.k); toast.k = setTimeout(() => { t.textContent = ''; }, 4500); };
const fv = f => Object.fromEntries(new FormData(f));

const sel = (name, opts, cur) => `<select name="${name}">${opts.map(([v, l]) => `<option value="${v}" ${String(cur) === String(v) ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
const btns = (ok) => `<small class="err" id="e-f"></small><div class="row"><button class="btn">${ok}</button><button type="button" class="btn ghost" data-a="close">Cancelar</button></div>`;

/* Puente para los módulos del panel: app.js completa estas funciones al arrancar. */
const panel = { reload: () => {}, me: () => null };

export { CFG, LOCAL, API, SLUG, TZ, OFF, $, esc, money, hm, dlong, ymd, pad, toISO, t5, imgUrl, dlg, dopen, dclose, toast, fv, sel, btns, panel };
