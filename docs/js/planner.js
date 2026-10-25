import { Cat, riseSet, position, moonInfo, nextQuarters, quarterName, sunAltitude, dirName, hhmm, dayLabel, shortDay, fmt, fmt0, fmtAngle, startOfDay } from './engine.js';
import { S, obs, now } from './state.js';
import { h, panel, label, stat, kv, chip, seg, select, numField, store } from './ui.js';
import { drawMoon } from './moon.js';
import { typeColor, objIcon } from './icons.js';
import { BODIES, LENSES, CUSTOM_BODY, CUSTOM_LENS, crop, fovReal, neededFocal, fmtFocal } from './gear.js';

export function renderPlanner(root) {
  const body = h('div');
  const tabs = () => seg(['Ay', 'Güneş', 'Kadraj'], S.plannerTab, i => { S.plannerTab = i; draw(); });
  const top = h('div');
  function draw() {
    top.replaceChildren(tabs());
    if (S.plannerTab === 0) moonTab(body, draw); else if (S.plannerTab === 1) sunTab(body, draw); else frameTab(body);
  }
  root.replaceChildren(h('div.page', {}, top, body));
  draw();
}

function dayPicker(redraw) {
  const d = S.planDay, today = startOfDay(new Date());
  return h('div.daypick', {},
    h('button.circle', { type: 'button', onclick: () => { S.planDay = startOfDay(new Date(d.getTime() - 12 * 3600e3)); redraw(); } }, '‹'),
    h('b.grow', {}, dayLabel(d)),
    h('button.circle', { type: 'button', onclick: () => { S.planDay = startOfDay(new Date(d.getTime() + 36 * 3600e3)); redraw(); } }, '›'),
    d.getTime() !== today.getTime() ? chip('Bugün', false, () => { S.planDay = today; redraw(); }) : null);
}

function moonCanvasEl(sizeCss, phase) {
  const c = h('canvas'); const dpr = devicePixelRatio || 1;
  c.width = c.height = sizeCss * dpr; c.style.width = c.style.height = sizeCss + 'px';
  drawMoon(c.getContext('2d'), sizeCss * dpr / 2, sizeCss * dpr / 2, sizeCss * dpr / 2, phase);
  return c;
}

function twilight(morning) {
  const ob = obs(), from = morning ? S.planDay : new Date(S.planDay.getTime() + 12 * 3600e3), dir = morning ? +1 : -1;
  const at = a => sunAltitude(dir, from, ob, a);
  return { night: at(-18), blue: at(-6), golden: at(-4), sun: at(-0.833), goldenEnd: at(6) };
}
function twilightBar(tw, morning) {
  const stops = morning ? [['Gece', tw.night], ['Mavi saat', tw.blue], ['Altın saat', tw.golden], ['Gün doğumu', tw.sun], ['Gündüz', tw.goldenEnd]]
    : [['Gündüz', tw.goldenEnd], ['Gün batımı', tw.sun], ['Altın saat', tw.golden], ['Mavi saat', tw.blue], ['Gece', tw.night]];
  return h('div', {}, h('div.tbar' + (morning ? '' : '.eve')), h('div.tstops', {}, stops.map(([n, t]) => h('div', {}, h('small', {}, n), h('b', {}, hhmm(t))))));
}

function moonTab(root, redraw) {
  const ob = obs(), moon = Cat.byId.get('moon');
  const rs = riseSet(moon, S.planDay, ob);
  const riseAz = rs.rise ? position(moon, rs.rise, ob).az : null;
  const mi = moonInfo(rs.rise || new Date(S.planDay.getTime() + 21 * 3600e3));
  const qs = nextQuarters(S.planDay);
  root.replaceChildren(dayPicker(redraw),
    panel(h('div.center', {}, moonCanvasEl(160, mi.phase), h('h2', {}, mi.name), h('small', {}, `%${fmt0(mi.illuminated * 100)} aydınlık`)),
      h('div.stats', {}, stat('Doğuş', hhmm(rs.rise)), stat('Batış', hhmm(rs.set)), stat('Doğuş yönü', riseAz != null ? fmt0(riseAz) + '°' : '—', riseAz != null ? dirName(riseAz) : null)),
      h('div.stats', {}, stat('Görünen büyüklük', fmt(mi.diamDeg, 2) + '°', mi.note), stat('Mesafe', fmt0(mi.distKm) + ' km'))),
    panel(label('Sabah ışığı'), twilightBar(twilight(true), true), label('Akşam ışığı'), twilightBar(twilight(false), false)),
    panel(label('Yaklaşan evreler'), qs.map(([q, t]) => {
      let extra = '';
      if (q === 2) { const d = moonInfo(t).distKm; extra = d < 362000 ? ' · süper ay' : d > 400000 ? ' · mikro ay' : ''; }
      return kv(quarterName(q), shortDay(t) + extra, extra.includes('süper') ? 'accent' : '');
    })),
    panel(label('Çekim ipucu'), h('p.muted', {}, 'Dolunay için başlangıç: ISO 100, f/8, 1/250 sn. Hilal ve dördünde 1–2 durak daha uzun pozlayın. Ufuktaki Ay daha büyük görünür ama aslında aynı boydadır; büyüklük etkisi için uzak bir ön planla uzun odak kullanın.')));
}

