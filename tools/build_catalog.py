"""Builds app/src/main/assets from d3-celestial data (BSD-3, Olaf Frohn).
Usage: python3 tools/build_catalog.py <d3-celestial/data>"""
import json, struct, sys, os
src = sys.argv[1]
out = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')
os.makedirs(out, exist_ok=True)
def J(f):
    txt = open(os.path.join(src, f), encoding='utf-8').read()
    for ch in '\u2005\u2009\u00a0\u202f': txt = txt.replace(ch, ' ')
    return json.loads(txt)
ra = lambda lon: lon % 360.0

# --- stars (mag <= 6), for the sky view
stars = sorted(J('stars.6.json')['features'], key=lambda f: f['properties']['mag'])
with open(os.path.join(out, 'stars.bin'), 'wb') as fh:
    fh.write(struct.pack('<i', len(stars)))
    for f in stars:
        lon, lat = f['geometry']['coordinates']
        bv = f['properties'].get('bv')
        try: bv = float(bv)
        except (TypeError, ValueError): bv = 0.6
        fh.write(struct.pack('<ffff', ra(lon), lat, float(f['properties']['mag']), bv))
hipmag = {str(f['id']): (f['properties']['mag'], f['geometry']['coordinates']) for f in stars}

TYPES = {
    'oc': 'Açık yıldız kümesi', 'gc': 'Küresel yıldız kümesi', 'pn': 'Gezegenimsi bulutsu',
    'snr': 'Süpernova kalıntısı', 'en': 'Salma bulutsusu', 'bn': 'Parlak bulutsu', 'rn': 'Yansıma bulutsusu',
    'sfr': 'Bulutsu (yıldız doğum bölgesi)', 'dn': 'Karanlık bulutsu', 'pos': 'Yıldız grubu', 'as': 'Yıldız grubu',
    'i': 'Samanyolu yıldız bulutu',
}
GAL = {'s', 'sb', 'sd', 's0', 'e', 'g', 'gg', 'i'}
def kind(t, mid=''):
    if t == 'i' and mid == 'M24': return 'Samanyolu yıldız bulutu'
    if t in GAL: return 'Galaksi'
    return TYPES.get(t, 'Derin uzay cismi')

