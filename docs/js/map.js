// Map: rise/set directions from where you are, and where to stand so the
// rising Moon appears behind a chosen foreground subject.
import { Cat, riseSet, position, moonInfo, dirName, hhmm, dayLabel, fmt, fmt0, startOfDay } from './engine.js';
import { S, obs, now } from './state.js';
import { h, panel, chip } from './ui.js';

let map = null;
export function stopMap() { if (map) { map.remove(); map = null; } }

function dest(lat, lon, km, az) {
  const R = 6371, d = km / R, b = az * Math.PI / 180, p1 = lat * Math.PI / 180, l1 = lon * Math.PI / 180;
  const p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(b));
  const l2 = l1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
  return [p2 * 180 / Math.PI, l2 * 180 / Math.PI];
}
function distBearing(a, b) {
  const R = 6371, p1 = a[0] * Math.PI / 180, p2 = b[0] * Math.PI / 180, dl = (b[1] - a[1]) * Math.PI / 180;
  const x = Math.sin((p2 - p1) / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  const d = 2 * R * Math.asin(Math.sqrt(x));
  const br = (Math.atan2(Math.sin(dl) * Math.cos(p2), Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)) * 180 / Math.PI + 360) % 360;
  return [d, br];
}

export function renderMap(root) {
  stopMap();
  let subject = null, subjectH = 20, ratio = 1, showSun = true;
  const el = h('div.mapview');
  const top = h('div.maptop'), bottom = h('div.mapbottom');
  root.replaceChildren(h('div.mapwrap', {}, el, top, bottom,
    h('button.fab', { type: 'button', title: 'Konumum', onclick: () => map.setView([S.lat, S.lon], Math.max(map.getZoom(), 13)) }, '⌖')));
  map = L.map(el, { zoomControl: false, attributionControl: true }).setView([S.lat, S.lon], 13);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OpenStreetMap katkıda bulunanlar' }).addTo(map);
  const layer = L.layerGroup().addTo(map);
  map.on('contextmenu', e => { subject = [e.latlng.lat, e.latlng.lng]; draw(); });

  function draw() {
    const ob = obs(), moon = Cat.byId.get('moon'), sun = Cat.byId.get('sun');
    const mrs = riseSet(moon, S.planDay, ob), srs = riseSet(sun, S.planDay, ob);
    const az = [mrs.rise && position(moon, mrs.rise, ob).az, mrs.set && position(moon, mrs.set, ob).az,
      srs.rise && position(sun, srs.rise, ob).az, srs.set && position(sun, srs.set, ob).az];
    const diam = moonInfo(mrs.rise || new Date(S.planDay.getTime() + 20 * 3600e3)).diamDeg;
    const standKm = subjectH / Math.tan(diam / ratio * Math.PI / 180) / 1000;
    const stand = subject && az[0] != null ? dest(subject[0], subject[1], standKm, (az[0] + 180) % 360) : null;
    layer.clearLayers();
    const here = [S.lat, S.lon];
    const ray = (from, a, km, color, dash, lbl) => {
      const to = dest(from[0], from[1], km, a);
      L.polyline([from, to], { color, weight: 4, dashArray: dash ? '10 8' : null }).addTo(layer);
      L.marker(to, { icon: L.divIcon({ className: 'raylabel', html: `<span style="color:${color}">${lbl}</span>`, iconSize: [120, 20], iconAnchor: [-6, 10] }) }).addTo(layer);
    };
    if (az[0] != null) ray(stand || here, az[0], stand ? standKm + 2 : 4, '#FFD66B', false, `Ay doğuşu ${fmt0(az[0])}°`);
    if (az[1] != null) ray(here, az[1], 4, '#FFB25A', true, 'Ay batışı');
    if (showSun) { if (az[2] != null) ray(here, az[2], 3, '#FF8A3D', false, 'Gün doğumu'); if (az[3] != null) ray(here, az[3], 3, '#FF5D5D', true, 'Gün batımı'); }
    const tgt = S.target && S.target.type !== 'MOON' && S.target.type !== 'SUN' ? S.target : null;
    if (tgt) ray(here, position(tgt, now(), ob).az, 3, '#38C8FF', false, tgt.name.split(' (')[0]);
    const dot = (p, c) => L.circleMarker(p, { radius: 9, color: '#fff', weight: 3, fillColor: c, fillOpacity: 1 }).addTo(layer);
    dot(here, '#1E7BFF'); if (subject) dot(subject, '#B79CFF'); if (stand) dot(stand, '#2FD18C');

    top.replaceChildren(panel(h('b', {}, dayLabel(S.planDay)),
      h('small.muted', {}, `Ay doğuşu ${hhmm(mrs.rise)}` + (az[0] != null ? ` · ${fmt0(az[0])}° ${dirName(az[0]).toLocaleLowerCase('tr')}` : '') + `   Gün doğumu ${hhmm(srs.rise)} · batımı ${hhmm(srs.set)}`),
      h('div.chips', {}, chip('Güneş çizgileri', showSun, () => { showSun = !showSun; draw(); }),
        chip('Yarın', false, () => { S.planDay = startOfDay(new Date(S.planDay.getTime() + 36 * 3600e3)); draw(); }),
        chip('Bugün', false, () => { S.planDay = startOfDay(new Date()); draw(); }))));
    if (!subject) bottom.replaceChildren(panel(h('b', {}, 'Çekim noktası bul'),
      h('small.muted', {}, "Haritada ön plana almak istediğin yere (kale, minare, ağaç…) uzun bas. NexSky, doğan Ay'ı onun arkasında görmek için nerede durman gerektiğini hesaplar.")));
    else {
      const [d, br] = stand ? distBearing(here, stand) : [0, 0];
      bottom.replaceChildren(panel(h('b.good', {}, 'Önerilen çekim noktası (yeşil)'),
        stand ? h('p', {}, `Ön plandan ${fmt(standKm, 1)} km uzakta; sana ${fmt(d, 1)} km, ${dirName(br).toLocaleLowerCase('tr')} yönünde. Ay ${hhmm(mrs.rise)} doğarken ön planın arkasında görünür.`) : h('p.warn', {}, 'Bu gün Ay doğmuyor.'),
        h('small.muted', {}, 'Ön plan yüksekliği'), h('div.chips', {}, [10, 20, 40, 80, 150].map(v => chip(v + ' m', subjectH === v, () => { subjectH = v; draw(); }))),
        h('small.muted', {}, "Ay'ın boyu, ön plana göre"), h('div.chips', {}, [[0.5, 'yarısı'], [1, 'aynı'], [2, '2 katı']].map(([r, l]) => chip(l, ratio === r, () => { ratio = r; draw(); })), chip('Temizle', false, () => { subject = null; draw(); })),
        h('small.muted', {}, 'Arazi yüksekliği hesaba katılmaz; ufukta dağ varsa Ay birkaç dakika geç görünür.')));
    }
  }
  draw();
}
