import { loadCatalog } from './engine.js';
import { loadMoon, setMoonTime } from './moon.js';
import { S, watchLocation } from './state.js';
import { h } from './ui.js';
import { renderSearch } from './search.js';
import { renderSky, stopSky } from './sky.js';
import { renderPlanner } from './planner.js';
import { renderMap, stopMap } from './map.js';
import { renderAbout } from './about.js';

const main = document.getElementById('main');
const nav = document.getElementById('nav');
const loc = document.getElementById('loc');
const TABS = [['Arama', '⌕'], ['Gökyüzü', '✦'], ['Planlayıcı', '◷'], ['Harita', '⌖']];

function go(i) {
  stopSky(); stopMap();
  S.tab = i;
  nav.querySelectorAll('button').forEach((b, k) => b.classList.toggle('on', k === i));
  main.classList.toggle('full', i === 1 || i === 3);
  [renderSearch, renderSky, renderPlanner, renderMap][i](main, go);
}

function showLoc() { loc.textContent = S.located ? `${S.lat.toFixed(2)}°, ${S.lon.toFixed(2)}°` : 'konum bekleniyor'; }

async function start() {
  nav.replaceChildren(...TABS.map(([l, ic], i) => h('button', { type: 'button', onclick: () => go(i) }, h('span.ic', {}, ic), h('span', {}, l))));
  document.getElementById('about').addEventListener('click', () => { stopSky(); stopMap(); main.classList.remove('full'); renderAbout(main, () => go(S.tab)); });
  main.replaceChildren(h('p.loading', {}, 'Gök kataloğu yükleniyor…'));
  await loadCatalog();
  loadMoon().then(() => { setMoonTime(new Date()); if (S.tab !== 1 && S.tab !== 3) go(S.tab); }).catch(() => {});
  S.listeners.add(() => { showLoc(); if (S.tab !== 1) go(S.tab); });
  showLoc(); watchLocation();
  go(0);
  setInterval(() => setMoonTime(new Date()), 600000);
}
start();

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').then(reg => {
    reg.addEventListener('updatefound', () => {
      const w = reg.installing;
      w && w.addEventListener('statechange', () => {
        if (w.state === 'installed' && navigator.serviceWorker.controller) {
          const bar = h('div.toast', {}, 'NexSky güncellendi. ', h('button.link', { type: 'button', onclick: () => location.reload() }, 'Yenile'));
          document.body.append(bar);
        }
      });
    });
  }).catch(() => {});
}