MESSIER_TR = {
    'M1': 'Yengeç Bulutsusu', 'M8': 'Lagün Bulutsusu', 'M11': 'Yaban Ördeği Kümesi', 'M13': 'Büyük Herkül Kümesi',
    'M16': 'Kartal Bulutsusu', 'M17': 'Omega Bulutsusu', 'M20': 'Üç Boğumlu Bulutsu (Trifid)', 'M27': 'Halter Bulutsusu',
    'M31': 'Andromeda Galaksisi', 'M33': 'Üçgen Galaksisi', 'M42': 'Orion Bulutsusu', 'M43': 'De Mairan Bulutsusu',
    'M44': 'Arı Kovanı Kümesi', 'M45': 'Ülker (Pleiades)', 'M51': 'Girdap Galaksisi', 'M57': 'Halka Bulutsusu',
    'M63': 'Ayçiçeği Galaksisi', 'M64': 'Kara Göz Galaksisi', 'M81': 'Bode Galaksisi', 'M82': 'Puro Galaksisi',
    'M83': 'Güney Fırıldak Galaksisi', 'M87': 'Başak A Galaksisi', 'M97': 'Baykuş Bulutsusu', 'M101': 'Fırıldak Galaksisi',
    'M104': 'Sombrero Galaksisi', 'M6': 'Kelebek Kümesi', 'M7': 'Ptolemy Kümesi', 'M76': 'Küçük Halter Bulutsusu',
    'M24': 'Yay Yıldız Bulutu', 'M22': 'Yay Küresel Kümesi', 'M3': 'Küresel Küme M3', 'M5': 'Küresel Küme M5',
    'M15': 'Pegasus Küresel Kümesi', 'M35': 'İkizler Açık Kümesi', 'M39': 'Kuğu Açık Kümesi', 'M78': 'M78 Yansıma Bulutsusu',
    'M110': 'Andromeda uydusu M110', 'M32': 'Andromeda uydusu M32', 'M4': 'Akrep Küresel Kümesi', 'M74': 'Hayalet Galaksi',
    'M106': 'M106 Galaksisi', 'M94': 'Kedi Gözü Galaksisi', 'M102': 'İğ Galaksisi', 'M77': 'Balina A Galaksisi',
}
EXTRA = {  # id: (Turkish name, aliases)
    'NGC 869': ('Çift Küme (h Persei)', ['ngc 869', 'ngc 884', 'cift kume', 'double cluster']),
    'NGC 884': ('Çift Küme (χ Persei)', ['ngc 884']),
    'NGC 7000': ('Kuzey Amerika Bulutsusu', ['north america nebula']),
    'IC 5070': ('Pelikan Bulutsusu', ['pelican nebula']),
    'IC 434': ('Atbaşı Bulutsusu (IC 434)', ['atbasi', 'horsehead', 'b33', 'barnard 33']),
    'NGC 2024': ('Alev Bulutsusu', ['flame nebula']),
    'NGC 1977': ('Koşan Adam Bulutsusu', ['running man']),
    'NGC 253': ('Heykeltıraş Galaksisi', ['sculptor galaxy', 'gumus dolar']),
    'NGC 6960': ('Peçe Bulutsusu (Batı)', ['veil nebula', 'cadi supurgesi', 'witchs broom']),
    'NGC 6992': ('Peçe Bulutsusu (Doğu)', ['veil nebula', 'eastern veil']),
    'NGC 7293': ('Sarmal Bulutsusu (Helix)', ['helix nebula', 'tanri gozu']),
    'NGC 2244': ('Rozet Bulutsusu (NGC 2244)', ['rosette nebula', 'ngc 2237']),
    'IC 1805': ('Kalp Bulutsusu', ['heart nebula']),
    'IC 1848': ('Ruh Bulutsusu', ['soul nebula']),
    'NGC 6543': ('Kedi Gözü Bulutsusu', ['cats eye nebula']),
    'NGC 891': ('NGC 891 Galaksisi', []),
    'NGC 2392': ('Eskimo Bulutsusu', ['eskimo nebula', 'clownface']),
    'NGC 6888': ('Hilal Bulutsusu', ['crescent nebula']),
    'NGC 7635': ('Kabarcık Bulutsusu', ['bubble nebula']),
    'NGC 5128': ('Erboğa A Galaksisi', ['centaurus a']),
    'NGC 2070': ('Tarantula Bulutsusu', ['tarantula nebula']),
    'NGC 7009': ('Satürn Bulutsusu', ['saturn nebula']),
    'NGC 4631': ('Balina Galaksisi', ['whale galaxy']),
    'NGC 1499': ('Kaliforniya Bulutsusu', ['california nebula']),
    'NGC 2264': ('Koni Bulutsusu / Noel Ağacı Kümesi', ['cone nebula', 'christmas tree']),
    'NGC 5139': ('Omega Erboğa Kümesi', ['omega centauri']),
    'NGC 104': ('47 Tukan Kümesi', ['47 tucanae']),
    'NGC 3372': ('Karina Bulutsusu', ['carina nebula']),
    'IC 1396': ('Fil Hortumu Bulutsusu', ['elephants trunk']),
    'NGC 281': ('Pacman Bulutsusu', ['pacman nebula']),
    'NGC 7380': ('Büyücü Bulutsusu', ['wizard nebula']),
    'NGC 1333': ('NGC 1333 Bulutsusu', []),
    'NGC 752': ('NGC 752 Açık Kümesi', []),
}
def dims(s):
    try:
        p = [float(x) for x in str(s).split('x')]
        return [p[0], p[1] if len(p) > 1 else p[0]]
    except ValueError:
        return [0, 0]
def magv(m):
    try:
        m = float(m); return None if m >= 99 else m
    except (TypeError, ValueError): return None