function sunTab(root, redraw) {
  const ob = obs(), sun = Cat.byId.get('sun');
  const rs = riseSet(sun, S.planDay, ob);
  const len = rs.rise && rs.set && rs.set > rs.rise ? Math.round((rs.set - rs.rise) / 60000) : null;
  root.replaceChildren(dayPicker(redraw),
    panel(h('div.stats', {},
      stat('Gün doğumu', hhmm(rs.rise), rs.rise && dirName(position(sun, rs.rise, ob).az)),
      stat('Öğle', hhmm(rs.transit), rs.transitAlt != null ? fmt0(rs.transitAlt) + '° yükseklik' : null),
      stat('Gün batımı', hhmm(rs.set), rs.set && dirName(position(sun, rs.set, ob).az))),
      len ? h('p.muted', {}, `Gün uzunluğu: ${Math.floor(len / 60)} sa ${len % 60} dk`) : null),
    panel(label('Sabah'), twilightBar(twilight(true), true), label('Akşam'), twilightBar(twilight(false), false)),
    panel(h('p.muted', {}, 'Mavi saat: Güneş ufkun 4–6° altındayken. Altın saat: Güneş ufkun 4° altından 6° üstüne kadar. Güneşi asla filtresiz uzun odakla ya da vizörden izlemeyin.')));
}

// ---- Kadraj
const st = store.get('frame', { cam: 'Nikon Coolpix P1000', lens: '150–600 mm (Sigma / Tamron)', tc: 1, cw: 23.5, ch: 15.6, lmin: 300, lmax: 1500, f: null, t: 0 });
const save = () => store.set('frame', st);

function frameView(fw, fh, o, objDeg, phase, aspect, compact) {
  const c = h('canvas.frame'); const W = compact ? 160 : 600, H = Math.round(W / aspect);
  c.width = W; c.height = H;
  const g = c.getContext('2d');
  const gr = g.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, W / 1.4); gr.addColorStop(0, '#13204A'); gr.addColorStop(1, '#050916');
  g.fillStyle = gr; g.fillRect(0, 0, W, H);
  const r = objDeg / 2 * (W / fw);
  if (o.type === 'MOON') drawMoon(g, W / 2, H / 2, Math.max(1.5, r), phase);
  else if (o.type === 'SUN') { g.fillStyle = '#FFD36B'; g.beginPath(); g.arc(W / 2, H / 2, Math.max(1.5, r), 0, 7); g.fill(); }
  else if (o.type === 'PLANET') {
    g.fillStyle = typeColor(o); g.beginPath(); g.arc(W / 2, H / 2, Math.max(1.5, r), 0, 7); g.fill();
    if (o.id === 'saturn' && r > 3) { g.strokeStyle = typeColor(o); g.lineWidth = r * 0.25; g.beginPath(); g.ellipse(W / 2, H / 2, r * 2.2, r * 0.45, 0, 0, 7); g.stroke(); }
  } else {
    const rr = Math.max(2, r); const grd = g.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, rr * 1.2);
    grd.addColorStop(0, typeColor(o) + 'B0'); grd.addColorStop(1, typeColor(o) + '00'); g.fillStyle = grd; g.beginPath(); g.arc(W / 2, H / 2, rr * 1.2, 0, 7); g.fill();
    g.fillStyle = '#fff'; g.beginPath(); g.arc(W / 2, H / 2, 2, 0, 7); g.fill();
  }
  if (!compact) { g.strokeStyle = '#ffffff22'; g.setLineDash([6, 6]); for (const k of [1, 2]) { g.beginPath(); g.moveTo(W * k / 3, 0); g.lineTo(W * k / 3, H); g.moveTo(0, H * k / 3); g.lineTo(W, H * k / 3); g.stroke(); } }
  return c;
}

