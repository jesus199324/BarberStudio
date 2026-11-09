/* Inventario: productos de cuidado, stock e historial de movimientos. */
import { $, esc, money, hm, dlong, imgUrl, api, dopen, dclose, toast, fv, sel, btns, panel } from './lib.js';

const REASON = { SALE: 'Venta', ORDER: 'Pedido web', PURCHASE: 'Reposición', ADJUSTMENT: 'Ajuste', VOID: 'Cobro anulado', ORDER_CANCEL: 'Pedido cancelado' };
const I = { lowOnly: false, items: [] };
const isOwner = () => { const me = panel.me(); return !!me && me.role === 'OWNER'; };
const find = id => I.items.find(p => p.id == id);
const err = m => { const e = $('#e-f'); if (e) e.textContent = m; };
const imgOk = f => ['image/jpeg', 'image/png', 'image/webp'].includes(f.type) && f.size <= 5 * 1024 * 1024;

function row(p) {
  const owner = isOwner();
  const pic = p.imageUrl ? `<img class="inv-pic" src="${esc(imgUrl(p.imageUrl))}" alt="">` : '<span class="inv-pic inv-nopic" aria-hidden="true">🧴</span>';
  const tags = (p.active ? '' : ' <span class="bd">Inactivo</span>') + (p.visibleInShop ? '' : ' <span class="bd">No visible en tienda</span>');
  const actions = owner ? `<div class="row"><button class="btn sm" data-a="invstock" data-id="${p.id}">Mover stock</button>`
    + `<button class="btn sm ghost" data-a="invedit" data-id="${p.id}">Editar</button>`
    + `<button class="btn sm ghost" data-a="invhist" data-id="${p.id}">Historial</button></div>` : '';
  return `<li>${pic}<div class="gr" style="flex:1;min-width:160px"><b>${esc(p.name)}</b>${tags}<br><span class="mu">${money(p.price)}</span></div>`
    + `<div class="inv-stock ${p.lowStock ? 'inv-low' : ''}" aria-label="Stock">${p.stock}<small>${p.lowStock ? 'stock bajo' : 'en stock'}</small></div>${actions}</li>`;
}

async function inventario() {
  I.items = await api('/api/products' + (I.lowOnly ? '?lowStock=true' : ''));
  const top = `<div class="row" style="margin-bottom:12px;align-items:center">${isOwner() ? '<button class="btn" data-a="invnew">Nuevo producto</button>' : ''}`
    + `<label style="font-weight:400;margin-left:auto"><input type="checkbox" data-a="invlow" ${I.lowOnly ? 'checked' : ''} style="width:auto"> Solo stock bajo</label></div>`;
  const empty = I.lowOnly ? 'No hay productos con stock bajo.' : 'Todavía no cargaste productos.';
  return top + (I.items.length ? `<ul class="list">${I.items.map(row).join('')}</ul>` : `<div class="empty">${empty}</div>`);
}

function pform(id) {
  const p = id ? find(id) : { active: true, visibleInShop: true, lowStockThreshold: 2 };
  const photo = id ? `<label>Foto (JPG, PNG o WebP, hasta 5 MB)<input type="file" name="file" accept="image/jpeg,image/png,image/webp"></label>`
    + (p.imageUrl ? '<button type="button" class="btn sm ghost" data-a="invimgdel" data-id="' + id + '">Quitar foto</button>' : '')
    : '<p class="mu"><small>La foto la podés subir después de crear el producto.</small></p>';
  dopen(`<h2>${id ? 'Editar' : 'Nuevo'} producto</h2><form data-f="invprod" class="stack" novalidate><input type="hidden" name="id" value="${id || ''}">
 <label>Nombre<input name="name" value="${esc(p.name || '')}" maxlength="120"></label>
 <label>Descripción<input name="description" value="${esc(p.description || '')}" maxlength="2000"></label>
 <label>Precio<input type="number" name="price" min="0" step="0.01" value="${p.price ?? ''}"></label>
 <label>Avisar stock bajo con<input type="number" name="lowStockThreshold" min="0" step="1" value="${p.lowStockThreshold}"></label>
 <label style="font-weight:400"><input type="checkbox" name="active" ${p.active ? 'checked' : ''} style="width:auto"> Activo (se puede vender)</label>
 <label style="font-weight:400"><input type="checkbox" name="visibleInShop" ${p.visibleInShop ? 'checked' : ''} style="width:auto"> Mostrar en la tienda online</label>
 ${photo}${btns('Guardar')}</form>`);
}