objs = []
for f in J('messier.json')['features']:
    p = f['properties']; lon, lat = f['geometry']['coordinates']; mid = f['id']
    trn = MESSIER_TR.get(mid)
    name = f"{trn} ({mid})" if trn else mid
    alias = [mid, mid.replace('M', 'M '), p.get('desig', ''), p.get('alt', '') or '', 'messier ' + mid[1:]]
    objs.append(dict(id=mid, name=name, kind=kind(p['type'], mid), ra=round(ra(lon), 4), dec=round(lat, 4),
                     mag=magv(p.get('mag')), size=dims(p.get('dim')), alias=[a for a in alias if a]))
d14 = {f['id']: f for f in J('dsos.14.json')['features']}
for f in J('dsos.bright.json')['features']:
    d14.setdefault(f['id'], f)
for k, (trn, al) in EXTRA.items():
    f = d14.get(k)
    if not f: print('eksik', k); continue
    p = f['properties']; lon, lat = f['geometry']['coordinates']
    objs.append(dict(id=k, name=trn, kind=kind(p['type']), ra=round(ra(lon), 4), dec=round(lat, 4),
                     mag=magv(p.get('mag')), size=dims(p.get('dim')), alias=[k, k.replace(' ', '')] + al))

STAR_TR = {
    'Sirius': ('Sirius (Akyıldız)', ['akyildiz', 'sirius']), 'Polaris': ('Kutup Yıldızı (Polaris)', ['kutup yildizi', 'demirkazik']),
    'Vega': ('Vega', ['akbaba']), 'Arcturus': ('Arktürüs', ['arcturus']), 'Betelgeuse': ('Betelgöz (Betelgeuse)', ['betelgeuse']),
    'Rigel': ('Rigel', []), 'Aldebaran': ('Aldebaran (Boğa\'nın Gözü)', []), 'Antares': ('Antares (Akrep\'in Kalbi)', []),
    'Spica': ('Spika (Başak)', ['spica']), 'Altair': ('Altair (Kartal)', []), 'Deneb': ('Deneb', []),
    'Regulus': ('Regulus (Aslan\'ın Kalbi)', []), 'Procyon': ('Procyon', []), 'Capella': ('Capella', ['keci yildizi']),
    'Canopus': ('Süheyl (Canopus)', ['canopus', 'suheyl']), 'Fomalhaut': ('Fomalhaut', []), 'Castor': ('Castor', []),
    'Pollux': ('Pollux', []), 'Mizar': ('Mizar', []), 'Alcor': ('Alcor', []), 'Algol': ('Algol (Şeytan Yıldızı)', ['seytan yildizi']),
}
names = J('starnames.json')
for hip, v in names.items():
    nm = v.get('name')
    if not nm or hip not in hipmag: continue
    mag, (lon, lat) = hipmag[hip]
    trn, al = STAR_TR.get(nm, (nm, []))
    if mag > 4.5 and nm not in STAR_TR: continue
    objs.append(dict(id='HIP' + hip, name=trn, kind='Yıldız', ra=round(ra(lon), 4), dec=round(lat, 4), mag=mag,
                     size=[0, 0], alias=[nm, v.get('desig', '') or ''] + al, con=v.get('c', '')))
json.dump(objs, open(os.path.join(out, 'objects.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))

cons = []
cn = {f['id']: f['properties'] for f in J('constellations.json')['features']}
cg = {f['id']: f['geometry']['coordinates'] for f in J('constellations.json')['features']}
for f in J('constellations.lines.json')['features']:
    cid = f['id']; p = cn.get(cid, {})
    lines = [[round(c, 3) for pt in seg for c in (ra(pt[0]), pt[1])] for seg in f['geometry']['coordinates']]
    lab = cg.get(cid, [0, 0])
    prev = next((x for x in cons if x['id'] == cid), None)
    if prev:  # Serpens comes in two parts
        prev['lines'] += lines; continue
    cons.append(dict(id=cid, name=p.get('tr') or p.get('name', cid), latin=p.get('name', cid),
                     label=[round(ra(lab[0]), 3), lab[1]], lines=lines))
json.dump(cons, open(os.path.join(out, 'constellations.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(len(stars), 'yıldız;', len(objs), 'aranabilir cisim;', len(cons), 'takımyıldız')
