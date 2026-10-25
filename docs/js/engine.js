// NexSky web — astronomy core (mirrors the Android SkyEngine.kt).
// Uses Astronomy Engine (MIT, Don Cross), loaded globally as `Astronomy`.
const A = window.Astronomy;
export const AU_KM = 149597870.7;

export const SOLAR = [
  { id: 'sun', name: 'Güneş', kind: 'Yıldız', type: 'SUN', body: 'Sun', alias: ['sun', 'gunes'] },
  { id: 'moon', name: 'Ay', kind: 'Doğal uydu', type: 'MOON', body: 'Moon', alias: ['moon', 'luna', 'dolunay', 'hilal'] },
  { id: 'mercury', name: 'Merkür', kind: 'Gezegen', type: 'PLANET', body: 'Mercury', alias: ['mercury', 'utarit'] },
  { id: 'venus', name: 'Venüs', kind: 'Gezegen', type: 'PLANET', body: 'Venus', alias: ['venus', 'coban yildizi', 'zuhre', 'sabah yildizi', 'aksam yildizi'] },
  { id: 'mars', name: 'Mars', kind: 'Gezegen', type: 'PLANET', body: 'Mars', alias: ['merih', 'kizil gezegen'] },
  { id: 'jupiter', name: 'Jüpiter', kind: 'Gezegen', type: 'PLANET', body: 'Jupiter', alias: ['jupiter', 'musteri'] },
  { id: 'saturn', name: 'Satürn', kind: 'Gezegen', type: 'PLANET', body: 'Saturn', alias: ['saturn', 'zuhal'] },
  { id: 'uranus', name: 'Uranüs', kind: 'Gezegen', type: 'PLANET', body: 'Uranus', alias: ['uranus'] },
  { id: 'neptune', name: 'Neptün', kind: 'Gezegen', type: 'PLANET', body: 'Neptune', alias: ['neptune'] },
];
const RADIUS_KM = { Sun: 695700, Moon: 1737.4, Mercury: 2439.7, Venus: 6051.8, Mars: 3389.5, Jupiter: 69911, Saturn: 58232, Uranus: 25362, Neptune: 24622 };
const TYPE_ORDER = { SUN: 0, MOON: 1, PLANET: 2, STAR: 3, DSO: 4, CONSTELLATION: 5 };

/** Lowercase, Turkish-aware, without diacritics. */
export function norm(s) {
  return s.replace(/I/g, 'ı').replace(/İ/g, 'i').toLowerCase()
    .replace(/ı/g, 'i').replace(/ş/g, 's').replace(/ğ/g, 'g').replace(/ç/g, 'c').replace(/ö/g, 'o').replace(/ü/g, 'u')
    .normalize('NFD').replace(/\p{Mn}+/gu, '');
}

export const Cat = { objects: [], stars: null, starMag: null, starBv: null, constellations: [], byId: new Map() };

export async function loadCatalog(base = 'data/') {
  const [objs, cons, starsBuf] = await Promise.all([
    fetch(base + 'objects.json').then(r => r.json()),
    fetch(base + 'constellations.json').then(r => r.json()),
    fetch(base + 'stars.bin').then(r => r.arrayBuffer()),
  ]);
  const list = SOLAR.map(o => ({ ...o, mag: null, size: [0, 0] }));
  for (const o of objs) list.push({
    id: o.id, name: o.name, kind: o.kind, type: o.kind === 'Yıldız' ? 'STAR' : 'DSO',
    ra: o.ra, dec: o.dec, mag: o.mag, size: o.size || [0, 0], alias: o.alias || [],
  });
  for (const c of cons) list.push({ id: c.id, name: c.name, kind: 'Takımyıldız', type: 'CONSTELLATION', ra: c.label[0], dec: c.label[1], mag: null, size: [0, 0], alias: [c.latin] });
  for (const o of list) { o.key = norm([o.name, o.id, ...(o.alias || [])].join(' ')); Cat.byId.set(o.id, o); }
  Cat.objects = list;
  Cat.constellations = cons;
  const dv = new DataView(starsBuf);
  const n = dv.getInt32(0, true);
  Cat.stars = new Float64Array(n * 3); Cat.starMag = new Float32Array(n); Cat.starBv = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const o = 4 + i * 16;
    const ra = dv.getFloat32(o, true) * Math.PI / 180, dec = dv.getFloat32(o + 4, true) * Math.PI / 180;
    Cat.stars[3 * i] = Math.cos(dec) * Math.cos(ra); Cat.stars[3 * i + 1] = Math.cos(dec) * Math.sin(ra); Cat.stars[3 * i + 2] = Math.sin(dec);
    Cat.starMag[i] = dv.getFloat32(o + 8, true); Cat.starBv[i] = dv.getFloat32(o + 12, true);
  }
}

