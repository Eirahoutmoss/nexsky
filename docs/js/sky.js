// Sky view: AR (motion sensors), Camera (live video behind) and Map (drag) modes.
import { Cat, SOLAR, position, horFrame, enu, radecVec, moonInfo, dirName, fmt0, fmt, fmtDist } from './engine.js';
import { S, obs, now } from './state.js';
import { h, store, toast } from './ui.js';
import { typeColor, objIcon, sparkle } from './icons.js';
import { drawMoon } from './moon.js';

let stopFn = null;
export function stopSky() { if (stopFn) stopFn(); stopFn = null; }

const deg = Math.PI / 180;

function buildFrame() {
  const t = now(), ob = obs();
  const rot = horFrame(t, ob);
  const n = Cat.starMag.length;
  const stars = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) rot(Cat.stars[3 * i], Cat.stars[3 * i + 1], Cat.stars[3 * i + 2], stars, 3 * i);
  const lines = Cat.constellations.flatMap(c => c.lines.map(seg => {
    const out = new Float32Array(seg.length / 2 * 3);
    for (let k = 0; k < seg.length / 2; k++) { const v = radecVec(seg[2 * k], seg[2 * k + 1]); rot(v[0], v[1], v[2], out, 3 * k); }
    return out;
  }));
  const labels = Cat.constellations.map(c => { const v = radecVec(c.label[0], c.label[1]); const o = new Float32Array(3); rot(v[0], v[1], v[2], o); return [c.name, o]; });
  const bodies = SOLAR.map(s => { const o = Cat.byId.get(s.id); const p = position(o, t, ob); return [o, enu(p.alt, p.az), p]; });
  const enuOf = o => {
    const b = bodies.find(x => x[0].id === o.id); if (b) return b[1];
    const v = radecVec(o.ra, o.dec); const out = new Float32Array(3); rot(v[0], v[1], v[2], out); return out;
  };
  const dsos = Cat.objects.filter(o => o.type === 'DSO' && (o.mag ?? 99) < 8.5).map(o => [o, enuOf(o)]);
  const named = Cat.objects.filter(o => o.type === 'STAR' && (o.mag ?? 9) < 3.5).map(o => [o, enuOf(o)]);
  return { stars, lines, labels, bodies, dsos, named, enuOf, phase: moonInfo(t).phase };
}

/** Device→world (E,N,U) rotation, row-major, from W3C Euler angles (degrees). */
function eulerMatrix(a, b, g) {
  const cA = Math.cos(a * deg), sA = Math.sin(a * deg), cB = Math.cos(b * deg), sB = Math.sin(b * deg), cG = Math.cos(g * deg), sG = Math.sin(g * deg);
  return [cA * cG - sA * sB * sG, -cB * sA, cA * sG + cG * sA * sB,
    cG * sA + cA * sB * sG, cA * cB, sA * sG - cA * cG * sB,
    -cB * sG, sB, cB * cG];
}
function manualMatrix(az, alt) {
  const a = alt * deg, z = az * deg;
  const fx = Math.cos(a) * Math.sin(z), fy = Math.cos(a) * Math.cos(z), fz = Math.sin(a);
  const zx = -fx, zy = -fy, zz = -fz;
  let ux = -fx * fz, uy = -fy * fz, uz = 1 - fz * fz; const ul = Math.max(1e-4, Math.hypot(ux, uy, uz)); ux /= ul; uy /= ul; uz /= ul;
  const xx = uy * zz - uz * zy, xy = uz * zx - ux * zz, xz = ux * zy - uy * zx;
  return [xx, ux, zx, xy, uy, zy, xz, uz, zz];
}
/** Rotate the world frame about "up" by `yaw` degrees (compass / calibration offset). */
function withYaw(m, yaw) {
  if (!yaw) return m;
  const c = Math.cos(yaw * deg), s = Math.sin(yaw * deg);
  // world' = Rz(yaw) · world  → rows mix E/N
  return [c * m[0] - s * m[3], c * m[1] - s * m[4], c * m[2] - s * m[5],
    s * m[0] + c * m[3], s * m[1] + c * m[4], s * m[2] + c * m[5],
    m[6], m[7], m[8]];
}
const headingOf = m => ((Math.atan2(-m[2], -m[5]) / deg) + 360) % 360;

