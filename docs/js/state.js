import { observer, startOfDay } from './engine.js';
import { store } from './ui.js';

const saved = store.get('loc', null);
export const S = {
  lat: saved ? saved.lat : 37.7648, lon: saved ? saved.lon : 38.2786, hgt: saved ? saved.h : 670, located: !!saved,
  target: null,          // a catalog object
  planDay: startOfDay(new Date()),
  tab: 0, plannerTab: 0,
  listeners: new Set(),
};
export const obs = () => observer(S.lat, S.lon, S.hgt);
export const now = () => new Date();
export function emit() { S.listeners.forEach(f => { try { f(); } catch (e) { console.error(e); } }); }

export function watchLocation() {
  if (!navigator.geolocation) return;
  navigator.geolocation.watchPosition(p => {
    S.lat = p.coords.latitude; S.lon = p.coords.longitude; if (p.coords.altitude != null) S.hgt = p.coords.altitude;
    const first = !S.located; S.located = true;
    store.set('loc', { lat: S.lat, lon: S.lon, h: S.hgt });
    if (first) emit();
  }, () => {}, { enableHighAccuracy: false, maximumAge: 300000, timeout: 30000 });
}
