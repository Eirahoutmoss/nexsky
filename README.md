<p align="center"><img src="docs/marka/nexsky-marka.png" alt="NexSky" width="720"></p>

# NexSky — Gökyüzü senin rehberin

Ay ve gökyüzü fotoğrafçıları için ücretsiz, açık kaynak, **internetsiz çalışan** gökyüzü rehberi ve çekim planlayıcı. Nex ailesinden.

> Durum: **önizleme (0.1.x)**. Android.

| Bölüm | Ne yapar |
|---|---|
| **Arama** | "Mars", "Jüpiter", "Orion Bulutsusu", "M42", "Ülker", "Kutup Yıldızı", "Büyük Ayı"… yaz; şu an nerede (yön, yükseklik), bugün doğuş, en yüksek nokta ve batış saati, parlaklık, görünen büyüklük, uzaklık. |
| **Gökyüzü** | Telefonu göğe tut: yıldızlar, takımyıldızlar, gezegenler, Ay ve derin uzay cisimleri gerçek konumlarında. Aranan cisim ekran dışındaysa ok seni ona çevirir. Harita modunda parmakla gezin. |
| **Planlayıcı** | **Ay:** doğuş/batış saati ve yönü, evre, aydınlanma, görünen büyüklük (süper ay), mesafe, yaklaşan evreler. **Güneş:** doğuş/batış, altın ve mavi saat. **Kadraj:** Nikon P1000 (24–3000 mm) ve diğer makinelerde cismin kadrajda gerçek boyutu. |
| **Harita** | Ay ve Güneş doğuş/batış yönleri haritada. Ön plana (kale, minare…) uzun bas: doğan Ay'ı onun arkasında görmek için nerede durman gerektiğini hesaplar. |

## Doğruluk

Konumlar [Astronomy Engine](https://github.com/cosinekitty/astronomy) (MIT, Don Cross) ile hesaplanır; JPL DE421 efemerisine göre Ay ve gezegenlerde yaklaşık 0,1–0,4 yay dakikası hata. Her şey telefonda hesaplanır, internet gerekmez (harita altlığı hariç; bakılan bölgeler önbellekte kalır).

## Derleme (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File build-apk.ps1
```

Çıktı: `dist\NexSky.apk`. İlk derlemede imza anahtarı `%USERPROFILE%\.nexsky\` altında oluşturulur — **yedekleyin**.

Katalog verisi `tools/build_catalog.py` ile [d3-celestial](https://github.com/ofrohn/d3-celestial) verisinden üretilir.

## Lisans

MIT — © 2026 Hasan Güler. Üçüncü taraf: Astronomy Engine (MIT), d3-celestial verisi (BSD-3), Inter yazı tipi (SIL OFL 1.1), harita © OpenStreetMap katkıda bulunanlar. Lisans metinleri `licenses/` klasöründe.