function projector(m, w, hgt, fov) {
  const f = (hgt / 2) / Math.tan(fov * deg / 2), cx = w / 2, cy = hgt / 2;
  return {
    f, w, h: hgt,
    p(e, n, u, out) {
      const dx = m[0] * e + m[3] * n + m[6] * u, dy = m[1] * e + m[4] * n + m[7] * u, dz = m[2] * e + m[5] * n + m[8] * u;
      if (dz > -0.05) return false;
      out[0] = cx + f * dx / -dz; out[1] = cy - f * dy / -dz; return true;
    },
    on(o, mg = 40) { return o[0] > -mg && o[0] < w + mg && o[1] > -mg && o[1] < hgt + mg; },
    edge(e, n, u) { const dx = m[0] * e + m[3] * n + m[6] * u, dy = m[1] * e + m[4] * n + m[7] * u; return Math.atan2(-dy, dx); },
  };
}

const starCol = bv => bv < 0 ? '#B9CCFF' : bv < 0.4 ? '#E6EDFF' : bv < 0.8 ? '#FFF6E5' : bv < 1.3 ? '#FFDDAA' : '#FFB98A';

function draw(g, pr, fr, fov, target, hits, overCamera) {
  const W = pr.w, H = pr.h, p = [0, 0], q = [0, 0];
  hits.length = 0;
  g.clearRect(0, 0, W, H);
  if (!overCamera) { const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, '#050916'); gr.addColorStop(0.6, '#0B1638'); gr.addColorStop(1, '#13204A'); g.fillStyle = gr; g.fillRect(0, 0, W, H); }
  // ground + horizon
  g.fillStyle = overCamera ? 'rgba(10,15,30,.2)' : 'rgba(10,15,30,.8)';
  for (let az = 0; az < 360; az += 6) {
    const a = [0, 0], b = [0, 0], c = [0, 0], d = [0, 0];
    const t1 = enu(0, az), b1 = enu(-35, az), t2 = enu(0, az + 6), b2 = enu(-35, az + 6);
    if (pr.p(...t1, a) && pr.p(...b1, b) && pr.p(...t2, c) && pr.p(...b2, d)) { g.beginPath(); g.moveTo(...a); g.lineTo(...c); g.lineTo(...d); g.lineTo(...b); g.closePath(); g.fill(); }
  }
  g.strokeStyle = '#3FA9FF'; g.lineWidth = 2; g.beginPath(); let on = false;
  for (let az = 0; az <= 360; az += 4) { const v = enu(0, az); if (pr.p(...v, p)) { on ? g.lineTo(...p) : g.moveTo(...p); on = true; } else on = false; }
  g.stroke();
  g.font = '600 15px Inter, system-ui'; g.textAlign = 'center';
  [['K', 0], ['KD', 45], ['D', 90], ['GD', 135], ['G', 180], ['GB', 225], ['B', 270], ['KB', 315]].forEach(([n, az]) => {
    const v = enu(1.5, az); if (pr.p(...v, p) && pr.on(p)) { g.fillStyle = n === 'K' ? '#FF6B6B' : '#7FD8FF'; g.fillText(n, p[0], p[1] - 6); }
  });
  // constellations
  g.strokeStyle = overCamera ? 'rgba(143,196,255,.6)' : 'rgba(63,122,217,.35)'; g.lineWidth = 1.4;
  for (const s of fr.lines) {
    let prev = false;
    for (let k = 0; k < s.length / 3; k++) {
      const okk = pr.p(s[3 * k], s[3 * k + 1], s[3 * k + 2], q);
      if (okk && prev && (pr.on(p, 400) || pr.on(q, 400))) { g.beginPath(); g.moveTo(...p); g.lineTo(...q); g.stroke(); }
      p[0] = q[0]; p[1] = q[1]; prev = okk;
    }
  }
  if (fov < 100) { g.font = '500 11px Inter, system-ui'; g.fillStyle = 'rgba(169,190,230,.6)'; for (const [n, v] of fr.labels) if (pr.p(v[0], v[1], v[2], p) && pr.on(p)) g.fillText(n.toLocaleUpperCase('tr'), p[0], p[1]); }
  // stars
  const lim = fov > 80 ? 4.5 : fov > 45 ? 5.2 : 6.1, boost = Math.min(2.2, Math.max(0.8, 70 / fov));
  for (let i = 0; i < Cat.starMag.length; i++) {
    const m = Cat.starMag[i]; if (m > lim) break;
    if (!pr.p(fr.stars[3 * i], fr.stars[3 * i + 1], fr.stars[3 * i + 2], p) || !pr.on(p, 10)) continue;
    const r = Math.max(0.9, 4.2 - 0.62 * m) * boost, col = starCol(Cat.starBv[i]);
    if (m < 1.5) { g.fillStyle = col + '30'; g.beginPath(); g.arc(p[0], p[1], r * 2.6, 0, 7); g.fill(); }
    g.fillStyle = col; g.beginPath(); g.arc(p[0], p[1], r, 0, 7); g.fill();
  }
  g.textAlign = 'left'; g.font = '500 11px Inter, system-ui'; g.fillStyle = '#9FB3D9';
  for (const [o, v] of fr.named) {
    if ((o.mag ?? 9) > (fov < 40 ? 3.5 : 1.6)) continue;
    if (pr.p(v[0], v[1], v[2], p) && pr.on(p)) { hits.push([o, p[0], p[1]]); g.fillText(o.name.split(' (')[0], p[0] + 8, p[1] + 4); }
  }
  if (fov < 90) for (const [o, v] of fr.dsos) {
    if (!pr.p(v[0], v[1], v[2], p) || !pr.on(p)) continue;
    const sz = Math.min(400, Math.max(6, (o.size[0] / 60) * pr.f * deg));
    g.strokeStyle = typeColor(o) + '99'; g.setLineDash([5, 4]); g.lineWidth = 1.4; g.beginPath(); g.arc(p[0], p[1], sz / 2, 0, 7); g.stroke(); g.setLineDash([]);
    hits.push([o, p[0], p[1]]);
    if (fov < 50) { g.fillStyle = typeColor(o); g.fillText(o.id, p[0] + sz / 2 + 4, p[1] + 4); }
  }
  g.font = '600 12px Inter, system-ui';
  for (const [o, v, sp] of fr.bodies) {
    if (!pr.p(v[0], v[1], v[2], p) || !pr.on(p)) continue;
    const tr = (sp.diam || 0) / 120 * pr.f * deg;
    if (o.type === 'MOON') drawMoon(g, p[0], p[1], Math.max(14, tr), fr.phase);
    else if (o.type === 'SUN') { g.fillStyle = '#FFC94A55'; g.beginPath(); g.arc(p[0], p[1], Math.max(14, tr) * 2, 0, 7); g.fill(); g.fillStyle = '#FFD36B'; g.beginPath(); g.arc(p[0], p[1], Math.max(14, tr), 0, 7); g.fill(); }
    else { const c = typeColor(o); g.fillStyle = c + '40'; g.beginPath(); g.arc(p[0], p[1], 12, 0, 7); g.fill(); g.fillStyle = c; g.beginPath(); g.arc(p[0], p[1], Math.max(5, tr), 0, 7); g.fill(); }
    hits.push([o, p[0], p[1]]);
    g.fillStyle = typeColor(o); g.fillText(o.name, p[0] + 14, p[1] - 10);
  }
  if (target) {
    const tv = fr.enuOf(target);
    if (pr.p(tv[0], tv[1], tv[2], p) && pr.on(p, -30)) {
      g.strokeStyle = '#38C8FF'; g.lineWidth = 3; g.beginPath(); g.arc(p[0], p[1], 34, 0, 7); g.stroke();
      g.strokeStyle = '#38C8FF40'; g.lineWidth = 8; g.beginPath(); g.arc(p[0], p[1], 46, 0, 7); g.stroke();
    } else {
      const ang = pr.edge(tv[0], tv[1], tv[2]);
      const rx = W / 2 - 60, ry = H / 2 - 140;
      const t = Math.min(Math.abs(rx / Math.cos(ang)), Math.abs(ry / Math.sin(ang)));
      const tx = W / 2 + Math.cos(ang) * t, ty = H / 2 + Math.sin(ang) * t;
      g.save(); g.translate(tx, ty); g.rotate(ang); g.fillStyle = '#38C8FF';
      g.beginPath(); g.moveTo(34, 0); g.lineTo(-14, -22); g.lineTo(-4, 0); g.lineTo(-14, 22); g.closePath(); g.fill(); g.restore();
      g.textAlign = 'center'; g.fillStyle = '#E8EEF8'; g.fillText(target.name.split(' (')[0], tx, ty + 40); g.textAlign = 'left';
    }
  }
  g.strokeStyle = 'rgba(255,255,255,.4)'; g.lineWidth = 1.5; g.beginPath(); g.arc(W / 2, H / 2, 10, 0, 7); g.stroke();
}