export function search(q, limit = 40) {
  const k = norm(q.trim());
  if (!k) return [];
  const toks = k.split(/\s+/).filter(Boolean);
  return Cat.objects.filter(o => toks.every(t => o.key.includes(t)))
    .sort((a, b) => (norm(a.name).startsWith(k) ? 0 : 1) - (norm(b.name).startsWith(k) ? 0 : 1)
      || TYPE_ORDER[a.type] - TYPE_ORDER[b.type] || (a.mag ?? 99) - (b.mag ?? 99))
    .slice(0, limit);
}

export const observer = (lat, lon, h = 0) => new A.Observer(lat, lon, Math.max(0, h));

/** Where an object is now: az/alt (refracted), RA hours / Dec of date, distance AU, magnitude, diameter arcmin. */
export function position(o, date, obs) {
  if (o.body) {
    const eq = A.Equator(o.body, date, obs, true, true);
    const hor = A.Horizon(date, obs, eq.ra, eq.dec, 'normal');
    let mag = null;
    try { mag = A.Illumination(o.body, date).mag; } catch { /* sun on some versions */ }
    const diam = 2 * Math.atan(RADIUS_KM[o.body] / (eq.dist * AU_KM)) * 180 / Math.PI * 60;
    return { az: hor.azimuth, alt: hor.altitude, ra: eq.ra, dec: eq.dec, distAu: eq.dist, mag, diam };
  }
  const v = A.VectorFromSphere(new A.Spherical(o.dec, o.ra, 1), date);
  const h = A.HorizonFromVector(A.RotateVector(A.Rotation_EQJ_HOR(date, obs), v), 'normal');
  return { az: h.lon, alt: h.lat, ra: o.ra / 15, dec: o.dec, distAu: null, mag: o.mag, diam: o.size[0] > 0 ? o.size[0] : null };
}

function bodyFor(o) {
  if (o.body) return o.body;
  A.DefineStar('Star1', Math.min(23.99999, Math.max(0, o.ra / 15)), o.dec, 1000);
  return 'Star1';
}

/** Next rise / culmination / set within a day after `start`. */
export function riseSet(o, start, obs) {
  const b = bodyFor(o);
  const safe = f => { try { return f(); } catch { return null; } };
  const rise = safe(() => A.SearchRiseSet(b, obs, +1, start, 1));
  const set = safe(() => A.SearchRiseSet(b, obs, -1, start, 1));
  const tr = safe(() => A.SearchHourAngle(b, obs, 0, start));
  let alwaysUp = false, neverUp = false;
  if (!rise && !set) { if (position(o, start, obs).alt > 0) alwaysUp = true; else neverUp = true; }
  return { rise: rise && rise.date, set: set && set.date, transit: tr && tr.time.date, transitAlt: tr && tr.hor.altitude, alwaysUp, neverUp };
}

export const sunAltitude = (dir, start, obs, alt) => { try { const t = A.SearchAltitude('Sun', obs, dir, start, 1, alt); return t && t.date; } catch { return null; } };

