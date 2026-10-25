// Photographic Moon: NASA LRO colour map (SVS CGI Moon Kit, public domain)
// wrapped onto a sphere as seen from Earth, lit for the phase.
import { libration } from './engine.js';

const SIZE = 360;
let tex = null, texW = 0, texH = 0;
let base = null, baseKey = '';
const cache = new Map();
let lib = [0, 0];

export async function loadMoon(url = 'data/moon_map.jpg') {
  const img = new Image();
  img.src = url;
  await img.decode();
  const c = document.createElement('canvas');
  c.width = img.naturalWidth; c.height = img.naturalHeight;
  const g = c.getContext('2d', { willReadFrequently: true });
  g.drawImage(img, 0, 0);
  tex = g.getImageData(0, 0, c.width, c.height).data;
  texW = c.width; texH = c.height;
}
export const moonReady = () => tex != null;
export function setMoonTime(date) { lib = libration(date); }

function buildBase(lat0, lon0) {
  const n = SIZE * SIZE;
  const col = new Uint8ClampedArray(n * 4), nx = new Float32Array(n), nz = new Float32Array(n);
  const cp = Math.cos(lat0 * Math.PI / 180), sp = Math.sin(lat0 * Math.PI / 180), lon0r = lon0 * Math.PI / 180;
  const half = SIZE / 2;
  for (let j = 0; j < SIZE; j++) {
    const y = (half - j - 0.5) / half;
    for (let i = 0; i < SIZE; i++) {
      const x = (i + 0.5 - half) / half, rr = x * x + y * y, k = j * SIZE + i;
      if (rr > 1) continue;
      const z = Math.sqrt(1 - rr);
      const y2 = y * cp + z * sp, z2 = -y * sp + z * cp;
      const lat = Math.asin(Math.max(-1, Math.min(1, y2)));
      const lon = Math.atan2(x, z2) + lon0r;
      let u = ((lon * 180 / Math.PI + 180) / 360) % 1; if (u < 0) u += 1;
      const v = (90 - lat * 180 / Math.PI) / 180;
      const tx = Math.min(texW - 1, (u * texW) | 0), ty = Math.min(texH - 1, (v * texH) | 0);
      const t = (ty * texW + tx) * 4;
      col[k * 4] = tex[t]; col[k * 4 + 1] = tex[t + 1]; col[k * 4 + 2] = tex[t + 2]; col[k * 4 + 3] = 255;
      nx[k] = x; nz[k] = z;
    }
  }
  base = { col, nx, nz };
}

/** A canvas with the Moon for `phaseDeg` (0 new … 180 full …), north up, waxing lit on the right. */
export function moonCanvas(phaseDeg) {
  if (!tex) return null;
  const lat0 = Math.round(lib[0] * 2) / 2, lon0 = Math.round(lib[1] * 2) / 2;
  const bk = lat0 + '/' + lon0;
  if (bk !== baseKey) { buildBase(lat0, lon0); baseKey = bk; cache.clear(); }
  const ph = Math.round((((phaseDeg % 360) + 360) % 360));
  const key = bk + '/' + ph;
  if (cache.has(key)) return cache.get(key);
  const e = ph * Math.PI / 180, sx = Math.sin(e), sz = -Math.cos(e);
  const c = document.createElement('canvas'); c.width = c.height = SIZE;
  const g = c.getContext('2d');
  const id = g.createImageData(SIZE, SIZE);
  const { col, nx, nz } = base;
  for (let k = 0; k < SIZE * SIZE; k++) {
    if (col[k * 4 + 3] === 0) continue;
    const lit = Math.max(0, Math.min(1, (nx[k] * sx + nz[k] * sz) * 6 + 0.5));
    const f = 0.05 + 0.95 * lit;
    id.data[k * 4] = col[k * 4] * f; id.data[k * 4 + 1] = col[k * 4 + 1] * f; id.data[k * 4 + 2] = col[k * 4 + 2] * f; id.data[k * 4 + 3] = 255;
  }
  g.putImageData(id, 0, 0);
  cache.set(key, c);
  if (cache.size > 24) cache.delete(cache.keys().next().value);
  return c;
}

/** Draws the Moon centred at (cx, cy) with radius r; falls back to a grey disc. */
export function drawMoon(g, cx, cy, r, phaseDeg) {
  const m = moonCanvas(phaseDeg);
  if (m) { g.drawImage(m, cx - r, cy - r, 2 * r, 2 * r); return; }
  g.fillStyle = '#c9cbcc'; g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.fill();
}