function visible(pr, fr) {
  const p = [0, 0], out = [];
  for (const [o, v, sp] of fr.bodies) if (sp.alt > -1 && pr.p(v[0], v[1], v[2], p) && pr.on(p, -10)) out.push([o, sp.alt, 0]);
  for (const o of Cat.objects) {
    const rank = o.type === 'STAR' && (o.mag ?? 9) < 2.2 ? 1 : o.type === 'DSO' && (o.mag ?? 99) < 7.5 ? 2 : o.type === 'CONSTELLATION' ? 3 : -1;
    if (rank < 0) continue;
    const v = fr.enuOf(o); if (v[2] < 0) continue;
    if (pr.p(v[0], v[1], v[2], p) && pr.on(p, -10)) out.push([o, Math.asin(Math.max(-1, Math.min(1, v[2]))) / deg, rank]);
  }
  return out.sort((a, b) => a[2] - b[2] || (a[0].mag ?? 99) - (b[0].mag ?? 99)).slice(0, 14);
}

export function renderSky(root) {
  stopSky();
  let mode = store.get('skyMode', 'map'); // 'ar' | 'camera' | 'map'
  let fov = 70, manAz = 180, manAlt = 30, picked = null;
  let camFov = store.get('camFov', 67), yawFix = store.get('yawFix', 0);
  let m = manualMatrix(manAz, manAlt), sm = null, sensorOn = false, hasAbs = false, video = null, stream = null;
  let fr = buildFrame(), frT = Date.now();
  const hits = [];

  const canvas = h('canvas.skycanvas');
  const vid = h('video.skyvideo', { playsInline: true, muted: true, autoplay: true });
  const card = h('div.skycard');
  const chips = h('div.chips');
  const head = h('div.heading');
  const modes = h('div.seg');
  const fovRow = h('div.fovrow');
  root.replaceChildren(h('div.sky', {}, vid, canvas, h('div.skytop', {}, card), h('div.skybottom', {}, h('div.label', {}, 'Görüş alanında'), chips, head, fovRow, modes)));
  const g = canvas.getContext('2d');

  function onOrient(e) {
    if (e.alpha == null) return;
    let alpha = e.alpha;
    if (typeof e.webkitCompassHeading === 'number') alpha = 360 - e.webkitCompassHeading; // iOS: absolute heading
    else if (e.type !== 'deviceorientationabsolute' && hasAbs) return;
    const r = eulerMatrix(alpha, e.beta, e.gamma);
    if (!sm) sm = r; else for (let i = 0; i < 9; i++) sm[i] += (r[i] - sm[i]) * 0.25;
    sensorOn = true;
  }
  function onAbs(e) { hasAbs = true; onOrient(e); }
  async function startSensors() {
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
      try { if (await DeviceOrientationEvent.requestPermission() !== 'granted') { toast('Yön sensörü izni verilmedi.'); return false; } } catch { toast('Yön sensörü izni alınamadı.'); return false; }
    }
    window.addEventListener('deviceorientationabsolute', onAbs);
    window.addEventListener('deviceorientation', onOrient);
    return true;
  }
  function stopSensors() { window.removeEventListener('deviceorientationabsolute', onAbs); window.removeEventListener('deviceorientation', onOrient); }
  async function startCamera() {
    try {
      stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      vid.srcObject = stream; await vid.play(); vid.style.display = 'block'; return true;
    } catch { toast('Kameraya erişilemedi.'); return false; }
  }
  function stopCamera() { if (stream) stream.getTracks().forEach(t => t.stop()); stream = null; vid.style.display = 'none'; }

  async function setMode(md) {
    if (md !== 'map' && !(await startSensors())) md = 'map';
    if (md === 'camera' && !(await startCamera())) md = 'ar';
    if (md !== 'camera') stopCamera();
    if (md === 'map') { stopSensors(); if (sensorOn && sm) { manAz = (headingOf(withYaw(sm, yawFix)) + 360) % 360; manAlt = 30; } }
    mode = md; store.set('skyMode', md); ui();
  }

  function ui() {
    modes.replaceChildren(...[['camera', 'Kamera'], ['ar', 'AR Modu'], ['map', 'Harita']].map(([k, l]) =>
      h('button' + (k === mode ? '.on' : ''), { type: 'button', onclick: () => setMode(k) }, l)));
    fovRow.replaceChildren();
    if (mode === 'camera') {
      const r = h('input', { type: 'range', min: 40, max: 90, step: 0.5, value: camFov });
      r.addEventListener('input', () => { camFov = +r.value; store.set('camFov', camFov); });
      fovRow.append(h('small', {}, 'Kamera görüş açısı'), r);
    }
    if (mode !== 'map') fovRow.append(h('button.mini', { type: 'button', onclick: align }, 'Hizala'));
  }
  // Calibration: centre the target (or the Moon) in the circle and tap "Hizala".
  function align() {
    const o = S.target || Cat.byId.get('moon');
    const p = position(o, now(), obs());
    if (p.alt < -2) { toast(`${o.name} ufkun altında; başka bir hedef seçin.`); return; }
    const cur = headingOf(withYaw(sm || m, yawFix));
    yawFix = (yawFix + (p.az - cur) + 540) % 360 - 180;
    store.set('yawFix', yawFix);
    toast(`${o.name.split(' (')[0]} ortaya alındı; pusula ${fmt(yawFix, 1)}° düzeltildi.`);
  }

  function showCard() {
    const o = picked || S.target;
    if (!o) { card.replaceChildren(); return; }
    const p = position(o, now(), obs());
    card.replaceChildren(h('div.panel.row', {}, objIcon(o, 30, fr.phase),
      h('div.grow', {}, h('b', {}, o.name), h('small', {}, `Yükseklik ${fmt0(p.alt)}° · Yön ${dirName(p.az).toLocaleLowerCase('tr')} (${fmt0(p.az)}°)` + (fmtDist(o, p) ? ' · ' + fmtDist(o, p) : '') + (p.mag != null ? ` · kadir ${fmt(p.mag, 1)}` : '')),
        picked && picked !== S.target ? h('button.link', { type: 'button', onclick: () => { S.target = picked; picked = null; showCard(); } }, 'Hedef yap') : null),
      h('button.x', { type: 'button', onclick: () => { if (picked) picked = null; else S.target = null; showCard(); } }, '✕')));
  }

  // gestures
  const pts = new Map(); let pinch0 = 0, fov0 = fov, moved = false;
  canvas.addEventListener('pointerdown', e => { canvas.setPointerCapture(e.pointerId); pts.set(e.pointerId, [e.clientX, e.clientY]); moved = false; if (pts.size === 2) { const [a, b] = [...pts.values()]; pinch0 = Math.hypot(a[0] - b[0], a[1] - b[1]); fov0 = fov; } });
  canvas.addEventListener('pointermove', e => {
    const prev = pts.get(e.pointerId); if (!prev) return;
    const dx = e.clientX - prev[0], dy = e.clientY - prev[1]; pts.set(e.pointerId, [e.clientX, e.clientY]);
    if (Math.abs(dx) + Math.abs(dy) > 2) moved = true;
    if (pts.size === 2) { const [a, b] = [...pts.values()]; const d = Math.hypot(a[0] - b[0], a[1] - b[1]); if (pinch0 > 0 && mode !== 'camera') fov = Math.min(110, Math.max(8, fov0 * pinch0 / d)); }
    else if (mode === 'map') { const dpp = fov / canvas.clientHeight; manAz = ((manAz - dx * dpp) % 360 + 360) % 360; manAlt = Math.min(90, Math.max(-30, manAlt + dy * dpp)); }
  });
  const up = e => {
    if (pts.size === 1 && !moved) {
      const r = canvas.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
      let best = null, bd = 60; for (const [o, hx, hy] of hits) { const d = Math.hypot(hx - x, hy - y); if (d < bd) { bd = d; best = o; } }
      picked = best; showCard();
    }
    pts.delete(e.pointerId); if (pts.size < 2) pinch0 = 0;
  };
  canvas.addEventListener('pointerup', up); canvas.addEventListener('pointercancel', e => pts.delete(e.pointerId));
  canvas.addEventListener('wheel', e => { e.preventDefault(); if (mode !== 'camera') fov = Math.min(110, Math.max(8, fov * (e.deltaY > 0 ? 1.1 : 0.9))); }, { passive: false });

  let raf = 0, lastList = 0;
  function loop() {
    raf = requestAnimationFrame(loop);
    const dpr = window.devicePixelRatio || 1, W = canvas.clientWidth, H = canvas.clientHeight;
    if (canvas.width !== W * dpr || canvas.height !== H * dpr) { canvas.width = W * dpr; canvas.height = H * dpr; }
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    if (Date.now() - frT > 2000) { fr = buildFrame(); frT = Date.now(); }
    m = mode === 'map' || !sm ? manualMatrix(manAz, manAlt) : withYaw(sm, yawFix);
    const vf = mode === 'camera' ? camFov : fov;
    const pr = projector(m, W, H, vf);
    draw(g, pr, fr, vf, S.target, hits, mode === 'camera' && !!stream);
    if (Date.now() - lastList > 1500) {
      lastList = Date.now();
      chips.replaceChildren(...visible(pr, fr).map(([o, alt]) => h('button.vchip', { type: 'button', onclick: () => { picked = o; showCard(); } }, objIcon(o, 18, fr.phase), `${o.name.split(' (')[0]} · ${fmt0(alt)}°`)));
      head.textContent = mode === 'map' ? 'Sürükleyerek gezin, iki parmakla yakınlaştırın'
        : sensorOn ? `Bakış: ${dirName(headingOf(m))} ${fmt0(headingOf(m))}°` : 'Yön sensörü bekleniyor…';
    }
  }
  ui(); showCard();
  // iOS only grants motion sensors from a tap, so it always starts in map mode there.
  const needsTap = typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function';
  if (mode !== 'map') { if (needsTap) mode = 'map'; else setMode(mode); }
  loop();
  stopFn = () => { cancelAnimationFrame(raf); stopSensors(); stopCamera(); };
}