export function constellationName(raH, dec) {
  try {
    const c = A.Constellation(raH, dec);
    const m = Cat.constellations.find(x => x.id === c.symbol);
    return m ? m.name : c.name;
  } catch { return ''; }
}

export function moonInfo(date) {
  const ph = A.MoonPhase(date);
  const ill = A.Illumination('Moon', date);
  const distKm = A.GeoMoon(date).Length() * AU_KM;
  const diamDeg = 2 * Math.atan(1737.4 / distKm) * 180 / Math.PI;
  const name = ph < 6 || ph > 354 ? 'Yeni Ay' : ph < 84 ? 'Büyüyen Hilal' : ph < 96 ? 'İlk Dördün' : ph < 174 ? 'Büyüyen Şişkin Ay'
    : ph < 186 ? 'Dolunay' : ph < 264 ? 'Küçülen Şişkin Ay' : ph < 276 ? 'Son Dördün' : 'Küçülen Hilal';
  const note = distKm < 362000 ? "süper ay'a yakın" : distKm > 400000 ? 'mikro ay' : 'ortalama';
  return { phase: ph, illuminated: ill.phase_fraction, distKm, diamDeg, name, note };
}

export function nextQuarters(start) {
  const out = []; let t = start;
  for (let i = 0; i < 4; i++) { const q = A.SearchMoonQuarter(t); out.push([q.quarter, q.time.date]); t = new Date(q.time.date.getTime() + 86400000); }
  return out;
}
export const quarterName = q => ['Yeni Ay', 'İlk Dördün', 'Dolunay', 'Son Dördün'][q];

export function libration(date) { try { const l = A.Libration(date); return [l.elat, l.elon]; } catch { return [0, 0]; } }

const DIRS = ['Kuzey', 'Kuzeydoğu', 'Doğu', 'Güneydoğu', 'Güney', 'Güneybatı', 'Batı', 'Kuzeybatı'];
export const dirName = az => DIRS[Math.floor((((az % 360) + 360) % 360 + 22.5) / 45) % 8];

/** J2000 → horizontal rotation as ENU-producing function. */
export function horFrame(date, obs) {
  const r = A.Rotation_EQJ_HOR(date, obs).rot;
  return (x, y, z, out, i = 0) => {
    const hx = r[0][0] * x + r[1][0] * y + r[2][0] * z;
    const hy = r[0][1] * x + r[1][1] * y + r[2][1] * z;
    const hz = r[0][2] * x + r[1][2] * y + r[2][2] * z;
    out[i] = -hy; out[i + 1] = hx; out[i + 2] = hz;
  };
}

export function enu(alt, az) {
  const a = alt * Math.PI / 180, z = az * Math.PI / 180;
  return [Math.cos(a) * Math.sin(z), Math.cos(a) * Math.cos(z), Math.sin(a)];
}
export function radecVec(raDeg, decDeg) {
  const ra = raDeg * Math.PI / 180, dec = decDeg * Math.PI / 180;
  return [Math.cos(dec) * Math.cos(ra), Math.cos(dec) * Math.sin(ra), Math.sin(dec)];
}

// ---- formatting
const nf0 = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 0 });
export const fmt0 = v => nf0.format(v);
export const fmt = (v, d = 1) => new Intl.NumberFormat('tr-TR', { minimumFractionDigits: d, maximumFractionDigits: d }).format(v);
export const hhmm = d => d ? d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : '—';
export const dayLabel = d => d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', weekday: 'long' });
export const shortDay = d => d ? d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' }) + ' ' + hhmm(d) : '—';
export function fmtAngle(deg) {
  if (deg >= 1) return fmt(deg, 2) + '°';
  if (deg * 60 >= 1) return fmt(deg * 60, 1) + '′';
  return fmt0(deg * 3600) + '″';
}
export function fmtDist(o, p) {
  if (p.distAu == null) return null;
  return o.type === 'MOON' ? fmt0(p.distAu * AU_KM) + ' km' : fmt(p.distAu, 2) + ' AB';
}
export function startOfDay(d) { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; }
