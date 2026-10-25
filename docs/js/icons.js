// Small canvas icons for objects (mirrors Common.kt ObjIcon).
import { drawMoon } from './moon.js';

export function typeColor(o) {
  switch (o.type) {
    case 'SUN': return '#FFC94A';
    case 'MOON': return '#DCE3EE';
    case 'PLANET': return { mars: '#E4703F', jupiter: '#E8C39A', saturn: '#E9D08C', venus: '#FFF1C2', mercury: '#B9B1A6', uranus: '#9BE3E8' }[o.id] || '#7FA6FF';
    case 'STAR': return '#FFFFFF';
    case 'DSO': return o.kind === 'Galaksi' ? '#B79CFF' : o.kind.includes('küme') ? '#8FD3FF' : '#FF8AC2';
    default: return '#6E86B8';
  }
}

export function sparkle(g, x, y, r, col) {
  const k = r * 0.22;
  g.fillStyle = col; g.beginPath();
  g.moveTo(x, y - r); g.quadraticCurveTo(x + k, y - k, x + r, y); g.quadraticCurveTo(x + k, y + k, x, y + r);
  g.quadraticCurveTo(x - k, y + k, x - r, y); g.quadraticCurveTo(x - k, y - k, x, y - r); g.fill();
}

export function objIcon(o, size = 34, phase = 150) {
  const c = document.createElement('canvas');
  const d = window.devicePixelRatio || 1;
  c.width = c.height = size * d; c.style.width = c.style.height = size + 'px';
  const g = c.getContext('2d'); g.scale(d, d);
  const r = size / 2, col = typeColor(o);
  if (o.type === 'MOON') drawMoon(g, r, r, r * 0.95, phase);
  else if (o.type === 'PLANET' || o.type === 'SUN') {
    const gr = g.createRadialGradient(r * 0.8, r * 0.8, 1, r, r, r);
    gr.addColorStop(0, '#fff'); gr.addColorStop(0.5, col); gr.addColorStop(1, col + '99');
    g.fillStyle = gr; g.beginPath(); g.arc(r, r, r / 1.15, 0, 7); g.fill();
    if (o.id === 'saturn') { g.strokeStyle = col; g.lineWidth = 2; g.beginPath(); g.ellipse(r, r, r, r * 0.25, -0.3, 0, 7); g.stroke(); }
  } else if (o.type === 'STAR') sparkle(g, r, r, r / 1.1, col);
  else if (o.type === 'DSO') {
    const gr = g.createRadialGradient(r, r, 0, r, r, r); gr.addColorStop(0, col); gr.addColorStop(1, col + '00');
    g.fillStyle = gr; g.fillRect(0, 0, size, size); g.fillStyle = '#fff'; g.beginPath(); g.arc(r, r, 2, 0, 7); g.fill();
  } else {
    const p = [[0.15, 0.7], [0.4, 0.35], [0.65, 0.55], [0.88, 0.2]].map(([x, y]) => [x * size, y * size]);
    g.strokeStyle = col; g.lineWidth = 1.5; g.beginPath(); p.forEach(([x, y], i) => i ? g.lineTo(x, y) : g.moveTo(x, y)); g.stroke();
    g.fillStyle = '#fff'; p.forEach(([x, y]) => { g.beginPath(); g.arc(x, y, 2, 0, 7); g.fill(); });
  }
  c.className = 'icon';
  return c;
}