function sform(id) {
  const p = find(id);
  dopen(`<h2>Mover stock: ${esc(p.name)}</h2><p class="mu">Stock actual: <b>${p.stock}</b> · Queda en: <b id="inv-after">${p.stock}</b></p>
 <form data-f="invstock" class="stack" novalidate><input type="hidden" name="id" value="${id}">
 <label>Tipo${sel('reason', [['PURCHASE', 'Reposición (+)'], ['ADJUSTMENT', 'Ajuste (+ / −)']], 'PURCHASE')}</label>
 <label>Cantidad<input type="number" name="delta" step="1" value="1" data-stock="${p.stock}"></label>
 <label>Nota (obligatoria en un ajuste)<input name="note" maxlength="255" placeholder="Ej: se rompió un frasco"></label>
 ${btns('Guardar')}</form>`);
}

/* El signo lo decide el tipo: la reposición siempre suma; en un ajuste la persona escribe el signo. */
const signedDelta = (reason, raw) => { const n = Math.trunc(Number(raw)); return reason === 'PURCHASE' ? Math.abs(n) : n; };

function updateAfter(form) {
  const out = $('#inv-after'); if (!out) return;
  const input = form.querySelector('[name=delta]'), stock = +input.dataset.stock;
  const d = signedDelta(form.querySelector('[name=reason]').value, input.value);
  out.textContent = Number.isFinite(d) ? stock + d : stock;
}
document.addEventListener('input', e => { const f = e.target.closest('form[data-f="invstock"]'); if (f) updateAfter(f); });
document.addEventListener('change', e => { const f = e.target.closest('form[data-f="invstock"]'); if (f) updateAfter(f); });

async function history(id) {
  const p = find(id);
  dopen(`<h2>Historial: ${esc(p.name)}</h2><div id="inv-hist" class="spin">Cargando…</div><div class="row"><button class="btn ghost" data-a="close">Cerrar</button></div>`);
  try {
    const ms = await api(`/api/products/${id}/movements`);
    const el = $('#inv-hist'); if (!el) return; el.className = '';
    el.innerHTML = ms.length ? `<ul class="list">${ms.map(m => `<li><div class="tm">${esc(dlong(m.createdAt))}<small>${hm(m.createdAt)}</small></div>`
      + `<div class="gr" style="flex:1"><b class="${m.delta < 0 ? 'inv-out' : 'inv-in'}">${m.delta > 0 ? '+' : '−'}${Math.abs(m.delta)}</b> ${esc(REASON[m.reason] || m.reason)}`
      + `${m.note ? `<br><span class="mu">${esc(m.note)}</span>` : ''}</div></li>`).join('')}</ul>` : '<div class="empty">Sin movimientos todavía.</div>';
  } catch (x) { const el = $('#inv-hist'); if (el) { el.className = ''; el.textContent = x.message; } }
}

export default {
  tabs: [['inventario', 'Inventario', false]],
  views: { inventario },
  actions: {
    invnew() { pform(); },
    invedit(d) { pform(d.id); },
    invstock(d) { sform(d.id); },
    invhist(d) { history(d.id); },
    invlow(d, el) { I.lowOnly = el.checked; panel.reload(); },
    async invimgdel(d) {
      try { await api(`/api/products/${d.id}/image`, { method: 'DELETE' }); dclose(); toast('Foto quitada.'); panel.reload(); }
      catch (x) { err(x.message); }
    },
  },
  forms: {
    async invprod(f) {
      const v = fv(f), file = f.file && f.file.files[0];
      if (!v.name.trim()) return err('Ingresá el nombre.');
      if (v.price === '' || Number(v.price) < 0) return err('Ingresá un precio válido.');
      if (file && !imgOk(file)) return err('La foto tiene que ser JPG, PNG o WebP, de hasta 5 MB.');
      const body = { name: v.name.trim(), description: v.description, price: Number(v.price),
        lowStockThreshold: v.lowStockThreshold === '' ? null : Number(v.lowStockThreshold),
        active: !!f.active.checked, visibleInShop: !!f.visibleInShop.checked };
      try {
        const saved = await api('/api/products' + (v.id ? '/' + v.id : ''), { method: v.id ? 'PUT' : 'POST', body });
        if (file) { const form = new FormData(); form.append('file', file); await api(`/api/products/${saved.id}/image`, { method: 'POST', form }); }
        dclose(); toast(v.id ? 'Producto actualizado.' : 'Producto creado.'); panel.reload();
      } catch (x) { err(x.message); }
    },
    async invstock(f) {
      const v = fv(f), delta = signedDelta(v.reason, v.delta);
      if (!Number.isFinite(delta) || delta === 0) return err('Ingresá una cantidad distinta de 0.');
      if (v.reason === 'ADJUSTMENT' && !v.note.trim()) return err('Indicá el motivo del ajuste.');
      try {
        await api(`/api/products/${v.id}/stock`, { method: 'POST', body: { delta, reason: v.reason, note: v.note.trim() || null } });
        dclose(); toast('Stock actualizado.'); panel.reload();
      } catch (x) { err(x.message); }
    },
  },
};
