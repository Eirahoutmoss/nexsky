import { h, panel, label } from './ui.js';
import { moonReady } from './moon.js';
export const VERSION = '0.1.0';

export function renderAbout(root, back) {
  root.replaceChildren(h('div.page', {},
    h('div.dhead', {}, h('button.back', { type: 'button', onclick: back }, '‹'), h('h2', {}, 'Hakkında')),
    h('div.center', {}, h('img', { src: 'icons/icon-192.png', width: 96, height: 96, alt: '' }),
      h('div.wordmark.big', { html: 'Nex<span>Sky</span>' }), h('small.tag', {}, 'GÖKYÜZÜ SENİN REHBERİN'),
      h('small.muted', {}, `Web sürümü ${VERSION} · Ay görüntüsü: ${moonReady() ? 'yüklü' : 'yükleniyor'}`)),
    panel(h('p', {}, 'Ay ve gökyüzü fotoğrafçıları için ücretsiz, açık kaynak gökyüzü rehberi ve çekim planlayıcı. Gezegenleri, yıldızları, bulutsuları ve galaksileri bul; Ayın doğuşunu, evresini ve büyüklüğünü planla; makinenin kadrajında nasıl görüneceğini çekimden önce gör.')),
    panel(label('Güncelleme'), h('p', {}, 'Web sürümü kendiliğinden güncellenir: yeni sürüm yayımlandığında uygulamayı bir sonraki açışında yenisi yüklenir. İlk açılıştan sonra internetsiz de çalışır (harita altlığı hariç).'),
      h('button.btn', { type: 'button', onclick: async () => { const r = await navigator.serviceWorker?.getRegistration(); await r?.update(); location.reload(); } }, 'Şimdi denetle')),
    panel(label("iPhone'a kurulum"), h('p', {}, 'Safari\'de bu sayfayı açın → Paylaş (□↑) → Ana Ekrana Ekle. NexSky simgesiyle tam ekran açılır.')),
    panel(label('Geliştiren'), h('b', {}, 'Hasan Güler'), h('small.muted', {}, 'Nex uygulama ailesi'),
      h('a', { href: 'https://github.com/Eirahoutmoss/nexsky', target: '_blank', rel: 'noopener' }, 'github.com/Eirahoutmoss/nexsky')),
    panel(label('Gizlilik'), h('p', {}, 'Konumun yalnızca cihazında gökyüzü hesabı için kullanılır, hiçbir yere gönderilmez. Hesap yok, reklam yok, izleme yok.')),
    panel(label('Lisans ve teşekkür'), h('p', {}, 'NexSky, MIT lisansıyla açık kaynaktır. © 2026 Hasan Güler'),
      h('ul.credits', {}, ['Astronomy Engine — Don Cross (MIT): konum hesapları', 'd3-celestial — Olaf Frohn (BSD-3): yıldız ve derin uzay katalogları',
        'NASA Scientific Visualization Studio, LRO (kamu malı): Ay görüntüsü — CGI Moon Kit', 'Leaflet (BSD-2) ve OpenStreetMap katkıda bulunanlar (ODbL): harita',
        'Inter — Rasmus Andersson (SIL OFL 1.1): yazı tipi'].map(t => h('li', {}, t))))));
}
