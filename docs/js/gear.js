// Cameras, lenses and the focal-length maths for the "Kadraj" planner.
const FF = [36, 24], CANON = [22.3, 14.9], APSC = [23.5, 15.6], M43 = [17.3, 13.0], S23 = [6.17, 4.55], ONE = [13.2, 8.8];
// Phones: a 4:3 "equivalent" sensor (diagonal 43.27 mm) so focal lengths are 35 mm-equivalent.
const PH = [34.62, 25.96];
const B = (name, group, s, fixedEq = null) => ({ name, group, w: s[0], h: s[1], fixedEq });

export const BODIES = [
  B('iPhone ultra geniş (13 mm)', 'Telefon', PH, [13, 13]),
  B('iPhone ana kamera (24–26 mm)', 'Telefon', PH, [24, 26]),
  B('iPhone 2× (48–52 mm)', 'Telefon', PH, [48, 52]),
  B('iPhone Pro 3× (77 mm)', 'Telefon', PH, [77, 77]),
  B('iPhone Pro 5× (120 mm)', 'Telefon', PH, [120, 120]),
  B('Android ana kamera (24 mm)', 'Telefon', PH, [24, 24]),
  B('Android telefoto 3× (70 mm)', 'Telefon', PH, [70, 70]),
  B('Android periskop 10× (230 mm)', 'Telefon', PH, [230, 230]),
  B('Nikon Coolpix P1000', 'Süper zoom', S23, [24, 3000]),
  B('Nikon Coolpix P950', 'Süper zoom', S23, [24, 2000]),
  B('Canon PowerShot SX70 HS', 'Süper zoom', S23, [21, 1365]),
  B('Panasonic Lumix FZ82', 'Süper zoom', S23, [20, 1200]),
  B('Sony RX10 IV', 'Süper zoom', ONE, [24, 600]),
  B('Panasonic Lumix FZ1000 II', 'Süper zoom', ONE, [25, 400]),
  B('Canon EOS R6 Mark II', 'Tam kare', FF), B('Canon EOS R5', 'Tam kare', FF), B('Canon EOS 6D Mark II', 'Tam kare', FF),
  B('Nikon Z6 III', 'Tam kare', FF), B('Nikon Z8', 'Tam kare', FF), B('Nikon D850', 'Tam kare', FF),
  B('Sony A7 IV', 'Tam kare', FF), B('Sony A7R V', 'Tam kare', FF),
  B('Canon EOS R7', 'APS-C', CANON), B('Canon EOS R10', 'APS-C', CANON), B('Canon EOS 90D', 'APS-C', CANON),
  B('Canon EOS 250D', 'APS-C', CANON), B('Canon EOS 2000D', 'APS-C', CANON), B('Canon EOS M50 Mark II', 'APS-C', CANON),
  B('Nikon D3500', 'APS-C', APSC), B('Nikon D7500', 'APS-C', APSC), B('Nikon Z50', 'APS-C', APSC),
  B('Sony A6000', 'APS-C', APSC), B('Sony A6100', 'APS-C', APSC), B('Sony A6400', 'APS-C', APSC), B('Sony A6600', 'APS-C', APSC), B('Sony A6700', 'APS-C', APSC),
  B('Fujifilm X-T5', 'APS-C', APSC), B('Fujifilm X-S20', 'APS-C', APSC),
  B('OM System OM-1', 'Mikro 4/3', M43), B('Panasonic Lumix G9 II', 'Mikro 4/3', M43),
  B('ZWO ASI585MC', 'Astro kamera', [11.2, 6.3]), B('ZWO ASI533MC Pro', 'Astro kamera', [11.31, 11.31]), B('ZWO ASI2600MC Pro', 'Astro kamera', [23.5, 15.7]),
];
export const CUSTOM_BODY = 'Kendi kameram (sensör ölçüsü gir)';

const L = (name, group, min, max = min) => ({ name, group, min, max });
export const LENSES = [
  L('14 mm geniş açı (Samanyolu)', 'Objektif', 14), L('24 mm f/1.4', 'Objektif', 24), L('50 mm f/1.8', 'Objektif', 50),
  L('18–55 mm kit', 'Objektif', 18, 55), L('24–70 mm', 'Objektif', 24, 70), L('70–200 mm', 'Objektif', 70, 200),
  L('55–250 mm (Canon)', 'Objektif', 55, 250), L('70–300 mm', 'Objektif', 70, 300), L('100–400 mm', 'Objektif', 100, 400),
  L('150–600 mm (Sigma / Tamron)', 'Objektif', 150, 600), L('180–600 mm (Nikon Z)', 'Objektif', 180, 600),
  L('200–600 mm (Sony)', 'Objektif', 200, 600), L('200–800 mm (Canon RF)', 'Objektif', 200, 800),
  L('Tamron 18–400 mm', 'Objektif', 18, 400), L('Sony 70–350 mm (APS-C)', 'Objektif', 70, 350), L('Sigma 60–600 mm', 'Objektif', 60, 600),
  L('500 mm f/5.6 PF (Nikon)', 'Objektif', 500), L('800 mm f/11 (Canon RF)', 'Objektif', 800),
  L('420–800 mm manuel tele', 'Ekonomik süper tele', 420, 800), L('650–1300 mm manuel tele', 'Ekonomik süper tele', 650, 1300),
  L('500 mm aynalı (katadioptrik)', 'Ekonomik süper tele', 500), L('900 mm f/8 manuel tele', 'Ekonomik süper tele', 900),
  L('RedCat 51 (250 mm)', 'Teleskop', 250), L('Evostar 72ED (420 mm)', 'Teleskop', 420), L('Evostar 80ED (600 mm)', 'Teleskop', 600),
  L('Newton 130/650', 'Teleskop', 650), L('Newton 150/750', 'Teleskop', 750), L('C8 + 0,63× düşürücü (1280 mm)', 'Teleskop', 1280),
  L('Maksutov 102 (1300 mm)', 'Teleskop', 1300), L('Maksutov 127 (1500 mm)', 'Teleskop', 1500),
  L('Celestron C8 (2032 mm)', 'Teleskop', 2032), L('Celestron C11 (2800 mm)', 'Teleskop', 2800),
];
export const CUSTOM_LENS = 'Kendi objektifim (odak aralığı gir)';

export const crop = b => 43.267 / Math.hypot(b.w, b.h);
/** Field of view (degrees, long × short) at a real focal length. */
export const fovReal = (b, f) => [2 * Math.atan(b.w / (2 * f)) * 180 / Math.PI, 2 * Math.atan(b.h / (2 * f)) * 180 / Math.PI];
/** Real focal (mm) at which an object of `deg` spans `frac` of the short side. */
export const neededFocal = (b, deg, frac) => b.h / (2 * Math.tan((deg / frac) * Math.PI / 360));
export function fmtFocal(mm) {
  const r = mm >= 10000 ? Math.round(mm / 100) * 100 : mm >= 1000 ? Math.round(mm / 10) * 10 : Math.round(mm);
  return new Intl.NumberFormat('tr-TR').format(r) + ' mm';
}
