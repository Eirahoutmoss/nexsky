// Tiny DOM helpers.
export function h(tag, attrs = {}, ...kids) {
  const [t, ...cls] = tag.split('.');
  const el = document.createElement(t || 'div');
  if (cls.length) el.className = cls.join(' ');
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'html') el.innerHTML = v;
    else if (k in el && k !== 'list') el[k] = v;
    else el.setAttribute(k, v);
  }
  for (const c of kids.flat(Infinity)) if (c != null && c !== false) el.append(c.nodeType ? c : document.createTextNode(String(c)));
  return el;
}
export const panel = (...kids) => h('section.panel', {}, ...kids);
export const label = t => h('div.label', {}, t);
export const stat = (l, v, sub) => h('div.stat', {}, h('small', {}, l), h('b', {}, v), sub ? h('small', {}, sub) : null);
export const kv = (k, v, cls) => h('div.kv', {}, h('span', {}, k), h('b', { className: cls || '' }, v));
export function chip(text, on, onclick) { return h('button.chip' + (on ? '.on' : ''), { onclick, type: 'button' }, text); }
export function seg(items, sel, onpick) {
  return h('div.seg', {}, items.map((t, i) => h('button' + (i === sel ? '.on' : ''), { type: 'button', onclick: () => onpick(i) }, t)));
}
/** <select> with optgroups. */
export function select(items, selected, text, group, onpick) {
  const s = h('select');
  let og = null, last = null;
  items.forEach((it, i) => {
    const g = group(it);
    if (g !== last) { og = h('optgroup', { label: g }); s.append(og); last = g; }
    og.append(h('option', { value: i, selected: it === selected }, text(it)));
  });
  s.addEventListener('change', () => onpick(items[+s.value]));
  return s;
}
export function numField(lbl, value, onchange) {
  const i = h('input', { type: 'text', inputMode: 'decimal', value: String(value) });
  i.addEventListener('input', () => { const v = parseFloat(i.value.replace(',', '.')); if (v > 0) onchange(v); });
  return h('label.num', {}, h('small', {}, lbl), i);
}
export function toast(msg) {
  const t = h('div.toast', {}, msg);
  document.body.append(t);
  setTimeout(() => t.remove(), 3500);
}
export const store = {
  get(k, d) { try { const v = localStorage.getItem('nexsky.' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem('nexsky.' + k, JSON.stringify(v)); } catch { /* private mode */ } },
};
