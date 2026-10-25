import { Cat, SOLAR, search, position, riseSet, constellationName, dirName, hhmm, fmt, fmt0, fmtAngle, fmtDist } from './engine.js';
import { S, obs, now } from './state.js';
import { h, panel, label, stat, kv } from './ui.js';
import { objIcon } from './icons.js';
import { moonInfo } from './engine.js';

const HIGHLIGHTS = ['M42', 'M31', 'M45', 'M13', 'M57', 'M27', 'M44', 'NGC 869', 'M8', 'M51', 'M81', 'HIP32349', 'HIP11767', 'HIP91262', 'HIP27989', 'HIP24436'];
let query = '';

export function renderSearch(root, go) {
  const list = h('div.list');
  const input = h('input.search', { type: 'search', placeholder: 'Mars, Jüpiter, Orion Bulutsusu, M42, Ülker…', value: query, autocomplete: 'off' });
  const head = h('div.label', {}, '');
  const moonPh = moonInfo(now()).phase;
  function fill() {
    const t = now(), ob = obs();
    let src = query.trim() ? search(query) : [...SOLAR.map(s => Cat.byId.get(s.id)), ...HIGHLIGHTS.map(id => Cat.byId.get(id)).filter(Boolean)];
    let rows = src.map(o => [o, position(o, t, ob)]);
    if (!query.trim()) rows.sort((a, b) => (a[1].alt > 0 ? 0 : 1) - (b[1].alt > 0 ? 0 : 1) || b[1].alt - a[1].alt);
    head.textContent = query.trim() ? `${rows.length} sonuç` : 'Şu an gökyüzünde';
    list.replaceChildren(...rows.map(([o, p]) => h('button.row', { type: 'button', onclick: () => detail(root, o, go) },
      objIcon(o, 34, moonPh),
      h('div.grow', {}, h('b', {}, o.name),
        h('small', { className: p.alt > 0 ? '' : 'dim' }, o.kind + ' · ' + (p.alt > 0 ? `${fmt0(p.alt)}° yüksekte, ${dirName(p.az).toLocaleLowerCase('tr')}` : 'ufkun altında'))),
      h('span.chev', {}, '›'))));
  }
  input.addEventListener('input', () => { query = input.value; fill(); });
  root.replaceChildren(h('div.page', {}, input, head, list));
  fill();
}

function detail(root, o, go) {
  const t = now(), ob = obs();
  const p = position(o, t, ob);
  const con = o.type === 'CONSTELLATION' ? '' : constellationName(p.ra, p.dec);
  const today = h('div.stats', {}, h('small', {}, 'Hesaplanıyor…'));
  setTimeout(() => {
    const r = riseSet(o, new Date(t.getTime() - 12 * 3600e3), ob);
    if (r.alwaysUp) today.replaceChildren(h('p', {}, 'Bu enlemde hiç batmaz (hep ufkun üstünde).'));
    else if (r.neverUp) today.replaceChildren(h('p.warn', {}, 'Bu enlemden hiç görünmez.'));
    else today.replaceChildren(
      stat('Doğuş', hhmm(r.rise), r.rise && dirName(position(o, r.rise, ob).az)),
      stat('En yüksek', hhmm(r.transit), r.transitAlt != null ? fmt0(r.transitAlt) + '°' : null),
      stat('Batış', hhmm(r.set), r.set && dirName(position(o, r.set, ob).az)));
  }, 10);
  const back = () => renderSearch(root, go);
  root.replaceChildren(h('div.page', {},
    h('div.dhead', {}, h('button.back', { type: 'button', onclick: back }, '‹'), objIcon(o, 44, moonInfo(t).phase),
      h('div', {}, h('h2', {}, o.name), h('small', {}, o.kind + (con ? ` · ${con} takımyıldızı` : '')))),
    panel(label('Şu an'), h('div.stats', {},
      stat('Yön', fmt0(p.az) + '°', dirName(p.az)),
      stat('Yükseklik', fmt0(p.alt) + '°', p.alt > 0 ? 'görünür' : 'ufkun altında'))),
    panel(label('Bugün'), today),
    panel(label('Bilgi'),
      p.mag != null ? kv('Parlaklık (kadir)', fmt(p.mag, 1)) : null,
      p.diam != null ? kv('Görünen büyüklük', fmtAngle(p.diam / 60)) : null,
      fmtDist(o, p) ? kv('Uzaklık', fmtDist(o, p)) : null,
      kv('Sağ açıklık / dik açıklık', `${fmt(p.ra, 2)}s / ${fmt(p.dec, 2)}°`)),
    h('div.actions', {},
      h('button.tile', { type: 'button', onclick: () => { S.target = o; go(1); } }, '✦', h('span', {}, 'Gökyüzünde')),
      h('button.tile', { type: 'button', onclick: () => { S.target = o; S.plannerTab = 2; go(2); } }, '◎', h('span', {}, 'Kadraj')),
      h('button.tile', { type: 'button', onclick: () => { S.target = o; go(3); } }, '⌖', h('span', {}, 'Haritada')))));
}