function frameTab(root) {
  const t = now(), ob = obs(), phase = moonInfo(t).phase;
  const customBody = { name: CUSTOM_BODY, group: 'Özel', w: st.cw, h: st.ch, fixedEq: null };
  const bodies = [...BODIES, customBody];
  const body = bodies.find(b => b.name === st.cam) || BODIES[8];
  const lensList = [...LENSES, { name: CUSTOM_LENS, group: 'Özel', min: Math.min(st.lmin, st.lmax), max: Math.max(st.lmin, st.lmax) }];
  const lens = lensList.find(l => l.name === st.lens) || LENSES[9];
  const cr = crop(body);
  const interchangeable = !body.fixedEq;
  const range = body.fixedEq ? [body.fixedEq[0] / cr, body.fixedEq[1] / cr] : lens.max > lens.min ? [lens.min, lens.max] : null;
  const fixedF = body.fixedEq ? body.fixedEq[1] / cr : lens.max;
  if (st.f == null || !range || st.f < range[0] - 1e-6 || st.f > range[1] + 1e-6) st.f = range ? (body.fixedEq ? range[1] : st.f ?? fixedF) : fixedF;
  if (range && (st.f < range[0] || st.f > range[1])) st.f = range[1];
  const targets = [...new Map([S.target, ...['moon', 'jupiter', 'saturn', 'mars', 'venus', 'M42', 'M31', 'M45', 'M13'].map(id => Cat.byId.get(id))].filter(Boolean).map(o => [o.id, o])).values()];
  const obj = targets[Math.min(st.t, targets.length - 1)];
  const pos = position(obj, t, ob);
  const objDeg = obj.type === 'DSO' ? Math.max(...obj.size) / 60 : (pos.diam || 0) / 60;
  const aspect = body.w / body.h;
  const redraw = () => { save(); frameTab(root); };
  const focalLabel = h('b');
  const results = h('div');

  function refresh() {
    const f = interchangeable ? st.f * st.tc : st.f, eq = f * cr;
    focalLabel.textContent = (interchangeable ? fmt0(f) + ' mm · ' : '') + fmt0(eq) + ' mm eşdeğer';
    const [fw, fh] = fovReal(body, f);
    const ratio = objDeg > 0 ? objDeg / Math.min(fw, fh) : 0;
    const verdict = objDeg <= 0 ? 'Bu cismin boyutu bilinmiyor.' : ratio > 1 ? 'Kadraja sığmaz — daha kısa odak seçin.' : ratio > 0.8 ? 'Kadrajı neredeyse dolduruyor.'
      : ratio > 0.25 ? `${obj.name.split(' (')[0]} kadraja rahatça sığar.` : ratio > 0.05 ? 'Küçük görünür; daha uzun odak deneyin.' : 'Çok küçük; bir nokta gibi görünür.';
    const framePanel = panel(frameView(fw, fh, obj, objDeg, phase, aspect),
      h('p.verdict' + (ratio > 1 ? '.warn' : ''), {}, verdict),
      kv('Kadraj', `${fmtAngle(fw)} × ${fmtAngle(fh)}`),
      objDeg > 0 ? kv('Cismin boyutu', `${fmtAngle(objDeg)} (kadrajın %${fmt0(ratio * 100)}'i)`) : null,
      kv('Şu an', pos.alt > 0 ? `${fmt0(pos.alt)}° yüksekte, ${dirName(pos.az).toLocaleLowerCase('tr')}` : 'ufkun altında'),
      obj.type === 'SUN' ? h('p.warn', {}, 'Güneşi yalnızca uygun güneş filtresiyle çekin.') : null);
    const maxReal = body.fixedEq ? body.fixedEq[1] / cr : lens.max * 2;
    const needPanel = objDeg > 0 ? panel(label('Kaç mm gerekir?'),
      h('p.muted', {}, `${obj.name.split(' (')[0]} (${fmtAngle(objDeg)}) bu sensörde kadrajın kısa kenarına göre:`),
      [[1, 'Kadrajı tam doldurur'], [0.5, 'Kadrajın yarısı'], [0.25, 'Kadrajın dörtte biri'], [0.1, "Kadrajın %10'u"]].map(([fr, l]) => {
        const need = neededFocal(body, objDeg, fr);
        return h('div.needrow', {}, h('span', {}, l), h('div', {}, h('b', { className: need <= maxReal ? 'good' : '' }, fmtFocal(need)), h('small', {}, fmtFocal(need * cr) + ' eşdeğer')));
      }),
      h('p.muted.small', {}, 'Yeşil: seçili ekipmanla ulaşılabilir' + (interchangeable ? ' (2× telekonvertör dahil).' : '.') + (obj.type === 'PLANET' ? ' Gezegenlerde ayrıntı için çoğunlukla teleskop + Barlow (2×–3×) gerekir.' : ''))) : null;
    const planetPanel = panel(label('Gezegenler için örnek hesap'), h('p.muted', {}, 'Bugünkü görünen boyutlarıyla, bu sensörde kadrajın yarısını kaplamaları için:'),
      ['moon', 'jupiter', 'saturn', 'venus', 'mars', 'mercury', 'uranus', 'neptune'].map(id => {
        const o = Cat.byId.get(id), d = (position(o, t, ob).diam || 0) / 60;
        return h('div.prow', {}, objIcon(o, 22, phase), h('span.grow', {}, o.name), h('small', {}, fmtAngle(d)), h('b', {}, fmtFocal(neededFocal(body, d, 0.5) * cr) + ' eşd.'));
      }));
    const cmp = body.fixedEq ? [body.fixedEq[0], 300, 1000, body.fixedEq[1]].filter((v, i, a) => v >= body.fixedEq[0] && v <= body.fixedEq[1] && a.indexOf(v) === i).map(e => [fmt0(e) + ' mm', e / cr, e / cr])
      : [[fmt0(lens.min) + ' mm', lens.min, lens.min], [fmt0(lens.max) + ' mm', lens.max, lens.max], [fmt0(lens.max) + ' mm + 1,4×', lens.max * 1.4, null], [fmt0(lens.max) + ' mm + 2×', lens.max * 2, null]]
        .filter((v, i, a) => a.findIndex(x => x[1] === v[1]) === i);
    const cmpPanel = panel(label('Karşılaştırma'), h('div.grid2', {}, cmp.map(([l, ff, setF]) => {
      const [w, hh] = fovReal(body, ff);
      return h('button.cmp', { type: 'button', onclick: () => { if (setF != null) { st.f = setF; if (interchangeable) st.tc = 1; redraw(); } } }, frameView(w, hh, obj, objDeg, phase, aspect, true), h('small', {}, l));
    })));
    results.replaceChildren(framePanel, needPanel, planetPanel, cmpPanel);
  }

  let slider = null;
  if (range) {
    slider = h('input', { type: 'range', min: Math.log(range[0]), max: Math.log(range[1]), step: 0.001, value: Math.log(st.f) });
    slider.addEventListener('input', () => { st.f = Math.exp(+slider.value); refresh(); });
    slider.addEventListener('change', save);
  }
  const gearPanel = panel(
    label('Kamera'), select(bodies, body, b => b.name, b => b.group, b => { st.cam = b.name; st.f = null; redraw(); }),
    body.name === CUSTOM_BODY ? h('div.row2', {}, numField('Sensör genişliği (mm)', st.cw, v => { st.cw = v; save(); }), numField('Sensör yüksekliği (mm)', st.ch, v => { st.ch = v; save(); }),
      h('button.mini', { type: 'button', onclick: redraw }, 'Uygula')) : null,
    interchangeable ? [label('Objektif / teleskop'), select(lensList, lens, l => l.name, l => l.group, l => { st.lens = l.name; st.f = null; redraw(); }),
      st.lens === CUSTOM_LENS ? h('div.row2', {}, numField('En kısa odak (mm)', st.lmin, v => { st.lmin = v; st.f = null; save(); }), numField('En uzun odak (mm)', st.lmax, v => { st.lmax = v; st.f = null; save(); }),
        h('button.mini', { type: 'button', onclick: redraw }, 'Uygula')) : null,
      h('div.chips', {}, h('small', {}, 'Telekonvertör'), [[1, 'yok'], [1.4, '1,4×'], [2, '2×']].map(([k, l]) => chip(l, st.tc === k, () => { st.tc = k; redraw(); })))] : null,
    label('Hedef'), h('div.chips.scroll', {}, targets.map((o, i) => chip(o.name.split(' (')[0], o === obj, () => { st.t = i; redraw(); }))),
    h('div.between', {}, label('Odak uzaklığı'), focalLabel),
    slider,
    body.fixedEq ? h('div.chips', {}, [24, 300, 1000, 2000, 3000].filter(e => e >= body.fixedEq[0] && e <= body.fixedEq[1]).map(e => chip(fmt0(e) + ' mm', false, () => { st.f = e / cr; redraw(); }))) : null);
  root.replaceChildren(gearPanel, results);
  refresh();
}
